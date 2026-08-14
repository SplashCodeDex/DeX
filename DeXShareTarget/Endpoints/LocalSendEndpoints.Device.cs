using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using DeXShareTarget.Models;
using DeXShareTarget.Services;

namespace DeXShareTarget.Endpoints
{
    public static partial class LocalSendEndpoints
    {
        private static bool IsLanAddress(string ip)
        {
            // A phone on the same LAN reports a private-range address. WAN devices
            // (same-email relay) expose their public address instead, so this cleanly
            // separates LAN browsing from WAN-only history.
            if (string.IsNullOrEmpty(ip)) return false;
            if (ip == "127.0.0.1" || ip == "::1") return true;
            if (!System.Net.IPAddress.TryParse(ip, out var addr)) return false;
            if (System.Net.IPAddress.IsLoopback(addr)) return true;
            if (addr.AddressFamily != System.Net.Sockets.AddressFamily.InterNetwork) return false;
            var b = addr.GetAddressBytes();
            return b[0] == 10
                || (b[0] == 172 && b[1] >= 16 && b[1] <= 31)
                || (b[0] == 192 && b[1] == 168)
                || (b[0] == 169 && b[1] == 254); // link-local fallback
        }

        /// <summary>Registers the device-discovery and pairing endpoints used by the PowerShell GUI.</summary>
        public static void MapLocalDeviceEndpoints(this WebApplication app)
        {
            // Local API for PowerShell to read discovered devices
            app.MapGet("/local/devices", () => 
            {
            // Clean up stale
            var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            foreach (var k in DiscoveryBackgroundService.Devices.Keys)
            {
                // Keep devices with a live WebSocket (WAN same-email phones stay listed
                // even though nothing re-discovers them over mDNS) — they must remain
                // clickable for Transfer History.
                if (now - DiscoveryBackgroundService.Devices[k].LastSeen > 10000 &&
                    !WebSocketConnectionManager.HasConnection(k))
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
                        // File Explorer eligibility signals: the phone must have a live
                        // WebSocket AND be on the LAN. WAN (same-email) devices get
                        // Transfer History only — remote SAF browsing is LAN-scoped.
                        IsOnline = WebSocketConnectionManager.HasConnection(d.Info.Fingerprint),
                        IsLan = IsLanAddress(d.Ip),
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
                    
                    var response = await http.GetAsync($"https://{ip}:{DeXConstants.HttpsPort}/api/localsend/v2/info");
                    if (!response.IsSuccessStatusCode)
                    {
                        response = await http.GetAsync($"http://{ip}:{DeXConstants.HttpsPort}/api/localsend/v2/info");
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
                        DiscoveryBackgroundService.AddOrUpdateDevice(dev);
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

            app.MapPost("/local/unpair", async (HttpRequest request) => 
            {
                var fp = request.Query["fingerprint"].ToString();
                if (!string.IsNullOrEmpty(fp))
                {
                    IdentityManager.RemovePairedDevice(fp);
                    WebSocketConnectionManager.Unverify(fp);
                    try
                    {
                        var msg = JsonSerializer.Serialize(new { type = "unpair", data = new { fingerprint = IdentityManager.Fingerprint } },
                            new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                        await WebSocketConnectionManager.SendAsync(fp, msg, requireVerified: false);
                        await WebSocketConnectionManager.DisconnectAsync(fp);
                    }
                    catch { }
                    return Results.Ok();
                }
                return Results.BadRequest();
            });
            app.MapGet("/local/pair-status", (HttpRequest request) => 
            {
                // Resolve by fingerprint (preferred) or by IP (legacy callers).
                var fp = request.Query["fingerprint"].ToString();
                var targetFp = fp;
                if (!string.IsNullOrEmpty(targetFp) && OutboundPairingStatus.TryGetValue(targetFp, out var status))
                {
                    var digitCount = PendingPairDigitCount.TryGetValue(targetFp, out var count) ? count : 0;
                    return Results.Json(new { status, digitCount });
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
                return Results.Json(new { ip = entry.Value.Ip, fingerprint = entry.Value.Fingerprint, pin = entry.Value.Pin, alias = entry.Value.Alias });
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

            app.MapPost("/local/pair-cancel", async (HttpRequest request) => 
            {
                var targetIp = request.Query["ip"].ToString();
                var targetFp = request.Query["fingerprint"].ToString();
                var attemptFp = targetFp;
                if (!string.IsNullOrEmpty(attemptFp))
                {
                    OutboundPairingStatus[attemptFp] = "Cancelled";
                    ClearPendingPair(attemptFp);
                    // Tell the phone the PC cancelled so its PIN dialog closes immediately
                    // instead of counting down its own 60s and only then rejecting.
                    try
                    {
                        var cancelMsg = JsonSerializer.Serialize(new { type = "pair-cancelled", data = new { } },
                            new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                        await WebSocketConnectionManager.SendAsync(attemptFp, cancelMsg);
                    }
                    catch { }
                }
                // NOTE: deliberately does NOT call IdentityManager.RemovePairedDevice.
                // Cancelling a pairing attempt must never revoke an already-established
                // trust (e.g. a re-pair that the user cancels or that times out would
                // silently drop the device from "Your Devices"). Explicit revocation is
                // the /local/unpair endpoint ("Forget Device") or the phone's unpair
                // message — not a cancel.
                return Results.Ok();
            });
        }
    }
}
