from pathlib import Path

# 0.7.0 follow-up: USB-C is independent of the FTP receiver. A paused wireless
# receiver must never block a wired import, and USB diagnostics must say USB-C.
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s=p.read_text()

old='''            try{
                if(p.getBoolean("receiver_paused",false)){file.delete();broadcast("Camera receiving is paused",findBestLocalIp());return;}'''
new='''            try{
                boolean usbImport=file!=null&&file.getAbsolutePath().replace('\\\\','/').contains("/usb_import/");
                if(p.getBoolean("receiver_paused",false)&&!usbImport){file.delete();broadcast("Camera receiving is paused",findBestLocalIp());return;}'''
if old not in s:
    old='''            try{
                if(p.getBoolean("receiver_paused",false)){file.delete();broadcast("Z8 receiving is paused",findBestLocalIp());return;}'''
if old not in s: raise SystemExit('0.7.0 USB pause-bypass target missing')
s=s.replace(old,new,1)

old='''.putString("last_received_name",originalName).putLong("last_received_time",now).putString("ftp_state","Photo received").apply();'''
new='''.putString("last_received_name",originalName).putLong("last_received_time",now).putString(usbImport?"usb_state":"ftp_state",usbImport?"USB import complete":"Photo received").apply();'''
if old not in s: raise SystemExit('0.7.0 USB state target missing')
s=s.replace(old,new,1)

old='''                addEvent(send?"Received from "+MultiCameraSupport.cameraName(p)+": "+originalName+" — queued for Flickr":"Received from "+MultiCameraSupport.cameraName(p)+": "+originalName+" — phone only");notifyEvent("Photo received from "+MultiCameraSupport.cameraName(p),originalName);'''
new='''                String receiveSource=usbImport?"USB-C camera":MultiCameraSupport.cameraName(p);
                addEvent(send?("Received from "+receiveSource+": "+originalName+" — queued for Flickr"):("Received from "+receiveSource+": "+originalName+" — phone only"));notifyEvent("Photo received from "+receiveSource,originalName);'''
if old in s:s=s.replace(old,new,1)

p.write_text(s)
