from pathlib import Path
for rel in [
    'nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java',
    'nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt'
]:
    p=Path(rel)
    if p.exists():
        s=p.read_text().replace('Nikon Auto Upload 0.7.0','Nikon Auto Upload 0.7.1').replace('Version 0.7.0 beta','Version 0.7.1 beta')
        p.write_text(s)
