using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
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
    public static partial class LocalSendEndpoints
    {
        // Transfer session state shared between prepare-upload and upload. Created once at
        // startup (the endpoints are registered a single time in MapLocalSendEndpoints).
        private static readonly ConcurrentDictionary<string, PrepareUploadRequestDto> ActiveUploadSessions = new();
        private static readonly ConcurrentDictionary<string, int> ActiveUploadSessionsProgress = new();

        /// <summary>Registers the LocalSend v2 transfer endpoints (info, register, prepare-upload, upload, punch, download).</summary>
        public static void MapShareEndpoints(this WebApplication app)
        {
            app.MapGet("/api/localsend/v2/info", () => Results.Json(new RegisterDto 
            { 
                Alias = Environment.MachineName,
                Fingerprint = IdentityManager.Fingerprint,
                IdentityHash = IdentityManager.IdentityHash,
                GoogleSub = string.IsNullOrEmpty(IdentityManager.GoogleSub) ? null : IdentityManager.GoogleSub,
                DeviceModel = Environment.MachineName,
                DeviceType = "desktop",
                Port = DeXConstants.HttpsPort,
                QuicPort = DeXConstants.QuicPort,
                TcpFallbackPort = DeXConstants.TcpFallbackPort,
                Protocol = "https",
                Version = "2.0"
            }));
            
            app.MapPost("/api/localsend/v2/register", (RegisterDto req) => 
            {
                return Results.Json(new { sessionId = Guid.NewGuid().ToString() });
            });

            // Reset the hosted-file map at startup, exactly as the original single-method registration did.
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

                var sessionId = Guid.NewGuid().ToString();
                ActiveUploadSessions[sessionId] = req;

                var resFiles = new Dictionary<string, string>();
                string downloadsFolder = DeXConstants.DownloadsFolder;
                Directory.CreateDirectory(downloadsFolder);
                foreach (var kvp in req.Files)
                {
                    resFiles[kvp.Key] = Guid.NewGuid().ToString(); // Token for the file
                }
                return Results.Json(new PrepareUploadResponseDto { SessionId = sessionId, Files = resFiles });
            });

            app.MapPost("/api/localsend/v2/upload", async (HttpRequest request) =>
            {
                var sessionId = request.Query["sessionId"].ToString();
                var fileId = request.Query["fileId"].ToString();
                var token = request.Query["token"].ToString(); // Token unused in minimal impl

                if (!ActiveUploadSessions.TryGetValue(sessionId, out var sessionReq)) return Results.BadRequest();
                if (!sessionReq.Files.TryGetValue(fileId, out var fileMeta)) return Results.BadRequest();

                string safeFileName = Path.GetFileName(fileMeta.FileName);
                if (string.IsNullOrEmpty(safeFileName)) safeFileName = "unnamed_file";
                
                // Folder bundles: recreate the relative path structure under Downloads/DeX
                string safeRelative = SanitizeRelativePath(fileMeta.RelativePath);
                
                string downloadsFolder = DeXConstants.DownloadsFolder;
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
                    var count = ActiveUploadSessionsProgress.AddOrUpdate(sessionId, 1, (_, v) => v + 1);
                    if (count == sessionReq.Files.Count)
                    {
                        ActiveUploadSessions.TryRemove(sessionId, out _);
                        ActiveUploadSessionsProgress.TryRemove(sessionId, out _);

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
        }
    }
}
