using System;
using DeXShareTarget.Models;
using DeXShareTarget.Services;
using DeXShareTarget.Endpoints;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shell;

namespace DeXShareTarget
{
    public partial class TransferWindow : Window
    {
        private List<string> files;
        public string? TargetIp { get; set; }
        private TextBlock txtStatus;
        private Border progressIndicator;
        private TextBlock txtSpeed;

        public TransferWindow(List<string> filePaths)
        {
            files = filePaths;
            Title = "DeX - Share";
            Width = 420;
            Height = 160;
            WindowStartupLocation = WindowStartupLocation.CenterScreen;
            Background = Brushes.Transparent;
            WindowStyle = WindowStyle.None;
            ResizeMode = ResizeMode.NoResize;
            Topmost = true;
            AllowsTransparency = true;

            string xaml = @"
            <Border xmlns=""http://schemas.microsoft.com/winfx/2006/xaml/presentation""
                    xmlns:x=""http://schemas.microsoft.com/winfx/2006/xaml""
                    Background=""{DynamicResource PrimaryBrush}"" CornerRadius=""12"" BorderBrush=""{DynamicResource AccentBrush}"" BorderThickness=""1"" Margin=""10"">
                <Border.Effect>
                    <DropShadowEffect Color=""Black"" BlurRadius=""15"" ShadowDepth=""0"" Opacity=""0.5""/>
                </Border.Effect>
                <Grid Margin=""20,15,20,15"">
                    <Grid.RowDefinitions>
                        <RowDefinition Height=""Auto""/>
                        <RowDefinition Height=""*"" />
                        <RowDefinition Height=""Auto""/>
                        <RowDefinition Height=""Auto""/>
                    </Grid.RowDefinitions>
                    
                    <TextBlock Text=""Sending to Android Device"" FontSize=""14"" FontWeight=""Bold"" Foreground=""{DynamicResource PrimaryTextBrush}"" Grid.Row=""0"" Margin=""0,0,0,5""/>
                    
                    <TextBlock x:Name=""txtStatus"" Text=""Initializing transfer..."" FontSize=""12"" Foreground=""{DynamicResource SecondaryTextBrush}"" Grid.Row=""1"" Margin=""0,0,0,15"" TextTrimming=""CharacterEllipsis""/>
                    
                    <Border Grid.Row=""2"" Height=""8"" Background=""{DynamicResource AccentBrush}"" CornerRadius=""4"" Margin=""0,0,0,5"" ClipToBounds=""True"">
                        <Border x:Name=""progressIndicator"" Background=""{DynamicResource SecondaryBrush}"" CornerRadius=""4"" Width=""0"" HorizontalAlignment=""Left""/>
                    </Border>
                    
                    <TextBlock x:Name=""txtSpeed"" Text=""0.00 MB/s - 0%"" FontSize=""11"" Foreground=""{DynamicResource SecondaryTextBrush}"" Grid.Row=""3"" HorizontalAlignment=""Right""/>
                </Grid>
            </Border>";

            string themeName = "DarkTheme";
            try 
            {
                var txt = System.IO.File.ReadAllText(System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DeX", "theme.json"));
                if (txt.Contains("LightTheme")) themeName = "LightTheme";
            } catch {}
            var dict = (ResourceDictionary)System.Windows.Markup.XamlReader.Load(new System.IO.FileStream(System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Themes", themeName + ".xaml"), System.IO.FileMode.Open, System.IO.FileAccess.Read));
            this.Resources.MergedDictionaries.Add(dict);

            var rootBorder = (Border)System.Windows.Markup.XamlReader.Parse(xaml);
            txtStatus = (TextBlock)rootBorder.FindName("txtStatus");
            progressIndicator = (Border)rootBorder.FindName("progressIndicator");
            txtSpeed = (TextBlock)rootBorder.FindName("txtSpeed");

            Content = rootBorder;
            
            TaskbarItemInfo = new TaskbarItemInfo { ProgressState = TaskbarItemProgressState.Normal };
            
            // Allow drag to move
            this.MouseLeftButtonDown += (s, e) => this.DragMove();
        }


        public async Task StartTransferAsync()
        {
            try
            {
                if (!string.IsNullOrEmpty(TargetIp))
                {
                    // Resolve the target IP to its fingerprint so the WebSocket push targets the right device
                    var matched = DeXShareTarget.Services.DiscoveryBackgroundService.Devices.Values.FirstOrDefault(d => d.Ip == TargetIp);
                    if (matched != null)
                    {
                        await PerformLocalSendTransferAsync(matched);
                    }
                    else
                    {
                        txtStatus.Text = "Error: Android device not paired or not connected via WebSocket.";
                        await Task.Delay(3000);
                    }
                    return;
                }

                // Try LocalSend Wi-Fi discovery first!
                var wifiDevice = await GetLocalSendDeviceAsync();
                if (wifiDevice != null)
                {
                    if (string.Equals(wifiDevice.Info.DeviceType, "desktop", StringComparison.OrdinalIgnoreCase))
                    {
                        // PC-to-PC: use QUIC P2P Service
                        await PerformNativeQuicP2PHostAsync(wifiDevice);
                    }
                    else
                    {
                        // PC-to-Android
                        await PerformLocalSendTransferAsync(wifiDevice);
                    }
                    return;
                }

                txtStatus.Text = "Error: No target PC or device discovered.";
                await Task.Delay(3000);
            }
            catch (Exception ex)
            {
                txtStatus.Text = "Error: " + ex.Message;
                await Task.Delay(5000);
            }
        }

        private static List<(string Path, string RelativePath)> FlattenFiles(IEnumerable<string> inputs)
        {
            var result = new List<(string, string)>();
            foreach (var input in inputs)
            {
                if (Directory.Exists(input)) Collect(input, "", result);
                else if (File.Exists(input)) result.Add((input, Path.GetFileName(input)));
            }
            return result;
        }

        private static void Collect(string dir, string prefix, List<(string, string)> acc)
        {
            foreach (var file in Directory.GetFiles(dir)) acc.Add((file, prefix + Path.GetFileName(file)));
            foreach (var sub in Directory.GetDirectories(dir)) Collect(sub, prefix + Path.GetFileName(sub) + "/", acc);
        }

        private async Task PerformNativeQuicP2PHostAsync(DiscoveredDevice targetDevice)
        {
            txtStatus.Text = $"Starting QUIC P2P Host for {targetDevice.Info.Alias}...";
            var cert = LocalSendServer.ServerCert;
            if (cert == null) { txtStatus.Text = "Error: Server certificate not available."; await Task.Delay(3000); return; }

            var cts = new CancellationTokenSource();
            var progress = new Progress<TransferProgress>(p => Dispatcher.Invoke(() =>
            {
                var pct = p.TotalBytes > 0 ? (double)p.BytesSent / p.TotalBytes : 0;
                txtStatus.Text = $"Sending: {p.CurrentFile} ({p.DoneFiles}/{p.TotalFiles})";
                txtSpeed.Text = $"{p.BytesSent / 1048576.0:F1} / {p.TotalBytes / 1048576.0:F1} MB — {pct:P0}";
                var parent = (Border)progressIndicator.Parent;
                progressIndicator.Width = parent.ActualWidth * pct;
                TaskbarItemInfo.ProgressValue = pct;
            }));

            var pairs = FlattenFiles(files);

            var (port, waitForCompletion) = await QuicP2PService.HostAsync(pairs, cert, progress, cts.Token);
            
            // Push connection info to target device via WebSocket
            var json = JsonSerializer.Serialize(new { type = "quic-p2p-pull", data = new { port = port, alias = Environment.MachineName } },
                new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                
            bool pushed = await DeXShareTarget.Services.WebSocketConnectionManager.SendAsync(targetDevice.Info.Fingerprint, json, requireVerified: true);

            if (!pushed)
            {
                txtStatus.Text = "Error: Target PC not paired or disconnected.";
                cts.Cancel();
                await Task.Delay(3000);
                return;
            }

            txtStatus.Text = $"Waiting for {targetDevice.Info.Alias} to connect...";
            await waitForCompletion();
            
            var parentFinal = (Border)progressIndicator.Parent;
            var animFinal = new System.Windows.Media.Animation.DoubleAnimation {
                To = parentFinal.ActualWidth,
                Duration = TimeSpan.FromMilliseconds(250),
                EasingFunction = new System.Windows.Media.Animation.QuadraticEase { EasingMode = System.Windows.Media.Animation.EasingMode.EaseOut }
            };
            progressIndicator.BeginAnimation(Border.WidthProperty, animFinal);
            TaskbarItemInfo.ProgressValue = 1.0;
            await Task.Delay(3000);
        }

        private async Task PerformLocalSendTransferAsync(DiscoveredDevice device)
        {
            // Folder bundles: directories are flattened into (path, relativePath) pairs — no zipping
            var pairs = FlattenFiles(files);
            long totalBytes = pairs.Sum(p => new FileInfo(p.Path).Length);
            Stopwatch globalSw = Stopwatch.StartNew();

            if (!DeXShareTarget.Services.WebSocketConnectionManager.IsVerified(device.Info.Fingerprint))
            {
                txtStatus.Text = "Error: Android device not paired or not connected via WebSocket.";
                await Task.Delay(3000);
                return;
            }

            txtStatus.Text = $"Notifying Android to pull {files.Count} files...";

            var pushed = await DeXShareTarget.Services.RelayService.HostAndPushAsync(device.Info.Fingerprint, pairs, Environment.MachineName);
            if (!pushed)
            {
                txtStatus.Text = "Error: Android device not paired or not connected via WebSocket.";
                await Task.Delay(3000);
                return;
            }

            globalSw.Stop();
            txtStatus.Text = "Transfer Signal Sent!";
            txtSpeed.Text = $"{totalBytes / 1048576.0:F1} MB triggered in {globalSw.Elapsed.TotalSeconds:F1}s";
            
            var parentFinal = (Border)progressIndicator.Parent;
            var animFinal = new System.Windows.Media.Animation.DoubleAnimation {
                To = parentFinal.ActualWidth,
                Duration = TimeSpan.FromMilliseconds(250),
                EasingFunction = new System.Windows.Media.Animation.QuadraticEase { EasingMode = System.Windows.Media.Animation.EasingMode.EaseOut }
            };
            progressIndicator.BeginAnimation(Border.WidthProperty, animFinal);
            
            TaskbarItemInfo.ProgressValue = 1.0;
            await Task.Delay(3000);
            // Hosted-file cleanup (sliding 5-minute TTL) is owned by RelayService.HostAndPushAsync
        }



        private async Task<DiscoveredDevice?> GetLocalSendDeviceAsync()
        {
            try 
            {
                using var http = new HttpClient();
                http.Timeout = TimeSpan.FromSeconds(2);
                var res = await http.GetStringAsync($"{DeXConstants.LocalApiBase}/local/devices");
                var list = JsonSerializer.Deserialize<List<DiscoveredDevice>>(res, new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
                // Prefer the mobile DeX client over other desktops on the LAN
                return list?.FirstOrDefault(d => string.Equals(d.Info.DeviceType, "mobile", StringComparison.OrdinalIgnoreCase))
                    ?? list?.FirstOrDefault();
            } 
            catch { return null; }
        }
    }

    public class ProgressableStreamContent : HttpContent
    {
        private readonly Stream content;
        private readonly Action<int> onRead;

        public ProgressableStreamContent(Stream content, Action<int> onRead)
        {
            this.content = content;
            this.onRead = onRead;
        }

        protected override async Task SerializeToStreamAsync(Stream stream, TransportContext? context)
        {
            var buffer = new byte[81920];
            int length;
            while ((length = await content.ReadAsync(buffer, 0, buffer.Length)) > 0)
            {
                await stream.WriteAsync(buffer, 0, length);
                onRead(length);
            }
        }

        protected override bool TryComputeLength(out long length)
        {
            length = content.Length;
            return true;
        }
    }
}
