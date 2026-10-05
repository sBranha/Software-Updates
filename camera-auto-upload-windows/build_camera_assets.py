from pathlib import Path
from io import BytesIO
import base64, html, re, urllib.request
from PIL import Image

ROOT = Path('camera-auto-upload-windows')
OUT = ROOT / 'CameraAssets'
OUT.mkdir(parents=True, exist_ok=True)

# Keep the installer lean and deterministic. Z8 comes directly from the Android
# app asset. These two Sony images come only from Sony's official product pages.
# All other supported models are resolved on demand by CameraPhotoService from
# their official manufacturer pages and then cached locally.
PAGES = {
    ('Sony','α7 III'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7m3-b',
    ('Sony','α9 III'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/all-interchangeable-lens-cameras/p/ilce9m3b',
}

def safe(v):
    return ''.join(ch if ch.isalnum() else '_' for ch in v)

def open_url(url):
    req = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) CameraAutoUploadAssetBuilder/1.0.7',
        'Accept-Language': 'en-US,en;q=0.9',
    })
    return urllib.request.urlopen(req, timeout=25).read()

def extract_hero(page_url):
    raw = open_url(page_url)
    text = raw.decode('utf-8', errors='ignore')
    patterns = [
        r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)',
        r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']og:image["\']',
        r'<meta[^>]+name=["\']twitter:image(?::src)?["\'][^>]+content=["\']([^"\']+)',
    ]
    for pat in patterns:
        m = re.search(pat, text, flags=re.I)
        if m:
            u = html.unescape(m.group(1)).strip()
            if u.startswith('//'): u = 'https:' + u
            if u.startswith('/'):
                from urllib.parse import urljoin
                u = urljoin(page_url, u)
            if u.startswith('http'): return u
    return None

def save_png(image_bytes, dest):
    with Image.open(BytesIO(image_bytes)) as im:
        im.load()
        if im.width > 1800 or im.height > 1400:
            im.thumbnail((1800, 1400), Image.Resampling.LANCZOS)
        if im.mode not in ('RGB','RGBA'):
            im = im.convert('RGBA' if 'A' in im.getbands() else 'RGB')
        im.save(dest, format='PNG', optimize=True)

# Exact Android Z8 asset: this is the same professional camera image the phone app uses.
z8_b64 = Path('nikon-auto-upload/assets/z8_camera.webp.b64')
if z8_b64.exists():
    data = base64.b64decode(''.join(z8_b64.read_text(encoding='utf-8').split()))
    dest = OUT / (safe('Nikon_Z8') + '.png')
    save_png(data, dest)
    print('Bundled Android camera asset:', dest)

for (brand, model), page in PAGES.items():
    dest = OUT / (safe(brand + '_' + model) + '.png')
    hero = extract_hero(page)
    if not hero:
        raise RuntimeError(f'Official product image not found for {brand} {model}')
    data = open_url(hero)
    save_png(data, dest)
    print('Bundled official manufacturer image:', brand, model)
