using System;
using System.IO;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using DeXShareTarget.Services;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class ExtraIdentityTests
    {
        private string _testDir;

        [TestInitialize]
        public void Setup()
        {
            _testDir = Path.Combine(Path.GetTempPath(), "DeX_ExtraIdentity_" + Guid.NewGuid().ToString());
            Directory.CreateDirectory(_testDir);
            IdentityManager.BaseDirectory = _testDir;
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
        public void Initialize_Idempotent_CanBeCalledMultipleTimes()
        {
            IdentityManager.Initialize();
            var fp1 = IdentityManager.Fingerprint;
            
            IdentityManager.Initialize(); // Second call shouldn't crash or erase
            var fp2 = IdentityManager.Fingerprint;
            
            Assert.AreEqual(fp1, fp2);
        }

        [TestMethod]
        public void IsIdentityToken_ReturnsFalse_ForEmptyOrNull()
        {
            Assert.IsFalse(IdentityManager.IsIdentityToken(null));
            Assert.IsFalse(IdentityManager.IsIdentityToken(""));
        }

        [TestMethod]
        public void LoadPairedDevices_HandlesCorruptedFileGracefully()
        {
            var path = Path.Combine(_testDir, "paired.json");
            File.WriteAllText(path, "{ corrupted_json");
            
            // Should not crash, just ignore corrupted list
            IdentityManager.Initialize();
            
            Assert.AreEqual(0, IdentityManager.PairedFingerprints.Count);
        }
        

    }
}
