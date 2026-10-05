from pathlib import Path

x = Path('camera-auto-upload-windows/MainWindow.xaml')
s = x.read_text(encoding='utf-8')
s = s.replace('v1.0.6', 'v1.0.7')
s = s.replace('Pick a camera model and the panel above loads a real photo of that exact model. Photos are cached locally after the first successful load.',
              'Pick a camera model and the panel above uses a curated professional product image for that exact model. No generic camera art or random retailer photos are used.')
x.write_text(s, encoding='utf-8')
