using System;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Http;

namespace DeXShareTarget.Services
{
    public static class ClipboardService
    {
        public static bool IsSyncEnabled { get; set; } = false;
        public static (string Text, DateTime At)? LastPhoneClipboard { get; set; }

        public static async Task<IResult> HandlePhonePushAsync(HttpRequest request)
        {
            var auth = request.Headers.Authorization.ToString();
            if (string.IsNullOrEmpty(auth) || !auth.StartsWith("Bearer ")) return Results.StatusCode(401);
            var token = auth.Substring("Bearer ".Length);
            if (!IdentityManager.IsIdentityToken(token) && !IdentityManager.PairedTokens.Values.Contains(token)) return Results.StatusCode(401);

            using var reader = new StreamReader(request.Body);
            var body = await reader.ReadToEndAsync();
            if (string.IsNullOrWhiteSpace(body)) return Results.BadRequest();

            if (!IsSyncEnabled) return Results.Ok();

            var (textToSet, imageBase64) = ParsePayload(body);
            LastPhoneClipboard = (!string.IsNullOrEmpty(imageBase64) ? $"[IMAGE:{imageBase64.Length}]" : textToSet, DateTime.UtcNow);

            if (!string.IsNullOrEmpty(imageBase64))
            {
                SetWindowsClipboardImage(imageBase64);
            }
            else
            {
                SetWindowsClipboardText(textToSet);
            }

            return Results.Ok();
        }

        public static async Task<IResult> HandleLocalPushAsync(HttpRequest request)
        {
            var ip = request.Query["ip"].ToString();
            var fp = request.Query["fingerprint"].ToString();
            if (string.IsNullOrEmpty(fp) && !string.IsNullOrEmpty(ip))
            {
                var dev = DiscoveryBackgroundService.Devices.Values.FirstOrDefault(d => d.Ip == ip);
                if (dev != null) fp = dev.Info.Fingerprint;
            }
            if (string.IsNullOrEmpty(fp)) return Results.NotFound();

            using var reader = new StreamReader(request.Body);
            var body = await reader.ReadToEndAsync();
            if (string.IsNullOrWhiteSpace(body)) return Results.BadRequest();

            var payload = FormatForwardPayload(body);
            var sent = await WebSocketConnectionManager.SendAsync(fp, payload);
            return sent ? Results.Ok() : Results.NotFound();
        }

        public static IResult GetState()
        {
            var last = LastPhoneClipboard;
            return Results.Json(new
            {
                text = last?.Text ?? "",
                at = last.HasValue ? new DateTimeOffset(last.Value.At).ToUnixTimeMilliseconds() : 0L
            });
        }

        private static (string Text, string? ImageBase64) ParsePayload(string body)
        {
            string textToSet = body;
            string? imageBase64 = null;
            try
            {
                using var doc = JsonDocument.Parse(body);
                var root = doc.RootElement;
                if (root.ValueKind == JsonValueKind.Object)
                {
                    if (root.TryGetProperty("imageBase64", out var imgElem)) imageBase64 = imgElem.GetString();
                    else if (root.TryGetProperty("data", out var d1) && d1.TryGetProperty("imageBase64", out var imgElem2)) imageBase64 = imgElem2.GetString();

                    if (root.TryGetProperty("text", out var txtElem)) textToSet = txtElem.GetString() ?? "";
                    else if (root.TryGetProperty("data", out var d2) && d2.TryGetProperty("text", out var txtElem2)) textToSet = txtElem2.GetString() ?? "";
                }
            }
            catch { }

            return (textToSet, imageBase64);
        }

        private static string FormatForwardPayload(string body)
        {
            try
            {
                using var doc = JsonDocument.Parse(body);
                var root = doc.RootElement;
                if (root.ValueKind == JsonValueKind.Object && root.TryGetProperty("type", out var typeElem) && typeElem.GetString() == "set-clipboard")
                {
                    return body;
                }
            }
            catch { }

            return JsonSerializer.Serialize(new { type = "set-clipboard", data = new { text = body } },
                new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
        }

        private static void SetWindowsClipboardImage(string imageBase64)
        {
            try
            {
                var imageBytes = Convert.FromBase64String(imageBase64);
                using var ms = new MemoryStream(imageBytes);
                var decoder = System.Windows.Media.Imaging.BitmapDecoder.Create(
                    ms,
                    System.Windows.Media.Imaging.BitmapCreateOptions.None,
                    System.Windows.Media.Imaging.BitmapCacheOption.OnLoad);
                var frame = decoder.Frames[0];
                frame.Freeze();

                if (System.Windows.Application.Current?.Dispatcher != null)
                {
                    System.Windows.Application.Current.Dispatcher.Invoke(() =>
                    {
                        System.Windows.Clipboard.SetImage(frame);
                    });
                }
                else
                {
                    var thread = new System.Threading.Thread(() =>
                    {
                        try { System.Windows.Clipboard.SetImage(frame); } catch { }
                    });
                    thread.SetApartmentState(System.Threading.ApartmentState.STA);
                    thread.Start();
                    thread.Join(1000);
                }
            }
            catch { }
        }

        private static void SetWindowsClipboardText(string text)
        {
            try
            {
                if (System.Windows.Application.Current?.Dispatcher != null)
                {
                    System.Windows.Application.Current.Dispatcher.Invoke(() =>
                    {
                        System.Windows.Clipboard.SetText(text);
                    });
                }
                else
                {
                    var thread = new System.Threading.Thread(() =>
                    {
                        try { System.Windows.Clipboard.SetText(text); } catch { }
                    });
                    thread.SetApartmentState(System.Threading.ApartmentState.STA);
                    thread.Start();
                    thread.Join(1000);
                }
            }
            catch { }
        }
    }
}
