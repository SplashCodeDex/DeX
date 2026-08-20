using System.Collections.Concurrent;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace DeXShareTarget.Services
{
    public static class WebSocketConnectionManager
    {
        private static readonly ConcurrentDictionary<string, WebSocket> _sockets = new();
        private static readonly ConcurrentDictionary<string, bool> _verified = new();
        private static readonly ConcurrentDictionary<string, SemaphoreSlim> _sendLocks = new();

        public static void AddSocket(string fingerprint, WebSocket socket, bool verified)
        {
            _sockets.AddOrUpdate(fingerprint, socket, (_, old) =>
            {
                if (old != socket && old.State == WebSocketState.Open)
                {
                    try { old.Abort(); } catch { }
                }
                return socket;
            });
            _sendLocks.GetOrAdd(fingerprint, _ => new SemaphoreSlim(1, 1));
            if (verified)
            {
                _verified[fingerprint] = true;
            }
        }

        public static void RemoveSocket(string fingerprint)
        {
            _sockets.TryRemove(fingerprint, out _);
            _verified.TryRemove(fingerprint, out _);
            if (_sendLocks.TryRemove(fingerprint, out var sem))
            {
                sem.Dispose();
            }
        }

        public static bool IsVerified(string fingerprint)
        {
            return _verified.TryGetValue(fingerprint, out var v) && v;
        }

        public static void MarkVerified(string fingerprint)
        {
            _verified[fingerprint] = true;
        }

        public static async Task DisconnectAsync(string fingerprint)
        {
            if (_sockets.TryRemove(fingerprint, out var socket))
            {
                _verified.TryRemove(fingerprint, out _);
                if (_sendLocks.TryRemove(fingerprint, out var sem))
                {
                    sem.Dispose();
                }
                if (socket.State == WebSocketState.Open || socket.State == WebSocketState.CloseReceived)
                {
                    try
                    {
                        using var cts = new CancellationTokenSource(TimeSpan.FromMilliseconds(500));
                        await socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Unpaired", cts.Token);
                    }
                    catch { }
                    finally { try { socket.Dispose(); } catch { } }
                }
            }
        }

        public static void Unverify(string fingerprint)
        {
            _verified.TryRemove(fingerprint, out _);
        }

        public static async Task<bool> SendAsync(string fingerprint, string payload, bool requireVerified = false)
        {
            if (requireVerified && !IsVerified(fingerprint)) return false;
            if (_sockets.TryGetValue(fingerprint, out var socket) && socket.State == WebSocketState.Open)
            {
                if (_sendLocks.TryGetValue(fingerprint, out var sem))
                {
                    await sem.WaitAsync();
                    try
                    {
                        if (socket.State == WebSocketState.Open)
                        {
                            var bytes = Encoding.UTF8.GetBytes(payload);
                            await socket.SendAsync(new System.ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None);
                            return true;
                        }
                    }
                    catch { }
                    finally
                    {
                        sem.Release();
                    }
                }
            }
            return false;
        }

        public static bool HasConnection(string fingerprint)
        {
            return _sockets.ContainsKey(fingerprint);
        }

        public static async Task BroadcastAsync(string payload, bool requireVerified = false)
        {
            var bytes = Encoding.UTF8.GetBytes(payload);
            var segment = new System.ArraySegment<byte>(bytes);
            foreach (var kvp in _sockets)
            {
                if (requireVerified && !IsVerified(kvp.Key)) continue;
                if (kvp.Value.State == WebSocketState.Open)
                {
                    if (_sendLocks.TryGetValue(kvp.Key, out var sem))
                    {
                        await sem.WaitAsync();
                        try
                        {
                            if (kvp.Value.State == WebSocketState.Open)
                            {
                                await kvp.Value.SendAsync(segment, WebSocketMessageType.Text, true, CancellationToken.None);
                            }
                        }
                        catch { }
                        finally
                        {
                            sem.Release();
                        }
                    }
                }
            }
        }
        public static async Task CloseAllAsync()
        {
            try
            {
                var shutdownPayload = "{\"type\":\"server-shutdown\",\"data\":{}}";
                await BroadcastAsync(shutdownPayload, requireVerified: false);
            }
            catch { }

            var sockets = _sockets.ToArray();
            _sockets.Clear();
            _verified.Clear();

            foreach (var kvp in sockets)
            {
                try
                {
                    if (kvp.Value.State == WebSocketState.Open || kvp.Value.State == WebSocketState.CloseReceived)
                    {
                        using var cts = new CancellationTokenSource(TimeSpan.FromMilliseconds(500));
                        await kvp.Value.CloseAsync(WebSocketCloseStatus.NormalClosure, "Server shutting down", cts.Token);
                    }
                }
                catch { }
                finally
                {
                    try { kvp.Value.Dispose(); } catch { }
                }
            }
        }
    }
}
