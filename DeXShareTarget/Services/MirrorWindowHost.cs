using System.Text.Json;
using System.Threading;
using System.Windows.Threading;
using DeXShareTarget.Windows;

namespace DeXShareTarget.Services
{
    /// <summary>
    /// Owns the phone screen-mirror window. Runs the WPF window on a dedicated STA
    /// thread (the hosting process has no Application), routes incoming JPEG frames
    /// to it, and tells the phone to stop streaming when the window is closed.
    /// One active mirror session at a time.
    /// </summary>
    public static class MirrorWindowHost
    {
        private static Thread? _uiThread;
        private static MirrorWindow? _window;
        private static string? _activeFingerprint;
        private static bool _firstFrameReceived;
        private static readonly object Gate = new();

        // If the phone never sends a single frame (consent denied, app not running,
        // socket race), the window would sit black forever. Close it after this long.
        private static readonly TimeSpan FirstFrameTimeout = TimeSpan.FromSeconds(60);

        public static bool IsActive
        {
            get { lock (Gate) { return _window != null; } }
        }

        /// <summary>Fingerprint of the phone currently being mirrored ("" when idle).</summary>
        public static string ActiveFingerprint
        {
            get { lock (Gate) { return _activeFingerprint ?? ""; } }
        }

        /// <summary>Opens the mirror window for a phone and streams its frames.</summary>
        public static void Start(string fingerprint, string alias)
        {
            lock (Gate)
            {
                if (_window != null) return; // one mirror at a time
                _activeFingerprint = fingerprint;
                _firstFrameReceived = false;
            }

            _uiThread = new Thread(() =>
            {
                var window = new MirrorWindow(alias, OnWindowClosed);
                lock (Gate) { _window = window; }

                // Watchdog: if no first frame arrives in time, the phone never started
                var watchdog = new DispatcherTimer { Interval = FirstFrameTimeout };
                watchdog.Tick += (_, _) =>
                {
                    bool gotFirst;
                    lock (Gate) { gotFirst = _firstFrameReceived; }
                    if (!gotFirst) window.Close();
                };
                watchdog.Start();

                var app = new System.Windows.Application();
                app.Run(window);
            });
            _uiThread.SetApartmentState(ApartmentState.STA);
            _uiThread.IsBackground = true;
            _uiThread.Start();
        }

        /// <summary>
        /// Routes an incoming JPEG frame to the active mirror window (any thread).
        /// Returns true when the frame belongs to the active session (even if it was
        /// dropped to bound latency); false when no session is active for this phone —
        /// the caller should then tell the phone to stop streaming.
        /// </summary>
        public static bool PushFrame(string fingerprint, byte[] jpeg)
        {
            MirrorWindow? window;
            lock (Gate)
            {
                if (_activeFingerprint != fingerprint || _window == null) return false;
                window = _window;
                _firstFrameReceived = true;
            }
            window.QueueFrame(jpeg);
            return true;
        }

        /// <summary>Closes the mirror window for a phone (if it is the active session).</summary>
        public static void Stop(string fingerprint)
        {
            MirrorWindow? window;
            lock (Gate)
            {
                if (_activeFingerprint != fingerprint) return;
                window = _window;
            }
            window?.Dispatcher.InvokeAsync(() => window.Close());
        }

        /// <summary>Closes whatever mirror session is active, if any.</summary>
        public static void StopActive()
        {
            lock (Gate)
            {
                if (_window == null) return;
                _window.Dispatcher.InvokeAsync(() => _window.Close());
            }
        }

        private static void OnWindowClosed()
        {
            string? fingerprint;
            lock (Gate)
            {
                fingerprint = _activeFingerprint;
                _window = null;
                _activeFingerprint = null;
                _firstFrameReceived = false;
            }

            if (!string.IsNullOrEmpty(fingerprint))
            {
                // Ask the phone to stop streaming (best effort)
                var stop = JsonSerializer.Serialize(new { type = "mirror-stop", data = new { } },
                    new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                _ = WebSocketConnectionManager.SendAsync(fingerprint, stop);
            }

            // Shut down the STA dispatcher; the thread exits after the window closes
            Dispatcher.CurrentDispatcher.BeginInvokeShutdown(DispatcherPriority.Normal);
        }
    }
}
