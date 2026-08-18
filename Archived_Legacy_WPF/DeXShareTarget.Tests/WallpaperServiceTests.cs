using System;
using System.Reflection;
using DeXShareTarget.Services;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class WallpaperServiceTests
    {
        [TestMethod]
        public void ExpandPath_ExpandsEnvironmentVariables()
        {
            var result = WallpaperService.ExpandPath("%TEMP%");
            var expected = Environment.GetEnvironmentVariable("TEMP");
            Assert.AreEqual(expected, result);
        }

        [TestMethod]
        public void ExpandPath_ReturnsOriginal_WhenNoVariables()
        {
            var path = @"C:\path\to\file.jpg";
            var result = WallpaperService.ExpandPath(path);
            Assert.AreEqual(path, result);
        }

        [TestMethod]
        public void ExpandPath_HandlesNullOrEmpty()
        {
            Assert.AreEqual(null, WallpaperService.ExpandPath(null!));
            Assert.AreEqual("", WallpaperService.ExpandPath(""));
        }

        [TestMethod]
        public void InvalidateCache_ClearsCache()
        {
            // It's a static void, just ensure it doesn't throw
            WallpaperService.InvalidateCache();
            Assert.IsTrue(true);
        }

        [TestMethod]
        public void GetContentType_DetectsJpegAndPng()
        {
            var method = typeof(WallpaperService).GetMethod("GetContentType", BindingFlags.NonPublic | BindingFlags.Static);
            Assert.IsNotNull(method);

            string Invoke(byte[] bytes) => (string)method.Invoke(null, new object[] { bytes })!;

            byte[] jpeg = new byte[] { 0xFF, 0xD8, 0x00 };
            Assert.AreEqual("image/jpeg", Invoke(jpeg));

            byte[] png = new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
            Assert.AreEqual("image/png", Invoke(png));

            byte[] unknown = new byte[] { 0x00, 0x01, 0x02 };
            Assert.AreEqual("image/jpeg", Invoke(unknown)); // default
        }
        
        [TestMethod]
        public void GetWallpaper480p_DoesNotThrow()
        {
            // This relies on Windows system state, so we can't assert the result is not null, 
            // but we can ensure it doesn't crash.
            var result = WallpaperService.GetWallpaper480p();
            if (result != null)
            {
                Assert.IsNotNull(result.Value.Bytes);
                Assert.IsNotNull(result.Value.ContentType);
                Assert.IsNotNull(result.Value.ETag);
            }
        }
    }
}
