package com.newswire.vehicleporttester;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
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

/**
 * Deep, opt-in USB capability audit running in a DEDICATED APP PROCESS.
 * Only Android's public accessory API is used; no payloads are sent.
 * The main Activity can kill this process if a native read blocks.
 */
public final class UsbAuditService extends Service {
    public static final String ACTION_EVENT = "com.newswire.vehicleporttester.USB_AUDIT_EVENT";
    public static final String ACTION_STOP = "com.newswire.vehicleporttester.STOP_USB_AUDIT";
    public static final String REPORT_FILE = "vehicle-usb-deep-audit.json";
    private static final String USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final int CAPTURE_LIMIT = 4096;
    private static final int ATTEMPT_MS = 12000;
    private volatile boolean cancelled;
    private volatile Thread auditThread;
    private volatile int totalRead;
    private JSONObject result;
    private final Object fileLock = new Object();
    private long auditStarted;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            cancelled = true;
            // This service runs in :usb_audit, NEVER the main UI process.
            Process.killProcess(Process.myPid());
            return START_NOT_STICKY;
        }
        if (auditThread != null && auditThread.isAlive()) return START_NOT_STICKY;
        cancelled = false;
        auditThread = new Thread(this::executeAudit, "IsolatedUsbAudit");
        auditThread.start();
        return START_NOT_STICKY;
    }

    private void event(String stage, int percent, String message, boolean finished) {
        Intent i = new Intent(ACTION_EVENT);
        i.setPackage(getPackageName());
        i.putExtra("step", stage);
        i.putExtra("progress", Math.max(0, Math.min(100, percent)));
        i.putExtra("message", message);
        i.putExtra("total_bytes", totalRead);
        i.putExtra("worker_pid", Process.myPid());
        i.putExtra("finished", finished);
        sendBroadcast(i);
    }

    private static String utc() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private JSONObject snapshot() {
        JSONObject state = new JSONObject();
        try {
            state.put("time_utc", utc());
            UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usb != null) {
                UsbAccessory[] accessories = usb.getAccessoryList();
                state.put("usb_manager_available", true);
                state.put("accessory_count", accessories == null ? 0 : accessories.length);
                state.put("usb_host_device_count", usb.getDeviceList().size());
                if (accessories != null && accessories.length > 0) {
                    state.put("accessory_manufacturer", accessories[0].getManufacturer());
                    state.put("accessory_model", accessories[0].getModel());
                    state.put("accessory_version", accessories[0].getVersion());
                    state.put("accessory_permission", usb.hasPermission(accessories[0]));
                }
            } else state.put("usb_manager_available", false);

            Intent sticky;
            IntentFilter f = new IntentFilter(USB_STATE);
            if (Build.VERSION.SDK_INT >= 33) {
                sticky = registerReceiver(null, f, Context.RECEIVER_NOT_EXPORTED);
            } else sticky = registerReceiver(null, f);
            JSONObject flags = new JSONObject();
            if (sticky != null) {
                for (String key : new String[] {"connected", "configured", "host_connected", "accessory"}) {
                    if (sticky.hasExtra(key)) flags.put(key, sticky.getBooleanExtra(key, false));
                }
            }
            state.put("usb_broadcast_available", sticky != null);
            state.put("android_usb_state", flags);
        } catch (Exception ex) {
            try { state.put("snapshot_error", ex.getClass().getSimpleName()); }
            catch (Exception ignored) {}
        }
        return state;
    }

    private void checkpoint() {
        synchronized (fileLock) {
            if (result == null) return;
            try {
                result.put("last_saved_utc", utc());
                result.put("elapsed_ms", SystemClock.elapsedRealtime() - auditStarted);
                result.put("total_bytes_read", totalRead);
                File target = new File(getFilesDir(), REPORT_FILE);
                File temp = new File(getFilesDir(), REPORT_FILE + ".tmp");
                byte[] data = result.toString(2).getBytes(StandardCharsets.UTF_8);
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    out.write(data);
                    out.getFD().sync();
                }
                if (!temp.renameTo(target)) {
                    try (FileOutputStream out = new FileOutputStream(target)) { out.write(data); }
                    temp.delete();
                }
            } catch (Exception ignored) {}
        }
    }

    private void addStep(JSONObject step) throws Exception {
        step.put("finished_utc", utc());
        result.getJSONArray("tests").put(step);
        checkpoint();
    }

    private boolean hasAccessory() {
        UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
        return usb != null && usb.getAccessoryList() != null && usb.getAccessoryList().length > 0;
    }

    private void waitWithUpdates(String phase, int sec, int percentBegin, int percentEnd) throws Exception {
        for (int s = 0; s < sec && !cancelled; s++) {
            int percent = percentBegin + (percentEnd - percentBegin) * s / Math.max(1, sec);
            event(phase, percent, phase + ": observing USB state " + (s + 1) + "/" + sec + " sec", false);
            SystemClock.sleep(1000);
        }
    }

    private JSONObject monitor(String label, int seconds, int pctStart, int pctEnd) throws Exception {
        JSONObject out = new JSONObject();
        out.put("test", label);
        out.put("started_utc", utc());
        long start = SystemClock.elapsedRealtime();
        JSONArray samples = new JSONArray();
        int changes = 0;
        String prev = null;
        for (int i = 0; i < seconds && !cancelled; i++) {
            JSONObject sample = snapshot();
            sample.put("at_ms", SystemClock.elapsedRealtime() - start);
            samples.put(sample);
            String signature = sample.optInt("accessory_count", -1) + ":"
                    + sample.optJSONObject("android_usb_state");
            if (prev != null && !prev.equals(signature)) changes++;
            prev = signature;
            event(label, pctStart + (pctEnd - pctStart) * (i + 1) / seconds,
                    label + ": sample " + (i + 1) + "/" + seconds, false);
            SystemClock.sleep(1000);
        }
        out.put("samples", samples);
        out.put("state_changes", changes);
        out.put("duration_ms", SystemClock.elapsedRealtime() - start);
        out.put("outcome", cancelled ? "cancelled" : changes == 0 ? "stable_observed" : "state_changes_detected");
        return out;
    }

    private JSONObject openClose(int attempt) throws Exception {
        JSONObject out = new JSONObject();
        out.put("test", "usb_open_close");
        out.put("attempt", attempt);
        out.put("started_utc", utc());
        out.put("before", snapshot());
        long start = SystemClock.elapsedRealtime();
        ParcelFileDescriptor fd = null;
        try {
            UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbAccessory[] aa = usb == null ? null : usb.getAccessoryList();
            if (aa == null || aa.length == 0) out.put("outcome", "no_accessory");
            else if (!usb.hasPermission(aa[0])) out.put("outcome", "permission_required");
            else {
                fd = usb.openAccessory(aa[0]);
                out.put("connection_opened", fd != null);
                out.put("outcome", fd != null ? "opened" : "open_returned_null");
            }
        } catch (Exception ex) {
            describeError(out, ex, "open_close");
        } finally {
            if (fd != null) {
                try { fd.close(); out.put("connection_closed", true); }
                catch (Exception ex) {
                    out.put("connection_closed", false);
                    out.put("close_error", ex.getClass().getSimpleName());
                }
            }
        }
        out.put("after", snapshot());
        out.put("duration_ms", SystemClock.elapsedRealtime() - start);
        return out;
    }

    private static void describeError(JSONObject out, Exception ex, String stage) {
        try {
            out.put("error_stage", stage);
            out.put("error_type", ex.getClass().getSimpleName());
            out.put("error_message", String.valueOf(ex.getMessage()));
            String code = "unknown";
            if (ex instanceof ErrnoException) {
                int errno = ((ErrnoException) ex).errno;
                out.put("error_errno", errno);
                if (errno == OsConstants.EIO) code = "usb_io_error";
                else if (errno == OsConstants.EAGAIN) code = "would_block";
                else if (errno == OsConstants.ENODEV || errno == OsConstants.EPIPE || errno == OsConstants.ENXIO)
                    code = "accessory_disconnected";
                else code = "usb_os_error";
            }
            out.put("outcome", code);
        } catch (Exception ignored) {}
    }

    private JSONObject capture(int attempt) throws Exception {
        JSONObject out = new JSONObject();
        out.put("test", "read_only_capture");
        out.put("attempt", attempt);
        out.put("started_utc", utc());
        out.put("before", snapshot());
        out.put("timeout_ms", ATTEMPT_MS);
        out.put("capture_limit_bytes", CAPTURE_LIMIT);
        out.put("bytes_written", 0);
        out.put("vehicle_commands_sent", 0);
        long start = SystemClock.elapsedRealtime();
        ParcelFileDescriptor fd = null;
        byte[] data = new byte[CAPTURE_LIMIT];
        byte[] buffer = new byte[256];
        int bytesRead = 0;
        JSONArray chunks = new JSONArray();
        String outcome = "no_accessory";
        String stage = "preflight";
        try {
            UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbAccessory[] aa = usb == null ? null : usb.getAccessoryList();
            if (aa != null && aa.length > 0) {
                if (!usb.hasPermission(aa[0])) outcome = "permission_required";
                else {
                    stage = "open_accessory";
                    fd = usb.openAccessory(aa[0]);
                    out.put("connection_opened", fd != null);
                    if (fd == null) outcome = "open_returned_null";
                    else {
                        // We explicitly verify O_NONBLOCK before any read.
                        stage = "configure_nonblocking";
                        int flags = Os.fcntlInt(fd.getFileDescriptor(), OsConstants.F_GETFL, 0);
                        Os.fcntlInt(fd.getFileDescriptor(), OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
                        int verified = Os.fcntlInt(fd.getFileDescriptor(), OsConstants.F_GETFL, 0);
                        if ((verified & OsConstants.O_NONBLOCK) == 0) {
                            outcome = "nonblocking_unsupported";
                        } else {
                            out.put("nonblocking_verified", true);
                            StructPollfd p = new StructPollfd();
                            p.fd = fd.getFileDescriptor();
                            p.events = (short) (OsConstants.POLLIN | OsConstants.POLLERR | OsConstants.POLLHUP);
                            outcome = "no_data_before_timeout";
                            long until = SystemClock.elapsedRealtime() + ATTEMPT_MS;
                            while (!cancelled && SystemClock.elapsedRealtime() < until && bytesRead < CAPTURE_LIMIT) {
                                int wait = (int) Math.min(250, Math.max(1, until - SystemClock.elapsedRealtime()));
                                stage = "poll";
                                int n = Os.poll(new StructPollfd[] {p}, wait);
                                if (n == 0) continue;
                                int flagsSeen = p.revents;
                                p.revents = 0;
                                if ((flagsSeen & OsConstants.POLLIN) != 0) {
                                    int r;
                                    try {
                                        stage = "read";
                                        r = Os.read(fd.getFileDescriptor(), buffer, 0,
                                                Math.min(buffer.length, CAPTURE_LIMIT - bytesRead));
                                    } catch (ErrnoException ex) {
                                        if (ex.errno == OsConstants.EAGAIN) continue;
                                        throw ex;
                                    }
                                    if (r <= 0) { outcome = "closed_by_peer"; break; }
                                    System.arraycopy(buffer, 0, data, bytesRead, r);
                                    bytesRead += r;
                                    totalRead += r;
                                    if (chunks.length() < 80) {
                                        JSONObject e = new JSONObject();
                                        e.put("at_ms", SystemClock.elapsedRealtime() - start);
                                        e.put("bytes", r);
                                        e.put("running_total", bytesRead);
                                        chunks.put(e);
                                    }
                                    event("read_only_capture", 24 + (attempt - 1) * 12,
                                            "Read attempt " + attempt + "/5: " + bytesRead + " bytes", false);
                                    outcome = "received_bytes";
                                } else if ((flagsSeen & (OsConstants.POLLERR | OsConstants.POLLHUP)) != 0) {
                                    outcome = "poll_error_or_disconnect";
                                    break;
                                }
                            }
                            if (cancelled) outcome = "cancelled";
                            else if (bytesRead == CAPTURE_LIMIT) outcome = "capture_limit_reached";
                            else if (SystemClock.elapsedRealtime() >= until && bytesRead > 0)
                                outcome = "partial_capture_until_timeout";
                        }
                    }
                }
            }
        } catch (Exception ex) {
            describeError(out, ex, stage);
            outcome = out.optString("outcome", "usb_error");
        } finally {
            if (fd != null) {
                try { fd.close(); out.put("connection_closed", true); }
                catch (Exception ex) { out.put("connection_closed", false); out.put("close_error", ex.toString()); }
            }
        }
        out.put("outcome", outcome);
        out.put("last_operation", stage);
        out.put("bytes_read", bytesRead);
        out.put("read_events", chunks);
        out.put("duration_ms", SystemClock.elapsedRealtime() - start);
        out.put("after", snapshot());
        StringBuilder hex = new StringBuilder(Math.min(12288, bytesRead * 3));
        final char[] digits = "0123456789ABCDEF".toCharArray();
        for (int i = 0; i < bytesRead; i++) {
            if (i != 0) hex.append(' ');
            hex.append(digits[(data[i] & 255) >>> 4]);
            hex.append(digits[data[i] & 15]);
        }
        out.put("hex_capture", hex.toString());
        out.put("raw_capture_privacy", "Incoming data may contain private application content. Do not share unless you intend to disclose it.");
        out.put("protocol_analysis", UsbProtocolAnalyzer.analyze(data, bytesRead));
        return out;
    }

    private void executeAudit() {
        auditStarted = SystemClock.elapsedRealtime();
        try {
            result = new JSONObject();
            result.put("report_format", "Vehicle Port Tester deep communication audit v1");
            result.put("app_version", "0.7.3");
            result.put("test_label", "comprehensive_usb_accessory_audit");
            result.put("started_utc", utc());
            result.put("status", "in_progress");
            result.put("worker_isolated_process", true);
            result.put("worker_pid", Process.myPid());
            result.put("read_only", true);
            result.put("bytes_written", 0);
            result.put("vehicle_commands_sent", 0);
            result.put("access_scope", "Android public USB accessory / USB state APIs only");
            result.put("firmware_or_vehicle_ecu_access_confirmed", false);
            result.put("ford_sync_internal_access_confirmed", false);
            result.put("notes", "Android USB permission does not grant Ford SYNC/ECU access. Some incoming data may be encrypted Android Auto traffic. Opening the accessory may disconnect Android Auto.");
            result.put("tests", new JSONArray());
            result.put("baseline", snapshot());
            checkpoint();
            event("preflight", 1, "Starting isolated diagnostic worker", false);
            if (!hasAccessory()) {
                result.put("status", "no_accessory_detected");
                return;
            }
            UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbAccessory[] aa = usb.getAccessoryList();
            if (aa == null || !usb.hasPermission(aa[0])) {
                result.put("status", "permission_required");
                return;
            }

            event("passive_monitor", 3, "Passive USB state observation (12 sec)", false);
            addStep(monitor("before_connection_stability", 12, 3, 17));
            if (cancelled) return;

            for (int i = 1; i <= 3 && !cancelled; i++) {
                event("open_close", 18 + i * 2, "Open/close stability " + i + "/3", false);
                addStep(openClose(i));
                SystemClock.sleep(750);
            }
            if (cancelled) return;

            // Five independent bounded attempts; each opens/closes the accessory.
            // The main process will kill this entire worker if the kernel blocks.
            for (int i = 1; i <= 5 && !cancelled; i++) {
                event("read_only_capture", 25 + (i - 1) * 12,
                        "Read-only attempt " + i + "/5 (12 sec max; 4 KB cap)", false);
                addStep(capture(i));
                SystemClock.sleep(1000);
            }
            if (cancelled) return;

            event("post_monitor", 88, "Final USB connection observation (10 sec)", false);
            addStep(monitor("after_connection_stability", 10, 88, 99));
            result.put("status", cancelled ? "cancelled" : "completed");
        } catch (Exception ex) {
            try {
                result.put("status", "worker_error");
                result.put("worker_error_type", ex.getClass().getSimpleName());
                result.put("worker_error_message", String.valueOf(ex.getMessage()));
            } catch (Exception ignored) {}
        } finally {
            try {
                result.put("final_usb_state", snapshot());
                result.put("completed_utc", utc());
                result.put("total_bytes_read", totalRead);
                result.put("vehicle_diagnostic_data_confirmed", false);
                result.put("interpretation", totalRead > 0
                        ? "Raw USB accessory bytes were captured; they are not confirmed Ford diagnostics or decrypted Android Auto messages."
                        : "No USB payload bytes were captured. Open/close success alone does not establish access to Ford SYNC internals.");
                if (cancelled) result.put("status", "cancelled");
            } catch (Exception ignored) {}
            checkpoint();
            event("finished", 100, "Deep audit: " + result.optString("status", "complete")
                    + ", " + totalRead + " bytes read", true);
            stopSelf();
        }
    }
}
