using System;
using System.IO;

namespace DeXShareTarget
{
    /// <summary>
    /// Single source of truth for the protocol ports and well-known paths shared across the
    /// C# engine (Kestrel endpoints, TCP fallback) so they never drift apart.
    /// </summary>
    public static class DeXConstants
    {
    /// <summary>HTTPS / HTTP/1.1 + WebSocket port (Kestrel, TCP 48424).</summary>
    public static int HttpsPort { get; set; } = 48424;

    /// <summary>HTTP/3 (QUIC) port (Kestrel, UDP 48423).</summary>
    public static int QuicPort { get; set; } = 48423;

    /// <summary>Unencrypted localhost-only control API port used by the PowerShell GUI.</summary>
    public const int LocalApiPort = 28425;

    /// <summary>Plain-TCP fallback transfer port (hosted file pulls).</summary>
    public static int TcpFallbackPort { get; set; } = 48426;

    /// <summary>Fixed UDP multicast discovery port (must match Android's DeXPorts.HTTPS = 48424).</summary>
    public const int DiscoveryPort = 48424;

    /// <summary>Localhost base URL for the control API.</summary>
    public static string LocalApiBase => $"http://127.0.0.1:{LocalApiPort}";

        /// <summary>Where received files and pulled phone files are saved.</summary>
        public static string DownloadsFolder =>
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "DeX");
    }
}
