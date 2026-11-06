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
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Read-only screen-to-screen USB traffic comparison. User labels the Ford screen
 * before starting each capture; Android cannot independently see that screen. */
public final class UsbScreenCompareService extends Service {
    public static final String ACTION_UPDATE = "com.newswire.vehicleporttester.SCREEN_CAPTURE_UPDATE";
    public static final String ACTION_STOP = "com.newswire.vehicleporttester.SCREEN_CAPTURE_STOP";
    public static final String EXTRA_MODE = "screen_mode";
    public static final String MODE_SYNC = "ford_sync_home";
    public static final String MODE_AUTO = "android_auto_display";
    public static final String SYNC_REPORT = "screen_compare_sync.json";
    public static final String AUTO_REPORT = "screen_compare_android_auto.json";
    private static final int DURATION_MS = 20000;
    private static final int STORE_LIMIT = 8192;
    private static final int NOTIFICATION_ID = 7515;
    private static final String CHANNEL = "vehicle_usb_screen_compare";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private volatile Thread worker;
    private PowerManager.WakeLock wakeLock;
    private Runnable cutoff;
    private JSONObject report;
    private String mode;
    private int totalBytes;
    private long started;
    private final Object fileLock = new Object();

    @Override public IBinder onBind(Intent intent) { return null; }

    public static String filename(String selected) {
        return MODE_AUTO.equals(selected) ? AUTO_REPORT : SYNC_REPORT;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            cancelled = true;
            Process.killProcess(Process.myPid());
            return START_NOT_STICKY;
        }
        if (worker != null && worker.isAlive()) return START_NOT_STICKY;
        mode = intent == null ? "" : intent.getStringExtra(EXTRA_MODE);
        if (!MODE_SYNC.equals(mode) && !MODE_AUTO.equals(mode)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        cancelled = false;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
                new NotificationChannel(CHANNEL,"USB screen comparison",NotificationManager.IMPORTANCE_LOW));
            Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Vehicle Port Tester: screen comparison")
                .setContentText("Capturing " + (MODE_SYNC.equals(mode) ? "SYNC home" : "Android Auto") + " USB traffic")
                .setOngoing(true).build();
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
            else startForeground(NOTIFICATION_ID, n);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VehiclePortTester:ScreenCompare");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(70000);
            }
            cutoff = () -> {
                if (worker != null && worker.isAlive()) {
                    mark("worker_hard_timeout", "hard_timeout_65_seconds");
                    Process.killProcess(Process.myPid());
                }
            };
            handler.postDelayed(cutoff, 65000);
        } catch (Exception ex) {
            event("error", 0, "Unable to start foreground test: " + ex.getClass().getSimpleName(), true);
            stopSelf();
            return START_NOT_STICKY;
        }
        worker = new Thread(this::perform, "ScreenUSBCompare");
        worker.start();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        release();
        super.onDestroy();
    }

    private void release() {
        if (cutoff != null) handler.removeCallbacks(cutoff);
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
        catch (Exception ignored) {}
    }

    private static String utc() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }

    private JSONObject snapshot() {
        JSONObject o = new JSONObject();
        try {
            o.put("utc", utc());
            UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbAccessory[] aa = usb == null ? null : usb.getAccessoryList();
            o.put("usb_manager_available", usb != null);
            o.put("accessory_count", aa == null ? 0 : aa.length);
            o.put("usb_host_device_count", usb == null ? 0 : usb.getDeviceList().size());
            if (aa != null && aa.length > 0) {
                o.put("accessory_manufacturer", aa[0].getManufacturer());
                o.put("accessory_model", aa[0].getModel());
                o.put("accessory_permission", usb.hasPermission(aa[0]));
            }
            Intent sticky;
            IntentFilter f = new IntentFilter("android.hardware.usb.action.USB_STATE");
            if (Build.VERSION.SDK_INT >= 33) sticky = registerReceiver(null, f, Context.RECEIVER_NOT_EXPORTED);
            else sticky = registerReceiver(null, f);
            JSONObject flags = new JSONObject();
            if (sticky != null) for (String key : new String[]{"connected","configured","host_connected","accessory"}) {
                if (sticky.hasExtra(key)) flags.put(key, sticky.getBooleanExtra(key,false));
            }
            o.put("usb_state", flags);
        } catch (Exception ex) {
            try { o.put("snapshot_error", ex.getClass().getSimpleName()); } catch (Exception ignored) {}
        }
        return o;
    }

    private void event(String stage, int percent, String message, boolean done) {
        Intent i = new Intent(ACTION_UPDATE);
        i.setPackage(getPackageName());
        i.putExtra("mode", mode);
        i.putExtra("stage", stage);
        i.putExtra("percent", Math.max(0,Math.min(100,percent)));
        i.putExtra("message", message);
        i.putExtra("bytes", totalBytes);
        i.putExtra("worker_pid", Process.myPid());
        i.putExtra("done", done);
        sendBroadcast(i);
    }

    private void checkpoint() {
        synchronized (fileLock) {
            if (report == null) return;
            try {
                report.put("last_saved_utc", utc());
                report.put("elapsed_ms", SystemClock.elapsedRealtime() - started);
                report.put("bytes_received", totalBytes);
                File file = new File(getFilesDir(), filename(mode));
                File tmp = new File(getFilesDir(), filename(mode) + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                if (!tmp.renameTo(file)) {
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                    tmp.delete();
                }
            } catch (Exception ignored) {}
        }
    }

    private void mark(String stage, String state) {
        try {
            report.put("current_operation", stage);
            report.put("status", state);
            report.put("operation_entered_utc", utc());
            checkpoint();
        } catch (Exception ignored) {}
    }

    private void perform() {
        started = SystemClock.elapsedRealtime();
        ParcelFileDescriptor fd = null;
        byte[] captured = new byte[STORE_LIMIT];
        int stored = 0;
        JSONArray eventLog = new JSONArray();
        String outcome = "unknown";
        String stage = "preflight";
        int events = 0;
        long firstByteMs = -1;
        long lastByteMs = -1;
        try {
            report = new JSONObject();
            report.put("report_format", "Vehicle USB screen traffic comparison v1");
            report.put("app_version", "0.7.5");
            report.put("test_label", "screen_labeled_usb_traffic_capture");
            report.put("user_selected_screen", mode);
            report.put("user_screen_label_unverified", true);
            report.put("started_utc", utc());
            report.put("status", "in_progress");
            report.put("read_only", true);
            report.put("vehicle_commands_sent", 0);
            report.put("bytes_written", 0);
            report.put("target_duration_ms", DURATION_MS);
            report.put("max_stored_bytes", STORE_LIMIT);
            report.put("android_auto_may_be_interrupted", true);
            report.put("payload_privacy_note", "Raw incoming bytes may contain private or encrypted application traffic.");
            report.put("ford_sync_internal_access_confirmed", false);
            report.put("vehicle_diagnostic_data_confirmed", false);
            report.put("before", snapshot());
            mark("preflight","in_progress");
            event("preflight", 5, "USB comparison: checking selected screen's connection...", false);
            UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbAccessory[] aa = usb == null ? null : usb.getAccessoryList();
            if (aa == null || aa.length == 0) outcome = "no_accessory";
            else if (!usb.hasPermission(aa[0])) outcome = "permission_required";
            else {
                stage = "open_accessory"; mark(stage, "in_progress");
                fd = usb.openAccessory(aa[0]);
                report.put("connection_opened", fd != null);
                if (fd == null) outcome = "open_returned_null";
                else {
                    stage = "configure_nonblocking"; mark(stage, "in_progress");
                    int flags = Os.fcntlInt(fd.getFileDescriptor(), OsConstants.F_GETFL, 0);
                    Os.fcntlInt(fd.getFileDescriptor(), OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
                    int confirmed = Os.fcntlInt(fd.getFileDescriptor(), OsConstants.F_GETFL, 0);
                    report.put("nonblocking_verified", (confirmed & OsConstants.O_NONBLOCK) != 0);
                    if ((confirmed & OsConstants.O_NONBLOCK) == 0) outcome = "nonblocking_unsupported";
                    else {
                        StructPollfd p = new StructPollfd();
                        p.fd = fd.getFileDescriptor();
                        p.events = (short)(OsConstants.POLLIN | OsConstants.POLLERR | OsConstants.POLLHUP);
                        byte[] buf = new byte[512];
                        long startCapture = SystemClock.elapsedRealtime();
                        long nextUpdate = startCapture + 2000;
                        long deadline = startCapture + DURATION_MS;
                        outcome = "capture_timed_out_no_bytes";
                        String lastMarked = "";
                        while (!cancelled && SystemClock.elapsedRealtime() < deadline) {
                            int wait = (int)Math.min(250, Math.max(1,deadline-SystemClock.elapsedRealtime()));
                            stage = "poll";
                            if (!lastMarked.equals(stage)) { mark(stage,"in_progress"); lastMarked = stage; }
                            int n = Os.poll(new StructPollfd[]{p},wait);
                            if (n > 0) {
                                int revents = p.revents;
                                p.revents = 0;
                                if ((revents & OsConstants.POLLIN) != 0) {
                                    stage = "read";
                                    if (!lastMarked.equals(stage)) { mark(stage,"in_progress"); lastMarked=stage; }
                                    int size;
                                    try { size=Os.read(fd.getFileDescriptor(),buf,0,buf.length); }
                                    catch (ErrnoException ex) {
                                        if (ex.errno == OsConstants.EAGAIN) continue;
                                        throw ex;
                                    }
                                    if (size <= 0) { outcome = "closed_by_peer"; break; }
                                    long moment = SystemClock.elapsedRealtime() - startCapture;
                                    if (firstByteMs < 0) firstByteMs = moment;
                                    lastByteMs = moment;
                                    totalBytes += size;
                                    int copy = Math.min(size, STORE_LIMIT-stored);
                                    if (copy > 0) { System.arraycopy(buf,0,captured,stored,copy); stored += copy; }
                                    if (eventLog.length() < 250) {
                                        JSONObject e = new JSONObject();
                                        e.put("ms",moment);e.put("bytes",size);e.put("cumulative_bytes",totalBytes);
                                        eventLog.put(e);
                                    }
                                    events++;
                                    outcome = "capture_received_bytes";
                                } else if ((revents & (OsConstants.POLLERR|OsConstants.POLLHUP)) != 0) {
                                    outcome = "poll_disconnect_or_error";
                                    break;
                                }
                            }
                            long now = SystemClock.elapsedRealtime();
                            if (now >= nextUpdate) {
                                report.put("last_capture_progress_ms", now-startCapture);
                                report.put("stored_bytes_so_far", stored);
                                checkpoint();
                                event("capture", 10+(int)Math.min(80,80*(now-startCapture)/DURATION_MS),
                                    "Reading USB traffic (" + ((now-startCapture)/1000) + "/20 sec, "+totalBytes+" bytes)",false);
                                nextUpdate=now+2000;
                            }
                        }
                        if (cancelled) outcome = "cancelled";
                        else if ("capture_received_bytes".equals(outcome) && SystemClock.elapsedRealtime() >= deadline)
                            outcome = "captured_until_timeout";
                        report.put("actual_capture_ms",SystemClock.elapsedRealtime()-startCapture);
                    }
                }
            }
        } catch (Exception ex) {
            outcome = "capture_error";
            if (ex instanceof ErrnoException) {
                int err = ((ErrnoException)ex).errno;
                if (err == OsConstants.EIO) outcome = "usb_io_error";
                else if (err == OsConstants.ENODEV || err == OsConstants.EPIPE || err == OsConstants.ENXIO)
                    outcome = "accessory_disconnected";
                try { report.put("error_errno",err); } catch (Exception ignored) {}
            }
            try {
                report.put("error_at_operation",stage);
                report.put("error_type",ex.getClass().getSimpleName());
                report.put("error_message",String.valueOf(ex.getMessage()));
            } catch (Exception ignored) {}
        } finally {
            try {
                if (fd != null) {
                    fd.close();
                    report.put("connection_closed",true);
                }
            } catch (Exception ex) {
                try { report.put("connection_closed",false); report.put("close_error",ex.toString()); }
                catch (Exception ignored) {}
            }
            try {
                report.put("outcome", outcome);
                report.put("read_events_recorded",events);
                report.put("stored_bytes",stored);
                report.put("first_byte_ms",firstByteMs < 0 ? JSONObject.NULL : firstByteMs);
                report.put("last_byte_ms",lastByteMs < 0 ? JSONObject.NULL : lastByteMs);
                report.put("read_events",eventLog);
                report.put("after",snapshot());
                StringBuilder raw = new StringBuilder(stored*3);
                char[] hex="0123456789ABCDEF".toCharArray();
                for(int x=0;x<stored;x++){
                    if(x>0)raw.append(' ');
                    int b=captured[x]&255;
                    raw.append(hex[b >>> 4]).append(hex[b & 15]);
                }
                report.put("hex_capture",raw.toString());
                report.put("protocol_analysis",UsbProtocolAnalyzer.analyze(captured,stored));
                report.put("status", cancelled ? "cancelled" : "completed");
                report.put("completed_utc",utc());
                report.put("interpretation","User-labelled Android USB accessory capture. Headers, timing, and sizes can be compared; encrypted traffic does not identify Ford SYNC internals.");
            } catch (Exception ignored) {}
            checkpoint();
            event("finished",100,"Screen traffic capture: "+outcome+" ("+totalBytes+" bytes)",true);
            release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
}
