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
                var body = await reader.ReadToEndAsync();
                if (string.IsNullOrWhiteSpace(body)) return Results.BadRequest();

                // Auto-sync is off: accept and drop silently (the desktop toggle controls this)
                if (!ClipboardSyncEnabled) return Results.Ok();

                string textToSet = body;
                string? imageBase64 = null;

                try
                {
                    using var doc = JsonDocument.Parse(body);
                    var root = doc.RootElement;
                    if (root.ValueKind == JsonValueKind.Object)
                    {
                        if (root.TryGetProperty("imageBase64", out var imgElem)) imageBase64 = imgElem.GetString();
                        else if (root.TryGetProperty("data", out var d1) && d1.TryGetProperty("imageBase64", out var imgElem2)) imageBase64 = imgElem2.GetString();

                        if (root.TryGetProperty("text", out var txtElem)) textToSet = txtElem.GetString() ?? "";
                        else if (root.TryGetProperty("data", out var d2) && d2.TryGetProperty("text", out var txtElem2)) textToSet = txtElem2.GetString() ?? "";
                    }
                }
                catch { }

                // Remember what the phone pushed so the PC auto-sync watcher can recognize
                // this content as "already synced" instead of echoing it back to the phone
                LastPhoneClipboard = (!string.IsNullOrEmpty(imageBase64) ? $"[IMAGE:{imageBase64.Length}]" : textToSet, DateTime.UtcNow);

                if (!string.IsNullOrEmpty(imageBase64))
                {
                    try
                    {
                        var tempPath = Path.Combine(Path.GetTempPath(), $"dex_clip_{Guid.NewGuid():N}.png");
                        var imageBytes = Convert.FromBase64String(imageBase64);
                        await File.WriteAllBytesAsync(tempPath, imageBytes);

                        var script = $"Add-Type -AssemblyName System.Windows.Forms, System.Drawing; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('{tempPath.Replace("'", "''")}'))";
                        var psi = new System.Diagnostics.ProcessStartInfo("powershell", $"-NoProfile -ExecutionPolicy Bypass -Command \"{script}\"")
                        {
                            UseShellExecute = false,
                            CreateNoWindow = true
                        };
                        System.Diagnostics.Process.Start(psi);
                    }
                    catch { }
                }
                else
                {
                    var psi = new System.Diagnostics.ProcessStartInfo("powershell", "-NoProfile -Command \"$input | Set-Clipboard\"")
                    {
                        RedirectStandardInput = true,
                        UseShellExecute = false,
                        CreateNoWindow = true
                    };
                    var p = System.Diagnostics.Process.Start(psi);
                    if (p != null)
                    {
                        await p.StandardInput.WriteAsync(textToSet);
                        p.StandardInput.Close();
                    }
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
                // PowerShell POSTs the clipboard text or rich-media JSON; the server forwards it to the phone.
                var ip = request.Query["ip"].ToString();
                var fp = request.Query["fingerprint"].ToString();
                if (string.IsNullOrEmpty(fp) && !string.IsNullOrEmpty(ip))
                {
                    var dev = DiscoveryBackgroundService.Devices.Values.FirstOrDefault(d => d.Ip == ip);
                    if (dev != null) fp = dev.Info.Fingerprint;
                }
                if (string.IsNullOrEmpty(fp)) return Results.NotFound();

                using var reader = new StreamReader(request.Body);
                var body = await reader.ReadToEndAsync();
                if (string.IsNullOrWhiteSpace(body)) return Results.BadRequest();

                string payload;
                try
                {
                    using var doc = JsonDocument.Parse(body);
                    var root = doc.RootElement;
                    if (root.ValueKind == JsonValueKind.Object && root.TryGetProperty("type", out var typeElem) && typeElem.GetString() == "set-clipboard")
                    {
                        payload = body;
                    }
                    else
                    {
                        payload = JsonSerializer.Serialize(new { type = "set-clipboard", data = new { text = body } },
                            new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                    }
                }
                catch
                {
                    payload = JsonSerializer.Serialize(new { type = "set-clipboard", data = new { text = body } },
                        new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                }

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
                var ipQuery = request.Query["ip"].ToString();
                if (string.IsNullOrEmpty(ipQuery)) return Results.BadRequest();

                var ips = ipQuery.Split(',');
                var mainIp = ips[0];
                var extraIps = ips.Length > 1 ? string.Join(",", ips.Skip(1)) : "";

                string payload = $"http://{mainIp}:{DeXConstants.HttpsPort}";
                if (!string.IsNullOrEmpty(extraIps)) payload += $"?ips={extraIps}";

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

            // Live 480p Windows Desktop Wallpaper for paired device card backgrounds on mobile with HTTP 304 ETag support
            app.MapGet("/api/dex/wallpaper", (HttpRequest request, HttpResponse response) =>
            {
                var token = request.Query["token"].ToString();
                var fingerprint = request.Query["fingerprint"].ToString();
                var auth = request.Headers.Authorization.ToString();
                if (!string.IsNullOrEmpty(auth) && auth.StartsWith("Bearer "))
                {
                    token = auth.Substring("Bearer ".Length);
                }

                if (!IdentityManager.IsPairedTokenOrFingerprint(token, fingerprint))
                {
                    return Results.StatusCode(401);
                }

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
                var token = request.Query["token"].ToString();
                var fingerprint = request.Query["fingerprint"].ToString();
                var auth = request.Headers.Authorization.ToString();
                if (!string.IsNullOrEmpty(auth) && auth.StartsWith("Bearer "))
                {
                    token = auth.Substring("Bearer ".Length);
                }

                if (!IdentityManager.IsPairedTokenOrFingerprint(token, fingerprint))
                {
                    return Results.StatusCode(401);
                }

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
