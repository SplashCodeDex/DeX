using System;
using System.IO;
using System.Threading;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using FlaUI.Core;
using FlaUI.UIA3;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Tools;

namespace DeXShareTarget.UITests
{
    [TestClass]
    public class TransferWindowUITests
    {
        [TestMethod]
        public void TransferWindow_Launch_DisplaysCorrectUIElements()
        {
            string dummyFile = "dummy.txt";
            File.WriteAllText(dummyFile, "test data");

            string exePath = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, @"..\..\..\..\DeXShareTarget\bin\Debug\net10.0-windows10.0.22000.0\DeXShareTarget.exe"));
            var app = Application.Launch(exePath, $"-IP 127.0.0.1 {dummyFile}");
            
            try
            {
                using var automation = new UIA3Automation();
                var window = app.GetMainWindow(automation, TimeSpan.FromSeconds(5));
                Assert.IsNotNull(window, "Main window was not found.");

                // Validate Window Title
                Assert.AreEqual("DeX - Share", window.Title);

                // Find elements by their Name (TextBlock content) or we can search by AutomationId if Name is bound to it in the runtime.
                // In pure WPF, x:Name maps to AutomationId. But for dynamically parsed XAML, let's verify.
                var txtStatus = window.FindFirstDescendant(cf => cf.ByAutomationId("txtStatus"))?.AsLabel();
                Assert.IsNotNull(txtStatus, "Status text block not found.");
                
                Assert.IsFalse(string.IsNullOrEmpty(txtStatus.Text), "txtStatus should contain text.");

                var progressIndicator = window.FindFirstDescendant(cf => cf.ByAutomationId("progressIndicator"));
                Assert.IsNotNull(progressIndicator, "Progress indicator not found.");
                
                var txtSpeed = window.FindFirstDescendant(cf => cf.ByAutomationId("txtSpeed"))?.AsLabel();
                Assert.IsNotNull(txtSpeed, "Speed text block not found.");
            }
            finally
            {
                if (app != null)
                {
                    app.Close();
                    app.Dispose();
                }
                
                if (File.Exists(dummyFile))
                {
                    File.Delete(dummyFile);
                }
            }
        }
    }
}
