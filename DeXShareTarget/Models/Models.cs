using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;
using DeXShareTarget.Services;

namespace DeXShareTarget.Models
{
    public class RegisterDto
    {
        [JsonPropertyName("alias")] public string Alias { get; set; } = Environment.MachineName;
        [JsonPropertyName("version")] public string Version { get; set; } = "2.0";
        [JsonPropertyName("deviceModel")] public string DeviceModel { get; set; } = "Unknown";
        [JsonPropertyName("deviceType")] public string DeviceType { get; set; } = "unknown";
        [JsonPropertyName("fingerprint")] public string Fingerprint { get; set; } = IdentityManager.Fingerprint;
        [JsonPropertyName("port")] public int Port { get; set; } = DeXConstants.HttpsPort;
        [JsonPropertyName("quicPort")] public int QuicPort { get; set; } = DeXConstants.QuicPort;
        [JsonPropertyName("tcpFallbackPort")] public int TcpFallbackPort { get; set; } = DeXConstants.TcpFallbackPort;
        [JsonPropertyName("protocol")] public string Protocol { get; set; } = "https";
        [JsonPropertyName("download")] public bool Download { get; set; } = true;
        [JsonPropertyName("identityHash")] public string? IdentityHash { get; set; }
        [JsonPropertyName("googleSub")] public string? GoogleSub { get; set; }
        [JsonPropertyName("battery")] public int? Battery => PcTelemetryService.GetTelemetry().Battery;
        [JsonPropertyName("isCharging")] public bool IsCharging => PcTelemetryService.GetTelemetry().IsCharging;
        [JsonPropertyName("wifiBand")] public string WifiBand => PcTelemetryService.GetTelemetry().WifiBand;
        [JsonPropertyName("wifiSsid")] public string? WifiSsid => PcTelemetryService.GetTelemetry().Ssid;
    }

    public class PrepareUploadRequestDto
    {
        [JsonPropertyName("info")] public RegisterDto Info { get; set; } = new();
        [JsonPropertyName("files")] public Dictionary<string, FileDto> Files { get; set; } = new();
    }

    public class FileDto
    {
        [JsonPropertyName("id")] public string Id { get; set; } = "";
        [JsonPropertyName("fileName")] public string FileName { get; set; } = "";
        [JsonPropertyName("size")] public long Size { get; set; }
        [JsonPropertyName("fileType")] public string FileType { get; set; } = "";
        [JsonPropertyName("partialHash")] public string? PartialHash { get; set; }
        [JsonPropertyName("relativePath")] public string? RelativePath { get; set; }
    }

    public class PrepareUploadResponseDto
    {
        [JsonPropertyName("sessionId")] public string SessionId { get; set; } = "";
        [JsonPropertyName("files")] public Dictionary<string, string> Files { get; set; } = new();
    }

    public class PairRequestDto
    {
        [JsonPropertyName("alias")] public string Alias { get; set; } = "";
        [JsonPropertyName("fingerprint")] public string Fingerprint { get; set; } = "";
        [JsonPropertyName("pin")] public string Pin { get; set; } = "";
        [JsonPropertyName("token")] public string? Token { get; set; }
    }

    public class DiscoveredDevice
    {
        public string Ip { get; set; } = "";
        public RegisterDto Info { get; set; } = new();
        public long LastSeen { get; set; }
        public bool IsPaired { get; set; }
        public bool IsAutoTrusted { get; set; }
    }

    public class PendingPairAttempt
    {
        public string Fingerprint { get; set; } = "";
        public string Pin { get; set; } = "";
        public string Alias { get; set; } = "";
        public string? Token { get; set; }
        public string Ip { get; set; } = "";
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    }

    public class VStreamManifestDto
    {
        [JsonPropertyName("sessionId")] public string SessionId { get; set; } = Guid.NewGuid().ToString();
        [JsonPropertyName("info")] public RegisterDto Info { get; set; } = new();
        [JsonPropertyName("totalSize")] public long TotalSize { get; set; }
        [JsonPropertyName("totalFiles")] public int TotalFiles { get; set; }
        [JsonPropertyName("items")] public List<VStreamItemDto> Items { get; set; } = new();
    }

    public class VStreamItemDto
    {
        [JsonPropertyName("id")] public string Id { get; set; } = Guid.NewGuid().ToString();
        [JsonPropertyName("relativePath")] public string RelativePath { get; set; } = "";
        [JsonPropertyName("size")] public long Size { get; set; }
        [JsonPropertyName("partialHash")] public string? PartialHash { get; set; }
        [JsonPropertyName("offset")] public long Offset { get; set; }
    }
}
