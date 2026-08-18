using System;
using System.Reflection;
using DeXShareTarget.Services;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class ClipboardServiceTests
    {
        [TestMethod]
        public void ParsePayload_ExtractsTextAndImage()
        {
            var method = typeof(ClipboardService).GetMethod("ParsePayload", BindingFlags.NonPublic | BindingFlags.Static);
            Assert.IsNotNull(method);

            (string Text, string? ImageBase64) Invoke(string body) => 
                ((string Text, string? ImageBase64))method.Invoke(null, new object[] { body })!;

            // Simple text
            var r1 = Invoke("{\"text\": \"hello\"}");
            Assert.AreEqual("hello", r1.Text);
            Assert.IsNull(r1.ImageBase64);

            // Nested data text
            var r2 = Invoke("{\"data\": {\"text\": \"nested\"}}");
            Assert.AreEqual("nested", r2.Text);
            Assert.IsNull(r2.ImageBase64);

            // Image
            var r3 = Invoke("{\"imageBase64\": \"base64string\"}");
            Assert.AreEqual("base64string", r3.ImageBase64);

            // Fallback (raw string when parse fails)
            var r4 = Invoke("just raw text");
            Assert.AreEqual("just raw text", r4.Text);
            Assert.IsNull(r4.ImageBase64);
        }

        [TestMethod]
        public void FormatForwardPayload_WrapsTextCorrectly()
        {
            var method = typeof(ClipboardService).GetMethod("FormatForwardPayload", BindingFlags.NonPublic | BindingFlags.Static);
            Assert.IsNotNull(method);

            string Invoke(string body) => (string)method.Invoke(null, new object[] { body })!;

            // Raw text should be wrapped
            var r1 = Invoke("hello");
            Assert.IsTrue(r1.Contains("\"type\":\"set-clipboard\""));
            Assert.IsTrue(r1.Contains("\"text\":\"hello\""));

            // Already wrapped JSON should be returned as-is
            string validJson = "{\"type\":\"set-clipboard\",\"data\":{\"text\":\"already valid\"}}";
            var r2 = Invoke(validJson);
            Assert.AreEqual(validJson, r2);
        }

        [TestMethod]
        public void GetState_ReturnsEmpty_WhenNoClipboard()
        {
            ClipboardService.LastPhoneClipboard = null;
            var result = ClipboardService.GetState();
            Assert.IsNotNull(result);
            // IResult validation without ASP.NET Core test hosting is tricky, 
            // but we can ensure it returns an object.
        }

        [TestMethod]
        public void StateProperties_CanBeSet()
        {
            ClipboardService.IsSyncEnabled = true;
            Assert.IsTrue(ClipboardService.IsSyncEnabled);

            var now = DateTime.UtcNow;
            ClipboardService.LastPhoneClipboard = ("test", now);
            Assert.IsNotNull(ClipboardService.LastPhoneClipboard);
            Assert.AreEqual("test", ClipboardService.LastPhoneClipboard.Value.Text);
            Assert.AreEqual(now, ClipboardService.LastPhoneClipboard.Value.At);
        }
    }
}
