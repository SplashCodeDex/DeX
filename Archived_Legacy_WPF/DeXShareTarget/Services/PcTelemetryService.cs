using System;
using System.IO;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;

namespace DeXShareTarget.Services
{
    /// <summary>
    /// Serves real-time Windows PC battery percentage, AC power charging status, and Wi-Fi band telemetry.
    /// </summary>
    public static class PcTelemetryService
    {
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool GetSystemPowerStatus(out SYSTEM_POWER_STATUS sps);

        [StructLayout(LayoutKind.Sequential)]
        private struct SYSTEM_POWER_STATUS
        {
            public byte ACLineStatus;        // 0 = Offline (Battery), 1 = Online (AC Power), 255 = Unknown
            public byte BatteryFlag;         // 1 = High, 2 = Low, 4 = Critical, 8 = Charging, 128 = No battery (Desktop), 255 = Unknown
            public byte BatteryLifePercent;  // 0-100 or 255 = Unknown
            public byte SystemStatusFlag;
            public int BatteryLifeTime;
            public int BatteryFullLifeTime;
        }

        private static (int? Battery, bool IsCharging, string WifiBand, string? Ssid)? _cachedTelemetry;
        private static DateTime _lastFetch = DateTime.MinValue;
        private static readonly object _lock = new object();

        public static (int? Battery, bool IsCharging, string WifiBand, string? Ssid) GetTelemetry()
        {
            var cached = _cachedTelemetry;
            if (cached != null && (DateTime.UtcNow - _lastFetch).TotalSeconds < 2)
            {
                return cached.Value;
            }

            lock (_lock)
            {
                if (_cachedTelemetry != null && (DateTime.UtcNow - _lastFetch).TotalSeconds < 2)
                {
                    return _cachedTelemetry.Value;
                }

                int? battery = null;
                bool isCharging = true;

                try
                {
                    if (GetSystemPowerStatus(out var sps))
                    {
                        isCharging = (sps.ACLineStatus == 1) || ((sps.BatteryFlag & 8) != 0);

                        if (sps.BatteryFlag == 128)
                        {
                            // Desktop PC without physical battery (AC power only)
                            battery = 100;
                            isCharging = true;
                        }
                        else if (sps.BatteryLifePercent <= 100)
                        {
                            battery = sps.BatteryLifePercent;
                        }
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[PcTelemetryService] Power status read notice: {ex.Message}");
                }

                var (wifiBand, ssid) = GetWifiBandAndSsid();
                var result = (battery, isCharging, wifiBand, ssid);
                _cachedTelemetry = result;
                _lastFetch = DateTime.UtcNow;
                return result;
            }
        }

        private static (string WifiBand, string? Ssid) GetWifiBandAndSsid()
        {
            try
            {
                var interfaces = NetworkInterface.GetAllNetworkInterfaces();
                foreach (var ni in interfaces)
                {
                    if (ni.OperationalStatus == OperationalStatus.Up &&
                        (ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211))
                    {
                        string name = ni.Name.ToLowerInvariant() + " " + ni.Description.ToLowerInvariant();
                        string band = "5GHz"; // Default to high-speed wireless
                        if (name.Contains("6ghz") || name.Contains("wifi 6e") || name.Contains("wifi 7")) band = "6GHz";
                        else if (name.Contains("2.4ghz") || name.Contains("2.4g")) band = "2.4GHz";

                        return (band, ni.Name);
                    }
                }

                // Fallback: Check if active interface is Ethernet
                foreach (var ni in interfaces)
                {
                    if (ni.OperationalStatus == OperationalStatus.Up &&
                        ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet)
                    {
                        return ("LAN", "Ethernet");
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[PcTelemetryService] Network interface notice: {ex.Message}");
            }

            return ("5GHz", null);
        }
    }
}
