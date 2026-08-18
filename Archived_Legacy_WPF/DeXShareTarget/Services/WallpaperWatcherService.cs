using System;
using System.IO;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace DeXShareTarget.Services
{
    /// <summary>
    /// Monitors Windows desktop wallpaper changes with 1-second debouncing and 500ms post-write buffer safeguards.
    /// Broadcasts 'wallpaper-updated' WebSocket messages to connected mobile devices.
    /// </summary>
    public static class WallpaperWatcherService
    {
        private static FileSystemWatcher? _watcher;
        private static Timer? _debounceTimer;
        private static readonly object _lock = new object();

        public static void Initialize()
        {
            lock (_lock)
            {
                if (_watcher != null) return;

                try
                {
                    string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                    string themesDir = Path.Combine(appData, "Microsoft", "Windows", "Themes");

                    if (!Directory.Exists(themesDir)) return;

                    _watcher = new FileSystemWatcher(themesDir)
                    {
                        NotifyFilter = NotifyFilters.LastWrite | NotifyFilters.FileName | NotifyFilters.Size,
                        Filter = "*",
                        EnableRaisingEvents = true,
                        InternalBufferSize = 16384
                    };

                    _watcher.Changed += OnWallpaperFileChanged;
                    _watcher.Created += OnWallpaperFileChanged;
                    _watcher.Renamed += OnWallpaperFileChanged;
                    _watcher.Error += OnWatcherError;

                    Console.WriteLine("[WallpaperWatcher] Initialized live wallpaper change monitor.");
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[WallpaperWatcher] Could not initialize watcher: {ex.Message}");
                }
            }
        }

        private static void OnWallpaperFileChanged(object sender, FileSystemEventArgs e)
        {
            // Filter strictly for TranscodedWallpaper files
            if (e.Name != null && !e.Name.StartsWith("TranscodedWallpaper", StringComparison.OrdinalIgnoreCase))
            {
                return;
            }

            lock (_lock)
            {
                // Debounce: Reset timer to 1000ms on every incoming OS file event
                _debounceTimer?.Dispose();
                _debounceTimer = new Timer(async _ => await ProcessDebouncedWallpaperChangeAsync(), null, 1000, Timeout.Infinite);
            }
        }

        private static async Task ProcessDebouncedWallpaperChangeAsync()
        {
            try
            {
                // Post-write buffer delay (500ms) to ensure Windows Explorer finishes writing the file
                await Task.Delay(500);

                // Clear cached wallpaper in WallpaperService
                WallpaperService.InvalidateCache();

                // Fetch fresh wallpaper info to compute new ETag
                var wallpaper = WallpaperService.GetWallpaper480p();
                string etag = wallpaper?.ETag ?? string.Empty;

                var payload = JsonSerializer.Serialize(new
                {
                    type = "wallpaper-updated",
                    data = new { etag, at = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() }
                }, new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });

                Console.WriteLine($"[WallpaperWatcher] Broadcasting debounced wallpaper update (ETag: {etag})");

                await WebSocketConnectionManager.BroadcastAsync(payload, requireVerified: true);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WallpaperWatcher] Error processing debounced event: {ex.Message}");
            }
        }

        private static void OnWatcherError(object sender, ErrorEventArgs e)
        {
            Console.WriteLine($"[WallpaperWatcher] Buffer error notice: {e.GetException()?.Message}");
        }

        public static void Stop()
        {
            lock (_lock)
            {
                try
                {
                    if (_watcher != null)
                    {
                        _watcher.EnableRaisingEvents = false;
                        _watcher.Changed -= OnWallpaperFileChanged;
                        _watcher.Created -= OnWallpaperFileChanged;
                        _watcher.Renamed -= OnWallpaperFileChanged;
                        _watcher.Error -= OnWatcherError;
                        _watcher.Dispose();
                        _watcher = null;
                    }
                }
                catch { }

                try
                {
                    _debounceTimer?.Dispose();
                    _debounceTimer = null;
                }
                catch { }
            }
        }
    }
}
