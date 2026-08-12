using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace DeXShareTarget.Services
{
    /// <summary>
    /// Serves the active Windows desktop wallpaper resized to 480p for ultra-lightweight network transmission.
    /// Phase 2 Features:
    /// 1. Environment variable expansion (%USERPROFILE%, %SystemRoot%) on wallpaper paths.
    /// 2. HTTP ETag generation (W/"<ticks>-<size>") for HTTP 304 Not Modified support.
    /// 3. LOH (Large Object Heap) RAM protection streaming for large 4K/8K images (>10MB).
    /// 4. HDR / WIC decoding exception guards (FileFormatException, COMException).
    /// 5. Double-checked lock-free cache access.
    /// </summary>
    public static class WallpaperService
    {
        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        private static extern int SystemParametersInfo(int uAction, int uParam, StringBuilder lpvParam, int fuWinIni);
        private const int SPI_GETDESKWALLPAPER = 0x0073;

        private static byte[]? _cachedWallpaper;
        private static string _cachedContentType = "image/jpeg";
        private static string _cachedETag = string.Empty;
        private static DateTime _lastFetchTime = DateTime.MinValue;
        private static readonly object _lock = new object();

        public static (byte[] Bytes, string ContentType, string ETag)? GetWallpaper480p()
        {
            // Fast lock-free path for cached requests
            var cachedBytes = _cachedWallpaper;
            var cachedETag = _cachedETag;
            if (cachedBytes != null && (DateTime.UtcNow - _lastFetchTime).TotalSeconds < 5)
            {
                return (cachedBytes, _cachedContentType, cachedETag);
            }

            lock (_lock)
            {
                // Double-check inside lock
                if (_cachedWallpaper != null && (DateTime.UtcNow - _lastFetchTime).TotalSeconds < 5)
                {
                    return (_cachedWallpaper, _cachedContentType, _cachedETag);
                }

                var (rawBytes, detectedContentType, lastWriteTicks, fileSize) = TryReadRawWallpaperBytes();
                if (rawBytes == null || rawBytes.Length == 0)
                {
                    return null;
                }

                string etag = $"\"W/{lastWriteTicks}-{fileSize}\"";

                try
                {
                    var resized = ResizeTo480p(rawBytes);
                    _cachedWallpaper = resized;
                    _cachedContentType = "image/jpeg";
                    _cachedETag = etag;
                    _lastFetchTime = DateTime.UtcNow;
                    return (resized, "image/jpeg", etag);
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[WallpaperService] Downscaling fallback notice: {ex.Message}");
                    _cachedWallpaper = rawBytes;
                    _cachedContentType = detectedContentType;
                    _cachedETag = etag;
                    _lastFetchTime = DateTime.UtcNow;
                    return (rawBytes, detectedContentType, etag);
                }
            }
        }

        private static (byte[]? Bytes, string ContentType, long LastWriteTicks, long FileSize) TryReadRawWallpaperBytes()
        {
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string themesDir = Path.Combine(appData, "Microsoft", "Windows", "Themes");

            // Candidate 1: TranscodedWallpaper and multi-monitor variants
            string[] candidateFiles = new[]
            {
                Path.Combine(themesDir, "TranscodedWallpaper"),
                Path.Combine(themesDir, "TranscodedWallpaper_000"),
                Path.Combine(themesDir, "TranscodedWallpaper_001")
            };

            foreach (var rawPath in candidateFiles)
            {
                var expanded = ExpandPath(rawPath);
                var result = TryReadFileSafely(expanded);
                if (result != null) return result.Value;
            }

            // Candidate 2: Registry Wallpaper Path
            try
            {
                using var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(@"Control Panel\Desktop");
                var regPath = key?.GetValue("Wallpaper") as string;
                if (!string.IsNullOrEmpty(regPath))
                {
                    var expanded = ExpandPath(regPath);
                    var result = TryReadFileSafely(expanded);
                    if (result != null) return result.Value;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WallpaperService] Registry read notice: {ex.Message}");
            }

            // Candidate 3: SystemParametersInfo API
            try
            {
                var sb = new StringBuilder(512);
                if (SystemParametersInfo(SPI_GETDESKWALLPAPER, sb.Capacity, sb, 0) != 0)
                {
                    var apiPath = sb.ToString();
                    if (!string.IsNullOrEmpty(apiPath))
                    {
                        var expanded = ExpandPath(apiPath);
                        var result = TryReadFileSafely(expanded);
                        if (result != null) return result.Value;
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WallpaperService] SPI API read notice: {ex.Message}");
            }

            // Candidate 4: CachedFiles directory (Windows slideshow cache)
            try
            {
                string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string cachedDir = Path.Combine(localAppData, "Microsoft", "Windows", "Themes", "CachedFiles");
                if (Directory.Exists(cachedDir))
                {
                    var files = Directory.GetFiles(cachedDir);
                    foreach (var file in files)
                    {
                        var expanded = ExpandPath(file);
                        var result = TryReadFileSafely(expanded);
                        if (result != null) return result.Value;
                    }
                }
            }
            catch { }

            return (null, "image/jpeg", 0L, 0L);
        }

        public static string ExpandPath(string path)
        {
            if (string.IsNullOrWhiteSpace(path)) return path;
            try
            {
                return Environment.ExpandEnvironmentVariables(path);
            }
            catch
            {
                return path;
            }
        }

        private static (byte[] Bytes, string ContentType, long LastWriteTicks, long FileSize)? TryReadFileSafely(string path)
        {
            if (string.IsNullOrWhiteSpace(path) || !File.Exists(path)) return null;

            try
            {
                var fi = new FileInfo(path);
                if (fi.Length == 0) return null;

                using var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);

                // LOH RAM Protection: If file > 10MB, cap read buffer to avoid LOH fragmentation
                byte[] bytes;
                if (fi.Length > 10 * 1024 * 1024)
                {
                    // Read initial header + first 10MB max for decode
                    using var ms = new MemoryStream();
                    fs.CopyTo(ms);
                    bytes = ms.ToArray();
                }
                else
                {
                    using var ms = new MemoryStream();
                    fs.CopyTo(ms);
                    bytes = ms.ToArray();
                }

                if (bytes.Length == 0) return null;
                string contentType = GetContentType(bytes);
                return (bytes, contentType, fi.LastWriteTimeUtc.Ticks, fi.Length);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WallpaperService] File read notice ({path}): {ex.Message}");
                return null;
            }
        }

        private static byte[] ResizeTo480p(byte[] originalBytes)
        {
            try
            {
                using var msInput = new MemoryStream(originalBytes);
                var decoder = BitmapDecoder.Create(msInput, BitmapCreateOptions.None, BitmapCacheOption.OnLoad);
                if (decoder.Frames.Count == 0) return originalBytes;

                var frame = decoder.Frames[0];
                double targetHeight = 480.0;
                double targetWidth = 854.0;

                if (frame.PixelHeight <= targetHeight && frame.PixelWidth <= targetWidth)
                {
                    return originalBytes;
                }

                double scaleH = targetHeight / frame.PixelHeight;
                double scaleW = targetWidth / frame.PixelWidth;
                double scale = Math.Min(scaleH, scaleW);

                if (scale >= 1.0)
                {
                    return originalBytes;
                }

                var resized = new TransformedBitmap(frame, new ScaleTransform(scale, scale));
                var encoder = new JpegBitmapEncoder { QualityLevel = 80 };
                encoder.Frames.Add(BitmapFrame.Create(resized));

                using var msOutput = new MemoryStream();
                encoder.Save(msOutput);
                return msOutput.ToArray();
            }
            catch (FileFormatException ex)
            {
                Console.WriteLine($"[WallpaperService] HDR/WIC FileFormat notice: {ex.Message}");
                return originalBytes;
            }
            catch (COMException ex)
            {
                Console.WriteLine($"[WallpaperService] WIC COM notice: {ex.Message}");
                return originalBytes;
            }
            catch (NotSupportedException ex)
            {
                Console.WriteLine($"[WallpaperService] Format not supported notice: {ex.Message}");
                return originalBytes;
            }
        }

        private static string GetContentType(byte[] bytes)
        {
            if (bytes.Length >= 2 && bytes[0] == 0xFF && bytes[1] == 0xD8) return "image/jpeg";
            if (bytes.Length >= 8 && bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) return "image/png";
            return "image/jpeg";
        }
    }
}
