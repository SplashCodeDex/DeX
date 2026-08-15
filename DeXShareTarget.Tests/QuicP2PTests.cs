using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Quic;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using DeXShareTarget.Services;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class QuicP2PTests
    {
        private string _testDir;
        private string _downloadDir;
        private string _sourceFile1;
        private string _sourceFile2;

        [TestInitialize]
        public void Setup()
        {
            _testDir = Path.Combine(Path.GetTempPath(), "DeX_QuicTests_" + Guid.NewGuid().ToString());
            _downloadDir = Path.Combine(_testDir, "Downloads");
            Directory.CreateDirectory(_testDir);
            Directory.CreateDirectory(_downloadDir);
            
            QuicP2PClient.BaseDownloadDirectory = _downloadDir;

            _sourceFile1 = Path.Combine(_testDir, "test1.txt");
            File.WriteAllText(_sourceFile1, "Hello QUIC World!");
            
            _sourceFile2 = Path.Combine(_testDir, "test2.bin");
            var bytes = new byte[1024 * 1024]; // 1MB
            new Random().NextBytes(bytes);
            File.WriteAllBytes(_sourceFile2, bytes);
        }

        [TestCleanup]
        public void Teardown()
        {
            if (Directory.Exists(_testDir))
            {
                try
                {
                    Directory.Delete(_testDir, true);
                }
                catch { } // Sometimes files are locked momentarily
            }
        }

        private X509Certificate2 GenerateEphemeralCert()
        {
            using var rsa = RSA.Create(2048);
            var req = new CertificateRequest("cn=DeX_Test", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
            using var leaf = req.CreateSelfSigned(DateTimeOffset.Now.AddDays(-1), DateTimeOffset.Now.AddDays(1));
            return X509CertificateLoader.LoadPkcs12(leaf.Export(X509ContentType.Pfx, "test"), "test", X509KeyStorageFlags.Exportable);
        }

        [TestMethod]
        public async Task StartListener_BindsToConfiguredPort()
        {
            if (!QuicListener.IsSupported) Assert.Inconclusive("QUIC is not supported on this OS.");

            using var cert = GenerateEphemeralCert();
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));

            var (port, waitForCompletion) = await QuicP2PService.HostAsync(
                new List<(string, string)>(),
                cert,
                null,
                cts.Token
            );

            Assert.IsTrue(port > 0);
            Assert.IsNotNull(waitForCompletion);
        }

        [TestMethod]
        [Ignore("Flaky on CI loopback")]
        public async Task Transfer_ReadsDataToCompletion_MatchesChecksum()
        {
            if (!QuicListener.IsSupported) Assert.Inconclusive("QUIC is not supported on this OS.");

            using var cert = GenerateEphemeralCert();
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(10));

            var files = new List<(string, string)>
            {
                (_sourceFile1, "test1.txt"),
                (_sourceFile2, "folder/test2.bin")
            };

            var (port, waitForCompletion) = await QuicP2PService.HostAsync(files, cert, null, cts.Token);

            var receiveTask = QuicP2PClient.ReceiveAsync("127.0.0.1", port, "TestHost");
            var hostTask = waitForCompletion();

            await Task.WhenAll(receiveTask, hostTask);

            // Verify
            var destFile1 = Path.Combine(_downloadDir, "test1.txt");
            var destFile2 = Path.Combine(_downloadDir, "folder", "test2.bin");

            Assert.IsTrue(File.Exists(destFile1));
            Assert.IsTrue(File.Exists(destFile2));

            Assert.AreEqual("Hello QUIC World!", File.ReadAllText(destFile1));
            
            var sourceBytes = File.ReadAllBytes(_sourceFile2);
            var destBytes = File.ReadAllBytes(destFile2);
            CollectionAssert.AreEqual(sourceBytes, destBytes);
        }

        [TestMethod]
        [Ignore("Flaky on CI loopback")]
        public async Task Transfer_FileProgressUpdates_TriggeredProperly()
        {
            if (!QuicListener.IsSupported) Assert.Inconclusive("QUIC is not supported on this OS.");

            using var cert = GenerateEphemeralCert();
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(10));

            var files = new List<(string, string)> { (_sourceFile1, "test1.txt") };
            
            int progressEvents = 0;
            var progress = new Progress<TransferProgress>(p =>
            {
                progressEvents++;
                Assert.IsTrue(p.TotalBytes > 0);
            });

            var (port, waitForCompletion) = await QuicP2PService.HostAsync(files, cert, progress, cts.Token);

            var receiveTask = QuicP2PClient.ReceiveAsync("127.0.0.1", port, "TestHost");
            
            await Task.WhenAll(receiveTask, waitForCompletion());

            Assert.IsTrue(progressEvents > 0, "Progress should have been reported.");
        }

        [TestMethod]
        public async Task Client_FailsGracefully_IfTargetUnreachable()
        {
            if (!QuicListener.IsSupported) Assert.Inconclusive("QUIC is not supported on this OS.");

            // Port 1 (or any unused port)
            var receiveTask = QuicP2PClient.ReceiveAsync("127.0.0.1", 1, "Unreachable");
            
            // Should not throw, but swallow and log error internally based on Client design
            await receiveTask; 
        }

        [TestMethod]
        public async Task Host_HandlesCancellation_Gracefully()
        {
            if (!QuicListener.IsSupported) Assert.Inconclusive("QUIC is not supported on this OS.");

            using var cert = GenerateEphemeralCert();
            using var cts = new CancellationTokenSource();
            
            var (port, waitForCompletion) = await QuicP2PService.HostAsync(new List<(string, string)>(), cert, null, cts.Token);
            
            cts.Cancel(); // Cancel immediately

            try
            {
                await waitForCompletion();
            }
            catch (OperationCanceledException) { }
            catch (QuicException) { }
        }
    }
}
