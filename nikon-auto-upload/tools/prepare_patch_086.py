from pathlib import Path
p=Path('nikon-auto-upload/tools/patch_086.py')
s=p.read_text()
old="""old='''Paint photoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);canvas.save();'''\nnew='''Paint photoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);if(Math.abs(t.photoHue)>.01f)photoPaint.setColorFilter(imageColorFilter(Color.WHITE,0f,t.photoHue));canvas.save();'''"""
new="""old='''Paint photoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);RectF photoDst=new RectF(pcx-pw/2f,pcy-ph/2f,pcx+pw/2f,pcy+ph/2f);canvas.save();'''\nnew='''Paint photoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);if(Math.abs(t.photoHue)>.01f)photoPaint.setColorFilter(imageColorFilter(Color.WHITE,0f,t.photoHue));RectF photoDst=new RectF(pcx-pw/2f,pcy-ph/2f,pcx+pw/2f,pcy+ph/2f);canvas.save();'''"""
if old not in s: raise SystemExit('prepare 0.8.6 photo-hue patch target missing')
p.write_text(s.replace(old,new,1))
