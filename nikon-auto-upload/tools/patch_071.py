from pathlib import Path

# ---------- MainActivity: compact + expandable shared status dashboard ----------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/MainActivity.java')
s=p.read_text()

old='''    TextView status,connectionInfo;
    boolean receiverRegistered=false,photoSelectMode=false;'''
new='''    TextView status,connectionInfo;
    TextView dashCamera,dashInternet,dashTransfer,dashUpload,dashDetails;
    LinearLayout dashDetailsBox;
    boolean receiverRegistered=false,photoSelectMode=false;'''
if old not in s: raise SystemExit('0.7.1 dashboard fields target missing')
s=s.replace(old,new,1)

old='''        body.addView(hero);space(10);

        Health h=health();'''
new='''        body.addView(hero);space(10);
        addConnectionDashboard();space(10);

        Health h=health();'''
if old not in s: raise SystemExit('0.7.1 dashboard insertion target missing')
s=s.replace(old,new,1)

old='''    void refreshLiveViews(String newest){
        if(status!=null){'''
new='''    void refreshLiveViews(String newest){
        refreshConnectionDashboard();
        if(status!=null){'''
if old not in s: raise SystemExit('0.7.1 live refresh target missing')
s=s.replace(old,new,1)

marker='''    int statusColor(String s){'''
helpers=r'''    void addConnectionDashboard(){
        LinearLayout box=cardBox();
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=txt("CONNECTION STATUS",13,blue,true);head.addView(title,new LinearLayout.LayoutParams(0,dp(38),1));
        Button details=smallButton(p.getBoolean("status_details_open",false)?"HIDE DETAILS":"DETAILS",Color.rgb(55,65,75));
        details.setOnClickListener(v->{boolean open=!p.getBoolean("status_details_open",false);p.edit().putBoolean("status_details_open",open).apply();if(dashDetailsBox!=null)dashDetailsBox.setVisibility(open?View.VISIBLE:View.GONE);details.setText(open?"HIDE DETAILS":"DETAILS");});
        head.addView(details,new LinearLayout.LayoutParams(dp(118),dp(42)));box.addView(head);
        dashCamera=txt("",14,white,true);dashCamera.setPadding(dp(4),dp(3),dp(4),dp(3));box.addView(dashCamera,new LinearLayout.LayoutParams(-1,dp(34)));
        dashInternet=txt("",13,white,true);dashInternet.setPadding(dp(4),dp(3),dp(4),dp(3));box.addView(dashInternet,new LinearLayout.LayoutParams(-1,dp(32)));
        dashTransfer=txt("",13,white,true);dashTransfer.setPadding(dp(4),dp(3),dp(4),dp(3));box.addView(dashTransfer,new LinearLayout.LayoutParams(-1,dp(32)));
        dashUpload=txt("",13,white,true);dashUpload.setPadding(dp(4),dp(3),dp(4),dp(3));box.addView(dashUpload,new LinearLayout.LayoutParams(-1,dp(32)));
        dashDetailsBox=new LinearLayout(this);dashDetailsBox.setOrientation(LinearLayout.VERTICAL);dashDetailsBox.setPadding(dp(4),dp(8),dp(4),0);dashDetailsBox.setVisibility(p.getBoolean("status_details_open",false)?View.VISIBLE:View.GONE);
        dashDetails=txt("",12,muted,false);dashDetailsBox.addView(dashDetails);
        Button connections=smallButton("CONNECTION SETTINGS",blue);connections.setOnClickListener(v->drawSettings());dashDetailsBox.addView(connections);
        box.addView(dashDetailsBox);body.addView(box);refreshConnectionDashboard();
    }

    void refreshConnectionDashboard(){
        if(p==null||dashCamera==null)return;
        ConnectionStatus.Report r=ConnectionStatus.build(this,p);
        dashCamera.setText("●  "+r.cameraHeadline());dashCamera.setTextColor(dashColor(r.cameraColor));
        dashInternet.setText("●  "+r.internetHeadline());dashInternet.setTextColor(dashColor(r.internetColor));
        dashTransfer.setText("●  "+r.transferHeadline());dashTransfer.setTextColor(dashColor(r.transferColor));
        dashUpload.setText("●  "+r.uploadHeadline());dashUpload.setTextColor(dashColor(r.uploadColor));
        if(dashDetails!=null){
            StringBuilder d=new StringBuilder();
            d.append("Camera: ").append(r.cameraName).append(" • ").append(r.cameraState).append('\n');
            d.append("Connection type: ").append(r.connectionType).append('\n');
            d.append("Wi-Fi / SSID: ").append(r.wifi).append('\n');
            d.append("Phone IP: ").append(r.ip).append('\n');
            d.append("Internet: ").append(r.internet).append('\n');
            d.append("Cellular/data: ").append(r.cellular).append('\n');
            d.append("Transfer: ").append(r.transfer).append(" • Speed: ").append(r.transferSpeed).append('\n');
            d.append("Photo progress: ").append(r.photoProgress).append('\n');
            d.append("Upload queue: ").append(r.waiting).append(" waiting • ").append(r.uploading).append(" uploading • ").append(r.completed).append(" completed • ").append(r.failed).append(" failed").append('\n');
            d.append("Destination: ").append(r.uploadDestination);
            dashDetails.setText(d.toString());
        }
    }

    int dashColor(int level){return level==ConnectionStatus.GREEN?green:(level==ConnectionStatus.YELLOW?amber:red);}

'''
if marker not in s: raise SystemExit('0.7.1 dashboard helper marker missing')
s=s.replace(marker,helpers+marker,1)
for oldver in ['Nikon Auto Upload 0.7.0','Nikon Auto Upload 0.6.2']:
    s=s.replace(oldver,'Nikon Auto Upload 0.7.1')
p.write_text(s)

# ---------- USB module: publish transfer state/progress/speed ----------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/UsbCameraActivity.java')
s=p.read_text()

old='''                String name=cameraName(d);p.edit().putString("usb_state","USB-C connected").putString("usb_camera_name",name).putString("usb_vid_pid",String.format(Locale.US,"%04X:%04X",d.getVendorId(),d.getProductId())).apply();'''
if old not in s:
    old='''                String name=cameraName(d);p.edit().putString("usb_state","Connected").putString("usb_camera_name",name).putString("usb_vid_pid",String.format(Locale.US,"%04X:%04X",d.getVendorId(),d.getProductId())).apply();'''
new='''                String name=cameraName(d);p.edit().putString("usb_state","USB-C connected").putString("usb_camera_name",name).putString("usb_vid_pid",String.format(Locale.US,"%04X:%04X",d.getVendorId(),d.getProductId())).putString("usb_transfer_state","Idle").putString("status_transfer_state","Idle").putInt("status_photo_current",0).putInt("status_photo_total",0).putLong("status_transfer_speed_bps",0L).apply();'''
if old not in s: raise SystemExit('0.7.1 USB connect publisher target missing')
s=s.replace(old,new,1)

old='''        p.edit().putString("usb_state",message==null?"Disconnected":message).apply();'''
new='''        p.edit().putString("usb_state",message==null?"Disconnected":message).putString("usb_transfer_state","Idle").putString("status_transfer_state","Idle").putLong("status_transfer_speed_bps",0L).apply();'''
if old not in s: raise SystemExit('0.7.1 USB disconnect publisher target missing')
s=s.replace(old,new,1)

old='''            int totalFiles=list.size(),index=0;
            for(UsbPtpCamera.PhotoObject x:list){'''
new='''            int totalFiles=list.size(),index=0;
            p.edit().putInt("status_photo_total",totalFiles).putInt("status_photo_current",0).putString("status_transfer_state","Transferring").putString("usb_transfer_state","Transferring").putLong("status_transfer_speed_bps",0L).apply();
            for(UsbPtpCamera.PhotoObject x:list){'''
if old not in s: raise SystemExit('0.7.1 USB batch publisher target missing')
s=s.replace(old,new,1)

old='''                if(ptp==null||!ptp.isOpen())break;index++;final int fileIndex=index;
                try{
                    File dir=new File(getCacheDir(),"usb_import");'''
new='''                if(ptp==null||!ptp.isOpen())break;index++;final int fileIndex=index;
                try{
                    final long transferStarted=System.nanoTime();final long[] lastSpeedWrite={0L};
                    p.edit().putInt("status_photo_current",fileIndex).putInt("status_photo_total",totalFiles).putString("status_transfer_state","Transferring").putString("usb_transfer_state","Transferring").apply();
                    File dir=new File(getCacheDir(),"usb_import");'''
if old not in s: raise SystemExit('0.7.1 USB file publisher target missing')
s=s.replace(old,new,1)

old='''                    ptp.download(x,part,(bytes,max)->runOnUiThread(()->{int pct=max<=0?0:(int)Math.min(100,(bytes*100L)/max);progress.setProgress(pct);summary.setText("Transferring "+x.name+" • "+pct+"% • "+formatBytes(bytes)+" / "+formatBytes(max));}));'''
new='''                    ptp.download(x,part,(bytes,max)->{
                        long now=System.nanoTime();double secs=Math.max(.001,(now-transferStarted)/1_000_000_000d);long bps=(long)(bytes/secs);
                        if(now-lastSpeedWrite[0]>500_000_000L){lastSpeedWrite[0]=now;p.edit().putLong("status_transfer_speed_bps",bps).apply();}
                        runOnUiThread(()->{int pct=max<=0?0:(int)Math.min(100,(bytes*100L)/max);progress.setProgress(pct);summary.setText("Transferring "+x.name+" • "+pct+"% • "+formatBytes(bytes)+" / "+formatBytes(max)+" • "+ConnectionStatus.formatSpeed(bps));});
                    });'''
if old not in s: raise SystemExit('0.7.1 USB speed target missing')
s=s.replace(old,new,1)

old='''                    markImported(x);sendIntoApp(done,x.name);p.edit().putString("usb_last_status","Imported "+x.name).putString("usb_last_photo",x.name).putLong("usb_last_photo_time",System.currentTimeMillis()).apply();'''
new='''                    markImported(x);sendIntoApp(done,x.name);p.edit().putString("usb_last_status","Imported "+x.name).putString("usb_last_photo",x.name).putLong("usb_last_photo_time",System.currentTimeMillis()).putString("usb_transfer_state","Complete").putString("status_transfer_state","Complete").putInt("status_photo_current",fileIndex).putInt("status_photo_total",totalFiles).apply();'''
if old not in s: raise SystemExit('0.7.1 USB complete target missing')
s=s.replace(old,new,1)

old='''                }catch(Exception e){p.edit().putString("usb_last_status","Failed: "+shortMsg(e)).apply();runOnUiThread(()->setStatus("USB transfer failed for "+x.name+": "+shortMsg(e),true));}'''
new='''                }catch(Exception e){p.edit().putString("usb_last_status","Failed: "+shortMsg(e)).putString("usb_transfer_state","Failed").putString("status_transfer_state","Failed").apply();runOnUiThread(()->setStatus("USB transfer failed for "+x.name+": "+shortMsg(e),true));}'''
if old not in s: raise SystemExit('0.7.1 USB fail target missing')
s=s.replace(old,new,1)
p.write_text(s)

# ---------- Flickr queue: active-upload count; queue itself remains unchanged ----------
p=Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload/DirectTransferService.java')
s=p.read_text()
old='''        Network cell=cellularNetwork;if(cell==null){if(!cellularRequestActive)requestCellular();return;}processing=true;
        try{'''
new='''        Network cell=cellularNetwork;if(cell==null){if(!cellularRequestActive)requestCellular();return;}processing=true;p.edit().putInt("status_uploading_count",1).apply();
        try{'''
if old not in s: raise SystemExit('0.7.1 Flickr active target missing')
s=s.replace(old,new,1)
old='''        }finally{processing=false;}
    }'''
new='''        }finally{processing=false;p.edit().putInt("status_uploading_count",0).apply();}
    }'''
if old not in s: raise SystemExit('0.7.1 Flickr finally target missing')
s=s.replace(old,new,1)
p.write_text(s)

# ---------- Help ----------
p=Path('nikon-auto-upload/app/src/main/assets/CAMERA_SETUP_HELP.txt')
if p.exists():
    s=p.read_text().replace('Version 0.7.0 beta','Version 0.7.1 beta')
    s+='''\n\nCONNECTION STATUS DASHBOARD (0.7.1)\n-----------------------------------\nThe Home screen now has a shared camera-status card for every transport. It shows camera/model, connection type, internet/cellular state, transfer progress, transfer speed when reported, and Flickr queue status. Tap DETAILS to expand SSID/network, phone IP, transfer details, queue counts and destination.\n\nGreen means working. Yellow means connected but limited (for example USB-C is working while internet is unavailable, or uploads are waiting). Red means disconnected, failed, or needs attention. USB-C importing remains fully functional without Wi-Fi or internet; Flickr photos remain queued until internet returns. The shared ConnectionStatus model is brand-neutral so Nikon, Sony, Canon, Fujifilm and future connectors can report into the same dashboard.\n'''
    p.write_text(s)
