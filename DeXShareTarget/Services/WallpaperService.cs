using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace DeXShareTarget.Services
{
    /// <summary>
    /// Serves the active Windows desktop wallpaper resized to 480p for ultra-lightweight network transmission to mobile devices.
    /// Includes double-checked lock-free cache reads, multi-monitor slideshow support, and precise MIME content type tracking.
    /// </summary>
    public static class WallpaperService
    {
        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        private static extern int SystemParametersInfo(int uAction, int uParam, StringBuilder lpvParam, int fuWinIni);
        private const int SPI_GETDESKWALLPAPER = 0x0073;

        private static byte[]? _cachedWallpaper;
        private static string _cachedContentType = "image/jpeg";
        private static DateTime _lastFetchTime = DateTime.MinValue;
        private static readonly object _lock = new object();

        public static (byte[] Bytes, string ContentType)? GetWallpaper480p()
        {
            // Fast lock-free read path for cached wallpaper (eliminates lock contention on concurrent requests)
            var cached = _cachedWallpaper;
            if (cached != null && (DateTime.UtcNow - _lastFetchTime).TotalSeconds < 5)
            {
                return (cached, _cachedContentType);
            }

            lock (_lock)
            {
                // Double-check after acquiring lock
                if (_cachedWallpaper != null && (DateTime.UtcNow - _lastFetchTime).TotalSeconds < 5)
                {
                    return (_cachedWallpaper, _cachedContentType);
                }

                var (rawBytes, detectedContentType) = TryReadRawWallpaperBytes();
                if (rawBytes == null || rawBytes.Length == 0)
                {
                    return null;
                }

                try
                {
                    var resized = ResizeTo480p(rawBytes);
                    _cachedWallpaper = resized;
                    _cachedContentType = "image/jpeg";
                    _lastFetchTime = DateTime.UtcNow;
                    return (resized, _cachedContentType);
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[WallpaperService] Downscaling fallback: {ex.Message}");
                    // Fallback to raw bytes with accurate detected MIME type
                    _cachedWallpaper = rawBytes;
                    _cachedContentType = detectedContentType;
                    _lastFetchTime = DateTime.UtcNow;
                    return (rawBytes, detectedContentType);
                }
            }
        }

        private static (byte[]? Bytes, string ContentType) TryReadRawWallpaperBytes()
        {
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string themesDir = Path.Combine(appData, "Microsoft", "Windows", "Themes");

            // 1. Try TranscodedWallpaper or multi-monitor variants (TranscodedWallpaper_000, etc.)
            string[] candidateFiles = new[]
            {
                Path.Combine(themesDir, "TranscodedWallpaper"),
                Path.Combine(themesDir, "TranscodedWallpaper_000"),
                Path.Combine(themesDir, "TranscodedWallpaper_001")
            };

            foreach (var candidate in candidateFiles)
            {
                try
                {
                    if (File.Exists(candidate))
                    {
                        using var fs = new FileStream(candidate, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                        using var ms = new MemoryStream();
                        fs.CopyTo(ms);
                        var bytes = ms.ToArray();
                        if (bytes.Length > 0)
                        {
                            return (bytes, GetContentType(bytes));
                        }
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[WallpaperService] File read notice ({candidate}): {ex.Message}");
                }
            }

            // 2. Try Registry Wallpaper Path
            try
            {
                using var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(@"Control Panel\Desktop");
                var wallpaperPath = key?.GetValue("Wallpaper") as string;
                if (!string.IsNullOrEmpty(wallpaperPath) && File.Exists(wallpaperPath))
                {
                    using var fs = new FileStream(wallpaperPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                    using var ms = new MemoryStream();
                    fs.CopyTo(ms);
                    var bytes = ms.ToArray();
                    if (bytes.Length > 0)
                    {
                        return (bytes, GetContentType(bytes));
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WallpaperService] Registry read notice: {ex.Message}");
            }

            // 3. Try SystemParametersInfo API
            try
            {
                var sb = new StringBuilder(512);
                if (SystemParametersInfo(SPI_GETDESKWALLPAPER, sb.Capacity, sb, 0) != 0)
                {
                    var apiPath = sb.ToString();
                    if (!string.IsNullOrEmpty(apiPath) && File.Exists(apiPath))
                    {
                        using var fs = new FileStream(apiPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                        using var ms = new MemoryStream();
                        fs.CopyTo(ms);
                        var bytes = ms.ToArray();
                        if (bytes.Length > 0)
                        {
                            return (bytes, GetContentType(bytes));
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WallpaperService] SPI API read notice: {ex.Message}");
            }

            // 4. Try CachedFiles directory (Windows slideshow cache)
            try
            {
                string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string cachedDir = Path.Combine(localAppData, "Microsoft", "Windows", "Themes", "CachedFiles");
                if (Directory.Exists(cachedDir))
                {
                    var files = Directory.GetFiles(cachedDir);
                    foreach (var file in files)
                    {
                        if (File.Exists(file))
                        {
                            using var fs = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                            using var ms = new MemoryStream();
                            fs.CopyTo(ms);
                            var bytes = ms.ToArray();
                            if (bytes.Length > 0)
                            {
                                return (bytes, GetContentType(bytes));
                            }
                        }
                    }
                }
            }
            catch { }

            return (null, "image/jpeg");
        }

        private static byte[] ResizeTo480p(byte[] originalBytes)
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

        private static string GetContentType(byte[] bytes)
        {
            if (bytes.Length >= 2 && bytes[0] == 0xFF && bytes[1] == 0xD8) return "image/jpeg";
            if (bytes.Length >= 8 && bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) return "image/png";
            return "image/jpeg";
        }
    }
}
