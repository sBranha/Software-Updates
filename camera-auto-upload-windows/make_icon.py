import base64
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parent.parent
src = root / "nikon-auto-upload" / "assets" / "launcher_z8.webp.b64"
out = Path(__file__).resolve().parent / "app.ico"
tmp = Path(__file__).resolve().parent / "app-icon.webp"

tmp.write_bytes(base64.b64decode(src.read_text(encoding="utf-8").strip()))
with Image.open(tmp) as im:
    im = im.convert("RGBA")
    im.save(out, format="ICO", sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
try:
    tmp.unlink()
except FileNotFoundError:
    pass
print(out)
