from pathlib import Path
p=Path('nikon-auto-upload/tools/patch_086.py')
s=p.read_text()
old='seek(v->{'
if old not in s: raise SystemExit('0.8.6 seek lambda target missing')
s=s.replace(old,'seek(()->{')
p.write_text(s)
