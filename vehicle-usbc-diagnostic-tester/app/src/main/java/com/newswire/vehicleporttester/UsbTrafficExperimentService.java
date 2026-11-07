package com.newswire.vehicleporttester;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * v0.7.6: compares steady-state, phone-side USB accessory traffic after excluding
 * the initial buffered burst. An isolated, foreground process makes native USB
 * read stalls terminable. No vehicle commands are ever written.
 *
 * User-supplied labels (Ford SYNC, Android Auto and action markers) are not
 * independently validated; this test cannot access SYNC internals or ECUs.
 */
public final class UsbTrafficExperimentService extends Service {
    public static final String ACTION_EVENT =
            "com.newswire.vehicleporttester.USB_TRAFFIC_EXPERIMENT_EVENT";
    public static final String ACTION_MARK =
            "com.newswire.vehicleporttester.USB_TRAFFIC_EXPERIMENT_MARK";
    public static final String ACTION_STOP =
            "com.newswire.vehicleporttester.USB_TRAFFIC_EXPERIMENT_STOP";
    public static final String EXTRA_MODE = "experiment_mode";
    public static final String EXTRA_MARK = "marker_label";
    public static final String SYNC = "ford_sync_steady";
    public static final String AUTO = "android_auto_steady";
    public static final String ACTIONS = "user_actions";
    public static final String AUTOMATIC = "automatic_usb_steady_state";
    public static final String REPORT_SYNC = "usb_v076_ford_sync_steady.json";
    public static final String REPORT_AUTO = "usb_v076_android_auto_steady.json";
    public static final String REPORT_ACTIONS = "usb_v076_action_markers.json";
    public static final String REPORT_AUTOMATIC = "usb_v077_automatic_steady_state.json";

    private static final int WARMUP_SECONDS = 5;
    private static final int STEADY_SECONDS = 40; // 5s warm-up + 3 x 10s + 5s final
    private static final int ACTION_SECONDS = 65; // 5s warm-up, 60s for markers
    private static final int MAX_CAPTURE = 4096;
    private static final int NID = 7616;
    private static final String CHANNEL = "vehicle_usb_traffic_v076";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private volatile boolean stopRequested;
    private Thread worker;
    private String mode;
    private JSONObject report;
    private long startedAt;
    private long captureStartedAt;
    private PowerManager.WakeLock lockPower;
    private Runnable cutoff;

    private final List<Marker> markerList = new ArrayList<>();

    static final class Marker {
        final int second;
        final long elapsedMs;
        final String label;
        Marker(int second, long elapsedMs, String label) {
            this.second = second;
            this.elapsedMs = elapsedMs;
            this.label = label;
        }
    }

    public static String fileFor(String mode) {
        if (SYNC.equals(mode)) return REPORT_SYNC;
        if (AUTO.equals(mode)) return REPORT_AUTO;
        if (AUTOMATIC.equals(mode)) return REPORT_AUTOMATIC;
        return REPORT_ACTIONS;
    }

    public static String labelFor(String mode) {
        if (SYNC.equals(mode)) return "Ford SYNC steady traffic";
        if (AUTO.equals(mode)) return "Android Auto steady traffic";
        if (AUTOMATIC.equals(mode)) return "One-click automatic steady USB analysis";
        return "Marked vehicle actions";
    }

    private static boolean validMode(String mode) {
        return SYNC.equals(mode) || AUTO.equals(mode) || ACTIONS.equals(mode) || AUTOMATIC.equals(mode);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRequested = true;
            Process.killProcess(Process.myPid()); // isolated :usb_experiment only
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_MARK.equals(intent.getAction())) {
            if (worker != null && worker.isAlive() && ACTIONS.equals(mode)) {
                String text = intent.getStringExtra(EXTRA_MARK);
                if (text != null && text.length() > 0) addMarker(text);
            }
            return START_NOT_STICKY;
        }
        if (worker != null && worker.isAlive()) return START_NOT_STICKY;
        mode = intent == null ? null : intent.getStringExtra(EXTRA_MODE);
        if (!validMode(mode)) { stopSelf(); return START_NOT_STICKY; }
        stopRequested = false;
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
                    new NotificationChannel(CHANNEL, "Vehicle USB traffic experiments",
                            NotificationManager.IMPORTANCE_LOW));
            Notification n = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("Vehicle Port Tester: " + labelFor(mode))
                    .setContentText("Read-only measurement running; return to app to stop")
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setOngoing(true).build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
            } else startForeground(NID, n);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                lockPower = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "VehiclePortTester:TrafficExperiment");
                lockPower.setReferenceCounted(false);
                lockPower.acquire(105000);
            }
            cutoff = () -> {
                if (worker != null && worker.isAlive()) {
                    try { state("worker_timeout_100_seconds"); }
                    catch (Exception ignored) {}
                    Process.killProcess(Process.myPid());
                }
            };
            handler.postDelayed(cutoff, 100000);
        } catch (Exception ex) {
            event("startup_error", 0,
                    "Foreground test not started: " + ex.getClass().getSimpleName(), true);
            stopSelf();
            return START_NOT_STICKY;
        }
        worker = new Thread(this::execute, "IsolatedTrafficExperiment");
        worker.start();
        return START_NOT_STICKY;
    }

    private void addMarker(String text) {
        int sec = captureStartedAt == 0 ? -1 :
                (int)((SystemClock.elapsedRealtime() - captureStartedAt) / 1000L);
        long elapsed = captureStartedAt == 0 ? -1 :
                SystemClock.elapsedRealtime() - captureStartedAt;
        synchronized (lock) {
            markerList.add(new Marker(sec, elapsed, text));
            try {
                if (report != null) report.put("user_action_markers", markerJson());
            } catch (Exception ignored) {}
            persistLocked();
        }
        event("action_marker", Math.min(99, Math.max(1, (sec * 100) / ACTION_SECONDS)),
                "Marked: " + text + " at " + sec + " sec", false);
    }

    private static String nowUtc() {
        SimpleDateFormat d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        d.setTimeZone(TimeZone.getTimeZone("UTC"));
        return d.format(new Date());
    }

    private JSONArray markerJson() throws Exception {
        JSONArray items = new JSONArray();
        for (Marker m : markerList) {
            JSONObject j = new JSONObject();
            j.put("label", m.label);
            j.put("elapsed_ms", m.elapsedMs);
            j.put("second", m.second);
            j.put("screen_or_action_unverified", true);
            items.put(j);
        }
        return items;
    }

    private void event(String stage, int progress, String message, boolean done) {
        Intent i = new Intent(ACTION_EVENT);
        i.setPackage(getPackageName());
        i.putExtra("stage", stage);
        i.putExtra("mode", mode);
        i.putExtra("progress", Math.max(0, Math.min(100, progress)));
        i.putExtra("message", message);
        i.putExtra("done", done);
        i.putExtra("pid", Process.myPid());
        sendBroadcast(i);
    }

    private void persistLocked() {
        if (report == null || mode == null) return;
        try {
            report.put("last_saved_utc", nowUtc());
            report.put("elapsed_ms", SystemClock.elapsedRealtime() - startedAt);
            File target = new File(getFilesDir(), fileFor(mode));
            File temp = new File(getFilesDir(), fileFor(mode) + ".tmp");
            byte[] bytes = report.toString(2).getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
                out.getFD().sync();
            }
            if (!temp.renameTo(target)) {
                try (FileOutputStream out = new FileOutputStream(target)) {
                    out.write(bytes);
                }
                temp.delete();
            }
        } catch (Exception ignored) {}
    }

    private void persist() {
        synchronized (lock) { persistLocked(); }
    }

    private void state(String stage) {
        synchronized (lock) {
            if (report == null) return;
            try {
                report.put("current_operation", stage);
                report.put("operation_entered_utc", nowUtc());
            } catch (Exception ignored) {}
            persistLocked();
        }
    }

    private JSONObject usbSnapshot() {
        JSONObject data = new JSONObject();
        try {
            UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbAccessory[] aa = usb == null ? null : usb.getAccessoryList();
            data.put("usb_manager_available", usb != null);
            data.put("accessory_count", aa == null ? 0 : aa.length);
            data.put("host_device_count", usb == null ? 0 : usb.getDeviceList().size());
            if (aa != null && aa.length > 0) {
                data.put("manufacturer", aa[0].getManufacturer());
                data.put("model", aa[0].getModel());
                data.put("permission", usb.hasPermission(aa[0]));
            }
            IntentFilter f = new IntentFilter("android.hardware.usb.action.USB_STATE");
            Intent sticky = Build.VERSION.SDK_INT >= 33
                    ? registerReceiver(null, f, Context.RECEIVER_NOT_EXPORTED)
                    : registerReceiver(null, f);
            JSONObject status = new JSONObject();
            if (sticky != null) {
                for (String key: new String[]{"connected","configured","host_connected","accessory"}) {
                    if (sticky.hasExtra(key)) status.put(key,
                            sticky.getBooleanExtra(key,false));
                }
            }
            data.put("usb_state", status);
        } catch (Exception ex) {
            try { data.put("snapshot_error", ex.getClass().getSimpleName()); }
            catch (Exception ignored) {}
        }
        return data;
    }

    private static final class Bucket {
        int bytes, chunks, headerLike, length35, length63, otherSizes;
        long firstMs=-1, lastMs=-1;
        JSONObject json(int second) throws Exception {
            JSONObject x = new JSONObject();
            x.put("second", second);
            x.put("bytes_received", bytes);
            x.put("read_events", chunks);
            x.put("header_like_events",headerLike);
            x.put("39_byte_events", length35);
            x.put("67_byte_events", length63);
            x.put("other_size_events", otherSizes);
            x.put("first_read_elapsed_ms", firstMs < 0 ? JSONObject.NULL : firstMs);
            x.put("last_read_elapsed_ms", lastMs < 0 ? JSONObject.NULL : lastMs);
            return x;
        }
    }

    private static JSONObject aggregate(Bucket[] bins, int start, int end)
            throws Exception {
        JSONObject m=new JSONObject();
        int bytes=0, chunks=0, headerLike=0, count39=0, count67=0, other=0;
        for (int i=Math.max(0,start);i<Math.min(end,bins.length);i++) {
            Bucket b=bins[i];
            bytes += b.bytes;
            chunks += b.chunks;
            headerLike += b.headerLike;
            count39 += b.length35;
            count67 += b.length63;
            other += b.otherSizes;
        }
        int seconds = Math.max(0,Math.min(end,bins.length)-Math.max(0,start));
        m.put("start_sec_inclusive",start);
        m.put("end_sec_exclusive",end);
        m.put("seconds", seconds);
        m.put("bytes_received",bytes);
        m.put("read_events",chunks);
        m.put("header_like_read_events",headerLike);
        m.put("39_byte_read_events",count39);
        m.put("67_byte_read_events",count67);
        m.put("other_size_read_events",other);
        m.put("bytes_per_second",seconds>0?
                Math.round((bytes/(double)seconds)*100.0)/100.0:JSONObject.NULL);
        m.put("read_events_per_second",seconds>0?
                Math.round((chunks/(double)seconds)*100.0)/100.0:JSONObject.NULL);
        return m;
    }

    private JSONObject comparisonReport(Bucket[] bins, int secs)
            throws Exception {
        JSONObject summary=new JSONObject();
        summary.put("excluded_initial_burst_seconds",WARMUP_SECONDS);
        summary.put("initial_burst",aggregate(bins,0,WARMUP_SECONDS));
        JSONArray windows=new JSONArray();
        for(int k=0;k<3;k++) {
            JSONObject w=aggregate(bins,WARMUP_SECONDS+k*10,WARMUP_SECONDS+(k+1)*10);
            w.put("window_number",k+1);
            windows.put(w);
        }
        summary.put("steady_windows",windows);
        summary.put("steady_total_30_seconds",aggregate(bins,5,35));
        summary.put("final_5_seconds",aggregate(bins,35,40));
        if (ACTIONS.equals(mode)) {
            JSONArray tests=new JSONArray();
            List<Marker> marks;
            synchronized(lock) { marks=new ArrayList<>(markerList); }
            for(Marker mark:marks) {
                JSONObject x=new JSONObject();
                x.put("label",mark.label);
                x.put("marked_at_second",mark.second);
                x.put("before_5s",aggregate(bins,Math.max(5,mark.second-5),mark.second));
                x.put("after_5s",aggregate(bins,mark.second,Math.min(secs,mark.second+5)));
                x.put("meaning","Observational rate difference, not proof that the action caused the change.");
                tests.put(x);
            }
            summary.put("action_comparisons",tests);
        }
        return summary;
    }

    private void execute() {
        startedAt=SystemClock.elapsedRealtime();
        ParcelFileDescriptor fd=null;
        int seconds=ACTIONS.equals(mode)?ACTION_SECONDS:STEADY_SECONDS;
        Bucket[] bins=new Bucket[seconds+1];
        for(int i=0;i<bins.length;i++)bins[i]=new Bucket();
        byte[] preview=new byte[MAX_CAPTURE];
        int previewStored=0;
        int allBytes=0, allEvents=0;
        String stage="preflight", outcome="not_started";
        try {
            report = new JSONObject();
            report.put("report_format", "Vehicle USB controlled traffic experiment v1");
            report.put("app_version","0.7.7");
            report.put("automatic_full_audit_phase",AUTOMATIC.equals(mode));
            report.put("test_label","steady_state_and_user_markers");
            report.put("selected_condition",mode);
            report.put("selected_condition_is_user_reported",true);
            report.put("started_utc",nowUtc());
            report.put("status","in_progress");
            report.put("read_only",true);
            report.put("vehicle_commands_sent",0);
            report.put("bytes_written",0);
            report.put("test_duration_seconds",seconds);
            report.put("warmup_exclusion_seconds",WARMUP_SECONDS);
            report.put("method","Ignore first 5 seconds of buffered traffic; measure 3 independent 10-second steady windows. Action mode also records user markers and before/after rates.");
            report.put("note","Android USB accessory access is not Ford SYNC or ECU diagnostic authorization. AUTOMATIC mode does not label the vehicle screen because the phone cannot read it. Opening accessory may interrupt Android Auto.");
            report.put("snapshot_before",usbSnapshot());
            report.put("user_action_markers",new JSONArray());
            state("preflight");
            event("preflight",1,"Checking Android accessory and USB permission",false);
            UsbManager usb=(UsbManager)getSystemService(Context.USB_SERVICE);
            UsbAccessory[] accessories=usb==null?null:usb.getAccessoryList();
            if(accessories==null||accessories.length==0)outcome="no_accessory";
            else if(!usb.hasPermission(accessories[0]))outcome="permission_required";
            else {
                stage="open_accessory";state(stage);
                fd=usb.openAccessory(accessories[0]);
                report.put("connection_opened",fd!=null);
                if(fd==null)outcome="open_returned_null";
                else {
                    stage="configure_nonblocking";state(stage);
                    int oldFlags=Os.fcntlInt(fd.getFileDescriptor(),OsConstants.F_GETFL,0);
                    Os.fcntlInt(fd.getFileDescriptor(),OsConstants.F_SETFL,
                            oldFlags|OsConstants.O_NONBLOCK);
                    int actualFlags=Os.fcntlInt(fd.getFileDescriptor(),OsConstants.F_GETFL,0);
                    boolean nonblock=(actualFlags&OsConstants.O_NONBLOCK)!=0;
                    report.put("nonblocking_verified",nonblock);
                    if(!nonblock)outcome="nonblocking_not_supported";
                    else {
                        StructPollfd p=new StructPollfd();
                        p.fd=fd.getFileDescriptor();
                        p.events=(short)(OsConstants.POLLIN|OsConstants.POLLERR|OsConstants.POLLHUP);
                        byte[] buffer=new byte[512];
                        captureStartedAt=SystemClock.elapsedRealtime();
                        long deadline=captureStartedAt+seconds*1000L;
                        long nextSample=captureStartedAt+1000L;
                        outcome="finished_no_bytes";
                        String lastStage="";
                        while(!stopRequested&&SystemClock.elapsedRealtime()<deadline) {
                            long now=SystemClock.elapsedRealtime();
                            int wait=(int)Math.min(250L,Math.max(1L,deadline-now));
                            stage="poll";
                            if(!stage.equals(lastStage)){state(stage);lastStage=stage;}
                            int ready=Os.poll(new StructPollfd[]{p},wait);
                            if(ready>0) {
                                int events=p.revents;
                                p.revents=0;
                                if((events&OsConstants.POLLIN)!=0) {
                                    stage="read";
                                    if(!stage.equals(lastStage)){state(stage);lastStage=stage;}
                                    int read;
                                    try {
                                        read=Os.read(fd.getFileDescriptor(),buffer,0,buffer.length);
                                    } catch(ErrnoException e) {
                                        if(e.errno==OsConstants.EAGAIN)continue;
                                        throw e;
                                    }
                                    if(read<=0){outcome="closed_by_peer";break;}
                                    long elapsed=SystemClock.elapsedRealtime()-captureStartedAt;
                                    int sec=(int)Math.min(seconds,elapsed/1000L);
                                    Bucket b=bins[sec];
                                    b.bytes+=read;b.chunks++;
                                    b.firstMs=b.firstMs<0?elapsed:b.firstMs;b.lastMs=elapsed;
                                    if(read==39)b.length35++;
                                    else if(read==67)b.length63++;
                                    else b.otherSizes++;
                                    if(read>=9&&(buffer[0]&255)==6&&(buffer[1]&255)==11
                                            &&(buffer[4]&255)==23
                                            &&(buffer[5]&255)==3&&(buffer[6]&255)==3)
                                        b.headerLike++;
                                    allBytes+=read;allEvents++;
                                    if(sec>=WARMUP_SECONDS&&previewStored<MAX_CAPTURE) {
                                        int n=Math.min(read,MAX_CAPTURE-previewStored);
                                        System.arraycopy(buffer,0,preview,previewStored,n);
                                        previewStored+=n;
                                    }
                                    outcome="received_bytes";
                                } else if((events&(OsConstants.POLLERR|OsConstants.POLLHUP))!=0) {
                                    outcome="poll_error_or_disconnect";break;
                                }
                            }
                            now=SystemClock.elapsedRealtime();
                            if(now>=nextSample) {
                                int tick=(int)Math.min(seconds,(now-captureStartedAt)/1000L);
                                synchronized(lock){
                                    report.put("last_completed_elapsed_seconds",tick);
                                    report.put("bytes_received_so_far",allBytes);
                                    report.put("read_events_so_far",allEvents);
                                    persistLocked();
                                }
                                event(tick<WARMUP_SECONDS?"discarding_warmup":"steady_measurement",
                                        Math.min(99,tick*100/seconds),
                                        (tick<WARMUP_SECONDS?"Ignoring initial buffered burst: ":"Measuring steady traffic: ")
                                        +tick+"/"+seconds+" sec; "+allBytes+" bytes",false);
                                nextSample=now+1000;
                            }
                        }
                        if(stopRequested)outcome="cancelled";
                        else if("received_bytes".equals(outcome)&&
                                SystemClock.elapsedRealtime()>=deadline)
                            outcome="completed_with_usb_bytes";
                        report.put("capture_elapsed_ms",SystemClock.elapsedRealtime()-captureStartedAt);
                    }
                }
            }
        }catch(Exception ex) {
            outcome="capture_error";
            if(ex instanceof ErrnoException){
                int errno=((ErrnoException)ex).errno;
                if(errno==OsConstants.EIO)outcome="usb_io_error";
                else if(errno==OsConstants.ENODEV||errno==OsConstants.EPIPE||errno==OsConstants.ENXIO)
                    outcome="usb_disconnected";
                try {report.put("error_errno",errno);}catch(Exception ignored){}
            }
            try {
                report.put("error_operation",stage);
                report.put("error_type",ex.getClass().getSimpleName());
                report.put("error_message",String.valueOf(ex.getMessage()));
            }catch(Exception ignored){}
        }finally {
            if(fd!=null){
                try {fd.close();report.put("connection_closed",true);}
                catch(Exception ex){
                    try {report.put("connection_closed",false);
                        report.put("close_error",String.valueOf(ex.getMessage()));}
                    catch(Exception ignored){}
                }
            }
            try {
                JSONArray series=new JSONArray();
                for(int i=0;i<seconds;i++)series.put(bins[i].json(i));
                report.put("per_second_measurements",series);
                report.put("analysis",comparisonReport(bins,seconds));
                report.put("total_bytes_including_warmup",allBytes);
                report.put("total_read_events_including_warmup",allEvents);
                report.put("captured_payload_preview_bytes",previewStored);
                StringBuilder hex=new StringBuilder(previewStored*3);
                char[] digits="0123456789ABCDEF".toCharArray();
                for(int i=0;i<previewStored;i++) {
                    if(i>0)hex.append(' ');
                    int b=preview[i]&255;
                    hex.append(digits[b>>>4]).append(digits[b&15]);
                }
                report.put("post_warmup_hex_sample",hex.toString());
                report.put("post_warmup_protocol_heuristic",
                        UsbProtocolAnalyzer.analyze(preview,previewStored));
                report.put("user_action_markers",markerJson());
                report.put("outcome",outcome);
                report.put("completed_utc",nowUtc());
                report.put("snapshot_after",usbSnapshot());
                report.put("status",stopRequested?"cancelled":"completed");
                report.put("interpretation",
                        "The report measures Android's accessory stream. Time-correlated changes do not establish what encrypted payloads contain or confirm Ford SYNC/ECU access.");
            }catch(Exception ignored){}
            persist();
            event("finished",100,labelFor(mode)+": "+outcome,true);
            if(cutoff!=null)handler.removeCallbacks(cutoff);
            try{if(lockPower!=null&&lockPower.isHeld())lockPower.release();}
            catch(Exception ignored){}
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    @Override public void onDestroy() {
        if(cutoff!=null)handler.removeCallbacks(cutoff);
        try{if(lockPower!=null&&lockPower.isHeld())lockPower.release();}
        catch(Exception ignored){}
        super.onDestroy();
    }
}
