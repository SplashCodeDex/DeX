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
            string exeDir = Path.GetDirectoryName(exePath);
            string themesDest = Path.Combine(exeDir, "Themes");
            if (!Directory.Exists(themesDest))
            {
                Directory.CreateDirectory(themesDest);
                string themesSource = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, @"..\..\..\..\MSIX_Source\Themes"));
                foreach (var file in Directory.GetFiles(themesSource))
                    File.Copy(file, Path.Combine(themesDest, Path.GetFileName(file)), true);
            }
            
            var app = Application.Launch(exePath, $"-IP 127.0.0.1 {dummyFile}");
            
            try
            {
                using var automation = new UIA3Automation();
                var retryResult = Retry.WhileNull(() => 
                {
                    var desktop = automation.GetDesktop();
                    return desktop.FindFirstChild(cf => cf.ByName("DeX - Share").And(cf.ByProcessId(app.ProcessId)))?.AsWindow();
                }, TimeSpan.FromSeconds(5), TimeSpan.FromMilliseconds(200));
                
                var window = retryResult.Result;
                Assert.IsNotNull(window, "Main window was not found.");
                var txtStatus = window.FindFirstDescendant(cf => cf.ByAutomationId("txtStatus"))?.AsLabel();
                Assert.IsNotNull(txtStatus, "Status text block not found.");
                
                Assert.IsFalse(string.IsNullOrEmpty(txtStatus.Text), "txtStatus should contain text.");

                var txtSpeed = window.FindFirstDescendant(cf => cf.ByAutomationId("txtSpeed"))?.AsLabel();
                Assert.IsNotNull(txtSpeed, "Speed text block not found.");
            }
            finally
            {
                try 
                {
                    if (app != null && !app.HasExited)
                    {
                        app.Close();
                    }
                } 
                catch { }
                
                if (app != null)
                {
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
