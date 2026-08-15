using System;
using System.Linq;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using DeXShareTarget.Services;
using DeXShareTarget.Models;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class DiscoveryBackgroundServiceTests
    {
        [TestInitialize]
        public void Setup()
        {
            DiscoveryBackgroundService.Devices.Clear();
        }

        [TestMethod]
        public void AddOrUpdateDevice_IgnoresNullFingerprint()
        {
            var device = new DiscoveredDevice
            {
                Ip = "192.168.1.100",
                Info = new RegisterDto { Fingerprint = null }
            };

            DiscoveryBackgroundService.AddOrUpdateDevice(device);

            Assert.AreEqual(0, DiscoveryBackgroundService.Devices.Count);
        }

        [TestMethod]
        public void AddOrUpdateDevice_AddsNewDeviceSuccessfully()
        {
            var fp = "fp_new";
            var device = new DiscoveredDevice
            {
                Ip = "192.168.1.100",
                Info = new RegisterDto { Fingerprint = fp, Alias = "Test PC" }
            };

            DiscoveryBackgroundService.AddOrUpdateDevice(device);

            Assert.IsTrue(DiscoveryBackgroundService.Devices.ContainsKey(fp));
            Assert.AreEqual("Test PC", DiscoveryBackgroundService.Devices[fp].Info.Alias);
        }

        [TestMethod]
        public void AddOrUpdateDevice_UpdatesExistingDevice()
        {
            var fp = "fp_update";
            var device1 = new DiscoveredDevice
            {
                Ip = "192.168.1.100",
                Info = new RegisterDto { Fingerprint = fp, Alias = "Old Alias" },
                LastSeen = 1000
            };
            
            DiscoveryBackgroundService.AddOrUpdateDevice(device1);

            var device2 = new DiscoveredDevice
            {
                Ip = "192.168.1.101",
                Info = new RegisterDto { Fingerprint = fp, Alias = "New Alias" },
                LastSeen = 2000
            };
            
            DiscoveryBackgroundService.AddOrUpdateDevice(device2);

            Assert.AreEqual(1, DiscoveryBackgroundService.Devices.Count);
            Assert.AreEqual("New Alias", DiscoveryBackgroundService.Devices[fp].Info.Alias);
            Assert.AreEqual("192.168.1.101", DiscoveryBackgroundService.Devices[fp].Ip);
            Assert.AreEqual(2000, DiscoveryBackgroundService.Devices[fp].LastSeen);
        }

        [TestMethod]
        public void AddOrUpdateDevice_EvictsOldestWhenCapReached()
        {
            // The service caps at 100 devices to prevent DoS.
            for (int i = 0; i < 100; i++)
            {
                DiscoveryBackgroundService.AddOrUpdateDevice(new DiscoveredDevice
                {
                    Ip = $"192.168.1.{i}",
                    Info = new RegisterDto { Fingerprint = $"fp_{i}" },
                    LastSeen = i // Increasing timestamp so fp_0 is the oldest
                });
            }

            Assert.AreEqual(100, DiscoveryBackgroundService.Devices.Count);
            Assert.IsTrue(DiscoveryBackgroundService.Devices.ContainsKey("fp_0"));

            // Adding the 101st device should evict the oldest (fp_0)
            DiscoveryBackgroundService.AddOrUpdateDevice(new DiscoveredDevice
            {
                Ip = "192.168.1.200",
                Info = new RegisterDto { Fingerprint = "fp_new" },
                LastSeen = 1000
            });

            Assert.AreEqual(100, DiscoveryBackgroundService.Devices.Count);
            Assert.IsFalse(DiscoveryBackgroundService.Devices.ContainsKey("fp_0"), "Oldest device was not evicted.");
            Assert.IsTrue(DiscoveryBackgroundService.Devices.ContainsKey("fp_new"), "New device was not added.");
            Assert.IsTrue(DiscoveryBackgroundService.Devices.ContainsKey("fp_99"), "Recent device was incorrectly evicted.");
        }

        [TestMethod]
        public void AddOrUpdateDevice_KeepsCurrentIfUnderCap()
        {
            for (int i = 0; i < 99; i++)
            {
                DiscoveryBackgroundService.AddOrUpdateDevice(new DiscoveredDevice
                {
                    Ip = $"192.168.1.{i}",
                    Info = new RegisterDto { Fingerprint = $"fp_{i}" },
                    LastSeen = i
                });
            }

            Assert.AreEqual(99, DiscoveryBackgroundService.Devices.Count);
            Assert.IsTrue(DiscoveryBackgroundService.Devices.ContainsKey("fp_0"));
        }
    }
}
