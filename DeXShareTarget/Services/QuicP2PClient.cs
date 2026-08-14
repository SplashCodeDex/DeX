using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Quic;
using System.Net.Security;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace DeXShareTarget.Services
{
    public static class QuicP2PClient
    {
        public static async Task ReceiveAsync(string senderIp, int port, string alias)
        {
            var options = new QuicClientConnectionOptions
            {
                DefaultStreamErrorCode = 0x01,
                DefaultCloseErrorCode = 0x02,
                RemoteEndPoint = new IPEndPoint(IPAddress.Parse(senderIp), port),
                ClientAuthenticationOptions = new SslClientAuthenticationOptions
                {
                    ApplicationProtocols = new List<SslApplicationProtocol> { new SslApplicationProtocol("dex-p2p") },
                    // Accept any cert since it's an ephemeral P2P TLS cert generated on the fly
                    RemoteCertificateValidationCallback = (sender, cert, chain, errors) => true
                }
            };

            Console.WriteLine($"[QUIC-P2P] Connecting to {senderIp}:{port} (Alias: {alias})...");

            try
            {
                await using var connection = await QuicConnection.ConnectAsync(options);
                Console.WriteLine($"[QUIC-P2P] Connected to {alias}. Waiting for streams...");

                var downloadDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "DeX");
                Directory.CreateDirectory(downloadDir);

                var tasks = new List<Task>();

                while (true)
                {
                    QuicStream stream;
                    try
                    {
                        stream = await connection.AcceptInboundStreamAsync();
                    }
                    catch (QuicException ex) when (ex.QuicError == QuicError.ConnectionAborted || ex.QuicError == QuicError.ConnectionTimeout)
                    {
                        break; // Connection closed by sender (all files sent)
                    }
                    catch (Exception)
                    {
                        break; // Other error, exit loop
                    }

                    tasks.Add(Task.Run(async () =>
                    {
                        try
                        {
                            await using (stream)
                            {
                                // Read 10-byte header
                                byte[] header = new byte[10];
                                int headerRead = 0;
                                while (headerRead < 10)
                                {
                                    int read = await stream.ReadAsync(header.AsMemory(headerRead, 10 - headerRead));
                                    if (read == 0) return; // Unexpected EOF
                                    headerRead += read;
                                }

                                long fileSize = 0;
                                for (int i = 0; i < 8; i++)
                                {
                                    fileSize = (fileSize << 8) | header[i];
                                }

                                int nameLength = (header[8] << 8) | header[9];

                                byte[] nameBytes = new byte[nameLength];
                                int nameRead = 0;
                                while (nameRead < nameLength)
                                {
                                    int read = await stream.ReadAsync(nameBytes.AsMemory(nameRead, nameLength - nameRead));
                                    if (read == 0) return; // Unexpected EOF
                                    nameRead += read;
                                }

                                string relativePath = Encoding.UTF8.GetString(nameBytes).Replace('\\', '/');
                                
                                // Clean up the path segments to prevent directory traversal
                                var segments = relativePath.Split('/');
                                var safeSegments = new List<string>();
                                foreach (var seg in segments)
                                {
                                    if (string.IsNullOrWhiteSpace(seg) || seg == "." || seg == "..") continue;
                                    safeSegments.Add(string.Join("_", seg.Split(Path.GetInvalidFileNameChars())));
                                }

                                if (safeSegments.Count == 0) safeSegments.Add("unnamed_file");

                                string sanitizedRelative = Path.Combine(safeSegments.ToArray());
                                string targetPath = Path.GetFullPath(Path.Combine(downloadDir, sanitizedRelative));

                                // Double check against traversal escaping the base dir
                                if (!targetPath.StartsWith(downloadDir, StringComparison.OrdinalIgnoreCase))
                                {
                                    throw new UnauthorizedAccessException("Directory traversal attempt blocked.");
                                }

                                Directory.CreateDirectory(Path.GetDirectoryName(targetPath)!);

                                // Avoid overwriting by appending numbers if needed
                                int counter = 1;
                                string originalTarget = targetPath;
                                while (File.Exists(targetPath))
                                {
                                    string ext = Path.GetExtension(originalTarget);
                                    string name = Path.GetFileNameWithoutExtension(originalTarget);
                                    string dir = Path.GetDirectoryName(originalTarget)!;
                                    targetPath = Path.Combine(dir, $"{name} ({counter}){ext}");
                                    counter++;
                                }

                                Console.WriteLine($"[QUIC-P2P] Receiving {relativePath} ({fileSize} bytes) -> {targetPath}");

                                using var fs = new FileStream(targetPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, FileOptions.Asynchronous);
                                byte[] buffer = new byte[81920];
                                int bytesRead;
                                long totalReceived = 0;

                                while (totalReceived < fileSize && (bytesRead = await stream.ReadAsync(buffer.AsMemory(0, buffer.Length))) > 0)
                                {
                                    await fs.WriteAsync(buffer.AsMemory(0, bytesRead));
                                    totalReceived += bytesRead;
                                }
                                
                                Console.WriteLine($"[QUIC-P2P] Finished receiving {relativePath}");
                            }
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[QUIC-P2P] Stream error: {ex.Message}");
                        }
                    }));
                }

                await Task.WhenAll(tasks);
                Console.WriteLine($"[QUIC-P2P] Transfer from {alias} complete.");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[QUIC-P2P] Failed to connect/receive from {alias}: {ex.Message}");
            }
        }
    }
}
