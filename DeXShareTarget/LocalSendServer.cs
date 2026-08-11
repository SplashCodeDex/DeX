using System;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Security;
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

            // Auto-update ports dynamically
            DeXConstants.HttpsPort = GetAvailableTcpPort();
            DeXConstants.QuicPort = GetAvailableUdpPort();
            DeXConstants.TcpFallbackPort = GetAvailableTcpPort();

            // Note: UPnP port mapping moved to the bottom of this method so it maps the correct dynamic ports

            builder.WebHost.ConfigureKestrel(options =>
            {
                // Endpoint 1: HTTP/1.1 on TCP 48424 (Does not block UDP 48424)
                // HTTP/1.1 only: OkHttp WebSockets (the Android client) cannot run over HTTP/2,
                // and every other client (Ktor CIO, PowerShell, .NET HttpClient) is HTTP/1.1 anyway.
                // The certificate is served through a selector so a runtime renewal (IP change)
                // applies to new connections without rebuilding the host.
                options.ListenAnyIP(DeXConstants.HttpsPort, listenOptions =>
                {
                    listenOptions.Protocols = Microsoft.AspNetCore.Server.Kestrel.Core.HttpProtocols.Http1;
                    listenOptions.UseHttps(httpsOptions =>
                    {
                        httpsOptions.ServerCertificateSelector = (_, _) => ServerCert;
                    });
                });
                
                // Endpoint 2: HTTP/3 (QUIC) on UDP 48423 (Exclusive QUIC socket)
                // QUIC requires a fixed certificate at listener setup (no per-connection selector),
                // so this endpoint uses the latest ServerCert and picks up a renewal on next start.
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

            // Port mappings are not needed for the certificate, so run them in the background
            // and re-run hourly so a changed LAN IP (DHCP) never silently breaks WAN.
            // This runs after Kestrel starts so it maps the newly assigned dynamic ports.
            _ = Task.Run(async () =>
            {
                while (true)
                {
                    try { await UpnpPortForward.ConfigureAsync(CancellationToken.None); }
                    catch (Exception ex) { Console.WriteLine($"[UPNP] Auto-config failed: {ex.Message}"); }

                    // The public IP or a LAN IP changed at runtime, so the current certificate's
                    // SANs no longer cover the addresses clients connect to and TLS would fail.
                    // Renew the certificate in place: the HTTP/1.1 endpoint serves the certificate
                    // through a per-connection selector, so new connections pick up the renewed
                    // certificate immediately — no server restart required.
                    if (ServerCert is not null && !CertCoversAddresses(ServerCert, PublicAddress))
                    {
                        try
                        {
                            Console.WriteLine("[CERT] Network address changed; renewing certificate");
                            ServerCert = GetOrCreateServerCertificate();
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[CERT] Certificate renewal failed: {ex.Message}");
                        }
                    }

                    await Task.Delay(TimeSpan.FromHours(1));
                }
            });
        }

        private static int GetAvailableTcpPort()
        {
            var l = new TcpListener(IPAddress.Loopback, 0);
            l.Start();
            int port = ((IPEndPoint)l.LocalEndpoint).Port;
            l.Stop();
            return port;
        }

        private static int GetAvailableUdpPort()
        {
            using var u = new UdpClient(new IPEndPoint(IPAddress.Any, 0));
            return ((IPEndPoint)u.Client.LocalEndPoint!).Port;
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
        /// Loads (or creates) a stable server certificate persisted under %APPDATA%\DeX.
        /// A stable cert is required so Android's Cronet/QUIC client can trust it once and
        /// reuse it across app restarts. SANs cover the machine name, current LAN IPs and the
        /// configured public (WAN) address, so hostname verification succeeds whether the
        /// phone connects by hostname, LAN IP or public address.
        /// </summary>
        /// <remarks>
        /// Hardening: an existing certificate is reused ONLY if it can actually serve TLS.
        /// Older builds persisted PFXs without a private key — such a file loads fine and
        /// passes every date/issuer check, then makes Kestrel refuse to start with
        /// "server mode SSL must use a certificate with the associated private key". The
        /// SslStreamCertificateContext.Create check below uses the exact API Kestrel calls,
        /// so a keyless/unsupported certificate is caught here and regenerated instead of
        /// crashing the server on the first launch.
        /// </remarks>
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
                    var usable = CanServeWithCert(existing);
                    var valid = existing.NotAfter > DateTimeOffset.UtcNow.AddDays(7);
                    var covers = CertCoversAddresses(existing, publicAddress);
                    var trustedIssuer = string.Equals(existing.Issuer, "CN=DeX Root CA", StringComparison.OrdinalIgnoreCase);
                    if (usable && valid && covers && trustedIssuer)
                    {
                        return existing;
                    }
                    LogCert($"Existing certificate rejected (servable={usable}, valid={valid}, coversAddresses={covers}, rootCaSigned={trustedIssuer}); regenerating");
                }
                catch (Exception ex)
                {
                    LogCert($"Existing certificate unreadable ({ex.Message}); regenerating");
                }
            }

            LogCert("Generating fresh server certificate");
            return CreateAndPersistServerCertificate(dir, certPath, publicAddress);
        }

        /// <summary>
        /// True if the certificate has a working private key that the TLS stack will accept.
        /// This calls SslStreamCertificateContext.Create — the exact API Kestrel uses when it
        /// starts HTTPS listeners — so a certificate that passes here is one Kestrel will serve.
        /// </summary>
        private static bool CanServeWithCert(X509Certificate2 cert)
        {
            try
            {
                // Probe with the exact API Kestrel uses; the context is GC-collected after the check.
                _ = SslStreamCertificateContext.Create(cert, null, offline: true);
                return true;
            }
            catch
            {
                return false;
            }
        }

        /// <summary>True if the certificate's SANs cover the machine name, every current LAN IPv4 and the public address.</summary>
        private static bool CertCoversAddresses(X509Certificate2 cert, string? publicAddress)
        {
            try
            {
                var san = cert.Extensions.OfType<X509SubjectAlternativeNameExtension>().FirstOrDefault();
                if (san is null) return false;
                var dnsNames = san.EnumerateDnsNames().ToHashSet(StringComparer.OrdinalIgnoreCase);
                var ipNames = san.EnumerateIPAddresses().Select(a => a.ToString()).ToHashSet(StringComparer.OrdinalIgnoreCase);

                foreach (var ip in GetLocalIPv4Addresses())
                {
                    if (!ipNames.Contains(ip)) return false;
                }
                if (!string.IsNullOrEmpty(publicAddress) && !dnsNames.Contains(publicAddress) && !ipNames.Contains(publicAddress))
                {
                    return false;
                }
                return true;
            }
            catch
            {
                return false;
            }
        }

        /// <summary>
        /// Creates a fresh certificate (signed by the bundled DeX root CA, or self-signed as a
        /// last-resort fallback if the embedded CA is unavailable), verifies the TLS stack will
        /// accept it, and persists it atomically so a crash mid-write can never leave a truncated
        /// PFX behind.
        /// </summary>
        private static X509Certificate2 CreateAndPersistServerCertificate(string dir, string certPath, string publicAddress)
        {
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

            // Preferred: signed by the bundled DeX root CA so Android's Cronet trusts it
            // automatically. If the embedded CA is missing (tampered/trimmed install), fall
            // back to a self-signed certificate so the server always starts; phones simply
            // prompt to trust it once.
            X509Certificate2 leaf;
            try
            {
                using var ca = LoadEmbeddedCa();
                // NOTE: CertificateRequest.Create(issuer, ...) returns a cert WITHOUT the private
                // key attached — exporting it directly would produce a keyless PFX and Kestrel
                // would refuse to start. CopyWithPrivateKey reattaches the request's RSA key.
                var signed = request.Create(ca, DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(1), Guid.NewGuid().ToByteArray());
                leaf = signed.CopyWithPrivateKey(rsa);
                signed.Dispose();
            }
            catch (Exception ex)
            {
                LogCert($"Root CA unavailable ({ex.Message}); using self-signed fallback");
                leaf = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(1));
            }

            if (!CanServeWithCert(leaf))
            {
                leaf.Dispose();
                throw new InvalidOperationException("Generated server certificate was rejected by the TLS stack");
            }

            // Persist atomically: write a temp file, then move it over the target. A crash
            // between the two steps leaves a valid old file (or a stray temp file), never a
            // corrupt PFX that loads but cannot serve TLS.
            var tmpPath = certPath + ".tmp";
            File.WriteAllBytes(tmpPath, leaf.Export(X509ContentType.Pfx, "dex-local"));
            File.Move(tmpPath, certPath, overwrite: true);
            leaf.Dispose();

            var reloaded = X509CertificateLoader.LoadPkcs12(File.ReadAllBytes(certPath), "dex-local", X509KeyStorageFlags.Exportable);
            if (!CanServeWithCert(reloaded))
            {
                throw new InvalidOperationException("Reloaded server certificate failed the TLS stack check");
            }
            LogCert("Server certificate persisted");
            return reloaded;
        }

        /// <summary>Persists certificate lifecycle events for field diagnostics (no secrets).</summary>
        private static void LogCert(string message)
        {
            try
            {
                var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "DeX");
                Directory.CreateDirectory(dir);
                File.AppendAllText(Path.Combine(dir, "cert.log"), $"[{DateTime.Now:O}] {message}\n");
            }
            catch { }
            Console.WriteLine($"[CERT] {message}");
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
