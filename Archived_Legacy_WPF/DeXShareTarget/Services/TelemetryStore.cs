using System.Collections.Concurrent;

namespace DeXShareTarget.Services
{
    /// <summary>Latest telemetry reported by connected devices (fingerprint -> battery / WiFi).</summary>
    public static class TelemetryStore
    {
        public static readonly ConcurrentDictionary<string, int> BatteryByFingerprint = new();
        public static readonly ConcurrentDictionary<string, (string? Ssid, int Rssi)> WifiByFingerprint = new();

        public static void SetBattery(string fingerprint, int battery)
        {
            BatteryByFingerprint[fingerprint] = battery;
        }

        public static int? GetBattery(string fingerprint)
        {
            return BatteryByFingerprint.TryGetValue(fingerprint, out var battery) ? battery : (int?)null;
        }

        public static void SetWifi(string fingerprint, string? ssid, int rssi)
        {
            WifiByFingerprint[fingerprint] = (ssid, rssi);
        }

        public static (string? Ssid, int Rssi)? GetWifi(string fingerprint)
        {
            return WifiByFingerprint.TryGetValue(fingerprint, out var wifi) ? wifi : null;
        }

        public static void Remove(string fingerprint)
        {
            BatteryByFingerprint.TryRemove(fingerprint, out _);
            WifiByFingerprint.TryRemove(fingerprint, out _);
        }
    }
}
