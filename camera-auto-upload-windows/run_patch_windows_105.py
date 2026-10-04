from pathlib import Path

p = Path('camera-auto-upload-windows/patch_windows_105.py')
s = p.read_text(encoding='utf-8')
s = s.replace(
    "ns = s.index('        <!-- Android-style bottom nav -->')",
    "marker = '        <!-- Android-style bottom nav -->'\nif marker in s:\n    ns = s.index(marker)\nelse:\n    ns = s.rfind('        <Border Grid.Row=\"2\"')\n    if ns < 0:\n        raise SystemExit('Windows navigation block not found')"
)
code = compile(s, str(p), 'exec')
exec(code, {'__name__':'__main__', '__file__':str(p)})
