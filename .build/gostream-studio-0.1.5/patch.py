from pathlib import Path

target = Path('gostream-studio-0.1.1/src/GoStreamStudio/Controls/MultiSourceEditor.cs')
target.write_text(Path('.build/gostream-studio-0.1.5/MultiSourceEditor.cs').read_text())

controller = Path('gostream-studio-0.1.1/src/GoStreamStudio/Core/GoStreamController.cs')
text = controller.read_text()

anchor = '''    public Task SetMultiSourceWindowBorderWidthAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowBorderWidth", windowIndex, value);
'''
if anchor not in text:
    raise SystemExit('MultiSource border-width anchor missing')

replacement = anchor + '''
    public Task SetMultiSourceWindowBorderHueAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowBorderHue", windowIndex, value);

    public Task SetMultiSourceWindowBorderSaturationAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowBorderSaturation", windowIndex, value);

    public Task SetMultiSourceWindowBorderBrightnessAsync(int windowIndex, decimal value)
        => _client.SendSetAsync("multiSourceWindowBorderBrightness", windowIndex, value);
'''
text = text.replace(anchor, replacement, 1)
controller.write_text(text)

final = target.read_text()
required = [
    'Copy', 'Paste', 'Reset', 'Position X', 'Position Y',
    'Crop (%)', 'Corner', 'Border', 'Hue', 'Saturation',
    'Brightness', 'Save preset', 'Browse Presets'
]
for item in required:
    if item not in final:
        raise SystemExit(f'MultiSource UI item missing: {item}')

ctl = controller.read_text()
for cmd in [
    'multiSourceWindowBorderHue',
    'multiSourceWindowBorderSaturation',
    'multiSourceWindowBorderBrightness'
]:
    if cmd not in ctl:
        raise SystemExit(f'MultiSource protocol command missing: {cmd}')

print('v0.1.5 website-matched MultiSource patch applied successfully')
