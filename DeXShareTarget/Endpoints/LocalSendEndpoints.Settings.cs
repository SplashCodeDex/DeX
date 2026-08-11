using System.IO;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using DeXShareTarget.Services;

namespace DeXShareTarget.Endpoints
{
    public static partial class LocalSendEndpoints
    {
        /// <summary>Registers the settings / identity endpoints (email, Google sign-in/sign-out/profile).</summary>
        public static void MapLocalSettingsEndpoints(this WebApplication app)
        {
            app.MapPost("/local/settings/email", async (HttpRequest req) => 
            {
                using var reader = new StreamReader(req.Body);
                var email = await reader.ReadToEndAsync();
                IdentityManager.SetEmail(email);
                return Results.Ok();
            });

            // PC-side Google Sign-In: opens the browser (OAuth loopback), then sets the verified email.
            // Reachable at http://127.0.0.1:48425/local/settings/google-signin
            app.MapGet("/local/settings/google-signin", async () =>
            {
                if (!DeXShareTarget.Services.GoogleOAuth.IsConfigured())
                {
                    return Results.Text("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Google Sign-In is not configured (oauth.local.json missing).</h3></body></html>", "text/html");
                }
                var profile = await DeXShareTarget.Services.GoogleOAuth.SignInAsync();
                if (profile != null)
                {
                    IdentityManager.SetEmail(profile.Email);
                    Console.WriteLine($"[OAUTH] Signed in as {profile.Email}");
                    var name = string.IsNullOrEmpty(profile.Name) ? profile.Email : profile.Name;
                    return Results.Text($"<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Signed in as {name} — DeX devices with this email are now auto-trusted.</h3></body></html>", "text/html");
                }
                return Results.Text("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed or was cancelled.</h3></body></html>", "text/html");
            });

            // OAuth browser redirect lands here (Kestrel, port 48425). The browser sends the
            // authorization code as query params; we hand it to the waiting SignInAsync flow.
            app.MapGet("/local/oauth/callback", (string? code, string? state, string? error) =>
            {
                if (!string.IsNullOrEmpty(error))
                {
                    return Results.Text("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed — Google reported an error.</h3></body></html>", "text/html");
                }
                if (string.IsNullOrEmpty(state) || string.IsNullOrEmpty(code))
                {
                    return Results.Text("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed — missing parameters.</h3></body></html>", "text/html");
                }
                var ok = GoogleOAuth.HandleCallback(state, code, out var cbError);
                if (ok)
                {
                    return Results.Text("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>DeX signed in — you can close this tab.</h3></body></html>", "text/html");
                }
                return Results.Text($"<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed — {cbError}</h3></body></html>", "text/html");
            });

            // PC sign-out: clears the email identity and the stored Google profile
            app.MapPost("/local/settings/signout", () =>
            {
                IdentityManager.SetEmail("");
                DeXShareTarget.Services.GoogleOAuth.SignOut();
                Console.WriteLine("[OAUTH] Signed out");
                return Results.Ok();
            });

            // The last signed-in Google profile (name/email/avatar) for the settings UI
            app.MapGet("/local/settings/google-profile", () =>
            {
                var profile = DeXShareTarget.Services.GoogleOAuth.LoadProfile();
                if (profile == null) return Results.Json(new { email = "", name = "", picture = "" });
                return Results.Json(profile);
            });
        }
    }
}
