from pathlib import Path

# 0.7.1: expose exact active Flickr upload count to AppStatusHub without
# changing the existing upload/queue behavior.
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s=p.read_text()
old='''        Network cell=cellularNetwork;if(cell==null){if(!cellularRequestActive)requestCellular();return;}processing=true;
        try{'''
new='''        Network cell=cellularNetwork;if(cell==null){if(!cellularRequestActive)requestCellular();return;}processing=true;p.edit().putInt("status_uploading_count",1).apply();
        try{'''
if old not in s: raise SystemExit('0.7.1 active upload target missing')
s=s.replace(old,new,1)
old='''        }finally{processing=false;}
    }'''
new='''        }finally{processing=false;p.edit().putInt("status_uploading_count",0).apply();}
    }'''
if old not in s: raise SystemExit('0.7.1 upload-finally target missing')
s=s.replace(old,new,1)
p.write_text(s)
