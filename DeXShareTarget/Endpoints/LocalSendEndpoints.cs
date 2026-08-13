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
using System.Threading.Tasks;
using Windows.Data.Xml.Dom;
using Windows.UI.Notifications;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using DeXShareTarget.Models;
using DeXShareTarget.Services;

namespace DeXShareTarget.Endpoints
{
    // Split across feature files for maintainability:
    //   LocalSendEndpoints.cs            - shared state, endpoint dispatcher, pairing + TCP helpers
    //   LocalSendEndpoints.Share.cs      - LocalSend v2 transfer endpoints
    //   LocalSendEndpoints.Device.cs     - device discovery + pairing endpoints
    //   LocalSendEndpoints.Control.cs    - clipboard / mirror / alias / QR / DND endpoints
    //   LocalSendEndpoints.Settings.cs   - settings / identity endpoints
    //   LocalSendEndpoints.FileExplorer.cs - PC File Explorer endpoints
    public static partial class LocalSendEndpoints
    {
        public static ConcurrentDictionary<string, string> HostedFiles = new();
        public static (string Text, DateTime At)? LastPhoneClipboard => ClipboardService.LastPhoneClipboard;
        public static bool ClipboardSyncEnabled
        {
            get => ClipboardService.IsSyncEnabled;
            set => ClipboardService.IsSyncEnabled = value;
        }
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

        /// <summary>Registers every endpoint exposed by the desktop engine.</summary>
        public static void MapLocalSendEndpoints(this WebApplication app)
        {
            app.MapShareEndpoints();
            app.MapLocalDeviceEndpoints();
            app.MapLocalControlEndpoints();
            app.MapLocalSettingsEndpoints();
            app.MapLocalFileExplorerEndpoints();

            _ = Task.Run(StartTcpServerAsync);
        }

        /// <summary>Generates a PIN + token, pushes a pair-prompt over the WebSocket and shows the PIN on the PC.</summary>
        /// <returns>The PIN if the prompt was delivered, otherwise an empty string.</returns>
        public static async Task<string> PushPairPromptAsync(string targetFp, string statusIp)
        {
            try
            {
                // Pending-pair attempts and their status are keyed by FINGERPRINT, never by
                // IP: a phone whose DHCP lease changes mid-pairing must still resolve, and two
                // phones behind the same NAT must not collide.
                OutboundPairingStatus[targetFp] = "Pending";
                var pin = new Random().Next(10000, 99999).ToString();
                var token = Guid.NewGuid().ToString("N");
                // NOTE: the token is deliberately NOT persisted here. It is only saved when
                // the phone accepts (pair-response). Persisting it upfront would clobber an
                // already-paired device's valid token on every re-pair attempt — if that
                // attempt is then cancelled or times out, the phone still holds the old token
                // while the PC has the new one, silently de-trusting the device.
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
                    PendingPairPins[targetFp] = new Models.PendingPairAttempt
                    {
                        Fingerprint = targetFp,
                        Pin = pin,
                        Alias = reqDto.Alias,
                        Token = token,
                        Ip = statusIp,
                        CreatedAt = DateTime.UtcNow
                    };
                    ShowPairPinToast(pin, targetFp);
                    return pin;
                }
                OutboundPairingStatus[targetFp] = "Failed"; // No active WebSocket connection
                return "";
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[PAIR-PUSH] Failed to push pair-prompt: {ex.Message}");
                OutboundPairingStatus[targetFp] = "Failed";
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
                            <text>Enter PIN {pin} on {alias}</text>
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
        public static void ClearPendingPair(string fingerprint)
        {
            PendingPairPins.TryRemove(fingerprint, out _);
        }

        private static async Task StartTcpServerAsync()
        {
            var listener = new TcpListener(IPAddress.Any, DeXConstants.TcpFallbackPort);
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
