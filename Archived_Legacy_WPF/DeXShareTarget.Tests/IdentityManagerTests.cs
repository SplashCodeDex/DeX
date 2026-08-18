using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using DeXShareTarget.Services;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class IdentityManagerTests
    {
        private string _testDir;

        [TestInitialize]
        public void Setup()
        {
            _testDir = Path.Combine(Path.GetTempPath(), "DeX_Tests_" + Guid.NewGuid().ToString());
            Directory.CreateDirectory(_testDir);
            IdentityManager.BaseDirectory = _testDir;
            
            // Reset state
            IdentityManager.Fingerprint = "";
            IdentityManager.IdentityHash = "";
            IdentityManager.Email = "";
            IdentityManager.GoogleSub = "";
            IdentityManager.PairedFingerprints.Clear();
            IdentityManager.PairedTokens.Clear();
            IdentityManager.PairedLastSeen.Clear();
            IdentityManager.DeviceAliases.Clear();
        }

        [TestCleanup]
        public void Teardown()
        {
            if (Directory.Exists(_testDir))
            {
                Directory.Delete(_testDir, true);
            }
        }

        [TestMethod]
        public void Initialize_CreatesNewIdentity_WhenFileMissing()
        {
            Assert.IsFalse(File.Exists(Path.Combine(_testDir, "identity.json")));
            IdentityManager.Initialize();
            
            Assert.IsTrue(File.Exists(Path.Combine(_testDir, "identity.json")));
            Assert.IsFalse(string.IsNullOrEmpty(IdentityManager.Fingerprint));
            Assert.IsFalse(string.IsNullOrEmpty(IdentityManager.IdentityHash));
        }

        [TestMethod]
        public void Initialize_LoadsExistingIdentity_Successfully()
        {
            var fp = Guid.NewGuid().ToString();
            var hash = Guid.NewGuid().ToString();
            var json = JsonSerializer.Serialize(new { fingerprint = fp, identityHash = hash, email = "test@example.com", googleSub = "sub123" });
            File.WriteAllText(Path.Combine(_testDir, "identity.json"), json);
            
            IdentityManager.Initialize();
            
            Assert.AreEqual(fp, IdentityManager.Fingerprint);
            Assert.AreEqual("test@example.com", IdentityManager.Email);
            Assert.AreEqual("sub123", IdentityManager.GoogleSub);
            
            using var sha = SHA256.Create();
            var expectedHash = BitConverter.ToString(sha.ComputeHash(Encoding.UTF8.GetBytes("test@example.com"))).Replace("-", "").ToLowerInvariant();
            Assert.AreEqual(expectedHash, IdentityManager.IdentityHash);
        }

        [TestMethod]
        public void GarbageCollectOrphanedDevices_RemovesDevicesOlderThan30Days()
        {
            IdentityManager.Initialize();
            var fp = "old_device";
            IdentityManager.SavePairedDevice(fp);
            
            var fortyDaysMs = 40L * 24 * 60 * 60 * 1000;
            IdentityManager.PairedLastSeen[fp] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - fortyDaysMs;
            
            // Call Initialize again which invokes GarbageCollectOrphanedDevices
            IdentityManager.Initialize();
            
            Assert.IsFalse(IdentityManager.PairedFingerprints.Contains(fp));
        }

        [TestMethod]
        public void GarbageCollectOrphanedDevices_KeepsRecentlySeenDevices()
        {
            IdentityManager.Initialize();
            var fp = "recent_device";
            IdentityManager.SavePairedDevice(fp);
            
            var tenDaysMs = 10L * 24 * 60 * 60 * 1000;
            IdentityManager.PairedLastSeen[fp] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - tenDaysMs;
            
            IdentityManager.Initialize();
            
            Assert.IsTrue(IdentityManager.PairedFingerprints.Contains(fp));
        }

        [TestMethod]
        public void SetEmail_GeneratesConsistentSHA256Hash()
        {
            IdentityManager.SetEmail(" TEST@EXAMPLE.com ");
            
            using var sha = SHA256.Create();
            var expectedHash = BitConverter.ToString(sha.ComputeHash(Encoding.UTF8.GetBytes("test@example.com"))).Replace("-", "").ToLowerInvariant();
            
            Assert.AreEqual(" TEST@EXAMPLE.com ", IdentityManager.Email);
            Assert.AreEqual(expectedHash, IdentityManager.IdentityHash);
        }

        [TestMethod]
        public void SetGoogleSub_UpdatesIdentityCorrectly()
        {
            IdentityManager.SetGoogleSub("google_12345");
            Assert.AreEqual("google_12345", IdentityManager.GoogleSub);
            
            var json = File.ReadAllText(Path.Combine(_testDir, "identity.json"));
            Assert.IsTrue(json.Contains("google_12345"));
        }

        [TestMethod]
        public void IsIdentityToken_ReturnsTrue_WhenMatchesEmailHash()
        {
            IdentityManager.SetEmail("test@example.com");
            Assert.IsTrue(IdentityManager.IsIdentityToken(IdentityManager.IdentityHash));
        }

        [TestMethod]
        public void IsIdentityToken_ReturnsTrue_WhenMatchesGoogleSub()
        {
            IdentityManager.SetGoogleSub("google_abc");
            Assert.IsTrue(IdentityManager.IsIdentityToken("google_abc"));
        }

        [TestMethod]
        public void IsPairedTokenOrFingerprint_AcceptsValidPairedDevice()
        {
            IdentityManager.Initialize();
            IdentityManager.SavePairedDevice("fp_1");
            IdentityManager.SavePairedToken("fp_1", "token_1");
            
            Assert.IsTrue(IdentityManager.IsPairedTokenOrFingerprint(null, "fp_1"));
            Assert.IsTrue(IdentityManager.IsPairedTokenOrFingerprint("token_1", null));
        }

        [TestMethod]
        public void IsPairedTokenOrFingerprint_RejectsUnknownDevice()
        {
            IdentityManager.Initialize();
            Assert.IsFalse(IdentityManager.IsPairedTokenOrFingerprint("unknown_token", "unknown_fp"));
        }

        [TestMethod]
        public void RemovePairedDevice_CleansUpAllJsonReferences()
        {
            IdentityManager.Initialize();
            var fp = "fp_remove";
            IdentityManager.SavePairedDevice(fp);
            IdentityManager.SavePairedToken(fp, "token");
            IdentityManager.UpdateLastSeen(fp);
            
            IdentityManager.RemovePairedDevice(fp);
            
            Assert.IsFalse(IdentityManager.PairedFingerprints.Contains(fp));
            Assert.IsFalse(IdentityManager.PairedTokens.ContainsKey(fp));
            Assert.IsFalse(IdentityManager.PairedLastSeen.ContainsKey(fp));
            
            // Verify JSON files are updated
            var devicesJson = File.ReadAllText(Path.Combine(_testDir, "paired_devices.json"));
            Assert.IsFalse(devicesJson.Contains(fp));
        }

        [TestMethod]
        public void UpdateLastSeen_RefreshesTimestamp()
        {
            IdentityManager.Initialize();
            var fp = "fp_time";
            var oldTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - 5000;
            IdentityManager.PairedLastSeen[fp] = oldTime;
            
            IdentityManager.UpdateLastSeen(fp);
            
            Assert.IsTrue(IdentityManager.PairedLastSeen[fp] > oldTime);
        }
    }
}
