using System.Threading;
using DeXShareTarget.Services;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class MirrorWindowHostTests
    {
        [TestCleanup]
        public void Cleanup()
        {
            MirrorWindowHost.StopActive();
            Thread.Sleep(100); // Give STA thread a moment to shut down if it was running
        }

        [TestMethod]
        public void InitialState_IsInactive()
        {
            Assert.IsFalse(MirrorWindowHost.IsActive);
            Assert.AreEqual("", MirrorWindowHost.ActiveFingerprint);
        }

        [TestMethod]
        public void PushFrame_ReturnsFalse_WhenNoSessionActive()
        {
            var result = MirrorWindowHost.PushFrame("any_fp", new byte[10]);
            Assert.IsFalse(result);
        }

        [TestMethod]
        public void Stop_DoesNotThrow_WhenInactive()
        {
            MirrorWindowHost.Stop("any_fp");
            MirrorWindowHost.StopActive();
            Assert.IsTrue(true); // Reached without exceptions
        }
    }
}
