using System.Text.Json;
using DeXShareTarget.Models;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class ModelsTests
    {
        [TestMethod]
        public void RegisterDto_HasExpectedDefaultValues()
        {
            var dto = new RegisterDto();
            Assert.AreEqual("2.0", dto.Version);
            Assert.AreEqual("Unknown", dto.DeviceModel);
            Assert.AreEqual("unknown", dto.DeviceType);
            Assert.AreEqual("https", dto.Protocol);
            Assert.IsTrue(dto.Download);
            Assert.IsNotNull(dto.Fingerprint); // Populated from IdentityManager
            Assert.IsNotNull(dto.Alias); // Populated from MachineName
        }

        [TestMethod]
        public void RegisterDto_SerializesWithCamelCaseNames()
        {
            var dto = new RegisterDto { Alias = "TestAlias" };
            var json = JsonSerializer.Serialize(dto);
            
            // System.Text.Json without options uses PascalCase by default unless JsonPropertyName is set
            // The attributes in Models.cs use "alias", "version", etc.
            Assert.IsTrue(json.Contains("\"alias\":\"TestAlias\""));
            Assert.IsTrue(json.Contains("\"deviceModel\":\"Unknown\""));
        }

        [TestMethod]
        public void FileDto_SerializesCorrectly()
        {
            var file = new FileDto
            {
                Id = "f123",
                FileName = "test.txt",
                Size = 1024,
                FileType = "text/plain"
            };

            var json = JsonSerializer.Serialize(file);
            Assert.IsTrue(json.Contains("\"id\":\"f123\""));
            Assert.IsTrue(json.Contains("\"fileName\":\"test.txt\""));
            Assert.IsTrue(json.Contains("\"size\":1024"));
            Assert.IsTrue(json.Contains("\"fileType\":\"text/plain\""));
        }

        [TestMethod]
        public void PrepareUploadRequestDto_InitializesEmptyCollections()
        {
            var req = new PrepareUploadRequestDto();
            Assert.IsNotNull(req.Info);
            Assert.IsNotNull(req.Files);
            Assert.AreEqual(0, req.Files.Count);
        }

        [TestMethod]
        public void PrepareUploadResponseDto_InitializesEmptyCollections()
        {
            var res = new PrepareUploadResponseDto();
            Assert.AreEqual("", res.SessionId);
            Assert.IsNotNull(res.Files);
            Assert.AreEqual(0, res.Files.Count);
        }

        [TestMethod]
        public void DiscoveredDevice_HasReasonableDefaults()
        {
            var dev = new DiscoveredDevice();
            Assert.IsNotNull(dev.Info);
            Assert.AreEqual("", dev.Ip);
            Assert.IsFalse(dev.IsPaired);
            Assert.IsFalse(dev.IsAutoTrusted);
            Assert.AreEqual(0, dev.LastSeen);
        }

        [TestMethod]
        public void PendingPairAttempt_SetsCreatedAtOnInitialization()
        {
            var attempt = new PendingPairAttempt();
            // Should be initialized to current UTC time
            var diff = System.DateTime.UtcNow - attempt.CreatedAt;
            Assert.IsTrue(diff.TotalSeconds < 5);
        }
    }
}
