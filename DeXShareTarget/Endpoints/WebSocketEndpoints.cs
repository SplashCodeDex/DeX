using System;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using DeXShareTarget.Services;

namespace DeXShareTarget.Endpoints
{
    public static class WebSocketEndpoints
    {
        public static void MapWebSocketEndpoints(this WebApplication app)
        {
            app.Map("/ws", async context =>
            {
                if (!context.WebSockets.IsWebSocketRequest)
                {
                    context.Response.StatusCode = 400;
                    return;
                }

                var fingerprint = context.Request.Query["fingerprint"].ToString();
                var alias = context.Request.Query["alias"].ToString();
                var token = context.Request.Query["token"].ToString();

                if (string.IsNullOrEmpty(fingerprint))
                {
                    context.Response.StatusCode = 400;
                    return;
                }

                // Paired devices must present their pairing token to be "verified" (eligible for
                // file pushes). Devices without a valid token still connect so they can receive
                // pair-prompts and re-establish trust; their socket stays unverified until the
                // pair-response arrives.
                bool verified = IdentityManager.PairedFingerprints.Contains(fingerprint) &&
                    IdentityManager.PairedTokens.TryGetValue(fingerprint, out var expectedToken) && expectedToken == token;

                using var webSocket = await context.WebSockets.AcceptWebSocketAsync();
                WebSocketConnectionManager.AddSocket(fingerprint, webSocket, verified);
                var clientIp = context.Connection.RemoteIpAddress?.ToString() ?? "";

                // Mark the device as online immediately if it isn't already
                if (!DiscoveryBackgroundService.Devices.ContainsKey(fingerprint))
                {
                    // Add basic placeholder device info, the multicast will fill in the rest later
                    DiscoveryBackgroundService.Devices[fingerprint] = new Models.DiscoveredDevice
                    {
                        Ip = clientIp,
                        LastSeen = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                        IsPaired = IdentityManager.PairedFingerprints.Contains(fingerprint),
                        Info = new Models.RegisterDto { Fingerprint = fingerprint, Alias = alias }
                    };
                }
                else
                {
                    DiscoveryBackgroundService.Devices[fingerprint].LastSeen = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                }

                try
                {
                    var buffer = new byte[1024 * 4];
                    while (webSocket.State == WebSocketState.Open)
                    {
                        var result = await webSocket.ReceiveAsync(new ArraySegment<byte>(buffer), CancellationToken.None);
                        if (result.MessageType == WebSocketMessageType.Close)
                        {
                            break;
                        }
                        if (result.MessageType == WebSocketMessageType.Text)
                        {
                            var text = Encoding.UTF8.GetString(buffer, 0, result.Count);
                            await HandleIncomingMessageAsync(fingerprint, clientIp, text);
                        }
                    }
                }
                catch (Exception)
                {
                    // Socket closed or error
                }
                finally
                {
                    WebSocketConnectionManager.RemoveSocket(fingerprint);
                    if (webSocket.State != WebSocketState.Closed && webSocket.State != WebSocketState.Aborted)
                    {
                        await webSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", CancellationToken.None);
                    }
                }
            });
        }

        private static async Task HandleIncomingMessageAsync(string fingerprint, string clientIp, string text)
        {
            try
            {
                using var doc = JsonDocument.Parse(text);
                var root = doc.RootElement;
                var type = root.TryGetProperty("type", out var t) ? t.GetString() : null;
                if (type == "pair-request")
                {
                    // Phone-initiated pairing: generate a PIN and push the prompt back to the phone
                    var pin = await LocalSendEndpoints.PushPairPromptAsync(fingerprint, clientIp);
                    if (string.IsNullOrEmpty(pin))
                    {
                        LocalSendEndpoints.OutboundPairingStatus[clientIp] = "Failed";
                    }
                    Console.WriteLine($"[WS] Pairing requested by {fingerprint}");
                }
                else if (type == "unpair")
                {
                    IdentityManager.RemovePairedDevice(fingerprint);
                    WebSocketConnectionManager.Unverify(fingerprint);
                    LocalSendEndpoints.OutboundPairingStatus[clientIp] = "Cancelled";
                    Console.WriteLine($"[WS] Device {fingerprint} requested unpair");
                }
                else if (type == "pair-response" && root.TryGetProperty("data", out var data))
                {
                    var accepted = data.TryGetProperty("accepted", out var a) && a.GetBoolean();
                    if (accepted)
                    {
                        IdentityManager.SavePairedDevice(fingerprint);
                        WebSocketConnectionManager.MarkVerified(fingerprint);
                        LocalSendEndpoints.OutboundPairingStatus[clientIp] = "Accepted";
                        Console.WriteLine($"[WS] Pairing accepted by {fingerprint}");
                    }
                    else
                    {
                        LocalSendEndpoints.OutboundPairingStatus[clientIp] = "Rejected";
                        Console.WriteLine($"[WS] Pairing rejected by {fingerprint}");
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WS] Failed to parse incoming message: {ex.Message}");
            }
        }
    }
}
