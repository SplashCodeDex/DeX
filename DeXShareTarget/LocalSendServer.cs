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

            // Load the persisted public address so the phone can be told about it even before UPnP probes
            try
            {
                var persisted = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "DeX", "public_address.txt");
                if (File.Exists(persisted)) PublicAddress = File.ReadAllText(persisted).Trim();
            }
            catch { }

            // Auto-configure the router (UPnP) so WAN works without manual port forwarding.
            // The public IP must be known BEFORE the certificate is created so its SAN covers
            // it; the probe is hard-capped so startup is never blocked by a slow router.
            try
            {
                using var upnpTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(3));
                await UpnpPortForward.ProbePublicIpAsync(upnpTimeout.Token);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[UPNP] Public IP probe failed: {ex.Message}");
            }

            ServerCert = GetOrCreateServerCertificate();
            // The address the current certificate covers; used to detect runtime IP changes
            var certifiedAddress = PublicAddress;

            // Port mappings are not needed for the certificate, so run them in the background
            // and re-run hourly so a changed LAN IP (DHCP) never silently breaks WAN.
            _ = Task.Run(async () =>
            {
                while (true)
                {
                    try { await UpnpPortForward.ConfigureAsync(CancellationToken.None); }
                    catch (Exception ex) { Console.WriteLine($"[UPNP] Auto-config failed: {ex.Message}"); }

                    // The public IP changed at runtime: the running certificate no longer covers
                    // it and QUIC over WAN would fail TLS. Restart the server so a fresh
                    // certificate (with the new SAN) is created; phones reconnect automatically.
                    if (!string.IsNullOrEmpty(PublicAddress) && PublicAddress != certifiedAddress)
                    {
                        certifiedAddress = PublicAddress;
                        try
                        {
                            Console.WriteLine("[CERT] Public IP changed; restarting server to renew certificate");
                            await App.StopAsync();
                            ServerCert = GetOrCreateServerCertificate();
                            await App.StartAsync();
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[CERT] Server restart after IP change failed: {ex.Message}");
                        }
                    }

                    await Task.Delay(TimeSpan.FromHours(1));
                }
            });

            builder.WebHost.ConfigureKestrel(options =>
            {
                // Endpoint 1: HTTP/1.1 on TCP 48424 (Does not block UDP 48424)
                // HTTP/1.1 only: OkHttp WebSockets (the Android client) cannot run over HTTP/2,
                // and every other client (Ktor CIO, PowerShell, .NET HttpClient) is HTTP/1.1 anyway.
                options.ListenAnyIP(DeXConstants.HttpsPort, listenOptions =>
                {
                    listenOptions.Protocols = Microsoft.AspNetCore.Server.Kestrel.Core.HttpProtocols.Http1;
                    listenOptions.UseHttps(ServerCert);
                });
                
                // Endpoint 2: HTTP/3 (QUIC) on UDP 48423 (Exclusive QUIC socket)
                options.ListenAnyIP(DeXConstants.QuicPort, listenOptions =>
                {
                    listenOptions.Protocols = Microsoft.AspNetCore.Server.Kestrel.Core.HttpProtocols.Http3;
                    listenOptions.UseHttps(ServerCert);
                });
                
                // Add a local unencrypted port for PowerShell GUI to query discovered devices easily
                options.ListenLocalhost(DeXConstants.LocalApiPort);
            });

            builder.Services.AddHostedService<DiscoveryBackgroundService>();

            App = builder.Build();

            // Custom Middleware to rewrite Alt-Svc to point to the dedicated HTTP/3 port (48423)
            App.Use(async (context, next) =>
            {
                context.Response.OnStarting(() =>
                {
                    context.Response.Headers["Alt-Svc"] = $"h3=\":{DeXConstants.QuicPort}\"; ma=86400";
                    return Task.CompletedTask;
                });
                await next();
            });

            App.UseWebSockets();

            App.MapLocalSendEndpoints();
            App.MapWebSocketEndpoints();

            await App.StartAsync();
        }

        /// <summary>The public (WAN) address the phone should reach this PC at; pushed to phones on connect.</summary>
        public static string? PublicAddress { get; private set; }

        /// <summary>Persists the UPnP-detected public address so the next certificate covers it.</summary>
        public static void SetPublicAddress(string address)
        {
            try
            {
                PublicAddress = address.Trim();
                var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "DeX");
                Directory.CreateDirectory(dir);
                File.WriteAllText(Path.Combine(dir, "public_address.txt"), PublicAddress);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[CERT] Failed to persist public address: {ex.Message}");
            }
        }

        /// <summary>
        /// Loads (or creates) a stable self-signed certificate persisted under %APPDATA%\DeX.
        /// A stable cert is required so Android's Cronet/QUIC client can trust it once and
        /// reuse it across app restarts. SANs cover the machine name and current LAN IPs so
        /// hostname verification succeeds whether the phone connects by hostname or by IP.
        /// The configured public (WAN) address is added as a SAN too.
        /// </summary>
        private static X509Certificate2 GetOrCreateServerCertificate()
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "DeX");
            Directory.CreateDirectory(dir);
            var certPath = Path.Combine(dir, "dexcert.pfx");
            var publicAddressPath = Path.Combine(dir, "public_address.txt");
            var publicAddress = File.Exists(publicAddressPath) ? File.ReadAllText(publicAddressPath).Trim() : "";

            if (File.Exists(certPath))
            {
                try
                {
                    var existing = X509CertificateLoader.LoadPkcs12(File.ReadAllBytes(certPath), "dex-local", X509KeyStorageFlags.Exportable);
                    var coversPublicAddress = string.IsNullOrEmpty(publicAddress) ||
                        existing.Extensions.OfType<X509SubjectAlternativeNameExtension>()
                            .Any(e => e.EnumerateDnsNames().Contains(publicAddress) ||
                                      e.EnumerateIPAddresses().Any(ip => ip.ToString() == publicAddress));
                    // Regenerate if expiring soon, the public address changed, or it was signed
                    // by the old self-signed scheme instead of the bundled DeX root CA
                    if (existing.NotAfter > DateTimeOffset.UtcNow.AddDays(7) &&
                        coversPublicAddress &&
                        string.Equals(existing.Issuer, "CN=DeX Root CA", StringComparison.OrdinalIgnoreCase))
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
            if (!string.IsNullOrEmpty(publicAddress))
            {
                if (IPAddress.TryParse(publicAddress, out var publicIp))
                {
                    san.AddIpAddress(publicIp);
                }
                else
                {
                    san.AddDnsName(publicAddress);
                }
            }
            request.CertificateExtensions.Add(san.Build());
            request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
            request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));

            // Signed by the bundled DeX root CA so Android's Cronet trusts it automatically.
            // NOTE: CertificateRequest.Create(issuer, ...) returns a cert WITHOUT the private
            // key attached — exporting it directly would produce a keyless PFX and Kestrel
            // would refuse to start ("server mode SSL must use a certificate with the
            // associated private key"). CopyWithPrivateKey reattaches the request's RSA key.
            using var ca = LoadEmbeddedCa();
            var signed = request.Create(
                ca,
                DateTimeOffset.UtcNow.AddDays(-1),
                DateTimeOffset.UtcNow.AddYears(1),
                Guid.NewGuid().ToByteArray());
            var leaf = signed.CopyWithPrivateKey(rsa);
            File.WriteAllBytes(certPath, leaf.Export(X509ContentType.Pfx, "dex-local"));
            return X509CertificateLoader.LoadPkcs12(File.ReadAllBytes(certPath), "dex-local", X509KeyStorageFlags.Exportable);
        }

        private static X509Certificate2 LoadEmbeddedCa()
        {
            using var stream = typeof(LocalSendServer).Assembly.GetManifestResourceStream("DeXShareTarget.dex_ca.pfx")
                ?? throw new InvalidOperationException("Embedded DeX root CA missing");
            using var ms = new MemoryStream();
            stream.CopyTo(ms);
            return X509CertificateLoader.LoadPkcs12(ms.ToArray(), "dex-ca-password", X509KeyStorageFlags.Exportable);
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
