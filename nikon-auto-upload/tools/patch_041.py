from pathlib import Path

p = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s = p.read_text()

# Keep the photo-viewer syntax correction from the 0.4.0 build.
s = s.replace('share.setOnClickListener(v->sharePhoto(photos.get(index[0]));', 'share.setOnClickListener(v->sharePhoto(photos.get(index[0])));')

# Use the actual Z8 artwork taken from the approved original mock-up throughout the UI.
s = s.replace('R.drawable.ic_camera', 'R.drawable.z8_camera')

# Header: camera app icon on Home, back arrow on Settings, settings cog on the main screens.
old = '''        if("Home".equals(active)){
            ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.z8_camera);header.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        }
        TextView ht=txt(title,22,white,true);LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(0,dp(48),1);htp.setMargins(dp(8),0,0,0);header.addView(ht,htp);
        if(!"Settings".equals(active)){
            TextView gear=iconText("⚙",24,muted);gear.setOnClickListener(v->drawSettings());header.addView(gear,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }'''
new = '''        if("Settings".equals(active)){
            TextView back=iconText("‹",34,white);back.setOnClickListener(v->drawHome());header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        }else if("Home".equals(active)){
            ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.launcher_z8);logo.setScaleType(ImageView.ScaleType.CENTER_CROP);header.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        }
        TextView ht=txt(title,22,white,true);LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(0,dp(48),1);htp.setMargins(dp(8),0,0,0);header.addView(ht,htp);
        if(!"Settings".equals(active)){
            TextView gear=iconText("⚙",24,white);gear.setBackground(rounded(Color.rgb(14,21,29),18,line,1));gear.setOnClickListener(v->drawSettings());header.addView(gear,new LinearLayout.LayoutParams(dp(44),dp(44)));
        }'''
if old not in s:
    raise SystemExit('Could not locate header block for 0.4.1 patch')
s = s.replace(old, new)

# The bottom bar is now Home / Photos / Uploads only. Settings lives in the top cog.
s = s.replace(
    'addNav("⌂","Home",active);addNav("▣","Photos",active);addNav("⇧","Uploads",active);addNav("⚙","Settings",active);',
    'addNav("⌂","Home",active);addNav("▣","Photos",active);addNav("⇧","Uploads",active);'
)

# Make the Z8 the same large hero element used by the original concept image.
s = s.replace(
    'ImageView camera=new ImageView(this);camera.setImageResource(R.drawable.z8_camera);camera.setScaleType(ImageView.ScaleType.FIT_CENTER);hero.addView(camera,new LinearLayout.LayoutParams(dp(190),dp(150)));',
    'ImageView camera=new ImageView(this);camera.setImageResource(R.drawable.z8_camera);camera.setScaleType(ImageView.ScaleType.FIT_CENTER);camera.setAdjustViewBounds(true);hero.addView(camera,new LinearLayout.LayoutParams(-1,dp(205)));'
)
s = s.replace('hero.setPadding(dp(18),dp(16),dp(18),dp(18));', 'hero.setPadding(dp(12),dp(10),dp(12),dp(16));')
s = s.replace('TextView z8=txt("Nikon Z8",22,white,true);', 'TextView z8=txt("Nikon Z8",24,white,true);')
s = s.replace('Connected by Wi-Fi FTP', 'Connected by Wi-Fi FTP • Full resolution')

# Slightly closer to the black/charcoal mock-up palette.
s = s.replace(
    'final int bg=Color.rgb(7,11,15),panel=Color.rgb(16,23,31),panel2=Color.rgb(23,32,42),line=Color.rgb(42,54,66);',
    'final int bg=Color.rgb(4,8,12),panel=Color.rgb(14,21,29),panel2=Color.rgb(22,31,41),line=Color.rgb(38,50,62);'
)

# Update visible version labels and diagnostics.
s = s.replace('Nikon Auto Upload 0.4.0', 'Nikon Auto Upload 0.4.1')
s = s.replace('Preview-style redesign  •  Wi-Fi FTP only — no Bluetooth', 'Original mock-up match  •  Wi-Fi FTP only — no Bluetooth')

p.write_text(s)
