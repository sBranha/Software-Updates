from pathlib import Path

x = Path('camera-auto-upload-windows/MainWindow.xaml')
s = x.read_text(encoding='utf-8')
s = s.replace('v1.0.5', 'v1.0.6')
s = s.replace('Pick a model and the camera artwork above changes to match the selected camera family and model.',
              'Pick a camera model and the panel above loads a real photo of that exact model. Photos are cached locally after the first successful load.')
x.write_text(s, encoding='utf-8')
