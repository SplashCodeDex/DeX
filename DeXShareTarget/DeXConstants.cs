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
        /// <summary>HTTPS / HTTP/1.1 + WebSocket port (Kestrel, TCP 53317).</summary>
        public const int HttpsPort = 53317;

        /// <summary>HTTP/3 (QUIC) port (Kestrel, UDP 53316).</summary>
        public const int QuicPort = 53316;

        /// <summary>Unencrypted localhost-only control API port used by the PowerShell GUI.</summary>
        public const int LocalApiPort = 53318;

        /// <summary>Plain-TCP fallback transfer port (hosted file pulls).</summary>
        public const int TcpFallbackPort = 53319;

        /// <summary>Localhost base URL for the control API.</summary>
        public const string LocalApiBase = "http://127.0.0.1:53318";

        /// <summary>Where received files and pulled phone files are saved.</summary>
        public static string DownloadsFolder =>
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "DeX");
    }
}
