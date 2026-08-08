using System;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using DeXShareTarget.Services;
using DeXShareTarget.Endpoints;

namespace DeXShareTarget
{
    public static class LocalSendServer
    {
        public static WebApplication? App;
        public static X509Certificate2? ServerCert;

        public static async Task StartAsync()
        {
            IdentityManager.Initialize();
            var builder = WebApplication.CreateBuilder();
            
            // Silence Kestrel verbose logs
            builder.Logging.SetMinimumLevel(LogLevel.Warning);

            ServerCert = GetOrCreateServerCertificate();

            builder.WebHost.ConfigureKestrel(options =>
            {
                // Endpoint 1: HTTP/1.1 on TCP 53317 (Does not block UDP 53317)
                // HTTP/1.1 only: OkHttp WebSockets (the Android client) cannot run over HTTP/2,
                // and every other client (Ktor CIO, PowerShell, .NET HttpClient) is HTTP/1.1 anyway.
                options.ListenAnyIP(53317, listenOptions =>
                {
                    listenOptions.Protocols = Microsoft.AspNetCore.Server.Kestrel.Core.HttpProtocols.Http1;
                    listenOptions.UseHttps(ServerCert);
                });
                
                // Endpoint 2: HTTP/3 (QUIC) on UDP 53316 (Exclusive QUIC socket)
                options.ListenAnyIP(53316, listenOptions =>
                {
                    listenOptions.Protocols = Microsoft.AspNetCore.Server.Kestrel.Core.HttpProtocols.Http3;
                    listenOptions.UseHttps(ServerCert);
                });
                
                // Add a local unencrypted port for PowerShell GUI to query discovered devices easily
                options.ListenLocalhost(53318);
            });

            builder.Services.AddHostedService<DiscoveryBackgroundService>();

            App = builder.Build();

            // Custom Middleware to rewrite Alt-Svc to point to the dedicated HTTP/3 port (53316)
            App.Use(async (context, next) =>
            {
                context.Response.OnStarting(() =>
                {
                    context.Response.Headers["Alt-Svc"] = "h3=\":53316\"; ma=86400";
                    return Task.CompletedTask;
                });
                await next();
            });

            App.UseWebSockets();

            App.MapLocalSendEndpoints();
            App.MapWebSocketEndpoints();

            await App.StartAsync();
        }

        /// <summary>
        /// Loads (or creates) a stable self-signed certificate persisted under %APPDATA%\DeX.
        /// A stable cert is required so Android's Cronet/QUIC client can trust it once and
        /// reuse it across app restarts. SANs cover the machine name and current LAN IPs so
        /// hostname verification succeeds whether the phone connects by hostname or by IP.
        /// </summary>
        private static X509Certificate2 GetOrCreateServerCertificate()
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "DeX");
            Directory.CreateDirectory(dir);
            var certPath = Path.Combine(dir, "dexcert.pfx");

            if (File.Exists(certPath))
            {
                try
                {
                    var existing = X509CertificateLoader.LoadPkcs12(File.ReadAllBytes(certPath), "dex-local", X509KeyStorageFlags.Exportable);
                    // Regenerate if expiring soon
                    if (existing.NotAfter > DateTimeOffset.UtcNow.AddDays(7))
                    {
                        return existing;
                    }
                }
                catch { /* corrupt or expired, regenerate below */ }
            }

            using var rsa = RSA.Create(2048);
            var request = new CertificateRequest($"cn={Environment.MachineName}", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
            var san = new SubjectAlternativeNameBuilder();
            san.AddDnsName(Environment.MachineName);
            foreach (var ip in GetLocalIPv4Addresses())
            {
                san.AddIpAddress(IPAddress.Parse(ip));
            }
            request.CertificateExtensions.Add(san.Build());
            request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
            request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));

            var created = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(1));
            File.WriteAllBytes(certPath, created.Export(X509ContentType.Pfx, "dex-local"));
            return X509CertificateLoader.LoadPkcs12(File.ReadAllBytes(certPath), "dex-local", X509KeyStorageFlags.Exportable);
        }

        private static string[] GetLocalIPv4Addresses()
        {
            try
            {
                return NetworkInterface.GetAllNetworkInterfaces()
                    .Where(ni => ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                    .SelectMany(ni => ni.GetIPProperties().UnicastAddresses)
                    .Where(u => u.Address.AddressFamily == AddressFamily.InterNetwork)
                    .Select(u => u.Address.ToString())
                    .Distinct()
                    .ToArray();
            }
            catch
            {
                return Array.Empty<string>();
            }
        }
    }
}
