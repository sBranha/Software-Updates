from pathlib import Path

p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s = p.read_text()

needle = 'Button setupCamera=smallButton("SHOW SETUP FOR "+cp.model.toUpperCase(Locale.US),green);setupCamera.setOnClickListener(v->MultiCameraSupport.showSetup(this,p));camChoice.addView(setupCamera);'
replacement = needle + '\n        Button cameraHelp=smallButton("CAMERA FTP HELP — ALL SUPPORTED MODELS",blue);cameraHelp.setOnClickListener(v->MultiCameraSupport.showHelpFile(this));camChoice.addView(cameraHelp);'
if needle not in s:
    raise SystemExit('0.5.1 could not locate camera setup button')
s = s.replace(needle, replacement, 1)

s = s.replace('Nikon Auto Upload 0.5.0  •  Nikon + Canon + Sony FTP beta',
              'Nikon Auto Upload 0.5.1  •  46 Nikon + Canon + Sony + Fujifilm FTP profiles')
s = s.replace('Nikon Auto Upload 0.5.0\\nGenerated:', 'Nikon Auto Upload 0.5.1\\nGenerated:')

p.write_text(s)
