from pathlib import Path

# ----- MainActivity: in-app help + 0.5.1 labels -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s = p.read_text()

needle = 'Button setupCamera=smallButton("SHOW SETUP FOR "+cp.model.toUpperCase(Locale.US),green);setupCamera.setOnClickListener(v->MultiCameraSupport.showSetup(this,p));camChoice.addView(setupCamera);'
replacement = needle + '\n        Button cameraHelp=smallButton("CAMERA FTP HELP — ALL SUPPORTED MODELS",blue);cameraHelp.setOnClickListener(v->MultiCameraSupport.showHelpFile(this));camChoice.addView(cameraHelp);'
if needle not in s:
    raise SystemExit('0.5.1 could not locate camera setup button')
s = s.replace(needle, replacement, 1)

s = s.replace('Nikon Auto Upload 0.5.0  •  Nikon + Canon + Sony FTP beta',
              'Nikon Auto Upload 0.5.1  •  57 Nikon + Canon + Sony + Fujifilm FTP profiles')
s = s.replace('Nikon Auto Upload 0.5.0\\nGenerated:', 'Nikon Auto Upload 0.5.1\\nGenerated:')
p.write_text(s)

# ----- MultiCameraSupport: additional official Sony + Nikon transmitter FTP bodies -----
p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MultiCameraSupport.java')
s = p.read_text()
sony_needle = '        sony("FX3",false), sony("FX30",false),'
sony_replacement = '        sony("FX3",false), sony("FX30",false), sony("FX2",false), sony("ILX-LR1",false),'
if sony_needle not in s:
    raise SystemExit('0.5.1 could not locate Sony cinema profile line')
s = s.replace(sony_needle, sony_replacement, 1)

nikon_needle = '        nikonAccessory("D850",false,"WT-7"), nikonAccessory("D6",true,"WT-6"),'
nikon_replacement = '''        nikonAccessory("D850",false,"WT-7"), nikonAccessory("D780",false,"WT-7"), nikonAccessory("D500",false,"WT-7"),
        nikonAccessory("D810A",false,"WT-7"), nikonAccessory("D810",false,"WT-7"), nikonAccessory("D750",false,"WT-7"), nikonAccessory("D7200",false,"WT-7"),
        nikonAccessory("D6",true,"WT-6"), nikonAccessory("D5",true,"WT-6 / WT-5"), nikonAccessory("D4S",true,"WT-5"), nikonAccessory("D4",true,"WT-5"),'''
if nikon_needle not in s:
    raise SystemExit('0.5.1 could not locate Nikon transmitter profile line')
s = s.replace(nikon_needle, nikon_replacement, 1)
p.write_text(s)
