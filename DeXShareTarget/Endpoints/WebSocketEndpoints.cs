using System;
using System.IO;
using System.Linq;
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
        // Mirrors Android's WifiInfo.RSSI_INVALID
        private const int WifiInfoRssiInvalid = -127;

        // Task 9: PIN Brute-Force Attack rate limiting dictionary
        private static readonly System.Collections.Concurrent.ConcurrentDictionary<string, (int count, long resetAt)> PairRequestLimits = new();

        // Same-email devices currently connected (fingerprint -> alias); powers the phone roster
        public static readonly System.Collections.Concurrent.ConcurrentDictionary<string, string> SameEmailAliases = new();

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
                // Devices signed in with the SAME email are automatically trusted: their
                // identity hash is the bearer token, verified and persisted permanently.
                bool verified = (IdentityManager.PairedFingerprints.Contains(fingerprint) &&
                    IdentityManager.PairedTokens.TryGetValue(fingerprint, out var expectedToken) && expectedToken == token)
                    || IdentityManager.IsIdentityToken(token);

                if (!verified && IdentityManager.IsIdentityToken(token))
                {
                    IdentityManager.SavePairedDevice(fingerprint);
                    IdentityManager.SavePairedToken(fingerprint, token!);
                    verified = true;
                    Console.WriteLine($"[WS] Device {fingerprint} auto-trusted via email identity");
                }

                // Roster membership: same-email devices are visible to each other for direct transfers
                if (verified && IdentityManager.IsIdentityToken(token) && !string.IsNullOrEmpty(alias))
                {
                    SameEmailAliases[fingerprint] = alias;
                }

                using var webSocket = await context.WebSockets.AcceptWebSocketAsync();
                WebSocketConnectionManager.AddSocket(fingerprint, webSocket, verified);
                var clientIp = context.Connection.RemoteIpAddress?.ToString() ?? "";
                IdentityManager.UpdateLastSeen(fingerprint);

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

                // Push our known public IP so the phone can auto-configure WAN (no manual entry needed)
                var publicAddress = LocalSendServer.PublicAddress;
                if (!string.IsNullOrEmpty(publicAddress))
                {
                try
                {
                    var push = JsonSerializer.Serialize(new { type = "public-address", data = new { address = publicAddress } },
                        new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                    await WebSocketConnectionManager.SendAsync(fingerprint, push, requireVerified: false);
                }
                catch { }
            }

            try
            {
                var trustCheck = JsonSerializer.Serialize(new { type = "trust-check", data = new { isTrusted = verified, fingerprint = IdentityManager.Fingerprint } },
                    new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                await WebSocketConnectionManager.SendAsync(fingerprint, trustCheck, requireVerified: false);
            }
            catch { }

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
                            await HandleIncomingMessageAsync(fingerprint, alias, clientIp, text);
                        }
                        else if (result.MessageType == WebSocketMessageType.Binary)
                        {
                            // Screen-mirror JPEG frames; reassemble fragmented chunks
                            using var stream = new MemoryStream();
                            stream.Write(buffer, 0, result.Count);
                            while (!result.EndOfMessage && webSocket.State == WebSocketState.Open)
                            {
                                result = await webSocket.ReceiveAsync(new ArraySegment<byte>(buffer), CancellationToken.None);
                                if (result.MessageType != WebSocketMessageType.Binary) break;
                                stream.Write(buffer, 0, result.Count);
                            }
                            // Orphan streams (no active window for this phone) are told to stop
                            if (!MirrorWindowHost.PushFrame(fingerprint, stream.ToArray()))
                            {
                                var stop = JsonSerializer.Serialize(new { type = "mirror-stop", data = new { } },
                                    new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                                await WebSocketConnectionManager.SendAsync(fingerprint, stop);
                            }
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
                    TelemetryStore.Remove(fingerprint);
                    SameEmailAliases.TryRemove(fingerprint, out _);
                    // The phone is gone: never leave a frozen mirror window behind
                    MirrorWindowHost.Stop(fingerprint);
                    
                    // Task 12: Clear any pending pairing prompts if the socket drops mid-pairing
                    LocalSendEndpoints.ClearPendingPair(fingerprint);
                    if (LocalSendEndpoints.OutboundPairingStatus.TryGetValue(fingerprint, out var status) && status == "Pending")
                    {
                        LocalSendEndpoints.OutboundPairingStatus[fingerprint] = "Failed";
                    }
                    
                    if (webSocket.State != WebSocketState.Closed && webSocket.State != WebSocketState.Aborted)
                    {
                        await webSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", CancellationToken.None);
                    }
                }
            });
        }

        private static async Task HandleIncomingMessageAsync(string fingerprint, string alias, string clientIp, string text)
        {
            try
            {
                using var doc = JsonDocument.Parse(text);
                var root = doc.RootElement;
                var type = root.TryGetProperty("type", out var t) ? t.GetString() : null;
                if (type == "resolve-endpoint" && root.TryGetProperty("data", out var resData))
                {
                    if (!WebSocketConnectionManager.IsVerified(fingerprint)) return;
                    // Phone A wants to send directly to phone B: hand each side the other's public endpoint
                    var target = resData.TryGetProperty("targetFingerprint", out var tf) ? tf.GetString() : null;
                    var options = new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };
                    if (!string.IsNullOrEmpty(target) && LocalSendEndpoints.PunchEndpoints.TryGetValue(target, out var targetEp))
                    {
                        var info = JsonSerializer.Serialize(new { type = "endpoint-info", data = new { targetFingerprint = target, ip = targetEp.Ip, port = targetEp.Port } }, options);
                        await WebSocketConnectionManager.SendAsync(fingerprint, info);

                        if (LocalSendEndpoints.PunchEndpoints.TryGetValue(fingerprint, out var requesterEp))
                        {
                            var peer = JsonSerializer.Serialize(new { type = "peer-endpoint", data = new { peerFingerprint = fingerprint, ip = requesterEp.Ip, port = requesterEp.Port } }, options);
                            await WebSocketConnectionManager.SendAsync(target, peer);
                        }
                    }
                    else
                    {
                        var missing = JsonSerializer.Serialize(new { type = "endpoint-info", data = new { targetFingerprint = target ?? "", ip = "", port = 0 } }, options);
                        await WebSocketConnectionManager.SendAsync(fingerprint, missing);
                    }
                }
                else if (type == "device-roster")
                {
                    if (!WebSocketConnectionManager.IsVerified(fingerprint)) return;
                    // Same-email devices only (the phone's "my devices" list over WAN)
                    var devices = SameEmailAliases
                        .Where(kv => kv.Key != fingerprint)
                        .Select(kv => new
                        {
                            fingerprint = kv.Key,
                            alias = kv.Value,
                            deviceType = DiscoveryBackgroundService.Devices.TryGetValue(kv.Key, out var d) && d.Info != null
                                ? d.Info.DeviceType ?? "mobile"
                                : "mobile"
                        })
                        .ToList();
                    var roster = JsonSerializer.Serialize(new { type = "device-roster", data = new { devices } },
                        new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                    await WebSocketConnectionManager.SendAsync(fingerprint, roster);
                }
                else if (type == "relay-transfer" && root.TryGetProperty("data", out var relayData))
                {
                    // Punch failed on the sender side: host the uploaded session files here and
                    // push them to the target device, which pulls them over QUIC/HTTP
                    var target = relayData.TryGetProperty("targetFingerprint", out var rt) ? rt.GetString() : null;
                    var sessionId = relayData.TryGetProperty("sessionId", out var rs) ? rs.GetString() : null;
                    var options = new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };

                    var ok = false;
                    var reason = "no files";
                    if (!string.IsNullOrEmpty(target) && !string.IsNullOrEmpty(sessionId) &&
                        LocalSendEndpoints.RelaySessionFiles.TryGetValue(sessionId, out var relayFiles) && relayFiles.Count > 0)
                    {
                        var senderAlias = LocalSendEndpoints.RelaySessionAliases.TryGetValue(sessionId, out var a) ? a : "Device";
                        // Recreate the folder structure the sender uploaded (relative to Downloads/DeX)
                        string downloadsFolder = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile) + "\\Downloads\\DeX";
                        var pairs = relayFiles.Select(f => (f.Path, Path.GetRelativePath(downloadsFolder, f.Path))).ToList();
                        ok = await RelayService.HostAndPushAsync(target, pairs, senderAlias);
                        if (!ok) reason = "target offline";
                    }

                    var relayReply = JsonSerializer.Serialize(new { type = ok ? "relay-started" : "relay-error", data = new { pushed = ok, reason } }, options);
                    await WebSocketConnectionManager.SendAsync(fingerprint, relayReply);
                }
                else if (type == "telemetry" && root.TryGetProperty("data", out var telemetryData))
                {
                    // Phone reports battery + WiFi over the WebSocket so the PC can show them
                    // for every connected device (no ADB query required)
                    var battery = telemetryData.TryGetProperty("battery", out var b) ? b.GetInt32() : -1;
                    if (battery >= 0)
                    {
                        TelemetryStore.SetBattery(fingerprint, battery);
                    }

                    var ssid = telemetryData.TryGetProperty("wifiSsid", out var s) ? s.GetString() : null;
                    var rssi = telemetryData.TryGetProperty("wifiRssi", out var r) ? r.GetInt32() : WifiInfoRssiInvalid;
                    if (!string.IsNullOrEmpty(ssid) || rssi != WifiInfoRssiInvalid)
                    {
                        TelemetryStore.SetWifi(fingerprint, string.IsNullOrEmpty(ssid) ? null : ssid, rssi);
                    }
                }
                else if (type == "mirror-denied")
                {
                    // The user declined screen sharing: close the mirror window immediately
                    MirrorWindowHost.Stop(fingerprint);
                }
                else if (type == "pin-digit-entered" && root.TryGetProperty("data", out var pinData))
                {
                    var count = pinData.TryGetProperty("digitCount", out var dc) ? dc.GetInt32() : 0;
                    LocalSendEndpoints.PendingPairDigitCount[fingerprint] = count;
                }
                else if (type == "pair-request")
                {
                    // Task 9: Add a fast, in-memory rate-limiter (max 5 attempts per 5 minutes)
                    var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                    var limit = PairRequestLimits.GetOrAdd(clientIp, _ => (0, now + 300000));
                    if (now > limit.resetAt) limit = (0, now + 300000);
                    limit.count++;
                    PairRequestLimits[clientIp] = limit;
                    
                    if (limit.count > 5)
                    {
                        Console.WriteLine($"[WS] Rate limited pair-request from {clientIp}");
                        return;
                    }

                    // Phone-initiated pairing: generate a PIN and push the prompt back to the phone
                    var pin = await LocalSendEndpoints.PushPairPromptAsync(fingerprint, clientIp);
                    if (string.IsNullOrEmpty(pin))
                    {
                        LocalSendEndpoints.OutboundPairingStatus[fingerprint] = "Failed";
                    }
                    Console.WriteLine($"[WS] Pairing requested by {fingerprint}");
                }
                else if (type == "unpair")
                {
                    IdentityManager.RemovePairedDevice(fingerprint);
                    WebSocketConnectionManager.Unverify(fingerprint);
                    LocalSendEndpoints.OutboundPairingStatus[fingerprint] = "Cancelled";
                    Console.WriteLine($"[WS] Device {fingerprint} requested unpair");
                    
                    // Terminate the connection immediately so the unverified socket doesn't linger as a ghost
                    await WebSocketConnectionManager.DisconnectAsync(fingerprint);
                }
                else if (type == "trust-check" && root.TryGetProperty("data", out var trustData))
                {
                    var isTrustedByPhone = trustData.TryGetProperty("isTrusted", out var b) && b.GetBoolean();
                    if (!isTrustedByPhone && IdentityManager.PairedFingerprints.Contains(fingerprint))
                    {
                        Console.WriteLine($"[WS] Phone {fingerprint} reported we are not trusted. Downgrading local trust.");
                        IdentityManager.RemovePairedDevice(fingerprint);
                        WebSocketConnectionManager.Unverify(fingerprint);
                        
                        var unpairMsg = JsonSerializer.Serialize(new { type = "unpair", data = new { fingerprint = IdentityManager.Fingerprint } },
                            new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                        await WebSocketConnectionManager.SendAsync(fingerprint, unpairMsg, requireVerified: false);
                        await WebSocketConnectionManager.DisconnectAsync(fingerprint);
                    }
                }
                else if (type == "pair-response" && root.TryGetProperty("data", out var data))
                {
                    var accepted = data.TryGetProperty("accepted", out var a) && a.GetBoolean();
                    // Read the pending attempt's token BEFORE clearing it: the token is only
                    // persisted now, on acceptance (PushPairPromptAsync deliberately does not
                    // save it upfront — see there).
                    var pendingToken = LocalSendEndpoints.PendingPairPins.TryGetValue(fingerprint, out var pending)
                        ? pending.Token
                        : null;
                    LocalSendEndpoints.ClearPendingPair(fingerprint);
                    if (accepted)
                    {
                        IdentityManager.SavePairedDevice(fingerprint);
                        if (!string.IsNullOrEmpty(pendingToken))
                        {
                            IdentityManager.SavePairedToken(fingerprint, pendingToken);
                        }
                        WebSocketConnectionManager.MarkVerified(fingerprint);
                        LocalSendEndpoints.OutboundPairingStatus[fingerprint] = "Accepted";
                        Console.WriteLine($"[WS] Pairing accepted by {fingerprint}");
                    }
                    else
                    {
                        LocalSendEndpoints.OutboundPairingStatus[fingerprint] = "Rejected";
                        Console.WriteLine($"[WS] Pairing rejected by {fingerprint}");
                    }
                }
                else if (type == "pull-progress" && root.TryGetProperty("data", out var progressData))
                {
                    // The phone reports live bytes while pushing files; surface it to the GUI.
                    var requestId = progressData.TryGetProperty("requestId", out var rid) ? rid.GetString() : null;
                    if (!string.IsNullOrEmpty(requestId))
                    {
                        DexRequestStore.UpdateProgress(requestId, progressData.Clone());
                    }
                }
                else if (type == "quic-p2p-pull" && root.TryGetProperty("data", out var quicData))
                {
                    var port = quicData.TryGetProperty("port", out var p) ? p.GetInt32() : 0;
                    var senderAlias = quicData.TryGetProperty("alias", out var a) ? a.GetString() : "PC";
                    if (port > 0)
                    {
                        _ = Task.Run(async () =>
                        {
                            await QuicP2PClient.ReceiveAsync(clientIp, port, senderAlias ?? "PC");
                        });
                    }
                }
                else if ((type == "list-shared-folders-reply" || type == "browse-reply" ||
                          type == "pull-reply" || type == "grant-reply") &&
                         root.TryGetProperty("data", out var fileShareReply))
                {
                    // PC File Explorer: the phone answered a list/browse/pull/grant request.
                    // Route the reply to the pending /local/dex/* caller using the requestId.
                    var requestId = fileShareReply.TryGetProperty("requestId", out var rid) ? rid.GetString() : null;
                    if (!string.IsNullOrEmpty(requestId))
                    {
                        DexRequestStore.Complete(requestId, fileShareReply);
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
