using System;
using System.IO;
using System.Threading.Tasks;
using DeXShareTarget.Services;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class HashHelperTests
    {
        private string _tempFile = "";

        [TestInitialize]
        public void Setup()
        {
            _tempFile = Path.GetTempFileName();
        }

        [TestCleanup]
        public void Cleanup()
        {
            if (File.Exists(_tempFile))
            {
                File.Delete(_tempFile);
            }
        }

        [TestMethod]
        public async Task ComputePartialHashAsync_ReturnsNull_WhenFileSizeIsZero()
        {
            // Act
            var hash = await HashHelper.ComputePartialHashAsync(_tempFile, 0);

            // Assert
            Assert.IsNull(hash);
        }

        [TestMethod]
        public async Task ComputePartialHashAsync_ReturnsValidHash_ForSmallFile()
        {
            // Arrange
            byte[] data = new byte[100];
            new Random(42).NextBytes(data); // Deterministic random
            await File.WriteAllBytesAsync(_tempFile, data);

            // Act
            var hash = await HashHelper.ComputePartialHashAsync(_tempFile, data.Length);

            // Assert
            Assert.IsNotNull(hash);
            Assert.IsTrue(hash.Length == 64); // SHA256 hex string length
        }

        [TestMethod]
        public async Task ComputePartialHashAsync_ReturnsValidHash_ForLargeFile()
        {
            // Arrange
            // 32KB * 3 = 98304 bytes. Greater than PartialSize * 2.
            int size = 98304;
            byte[] data = new byte[size];
            new Random(42).NextBytes(data);
            await File.WriteAllBytesAsync(_tempFile, data);

            // Act
            var hash = await HashHelper.ComputePartialHashAsync(_tempFile, size);

            // Assert
            Assert.IsNotNull(hash);
            Assert.IsTrue(hash.Length == 64);
        }

        [TestMethod]
        public async Task ComputePartialHashAsync_ReturnsNull_ForNonExistentFile()
        {
            // Arrange
            string badFile = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());

            // Act
            var hash = await HashHelper.ComputePartialHashAsync(badFile, 100);

            // Assert
            Assert.IsNull(hash);
        }

        [TestMethod]
        public async Task ComputePartialHashAsync_IsConsistent()
        {
            // Arrange
            byte[] data = new byte[1024];
            new Random().NextBytes(data);
            await File.WriteAllBytesAsync(_tempFile, data);

            // Act
            var hash1 = await HashHelper.ComputePartialHashAsync(_tempFile, data.Length);
            var hash2 = await HashHelper.ComputePartialHashAsync(_tempFile, data.Length);

            // Assert
            Assert.AreEqual(hash1, hash2);
        }
    }
}
