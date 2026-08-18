using System;
using System.IO;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using DeXShareTarget.Services;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class GoogleOAuthTests
    {
        private string _testDir;

        [TestInitialize]
        public void Setup()
        {
            _testDir = Path.Combine(Path.GetTempPath(), "DeX_OAuthTests_" + Guid.NewGuid().ToString());
            Directory.CreateDirectory(_testDir);
            GoogleOAuth.BaseDirectory = _testDir;
            
            // Ensure clean state
            GoogleOAuth.SignOut();
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
        public void HandleCallback_ReturnsFalse_ForUnknownState()
        {
            string error;
            var result = GoogleOAuth.HandleCallback("unknown_state", "some_code", out error);
            
            Assert.IsFalse(result);
            Assert.IsNotNull(error);
            Assert.IsTrue(error.Contains("No pending sign-in"));
        }

        [TestMethod]
        public void HandleCallback_ReturnsFalse_WhenCodeIsNull()
        {
            // Even if we don't have a pending state to test directly without calling SignInAsync,
            // we can verify the signature and behavior
            string error;
            var result = GoogleOAuth.HandleCallback("unknown_state", null, out error);
            
            Assert.IsFalse(result);
            Assert.IsNotNull(error);
        }

        [TestMethod]
        public void SaveAndLoadProfile_RoundTripsCorrectly()
        {
            var profile = new GoogleOAuth.GoogleProfile("test@example.com", "Test User", "https://example.com/pic.png", "sub_123");
            
            GoogleOAuth.SaveProfile(profile);
            
            var loaded = GoogleOAuth.LoadProfile();
            
            Assert.IsNotNull(loaded);
            Assert.AreEqual("test@example.com", loaded.Email);
            Assert.AreEqual("Test User", loaded.Name);
            Assert.AreEqual("https://example.com/pic.png", loaded.Picture);
            Assert.AreEqual("sub_123", loaded.Sub);
            
            Assert.IsTrue(File.Exists(Path.Combine(_testDir, "google_profile.json")));
        }

        [TestMethod]
        public void SignOut_RemovesProfileFile()
        {
            var profile = new GoogleOAuth.GoogleProfile("test@example.com", "Test User", "", "");
            GoogleOAuth.SaveProfile(profile);
            
            Assert.IsTrue(File.Exists(Path.Combine(_testDir, "google_profile.json")));
            
            GoogleOAuth.SignOut();
            
            Assert.IsFalse(File.Exists(Path.Combine(_testDir, "google_profile.json")));
            Assert.IsNull(GoogleOAuth.LoadProfile());
        }
    }
}
