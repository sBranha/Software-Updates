from pathlib import Path

# 0.7.0 shared Connection Status dashboard. This sits above every transport and
# only reports state; it does not own USB, FTP, Wi-Fi or Bluetooth connections.

# ----- AppStatusHub: prefer the richer shared ConnectionStatus report -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/AppStatusHub.java')
s=p.read_text()
s=s.replace(
'''        try{s.cameraModel=MultiCameraSupport.cameraName(p);}catch(Exception ignored){}
        String reportedModel=p.getString("status_camera_model","");if(!reportedModel.isEmpty())s.cameraModel=reportedModel;

        String connection=ConnectionStatus.cameraConnection(p);''',
'''        ConnectionStatus.Report shared=ConnectionStatus.build(context,p);
        s.cameraModel=shared.cameraName;
        String reportedModel=p.getString("status_camera_model","");if(!reportedModel.isEmpty())s.cameraModel=reportedModel;

        String connection=(shared.connectionType==null||"None".equals(shared.connectionType))?ConnectionStatus.cameraConnection(p):shared.connectionType;''',1)
s=s.replace(
'''.putInt("status_transfer_current",Math.max(0,current)).putInt("status_transfer_total",Math.max(0,total)).apply();''',
'''.putInt("status_transfer_current",Math.max(0,current)).putInt("status_transfer_total",Math.max(0,total)).putInt("status_photo_current",Math.max(0,current)).putInt("status_photo_total",Math.max(0,total)).apply();''',1)
s=s.replace(
'''.remove("status_transfer_current").remove("status_transfer_total").apply();''',
'''.remove("status_transfer_current").remove("status_transfer_total").remove("status_photo_current").remove("status_photo_total").apply();''',1)
p.write_text(s)

# ----- MainActivity: compact expandable dashboard on Home -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text()
needle='''        body.addView(hero);space(10);

        Health h=health();'''
replacement='''        body.addView(hero);space(10);

        ConnectionStatusDashboard dashboard=new ConnectionStatusDashboard(this,p);body.addView(dashboard);space(10);

        Health h=health();'''
if needle not in s: raise SystemExit('0.7.0 dashboard Home insertion target missing')
s=s.replace(needle,replacement,1)

# Keep only a one-line activity feed below the expandable dashboard, instead of
# repeating the whole connection block again on Home.
old='''        sectionHeader("Status");LinearLayout statusCard=cardBox();connectionInfo=txt("",13,white,false);statusCard.addView(connectionInfo);body.addView(statusCard);status=txt("● "+p.getString("last_status","Ready"),12,statusColor(p.getString("last_status","")),true);body.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));refreshLiveViews(null);'''
new='''        sectionHeader("Activity");connectionInfo=null;status=txt("● "+p.getString("last_status","Ready"),12,statusColor(p.getString("last_status","")),true);body.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));refreshLiveViews(null);'''
if old in s:s=s.replace(old,new,1)
p.write_text(s)

# ----- USB module publishes detailed status including speed and file progress -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/UsbCameraActivity.java')
s=p.read_text()
old='''                String name=cameraName(d);p.edit().putString("usb_state","Connected").putString("usb_camera_name",name).putString("usb_vid_pid",String.format(Locale.US,"%04X:%04X",d.getVendorId(),d.getProductId())).apply();'''
if old not in s:
    old='''                String name=cameraName(d);p.edit().putString("usb_state","USB-C connected").putString("usb_camera_name",name).putString("usb_vid_pid",String.format(Locale.US,"%04X:%04X",d.getVendorId(),d.getProductId())).apply();'''
new='''                String name=cameraName(d);p.edit().putString("usb_state","USB-C connected").putString("usb_camera_name",name).putString("usb_vid_pid",String.format(Locale.US,"%04X:%04X",d.getVendorId(),d.getProductId())).apply();
                AppStatusHub.reportCamera(p,name,"USB-C",true);AppStatusHub.reportTransfer(p,"Idle","",0,0,0);'''
if old not in s: raise SystemExit('0.7.0 USB camera-report target missing')
s=s.replace(old,new,1)

old='''        p.edit().putString("usb_state",message==null?"Disconnected":message).apply();'''
new='''        p.edit().putString("usb_state",message==null?"Disconnected":message).apply();AppStatusHub.clearCameraReport(p);AppStatusHub.clearTransfer(p);'''
if old not in s: raise SystemExit('0.7.0 USB disconnect target missing')
s=s.replace(old,new,1)

old='''                if(ptp==null||!ptp.isOpen())break;index++;final int fileIndex=index;
                try{'''
new='''                if(ptp==null||!ptp.isOpen())break;index++;final int fileIndex=index;final long transferStarted=SystemClock.elapsedRealtime();final long[] speedMark=new long[]{transferStarted,0};
                AppStatusHub.reportTransfer(p,"Transferring",x.name,0,fileIndex,totalFiles);
                try{'''
if old not in s: raise SystemExit('0.7.0 USB transfer-start target missing')
s=s.replace(old,new,1)

old='''                    ptp.download(x,part,(bytes,max)->runOnUiThread(()->{int pct=max<=0?0:(int)Math.min(100,(bytes*100L)/max);progress.setProgress(pct);summary.setText("Transferring "+x.name+" • "+pct+"% • "+formatBytes(bytes)+" / "+formatBytes(max));}));'''
new='''                    ptp.download(x,part,(bytes,max)->{
                        long now=SystemClock.elapsedRealtime(),dt=Math.max(1,now-speedMark[0]),delta=Math.max(0,bytes-speedMark[1]);long bps=(delta*1000L)/dt;
                        if(dt>=300){speedMark[0]=now;speedMark[1]=bytes;AppStatusHub.reportTransfer(p,"Transferring",x.name,bps,fileIndex,totalFiles);}
                        runOnUiThread(()->{int pct=max<=0?0:(int)Math.min(100,(bytes*100L)/max);progress.setProgress(pct);summary.setText("Transferring "+x.name+" • "+pct+"% • "+formatBytes(bytes)+" / "+formatBytes(max)+(bps>0?" • "+ConnectionStatus.formatSpeed(bps):""));});
                    });'''
if old not in s: raise SystemExit('0.7.0 USB progress target missing')
s=s.replace(old,new,1)

old='''                    markImported(x);sendIntoApp(done,x.name);p.edit().putString("usb_last_status","Imported "+x.name).putString("usb_last_photo",x.name).putLong("usb_last_photo_time",System.currentTimeMillis()).apply();
                    runOnUiThread(()->setStatus("USB transfer complete: "+x.name,false));'''
new='''                    markImported(x);sendIntoApp(done,x.name);p.edit().putString("usb_last_status","Imported "+x.name).putString("usb_last_photo",x.name).putLong("usb_last_photo_time",System.currentTimeMillis()).apply();
                    AppStatusHub.reportTransfer(p,"Complete",x.name,0,fileIndex,totalFiles);
                    runOnUiThread(()->setStatus("USB transfer complete: "+x.name,false));'''
if old not in s: raise SystemExit('0.7.0 USB complete target missing')
s=s.replace(old,new,1)

old='''                }catch(Exception e){p.edit().putString("usb_last_status","Failed: "+shortMsg(e)).apply();runOnUiThread(()->setStatus("USB transfer failed for "+x.name+": "+shortMsg(e),true));}'''
new='''                }catch(Exception e){p.edit().putString("usb_last_status","Failed: "+shortMsg(e)).apply();AppStatusHub.reportTransfer(p,"Failed",x.name,0,fileIndex,totalFiles);runOnUiThread(()->setStatus("USB transfer failed for "+x.name+": "+shortMsg(e),true));}'''
if old not in s: raise SystemExit('0.7.0 USB failure target missing')
s=s.replace(old,new,1)
p.write_text(s)

# ----- Nikon PTP handoff publishes shared transfer state -----
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/NikonPtpAutoTransfer.java')
s=p.read_text()
old='''        prefs.edit().putString("nikon_handoff_state", "Downloading " + safeName).apply();
        state("Downloading " + safeName + " from Nikon…", false);'''
new='''        prefs.edit().putString("nikon_handoff_state", "Downloading " + safeName).apply();
        AppStatusHub.reportTransfer(prefs,"Transferring",safeName,0,1,1);
        state("Downloading " + safeName + " from Nikon…", false);'''
if old in s:s=s.replace(old,new,1)
old='''        state("Photo received: " + safeName, false);'''
new='''        AppStatusHub.reportTransfer(prefs,"Complete",safeName,0,1,1);
        state("Photo received: " + safeName, false);'''
if old in s:s=s.replace(old,new,1)
p.write_text(s)

# Help text for the shared dashboard.
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text()+'''\n\nSHARED CONNECTION STATUS DASHBOARD\n----------------------------------\nThe Home screen has an expandable status dashboard shared by USB-C, Wi-Fi/FTP, phone hotspot, Nikon Bluetooth/Wi-Fi handoff, Flickr, and future camera-brand connectors.\n\nGreen = connected/working. Yellow = connected but limited (for example, USB import works but internet is unavailable). Red = disconnected, failed, or needs attention.\n\nThe compact view shows camera/model + connection type, internet, current transfer/progress/speed when available, and Flickr queue state. SHOW DETAILS adds SSID/network when Android exposes it, phone IP, camera IP when known, cellular state, transfer file, and waiting/uploading/completed/failed queue counts.\n\nUSB-C and internet are intentionally independent: losing Wi-Fi/internet never marks a working USB camera as failed, and imported photos remain queued until Flickr can reach the internet again.\n'''
    p.write_text(s)
