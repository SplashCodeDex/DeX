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
        }
    }
}
