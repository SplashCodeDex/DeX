using System;
using System.IO;
using System.Threading;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using FlaUI.Core;
using FlaUI.UIA3;
using FlaUI.Core.Tools;
using FlaUI.Core.Definitions;
using FlaUI.Core.WindowsAPI;
using FlaUI.Core.AutomationElements;

namespace DeXShareTarget.UITests
{
    [TestClass]
    [DoNotParallelize]
    public class MirrorWindowUITests
    {
        [TestMethod]
        public void MirrorWindow_Launch_DisplaysCorrectTitleAndSizeConstraints()
        {
            string exePath = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, @"..\..\..\..\DeXShareTarget\bin\Debug\net10.0-windows10.0.22000.0\DeXShareTarget.exe"));
            string alias = "Test Alias";
            
            var app = Application.Launch(exePath, $"-Mirror \"{alias}\"");
            
            Exception threadEx = null;
            var staThread = new Thread(() =>
            {
                try
                {
                    using var automation = new UIA3Automation();
                    var retryResult = Retry.WhileNull(() => 
                    {
                        var desktop = automation.GetDesktop();
                        return desktop.FindFirstChild(cf => cf.ByName($"DeX Mirror — {alias}").And(cf.ByProcessId(app.ProcessId)))?.AsWindow();
                    }, TimeSpan.FromSeconds(5), TimeSpan.FromMilliseconds(200));
                    
                    var window = retryResult.Result;
                    Assert.IsNotNull(window, "Mirror window was not found.");
                    
                    // Assert Window Title
                    Assert.AreEqual($"DeX Mirror — {alias}", window.Title);

                    // Wait for the window to settle
                    Thread.Sleep(500);

                    // Ensure it has an image control
                    var imageControl = window.FindFirstDescendant(cf => cf.ByControlType(ControlType.Image));
                    Assert.IsNotNull(imageControl, "Image control not found in the Mirror Window.");

                    // Test Size Constraints
                    var bounds = window.BoundingRectangle;
                    window.Patterns.Transform.Pattern.Resize(200, 200);
                    Thread.Sleep(500); // Wait for WPF layout pass

                    var newBounds = window.BoundingRectangle;
                    Assert.IsTrue(newBounds.Width >= 300, $"Width should be >= 300, but was {newBounds.Width}");
                    Assert.IsTrue(newBounds.Height >= 400, $"Height should be >= 400, but was {newBounds.Height}");
                }
                catch (Exception ex)
                {
                    threadEx = ex;
                }
            });
            staThread.SetApartmentState(ApartmentState.STA);
            staThread.Start();
            staThread.Join();

            try { if (app != null && !app.HasExited) app.Close(); } catch { }
            if (app != null) app.Dispose();

            if (threadEx != null) throw threadEx;
        }
    }
}
