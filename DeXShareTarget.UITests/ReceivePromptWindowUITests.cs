using System;
using System.Diagnostics;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using FlaUI.Core;
using FlaUI.UIA3;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Tools;

namespace DeXShareTarget.UITests
{
    [TestClass]
    public class ReceivePromptWindowUITests
    {
        [TestMethod]
        public async Task ReceivePromptWindow_WhenTriggered_ShowsAcceptAndDeclineButtons()
        {
            // Start the application in normal mode so it spins up the local LocalSendServer.
            // We use -Background to avoid showing standard UI or doing ShareTarget things.
            // The server runs on port 53317 (LocalSend default) or dynamic.
            
            string exePath = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, @"..\..\..\..\DeXShareTarget\bin\Debug\net10.0-windows10.0.22000.0\DeXShareTarget.exe"));
            var app = Application.Launch(exePath, "-Background");
            
            try
            {
                // Wait for the server to start (binds to port 53317 by default)
                await Task.Delay(2000);
                
                // Simulate an incoming LocalSend prepare-upload request
                using var http = new HttpClient();
                http.Timeout = TimeSpan.FromSeconds(5);
                
                var payload = new 
                {
                    info = new { alias = "Mock Phone", deviceType = "mobile", fingerprint = "123456789" },
                    files = new object[] 
                    {
                        new { id = "1", fileName = "test.png", size = 100, fileType = "image/png" }
                    }
                };
                
                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
                
                // We send it asynchronously so we don't block the test from interacting with the UI
                var postTask = http.PostAsync("http://127.0.0.1:53317/api/localsend/v2/prepare-upload", content);

                using var automation = new UIA3Automation();
                
                // We must find the "DeX - Receive" window that pops up
                Window? promptWindow = null;
                
                // Retry for up to 5 seconds to find the window
                var retryResult = Retry.WhileNull(() => 
                {
                    return app.GetMainWindow(automation, TimeSpan.FromSeconds(1));
                }, TimeSpan.FromSeconds(5), TimeSpan.FromMilliseconds(200));
                
                promptWindow = retryResult.Result;
                Assert.IsNotNull(promptWindow, "Receive Prompt window did not appear.");
                
                Assert.AreEqual("DeX - Receive", promptWindow.Title);

                var btnAccept = promptWindow.FindFirstDescendant(cf => cf.ByAutomationId("btnAccept"))?.AsButton();
                Assert.IsNotNull(btnAccept, "Accept button not found.");
                
                var btnDecline = promptWindow.FindFirstDescendant(cf => cf.ByAutomationId("btnDecline"))?.AsButton();
                Assert.IsNotNull(btnDecline, "Decline button not found.");

                // Simulate clicking Decline
                btnDecline.Invoke();
                
                // Wait for the HTTP response
                var response = await postTask;
                Assert.AreEqual(System.Net.HttpStatusCode.Forbidden, response.StatusCode, "Declining should return HTTP 403 Forbidden.");
            }
            finally
            {
                if (app != null)
                {
                    app.Close();
                    app.Dispose();
                }
            }
        }
    }
}
