using System.Collections.Concurrent;
using System.Text.Json;
using System.Threading.Tasks;

namespace DeXShareTarget.Services
{
    /// <summary>
    /// Tracks PC-initiated File Explorer requests and their phone replies over the WebSocket.
    /// Each request keeps a small state object: the phone's live progress updates land in
    /// [Progress] and the terminal reply lands in [Result]. The PowerShell GUI starts a request
    /// and polls its status (live progress for pulls), or waits for completion for fast calls.
    /// </summary>
    public static class DexRequestStore
    {
        public sealed class DexState
        {
            public string Type { get; init; } = "";
            public string Fingerprint { get; set; } = "";
            public JsonElement? Progress { get; private set; }
            public JsonElement? Result { get; private set; }
            public bool Done { get; private set; }
            public bool Cancelled { get; private set; }
            public DateTime CreatedAt { get; } = DateTime.UtcNow;
            private readonly object _lock = new();

            public void SetProgress(JsonElement p) { lock (_lock) { Progress = p.Clone(); } }
            public void Complete(JsonElement r) { lock (_lock) { Result = r.Clone(); Done = true; } }
            public void Cancel() { lock (_lock) { Cancelled = true; } }
        }

        private static readonly ConcurrentDictionary<string, DexState> _states = new();

        /// <summary>Registers a new pending request and returns its id.</summary>
        public static string NewPending(string type)
        {
            var id = System.Guid.NewGuid().ToString("N");
            _states[id] = new DexState { Type = type };
            return id;
        }

        /// <summary>Routes a live progress message from the phone to the request state.</summary>
        public static void UpdateProgress(string id, JsonElement progress)
        {
            if (_states.TryGetValue(id, out var s)) s.SetProgress(progress);
        }

        /// <summary>Completes the request with the phone's terminal reply.</summary>
        public static void Complete(string id, JsonElement reply)
        {
            if (_states.TryGetValue(id, out var s)) s.Complete(reply);
        }

        /// <summary>Flags the request as cancelled so the caller can surface it.</summary>
        public static void Cancel(string id)
        {
            if (_states.TryGetValue(id, out var s)) s.Cancel();
        }

        public static DexState? GetState(string id)
        {
            _states.TryGetValue(id, out var s);
            return s;
        }

        /// <summary>Waits up to [timeoutSeconds] for the terminal reply, then returns it (or null).</summary>
        public static async Task<JsonElement?> WaitAsync(string id, int timeoutSeconds)
        {
            var s = GetState(id);
            if (s == null) return null;
            var deadline = DateTime.UtcNow.AddSeconds(timeoutSeconds);
            while (DateTime.UtcNow < deadline)
            {
                if (s.Done) return s.Result;
                await Task.Delay(100);
            }
            Remove(id);
            return null;
        }

        public static void Remove(string id) => _states.TryRemove(id, out _);
    }
}
