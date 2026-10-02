from pathlib import Path

# 0.8.2 compile fix: the layer editor renders imported PNG files directly.
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/OverlayStudioActivity.java')
s=p.read_text()
if 'import java.io.File;' not in s:
    marker='import java.util.*;\n'
    if marker not in s: raise SystemExit('0.8.2 java.io.File import marker missing')
    s=s.replace(marker,'import java.io.File;\n'+marker,1)
p.write_text(s)
