using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace DeXShareTarget.Services
{
    /// <summary>
    /// PC-side Google Sign-In: OAuth 2.0 Authorization Code + PKCE with a loopback
    /// redirect (http://127.0.0.1:53325). The verified email becomes this PC's identity,
    /// so every same-email device is auto-trusted.
    ///
    /// Credentials live in oauth.local.json (git-ignored — never commit it).
    /// A raw TcpListener is used instead of HttpListener so no URL ACL is required.
    /// </summary>
    public static class GoogleOAuth
    {
        private const int LoopbackPort = 53325;
        private const string RedirectUri = "http://127.0.0.1:53325/";
        private const string AuthorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth";
        private const string TokenUrl = "https://oauth2.googleapis.com/token";

    public record GoogleProfile(string Email, string Name, string Picture, string Sub);

    private static (string ClientId, string ClientSecret)? _cached;

    public static bool IsConfigured() => LoadCredentials() != null;

    /// <summary>Runs the full sign-in flow. Returns the verified profile, or null on failure/cancel.</summary>
    public static async Task<GoogleProfile?> SignInAsync(CancellationToken ct = default)
    {
        var creds = LoadCredentials();
        if (creds == null) return null;

        var verifier = Base64Url(RandomNumberGenerator.GetBytes(32));
        var challenge = Base64Url(SHA256.HashData(Encoding.UTF8.GetBytes(verifier)));
        var state = Guid.NewGuid().ToString("N");

        var authUrl = $"{AuthorizeUrl}?client_id={Uri.EscapeDataString(creds.Value.ClientId)}" +
            $"&redirect_uri={Uri.EscapeDataString(RedirectUri)}&response_type=code" +
            $"&scope={Uri.EscapeDataString("openid email profile")}" +
            $"&code_challenge={challenge}&code_challenge_method=S256&state={state}" +
            $"&prompt=select_account";

        // Open the system browser, then await the loopback redirect
        var codeTask = WaitForCodeAsync(state, ct);
        try
        {
            Process.Start(new ProcessStartInfo(authUrl) { UseShellExecute = true });
        }
        catch { /* browser may fail to launch; the listener still times out */ }

        var code = await codeTask;
        if (code == null) return null;

        // Exchange the authorization code for tokens (desktop-app pattern: secret included)
        using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        var form = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["code"] = code,
            ["client_id"] = creds.Value.ClientId,
            ["client_secret"] = creds.Value.ClientSecret,
            ["redirect_uri"] = RedirectUri,
            ["grant_type"] = "authorization_code",
            ["code_verifier"] = verifier
        });
        var response = await http.PostAsync(TokenUrl, form, ct);
        if (!response.IsSuccessStatusCode) return null;

        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(ct));
        var idToken = json.RootElement.TryGetProperty("id_token", out var t) ? t.GetString() : null;
        if (string.IsNullOrEmpty(idToken)) return null;

        // id_token is a JWT: header.payload.signature — the profile lives in the payload
        var parts = idToken.Split('.');
        if (parts.Length != 3) return null;
        try
        {
            var decoded = Encoding.UTF8.GetString(Convert.FromBase64String(Base64Pad(parts[1])));
            using var claims = JsonDocument.Parse(decoded);
            var email = claims.RootElement.TryGetProperty("email", out var e) ? e.GetString() : null;
            if (string.IsNullOrEmpty(email)) return null;

            var profile = new GoogleProfile(
                Email: email,
                Name: claims.RootElement.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "",
                Picture: claims.RootElement.TryGetProperty("picture", out var p) ? p.GetString() ?? "" : "",
                Sub: claims.RootElement.TryGetProperty("sub", out var s) ? s.GetString() ?? "" : ""
            );
            SaveProfile(profile);
            return profile;
        }
        catch { return null; }
    }

    /// <summary>Persists the signed-in profile so the UI can show it after restarts.</summary>
    public static void SaveProfile(GoogleProfile profile)
    {
        try
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DeX");
            Directory.CreateDirectory(dir);
            File.WriteAllText(Path.Combine(dir, "google_profile.json"), JsonSerializer.Serialize(profile));
        }
        catch { }
    }

    /// <summary>The last signed-in profile, or null when never signed in.</summary>
    public static GoogleProfile? LoadProfile()
    {
        try
        {
            var path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DeX", "google_profile.json");
            if (!File.Exists(path)) return null;
            return JsonSerializer.Deserialize<GoogleProfile>(File.ReadAllText(path));
        }
        catch { return null; }
    }

    /// <summary>Removes the stored profile (sign-out).</summary>
    public static void SignOut()
    {
        try
        {
            var path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DeX", "google_profile.json");
            if (File.Exists(path)) File.Delete(path);
        }
        catch { }
    }

        private static async Task<string?> WaitForCodeAsync(string expectedState, CancellationToken ct)
        {
            using var listener = new TcpListener(IPAddress.Loopback, LoopbackPort);
            listener.Start();
            try
            {
                using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
                timeout.CancelAfter(TimeSpan.FromMinutes(2));
                while (!timeout.IsCancellationRequested)
                {
                    var client = await listener.AcceptTcpClientAsync(timeout.Token);
                    try
                    {
                        using var stream = client.GetStream();
                        var requestBytes = new byte[8192];
                        var read = await stream.ReadAsync(requestBytes.AsMemory(0, requestBytes.Length), timeout.Token);
                        var request = Encoding.ASCII.GetString(requestBytes, 0, read);
                        var firstLine = request.Split('\r', '\n')[0];
                        var path = firstLine.Split(' ').Length > 1 ? firstLine.Split(' ')[1] : "/";
                        var query = path.Contains('?') ? path[(path.IndexOf('?') + 1)..] : "";
                        var code = GetQueryValue(query, "code");
                        var state = GetQueryValue(query, "state");
                        var success = code != null && state == expectedState;

                        var body = success
                            ? "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>DeX signed in — you can close this tab.</h3></body></html>"
                            : "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed or cancelled.</h3></body></html>";
                        var responseText = $"HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {Encoding.UTF8.GetByteCount(body)}\r\nConnection: close\r\n\r\n{body}";
                        var responseBytes = Encoding.UTF8.GetBytes(responseText);
                        await stream.WriteAsync(responseBytes.AsMemory(0, responseBytes.Length), timeout.Token);

                        if (success) return code;
                    }
                    catch { /* keep waiting for the right redirect */ }
                }
            }
            catch { }
            return null;
        }

        private static string? GetQueryValue(string query, string key)
        {
            foreach (var pair in query.Split('&'))
            {
                var eq = pair.IndexOf('=');
                if (eq <= 0) continue;
                var k = Uri.UnescapeDataString(pair[..eq]);
                if (k == key) return Uri.UnescapeDataString(pair[(eq + 1)..]);
            }
            return null;
        }

        private static string Base64Url(byte[] bytes) =>
            Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');

        private static string Base64Pad(string s)
        {
            switch (s.Length % 4)
            {
                case 2: return s + "==";
                case 3: return s + "=";
                default: return s;
            }
        }

        private static (string ClientId, string ClientSecret)? LoadCredentials()
        {
            if (_cached != null) return _cached;
            var file = FindCredentialsFile();
            if (file == null) return null;
            try
            {
                using var doc = JsonDocument.Parse(File.ReadAllText(file));
                var id = doc.RootElement.TryGetProperty("desktopClientId", out var i) ? i.GetString() : null;
                var secret = doc.RootElement.TryGetProperty("desktopClientSecret", out var s) ? s.GetString() : null;
                if (!string.IsNullOrEmpty(id) && !string.IsNullOrEmpty(secret)) _cached = (id, secret);
            }
            catch { }
            return _cached;
        }

        private static string? FindCredentialsFile()
        {
            var candidates = new List<string>
            {
                Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "oauth.local.json"),
                Path.Combine(Directory.GetCurrentDirectory(), "oauth.local.json")
            };
            // Walk up from the output directory toward the source root (dev runs)
            var dir = new DirectoryInfo(AppDomain.CurrentDomain.BaseDirectory);
            for (var i = 0; i < 6 && dir != null; i++, dir = dir.Parent)
            {
                candidates.Add(Path.Combine(dir.FullName, "oauth.local.json"));
                candidates.Add(Path.Combine(dir.FullName, "DeXShareTarget", "oauth.local.json"));
            }
            return candidates.FirstOrDefault(File.Exists);
        }
    }
}
