using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using Windows.Data.Xml.Dom;
using Windows.UI.Notifications;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using DeXShareTarget.Models;
using DeXShareTarget.Services;

namespace DeXShareTarget.Endpoints
{
    public static class LocalSendEndpoints
    {
        public static ConcurrentDictionary<string, string> HostedFiles = new();
        // Most recent clipboard text pushed by a phone (text + UTC timestamp). Powers the
        // PC-side auto-sync watcher, which must not echo the phone's text back to it.
        public static (string Text, DateTime At)? LastPhoneClipboard { get; private set; }

        // Master switch for automatic clipboard sync, controlled by the desktop quick-action
        // toggle. When off, phone clipboard pushes are accepted but dropped (no PC clipboard
        // write, no echo state). Manual PC -> phone pushes are NOT affected.
        public static bool ClipboardSyncEnabled { get; set; } = false;
        // Per-file pull tokens: the phone must present the token to download a hosted file
        public static ConcurrentDictionary<string, string> HostedFileTokens = new();
        // Last time each hosted file was served, used for sliding expiry so slow pulls don't 404
        public static ConcurrentDictionary<string, DateTime> HostedFileLastAccess = new();
        // Public TCP endpoints of phones (for direct phone-to-phone punching), keyed by fingerprint
        public static ConcurrentDictionary<string, (string Ip, int Port, DateTime Ts)> PunchEndpoints = new();
        // Files received per upload session (relay fallback): sessionId -> (name, path)
        public static ConcurrentDictionary<string, List<(string Name, string Path)>> RelaySessionFiles = new();
        public static ConcurrentDictionary<string, string> RelaySessionAliases = new();
        public static bool IsDndEnabled { get; set; } = false;
        public static ConcurrentDictionary<string, string> OutboundPairingStatus = new();
        // Active outbound pairing attempts so the GUI can display the PIN even for phone-initiated pairing
        public static ConcurrentDictionary<string, PendingPairAttempt> PendingPairPins = new();

        /// <summary>Normalizes a client-supplied relative path: forward slashes, no ".." or traversal.</summary>
        private static string SanitizeRelativePath(string? path)
        {
            if (string.IsNullOrWhiteSpace(path)) return "";
            var parts = path.Replace('\\', '/').Split('/')
                .Where(p => !string.IsNullOrEmpty(p) && p != "." && p != "..")
                .Select(p => Path.GetFileName(p)); // strips any residual traversal
            return string.Join(Path.DirectorySeparatorChar, parts);
        }

        public static void MapLocalSendEndpoints(this WebApplication app)
        {
            app.MapGet("/api/localsend/v2/info", () => Results.Json(new RegisterDto { IdentityHash = IdentityManager.IdentityHash }));
            
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

            app.MapPost("/api/localsend/v2/register", (RegisterDto req) => 
            {
                return Results.Json(new { sessionId = Guid.NewGuid().ToString() });
            });

            var activeSessions = new ConcurrentDictionary<string, PrepareUploadRequestDto>();
            var activeSessionsProgress = new ConcurrentDictionary<string, int>();
            HostedFiles = new ConcurrentDictionary<string, string>();

            app.MapPost("/api/localsend/v2/prepare-upload", async (HttpRequest request, PrepareUploadRequestDto req, CancellationToken ct) =>
            {
                if (IsDndEnabled) return Results.StatusCode(403);

                var auth = request.Headers.Authorization.ToString();
                var token = auth.StartsWith("Bearer ") ? auth.Substring("Bearer ".Length) : null;

                bool isAutoTrusted = IdentityManager.IsIdentityToken(token);
                bool isPaired = !string.IsNullOrEmpty(token) && IdentityManager.PairedTokens.TryGetValue(req.Info.Fingerprint, out var expectedToken) && expectedToken == token;

                if (!isAutoTrusted && !isPaired)
                {
                    return Results.StatusCode(403);
                }
                var tcs = new TaskCompletionSource<bool>();
                ReceivePromptWindow? win = null;
                System.Windows.Application.Current.Dispatcher.Invoke(() =>
                {
                    var senderAlias = req.Info.Alias ?? "Unknown Device";
                    win = new ReceivePromptWindow(senderAlias, req.Files.Count);
                    win.Show();
                    _ = win.WaitForResponseAsync().ContinueWith(t => 
                    {
                        tcs.TrySetResult(t.Result);
                    });
                });

                bool res = false;
                using (ct.Register(() => { tcs.TrySetResult(false); win?.Dispatcher.Invoke(() => win.Close()); }))
                {
                    res = await tcs.Task;
                }

                if (!res) return Results.StatusCode(403);
                
                var sessionId = Guid.NewGuid().ToString();
                activeSessions[sessionId] = req;
                _ = Task.Delay(TimeSpan.FromMinutes(10)).ContinueWith(t => { activeSessions.TryRemove(sessionId, out _); activeSessionsProgress.TryRemove(sessionId, out _); });
                var resFiles = new Dictionary<string, string>();
                string downloadsFolder = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile) + "\\Downloads\\DeX";
                Directory.CreateDirectory(downloadsFolder);
                foreach (var kvp in req.Files)
                {
                    string safeFileName = Path.GetFileName(kvp.Value.FileName);
                    if (string.IsNullOrEmpty(safeFileName)) safeFileName = "unnamed_file";
                    
                    string destPath = Path.Combine(downloadsFolder, safeFileName);
                    if (File.Exists(destPath) && new FileInfo(destPath).Length == kvp.Value.Size)
                    {
                        var localPartial = await HashHelper.ComputePartialHashAsync(destPath, kvp.Value.Size);
                        if (localPartial != null && localPartial == kvp.Value.PartialHash)
                        {
                            File.SetLastWriteTime(destPath, DateTime.Now);
                            resFiles[kvp.Key] = "[SKIP]";
                            continue;
                        }
                    }

                    resFiles[kvp.Key] = Guid.NewGuid().ToString(); // Token for the file
                }
                return Results.Json(new PrepareUploadResponseDto { SessionId = sessionId, Files = resFiles });
            });

            app.MapPost("/api/localsend/v2/upload", async (HttpRequest request) =>
            {
                var sessionId = request.Query["sessionId"].ToString();
                var fileId = request.Query["fileId"].ToString();
                var token = request.Query["token"].ToString(); // Token unused in minimal impl

                if (!activeSessions.TryGetValue(sessionId, out var sessionReq)) return Results.BadRequest();
                if (!sessionReq.Files.TryGetValue(fileId, out var fileMeta)) return Results.BadRequest();

                string safeFileName = Path.GetFileName(fileMeta.FileName);
                if (string.IsNullOrEmpty(safeFileName)) safeFileName = "unnamed_file";
                
                // Folder bundles: recreate the relative path structure under Downloads/DeX
                string safeRelative = SanitizeRelativePath(fileMeta.RelativePath);
                
                string downloadsFolder = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile) + "\\Downloads\\DeX";
                Directory.CreateDirectory(downloadsFolder);
                string destPath = string.IsNullOrEmpty(safeRelative)
                    ? Path.Combine(downloadsFolder, safeFileName)
                    : Path.Combine(downloadsFolder, safeRelative);
                if (!string.IsNullOrEmpty(safeRelative))
                {
                    var parentDir = Path.GetDirectoryName(destPath);
                    if (!string.IsNullOrEmpty(parentDir)) Directory.CreateDirectory(parentDir);
                }

                int counter = 1;
                while (File.Exists(destPath))
                {
                    string nameNoExt = Path.GetFileNameWithoutExtension(safeFileName);
                    string ext = Path.GetExtension(safeFileName);
                    destPath = Path.Combine(downloadsFolder, $"{nameNoExt} ({counter}){ext}");
                    counter++;
                }

                try
                {
                    using var fs = new FileStream(destPath, FileMode.CreateNew);
                    await request.Body.CopyToAsync(fs);
                }
                catch
                {
                    // Never leave a partial file behind on a failed upload
                    try { if (File.Exists(destPath)) File.Delete(destPath); } catch { }
                    return Results.StatusCode(500);
                }

                // Track received files for the relay fallback (A→PC→B when punching fails).
                // Cleaned by its own 10-minute TTL, independent of the session's early removal.
                var relayList = RelaySessionFiles.GetOrAdd(sessionId, _ => new List<(string, string)>());
                lock (relayList) relayList.Add((safeFileName, destPath));
                RelaySessionAliases[sessionId] = sessionReq.Info.Alias ?? "Device";
                _ = Task.Delay(TimeSpan.FromMinutes(10)).ContinueWith(t =>
                {
                    RelaySessionFiles.TryRemove(sessionId, out _);
                    RelaySessionAliases.TryRemove(sessionId, out _);
                });

                try
                {
                    var count = activeSessionsProgress.AddOrUpdate(sessionId, 1, (_, v) => v + 1);
                    if (count == sessionReq.Files.Count)
                    {
                        activeSessions.TryRemove(sessionId, out _);
                        activeSessionsProgress.TryRemove(sessionId, out _);

                        string toastXmlString = 
                        $@"<toast>
                            <visual>
                                <binding template='ToastGeneric'>
                                    <text>DeX Transfer Complete</text>
                                    <text>Received {count} file(s) from {sessionReq.Info.Alias}</text>
                                </binding>
                            </visual>
                        </toast>";
                        var xmlDoc = new XmlDocument();
                        xmlDoc.LoadXml(toastXmlString);
                        var toastNode = new ToastNotification(xmlDoc);
                        ToastNotificationManager.CreateToastNotifier("DeX").Show(toastNode);
                    }
                }
                catch { }

                return Results.Ok();
            });

            app.MapGet("/punch/endpoint", (string fingerprint, HttpContext context) =>
            {
                // STUN-style reflection: the phone connects FROM its punch listener port, so the
                // source address of this TLS connection IS its public TCP endpoint. Other phones
                // use it for direct (NAT-punched) transfers.
                var remoteIp = context.Connection.RemoteIpAddress?.ToString() ?? "";
                var remotePort = context.Connection.RemotePort;
                if (!string.IsNullOrEmpty(fingerprint) && !string.IsNullOrEmpty(remoteIp) && remotePort > 0)
                {
                    PunchEndpoints[fingerprint] = (remoteIp, remotePort, DateTime.UtcNow);

                    // Prune stale registrations (phones refresh every 2 minutes)
                    var cutoff = DateTime.UtcNow.AddMinutes(-5);
                    foreach (var (fp, ep) in PunchEndpoints)
                    {
                        if (ep.Ts < cutoff) PunchEndpoints.TryRemove(fp, out _);
                    }

                    return Results.Json(new { ip = remoteIp, port = remotePort });
                }
                return Results.BadRequest();
            });

            app.MapGet("/download/{fileId}", (string fileId, HttpRequest request) =>
            {
                // Pulls are authenticated with the per-file token delivered in the prepare-upload message
                if (!HostedFiles.TryGetValue(fileId, out string? path) ||
                    !HostedFileTokens.TryGetValue(fileId, out var expectedToken) ||
                    request.Query["token"].ToString() != expectedToken)
                {
                    return Results.NotFound();
                }
                if (!File.Exists(path)) return Results.NotFound();

                HostedFileLastAccess[fileId] = DateTime.UtcNow;
                // Range processing enables resumable downloads over flaky WAN connections
                return Results.File(path, "application/octet-stream", enableRangeProcessing: true);
            });

            var rateLimits = new ConcurrentDictionary<string, DateTime>();


            // Local API for PowerShell to read discovered devices
            app.MapGet("/local/devices", () => 
            {
                // Clean up stale
                var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                foreach (var k in DiscoveryBackgroundService.Devices.Keys)
                {
                    if (now - DiscoveryBackgroundService.Devices[k].LastSeen > 10000)
                        DiscoveryBackgroundService.Devices.TryRemove(k, out _);
                }
                return Results.Json(DiscoveryBackgroundService.Devices.Values.Select(d =>
                {
                    var wifi = TelemetryStore.GetWifi(d.Info.Fingerprint);
                    return new
                    {
                        d.Ip,
                        d.Info,
                        d.LastSeen,
                        d.IsPaired,
                        d.IsAutoTrusted,
                        Battery = TelemetryStore.GetBattery(d.Info.Fingerprint),
                        WifiSsid = wifi?.Ssid,
                        WifiRssi = wifi?.Rssi
                    };
                }));
            });

            app.MapPost("/local/devices/flush", () => 
            {
                DiscoveryBackgroundService.Devices.Clear();
                return Results.Ok();
            });

            app.MapGet("/local/devices/ping", async (string ip) =>
            {
                if (string.IsNullOrEmpty(ip)) return Results.BadRequest();
                try
                {
                    using var handler = new System.Net.Http.SocketsHttpHandler
                    {
                        SslOptions = new System.Net.Security.SslClientAuthenticationOptions
                        {
                            RemoteCertificateValidationCallback = (s, c, ch, e) => true,
                            ApplicationProtocols = new System.Collections.Generic.List<System.Net.Security.SslApplicationProtocol>
                                { System.Net.Security.SslApplicationProtocol.Http11 }
                        }
                    };
                    using var http = new System.Net.Http.HttpClient(handler) { Timeout = TimeSpan.FromSeconds(2) };
                    
                    var response = await http.GetAsync($"https://{ip}:53317/api/localsend/v2/info");
                    if (!response.IsSuccessStatusCode)
                    {
                        response = await http.GetAsync($"http://{ip}:53317/api/localsend/v2/info");
                    }

                    if (response.IsSuccessStatusCode)
                    {
                        var json = await response.Content.ReadAsStringAsync();
                        var root = JsonDocument.Parse(json).RootElement;
                        var info = new RegisterDto
                        {
                            Alias = root.TryGetProperty("alias", out var a) ? (a.GetString() ?? "Unknown") : "Unknown",
                            DeviceModel = root.TryGetProperty("deviceModel", out var dm) ? (dm.GetString() ?? "Device") : "Device",
                            DeviceType = root.TryGetProperty("deviceType", out var dt) ? (dt.GetString() ?? "desktop") : "desktop",
                            Fingerprint = root.TryGetProperty("fingerprint", out var fp) ? (fp.GetString() ?? Guid.NewGuid().ToString()) : Guid.NewGuid().ToString(),
                            IdentityHash = root.TryGetProperty("identityHash", out var ih) ? ih.GetString() : null
                        };

                        var dev = new DiscoveredDevice
                        {
                            Ip = ip,
                            Info = info,
                            LastSeen = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                        };
                        DiscoveryBackgroundService.Devices[info.Fingerprint] = dev;
                        return Results.Ok(dev);
                    }
                }
                catch { }
                return Results.NotFound();
            });

            app.MapGet("/local/token", (HttpRequest request) => 
            {
                var ip = request.Query["ip"].ToString();
                if (string.IsNullOrEmpty(ip)) return Results.BadRequest();
                var dev = DiscoveryBackgroundService.Devices.Values.FirstOrDefault(d => d.Ip == ip);
                if (dev == null) return Results.NotFound();
                if (IdentityManager.PairedTokens.TryGetValue(dev.Info.Fingerprint, out var token) && token != null)
                    return Results.Json(new { token });
                return Results.NotFound();
            });

            app.MapPost("/local/unpair", (HttpRequest request) => 
            {
                var fp = request.Query["fingerprint"].ToString();
                if (!string.IsNullOrEmpty(fp))
                {
                    IdentityManager.RemovePairedDevice(fp);
                    return Results.Ok();
                }
                return Results.BadRequest();
            });
            app.MapGet("/local/pair-status", (HttpRequest request) => 
            {
                var ip = request.Query["ip"].ToString();
                if (!string.IsNullOrEmpty(ip) && OutboundPairingStatus.TryGetValue(ip, out var status))
                {
                    return Results.Json(new { status });
                }
                return Results.NotFound();
            });

            app.MapGet("/local/pending-pair", () =>
            {
                // Return the most recent active pairing attempt (phone-initiated flows have no GUI
                // trigger, so the GUI polls this to display the PIN panel)
                var now = DateTime.UtcNow;
                var entry = PendingPairPins
                    .Where(kv => now - kv.Value.CreatedAt < TimeSpan.FromSeconds(75))
                    .OrderByDescending(kv => kv.Value.CreatedAt)
                    .FirstOrDefault();
                if (entry.Value == null)
                {
                    return Results.NotFound();
                }
                return Results.Json(new { ip = entry.Key, fingerprint = entry.Value.Fingerprint, pin = entry.Value.Pin, alias = entry.Value.Alias });
            });

            app.MapGet("/local/cert", () =>
            {
                // The stable server certificate, for the Android app to install as a trusted CA
                // so Cronet can speak HTTP/3 (QUIC) to this PC
                if (LocalSendServer.ServerCert == null)
                {
                    return Results.NotFound();
                }
                return Results.File(LocalSendServer.ServerCert.RawData, "application/x-x509-ca-cert", "dex.pem");
            });

            app.MapPost("/local/pair-initiate", async (HttpRequest request) => 
            {
                var targetIp = request.Query["ip"].ToString();
                var targetFp = request.Query["fingerprint"].ToString();
                
                if (string.IsNullOrEmpty(targetIp) || string.IsNullOrEmpty(targetFp))
                    return Results.BadRequest();

                var pin = await PushPairPromptAsync(targetFp, targetIp);
                return Results.Json(new { pin });
            });

            app.MapPost("/local/pair-cancel", (HttpRequest request) => 
            {
                var targetIp = request.Query["ip"].ToString();
                var fp = request.Query["fingerprint"].ToString();
                if (!string.IsNullOrEmpty(targetIp))
                {
                    OutboundPairingStatus[targetIp] = "Cancelled";
                    ClearPendingPair(targetIp);
                }
                if (!string.IsNullOrEmpty(fp))
                {
                    IdentityManager.RemovePairedDevice(fp);
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

                string payload = $"http://{ip}:53317";
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

            app.MapPost("/local/settings/email", async (HttpRequest req) => 
            {
                using var reader = new StreamReader(req.Body);
                var email = await reader.ReadToEndAsync();
                IdentityManager.SetEmail(email);
                return Results.Ok();
            });

            // PC-side Google Sign-In: opens the browser (OAuth loopback), then sets the verified email.
            // Reachable at http://127.0.0.1:53318/local/settings/google-signin
            app.MapGet("/local/settings/google-signin", async () =>
            {
                if (!DeXShareTarget.Services.GoogleOAuth.IsConfigured())
                {
                    return Results.Text("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Google Sign-In is not configured (oauth.local.json missing).</h3></body></html>", "text/html");
                }
                var profile = await DeXShareTarget.Services.GoogleOAuth.SignInAsync();
                if (profile != null)
                {
                    IdentityManager.SetEmail(profile.Email);
                    Console.WriteLine($"[OAUTH] Signed in as {profile.Email}");
                    var name = string.IsNullOrEmpty(profile.Name) ? profile.Email : profile.Name;
                    return Results.Text($"<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Signed in as {name} — DeX devices with this email are now auto-trusted.</h3></body></html>", "text/html");
                }
                return Results.Text("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed or was cancelled.</h3></body></html>", "text/html");
            });

            // PC sign-out: clears the email identity and the stored Google profile
            app.MapPost("/local/settings/signout", () =>
            {
                IdentityManager.SetEmail("");
                DeXShareTarget.Services.GoogleOAuth.SignOut();
                Console.WriteLine("[OAUTH] Signed out");
                return Results.Ok();
            });

            // The last signed-in Google profile (name/email/avatar) for the settings UI
            app.MapGet("/local/settings/google-profile", () =>
            {
                var profile = DeXShareTarget.Services.GoogleOAuth.LoadProfile();
                if (profile == null) return Results.Json(new { email = "", name = "", picture = "" });
                return Results.Json(profile);
            });

            // PC File Explorer over the WebSocket (phone exposes its SAF-granted folders).
            // Each endpoint pushes a request to the phone. Fast calls (list/browse/grant) block
            // for the reply; pulls are async so the GUI can show live progress and cancel.
            app.MapPost("/local/dex/list-folders", async (HttpRequest request) =>
            {
                var body = await request.ReadFromJsonAsync<JsonObject>();
                var ip = body?["ip"]?.GetValue<string>() ?? request.Query["ip"].ToString();
                if (string.IsNullOrEmpty(ip)) return Results.BadRequest();
                if (!TrySendDexRequest(ip, "list-shared-folders", null, out var requestId)) return Results.NotFound();
                var reply = await DexRequestStore.WaitAsync(requestId, 25);
                return reply == null ? Results.NotFound() : Results.Json(reply);
            });

            app.MapPost("/local/dex/browse", async (HttpRequest request) =>
            {
                var body = await request.ReadFromJsonAsync<JsonObject>();
                var ip = body?["ip"]?.GetValue<string>() ?? request.Query["ip"].ToString();
                var folderUri = body?["folderUri"]?.GetValue<string>() ?? request.Query["folderUri"].ToString();
                if (string.IsNullOrEmpty(ip) || string.IsNullOrEmpty(folderUri)) return Results.BadRequest();
                var extra = new JsonObject { ["folderUri"] = folderUri };
                if (!TrySendDexRequest(ip, "browse-folder", extra, out var requestId)) return Results.NotFound();
                var reply = await DexRequestStore.WaitAsync(requestId, 25);
                return reply == null ? Results.NotFound() : Results.Json(reply);
            });

            // Pull is asynchronous: returns the requestId immediately so the GUI polls progress.
            app.MapPost("/local/dex/pull", async (HttpRequest request) =>
            {
                var body = await request.ReadFromJsonAsync<JsonObject>();
                var ip = body?["ip"]?.GetValue<string>() ?? request.Query["ip"].ToString();
                var files = body?["files"];
                if (string.IsNullOrEmpty(ip) || files == null) return Results.BadRequest();
                var extra = new JsonObject { ["files"] = files.DeepClone() };
                if (!TrySendDexRequest(ip, "pull-files", extra, out var requestId)) return Results.NotFound();
                return Results.Json(new { requestId });
            });

            // Live progress + terminal result of an in-flight pull.
            app.MapGet("/local/dex/pull-status", (HttpRequest request) =>
            {
                var requestId = request.Query["requestId"].ToString();
                if (string.IsNullOrEmpty(requestId)) return Results.BadRequest();
                var state = DexRequestStore.GetState(requestId);
                if (state == null) return Results.Json(new { done = true, gone = true });
                var obj = new JsonObject
                {
                    ["done"] = state.Done,
                    ["cancelled"] = state.Cancelled
                };
                if (state.Progress != null) obj["progress"] = JsonSerializer.SerializeToNode(state.Progress.Value);
                if (state.Result != null) obj["result"] = JsonSerializer.SerializeToNode(state.Result.Value);
                return Results.Json(obj);
            });

            // Ask the phone to abort an in-flight pull.
            app.MapPost("/local/dex/pull-cancel", (HttpRequest request) =>
            {
                var requestId = request.Query["requestId"].ToString();
                if (string.IsNullOrEmpty(requestId)) return Results.BadRequest();
                var state = DexRequestStore.GetState(requestId);
                if (state == null) return Results.NotFound();
                DexRequestStore.Cancel(requestId);
                if (!string.IsNullOrEmpty(state.Fingerprint))
                {
                    var cancel = JsonSerializer.Serialize(new { type = "pull-cancel", data = new { requestId } },
                        new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                    _ = WebSocketConnectionManager.SendAsync(state.Fingerprint, cancel, requireVerified: false);
                }
                return Results.Ok();
            });

            app.MapPost("/local/dex/grant-folder", async (HttpRequest request) =>
            {
                var body = await request.ReadFromJsonAsync<JsonObject>();
                var ip = body?["ip"]?.GetValue<string>() ?? request.Query["ip"].ToString();
                if (string.IsNullOrEmpty(ip)) return Results.BadRequest();
                if (!TrySendDexRequest(ip, "grant-shared-folder", null, out var requestId)) return Results.NotFound();
                var reply = await DexRequestStore.WaitAsync(requestId, 190);
                return reply == null ? Results.NotFound() : Results.Json(reply);
            });

            _ = Task.Run(StartTcpServerAsync);
        }

        /// <summary>
        /// Resolves the phone by LAN IP and forwards a File Explorer request over the WebSocket.
        /// Registers a pending request, records the phone's fingerprint (for cancels) and returns
        /// its requestId. Only verified (paired / same-email) phones are eligible — file browsing
        /// must never reach an untrusted device.
        /// </summary>
        private static bool TrySendDexRequest(string ip, string type, JsonObject? extra, out string requestId)
        {
            requestId = "";
            var dev = DiscoveryBackgroundService.Devices.Values.FirstOrDefault(d => d.Ip == ip);
            if (dev == null || dev.Info == null) return false;
            var fp = dev.Info.Fingerprint;

            requestId = DexRequestStore.NewPending(type);
            var state = DexRequestStore.GetState(requestId);
            if (state != null) state.Fingerprint = fp;

            var data = new JsonObject { ["requestId"] = requestId };
            if (extra != null)
            {
                foreach (var kv in extra)
                {
                    if (kv.Value != null) data[kv.Key] = kv.Value.DeepClone();
                }
            }
            var payload = new JsonObject { ["type"] = type, ["data"] = data };
            var json = payload.ToJsonString();

            return WebSocketConnectionManager.SendAsync(fp, json, requireVerified: true).GetAwaiter().GetResult();
        }

        /// <summary>Generates a PIN + token, pushes a pair-prompt over the WebSocket and shows the PIN on the PC.</summary>
        /// <returns>The PIN if the prompt was delivered, otherwise an empty string.</returns>
        public static async Task<string> PushPairPromptAsync(string targetFp, string statusIp)
        {
            try
            {
                OutboundPairingStatus[statusIp] = "Pending";
                var pin = new Random().Next(10000, 99999).ToString();
                var token = Guid.NewGuid().ToString("N");
                IdentityManager.SavePairedToken(targetFp, token);
                var reqDto = new PairRequestDto
                {
                    Alias = Environment.MachineName,
                    Fingerprint = IdentityManager.Fingerprint,
                    Pin = pin,
                    Token = token
                };
                var payload = new { type = "pair-prompt", data = reqDto };
                var json = JsonSerializer.Serialize(payload, new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                if (await WebSocketConnectionManager.SendAsync(targetFp, json))
                {
                    PendingPairPins[statusIp] = new Models.PendingPairAttempt
                    {
                        Fingerprint = targetFp,
                        Pin = pin,
                        Alias = reqDto.Alias,
                        CreatedAt = DateTime.UtcNow
                    };
                    ShowPairPinToast(pin, targetFp);
                    return pin;
                }
                OutboundPairingStatus[statusIp] = "Failed"; // No active WebSocket connection
                return "";
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[PAIR-PUSH] Failed to push pair-prompt: {ex.Message}");
                OutboundPairingStatus[statusIp] = "Failed";
                return "";
            }
        }

        private static void ShowPairPinToast(string pin, string targetFp)
        {
            try
            {
                var alias = DiscoveryBackgroundService.Devices.TryGetValue(targetFp, out var dev)
                    && !string.IsNullOrEmpty(dev.Info.Alias) ? dev.Info.Alias : "your device";
                string toastXmlString = $@"<toast duration='long'>
                    <visual>
                        <binding template='ToastGeneric'>
                            <text>DeX Pairing PIN</text>
                            <text>Enter {pin} on {alias}</text>
                        </binding>
                    </visual>
                </toast>";
                var xmlDoc = new XmlDocument();
                xmlDoc.LoadXml(toastXmlString);
                ToastNotificationManager.CreateToastNotifier("DeX").Show(new ToastNotification(xmlDoc));
            }
            catch { }
        }

        /// <summary>Clears the stored PIN for a pairing attempt once it completes, is cancelled, or expires.</summary>
        public static void ClearPendingPair(string statusIp)
        {
            PendingPairPins.TryRemove(statusIp, out _);
        }

        private static async Task StartTcpServerAsync()
        {
            var listener = new TcpListener(IPAddress.Any, 53319);
            listener.Start();
            while (true)
            {
                try
                {
                    var client = await listener.AcceptTcpClientAsync();
                    _ = Task.Run(async () =>
                    {
                        try
                        {
                            using var stream = client.GetStream();
                            var buffer = new byte[36];
                            int read = await stream.ReadAsync(buffer, 0, 36);
                            if (read == 36)
                        {
                                var fileId = Encoding.UTF8.GetString(buffer);
                                if (HostedFiles.TryGetValue(fileId, out var path) && File.Exists(path))
                                {
                                    using var fs = new FileStream(path, FileMode.Open, FileAccess.Read);
                                    await fs.CopyToAsync(stream, 81920);
                                }
                            }
                        }
                        catch { }
                        finally { client.Close(); }
                    });
                }
                catch { }
            }
        }
    }
}
