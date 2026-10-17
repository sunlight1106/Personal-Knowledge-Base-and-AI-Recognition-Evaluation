using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

[assembly: System.Reflection.AssemblyVersion("0.1.0.0")]
[assembly: System.Reflection.AssemblyProduct("Personal Knowledge Base & AI Evaluation")]
// Framework 4.8 is included in supported Windows 10/11 systems. No Python/Node/JDK required.
class Launcher : Form {
    const string Version = "0.1";
    const string RuntimeHash = "__RUNTIME_SHA256__";
    const string BaseUrl = "https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/releases/download/v0.1/";
    readonly Label status = new Label();
    readonly TextBox log = new TextBox();
    readonly Button start = new Button();
    readonly Button logs = new Button();
    readonly string root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PKB-AI", "0.1");
    bool busy;
    readonly bool smoke;
    static string Hash(string path) { using(var stream=File.OpenRead(path)) using(var sha=SHA256.Create()) return BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", "").ToLowerInvariant(); }
    void Report(string message) { if(IsDisposed) return; if(InvokeRequired) { BeginInvoke(new Action<string>(Report), message); return; } status.Text=message; log.AppendText(message+Environment.NewLine); }
    Launcher(bool smokeTest=false) {
        smoke=smokeTest;
        if(smoke) { WindowState=FormWindowState.Minimized; ShowInTaskbar=false; }
        Text="个人知识库 · 0.1 测试版"; ClientSize=new Size(660,430); MinimumSize=new Size(620,440); StartPosition=FormStartPosition.CenterScreen; BackColor=Color.FromArgb(247,248,244); Font=new Font("Microsoft YaHei UI",10); AutoScaleMode=AutoScaleMode.Dpi;
        var title=new Label {Text="留下一点思考，开始今天的学习。",Font=new Font("Microsoft YaHei UI",18),Location=new Point(30,28),AutoSize=true}; Controls.Add(title);
        status.SetBounds(32,82,590,50); status.Text="正在准备…"; Controls.Add(status);
        log.SetBounds(32,143,594,198); log.Multiline=true; log.ReadOnly=true; log.ScrollBars=ScrollBars.Vertical; log.BorderStyle=BorderStyle.FixedSingle; log.BackColor=Color.White; log.Anchor=AnchorStyles.Left|AnchorStyles.Right|AnchorStyles.Top|AnchorStyles.Bottom; Controls.Add(log);
        start.Text="启动平台"; start.SetBounds(32,365,145,40); start.Anchor=AnchorStyles.Bottom|AnchorStyles.Left; start.BackColor=Color.FromArgb(53,94,76); start.ForeColor=Color.White; start.FlatStyle=FlatStyle.Flat; start.Click+=async(s,e)=>await Launch(); Controls.Add(start);
        logs.Text="打开安装目录"; logs.SetBounds(195,365,150,40); logs.Anchor=AnchorStyles.Bottom|AnchorStyles.Left; logs.Click+=(s,e)=>{Directory.CreateDirectory(root); Process.Start("explorer.exe",root);}; Controls.Add(logs);
        FormClosing+=(s,e)=>{if(busy){e.Cancel=true;Report("正在准备平台，请等待完成。可最小化此窗口。");}};
        Shown+=async(s,e)=>await Launch();
    }
    async Task Prepare() {
        string marker=Path.Combine(root,"runtime.verified");
        if(File.Exists(marker) && File.ReadAllText(marker)==RuntimeHash && File.Exists(Path.Combine(root,"release.json")) && File.Exists(Path.Combine(root,"scripts","desktop","start-release.ps1"))) return;
        Directory.CreateDirectory(root);
        string archive=Path.Combine(root,"runtime-0.1.zip"), supplied=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"runtime-0.1.zip");
        if(File.Exists(supplied)) { Report("校验安装包…"); File.Copy(supplied,archive,true); }
        else {
            Report("首次安装：正在下载程序文件…");
            using(var client=new WebClient()) { client.Headers.Add("User-Agent","PKB-Desktop/0.1"); client.DownloadProgressChanged+=(s,e)=>{if(e.ProgressPercentage%10==0) Report("下载程序文件 "+e.ProgressPercentage+"%");}; await client.DownloadFileTaskAsync(new Uri(BaseUrl+"runtime-0.1.zip"),archive+".partial"); }
            if(File.Exists(archive)) File.Delete(archive); File.Move(archive+".partial",archive);
        }
        if(!String.Equals(await Task.Run(()=>Hash(archive)),RuntimeHash,StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("安装包校验失败。请从本项目 v0.1 发布页重新下载。");
        // Validate all entries first; no path traversal, links, user data or giant expanded archives.
        using(var zip=ZipFile.OpenRead(archive)) {
            long total=0; string prefix=Path.GetFullPath(root)+Path.DirectorySeparatorChar;
            foreach(var entry in zip.Entries) {
                string path=Path.GetFullPath(Path.Combine(root,entry.FullName)); total+=entry.Length;
                if(!path.StartsWith(prefix,StringComparison.OrdinalIgnoreCase) || entry.FullName.Contains(":") || entry.FullName==".env" || entry.FullName.StartsWith(".runtime/") || total>200*1024*1024) throw new InvalidDataException("安装包内容无效。");
            }
            foreach(var entry in zip.Entries) {
                string path=Path.GetFullPath(Path.Combine(root,entry.FullName));
                if(String.IsNullOrEmpty(entry.Name)) Directory.CreateDirectory(path);
                else { Directory.CreateDirectory(Path.GetDirectoryName(path)); entry.ExtractToFile(path,true); }
            }
        }
        File.WriteAllText(marker,RuntimeHash);
    }
    async Task Launch() {
        if(busy) return; busy=true; start.Enabled=false;
        try {
            await Prepare(); Report("检查 Docker 和服务状态；首次准备需要下载容器镜像。");
            var process=new Process(); process.StartInfo=new ProcessStartInfo("powershell.exe", "-NoProfile -ExecutionPolicy Bypass -File \""+Path.Combine(root,"scripts","desktop","start-release.ps1")+"\"") { WorkingDirectory=root,UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true,StandardOutputEncoding=Encoding.UTF8,StandardErrorEncoding=Encoding.UTF8 };
            process.OutputDataReceived+=(s,e)=>{if(e.Data!=null) Report(e.Data);}; process.ErrorDataReceived+=(s,e)=>{if(e.Data!=null) Report(e.Data);}; process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine(); await Task.Run(()=>process.WaitForExit());
            int code=process.ExitCode; process.Dispose(); if(code!=0) throw new Exception("启动尚未完成。请查看上方提示，处理后点击重试。日志在安装目录的 .runtime 中。");
            string url=File.ReadAllText(Path.Combine(root,".runtime","desktop-url.txt")).Trim(); Uri parsed;
            if(!Uri.TryCreate(url,UriKind.Absolute,out parsed)||parsed.Scheme!="http"||parsed.Host!="localhost") throw new InvalidDataException("启动地址无效。");
            if(!smoke) Process.Start(new ProcessStartInfo(url){UseShellExecute=true}); Report("已就绪。网页已打开，可以关闭此窗口。再次点击启动会打开现有服务。"); start.Text="打开平台";
        } catch(Exception error) { Report(error.Message); start.Text="重试启动"; if(smoke) Environment.ExitCode=1; }
        finally {busy=false; start.Enabled=true; if(smoke) {Directory.CreateDirectory(root);File.WriteAllText(Path.Combine(root,"launcher-smoke.log"),log.Text);Close();}}
    }
    [STAThread] static int Main(string[] args) {
        ServicePointManager.SecurityProtocol=SecurityProtocolType.Tls12;
        if(args.Length>0 && args[0]=="--self-test") {
            if(RuntimeHash.Length!=64 || Version!="0.1") return 2;
            if(args.Length>1) return Hash(args[1])==RuntimeHash ? 0 : 3;
            return 0;
        }
        bool created; using(var mutex=new Mutex(true,"Local\\PKB-AI-Desktop-Launcher",out created)) {
            if(!created) { MessageBox.Show("启动窗口已经打开，请切换到该窗口。","个人知识库"); return 0; }
            Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false); Application.Run(new Launcher(args.Length>0 && args[0]=="--smoke-test")); return Environment.ExitCode;
        }
    }
}
