using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using DeXShareTarget.Endpoints;
using DeXShareTarget.Models;
using DeXShareTarget.Services;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class LocalSendEndpointsTests
    {
        private WebApplication _app;
        private HttpClient _client;
        private string _testDir;

        [TestInitialize]
        public async Task Setup()
        {
            _testDir = Path.Combine(Path.GetTempPath(), "DeX_EndpointsTests_" + Guid.NewGuid().ToString());
            Directory.CreateDirectory(_testDir);
            IdentityManager.BaseDirectory = _testDir;
            IdentityManager.Initialize();

            var builder = WebApplication.CreateBuilder();
            builder.WebHost.UseUrls("http://127.0.0.1:0"); // Random port
            _app = builder.Build();
            
            _app.MapLocalDeviceEndpoints();
            
            // Dummy mappings for endpoints that require testing but might be in other partial classes
            // We just need the ones in Device and Control to start
            _app.MapPost("/local/test-ping", () => "pong");

            await _app.StartAsync();

            var address = _app.Urls.FirstOrDefault();
            _client = new HttpClient { BaseAddress = new Uri(address) };
            
            DiscoveryBackgroundService.Devices.Clear();
            LocalSendEndpoints.PendingPairPins.Clear();
            LocalSendEndpoints.OutboundPairingStatus.Clear();
        }

        [TestCleanup]
        public async Task Teardown()
        {
            _client?.Dispose();
            if (_app != null)
            {
                await _app.StopAsync();
                await _app.DisposeAsync();
            }
            if (Directory.Exists(_testDir))
            {
                try { Directory.Delete(_testDir, true); } catch { }
            }
        }

        [TestMethod]
        public async Task GetDevices_ReturnsEmpty_WhenNoDevices()
        {
            var response = await _client.GetAsync("/local/devices");
            response.EnsureSuccessStatusCode();
            
            var json = await response.Content.ReadAsStringAsync();
            var devices = JsonSerializer.Deserialize<List<object>>(json);
            
            Assert.AreEqual(0, devices.Count);
        }

        [TestMethod]
        public async Task GetDevices_ReturnsDiscoveredDevices()
        {
            var fp = "fp_123";
            DiscoveryBackgroundService.AddOrUpdateDevice(new DiscoveredDevice
            {
                Ip = "192.168.1.50",
                Info = new RegisterDto { Fingerprint = fp, Alias = "TestPhone" },
                LastSeen = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            });

            var response = await _client.GetAsync("/local/devices");
            var json = await response.Content.ReadAsStringAsync();
            
            Assert.IsTrue(json.Contains("TestPhone"));
            Assert.IsTrue(json.Contains("192.168.1.50"));
        }

        [TestMethod]
        public async Task FlushDevices_ClearsAllDevices()
        {
            DiscoveryBackgroundService.AddOrUpdateDevice(new DiscoveredDevice
            {
                Ip = "192.168.1.50",
                Info = new RegisterDto { Fingerprint = "fp1" }
            });

            var response = await _client.PostAsync("/local/devices/flush", null);
            response.EnsureSuccessStatusCode();
            
            Assert.AreEqual(0, DiscoveryBackgroundService.Devices.Count);
        }

        [TestMethod]
        public async Task GetToken_ReturnsNotFound_IfDeviceUnknown()
        {
            var response = await _client.GetAsync("/local/token?ip=1.1.1.1");
            Assert.AreEqual(System.Net.HttpStatusCode.NotFound, response.StatusCode);
        }

        [TestMethod]
        public async Task GetToken_ReturnsToken_IfDevicePaired()
        {
            var fp = "fp_token";
            var ip = "192.168.1.60";
            DiscoveryBackgroundService.AddOrUpdateDevice(new DiscoveredDevice
            {
                Ip = ip,
                Info = new RegisterDto { Fingerprint = fp }
            });
            IdentityManager.SavePairedToken(fp, "secret-token");

            var response = await _client.GetAsync($"/local/token?ip={ip}");
            response.EnsureSuccessStatusCode();
            
            var json = await response.Content.ReadAsStringAsync();
            Assert.IsTrue(json.Contains("secret-token"));
        }

        [TestMethod]
        public async Task Unpair_RemovesDevice_FromIdentityManager()
        {
            var fp = "fp_unpair";
            IdentityManager.SavePairedDevice(fp);
            Assert.IsTrue(IdentityManager.PairedFingerprints.Contains(fp));

            var response = await _client.PostAsync($"/local/unpair?fingerprint={fp}", null);
            response.EnsureSuccessStatusCode();

            Assert.IsFalse(IdentityManager.PairedFingerprints.Contains(fp));
        }

        [TestMethod]
        public async Task PairStatus_ReturnsNotFound_IfNoAttempt()
        {
            var response = await _client.GetAsync("/local/pair-status?fingerprint=unknown");
            Assert.AreEqual(System.Net.HttpStatusCode.NotFound, response.StatusCode);
        }

        [TestMethod]
        public async Task PairStatus_ReturnsStatus_WhenPending()
        {
            var fp = "fp_status";
            LocalSendEndpoints.OutboundPairingStatus[fp] = "Pending";
            LocalSendEndpoints.PendingPairDigitCount[fp] = 3;

            var response = await _client.GetAsync($"/local/pair-status?fingerprint={fp}");
            response.EnsureSuccessStatusCode();

            var json = await response.Content.ReadAsStringAsync();
            Assert.IsTrue(json.Contains("Pending"));
            Assert.IsTrue(json.Contains("3"));
        }

        [TestMethod]
        public async Task PendingPair_ReturnsNotFound_IfEmpty()
        {
            var response = await _client.GetAsync("/local/pending-pair");
            Assert.AreEqual(System.Net.HttpStatusCode.NotFound, response.StatusCode);
        }

        [TestMethod]
        public async Task PairCancel_UpdatesStatus_ToCancelled()
        {
            var fp = "fp_cancel";
            LocalSendEndpoints.OutboundPairingStatus[fp] = "Pending";

            var response = await _client.PostAsync($"/local/pair-cancel?fingerprint={fp}", null);
            response.EnsureSuccessStatusCode();

            Assert.AreEqual("Cancelled", LocalSendEndpoints.OutboundPairingStatus[fp]);
        }

        [TestMethod]
        public async Task PairCancel_DoesNotThrow_IfUnknown()
        {
            var response = await _client.PostAsync("/local/pair-cancel?fingerprint=unknown", null);
            response.EnsureSuccessStatusCode();
        }
    }
}
