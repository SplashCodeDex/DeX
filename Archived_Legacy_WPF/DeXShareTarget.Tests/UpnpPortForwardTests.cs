using System;
using System.Reflection;
using System.Threading;
using System.Threading.Tasks;
using DeXShareTarget.Services;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class UpnpPortForwardTests
    {
        [TestMethod]
        public async Task ProbePublicIpAsync_DoesNotThrow_WhenCancelled()
        {
            var cts = new CancellationTokenSource();
            cts.Cancel(); // Cancel immediately
            
            // Should return immediately without throwing
            await UpnpPortForward.ProbePublicIpAsync(cts.Token);
            Assert.IsTrue(true); // Reached here without exception
        }

        [TestMethod]
        public async Task ConfigureAsync_DoesNotThrow_WhenCancelled()
        {
            var cts = new CancellationTokenSource();
            cts.Cancel();
            
            await UpnpPortForward.ConfigureAsync(cts.Token);
            Assert.IsTrue(true);
        }

        [TestMethod]
        public void IsUsablePublicIpv4_EvaluatesCorrectly()
        {
            // Use reflection to test the private method IsUsablePublicIpv4
            var method = typeof(UpnpPortForward).GetMethod("IsUsablePublicIpv4", BindingFlags.NonPublic | BindingFlags.Static);
            Assert.IsNotNull(method);

            bool Invoke(string ip) => (bool)method.Invoke(null, new object[] { ip })!;

            // Private / Reserved ranges
            Assert.IsFalse(Invoke("192.168.1.1"));
            Assert.IsFalse(Invoke("10.0.0.1"));
            Assert.IsFalse(Invoke("172.16.0.1"));
            Assert.IsFalse(Invoke("100.64.0.1"));
            Assert.IsFalse(Invoke("127.0.0.1"));
            Assert.IsFalse(Invoke("169.254.1.1"));
            Assert.IsFalse(Invoke(null!));
            Assert.IsFalse(Invoke(""));
            Assert.IsFalse(Invoke("invalid-ip"));

            // Public ranges
            Assert.IsTrue(Invoke("8.8.8.8"));
            Assert.IsTrue(Invoke("1.1.1.1"));
            Assert.IsTrue(Invoke("104.21.5.12"));
        }
    }
}
