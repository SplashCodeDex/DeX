using System;
using System.Text.Json;
using System.Threading.Tasks;
using DeXShareTarget.Services;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace DeXShareTarget.Tests
{
    [TestClass]
    public class DexRequestStoreTests
    {
        [TestMethod]
        public void NewPending_ReturnsValidIdAndCreatesState()
        {
            // Act
            string id = DexRequestStore.NewPending("TEST_TYPE");

            // Assert
            Assert.IsFalse(string.IsNullOrWhiteSpace(id));
            var state = DexRequestStore.GetState(id);
            Assert.IsNotNull(state);
            Assert.AreEqual("TEST_TYPE", state.Type);
            Assert.IsFalse(state.Done);
            Assert.IsFalse(state.Cancelled);
        }

        [TestMethod]
        public void UpdateProgress_UpdatesStateProgress()
        {
            // Arrange
            string id = DexRequestStore.NewPending("PROGRESS_TEST");
            using var doc = JsonDocument.Parse("{\"progress\": 50}");
            
            // Act
            DexRequestStore.UpdateProgress(id, doc.RootElement);

            // Assert
            var state = DexRequestStore.GetState(id);
            Assert.IsNotNull(state);
            Assert.IsNotNull(state.Progress);
            Assert.AreEqual(50, state.Progress.Value.GetProperty("progress").GetInt32());
        }

        [TestMethod]
        public void Complete_SetsResultAndMarksDone()
        {
            // Arrange
            string id = DexRequestStore.NewPending("COMPLETE_TEST");
            using var doc = JsonDocument.Parse("{\"success\": true}");

            // Act
            DexRequestStore.Complete(id, doc.RootElement);

            // Assert
            var state = DexRequestStore.GetState(id);
            Assert.IsNotNull(state);
            Assert.IsTrue(state.Done);
            Assert.IsNotNull(state.Result);
            Assert.IsTrue(state.Result.Value.GetProperty("success").GetBoolean());
        }

        [TestMethod]
        public void Cancel_MarksStateAsCancelled()
        {
            // Arrange
            string id = DexRequestStore.NewPending("CANCEL_TEST");

            // Act
            DexRequestStore.Cancel(id);

            // Assert
            var state = DexRequestStore.GetState(id);
            Assert.IsNotNull(state);
            Assert.IsTrue(state.Cancelled);
            Assert.IsFalse(state.Done);
        }

        [TestMethod]
        public void Remove_DeletesState()
        {
            // Arrange
            string id = DexRequestStore.NewPending("REMOVE_TEST");

            // Act
            DexRequestStore.Remove(id);

            // Assert
            var state = DexRequestStore.GetState(id);
            Assert.IsNull(state);
        }

        [TestMethod]
        public async Task WaitAsync_ReturnsResultWhenCompleted()
        {
            // Arrange
            string id = DexRequestStore.NewPending("WAIT_TEST");
            using var doc = JsonDocument.Parse("{\"status\": \"ok\"}");

            // Act
            var waitTask = DexRequestStore.WaitAsync(id, 2);
            DexRequestStore.Complete(id, doc.RootElement);
            var result = await waitTask;

            // Assert
            Assert.IsNotNull(result);
            Assert.AreEqual("ok", result.Value.GetProperty("status").GetString());
        }

        [TestMethod]
        public async Task WaitAsync_ReturnsNullOnTimeout()
        {
            // Arrange
            string id = DexRequestStore.NewPending("TIMEOUT_TEST");

            // Act - wait 1 second
            var waitTask = DexRequestStore.WaitAsync(id, 1);
            var result = await waitTask;

            // Assert
            Assert.IsNull(result);
            // State should be removed on timeout
            Assert.IsNull(DexRequestStore.GetState(id));
        }
    }
}
