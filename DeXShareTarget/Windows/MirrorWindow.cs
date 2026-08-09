using System.Windows;
using System.Windows.Media.Imaging;

namespace DeXShareTarget.Windows
{
    /// <summary>WPF window that renders JPEG frames streamed from the phone.</summary>
    public class MirrorWindow : Window
    {
        private readonly System.Windows.Controls.Image _image;
        private readonly object _renderLock = new();
        private bool _framePending;

        public MirrorWindow(string alias, Action onClosed)
        {
            Title = $"DeX Mirror — {alias}";
            Width = 540;
            Height = 960;
            Background = System.Windows.Media.Brushes.Black;
            WindowStartupLocation = WindowStartupLocation.CenterScreen;
            ResizeMode = ResizeMode.CanResize;
            MinWidth = 300;
            MinHeight = 400;

            _image = new System.Windows.Controls.Image
            {
                Stretch = System.Windows.Media.Stretch.Uniform,
                Margin = new Thickness(0)
            };
            Content = _image;

            Closed += (_, _) => onClosed();
        }

        /// <summary>
        /// Queues a JPEG frame for display. Frames arriving while a previous one is still
        /// waiting to render are dropped so a slow PC never builds up latency.
        /// </summary>
        public void QueueFrame(byte[] jpeg)
        {
            lock (_renderLock)
            {
                if (_framePending) return; // drop stale frame
                _framePending = true;
            }
            Dispatcher.BeginInvoke(new Action(() =>
            {
                try { SetFrame(jpeg); }
                finally { lock (_renderLock) { _framePending = false; } }
            }));
        }

        /// <summary>Decodes and displays one JPEG frame. Must be called on the window's dispatcher thread.</summary>
        private void SetFrame(byte[] jpeg)
        {
            using var ms = new System.IO.MemoryStream(jpeg);
            var bmp = new BitmapImage();
            bmp.BeginInit();
            bmp.CacheOption = BitmapCacheOption.OnLoad;
            bmp.StreamSource = ms;
            bmp.EndInit();
            bmp.Freeze();
            _image.Source = bmp;
        }
    }
}
