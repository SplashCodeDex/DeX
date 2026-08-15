using System;
using System.Net.WebSockets;
using System.Threading;
using System.Threading.Tasks;
using DeXShareTarget.Services;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class WebSocketConnectionManagerTests
    {
        private class DummyWebSocket : WebSocket
        {
            public override WebSocketCloseStatus? CloseStatus => null;
            public override string? CloseStatusDescription => null;
            public override WebSocketState State { get; }
            public override string? SubProtocol => null;

            public DummyWebSocket(WebSocketState state)
            {
                State = state;
            }

            public override void Abort() { }
            public override Task CloseAsync(WebSocketCloseStatus closeStatus, string? statusDescription, CancellationToken cancellationToken) => Task.CompletedTask;
            public override Task CloseOutputAsync(WebSocketCloseStatus closeStatus, string? statusDescription, CancellationToken cancellationToken) => Task.CompletedTask;
            public override void Dispose() { }
            public override Task<WebSocketReceiveResult> ReceiveAsync(ArraySegment<byte> buffer, CancellationToken cancellationToken) => Task.FromResult(new WebSocketReceiveResult(0, WebSocketMessageType.Text, true));
            public override Task SendAsync(ArraySegment<byte> buffer, WebSocketMessageType messageType, bool endOfMessage, CancellationToken cancellationToken) => Task.CompletedTask;
        }

        [TestCleanup]
        public async Task Cleanup()
        {
            await WebSocketConnectionManager.CloseAllAsync();
        }

        [TestMethod]
        public void AddSocket_AddsConnectionAndMarksVerified()
        {
            var ws = new DummyWebSocket(WebSocketState.Open);
            WebSocketConnectionManager.AddSocket("fp_add", ws, verified: true);

            Assert.IsTrue(WebSocketConnectionManager.HasConnection("fp_add"));
            Assert.IsTrue(WebSocketConnectionManager.IsVerified("fp_add"));
        }

        [TestMethod]
        public void RemoveSocket_RemovesConnection()
        {
            var ws = new DummyWebSocket(WebSocketState.Open);
            WebSocketConnectionManager.AddSocket("fp_remove", ws, true);
            WebSocketConnectionManager.RemoveSocket("fp_remove");

            Assert.IsFalse(WebSocketConnectionManager.HasConnection("fp_remove"));
            Assert.IsFalse(WebSocketConnectionManager.IsVerified("fp_remove"));
        }

        [TestMethod]
        public void MarkVerified_Unverify_TogglesVerificationStatus()
        {
            var ws = new DummyWebSocket(WebSocketState.Open);
            WebSocketConnectionManager.AddSocket("fp_verify", ws, false);
            
            Assert.IsFalse(WebSocketConnectionManager.IsVerified("fp_verify"));
            
            WebSocketConnectionManager.MarkVerified("fp_verify");
            Assert.IsTrue(WebSocketConnectionManager.IsVerified("fp_verify"));

            WebSocketConnectionManager.Unverify("fp_verify");
            Assert.IsFalse(WebSocketConnectionManager.IsVerified("fp_verify"));
        }

        [TestMethod]
        public async Task SendAsync_ReturnsFalse_WhenNotVerifiedAndRequired()
        {
            var ws = new DummyWebSocket(WebSocketState.Open);
            WebSocketConnectionManager.AddSocket("fp_send1", ws, false);

            var result = await WebSocketConnectionManager.SendAsync("fp_send1", "data", requireVerified: true);
            Assert.IsFalse(result);
        }

        [TestMethod]
        public async Task SendAsync_ReturnsTrue_WhenVerifiedAndRequired()
        {
            var ws = new DummyWebSocket(WebSocketState.Open);
            WebSocketConnectionManager.AddSocket("fp_send2", ws, true);

            var result = await WebSocketConnectionManager.SendAsync("fp_send2", "data", requireVerified: true);
            Assert.IsTrue(result);
        }
        
        [TestMethod]
        public async Task DisconnectAsync_RemovesSocket()
        {
            var ws = new DummyWebSocket(WebSocketState.Open);
            WebSocketConnectionManager.AddSocket("fp_disconnect", ws, true);
            
            await WebSocketConnectionManager.DisconnectAsync("fp_disconnect");
            
            Assert.IsFalse(WebSocketConnectionManager.HasConnection("fp_disconnect"));
        }
    }
}
