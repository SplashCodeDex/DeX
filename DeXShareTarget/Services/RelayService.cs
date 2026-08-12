using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using DeXShareTarget.Endpoints;
using DeXShareTarget.Models;

namespace DeXShareTarget.Services
{
    /// <summary>
    /// Stream-through relay fallback for phone-to-phone transfers when direct NAT punching
    /// fails (symmetric NAT / CGNAT): hosts the uploaded files on this PC's Kestrel and
    /// pushes a prepare-upload to the target device, which pulls them over QUIC/HTTP.
    /// The PC never stores beyond the transfer — files are the phone's upload copies.
    /// </summary>
    public static class RelayService
    {
        /// <summary>Hosts [files] (path + relativePath pairs) and pushes a prepare-upload to [targetFingerprint]. Returns whether the push was delivered.</summary>
        public static async Task<bool> HostAndPushAsync(string targetFingerprint, IReadOnlyList<(string Path, string RelativePath)> files, string senderAlias)
        {
            if (string.IsNullOrEmpty(targetFingerprint) || files == null || files.Count == 0) return false;

            var fileMap = new Dictionary<string, object>();
            foreach (var (path, relativePath) in files.Where(f => File.Exists(f.Path)))
            {
                var fi = new FileInfo(path);
                var fileId = Guid.NewGuid().ToString();
                var pullToken = Guid.NewGuid().ToString();

                LocalSendEndpoints.HostedFiles[fileId] = path;
                LocalSendEndpoints.HostedFileTokens[fileId] = pullToken;
                LocalSendEndpoints.HostedFileLastAccess[fileId] = DateTime.UtcNow;

                fileMap[fileId] = new
                {
                    id = fileId,
                    fileName = fi.Name,
                    size = fi.Length,
                    fileType = "application/octet-stream",
                    token = pullToken,
                    relativePath = string.IsNullOrEmpty(relativePath) ? null : relativePath
                };
            }
            if (fileMap.Count == 0) return false;

            var prepareReq = new
            {
                info = new RegisterDto 
                { 
                    Alias = senderAlias, 
                    DeviceModel = "PC", 
                    DeviceType = "desktop", 
                    Fingerprint = IdentityManager.Fingerprint, 
                    Port = DeXConstants.HttpsPort, 
                    QuicPort = DeXConstants.QuicPort,
                    TcpFallbackPort = DeXConstants.TcpFallbackPort,
                    Protocol = "localsend", 
                    Download = false 
                },
                files = fileMap
            };
            var json = JsonSerializer.Serialize(new { type = "prepare-upload", data = prepareReq },
                new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });

            // Sliding TTL cleanup: files expire 5 minutes after their last pull request
            var hostedIds = fileMap.Keys.ToList();
            _ = Task.Run(async () =>
            {
                while (true)
                {
                    await Task.Delay(TimeSpan.FromMinutes(1));
                    var now = DateTime.UtcNow;
                    var stale = hostedIds.Where(id =>
                        !LocalSendEndpoints.HostedFileLastAccess.TryGetValue(id, out var last) ||
                        (now - last) > TimeSpan.FromMinutes(5)).ToList();
                    foreach (var id in stale)
                    {
                        LocalSendEndpoints.HostedFiles.TryRemove(id, out _);
                        LocalSendEndpoints.HostedFileTokens.TryRemove(id, out _);
                        LocalSendEndpoints.HostedFileLastAccess.TryRemove(id, out _);
                    }
                    if (!hostedIds.Any(id => LocalSendEndpoints.HostedFiles.ContainsKey(id))) break;
                }
            });

            return await WebSocketConnectionManager.SendAsync(targetFingerprint, json, requireVerified: true);
        }

        /// <summary>Hosts [files] via DeX-VStream virtual manifest streaming to [targetFingerprint].</summary>
        public static async Task<bool> HostAndPushVStreamAsync(string targetFingerprint, IReadOnlyList<(string Path, string RelativePath)> files, string senderAlias)
        {
            if (string.IsNullOrEmpty(targetFingerprint) || files == null || files.Count == 0) return false;

            var vItems = new List<object>();
            long totalSize = 0;
            var hostedIds = new List<string>();

            foreach (var (path, relativePath) in files.Where(f => File.Exists(f.Path)))
            {
                var fi = new FileInfo(path);
                var itemId = Guid.NewGuid().ToString();
                var pullToken = Guid.NewGuid().ToString();

                LocalSendEndpoints.HostedFiles[itemId] = path;
                LocalSendEndpoints.HostedFileTokens[itemId] = pullToken;
                LocalSendEndpoints.HostedFileLastAccess[itemId] = DateTime.UtcNow;
                hostedIds.Add(itemId);
                totalSize += fi.Length;

                vItems.Add(new
                {
                    id = itemId,
                    relativePath = string.IsNullOrEmpty(relativePath) ? fi.Name : relativePath,
                    size = fi.Length,
                    token = pullToken
                });
            }
            if (vItems.Count == 0) return false;

            var vManifestReq = new
            {
                info = new RegisterDto
                {
                    Alias = senderAlias,
                    DeviceModel = "PC",
                    DeviceType = "desktop",
                    Fingerprint = IdentityManager.Fingerprint,
                    Port = DeXConstants.HttpsPort,
                    QuicPort = DeXConstants.QuicPort,
                    TcpFallbackPort = DeXConstants.TcpFallbackPort,
                    Protocol = "vstream",
                    Download = false
                },
                totalSize = totalSize,
                totalFiles = vItems.Count,
                items = vItems
            };

            var json = JsonSerializer.Serialize(new { type = "vstream-prepare", data = vManifestReq },
                new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });

            // Sliding TTL cleanup
            _ = Task.Run(async () =>
            {
                while (true)
                {
                    await Task.Delay(TimeSpan.FromMinutes(1));
                    var now = DateTime.UtcNow;
                    var stale = hostedIds.Where(id =>
                        !LocalSendEndpoints.HostedFileLastAccess.TryGetValue(id, out var last) ||
                        (now - last) > TimeSpan.FromMinutes(5)).ToList();
                    foreach (var id in stale)
                    {
                        LocalSendEndpoints.HostedFiles.TryRemove(id, out _);
                        LocalSendEndpoints.HostedFileTokens.TryRemove(id, out _);
                        LocalSendEndpoints.HostedFileLastAccess.TryRemove(id, out _);
                    }
                    if (!hostedIds.Any(id => LocalSendEndpoints.HostedFiles.ContainsKey(id))) break;
                }
            });

            return await WebSocketConnectionManager.SendAsync(targetFingerprint, json, requireVerified: true);
        }
    }
}
