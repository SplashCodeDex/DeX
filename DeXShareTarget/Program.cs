using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using Windows.ApplicationModel.Activation;
using Windows.ApplicationModel.DataTransfer.ShareTarget;

namespace DeXShareTarget
{
    class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            var program = new Program();
            program.Run(args).Wait();
        }

        public async Task Run(string[] args)
        {
            global::Windows.ApplicationModel.Activation.IActivatedEventArgs? activatedArgs = null;
            try { activatedArgs = global::Windows.ApplicationModel.AppInstance.GetActivatedEventArgs(); } catch { }

            if (activatedArgs != null && activatedArgs.Kind == ActivationKind.ShareTarget)
            {
                var shareArgs = (ShareTargetActivatedEventArgs)activatedArgs;
                ShareOperation shareOperation = shareArgs.ShareOperation;
                
                var filesTask = shareOperation.Data.GetStorageItemsAsync().AsTask();
                filesTask.Wait();
                var items = filesTask.Result;
                
                var filePaths = new List<string>();
                foreach (var item in items)
                {
                    filePaths.Add(item.Path);
                }
                
                if (filePaths.Count > 0)
                {
                    await SendToComposeAsync(filePaths);
                }
                
                shareOperation.ReportCompleted();
            }
            else
            {
                string exeDir = AppDomain.CurrentDomain.BaseDirectory;
                string scriptPath = Path.GetFullPath(Path.Combine(exeDir, "..\\..\\..\\..\\gradlew.bat"));
                if (File.Exists(scriptPath))
                {
                    var startInfo = new ProcessStartInfo
                    {
                        FileName = scriptPath,
                        Arguments = "run",
                        WorkingDirectory = Path.GetDirectoryName(scriptPath),
                        UseShellExecute = true,
                        CreateNoWindow = true
                    };
                    Process.Start(startInfo);
                }
            }
        }

        private async Task SendToComposeAsync(List<string> files)
        {
            try
            {
                using var client = new HttpClient();
                var payload = new { files = files };
                var json = JsonSerializer.Serialize(payload);
                var content = new StringContent(json, Encoding.UTF8, "application/json");
                await client.PostAsync("http://127.0.0.1:28425/local/share-target", content);
            }
            catch (Exception ex)
            {
                try { File.AppendAllText("crash.log", ex.ToString()); } catch { }
            }
        }
    }
}
