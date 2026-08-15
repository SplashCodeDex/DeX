using Microsoft.VisualStudio.TestTools.UnitTesting;
using DeXShareTarget.Services;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class TelemetryStoreTests
    {
        [TestCleanup]
        public void Cleanup()
        {
            TelemetryStore.BatteryByFingerprint.Clear();
            TelemetryStore.WifiByFingerprint.Clear();
        }

        [TestMethod]
        public void GetBattery_ReturnsNull_WhenNotSet()
        {
            Assert.IsNull(TelemetryStore.GetBattery("unknown_fp"));
        }

        [TestMethod]
        public void SetBattery_UpdatesBatteryValue()
        {
            TelemetryStore.SetBattery("fp1", 85);
            Assert.AreEqual(85, TelemetryStore.GetBattery("fp1"));
        }

        [TestMethod]
        public void GetWifi_ReturnsNull_WhenNotSet()
        {
            Assert.IsNull(TelemetryStore.GetWifi("unknown_fp"));
        }

        [TestMethod]
        public void SetWifi_UpdatesWifiValue()
        {
            TelemetryStore.SetWifi("fp2", "MyHomeWifi", -50);
            var wifi = TelemetryStore.GetWifi("fp2");
            Assert.IsNotNull(wifi);
            Assert.AreEqual("MyHomeWifi", wifi.Value.Ssid);
            Assert.AreEqual(-50, wifi.Value.Rssi);
        }

        [TestMethod]
        public void Remove_ClearsBothBatteryAndWifi()
        {
            TelemetryStore.SetBattery("fp3", 100);
            TelemetryStore.SetWifi("fp3", "TestNet", -40);

            TelemetryStore.Remove("fp3");

            Assert.IsNull(TelemetryStore.GetBattery("fp3"));
            Assert.IsNull(TelemetryStore.GetWifi("fp3"));
        }
    }
}
