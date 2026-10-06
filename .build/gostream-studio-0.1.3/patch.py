from pathlib import Path

main = Path('gostream-studio-0.1.1/src/GoStreamStudio/MainForm.cs')
text = main.read_text()

def rep(old, new, count=1):
    global text
    if old not in text:
        raise SystemExit(f'MainForm anchor not found: {old[:100]!r}')
    text = text.replace(old, new, count)

rep(
    '    private readonly Dictionary<string, Button> _navButtons = new(StringComparer.OrdinalIgnoreCase);\n',
    '    private readonly Dictionary<string, Button> _navButtons = new(StringComparer.OrdinalIgnoreCase);\n'
    '    private MultiSourceEditor? _multiSourceEditor;\n'
)

rep(
'''    private Control BuildMultiViewWorkspace()
    {
        return BuildFeatureWorkspace(
            "MultiView",
            "MultiSource and layout controls for the connected GoStream model.",
            BuildPresetCard());
    }
''',
'''    private Control BuildMultiViewWorkspace()
    {
        _multiSourceEditor = new MultiSourceEditor(_controller, SetLog);
        return BuildFeatureWorkspace(
            "MultiView / MultiSource",
            "Four-window MultiSource editor for GoStream 8 / 12; GoStream 4 uses the first two windows.",
            _multiSourceEditor);
    }
'''
)

needle = '        ApplyProfileToButtons();'
pos = text.rfind(needle)
if pos < 0:
    raise SystemExit('Final ApplyProfileToButtons call not found')
pos += len(needle)
text = text[:pos] + '''

        if (_multiSourceEditor is not null)
        {
            var profile = _settings.ProfileMode == DeviceProfileMode.Auto
                ? _controller.ActiveProfile
                : DeviceProfile.FromMode(_settings.ProfileMode);
            var windowCount = profile.Mode == DeviceProfileMode.GoStream4 ? 2 : 4;
            _multiSourceEditor.SetSources(_controller.CameraSourceIds(), windowCount);
        }''' + text[pos:]

main.write_text(text)

controller = Path('gostream-studio-0.1.1/src/GoStreamStudio/Core/GoStreamController.cs')
ct = controller.read_text()
anchor = '''    public Task SetAudioModeAsync(int sourceId, int mode)
        => _client.SendSetAsync("audioMixerEnable", sourceId, mode);
'''
if anchor not in ct:
    raise SystemExit('GoStreamController anchor not found')

methods = anchor + '''
    public Task SetMultiSourceEnabledAsync(bool enabled)
        => _client.SendSetAsync("multiSourceEnable", enabled ? 1 : 0);

    public Task SetMultiSourcePlaceInAsync(int placeIn)
        => _client.SendSetAsync("multiSourcePlaceIn", placeIn);

    public Task SetMultiSourceFillSourceAsync(int sourceId)
        => _client.SendSetAsync("multiSourceFillSource", sourceId);

    public Task SetMultiSourceWindowEnabledAsync(int windowIndex, bool enabled)
        => _client.SendSetAsync("multiSourceWindowEnable", windowIndex, enabled ? 1 : 0);

    public Task SetMultiSourceWindowSourceAsync(int windowIndex, int sourceId)
        => _client.SendSetAsync("multiSourceWindowSource", windowIndex, sourceId);

    public Task SetMultiSourceWindowXAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowXPosition", windowIndex, value);

    public Task SetMultiSourceWindowYAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowYPosition", windowIndex, value);

    public Task SetMultiSourceWindowSizeAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowSize", windowIndex, value);

    public Task SetMultiSourceWindowCropLeftAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowCropLeft", windowIndex, value);

    public Task SetMultiSourceWindowCropRightAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowCropRight", windowIndex, value);

    public Task SetMultiSourceWindowCropTopAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowCropTop", windowIndex, value);

    public Task SetMultiSourceWindowCropBottomAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowCropBottom", windowIndex, value);

    public Task SetMultiSourceWindowCornerRadiusAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowCornerRadius", windowIndex, value);

    public Task SetMultiSourceWindowBorderWidthAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowBorderWidth", windowIndex, value);
'''
ct = ct.replace(anchor, methods, 1)
controller.write_text(ct)

target = Path('gostream-studio-0.1.1/src/GoStreamStudio/Controls/MultiSourceEditor.cs')
target.write_text(Path('.build/gostream-studio-0.1.3/MultiSourceEditor.cs').read_text())

if 'new MultiSourceEditor(_controller, SetLog)' not in main.read_text():
    raise SystemExit('MultiSource editor wiring missing')
if 'multiSourceWindowSource' not in controller.read_text():
    raise SystemExit('MultiSource controller methods missing')
if not target.exists():
    raise SystemExit('MultiSourceEditor.cs copy failed')
print('v0.1.3 MultiSource editor patch applied successfully')
