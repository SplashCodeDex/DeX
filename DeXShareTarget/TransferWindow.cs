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
                    await PerformLocalSendTransferAsync(wifiDevice);
                    return;
                }


                // Fallback to QUIC PC-to-PC (Internet / NAT Hole Punching)
                await PerformThrufluxHostAsync();
            }
            catch (Exception ex)
            {
                txtStatus.Text = "Error: " + ex.Message;
                await Task.Delay(5000);
            }
        }

        private async Task PerformLocalSendTransferAsync(DiscoveredDevice device)
        {
            long totalBytes = files.Sum(f => new FileInfo(f).Length);
            Stopwatch globalSw = Stopwatch.StartNew();

            if (!DeXShareTarget.Services.WebSocketConnectionManager.IsVerified(device.Info.Fingerprint))
            {
                txtStatus.Text = "Error: Android device not paired or not connected via WebSocket.";
                await Task.Delay(3000);
                return;
            }

            var hostedIds = new List<string>();
            var fileMap = new Dictionary<string, object>();

            foreach (var f in files)
            {
                var fi = new FileInfo(f);
                var fileId = Guid.NewGuid().ToString();
                var pullToken = Guid.NewGuid().ToString();
                
                // 1. Host the file in Kestrel (pulls are authenticated with the per-file token)
                LocalSendEndpoints.HostedFiles[fileId] = f;
                LocalSendEndpoints.HostedFileTokens[fileId] = pullToken;
                LocalSendEndpoints.HostedFileLastAccess[fileId] = DateTime.UtcNow;
                hostedIds.Add(fileId);

                fileMap[fileId] = new 
                {
                    id = fileId,
                    fileName = fi.Name,
                    size = fi.Length,
                    fileType = "application/octet-stream",
                    token = pullToken
                };
            }

            txtStatus.Text = $"Notifying Android to pull {files.Count} files...";

            var prepareReq = new 
            {
                info = new { alias = Environment.MachineName, deviceModel = "PC", deviceType = "desktop", fingerprint = IdentityManager.Fingerprint, port = 53317, protocol = "localsend", download = false },
                files = fileMap
            };

            var wsPayload = new { type = "prepare-upload", data = prepareReq };
            var json = JsonSerializer.Serialize(wsPayload, new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });

            var pushed = await DeXShareTarget.Services.WebSocketConnectionManager.SendAsync(device.Info.Fingerprint, json, requireVerified: true);
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
            
            // Clean up hosted files memory after a while so we don't leak memory (not deleting the physical file).
            // Sliding TTL: a file expires 5 minutes after its last download request, so slow pulls keep working.
            _ = Task.Run(async () => {
                while (true)
                {
                    await Task.Delay(TimeSpan.FromMinutes(1));
                    var now = DateTime.UtcNow;
                    var stale = hostedIds.Where(id =>
                        !LocalSendEndpoints.HostedFileLastAccess.TryGetValue(id, out var last) ||
                        (now - last) > TimeSpan.FromMinutes(5)).ToList();
                    foreach (var id in stale)
                    {
                        LocalSendEndpoints.HostedFiles.TryRemove(id, out _);
                        LocalSendEndpoints.HostedFileTokens.TryRemove(id, out _);
                        LocalSendEndpoints.HostedFileLastAccess.TryRemove(id, out _);
                    }
                    if (!hostedIds.Any(id => LocalSendEndpoints.HostedFiles.ContainsKey(id))) break;
                }
            });
        }

        private async Task PerformThrufluxHostAsync()
        {
            txtStatus.Text = "Starting QUIC P2P Host...";
            
            string exeDir = AppDomain.CurrentDomain.BaseDirectory;
            string thruPath = Path.Combine(exeDir, "bin", "thru.exe");
            
            if (!File.Exists(thruPath))
            {
                txtStatus.Text = "QUIC engine (thru.exe) not found.";
                await Task.Delay(3000);
                return;
            }
            
            string args = "host ";
            foreach(var f in files) {
                args += $"\"{f}\" ";
            }
            
            var proc = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = thruPath,
                    Arguments = args,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true
                }
            };
            
            proc.Start();
            
            string joinCode = "";
            
            _ = Task.Run(() => 
            {
                while (!proc.StandardOutput.EndOfStream)
                {
                    string? line = proc.StandardOutput.ReadLine();
                    if (line != null && line.Contains("thru join"))
                    {
                        int idx = line.IndexOf("thru join");
                        joinCode = line.Substring(idx + 10).Trim();
                        Dispatcher.Invoke(() => {
                            txtStatus.Text = $"QUIC Code: {joinCode} (Waiting...)";
                            Clipboard.SetText(joinCode);
                        });
                    }
                    if (line != null && line.Contains("Transfer complete")) 
                    {
                        Dispatcher.Invoke(() => txtStatus.Text = "QUIC Transfer Complete!");
                    }
                }
            });
            
            await Task.Run(() => proc.WaitForExit());
            
            if (txtStatus.Text.Contains("Waiting")) {
                txtStatus.Text = "QUIC Session Closed.";
            } else {
                txtStatus.Text = "QUIC Transfer Complete!";
            }
            
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

        private async Task<DiscoveredDevice?> GetLocalSendDeviceAsync()
        {
            try 
            {
                using var http = new HttpClient();
                http.Timeout = TimeSpan.FromSeconds(2);
                var res = await http.GetStringAsync("http://127.0.0.1:53318/local/devices");
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
