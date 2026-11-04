from pathlib import Path
import os

# 0.8.7 burst reliability patch. Run after the 0.8.6 migration chain.
# Retain the app's original camera support, overlays, and Flickr preferences.
root = Path('nikon-auto-upload/app/src/main/java/com/nikonautoupload')

def one(s, old, new, label):
    n=s.count(old)
    if n!=1: raise SystemExit('0.8.7 '+label+' expected once, found '+str(n))
    return s.replace(old,new,1)

p=root/'SimpleFtpServer.java';s=p.read_text()
if s.count('s.setSoTimeout(120000);')!=2: raise SystemExit('FTP timeouts changed')
s=s.replace('s.setSoTimeout(120000);','s.setSoTimeout(900000);')
s=one(s,'ss.setSoTimeout(30000);','ss.setSoTimeout(120000);','PASV timeout')
s=one(s,'File part = new File(tempDir, safeName + ".part");\n                        File done = new File(tempDir, safeName);',
'''String uniqueId = java.util.UUID.randomUUID().toString();
                        File part = new File(tempDir, uniqueId + "__" + safeName + ".part");
                        File done = new File(tempDir, uniqueId + "__" + safeName + ".ready");''','unique staging')
s=one(s,'''if (done.exists()) done.delete();
                        if (!part.renameTo(done)) {
                            copy(part, done);
                            part.delete();
                        }''','if (!part.renameTo(done)) throw new IOException("Cannot finalize camera file: " + safeName);','atomic staging')
s=one(s,'        } finally {\n            closeQuietly(dataServer);\n        }\n    }',
'''        } finally {
            closeQuietly(dataServer);
            if (running) listener.onStatus("Camera FTP session ended — waiting for reconnection");
        }
    }''','disconnect status')
p.write_text(s)

p=root/'DirectTransferService.java';s=p.read_text()
s=one(s,'private final ExecutorService io=Executors.newSingleThreadExecutor();',
'private final ExecutorService io=Executors.newSingleThreadExecutor();\n    private final ExecutorService saves=Executors.newSingleThreadExecutor();','independent photo saver')
s=one(s,'p.edit().putBoolean("receiver_running",true).putString("ftp_state",p.getBoolean("receiver_paused",false)?"Receiver paused":"Waiting for camera").apply();',
'''p.edit().putBoolean("receiver_running",true).putBoolean("camera_ftp_session_active",false).putString("ftp_state",p.getBoolean("receiver_paused",false)?"Receiver paused":"Waiting for camera").apply();
        recoverBufferedTransfers();''','startup recovery')
s=one(s,'File temp=new File(getCacheDir(),"nikon_ftp");ftp=new SimpleFtpServer(FTP_PORT,p.getString("ftp_user",DEFAULT_FTP_USER),p.getString("ftp_password",DEFAULT_FTP_PASSWORD),temp,this);ftp.start();',
'File temp=new File(getFilesDir(),"nikon_ftp");SimpleFtpServer candidate=new SimpleFtpServer(FTP_PORT,p.getString("ftp_user",DEFAULT_FTP_USER),p.getString("ftp_password",DEFAULT_FTP_PASSWORD),temp,this);candidate.start();ftp=candidate;','safe FTP startup')
s=one(s,'p.edit().putBoolean("receiver_paused",true).putString("ftp_state","Receiver paused").apply();',
'p.edit().putBoolean("receiver_paused",true).putBoolean("camera_ftp_session_active",false).putString("ftp_state","Receiver paused").apply();','pause wake')
s=one(s,'p.edit().putBoolean("receiver_paused",false).putString("ftp_state","Waiting for camera").apply();startServerOnly();broadcast("Camera receiving resumed",findBestLocalIp());',
'p.edit().putBoolean("receiver_paused",false).putString("ftp_state","Waiting for camera").apply();recoverBufferedTransfers();startServerOnly();broadcast("Camera receiving resumed",findBestLocalIp());','resume recovery')
s=one(s,'        if(ftp!=null){ftp.stop();ftp=null;}\n        startServerOnly();\n        broadcast("FTP receiver restarted with updated login",findBestLocalIp());',
'        if(ftp!=null){ftp.stop();ftp=null;}\n        recoverBufferedTransfers();startServerOnly();\n        broadcast("FTP receiver restarted with updated login",findBestLocalIp());','restart recovery')
s=one(s,'    @Override public void onPhotoReceived(File file,String originalName){\n        io.submit(()->{',
'''    // Completed FTP transfers survive app restarts; save photos independent of Flickr.
    private void recoverBufferedTransfers(){
        for(File folder:new File[]{new File(getFilesDir(),"nikon_ftp"),new File(getCacheDir(),"nikon_ftp")}){
            File[] ready=folder.listFiles((dir,name)->
                name.endsWith(".ready") || (!name.endsWith(".part") &&
                (name.toLowerCase(Locale.US).endsWith(".jpg") || name.toLowerCase(Locale.US).endsWith(".jpeg") ||
                 name.toLowerCase(Locale.US).endsWith(".nef") || name.toLowerCase(Locale.US).endsWith(".cr3"))));
            if(ready==null)continue;
            Arrays.sort(ready,Comparator.comparing(File::getName));
            for(File file:ready){
                String filename=file.getName(),original=filename;
                if(filename.endsWith(".ready") && filename.contains("__"))
                    original=filename.substring(filename.indexOf("__")+2,filename.length()-".ready".length());
                onPhotoReceived(file,original);
            }
        }
    }

    @Override public void onPhotoReceived(File file,String originalName){
        saves.submit(()->{''','photo staging and replay')
s=one(s,'if(p.getBoolean("receiver_paused",false)&&!usbImport){file.delete();broadcast("Camera receiving is paused",findBestLocalIp());return;}',
'if(p.getBoolean("receiver_paused",false)&&!usbImport){addEvent("Photo retained for resume: "+originalName);broadcast("Receiver paused — pending photo kept",findBestLocalIp());return;}','keep paused photo')
s=one(s,'if(send){broadcast("Received "+originalName+" — saved on phone and queued for Flickr",findBestLocalIp());processPending();}else broadcast("Received "+originalName+" — saved on phone; Flickr upload is OFF",findBestLocalIp());',
'if(send){broadcast("Received "+originalName+" — saved on phone and queued for Flickr",findBestLocalIp());io.submit(this::processPending);}else broadcast("Received "+originalName+" — saved on phone; Flickr upload is OFF",findBestLocalIp());','nonblocking Flickr queue')
s=one(s,'notifyEvent("Photo receive error",shortMessage(e));addEvent("Receive error: "+shortMessage(e));broadcast("Photo receive error: "+shortMessage(e),findBestLocalIp());',
'notifyEvent("Photo receive error",shortMessage(e));addEvent("Incoming photo retained for retry: "+originalName+" — "+shortMessage(e));broadcast("Photo save deferred — retry after receiver restart",findBestLocalIp());','retry on save failure')
s=one(s,'if(low.contains("tcp connected")){p.edit().putString("ftp_state",MultiCameraSupport.cameraName(p)+" connected").apply();',
'if(low.contains("tcp connected")){p.edit().putBoolean("camera_ftp_session_active",true).putString("ftp_state",MultiCameraSupport.cameraName(p)+" connected").apply();','connection wake')
s=one(s,'else if(low.contains("login accepted")){p.edit().putString("ftp_state",MultiCameraSupport.cameraName(p)+" logged in").apply();',
'else if(low.contains("login accepted")){p.edit().putBoolean("camera_ftp_session_active",true).putString("ftp_state",MultiCameraSupport.cameraName(p)+" logged in").apply();','login wake')
s=one(s,'else if(low.contains("login rejected")||low.contains("session ended")){p.edit().putString("ftp_state",text).apply();',
'else if(low.contains("login rejected")||low.contains("session ended")){p.edit().putBoolean("camera_ftp_session_active",false).putString("ftp_state",text).apply();','disconnect wake')
s=one(s,'Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Could not create phone photo");\n        try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Could not open phone photo");byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}\n        if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}return u;',
'''Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Could not create phone photo");
        try{
            try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Could not open phone photo");byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}
            return u;
        }catch(Exception e){try{getContentResolver().delete(u,null,null);}catch(Exception ignored){}throw e;}''','incomplete gallery cleanup')
s=one(s,'retry.shutdownNow();io.shutdownNow();super.onDestroy();',
'p.edit().putBoolean("camera_ftp_session_active",false).apply();retry.shutdownNow();io.shutdownNow();saves.shutdownNow();super.onDestroy();','shutdown')
p.write_text(s)

p=root/'MainActivity.java';s=p.read_text()
s=one(s,'            refreshLiveViews(s);\n        }',
'            refreshLiveViews(s);\n            applyKeepAwake();\n        }','wake update')
s=one(s,'void applyKeepAwake(){if(p!=null&&p.getBoolean("keep_screen_awake",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}',
'void applyKeepAwake(){if(p!=null&&(p.getBoolean("keep_screen_awake",false)||p.getBoolean("camera_ftp_session_active",false)))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}','automatic awake')
p.write_text(s)
print('Camera Auto Upload 0.8.7 reliability fixes applied')
