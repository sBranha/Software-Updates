from pathlib import Path

main = Path('gostream-studio-0.1.1/src/GoStreamStudio/MainForm.cs')
text = main.read_text()

def rep(old, new, count=1):
    global text
    if old not in text:
        raise SystemExit(f'MainForm anchor not found: {old[:120]!r}')
    text = text.replace(old, new, count)

rep(
    '    private MultiSourceEditor? _multiSourceEditor;\n',
    '    private MultiSourceEditor? _multiSourceEditor;\n'
    '    private readonly Dictionary<string, WorkspaceForm> _workspaceWindows = new(StringComparer.OrdinalIgnoreCase);\n'
)

rep(
'''        else
        {
            ShowWorkspace(name);
        }
''',
'''        else if (name == "Switcher")
        {
            ShowWorkspace("Switcher");
        }
        else
        {
            OpenWorkspaceWindow(name);
        }
''',
1
)

anchor = '    private async void TopNav_Click(object? sender, EventArgs e)\n'
if anchor not in text:
    raise SystemExit('TopNav_Click anchor missing')

methods = r'''    private void OpenWorkspaceWindow(string name)
    {
        if (_workspaceWindows.TryGetValue(name, out var existing) && !existing.IsDisposed)
        {
            if (existing.WindowState == FormWindowState.Minimized)
                existing.WindowState = FormWindowState.Maximized;
            existing.BringToFront();
            existing.Activate();
            return;
        }

        if (name == "Audio")
            _audioPageStrips.Clear();

        Control content = name switch
        {
            "Audio" => BuildAudioWorkspace(),
            "MultiView" => BuildMultiViewWorkspace(),
            "Media" => BuildMediaWorkspace(),
            "Macros" => BuildMacrosWorkspace(),
            "Streaming" => BuildStreamingWorkspace(),
            _ => BuildFeatureWorkspace(name, $"{name} controls.", new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = Theme.Surface
            })
        };

        if (name == "MultiView" && _multiSourceEditor is not null)
            ConfigureMultiSourceEditor(_multiSourceEditor);

        var title = name == "MultiView" ? "MultiView / MultiSource" : name;
        var window = new WorkspaceForm(title, content, WorkspaceStatusText);

        window.FormClosed += (_, _) =>
        {
            _workspaceWindows.Remove(name);
            if (name == "Audio")
                _audioPageStrips.Clear();
            if (name == "MultiView" && _multiSourceEditor is not null && _multiSourceEditor.IsDisposed)
                _multiSourceEditor = null;
        };

        _workspaceWindows[name] = window;
        window.Show(this);
        window.BringToFront();
    }

    private string WorkspaceStatusText()
    {
        if (!_controller.IsConnected)
            return $"Device Disconnected  •  {_settings.DeviceIp}:{_settings.DevicePort}";

        return $"Device Connected  •  {_controller.DeviceName}  •  {_controller.ActiveProfile.DisplayName}  •  {_settings.DeviceIp}:{_settings.DevicePort}";
    }

    private void ConfigureMultiSourceEditor(MultiSourceEditor editor)
    {
        if (editor.IsDisposed) return;

        var profile = _settings.ProfileMode == DeviceProfileMode.Auto
            ? _controller.ActiveProfile
            : DeviceProfile.FromMode(_settings.ProfileMode);
        var windowCount = profile.Mode == DeviceProfileMode.GoStream4 ? 2 : 4;
        editor.SetSources(_controller.CameraSourceIds(), windowCount);
    }

'''
text = text.replace(anchor, methods + anchor, 1)

rep(
'''        if (_multiSourceEditor is not null)
        {
            var profile = _settings.ProfileMode == DeviceProfileMode.Auto
                ? _controller.ActiveProfile
                : DeviceProfile.FromMode(_settings.ProfileMode);
            var windowCount = profile.Mode == DeviceProfileMode.GoStream4 ? 2 : 4;
            _multiSourceEditor.SetSources(_controller.CameraSourceIds(), windowCount);
        }
''',
'''        if (_multiSourceEditor is not null && !_multiSourceEditor.IsDisposed)
            ConfigureMultiSourceEditor(_multiSourceEditor);

        foreach (var workspace in _workspaceWindows.Values.Where(w => !w.IsDisposed))
        {
            var streamingPageStatus = FindDeep(workspace, "StreamingPageStatus") as Label;
            if (streamingPageStatus is not null)
                streamingPageStatus.Text =
                    $"Streaming: {(_controller.StreamEnabled ? "LIVE" : "OFF")}    Recording: {(_controller.RecordStatus == 0 ? "OFF" : "ACTIVE")}";
        }
''',
1
)

rep(
'''        _meterTimer.Stop();
        _refreshTimer.Stop();
''',
'''        _meterTimer.Stop();
        _refreshTimer.Stop();
        foreach (var workspace in _workspaceWindows.Values.ToArray())
        {
            try { if (!workspace.IsDisposed) workspace.Close(); } catch { }
        }
        _workspaceWindows.Clear();
''',
1
)

main.write_text(text)

target = Path('gostream-studio-0.1.1/src/GoStreamStudio/Controls/WorkspaceForm.cs')
target.write_text(Path('.build/gostream-studio-0.1.4/WorkspaceForm.cs').read_text())

final = main.read_text()
if 'OpenWorkspaceWindow(name);' not in final:
    raise SystemExit('Dedicated workspace window navigation missing')
if 'new WorkspaceForm(' not in final:
    raise SystemExit('WorkspaceForm wiring missing')
if not target.exists():
    raise SystemExit('WorkspaceForm source copy failed')

print('v0.1.4 full workspace windows patch applied successfully')
