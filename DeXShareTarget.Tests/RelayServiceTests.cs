using System;
using System.IO;
using System.Threading.Tasks;
using DeXShareTarget.Endpoints;
using DeXShareTarget.Services;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class RelayServiceTests
    {
        private string _tempFile = "";

        [TestInitialize]
        public void Setup()
        {
            _tempFile = Path.GetTempFileName();
            File.WriteAllText(_tempFile, "Relay Test Content");
            
            LocalSendEndpoints.HostedFiles.Clear();
            LocalSendEndpoints.HostedFileTokens.Clear();
            LocalSendEndpoints.HostedFileLastAccess.Clear();
        }

        [TestCleanup]
        public void Cleanup()
        {
            if (File.Exists(_tempFile)) File.Delete(_tempFile);
            LocalSendEndpoints.HostedFiles.Clear();
            LocalSendEndpoints.HostedFileTokens.Clear();
            LocalSendEndpoints.HostedFileLastAccess.Clear();
        }

        [TestMethod]
        public async Task HostAndPushAsync_ReturnsFalse_WhenTargetOrFilesEmpty()
        {
            var result1 = await RelayService.HostAndPushAsync("", new[] { (_tempFile, "") }, "Alias");
            var result2 = await RelayService.HostAndPushAsync("fp", Array.Empty<(string, string)>(), "Alias");

            Assert.IsFalse(result1);
            Assert.IsFalse(result2);
        }

        [TestMethod]
        public async Task HostAndPushAsync_PopulatesHostedFiles()
        {
            // Even if SendAsync fails (because socket isn't connected), it should populate the collections
            await RelayService.HostAndPushAsync("dummy_fp", new[] { (_tempFile, "rel/path.txt") }, "Alias");

            Assert.AreEqual(1, LocalSendEndpoints.HostedFiles.Count);
            Assert.AreEqual(1, LocalSendEndpoints.HostedFileTokens.Count);
            Assert.AreEqual(1, LocalSendEndpoints.HostedFileLastAccess.Count);
            
            // Check that the file was added
            var enumerator = LocalSendEndpoints.HostedFiles.GetEnumerator();
            enumerator.MoveNext();
            Assert.AreEqual(_tempFile, enumerator.Current.Value);
        }
        
        [TestMethod]
        public async Task HostAndPushAsync_IgnoresNonExistentFiles()
        {
            var badPath = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
            
            await RelayService.HostAndPushAsync("dummy_fp", new[] { (badPath, "") }, "Alias");

            Assert.AreEqual(0, LocalSendEndpoints.HostedFiles.Count);
        }
    }
}
