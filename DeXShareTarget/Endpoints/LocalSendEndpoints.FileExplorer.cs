using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using DeXShareTarget.Services;

namespace DeXShareTarget.Endpoints
{
    public static partial class LocalSendEndpoints
    {
        /// <summary>Registers the PC File Explorer endpoints (list/browse/pull over the phone's WebSocket).</summary>
        public static void MapLocalFileExplorerEndpoints(this WebApplication app)
        {
            // PC File Explorer over the WebSocket (phone exposes its SAF-granted folders).
            // Each endpoint pushes a request to the phone. Fast calls (list/browse/grant) block
            // for the reply; pulls are async so the GUI can show live progress and cancel.
            app.MapPost("/local/dex/list-folders", async (HttpRequest request) =>
            {
                var body = await request.ReadFromJsonAsync<JsonObject>();
                var ip = body?["ip"]?.GetValue<string>() ?? request.Query["ip"].ToString();
                if (string.IsNullOrEmpty(ip)) return Results.BadRequest();
                if (!TrySendDexRequest(ip, "list-shared-folders", null, out var requestId)) return Results.NotFound();
                var reply = await DexRequestStore.WaitAsync(requestId, 25);
                return reply == null ? Results.NotFound() : Results.Json(reply);
            });

            app.MapPost("/local/dex/browse", async (HttpRequest request) =>
            {
                var body = await request.ReadFromJsonAsync<JsonObject>();
                var ip = body?["ip"]?.GetValue<string>() ?? request.Query["ip"].ToString();
                var folderUri = body?["folderUri"]?.GetValue<string>() ?? request.Query["folderUri"].ToString();
                if (string.IsNullOrEmpty(ip) || string.IsNullOrEmpty(folderUri)) return Results.BadRequest();
                var extra = new JsonObject { ["folderUri"] = folderUri };
                if (!TrySendDexRequest(ip, "browse-folder", extra, out var requestId)) return Results.NotFound();
                var reply = await DexRequestStore.WaitAsync(requestId, 25);
                return reply == null ? Results.NotFound() : Results.Json(reply);
            });

            // Pull is asynchronous: returns the requestId immediately so the GUI polls progress.
            app.MapPost("/local/dex/pull", async (HttpRequest request) =>
            {
                var body = await request.ReadFromJsonAsync<JsonObject>();
                var ip = body?["ip"]?.GetValue<string>() ?? request.Query["ip"].ToString();
                var files = body?["files"];
                if (string.IsNullOrEmpty(ip) || files == null) return Results.BadRequest();
                var extra = new JsonObject { ["files"] = files.DeepClone() };
                if (!TrySendDexRequest(ip, "pull-files", extra, out var requestId)) return Results.NotFound();
                return Results.Json(new { requestId });
            });

            // Live progress + terminal result of an in-flight pull.
            app.MapGet("/local/dex/pull-status", (HttpRequest request) =>
            {
                var requestId = request.Query["requestId"].ToString();
                if (string.IsNullOrEmpty(requestId)) return Results.BadRequest();
                var state = DexRequestStore.GetState(requestId);
                if (state == null) return Results.Json(new { done = true, gone = true });
                var obj = new JsonObject
                {
                    ["done"] = state.Done,
                    ["cancelled"] = state.Cancelled
                };
                if (state.Progress != null) obj["progress"] = JsonSerializer.SerializeToNode(state.Progress.Value);
                if (state.Result != null) obj["result"] = JsonSerializer.SerializeToNode(state.Result.Value);
                return Results.Json(obj);
            });

            // Ask the phone to abort an in-flight pull.
            app.MapPost("/local/dex/pull-cancel", (HttpRequest request) =>
            {
                var requestId = request.Query["requestId"].ToString();
                if (string.IsNullOrEmpty(requestId)) return Results.BadRequest();
                var state = DexRequestStore.GetState(requestId);
                if (state == null) return Results.NotFound();
                DexRequestStore.Cancel(requestId);
                if (!string.IsNullOrEmpty(state.Fingerprint))
                {
                    var cancel = JsonSerializer.Serialize(new { type = "pull-cancel", data = new { requestId } },
                        new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                    _ = WebSocketConnectionManager.SendAsync(state.Fingerprint, cancel, requireVerified: false);
                }
                return Results.Ok();
            });

            app.MapPost("/local/dex/grant-folder", async (HttpRequest request) =>
            {
                var body = await request.ReadFromJsonAsync<JsonObject>();
                var ip = body?["ip"]?.GetValue<string>() ?? request.Query["ip"].ToString();
                if (string.IsNullOrEmpty(ip)) return Results.BadRequest();
                if (!TrySendDexRequest(ip, "grant-shared-folder", null, out var requestId)) return Results.NotFound();
                var reply = await DexRequestStore.WaitAsync(requestId, 190);
                return reply == null ? Results.NotFound() : Results.Json(reply);
            });
        }

        /// <summary>
        /// Resolves the phone by LAN IP and forwards a File Explorer request over the WebSocket.
        /// Registers a pending request, records the phone's fingerprint (for cancels) and returns
        /// its requestId. Only verified (paired / same-email) phones are eligible — file browsing
        /// must never reach an untrusted device.
        /// </summary>
        private static bool TrySendDexRequest(string ip, string type, JsonObject? extra, out string requestId)
        {
            requestId = "";
            var dev = DiscoveryBackgroundService.Devices.Values.FirstOrDefault(d => d.Ip == ip);
            if (dev == null || dev.Info == null) return false;
            var fp = dev.Info.Fingerprint;

            requestId = DexRequestStore.NewPending(type);
            var state = DexRequestStore.GetState(requestId);
            if (state != null) state.Fingerprint = fp;

            var data = new JsonObject { ["requestId"] = requestId };
            if (extra != null)
            {
                foreach (var kv in extra)
                {
                    if (kv.Value != null) data[kv.Key] = kv.Value.DeepClone();
                }
            }
            var payload = new JsonObject { ["type"] = type, ["data"] = data };
            var json = payload.ToJsonString();

            return WebSocketConnectionManager.SendAsync(fp, json, requireVerified: true).GetAwaiter().GetResult();
        }
    }
}
