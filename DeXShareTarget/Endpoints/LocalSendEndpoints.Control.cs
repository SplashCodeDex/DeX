using System;
using System.IO;
using System.Linq;
using System.Text.Json;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using DeXShareTarget.Services;

namespace DeXShareTarget.Endpoints
{
    public static partial class LocalSendEndpoints
    {
        /// <summary>Registers the quick-action control endpoints (clipboard, mirror, alias, QR, DND) and the phone clipboard push.</summary>
        public static void MapLocalControlEndpoints(this WebApplication app)
        {
            app.MapPost("/api/dex/clipboard", async (HttpRequest request) =>
            {
                var auth = request.Headers.Authorization.ToString();
                if (string.IsNullOrEmpty(auth) || !auth.StartsWith("Bearer ")) return Results.StatusCode(401);
                var token = auth.Substring("Bearer ".Length);
                if (!IdentityManager.IsIdentityToken(token) && !IdentityManager.PairedTokens.Values.Contains(token)) return Results.StatusCode(401);

                using var reader = new StreamReader(request.Body);
                var text = await reader.ReadToEndAsync();
                if (string.IsNullOrWhiteSpace(text)) return Results.BadRequest();

                // Auto-sync is off: accept and drop silently (the desktop toggle controls this)
                if (!ClipboardSyncEnabled) return Results.Ok();

                // Remember what the phone pushed so the PC auto-sync watcher can recognize
                // this text as "already synced" instead of echoing it back to the phone
                LastPhoneClipboard = (text, DateTime.UtcNow);
                
                var psi = new System.Diagnostics.ProcessStartInfo("powershell", "-NoProfile -Command \"$input | Set-Clipboard\"")
                {
                    RedirectStandardInput = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };
                var p = System.Diagnostics.Process.Start(psi);
                if (p != null)
                {
                    await p.StandardInput.WriteAsync(text);
                    p.StandardInput.Close();
                }
                
                return Results.Ok();
            });

            app.MapPost("/local/clipboard-sync", (HttpRequest request) =>
            {
                // Desktop quick-action toggle: master switch for automatic 2-way clipboard sync
                var enabled = request.Query["enabled"].ToString();
                if (bool.TryParse(enabled, out var value))
                {
                    ClipboardSyncEnabled = value;
                    return Results.Ok();
                }
                return Results.BadRequest();
            });

            app.MapGet("/local/clipboard-state", () =>
            {
                // Last clipboard text a phone pushed to this PC, so the auto-sync watcher
                // can tell "already synced" text apart from fresh local copies
                var last = LastPhoneClipboard;
                return Results.Json(new
                {
                    text = last?.Text ?? "",
                    at = last.HasValue ? new DateTimeOffset(last.Value.At).ToUnixTimeMilliseconds() : 0L
                });
            });

            app.MapGet("/local/mirror-state", () =>
            {
                // Lets the desktop keep the quick-action mirror toggle in sync with reality
                return Results.Json(new { active = MirrorWindowHost.IsActive, fingerprint = MirrorWindowHost.ActiveFingerprint });
            });

            app.MapPost("/local/mirror-stop", (HttpRequest request) =>
            {
                // Stops the active mirror session (or the one for a specific device)
                var ip = request.Query["ip"].ToString();
                var fp = request.Query["fingerprint"].ToString();
                if (string.IsNullOrEmpty(fp) && !string.IsNullOrEmpty(ip))
                {
                    var dev = DiscoveryBackgroundService.Devices.Values.FirstOrDefault(d => d.Ip == ip);
                    if (dev != null) fp = dev.Info.Fingerprint;
                }
                if (string.IsNullOrEmpty(fp)) { MirrorWindowHost.StopActive(); }
                else { MirrorWindowHost.Stop(fp); }
                return Results.Ok();
            });

            app.MapPost("/local/mirror", async (HttpRequest request) =>
            {
                // Start a screen-mirror session: ask the phone to stream (it shows the
                // MediaProjection consent) and open the desktop mirror window.
                var ip = request.Query["ip"].ToString();
                var fp = request.Query["fingerprint"].ToString();
                if (string.IsNullOrEmpty(fp) && !string.IsNullOrEmpty(ip))
                {
                    var dev = DiscoveryBackgroundService.Devices.Values.FirstOrDefault(d => d.Ip == ip);
                    if (dev != null) fp = dev.Info.Fingerprint;
                }
                if (string.IsNullOrEmpty(fp)) return Results.NotFound();

                var alias = DiscoveryBackgroundService.Devices.TryGetValue(fp, out var d) && d.Info != null
                    ? d.Info.Alias ?? fp
                    : fp;

                var start = JsonSerializer.Serialize(new { type = "mirror-start", data = new { } },
                    new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                var sent = await WebSocketConnectionManager.SendAsync(fp, start);
                if (!sent) return Results.NotFound();

                MirrorWindowHost.Start(fp, alias);
                return Results.Ok();
            });

            app.MapPost("/local/clipboard-push", async (HttpRequest request) =>
            {
                // PC -> phone clipboard sync over the WebSocket (no ADB required).
                // PowerShell POSTs the clipboard text; the server forwards it to the phone.
                var ip = request.Query["ip"].ToString();
                var fp = request.Query["fingerprint"].ToString();
                if (string.IsNullOrEmpty(fp) && !string.IsNullOrEmpty(ip))
                {
                    var dev = DiscoveryBackgroundService.Devices.Values.FirstOrDefault(d => d.Ip == ip);
                    if (dev != null) fp = dev.Info.Fingerprint;
                }
                if (string.IsNullOrEmpty(fp)) return Results.NotFound();

                using var reader = new StreamReader(request.Body);
                var text = await reader.ReadToEndAsync();
                if (string.IsNullOrWhiteSpace(text)) return Results.BadRequest();

                var payload = JsonSerializer.Serialize(new { type = "set-clipboard", data = new { text } },
                    new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                var sent = await WebSocketConnectionManager.SendAsync(fp, payload);
                return sent ? Results.Ok() : Results.NotFound();
            });

            app.MapPost("/local/alias", (HttpRequest request) => 
            {
                var fp = request.Query["fingerprint"].ToString();
                var alias = request.Query["alias"].ToString();
                if (!string.IsNullOrEmpty(fp) && !string.IsNullOrEmpty(alias))
                {
                    IdentityManager.SetDeviceAlias(fp, alias);
                    return Results.Ok();
                }
                return Results.BadRequest();
            });

            app.MapGet("/local/qr", (HttpRequest request) => 
            {
                var ip = request.Query["ip"].ToString();
                if (string.IsNullOrEmpty(ip)) return Results.BadRequest();

                string payload = $"http://{ip}:{DeXConstants.HttpsPort}";
                using var qrGenerator = new QRCoder.QRCodeGenerator();
                using var qrCodeData = qrGenerator.CreateQrCode(payload, QRCoder.QRCodeGenerator.ECCLevel.M);
                using var qrCode = new QRCoder.PngByteQRCode(qrCodeData);
                var qrCodeImage = qrCode.GetGraphic(10); // 10 pixels per module
                
                return Results.File(qrCodeImage, "image/png");
            });

            app.MapPost("/local/dnd", (HttpRequest request) => 
            {
                var enabled = request.Query["enabled"].ToString() == "true";
                IsDndEnabled = enabled;
                return Results.Ok(new { dnd = IsDndEnabled });
            });

            // Live 480p Windows Desktop Wallpaper for device card backgrounds on mobile with HTTP 304 ETag support
            app.MapGet("/api/dex/wallpaper", (HttpRequest request, HttpResponse response) =>
            {
                var wallpaper = WallpaperService.GetWallpaper480p();
                if (wallpaper == null) return Results.NotFound();

                response.Headers["ETag"] = wallpaper.Value.ETag;
                response.Headers["Cache-Control"] = "public, max-age=300";

                var clientEtag = request.Headers.IfNoneMatch.ToString();
                if (!string.IsNullOrEmpty(clientEtag) && clientEtag == wallpaper.Value.ETag)
                {
                    return Results.StatusCode(304); // Not Modified
                }

                return Results.Bytes(wallpaper.Value.Bytes, contentType: wallpaper.Value.ContentType);
            });

            app.MapGet("/api/localsend/v2/wallpaper", (HttpRequest request, HttpResponse response) =>
            {
                var wallpaper = WallpaperService.GetWallpaper480p();
                if (wallpaper == null) return Results.NotFound();

                response.Headers["ETag"] = wallpaper.Value.ETag;
                response.Headers["Cache-Control"] = "public, max-age=300";

                var clientEtag = request.Headers.IfNoneMatch.ToString();
                if (!string.IsNullOrEmpty(clientEtag) && clientEtag == wallpaper.Value.ETag)
                {
                    return Results.StatusCode(304); // Not Modified
                }

                return Results.Bytes(wallpaper.Value.Bytes, contentType: wallpaper.Value.ContentType);
            });
        }
    }
}
