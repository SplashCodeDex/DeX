using System;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Xml.Linq;

namespace DeXShareTarget.Services
{
    /// <summary>
    /// Auto-configures the router via UPnP/IGD so WAN transfers work without manual port
    /// forwarding: maps TCP 53317 + UDP 53316 to this PC and records the public IP so the
    /// TLS certificate covers it. Every failure is silent — Thruflux remains the fallback.
    /// </summary>
    public static class UpnpPortForward
    {
        private const int SsdpPort = 1900;
        private const string SsdpMulticast = "239.255.255.250";
        private const int DeXHttpsPort = 53317;
        private const int DeXQuicPort = 53316;

        private static readonly string[] IgdServices =
        {
            "urn:schemas-upnp-org:service:WANIPConnection:2",
            "urn:schemas-upnp-org:service:WANIPConnection:1",
            "urn:schemas-upnp-org:service:WANPPPConnection:1"
        };

        private sealed record IgdInfo(string ControlUrl, string ServiceType, IPAddress RouterIp);

        /// <summary>Fast probe: discovers the IGD and stores the public IP for the certificate. Never throws.</summary>
        public static async Task ProbePublicIpAsync(CancellationToken ct)
        {
            var igd = await DiscoverIgdAsync(ct);
            if (igd == null) return;
            var publicIp = await GetExternalIpAsync(igd, ct);
            if (!string.IsNullOrEmpty(publicIp))
            {
                LocalSendServer.SetPublicAddress(publicIp);
                Console.WriteLine($"[UPNP] Public IP: {publicIp}");
            }
        }

        /// <summary>Full auto-config: public IP probe plus permanent port mappings for both DeX endpoints.</summary>
        public static async Task ConfigureAsync(CancellationToken ct)
        {
            var igd = await DiscoverIgdAsync(ct);
            if (igd == null)
            {
                Console.WriteLine("[UPNP] No UPnP Internet Gateway Device found; WAN needs manual port forwarding");
                return;
            }

            await AddPortMappingAsync(igd, DeXHttpsPort, "TCP", ct);
            await AddPortMappingAsync(igd, DeXQuicPort, "UDP", ct);

            var publicIp = await GetExternalIpAsync(igd, ct);
            if (!string.IsNullOrEmpty(publicIp))
            {
                LocalSendServer.SetPublicAddress(publicIp);
                Console.WriteLine($"[UPNP] Public IP: {publicIp}");
            }
        }

        private static async Task<IgdInfo?> DiscoverIgdAsync(CancellationToken ct)
        {
            using var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
            socket.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            socket.Bind(new IPEndPoint(IPAddress.Any, 0));
            socket.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.AddMembership,
                new MulticastOption(IPAddress.Parse(SsdpMulticast), IPAddress.Any));
            socket.ReceiveTimeout = 3000;

            const string search =
                "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";
            socket.SendTo(Encoding.ASCII.GetBytes(search), new IPEndPoint(IPAddress.Parse(SsdpMulticast), SsdpPort));

            var buffer = new byte[16384];
            var deadline = DateTime.UtcNow.AddSeconds(4);
            while (DateTime.UtcNow < deadline && !ct.IsCancellationRequested)
            {
                EndPoint remote = new IPEndPoint(IPAddress.Any, 0);
                int received;
                try { received = socket.ReceiveFrom(buffer, ref remote); }
                catch (SocketException) { break; }

                var text = Encoding.ASCII.GetString(buffer, 0, received);
                var location = text.Split('\n')
                    .Select(l => l.Trim())
                    .FirstOrDefault(l => l.StartsWith("LOCATION:", StringComparison.OrdinalIgnoreCase))?
                    .Substring("LOCATION:".Length).Trim();
                if (string.IsNullOrEmpty(location) || remote is not IPEndPoint routerEp) continue;

                var control = await FindIgdControlAsync(location, ct);
                if (control != null) return control with { RouterIp = routerEp.Address };
            }
            return null;
        }

        private static async Task<IgdInfo?> FindIgdControlAsync(string deviceDescriptionUrl, CancellationToken ct)
        {
            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(3) };
            string xml;
            try { xml = await http.GetStringAsync(deviceDescriptionUrl, ct); }
            catch { return null; }

            XNamespace ns = "urn:schemas-upnp-org:device-1-0";
            var doc = XDocument.Parse(xml);
            foreach (var service in doc.Descendants(ns + "service"))
            {
                var type = service.Element(ns + "serviceType")?.Value ?? "";
                var controlPath = service.Element(ns + "controlURL")?.Value ?? "";
                if (IgdServices.Contains(type) && !string.IsNullOrEmpty(controlPath))
                {
                    return new IgdInfo(ResolveUrl(deviceDescriptionUrl, controlPath), type, IPAddress.Loopback);
                }
            }
            return null;
        }

        private static async Task AddPortMappingAsync(IgdInfo igd, int port, string protocol, CancellationToken ct)
        {
            var localIp = GetLocalIpForRoute(igd.RouterIp);
            if (localIp == null) return;

            var body = $@"<?xml version=""1.0""?>
<s:Envelope xmlns:s=""http://schemas.xmlsoap.org/soap/envelope/"" s:encodingStyle=""http://schemas.xmlsoap.org/soap/encoding/"">
<s:Body>
<u:AddPortMapping xmlns:u=""{igd.ServiceType}"">
<NewRemoteHost></NewRemoteHost>
<NewExternalPort>{port}</NewExternalPort>
<NewProtocol>{protocol}</NewProtocol>
<NewInternalPort>{port}</NewInternalPort>
<NewInternalClient>{localIp}</NewInternalClient>
<NewEnabled>1</NewEnabled>
<NewPortMappingDescription>DeX {protocol} {port}</NewPortMappingDescription>
<NewLeaseDuration>0</NewLeaseDuration>
</u:AddPortMapping>
</s:Body>
</s:Envelope>";

            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(4) };
            using var request = new HttpRequestMessage(HttpMethod.Post, igd.ControlUrl);
            request.Headers.Add("SOAPACTION", $"\"{igd.ServiceType}#AddPortMapping\"");
            request.Content = new StringContent(body, Encoding.UTF8, "text/xml");
            try
            {
                var response = await http.SendAsync(request, ct);
                Console.WriteLine($"[UPNP] Port mapping {protocol} {port} -> {localIp}: {(response.IsSuccessStatusCode ? "OK" : $"HTTP {(int)response.StatusCode}")}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[UPNP] Port mapping {protocol} {port} failed: {ex.Message}");
            }
        }

        private static async Task<string?> GetExternalIpAsync(IgdInfo igd, CancellationToken ct)
        {
            var body = $@"<?xml version=""1.0""?>
<s:Envelope xmlns:s=""http://schemas.xmlsoap.org/soap/envelope/"" s:encodingStyle=""http://schemas.xmlsoap.org/soap/encoding/"">
<s:Body><u:GetExternalIPAddress xmlns:u=""{igd.ServiceType}""></u:GetExternalIPAddress></s:Body>
</s:Envelope>";

            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(4) };
            using var request = new HttpRequestMessage(HttpMethod.Post, igd.ControlUrl);
            request.Headers.Add("SOAPACTION", $"\"{igd.ServiceType}#GetExternalIPAddress\"");
            request.Content = new StringContent(body, Encoding.UTF8, "text/xml");
            try
            {
                var response = await http.SendAsync(request, ct);
                if (!response.IsSuccessStatusCode) return null;
                var xml = await response.Content.ReadAsStringAsync(ct);
                // Match by local name so the response namespace doesn't matter
                var address = XDocument.Parse(xml).Descendants()
                    .FirstOrDefault(e => e.Name.LocalName == "NewExternalIPAddress")?.Value?.Trim();
                return string.IsNullOrEmpty(address) || address == "0.0.0.0" ? null : address;
            }
            catch { return null; }
        }

        /// <summary>The LAN address of the interface that routes to the router.</summary>
        private static string? GetLocalIpForRoute(IPAddress routerIp)
        {
            try
            {
                using var probe = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
                probe.Connect(new IPEndPoint(routerIp, SsdpPort));
                return ((IPEndPoint)probe.LocalEndPoint!).Address.ToString();
            }
            catch { return null; }
        }

        private static string ResolveUrl(string baseUrl, string controlPath)
        {
            if (Uri.TryCreate(controlPath, UriKind.Absolute, out var absolute)) return absolute.ToString();
            var uri = new Uri(baseUrl);
            var path = controlPath.StartsWith("/") ? controlPath : uri.AbsolutePath.TrimEnd('/') + "/" + controlPath;
            return new Uri(uri, path).ToString();
        }
    }
}
