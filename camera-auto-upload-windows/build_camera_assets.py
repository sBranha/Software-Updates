from pathlib import Path
from io import BytesIO
import base64, html, re, urllib.request
from PIL import Image

ROOT = Path('camera-auto-upload-windows')
OUT = ROOT / 'CameraAssets'
OUT.mkdir(parents=True, exist_ok=True)

# Official manufacturer product/lineup pages only. The builder pulls the page's
# own hero/product image and converts it to PNG so Windows can display it offline.
PAGES = {
    # Nikon
    ('Nikon','Z9'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/z9/',
    ('Nikon','Z8'): 'https://www.nikonusa.com/p/z-8/1698/overview',
    ('Nikon','Z6III'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/z6_3/',
    ('Nikon','Z5II'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/z5_2/',
    ('Nikon','Zf'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/zf/',
    ('Nikon','Z50II'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/z50_2/',
    ('Nikon','ZR'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/zr/',
    ('Nikon','Z7II'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/z7_2/',
    ('Nikon','Z6II'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/z6_2/',
    ('Nikon','Z7'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/z7/',
    ('Nikon','Z6'): 'https://imaging.nikon.com/imaging/lineup/mirrorless/z6/',
    ('Nikon','D850'): 'https://imaging.nikon.com/imaging/lineup/dslr/d850/',
    ('Nikon','D6'): 'https://imaging.nikon.com/imaging/lineup/dslr/d6/',
    # Canon
    ('Canon','EOS R1'): 'https://www.usa.canon.com/shop/p/eos-r1',
    ('Canon','EOS R5 Mark II'): 'https://www.usa.canon.com/shop/p/eos-r5-mark-ii',
    ('Canon','EOS R3'): 'https://www.usa.canon.com/shop/p/eos-r3',
    ('Canon','EOS R5'): 'https://www.usa.canon.com/shop/p/eos-r5',
    ('Canon','EOS R6 Mark III'): 'https://www.usa.canon.com/shop/p/eos-r6-mark-iii',
    ('Canon','EOS R6 Mark II'): 'https://www.usa.canon.com/shop/p/eos-r6-mark-ii',
    ('Canon','EOS R6 V'): 'https://www.usa.canon.com/shop/p/eos-r6-v',
    ('Canon','EOS R6'): 'https://www.usa.canon.com/shop/p/eos-r6',
    ('Canon','EOS-1D X Mark III'): 'https://www.usa.canon.com/shop/p/eos-1d-x-mark-iii',
    ('Canon','EOS-1D X Mark II'): 'https://www.usa.canon.com/shop/p/eos-1d-x-mark-ii',
    # Sony
    ('Sony','α7 V'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7m5-b',
    ('Sony','α1 II'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce1m2-b',
    ('Sony','α1'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce1-b',
    ('Sony','α9 III'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/all-interchangeable-lens-cameras/p/ilce9m3b',
    ('Sony','α9 II'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce9m2-b',
    ('Sony','α9'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce9-b',
    ('Sony','α7 IV'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7m4-b',
    ('Sony','α7 III'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7m3-b',
    ('Sony','α7S III'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7sm3-b',
    ('Sony','α7R V'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm5-b',
    ('Sony','α7R IV'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm4-b',
    ('Sony','α7R IVA'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm4a-b',
    ('Sony','α7R III'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm3-b',
    ('Sony','α7R IIIA'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm3a-b',
    ('Sony','α7C II'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7cm2-b',
    ('Sony','α7CR'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7cr-b',
    ('Sony','α7C'): 'https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7c-b',
    ('Sony','FX3'): 'https://electronics.sony.com/imaging/cinema-line-cameras/all-cinema-line-cameras/p/ilmefx3-b',
    ('Sony','FX30'): 'https://electronics.sony.com/imaging/cinema-line-cameras/all-cinema-line-cameras/p/ilmefx30-b',
    # Fujifilm
    ('Fujifilm','GFX100 II'): 'https://fujifilm-x.com/en-us/products/cameras/gfx100-ii/',
    ('Fujifilm','GFX100S II'): 'https://fujifilm-x.com/en-us/products/cameras/gfx100s-ii/',
    ('Fujifilm','X-H2'): 'https://fujifilm-x.com/en-us/products/cameras/x-h2/',
    ('Fujifilm','X-H2S'): 'https://fujifilm-x.com/en-us/products/cameras/x-h2s/',
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

# Exact Android Z8 asset first: this is the same photo already used by the phone app.
z8_b64 = Path('nikon-auto-upload/assets/z8_camera.webp.b64')
if z8_b64.exists():
    try:
        data = base64.b64decode(''.join(z8_b64.read_text(encoding='utf-8').split()))
        dest = OUT / (safe('Nikon_Z8') + '.png')
        save_png(data, dest)
        print('Bundled Android camera asset:', dest)
    except Exception as e:
        print('WARNING: Android Z8 asset conversion failed:', e)

for (brand, model), page in PAGES.items():
    dest = OUT / (safe(brand + '_' + model) + '.png')
    if dest.exists():
        continue
    try:
        hero = extract_hero(page)
        if not hero:
            print('WARNING: no official hero image found:', brand, model, page)
            continue
        data = open_url(hero)
        save_png(data, dest)
        print('Official camera asset:', brand, model, hero)
    except Exception as e:
        print('WARNING: official image unavailable:', brand, model, e)
