using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace CameraAutoUpload.Windows;

public static class SettingsStore
{
    public static AppSettings Load()
    {
        AppPaths.Ensure();
        try
        {
            if (File.Exists(AppPaths.SettingsFile))
                return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(AppPaths.SettingsFile), JsonOptions.Default) ?? new();
        }
        catch { }
        return new();
    }

    public static void Save(AppSettings settings)
    {
        AppPaths.Ensure();
        AtomicFile.WriteAllText(AppPaths.SettingsFile, JsonSerializer.Serialize(settings, JsonOptions.Default));
    }
}

/// <summary>Write to a temp file then swap it in, so a crash or power cut can never leave a half-written (and then silently reset) settings or queue file.</summary>
public static class AtomicFile
{
    public static void WriteAllText(string path, string text)
    {
        var tmp = path + ".tmp";
        File.WriteAllText(tmp, text);
        if (File.Exists(path)) File.Replace(tmp, path, null, true);
        else File.Move(tmp, path);
    }
}

public sealed class WatchFolderService : IDisposable
{
    FileSystemWatcher? watcher;
    readonly ConcurrentDictionary<string, DateTime> recentlySeen = new(StringComparer.OrdinalIgnoreCase);
    public event Func<string, Task>? FileArrived;
    public event Action<string>? Log;

    public void Start(string folder)
    {
        Stop();
        if (string.IsNullOrWhiteSpace(folder) || !Directory.Exists(folder)) return;
        watchedFolder = folder;
        startedUtc = DateTime.UtcNow;
        watcher = new FileSystemWatcher(folder)
        {
            IncludeSubdirectories = true,
            NotifyFilter = NotifyFilters.FileName | NotifyFilters.CreationTime | NotifyFilters.Size | NotifyFilters.LastWrite,
            Filter = "*.*",
            // 64 KB (the maximum) so a fast burst doesn't overflow Windows' change buffer.
            InternalBufferSize = 64 * 1024
        };
        watcher.Created += OnFile;
        watcher.Renamed += OnRename;
        watcher.Error += OnWatcherError;
        watcher.EnableRaisingEvents = true;
        Log?.Invoke($"Watching tether folder: {folder}");
    }

    string watchedFolder = "";
    DateTime startedUtc;

    void OnFile(object? s, FileSystemEventArgs e) => _ = HandleAsync(e.FullPath);
    void OnRename(object? s, RenamedEventArgs e) => _ = HandleAsync(e.FullPath);

    // If Windows' change buffer overflowed during a burst, events were lost: rescan for new files.
    void OnWatcherError(object? s, ErrorEventArgs e)
    {
        Log?.Invoke("Tether folder watcher overflowed — rescanning for new photos");
        var folder = watchedFolder;
        _ = Task.Run(async () =>
        {
            try
            {
                foreach (var f in Directory.EnumerateFiles(folder, "*.*", SearchOption.AllDirectories))
                    if (IsPhoto(f) && File.GetLastWriteTimeUtc(f) >= startedUtc && !recentlySeen.ContainsKey(f)) await HandleAsync(f);
            }
            catch (Exception ex) { Log?.Invoke("Rescan: " + ex.Message); }
        });
    }

    async Task HandleAsync(string path)
    {
        if (!IsPhoto(path)) return;
        if (!recentlySeen.TryAdd(path, DateTime.UtcNow))
        {
            if (DateTime.UtcNow - recentlySeen[path] < TimeSpan.FromMinutes(10)) return;
            recentlySeen[path] = DateTime.UtcNow;
        }
        if (recentlySeen.Count > 20000)
            foreach (var k in recentlySeen.Where(x => DateTime.UtcNow - x.Value > TimeSpan.FromHours(1)).Select(x => x.Key).ToList()) recentlySeen.TryRemove(k, out _);

        // A file is complete only when the writer has closed it (we can open it exclusively)
        // and its size has stopped changing. Previously a file still being written could be
        // copied and uploaded half-finished.
        long lastSize = -1;
        var ready = false;
        for (var i = 0; i < 240 && !ready; i++)
        {
            try
            {
                using var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.None);
                if (fs.Length > 0 && fs.Length == lastSize) ready = true;
                else lastSize = fs.Length;
            }
            catch (FileNotFoundException) { return; }
            catch (DirectoryNotFoundException) { return; }
            catch (IOException) { }
            catch (UnauthorizedAccessException) { }
            if (!ready) await Task.Delay(500);
        }
        if (!ready) { Log?.Invoke($"Tether file never finished writing, skipped: {Path.GetFileName(path)}"); recentlySeen.TryRemove(path, out _); return; }
        Log?.Invoke($"Tether file received: {Path.GetFileName(path)}");
        if (FileArrived != null)
        {
            try { await FileArrived(path); }
            catch (Exception ex) { Log?.Invoke("Tether import: " + ex.Message); }
        }
    }

    static bool IsPhoto(string p)
    {
        var e = Path.GetExtension(p).ToLowerInvariant();
        return e is ".jpg" or ".jpeg" or ".png" or ".nef" or ".nrw" or ".tif" or ".tiff";
    }

    public void Stop()
    {
        if (watcher != null)
        {
            watcher.EnableRaisingEvents = false;
            watcher.Dispose();
            watcher = null;
        }
    }
    public void Dispose() => Stop();
}

public sealed class FtpReceiverService : IDisposable
{
    TcpListener? listener;
    CancellationTokenSource? cts;
    string root = "";
    string user = "nikon";
    string pass = "nikon";
    public bool Running => listener != null;
    public event Func<string, Task>? FileReceived;
    public event Action<string>? Log;

    // Processing (copy, editor, queue) runs off the camera's connection so the camera can send
    // the next file immediately. Files are processed in arrival order.
    readonly SemaphoreSlim processingOrder = new(1, 1);
    void HandOff(string path)
    {
        _ = Task.Run(async () =>
        {
            await processingOrder.WaitAsync();
            try { if (FileReceived != null) await FileReceived(path); }
            catch (Exception ex) { Log?.Invoke("Processing " + Path.GetFileName(path) + ": " + ex.Message); }
            finally { processingOrder.Release(); }
        });
    }

    public void Start(int port, string username, string password, string outputFolder)
    {
        Stop();
        root = outputFolder;
        user = username;
        pass = password;
        Directory.CreateDirectory(root);
        cts = new CancellationTokenSource();
        listener = new TcpListener(IPAddress.Any, port);
        listener.Start();
        _ = AcceptLoop(cts.Token);
        Log?.Invoke($"FTP receiver listening on port {port}");
    }

    async Task AcceptLoop(CancellationToken token)
    {
        while (!token.IsCancellationRequested && listener != null)
        {
            try
            {
                var client = await listener.AcceptTcpClientAsync(token);
                _ = HandleClient(client, token);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { Log?.Invoke("FTP accept: " + ex.Message); await Task.Delay(500, token).ContinueWith(_ => { }); }
        }
    }

    async Task HandleClient(TcpClient client, CancellationToken token)
    {
        TcpListener? passive = null;
        var storedSizes = new Dictionary<string, long>(StringComparer.OrdinalIgnoreCase);
        try {
        using (client)
        using (var stream = client.GetStream())
        using (var reader = new StreamReader(stream, Encoding.ASCII, false, 4096, true))
        using (var writer = new StreamWriter(stream, Encoding.ASCII, 4096, true) { NewLine = "\r\n", AutoFlush = true })
        {
            await writer.WriteLineAsync("220 Camera Auto Upload Windows FTP ready");
            var authed = string.IsNullOrEmpty(pass);
            while (!token.IsCancellationRequested)
            {
                var line = await reader.ReadLineAsync();
                if (line == null) break;
                var split = line.IndexOf(' ');
                var cmd = (split < 0 ? line : line[..split]).Trim().ToUpperInvariant();
                var arg = split < 0 ? "" : line[(split + 1)..].Trim();
                switch (cmd)
                {
                    case "USER": await writer.WriteLineAsync(string.Equals(arg, user, StringComparison.Ordinal) ? "331 Password required" : "530 Invalid user"); break;
                    case "PASS": authed = string.IsNullOrEmpty(pass) || string.Equals(arg, pass, StringComparison.Ordinal); await writer.WriteLineAsync(authed ? "230 Logged on" : "530 Login incorrect"); break;
                    case "SYST": await writer.WriteLineAsync("215 UNIX Type: L8"); break;
                    case "FEAT": await writer.WriteLineAsync("211-Features\r\n UTF8\r\n SIZE\r\n211 End"); break;
                    case "OPTS": await writer.WriteLineAsync("200 OK"); break;
                    case "TYPE": await writer.WriteLineAsync("200 Type set"); break;
                    case "PWD": await writer.WriteLineAsync("257 \"/\""); break;
                    case "CWD": await writer.WriteLineAsync("250 Directory changed"); break;
                    case "NOOP": await writer.WriteLineAsync("200 OK"); break;
                    case "SIZE":
                    {
                        var key = SanitizeFileName(Path.GetFileName(arg));
                        await writer.WriteLineAsync(storedSizes.TryGetValue(key, out var size) ? $"213 {size}" : "550 File not found");
                        break;
                    }
                    case "XPWD": await writer.WriteLineAsync("257 \"/\""); break;
                    case "XCWD": case "CDUP": case "XCUP": await writer.WriteLineAsync("250 Directory changed"); break;
                    case "MKD": case "XMKD": await writer.WriteLineAsync("257 Directory created"); break;
                    case "MODE": await writer.WriteLineAsync("200 Mode S"); break;
                    case "STRU": await writer.WriteLineAsync("200 Structure F"); break;
                    case "ALLO": await writer.WriteLineAsync("202 No storage allocation necessary"); break;
                    case "REST":
                        // Resuming would produce a file missing its beginning; ask for the whole file.
                        await writer.WriteLineAsync(arg == "0" ? "350 Restart position accepted" : "504 Resume not supported; send the whole file");
                        break;
                    case "APPE": await writer.WriteLineAsync("502 APPE not supported; use STOR"); break;
                    case "LIST":
                    case "NLST":
                    {
                        if (!authed) { await writer.WriteLineAsync("530 Not logged in"); break; }
                        if (passive == null) { await writer.WriteLineAsync("425 Use PASV first"); break; }
                        await writer.WriteLineAsync("150 Opening data connection");
                        try
                        {
                            using var dataClient = await passive.AcceptTcpClientAsync(token).AsTask().WaitAsync(TimeSpan.FromMinutes(2), token);
                            await using var data = dataClient.GetStream();
                            if (cmd == "LIST") { var b = Encoding.ASCII.GetBytes("drwxr-xr-x 1 nikon nikon 0 Jan 01 00:00 .\r\n"); await data.WriteAsync(b, token); }
                            await writer.WriteLineAsync("226 Transfer complete");
                        }
                        catch (Exception) { await writer.WriteLineAsync("425 Could not open data connection"); }
                        finally { passive.Stop(); passive = null; }
                        break;
                    }
                    case "PASV":
                    {
                        passive?.Stop();
                        passive = new TcpListener(IPAddress.Any, 0);
                        passive.Start();
                        var ep = (IPEndPoint)passive.LocalEndpoint;
                        var localIp = ((IPEndPoint)client.Client.LocalEndPoint!).Address;
                        if (localIp.Equals(IPAddress.Any) || localIp.Equals(IPAddress.Loopback)) localIp = FindLocalIPv4();
                        var b = localIp.MapToIPv4().GetAddressBytes();
                        await writer.WriteLineAsync($"227 Entering Passive Mode ({b[0]},{b[1]},{b[2]},{b[3]},{ep.Port / 256},{ep.Port % 256})");
                        break;
                    }
                    case "EPSV":
                    {
                        passive?.Stop();
                        passive = new TcpListener(IPAddress.Any, 0);
                        passive.Start();
                        var ep = (IPEndPoint)passive.LocalEndpoint;
                        await writer.WriteLineAsync($"229 Entering Extended Passive Mode (|||{ep.Port}|)");
                        break;
                    }
                    case "STOR":
                    {
                        if (!authed) { await writer.WriteLineAsync("530 Not logged in"); break; }
                        if (passive == null) { await writer.WriteLineAsync("425 Use PASV first"); break; }
                        await writer.WriteLineAsync("150 Opening binary mode data connection");
                        var clean = SanitizeFileName(Path.GetFileName(arg));
                        if (string.IsNullOrWhiteSpace(clean)) clean = "camera_" + DateTime.Now.ToString("yyyyMMdd_HHmmssfff") + ".jpg";
                        // Receive into a hidden .part file; only a fully received file gets its real name.
                        var part = Path.Combine(root, "." + Guid.NewGuid().ToString("N") + ".part");
                        string? target = null;
                        try
                        {
                            long bytes;
                            using (var dataClient = await passive.AcceptTcpClientAsync(token).AsTask().WaitAsync(TimeSpan.FromMinutes(2), token))
                            {
                                dataClient.ReceiveBufferSize = 1024 * 1024;
                                await using var data = dataClient.GetStream();
                                await using var file = new FileStream(part, FileMode.CreateNew, FileAccess.Write, FileShare.None, 256 * 1024, true);
                                await data.CopyToAsync(file, 256 * 1024, token);
                                bytes = file.Length;
                            }
                            if (bytes == 0) { File.Delete(part); await writer.WriteLineAsync("226 Transfer complete"); break; }
                            lock (renameLock) { target = UniquePath(root, clean); File.Move(part, target); }
                            storedSizes[clean] = bytes;
                            await writer.WriteLineAsync("226 Transfer complete");
                            Log?.Invoke($"FTP received: {Path.GetFileName(target)}");
                            HandOff(target);
                        }
                        catch (Exception ex)
                        {
                            try { if (File.Exists(part)) File.Delete(part); } catch { }
                            await writer.WriteLineAsync("426 Transfer aborted; please resend");
                            Log?.Invoke("FTP transfer incomplete (camera will resend): " + ex.Message);
                        }
                        finally { passive?.Stop(); passive = null; }
                        break;
                    }
                    case "QUIT": await writer.WriteLineAsync("221 Goodbye"); return;
                    default: await writer.WriteLineAsync("200 OK"); break;
                }
            }
        }
        }
        catch (Exception ex) when (ex is IOException or SocketException or ObjectDisposedException or OperationCanceledException)
        {
            if (!token.IsCancellationRequested) Log?.Invoke("Camera FTP session ended: " + ex.Message);
        }
        finally { passive?.Stop(); }
    }

    readonly object renameLock = new();

    static IPAddress FindLocalIPv4()
    {
        try
        {
            foreach (var a in Dns.GetHostAddresses(Dns.GetHostName()))
                if (a.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(a)) return a;
        }
        catch { }
        return IPAddress.Loopback;
    }

    static string SanitizeFileName(string name)
    {
        foreach (var c in Path.GetInvalidFileNameChars()) name = name.Replace(c, '_');
        return name;
    }

    public static string UniquePath(string folder, string file)
    {
        var p = Path.Combine(folder, file);
        if (!File.Exists(p)) return p;
        var n = Path.GetFileNameWithoutExtension(file); var e = Path.GetExtension(file);
        for (var i = 1; i < 10000; i++) { p = Path.Combine(folder, $"{n}_{i}{e}"); if (!File.Exists(p)) return p; }
        return Path.Combine(folder, $"{n}_{Guid.NewGuid():N}{e}");
    }

    public void Stop()
    {
        cts?.Cancel(); cts?.Dispose(); cts = null;
        try { listener?.Stop(); } catch { }
        listener = null;
        Log?.Invoke("FTP receiver stopped");
    }
    public void Dispose() => Stop();
}

public sealed class FlickrClient
{
    readonly HttpClient http = new() { Timeout = TimeSpan.FromMinutes(3) };

    public async Task<string> UploadAsync(AppSettings s, string filePath, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(s.FlickrApiKey) || string.IsNullOrWhiteSpace(s.FlickrApiSecret) || string.IsNullOrWhiteSpace(s.FlickrToken) || string.IsNullOrWhiteSpace(s.FlickrTokenSecret))
            throw new InvalidOperationException("Flickr API key/secret/token are not configured.");

        const string url = "https://up.flickr.com/services/upload/";
        var oauth = new SortedDictionary<string, string>(StringComparer.Ordinal)
        {
            ["oauth_consumer_key"] = s.FlickrApiKey,
            ["oauth_nonce"] = Guid.NewGuid().ToString("N"),
            ["oauth_signature_method"] = "HMAC-SHA1",
            ["oauth_timestamp"] = DateTimeOffset.UtcNow.ToUnixTimeSeconds().ToString(),
            ["oauth_token"] = s.FlickrToken,
            ["oauth_version"] = "1.0",
            ["is_public"] = "0",
            ["is_friend"] = "0",
            ["is_family"] = "0",
            ["hidden"] = "2"
        };
        var signature = Sign("POST", url, oauth, s.FlickrApiSecret, s.FlickrTokenSecret);
        var authPairs = oauth.Where(k => k.Key.StartsWith("oauth_", StringComparison.Ordinal)).ToDictionary(k => k.Key, v => v.Value);
        authPairs["oauth_signature"] = signature;
        var authHeader = "OAuth " + string.Join(", ", authPairs.OrderBy(x => x.Key).Select(x => $"{Encode(x.Key)}=\"{Encode(x.Value)}\""));

        using var form = new MultipartFormDataContent();
        form.Add(new StringContent("0"), "is_public");
        form.Add(new StringContent("0"), "is_friend");
        form.Add(new StringContent("0"), "is_family");
        form.Add(new StringContent("2"), "hidden");
        await using var fs = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read);
        using var file = new StreamContent(fs);
        file.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("image/jpeg");
        form.Add(file, "photo", Path.GetFileName(filePath));
        using var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = form };
        req.Headers.TryAddWithoutValidation("Authorization", authHeader);
        using var resp = await http.SendAsync(req, ct);
        var body = await resp.Content.ReadAsStringAsync(ct);
        if (!resp.IsSuccessStatusCode || !body.Contains("stat=\"ok\"", StringComparison.OrdinalIgnoreCase))
            throw new InvalidOperationException("Flickr upload failed: " + body);
        var a = body.IndexOf("<photoid>", StringComparison.OrdinalIgnoreCase);
        var b = body.IndexOf("</photoid>", StringComparison.OrdinalIgnoreCase);
        return a >= 0 && b > a ? body[(a + 9)..b] : "uploaded";
    }

    static string Sign(string method, string url, SortedDictionary<string, string> values, string consumerSecret, string tokenSecret)
    {
        var normalized = string.Join("&", values.OrderBy(x => x.Key).Select(x => Encode(x.Key) + "=" + Encode(x.Value)));
        var baseString = method.ToUpperInvariant() + "&" + Encode(url) + "&" + Encode(normalized);
        var key = Encode(consumerSecret) + "&" + Encode(tokenSecret);
        using var hmac = new HMACSHA1(Encoding.ASCII.GetBytes(key));
        return Convert.ToBase64String(hmac.ComputeHash(Encoding.ASCII.GetBytes(baseString)));
    }
    static string Encode(string s) => Uri.EscapeDataString(s).Replace("%7E", "~", StringComparison.OrdinalIgnoreCase);
}

public sealed class UploadQueueService
{
    readonly SemaphoreSlim gate = new(1, 1);
    readonly FlickrClient flickr = new();
    List<UploadItem> queue = new();
    CancellationTokenSource? loopCts;
    public event Action<string>? Log;
    public event Action<int>? CountChanged;
    public int Count => queue.Count;

    public void Load()
    {
        AppPaths.Ensure();
        try { if (File.Exists(AppPaths.QueueFile)) queue = JsonSerializer.Deserialize<List<UploadItem>>(File.ReadAllText(AppPaths.QueueFile), JsonOptions.Default) ?? new(); }
        catch { queue = new(); }
        CountChanged?.Invoke(queue.Count);
    }

    public async Task EnqueueAsync(string path)
    {
        if (!File.Exists(path)) return;
        await gate.WaitAsync();
        try
        {
            if (!queue.Any(x => string.Equals(x.FilePath, path, StringComparison.OrdinalIgnoreCase))) queue.Add(new UploadItem { FilePath = path });
            Save();
        }
        finally { gate.Release(); }
        CountChanged?.Invoke(queue.Count);
        wake.Release();
    }

    readonly SemaphoreSlim wake = new(0, int.MaxValue);

    public void Start(Func<AppSettings> settingsProvider)
    {
        loopCts?.Cancel(); loopCts = new CancellationTokenSource();
        _ = Task.Run(() => Loop(settingsProvider, loopCts.Token));
    }

    async Task Loop(Func<AppSettings> getSettings, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                var s = getSettings();
                if (s.FlickrEnabled)
                {
                    UploadItem? item = null;
                    await gate.WaitAsync(ct);
                    try
                    {
                        // Drop entries whose file is gone, then take the oldest item that isn't
                        // waiting out a retry delay. One bad photo can no longer block the queue.
                        var missing = queue.RemoveAll(x => !File.Exists(x.FilePath));
                        if (missing > 0) { Save(); Log?.Invoke($"Removed {missing} queue item(s) whose file no longer exists"); }
                        var now = DateTime.UtcNow;
                        item = queue.Where(x => x.NextAttemptUtc <= now).OrderBy(x => x.Attempts > 0 ? 1 : 0).ThenBy(x => x.AddedUtc).FirstOrDefault();
                    }
                    finally { gate.Release(); }
                    if (missingCountChanged()) CountChanged?.Invoke(queue.Count);
                    if (item != null)
                    {
                        try
                        {
                            Log?.Invoke("Uploading to Flickr: " + Path.GetFileName(item.FilePath));
                            var id = await flickr.UploadAsync(s, item.FilePath, ct);
                            await gate.WaitAsync(ct);
                            try { queue.RemoveAll(x => x.Id == item.Id); Save(); }
                            finally { gate.Release(); }
                            CountChanged?.Invoke(queue.Count);
                            Log?.Invoke("Flickr upload complete: " + id);
                            continue;
                        }
                        catch (Exception ex) when (ex is not OperationCanceledException || !ct.IsCancellationRequested)
                        {
                            await gate.WaitAsync(ct);
                            try
                            {
                                item.Attempts++; item.LastError = ex.Message;
                                var delay = TimeSpan.FromSeconds(Math.Min(900, 15 * Math.Pow(2, Math.Min(6, item.Attempts - 1))));
                                item.NextAttemptUtc = DateTime.UtcNow + delay;
                                Save();
                            }
                            finally { gate.Release(); }
                            Log?.Invoke($"Flickr (attempt {item.Attempts}, retry in {(int)Math.Ceiling((item.NextAttemptUtc - DateTime.UtcNow).TotalSeconds)} s): " + ex.Message);
                            continue;
                        }
                    }
                }
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { Log?.Invoke("Upload queue: " + ex.Message); }
            try { await wake.WaitAsync(TimeSpan.FromSeconds(12), ct); } catch { break; }
        }
    }

    int lastReportedCount = -1;
    bool missingCountChanged() { var changed = lastReportedCount != queue.Count; lastReportedCount = queue.Count; return changed; }

    // Caller must hold the gate.
    void Save()
    {
        AppPaths.Ensure();
        try { AtomicFile.WriteAllText(AppPaths.QueueFile, JsonSerializer.Serialize(queue, JsonOptions.Default)); }
        catch (Exception ex) { Log?.Invoke("Could not save upload queue: " + ex.Message); }
    }
}

public static class WindowsCameraHelpers
{
    public static IEnumerable<string> FindCameraFolders()
    {
        var found = new List<string>();
        foreach (var d in DriveInfo.GetDrives())
        {
            try
            {
                if (!d.IsReady) continue;
                if (d.DriveType is not (DriveType.Removable or DriveType.Fixed)) continue;
                var dcim = Path.Combine(d.RootDirectory.FullName, "DCIM");
                if (Directory.Exists(dcim)) found.Add(dcim);
            }
            catch { }
        }
        return found;
    }

    public static void OpenHotspotSettings()
    {
        try { Process.Start(new ProcessStartInfo("ms-settings:network-mobilehotspot") { UseShellExecute = true }); } catch { }
    }
}
