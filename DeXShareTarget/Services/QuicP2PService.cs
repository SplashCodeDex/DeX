using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Quic;
using System.Net.Security;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace DeXShareTarget.Services
{
    public record TransferProgress(long BytesSent, long TotalBytes, string CurrentFile, int DoneFiles, int TotalFiles);

    public static class QuicP2PService
    {
        public static async Task<(int Port, Func<Task> WaitForCompletion)> HostAsync(
            IReadOnlyList<string> filePaths,
            X509Certificate2 cert,
            IProgress<TransferProgress>? progress = null,
            CancellationToken ct = default)
        {
            var listenerOptions = new QuicListenerOptions
            {
                ListenEndPoint = new IPEndPoint(IPAddress.Any, 0),
                ApplicationProtocols = new List<SslApplicationProtocol> { new SslApplicationProtocol("dex-p2p") },
                ConnectionOptionsCallback = (connection, sslInfo, cancellationToken) =>
                {
                    return ValueTask.FromResult(new QuicServerConnectionOptions
                    {
                        DefaultStreamErrorCode = 0x01,
                        DefaultCloseErrorCode = 0x02,
                        ServerAuthenticationOptions = new SslServerAuthenticationOptions
                        {
                            ApplicationProtocols = new List<SslApplicationProtocol> { new SslApplicationProtocol("dex-p2p") },
                            ServerCertificate = cert
                        }
                    });
                }
            };

            var listener = await QuicListener.ListenAsync(listenerOptions, ct);
            int port = listener.LocalEndPoint.Port;

            Func<Task> waitForCompletion = async () =>
            {
                try
                {
                    using var connection = await listener.AcceptConnectionAsync(ct);
                    await listener.DisposeAsync();

                    long totalBytes = 0;
                    foreach (var path in filePaths)
                    {
                        if (File.Exists(path)) totalBytes += new FileInfo(path).Length;
                    }

                    long globalSent = 0;
                    int doneFiles = 0;

                    foreach (var path in filePaths)
                    {
                        if (ct.IsCancellationRequested) break;
                        if (!File.Exists(path)) continue;

                        var fi = new FileInfo(path);
                        string currentFile = fi.Name;

                        progress?.Report(new TransferProgress(globalSent, totalBytes, currentFile, doneFiles, filePaths.Count));

                        using var stream = await connection.OpenOutboundStreamAsync(QuicStreamType.Bidirectional, ct);

                        byte[] nameBytes = Encoding.UTF8.GetBytes(currentFile);
                        byte[] header = new byte[10 + nameBytes.Length];
                        
                        long fileSize = fi.Length;
                        for (int i = 0; i < 8; i++) header[i] = (byte)(fileSize >> (56 - (i * 8)));
                        
                        header[8] = (byte)(nameBytes.Length >> 8);
                        header[9] = (byte)(nameBytes.Length);
                        
                        Array.Copy(nameBytes, 0, header, 10, nameBytes.Length);

                        await stream.WriteAsync(header, ct);

                        using var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read, 81920, FileOptions.SequentialScan);
                        byte[] buffer = new byte[81920];
                        int read;
                        
                        while ((read = await fs.ReadAsync(buffer, 0, buffer.Length, ct)) > 0)
                        {
                            await stream.WriteAsync(buffer.AsMemory(0, read), ct);
                            globalSent += read;
                            progress?.Report(new TransferProgress(globalSent, totalBytes, currentFile, doneFiles, filePaths.Count));
                        }
                        
                        stream.CompleteWrites();
                        doneFiles++;
                    }
                    
                    progress?.Report(new TransferProgress(globalSent, totalBytes, "Complete", doneFiles, filePaths.Count));
                }
                catch (Exception)
                {
                    await listener.DisposeAsync();
                    throw;
                }
            };

            return (port, waitForCompletion);
        }
    }
}
