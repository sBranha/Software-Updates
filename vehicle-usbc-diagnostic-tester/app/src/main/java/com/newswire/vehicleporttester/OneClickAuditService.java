package com.newswire.vehicleporttester;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Single, parked-only, read-only full USB capability audit. An Android
 * foreground worker runs the entire audit from one tap, saves an internal
 * checkpoint after every step, and automatically exports a single JSON file
 * to Downloads/VehiclePortTester. The car's screen cannot be changed from
 * Android and ECUs are NOT queried without a supported OBD adapter.
 */
public final class OneClickAuditService extends Service {
    public static final String EVENT="com.newswire.vehicleporttester.ONE_CLICK_AUDIT_EVENT";
    public static final String STOP="com.newswire.vehicleporttester.ONE_CLICK_STOP";
    public static final String FILE="vehicle-v077-one-click-full-audit.json";
    private static final String CHANNEL="vehicle_port_one_click";
    private static final int NOTIFICATION_ID=7717;
    private static final int SECONDS=60;
    private static final int WARMUP_SECONDS=5;
    private static final int MAX_SAMPLE=8192;
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private final Object fileLock=new Object();
    private volatile boolean cancelled=false;
    private Thread worker;
    private JSONObject report;
    private long started;
    private long captureStart;
    private PowerManager.WakeLock wake;
    private Runnable hardStop;

    @Override public IBinder onBind(Intent i){return null;}

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&STOP.equals(i.getAction())){
            cancelled=true;
            Process.killProcess(Process.myPid()); // dedicated :one_click process only
            return START_NOT_STICKY;
        }
        if(worker!=null&&worker.isAlive())return START_NOT_STICKY;
        cancelled=false;
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(nm!=null && Build.VERSION.SDK_INT>=26)
                nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "Vehicle Port Tester full audit",NotificationManager.IMPORTANCE_LOW));
            Notification n=new Notification.Builder(this,CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("Vehicle Port Tester: full USB audit")
                    .setContentText("Read-only, automatic recording in progress")
                    .setOngoing(true).build();
            if(Build.VERSION.SDK_INT>=34)
                startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
            else startForeground(NOTIFICATION_ID,n);
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            if(pm!=null){
                wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "VehiclePortTester:OneClickFullAudit");
                wake.setReferenceCounted(false);
                wake.acquire(155000);
            }
            hardStop=()->{
                if(worker!=null&&worker.isAlive()){
                    try{
                        if(report!=null){
                            report.put("status","worker_hard_timeout_150_seconds");
                            report.put("last_operation",report.optString("current_operation","unknown"));
                            checkpoint();
                        }
                    }catch(Exception ignored){}
                    Process.killProcess(Process.myPid());
                }
            };
            mainHandler.postDelayed(hardStop,150000);
        }catch(Exception e){
            notifyProgress("startup_failed",0,"Unable to start full audit foreground service: "
                    +e.getClass().getSimpleName(),true);
            stopSelf();
            return START_NOT_STICKY;
        }
        worker=new Thread(this::runAudit,"OneClickFullAudit");
        worker.start();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy(){
        release();
        super.onDestroy();
    }

    private void release(){
        if(hardStop!=null)mainHandler.removeCallbacks(hardStop);
        try{if(wake!=null&&wake.isHeld())wake.release();}
        catch(Exception ignored){}
    }

    private static String utc(){
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private static String filename(){
        return "Vehicle-Capability-Explorer-v0.8.0-SCAN-EVERYTHING-"
                +new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())
                +".json";
    }

    private void notifyProgress(String stage,int percent,String note,boolean finished){
        Intent intent=new Intent(EVENT);
        intent.setPackage(getPackageName());
        intent.putExtra("stage",stage);
        intent.putExtra("percent",Math.max(0,Math.min(100,percent)));
        intent.putExtra("text",note);
        intent.putExtra("done",finished);
        intent.putExtra("worker_pid",Process.myPid());
        sendBroadcast(intent);
    }

    private void checkpoint(){
        synchronized(fileLock){
            if(report==null)return;
            try{
                report.put("last_checkpoint_utc",utc());
                report.put("elapsed_ms",SystemClock.elapsedRealtime()-started);
                byte[] raw=report.toString(2).getBytes(StandardCharsets.UTF_8);
                File tmp=new File(getFilesDir(),FILE+".tmp");
                File target=new File(getFilesDir(),FILE);
                try(FileOutputStream f=new FileOutputStream(tmp)){
                    f.write(raw);f.getFD().sync();
                }
                if(!tmp.renameTo(target)){
                    try(FileOutputStream f=new FileOutputStream(target)){f.write(raw);}
                    tmp.delete();
                }
            }catch(Exception ignored){}
        }
    }

    private void operation(String name,int progress){
        try{
            report.put("current_operation",name);
            report.put("operation_entered_utc",utc());
        }catch(Exception ignored){}
        checkpoint();
        notifyProgress(name,progress,"Full audit: "+name.replace('_',' '),false);
    }

    private JSONObject usbSnapshot(){
        JSONObject value=new JSONObject();
        try{
            UsbManager usb=(UsbManager)getSystemService(Context.USB_SERVICE);
            UsbAccessory[] accessories=usb==null?null:usb.getAccessoryList();
            value.put("time_utc",utc());
            value.put("usb_manager_present",usb!=null);
            value.put("accessories_count",accessories==null?0:accessories.length);
            value.put("host_devices_count",usb==null?0:usb.getDeviceList().size());
            if(accessories!=null&&accessories.length>0){
                UsbAccessory a=accessories[0];
                value.put("accessory_manufacturer",a.getManufacturer());
                value.put("accessory_model",a.getModel());
                value.put("accessory_version",a.getVersion());
                value.put("android_permission",usb.hasPermission(a));
            }
            IntentFilter filter=new IntentFilter("android.hardware.usb.action.USB_STATE");
            Intent sticky=Build.VERSION.SDK_INT>=33
                    ?registerReceiver(null,filter,Context.RECEIVER_NOT_EXPORTED)
                    :registerReceiver(null,filter);
            JSONObject state=new JSONObject();
            if(sticky!=null){
                for(String key:new String[]{"connected","configured","host_connected","accessory"}){
                    if(sticky.hasExtra(key))
                        state.put(key,sticky.getBooleanExtra(key,false));
                }
            }
            value.put("usb_state",state);
        }catch(Exception e){
            try{value.put("error",e.getClass().getSimpleName());}
            catch(Exception ignored){}
        }
        return value;
    }

    private JSONObject capabilityPhoneApps(){
        JSONObject out=new JSONObject();
        JSONArray apps=new JSONArray();
        try{
            PackageManager pm=getPackageManager();
            for(String action:new String[]{
                "androidx.car.app.CarAppService",
                "android.media.browse.MediaBrowserService",
                "androidx.media3.session.MediaLibraryService"}){
                List<ResolveInfo> items=pm.queryIntentServices(new Intent(action),0);
                if(items==null)continue;
                for(ResolveInfo r:items){
                    if(apps.length()>=60)break;
                    if(r.serviceInfo==null)continue;
                    JSONObject a=new JSONObject();
                    a.put("service_type",action);
                    a.put("phone_package_name",r.serviceInfo.packageName);
                    a.put("app_label",String.valueOf(r.loadLabel(pm)));
                    apps.put(a);
                }
            }
            out.put("phone_side_services",apps);
            out.put("services_count",apps.length());
            out.put("note","These are phone-side Android Auto/media service declarations, NOT apps running in Ford SYNC.");
        }catch(Exception ex){
            try{out.put("error",ex.getClass().getSimpleName());}
            catch(Exception ignored){}
        }
        return out;
    }

    private JSONObject bluetoothObdCapability(){
        JSONObject o=new JSONObject();
        try{
            BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter bt=bm==null?null:bm.getAdapter();
            o.put("hardware_available",bt!=null);
            o.put("read_only_detection",true);
            o.put("vehicle_commands_sent",0);
            if(bt==null){o.put("status","no_bluetooth_hardware");return o;}
            if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(
                    android.Manifest.permission.BLUETOOTH_CONNECT)
                    !=PackageManager.PERMISSION_GRANTED){
                o.put("status","skipped_bluetooth_connect_permission_not_granted");
                o.put("note","No runtime prompt in unattended scan; this is NOT an OBD scan.");
                return o;
            }
            o.put("enabled",bt.isEnabled());
            JSONArray devices=new JSONArray();
            int probable=0;
            Set<BluetoothDevice> bonded=bt.getBondedDevices();
            if(bonded!=null)for(BluetoothDevice d:bonded){
                String name=d.getName()==null?"Unknown paired device":d.getName();
                boolean obd=name.toLowerCase(Locale.US).matches(
                    ".*(elm327|obd|obdlink|veepeak|vgate|vlink|icar|carista|stn).*");
                JSONObject item=new JSONObject();
                item.put("name",name);
                item.put("potential_obd_adapter_name",obd);
                if(obd)probable++;
                devices.put(item);
            }
            o.put("paired_devices",devices);
            o.put("candidate_obd_adapters",probable);
            o.put("status",probable>0?"possible_paired_obd_adapter_found":"no_named_obd_adapter_found");
            o.put("note","No Bluetooth session opened or diagnostic PIDs queried. Name match alone does not prove a working OBD adapter.");
        }catch(Exception ex){
            try{o.put("status","unavailable");o.put("error",ex.getClass().getSimpleName());}
            catch(Exception ignored){}
        }
        return o;
    }

    private JSONObject openClose(int attempt){
        JSONObject r=new JSONObject();
        ParcelFileDescriptor fd=null;
        try{
            r.put("attempt",attempt);
            r.put("before",usbSnapshot());
            UsbManager usb=(UsbManager)getSystemService(Context.USB_SERVICE);
            UsbAccessory[] aa=usb==null?null:usb.getAccessoryList();
            if(aa==null||aa.length==0)r.put("status","no_accessory");
            else if(!usb.hasPermission(aa[0]))r.put("status","permission_required");
            else{
                fd=usb.openAccessory(aa[0]);
                r.put("opened",fd!=null);
                r.put("status",fd==null?"null_descriptor":"opened");
            }
        }catch(Exception ex){
            try{r.put("status","open_error");r.put("error_type",ex.getClass().getSimpleName());}
            catch(Exception ignored){}
        }finally{
            if(fd!=null){
                try{fd.close();r.put("closed",true);}
                catch(Exception ignored){}
            }
        }
        return r;
    }

    private static final class SecondStats {
        int bytes, reads, reads39, reads67, others, headerLooks;
        JSONObject json(int sec)throws Exception{
            JSONObject o=new JSONObject();
            o.put("second",sec);o.put("bytes",bytes);o.put("reads",reads);
            o.put("reads_39_bytes",reads39);o.put("reads_67_bytes",reads67);
            o.put("other_sizes",others);o.put("tls_like_headers",headerLooks);
            return o;
        }
    }

    private static JSONObject sum(SecondStats[] values,int begin,int end)
            throws Exception{
        int bytes=0,reads=0,r39=0,r67=0,other=0,headers=0;
        for(int x=begin;x<end&&x<values.length;x++){
            SecondStats b=values[x];
            bytes+=b.bytes;reads+=b.reads;r39+=b.reads39;r67+=b.reads67;
            other+=b.others;headers+=b.headerLooks;
        }
        int seconds=Math.min(end,values.length)-begin;
        JSONObject o=new JSONObject();
        o.put("start_second",begin);o.put("end_second_exclusive",end);
        o.put("seconds",seconds);o.put("bytes",bytes);o.put("read_events",reads);
        o.put("reads_39_bytes",r39);o.put("reads_67_bytes",r67);
        o.put("other_size_reads",other);o.put("tls_like_read_headers",headers);
        o.put("bytes_per_second",seconds>0?(Math.round(bytes*100.0/seconds)/100.0):0);
        o.put("read_events_per_second",seconds>0?(Math.round(reads*100.0/seconds)/100.0):0);
        return o;
    }

    private JSONObject sampleUsb(){
        JSONObject test=new JSONObject();
        ParcelFileDescriptor fd=null;
        byte[] sample=new byte[MAX_SAMPLE];
        int stored=0;
        SecondStats[] bySecond=new SecondStats[SECONDS];
        for(int i=0;i<SECONDS;i++)bySecond[i]=new SecondStats();
        String outcome="no_accessory",step="preflight",previous="";
        int total=0,reads=0;
        try{
            test.put("duration_target_seconds",SECONDS);
            test.put("initial_buffer_discard_seconds",WARMUP_SECONDS);
            test.put("capture_sample_max_bytes",MAX_SAMPLE);
            test.put("read_only",true);
            UsbManager usb=(UsbManager)getSystemService(Context.USB_SERVICE);
            UsbAccessory[] aa=usb==null?null:usb.getAccessoryList();
            if(aa==null||aa.length==0)outcome="no_accessory";
            else if(!usb.hasPermission(aa[0]))outcome="permission_required";
            else{
                step="open_accessory";operation(step,20);
                fd=usb.openAccessory(aa[0]);
                test.put("opened",fd!=null);
                if(fd==null)outcome="accessory_open_returned_null";
                else{
                    step="configure_nonblocking";operation(step,21);
                    int old=Os.fcntlInt(fd.getFileDescriptor(),OsConstants.F_GETFL,0);
                    Os.fcntlInt(fd.getFileDescriptor(),OsConstants.F_SETFL,old|OsConstants.O_NONBLOCK);
                    int newFlags=Os.fcntlInt(fd.getFileDescriptor(),OsConstants.F_GETFL,0);
                    boolean verified=(newFlags&OsConstants.O_NONBLOCK)!=0;
                    test.put("nonblocking_verified",verified);
                    if(!verified)outcome="nonblocking_unavailable";
                    else{
                        StructPollfd poll=new StructPollfd();
                        poll.fd=fd.getFileDescriptor();
                        poll.events=(short)(OsConstants.POLLIN|OsConstants.POLLERR|OsConstants.POLLHUP);
                        byte[] buffer=new byte[512];
                        captureStart=SystemClock.elapsedRealtime();
                        long deadline=captureStart+SECONDS*1000L;
                        long nextCheckpoint=captureStart+2000;
                        outcome="no_data_during_capture";
                        while(!cancelled&&SystemClock.elapsedRealtime()<deadline){
                            long now=SystemClock.elapsedRealtime();
                            int wait=(int)Math.min(250,Math.max(1,deadline-now));
                            step="poll";
                            if(!step.equals(previous)){operation(step,22);previous=step;}
                            int available=Os.poll(new StructPollfd[]{poll},wait);
                            if(available>0){
                                int events=poll.revents;poll.revents=0;
                                if((events&OsConstants.POLLIN)!=0){
                                    step="read";
                                    if(!step.equals(previous)){operation(step,22);previous=step;}
                                    int count;
                                    try{
                                        count=Os.read(fd.getFileDescriptor(),buffer,0,buffer.length);
                                    }catch(ErrnoException ex){
                                        if(ex.errno==OsConstants.EAGAIN)continue;
                                        throw ex;
                                    }
                                    if(count<=0){outcome="peer_closed_connection";break;}
                                    int sec=(int)Math.min(SECONDS-1,(SystemClock.elapsedRealtime()
                                        -captureStart)/1000L);
                                    SecondStats slot=bySecond[sec];
                                    slot.bytes+=count;slot.reads++;
                                    if(count==39)slot.reads39++;
                                    else if(count==67)slot.reads67++;
                                    else slot.others++;
                                    if(count>=9&&(buffer[0]&255)==6&&(buffer[1]&255)==11
                                        &&(buffer[4]&255)==23
                                        &&(buffer[5]&255)==3&&(buffer[6]&255)==3)
                                        slot.headerLooks++;
                                    total+=count;reads++;
                                    if(sec>=WARMUP_SECONDS && stored<MAX_SAMPLE){
                                        int take=Math.min(count,MAX_SAMPLE-stored);
                                        System.arraycopy(buffer,0,sample,stored,take);
                                        stored+=take;
                                    }
                                    outcome="receiving_data";
                                }else if((events&(OsConstants.POLLERR|OsConstants.POLLHUP))!=0){
                                    outcome="usb_disconnect_or_poll_error";break;
                                }
                            }
                            now=SystemClock.elapsedRealtime();
                            if(now>=nextCheckpoint){
                                int elapsed=(int)((now-captureStart)/1000L);
                                synchronized(fileLock){
                                    test.put("seconds_completed",elapsed);
                                    test.put("total_bytes_so_far",total);
                                    report.put("live_capture",test);
                                }
                                checkpoint();
                                int progress=25+Math.min(60,elapsed);
                                notifyProgress("usb_stream_capture",progress,
                                    "Recording USB communication: "+elapsed+"/60 sec, "
                                        +total+" bytes",false);
                                nextCheckpoint=now+2000;
                            }
                        }
                        if(cancelled)outcome="cancelled";
                        else if("receiving_data".equals(outcome)
                            &&SystemClock.elapsedRealtime()>=deadline)
                            outcome="completed_with_bytes";
                        test.put("actual_capture_ms",SystemClock.elapsedRealtime()-captureStart);
                    }
                }
            }
        }catch(Exception ex){
            outcome="usb_capture_error";
            try{
                test.put("error_at_operation",step);
                test.put("error_type",ex.getClass().getSimpleName());
                test.put("error_message",String.valueOf(ex.getMessage()));
                if(ex instanceof ErrnoException)
                    test.put("errno",((ErrnoException)ex).errno);
            }catch(Exception ignored){}
        }finally{
            if(fd!=null){
                try{fd.close();test.put("connection_closed",true);}
                catch(Exception ex){
                    try{test.put("connection_closed",false);test.put("close_error",ex.toString());}
                    catch(Exception ignored){}
                }
            }
            try{
                JSONArray seconds=new JSONArray();
                for(int i=0;i<SECONDS;i++)seconds.put(bySecond[i].json(i));
                test.put("seconds",seconds);
                test.put("first_5s_buffered",sum(bySecond,0,5));
                JSONArray segments=new JSONArray();
                segments.put(sum(bySecond,5,20));
                segments.put(sum(bySecond,20,35));
                segments.put(sum(bySecond,35,50));
                test.put("three_15_second_steady_windows",segments);
                test.put("all_45s_steady",sum(bySecond,5,50));
                test.put("final_10s",sum(bySecond,50,60));
                test.put("read_events_total",reads);
                test.put("bytes_received_total",total);
                test.put("post_warmup_sample_bytes",stored);
                StringBuilder hex=new StringBuilder(stored*3);
                char[] h="0123456789ABCDEF".toCharArray();
                for(int i=0;i<stored;i++){
                    if(i>0)hex.append(' ');
                    int v=sample[i]&255;
                    hex.append(h[v>>>4]).append(h[v&15]);
                }
                test.put("post_warmup_hex_sample",hex.toString());
                test.put("post_warmup_frame_heuristics",
                    UsbProtocolAnalyzer.analyze(sample,stored));
                test.put("outcome",outcome);
                test.put("vehicle_diagnostic_data_confirmed",false);
                test.put("note","TLS-like frames may be Android Auto or other encrypted traffic; cannot interpret vehicle diagnostics.");
            }catch(Exception ignored){}
        }
        return test;
    }

    private String writeToDownloads(String contents,String name)throws Exception{
        if(Build.VERSION.SDK_INT<29)throw new IllegalStateException(
            "Automatic public Downloads export needs Android 10 or later");
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,"application/json");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS+"/VehiclePortTester");
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new IllegalStateException("Downloads entry creation failed");
        try(OutputStream output=getContentResolver().openOutputStream(uri)){
            if(output==null)throw new IllegalStateException("Downloads writer unavailable");
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        ContentValues done=new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING,0);
        getContentResolver().update(uri,done,null,null);
        return "Downloads/VehiclePortTester/"+name;
    }

    private void runAudit(){
        started=SystemClock.elapsedRealtime();
        String status="completed";
        try{
            report=new JSONObject();
            report.put("report_format","Vehicle Capability Explorer one-click scan v1");
            report.put("app_version","0.8.0");
            report.put("scan_mode","automatic_read_only_capability_discovery");
            report.put("started_utc",utc());
            report.put("status","in_progress");
            report.put("read_only",true);
            report.put("bytes_written_to_vehicle",0);
            report.put("vehicle_commands_sent",0);
            report.put("obd_codes_scanned",false);
            report.put("ford_sync_internal_access_confirmed",false);
            report.put("vehicle_diagnostic_data_confirmed",false);
            report.put("phone_manufacturer",Build.MANUFACTURER);
            report.put("phone_model",Build.MODEL);
            report.put("phone_android_api",Build.VERSION.SDK_INT);
            report.put("screen_stays_awake",true);
            report.put("automatic_report_export",true);
            report.put("note","Run while vehicle parked. Opening Android accessory can interrupt Android Auto. No Ford ECU or SYNC commands are sent.");
            checkpoint();

            operation("baseline_usb_stability",2);
            JSONArray snapshots=new JSONArray();
            for(int i=0;i<5&&!cancelled;i++){
                snapshots.put(usbSnapshot());
                SystemClock.sleep(1000);
            }
            report.put("five_second_usb_stability",snapshots);
            report.put("baseline",snapshots.length()>0?snapshots.optJSONObject(0):JSONObject.NULL);
            checkpoint();

            operation("phone_side_car_apps",8);
            report.put("phone_car_media_services",capabilityPhoneApps());
            checkpoint();
            operation("paired_bluetooth_obd_adapter_availability",12);
            report.put("optional_bluetooth_obd",bluetoothObdCapability());
            checkpoint();

            operation("usb_open_close_three_attempts",15);
            JSONArray attempts=new JSONArray();
            for(int x=1;x<=3&&!cancelled;x++){
                attempts.put(openClose(x));
                checkpoint();
                SystemClock.sleep(450);
            }
            report.put("three_usb_open_close_checks",attempts);
            checkpoint();

            operation("usb_protocol_and_steady_state_60sec",22);
            JSONObject capture=sampleUsb();
            report.put("usb_stream_and_steady_analysis",capture);
            report.put("total_usb_bytes",capture.optInt("bytes_received_total",0));
            checkpoint();

            operation("final_usb_stability",89);
            JSONArray finalSamples=new JSONArray();
            for(int i=0;i<5&&!cancelled;i++){
                finalSamples.put(usbSnapshot());
                SystemClock.sleep(1000);
            }
            report.put("final_five_second_usb_samples",finalSamples);
            JSONObject limits=new JSONObject();
            limits.put("unplugged_test","not_performed: physical unplug required");
            limits.put("usb_a_vs_usb_c_ports","not_performed: physical cable movement required");
            limits.put("ford_sync_vs_android_auto_screen",
                "not_performed: vehicle screen state cannot be controlled or verified by phone");
            limits.put("manual_action_changes",
                "not_performed: a one-click unattended run cannot operate the Ford touchscreen");
            limits.put("obd_engine_transmission_abs",
                "not_performed: no verified connected diagnostic adapter session");
            report.put("not_automatable_or_unavailable_checks",limits);
            operation("analyze_explorer_screen_and_vehicle_capabilities",93);
            report.put("capability_map",CapabilityMatrix.build(this,report));
            checkpoint();
            report.put("interpretation","This automatically measures Android accessory connectivity and incoming USB traffic. It does not prove access to Ford SYNC internals, decrypted Android Auto content or vehicle ECUs.");
            status=cancelled?"cancelled":"completed";
        }catch(Exception ex){
            status="worker_error";
            try{
                if(report!=null){
                    report.put("worker_error_type",ex.getClass().getSimpleName());
                    report.put("worker_error_message",String.valueOf(ex.getMessage()));
                }
            }catch(Exception ignored){}
        }finally{
            if(report==null)report=new JSONObject();
            try{
                report.put("status",status);
                report.put("completed_utc",utc());
                report.put("duration_ms",SystemClock.elapsedRealtime()-started);
                report.put("auto_download_success",false);
                checkpoint();
                operation("save_complete_report_to_downloads",96);
                // The downloaded JSON includes the same complete export metadata
                // as the internal checkpoint, so it is self-contained.
                String name=filename();
                String path="Downloads/VehiclePortTester/"+name;
                report.put("saved_downloads_path",path);
                report.put("auto_download_success",true);
                checkpoint();
                writeToDownloads(report.toString(2),name);
            }catch(Exception ex){
                try{
                    report.put("auto_download_success",false);
                    report.remove("saved_downloads_path");
                    report.put("auto_download_error",ex.getClass().getSimpleName()
                        +": "+String.valueOf(ex.getMessage()));
                }catch(Exception ignored){}
            }
            checkpoint();
            notifyProgress("finished",100,
                "Full audit finished. "+(report.optBoolean("auto_download_success",false)
                    ?report.optString("saved_downloads_path")
                    :"Saved in app. Use EXPORT JSON REPORT."),true);
            release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
}
