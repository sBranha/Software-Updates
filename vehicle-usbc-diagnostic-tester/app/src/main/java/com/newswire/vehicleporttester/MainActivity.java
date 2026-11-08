package com.newswire.vehicleporttester;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.ErrnoException;
import java.util.concurrent.atomic.AtomicBoolean;
import android.system.StructPollfd;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.BatteryManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final String USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final int EXPORT_JSON = 9001;
    private static final int REQ_SCAN_BLUETOOTH = 8805;
    private TextView summary;
    private TextView details;
    private TextView footer;
    private TextView scanStatus;
    private ProgressBar scanProgress;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean scanActive = false;
    private volatile boolean connectionActive = false;
    private volatile boolean listenCancelRequested = false;
    private volatile boolean listenReaderStalled = false;
    private volatile ParcelFileDescriptor listenDescriptor = null;
    private volatile int listenBytesObserved = 0;
    private volatile boolean listenOpened = false;
    private volatile long listenStartAt = 0;
    private final AtomicBoolean listenCompleted = new AtomicBoolean(true);
    private Button stopListenButton;
    private Runnable listenWatchdog;
    private volatile boolean auditRunning = false;
    private volatile int auditWorkerPid = 0;
    private volatile long auditStartedAt = 0L;
    private Button stopAuditButton;
    private Runnable auditWatchdog;
    private boolean auditReceiverRegistered;
    private final BroadcastReceiver auditUpdates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!auditRunning || !UsbAuditService.ACTION_EVENT.equals(intent.getAction())) return;
            int pid = intent.getIntExtra("worker_pid", 0);
            if (pid > 0 && pid != android.os.Process.myPid()) auditWorkerPid = pid;
            int progress = intent.getIntExtra("progress", 0);
            String stage = intent.getStringExtra("step");
            String message = intent.getStringExtra("message");
            scanProgress.setIndeterminate(false);
            scanProgress.setProgress(progress);
            scanStatus.setText(message == null ? "Running USB audit..." : message);
            if (intent.getBooleanExtra("finished", false)) {
                finishDeepAudit(null);
            } else {
                summary.setText("Deep USB audit: " + (stage == null ? "running" : stage));
                details.setText("Stage: " + stage + "\nTotal bytes observed: "
                    + intent.getIntExtra("total_bytes", 0)
                    + "\nWorker process: " + auditWorkerPid
                    + "\nThe app sends no vehicle commands.");
            }
        }
    };
    private volatile boolean screenCaptureRunning;
    private volatile int screenWorkerPid;
    private String screenCaptureMode = "";
    private long screenStartedAt;
    private Runnable screenWatchdog;
    private Button stopScreenCapture;
    private boolean screenReceiverRegistered;
    private final BroadcastReceiver screenUpdates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!UsbScreenCompareService.ACTION_UPDATE.equals(intent.getAction())
                    || !screenCaptureRunning) return;
            if (!screenCaptureMode.equals(intent.getStringExtra("mode"))) return;
            int pid = intent.getIntExtra("worker_pid", 0);
            if (pid > 0 && pid != android.os.Process.myPid()) screenWorkerPid = pid;
            scanProgress.setIndeterminate(false);
            scanProgress.setProgress(intent.getIntExtra("percent", 0));
            String status = intent.getStringExtra("message");
            scanStatus.setText(status == null ? "Comparing USB traffic..." : status);
            if (intent.getBooleanExtra("done", false)) {
                finishScreenCapture(null);
            } else {
                summary.setText("Capturing " + screenLabel(screenCaptureMode));
                details.setText("Live received: " + intent.getIntExtra("bytes", 0)
                    + " bytes\nWorker process: " + screenWorkerPid
                    + "\nNo vehicle commands are sent.");
            }
        }
    };
    private volatile boolean experimentRunning = false;
    private String experimentMode = "";
    private volatile int experimentWorkerPid = 0;
    private long experimentStartedAt = 0;
    private Runnable experimentWatchdog;
    private boolean experimentReceiverRegistered;
    private Button experimentStopButton;
    private Button experimentMarkMaps;
    private Button experimentMarkMusic;
    private Button experimentMarkScreen;
    private Button experimentMarkOther;
    private final BroadcastReceiver experimentUpdates = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!experimentRunning ||
                !UsbTrafficExperimentService.ACTION_EVENT.equals(intent.getAction())) return;
            if (!experimentMode.equals(intent.getStringExtra("mode"))) return;
            int pid=intent.getIntExtra("pid",0);
            if(pid>0 && pid!=android.os.Process.myPid())experimentWorkerPid=pid;
            scanProgress.setIndeterminate(false);
            scanProgress.setProgress(intent.getIntExtra("progress",0));
            String msg=intent.getStringExtra("message");
            if(msg!=null)scanStatus.setText(msg);
            if(intent.getBooleanExtra("done",false)) {
                finishExperiment(null);
            } else {
                summary.setText(UsbTrafficExperimentService.labelFor(experimentMode));
                details.setText("Operation: "+intent.getStringExtra("stage")
                    +"\nUSB data capture is read-only; no vehicle commands.");
            }
        }
    };
    private boolean usbAutoPermissionReceiverRegistered=false;
    private final BroadcastReceiver usbAutoPermissionResult=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(!UsbPermissionReceiver.RESULT.equals(i.getAction()))return;
            if(!getPreferences(MODE_PRIVATE).getBoolean("v081_scan_waiting_permissions",false))return;
            scanStatus.setText(i.getBooleanExtra("granted",false)
                ?"USB permission granted. Continuing scan setup..."
                :"USB permission declined. Continuing limited discovery with this step unavailable.");
            continueFullScanPermissionSetup();
        }
    };
    private volatile boolean fullAutoRunning = false;
    private volatile int fullAutoPid = 0;
    private long fullAutoStarted = 0;
    private boolean fullAutoReceiverRegistered = false;
    private Runnable fullAutoWatchdog;
    private Button stopFullAutoButton;
    private static final String FULL_REPORT = OneClickAuditService.FILE;
    private final BroadcastReceiver fullAutoEvents = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            if(!OneClickAuditService.EVENT.equals(i.getAction())||!fullAutoRunning)return;
            int pid=i.getIntExtra("worker_pid",0);
            if(pid>0&&pid!=android.os.Process.myPid())fullAutoPid=pid;
            scanProgress.setIndeterminate(false);
            scanProgress.setProgress(i.getIntExtra("percent",0));
            String message=i.getStringExtra("text");
            scanStatus.setText(message==null?"Automatic audit running":message);
            summary.setText("ONE-CLICK FULL USB AUDIT");
            details.setText("Stage: "+i.getStringExtra("stage")
                +"\nNo vehicle commands sent. Report automatically saves to Downloads.");
            if(i.getBooleanExtra("done",false))showCompletedFullAuto(null);
        }
    };
    private String lastReport = "";
    private String latestFilename = "";
    private final BroadcastReceiver usbChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (USB_STATE.equals(intent.getAction()) && !scanActive && !connectionActive) {
                scanStatus.setText("USB state changed. Tap DEEP USB-C DISCOVERY for a new full scan. The last report has been preserved.");
            }
        }
    };
    private boolean receiverRegistered;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildLayout();
        scanStatus.setText("Ready. Park the vehicle, connect USB and tap ONE BUTTON to record everything.");
    }

    @Override public void onStart() {
        super.onStart();
        try{
            IntentFilter permissionFilter=new IntentFilter(UsbPermissionReceiver.RESULT);
            if(Build.VERSION.SDK_INT>=33)
                registerReceiver(usbAutoPermissionResult,permissionFilter,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(usbAutoPermissionResult,permissionFilter);
            usbAutoPermissionReceiverRegistered=true;
        }catch(Exception ignored){}
        if(getPreferences(MODE_PRIVATE).getBoolean("v081_scan_waiting_permissions",false))
            uiHandler.post(this::continueFullScanPermissionSetup);
        try {
            IntentFilter auditFilter = new IntentFilter(UsbAuditService.ACTION_EVENT);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(auditUpdates, auditFilter, Context.RECEIVER_NOT_EXPORTED);
            } else registerReceiver(auditUpdates, auditFilter);
            auditReceiverRegistered = true;
        } catch (Exception ex) {
            if (scanStatus != null) scanStatus.setText("Audit receiver unavailable: " + ex.getMessage());
        }
        try {
            IntentFilter filter = new IntentFilter(USB_STATE);
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(usbChanged, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(usbChanged, filter);
            receiverRegistered = true;
        } catch (Exception ex) {
            footer.setText("USB change notifications unavailable: tap Scan Connection after plugging in.");
        }
        resumeDeepAuditIfNeeded();
        try {
            IntentFilter screenFilter = new IntentFilter(UsbScreenCompareService.ACTION_UPDATE);
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(screenUpdates, screenFilter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenUpdates, screenFilter);
            screenReceiverRegistered = true;
        } catch (Exception ex) {
            scanStatus.setText("Screen comparison updates unavailable: "
                + ex.getClass().getSimpleName());
        }
        resumeScreenCaptureIfNeeded();
        try {
            IntentFilter f=new IntentFilter(UsbTrafficExperimentService.ACTION_EVENT);
            if(Build.VERSION.SDK_INT>=33)
                registerReceiver(experimentUpdates,f,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(experimentUpdates,f);
            experimentReceiverRegistered=true;
        } catch(Exception ex) {
            scanStatus.setText("Experiment receiver unavailable: "+
                ex.getClass().getSimpleName());
        }
        resumeExperimentIfNeeded();
        try {
            IntentFilter f=new IntentFilter(OneClickAuditService.EVENT);
            if(Build.VERSION.SDK_INT>=33)
                registerReceiver(fullAutoEvents,f,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(fullAutoEvents,f);
            fullAutoReceiverRegistered=true;
        }catch(Exception e){
            scanStatus.setText("Full audit updates unavailable: "+e.getClass().getSimpleName());
        }
        resumeFullAutoIfNeeded();
    }

    @Override public void onStop() {
        // Do not cancel a two-minute audit merely because Android temporarily
        // backgrounds this activity, locks the screen, or shows a system dialog.
        // The audit continues in a foreground service in a separate process.
        if(usbAutoPermissionReceiverRegistered) {
            try{unregisterReceiver(usbAutoPermissionResult);}catch(Exception ignored){}
            usbAutoPermissionReceiverRegistered=false;
        }
        if(fullAutoReceiverRegistered) {
            try{unregisterReceiver(fullAutoEvents);}catch(Exception ignored){}
            fullAutoReceiverRegistered=false;
        }
        if(experimentReceiverRegistered) {
            try{unregisterReceiver(experimentUpdates);}catch(Exception ignored){}
            experimentReceiverRegistered=false;
        }
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenUpdates); } catch (Exception ignored) {}
            screenReceiverRegistered = false;
        }
        if (auditReceiverRegistered) {
            try { unregisterReceiver(auditUpdates); } catch (Exception ignored) {}
            auditReceiverRegistered = false;
        }
        if (receiverRegistered) {
            try { unregisterReceiver(usbChanged); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(11, 22, 38));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(30), dp(22), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView eyebrow = text("USB CAPABILITY TEST • NO VEHICLE COMMANDS", 12, 0xff84d9cc, true);
        root.addView(eyebrow);
        TextView heading = text("Vehicle Capability Explorer\nv0.8.1", 28, Color.WHITE, true);
        LinearLayout.LayoutParams h = new LinearLayout.LayoutParams(-1, -2);
        h.topMargin = dp(12);
        root.addView(heading, h);
        TextView intro = text("Explore what your phone can actually access in the Explorer: screen apps, USB communication, vehicle information, and control possibilities — with evidence for every result.", 15, 0xffb8c9d8, false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(12);
        ip.bottomMargin = dp(20);
        root.addView(intro, ip);

        TextView instructions = text("Park the vehicle, turn on ignition/accessory power, connect your phone to the Explorer, then tap SCAN EVERYTHING.", 14, 0xff84d9cc, false);
        root.addView(instructions);
        scanStatus = text("Ready to scan", 14, 0xff84d9cc, true);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2);
        stp.topMargin = dp(14);
        root.addView(scanStatus, stp);
        scanProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        scanProgress.setMax(100);
        scanProgress.setProgress(0);
        scanProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams prog = new LinearLayout.LayoutParams(-1, dp(10));
        prog.topMargin = dp(8);
        prog.bottomMargin = dp(12);
        root.addView(scanProgress, prog);
        Button oneClick=button("SCAN EVERYTHING — ONE CLICK",0xff119a85);
        LinearLayout.LayoutParams oneLp=new LinearLayout.LayoutParams(-1,dp(72));
        oneLp.topMargin=dp(10);
        oneLp.bottomMargin=dp(8);
        root.addView(oneClick,oneLp);
        oneClick.setTextSize(17);
        oneClick.setOnClickListener(v->beginFullScanWithPermissions());
        TextView oneInfo=text("One tap scans all accessible Android USB and car-app capabilities, tests connection stability and live traffic, and checks whether a paired Bluetooth OBD adapter can be identified. Results are organized into confirmed, possible, and unavailable functions. About 80 seconds. PARKED ONLY: Android Auto may disconnect; no vehicle commands will be sent.",13,0xffffcc91,false);
        root.addView(oneInfo);
        stopFullAutoButton=button("STOP SCAN",0xffb64236);
        root.addView(stopFullAutoButton);
        stopFullAutoButton.setVisibility(View.GONE);
        stopFullAutoButton.setOnClickListener(v->stopFullAuto());
        Button showFull=button("VIEW VEHICLE CAPABILITY RESULTS",0xff29435a);
        root.addView(showFull);
        showFull.setOnClickListener(v->loadLastFullAuto());
        Button advancedToggle=button("ADVANCED USB DIAGNOSTICS  ▾",0xff263C54);
        LinearLayout.LayoutParams advancedParams=new LinearLayout.LayoutParams(-1,-2);
        advancedParams.topMargin=dp(12);
        root.addView(advancedToggle,advancedParams);
        LinearLayout advancedArea=new LinearLayout(this);
        advancedArea.setOrientation(LinearLayout.VERTICAL);
        advancedArea.setVisibility(View.GONE);
        root.addView(advancedArea);
        advancedToggle.setOnClickListener(v->{
            boolean expanded=advancedArea.getVisibility()!=View.VISIBLE;
            advancedArea.setVisibility(expanded?View.VISIBLE:View.GONE);
            advancedToggle.setText(expanded?"HIDE ADVANCED DIAGNOSTICS  ▴":"ADVANCED USB DIAGNOSTICS  ▾");
        });
        Button unplugged = button("1  SCAN UNPLUGGED", 0xff29435a);
        advancedArea.addView(unplugged);
        unplugged.setOnClickListener(v -> runScan("Test: disconnected", true));
        Button usbC = button("2  SCAN USB-C PORT", 0xff247ec6);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(10);
        advancedArea.addView(usbC, cp);
        usbC.setOnClickListener(v -> runScan("Test: usb_c", true));
        Button usbA = button("3  SCAN REGULAR USB PORT", 0xff247ec6);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = dp(10);
        advancedArea.addView(usbA, ap);
        usbA.setOnClickListener(v -> runScan("Test: usb_a", true));
        Button deep = button("4  DEEP USB-C DISCOVERY", 0xffb64236);
        LinearLayout.LayoutParams deepLp = new LinearLayout.LayoutParams(-1, -2);
        deepLp.topMargin = dp(12);
        advancedArea.addView(deep, deepLp);
        deep.setOnClickListener(v -> runScan("Test: usb_c_deep", true));
        Button exportDeep = button("EXPORT SAVED DEEP USB-C REPORT", 0xff29435a);
        LinearLayout.LayoutParams exportDeepLp = new LinearLayout.LayoutParams(-1, -2);
        exportDeepLp.topMargin = dp(8);
        advancedArea.addView(exportDeep, exportDeepLp);
        exportDeep.setOnClickListener(v -> exportSavedDeepReport());
        Button access = button("REQUEST USB ACCESS PERMISSION", 0xff776225);
        LinearLayout.LayoutParams accessLp = new LinearLayout.LayoutParams(-1, -2);
        accessLp.topMargin = dp(10);
        advancedArea.addView(access, accessLp);
        access.setOnClickListener(v -> requestUsbAccess());
        Button test = button("5  TEST USB OPEN / CLOSE", 0xff876338);
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1,-2);
        testLp.topMargin = dp(12);
        advancedArea.addView(test,testLp);
        test.setOnClickListener(v -> confirmConnectionTest());
        TextView testWarning = text("PARKED ONLY. Android Auto may disconnect. No USB payloads or vehicle commands sent.",12,0xffffcc91,false);
        advancedArea.addView(testWarning);
        Button exportTest = button("EXPORT SAVED USB CONNECTION TEST",0xff29435a);
        LinearLayout.LayoutParams testExportLp = new LinearLayout.LayoutParams(-1,-2);
        testExportLp.topMargin=dp(8);
        advancedArea.addView(exportTest,testExportLp);
        exportTest.setOnClickListener(v -> exportSavedConnectionTest());
        Button listen = button("6  ANALYZE USB STREAM (10 SEC / 4 KB)", 0xff52678a);
        LinearLayout.LayoutParams listenLp = new LinearLayout.LayoutParams(-1, -2);
        listenLp.topMargin = dp(12);
        advancedArea.addView(listen, listenLp);
        listen.setOnClickListener(v -> confirmListenTest());
        stopListenButton = button("STOP USB STREAM TEST", 0xffb64236);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-1, -2);
        stopLp.topMargin = dp(8);
        advancedArea.addView(stopListenButton, stopLp);
        stopListenButton.setVisibility(View.GONE);
        stopListenButton.setOnClickListener(v -> stopListenTest());
        TextView listenWarning = text("PARKED ONLY. Opt-in read-only capture, up to 4,096 bytes or 10 seconds. Reports frame headers, lengths, and timing. No messages are sent. Android Auto may disconnect; exported raw data may be private.", 12, 0xffffcc91, false);
        advancedArea.addView(listenWarning);
        Button exportListen = button("EXPORT SAVED USB PROTOCOL REPORT", 0xff29435a);
        LinearLayout.LayoutParams listenExportLp = new LinearLayout.LayoutParams(-1, -2);
        listenExportLp.topMargin = dp(8);
        advancedArea.addView(exportListen, listenExportLp);
        exportListen.setOnClickListener(v -> exportSavedListenTest());

        Button deepAudit = button("7  DEEP COMMUNICATION AUDIT (~2 MIN)", 0xffb64236);
        LinearLayout.LayoutParams auditLp = new LinearLayout.LayoutParams(-1, -2);
        auditLp.topMargin = dp(18);
        advancedArea.addView(deepAudit, auditLp);
        deepAudit.setOnClickListener(v -> confirmDeepAudit());
        TextView auditWarning = text("PARKED ONLY. Screen stays awake and the USB audit continues if Android temporarily backgrounds the app. STOP remains available; a safety timeout still applies. Can interrupt Android Auto. Raw bytes may be private.", 12, 0xffffcc91, false);
        advancedArea.addView(auditWarning);
        stopAuditButton = button("STOP DEEP AUDIT", 0xffb64236);
        advancedArea.addView(stopAuditButton);
        stopAuditButton.setVisibility(View.GONE);
        stopAuditButton.setOnClickListener(v -> stopDeepAudit("cancelled_by_user"));
        Button auditExport = button("EXPORT SAVED DEEP AUDIT REPORT", 0xff29435a);
        advancedArea.addView(auditExport);
        auditExport.setOnClickListener(v -> exportDeepAudit());


        TextView compareHead = text("USB SCREEN TRAFFIC COMPARISON (v0.8.1)", 16, Color.WHITE, true);
        LinearLayout.LayoutParams compareHeading = new LinearLayout.LayoutParams(-1, -2);
        compareHeading.topMargin = dp(20);
        advancedArea.addView(compareHead, compareHeading);
        TextView compareHelp = text("While PARKED, first show the regular Ford SYNC home screen and capture it. Next switch the vehicle display to Android Auto and capture again. We compare only the Android accessory traffic; the app cannot independently confirm what's on the Ford screen. Capturing can interrupt Android Auto.", 12, 0xffffcc91, false);
        advancedArea.addView(compareHelp);
        Button syncCapture = button("8  CAPTURE FORD SYNC HOME SCREEN (20 SEC)", 0xff247ec6);
        advancedArea.addView(syncCapture);
        syncCapture.setOnClickListener(v -> confirmScreenCapture(UsbScreenCompareService.MODE_SYNC));
        Button autoCapture = button("9  CAPTURE ANDROID AUTO SCREEN (20 SEC)", 0xff247ec6);
        advancedArea.addView(autoCapture);
        autoCapture.setOnClickListener(v -> confirmScreenCapture(UsbScreenCompareService.MODE_AUTO));
        stopScreenCapture = button("STOP SCREEN TRAFFIC CAPTURE", 0xffb64236);
        advancedArea.addView(stopScreenCapture);
        stopScreenCapture.setVisibility(View.GONE);
        stopScreenCapture.setOnClickListener(v -> stopScreenCapture("stopped_by_user"));
        Button exportCompare = button("10  COMPARE AND EXPORT BOTH SCREEN TESTS", 0xff23736b);
        advancedArea.addView(exportCompare);
        exportCompare.setOnClickListener(v -> showScreenTrafficComparison());


        TextView experimentHeading=text("v0.8.1  CONTROLLED TRAFFIC EXPERIMENT",17,Color.WHITE,true);
        LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(-1,-2);
        elp.topMargin=dp(20);
        advancedArea.addView(experimentHeading,elp);
        TextView experimentIntro=text("PARKED ONLY. First 5 seconds are excluded from steady-state measurements. Three separate 10-second windows are compared. Use the action test to mark when you open Maps, change music, or switch screens. These labels are user-reported; the app cannot see what SYNC displays. Capturing can interrupt Android Auto.",12,0xffffcc91,false);
        advancedArea.addView(experimentIntro);
        Button steadySync=button("11  MEASURE SYNC STEADY TRAFFIC (40 SEC)",0xff23736b);
        advancedArea.addView(steadySync);
        steadySync.setOnClickListener(v->confirmExperiment(UsbTrafficExperimentService.SYNC));
        Button steadyAuto=button("12  MEASURE ANDROID AUTO STEADY TRAFFIC (40 SEC)",0xff23736b);
        advancedArea.addView(steadyAuto);
        steadyAuto.setOnClickListener(v->confirmExperiment(UsbTrafficExperimentService.AUTO));
        Button actions=button("13  RECORD ACTION MARKERS (65 SEC)",0xffb64236);
        advancedArea.addView(actions);
        actions.setOnClickListener(v->confirmExperiment(UsbTrafficExperimentService.ACTIONS));
        experimentMarkMaps=button("MARK: OPEN MAPS",0xff52678a);
        advancedArea.addView(experimentMarkMaps);
        experimentMarkMaps.setOnClickListener(v->sendActionMarker("Open Maps"));
        experimentMarkMusic=button("MARK: CHANGE MUSIC APP",0xff52678a);
        advancedArea.addView(experimentMarkMusic);
        experimentMarkMusic.setOnClickListener(v->sendActionMarker("Change music app"));
        experimentMarkScreen=button("MARK: SWITCH VEHICLE SCREEN",0xff52678a);
        advancedArea.addView(experimentMarkScreen);
        experimentMarkScreen.setOnClickListener(v->sendActionMarker("Switch vehicle display"));
        experimentMarkOther=button("MARK: OTHER ACTION",0xff52678a);
        advancedArea.addView(experimentMarkOther);
        experimentMarkOther.setOnClickListener(v->sendActionMarker("Other user action"));
        experimentStopButton=button("STOP CONTROLLED EXPERIMENT",0xffb64236);
        advancedArea.addView(experimentStopButton);
        experimentStopButton.setOnClickListener(v->stopExperiment("stopped_by_user"));
        experimentStopButton.setVisibility(View.GONE);
        showMarkerControls(false);
        Button experimentCompare=button("14  COMPARE STEADY RESULTS / EXPORT JSON",0xff23736b);
        advancedArea.addView(experimentCompare);
        experimentCompare.setOnClickListener(v->compareExperiments());

        Button comparison = button("VIEW AND EXPORT COMPARISON", 0xff23736b);
        LinearLayout.LayoutParams cmp = new LinearLayout.LayoutParams(-1, -2);
        cmp.topMargin = dp(14);
        advancedArea.addView(comparison, cmp);
        comparison.setOnClickListener(v -> showComparison());

        summary = text("Waiting for scan", 17, Color.WHITE, true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(22);
        root.addView(summary, sp);

        details = text("", 15, 0xffd5e2ea, false);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
        dp.topMargin = dp(10);
        dp.bottomMargin = dp(15);
        root.addView(details, dp);

        Button export = button("EXPORT JSON REPORT", 0xff29435a);
        root.addView(export);
        export.setOnClickListener(v -> exportJson());
        Button share = button("SHARE REPORT AS TEXT", 0xff29435a);
        LinearLayout.LayoutParams sh = new LinearLayout.LayoutParams(-1, -2);
        sh.topMargin = dp(10);
        root.addView(share, sh);
        share.setOnClickListener(v -> shareReport());

        footer = text("Discovery requires no network account. USB permission is optional. Reports stay on the phone until you export or share; no undocumented vehicle commands are sent.", 12, 0xff9fb7c8, false);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(18);
        root.addView(footer, fp);
    }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    private TextView text(String content, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(3), 1.0f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setMinHeight(dp(52));
        return b;
    }

    private String timeUtc() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private String fileTimeUtc() {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private Intent usbStateIntent() {
        try {
            IntentFilter filter = new IntentFilter(USB_STATE);
            if (Build.VERSION.SDK_INT >= 33)
                return registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            return registerReceiver(null, filter);
        } catch (Exception ignored) { return null; }
    }

    private void scanStage(int percent, String stage) {
        runOnUiThread(() -> {
            if (scanProgress != null && scanActive) {
                scanProgress.setIndeterminate(false);
                scanProgress.setProgress(percent);
                scanStatus.setText(stage + " • " + percent + "%");
            }
        });
    }

    private void finishScan(long startedMs, String message) {
        long elapsed = SystemClock.elapsedRealtime() - startedMs;
        long holdMs = Math.max(0, 550 - elapsed);
        uiHandler.postDelayed(() -> {
            scanProgress.setIndeterminate(false);
            scanProgress.setProgress(100);
            scanStatus.setText(message);
            scanActive = false;
        }, holdMs);
    }

    /**
     * Android mediates permission. We ask the OS for access only when the user
     * requests it; no vendor-specific command or bytes are sent to the vehicle.
     */
    private void requestUsbAccess() {
        try {
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (manager == null) {
                scanStatus.setText("USB manager unavailable");
                return;
            }
            UsbAccessory[] accessories = manager.getAccessoryList();
            boolean hasAccessory = accessories != null && accessories.length > 0;
            UsbDevice firstDevice = null;
            if (!hasAccessory && !manager.getDeviceList().isEmpty())
                firstDevice = manager.getDeviceList().values().iterator().next();
            if (!hasAccessory && firstDevice == null) {
                scanStatus.setText("No USB accessory or host device currently visible for permission request");
                return;
            }
            Intent permission = new Intent(UsbPermissionReceiver.ACTION);
            permission.setComponent(new ComponentName(this, UsbPermissionReceiver.class));
            PendingIntent response = PendingIntent.getBroadcast(this, 431,
                    permission, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            if (hasAccessory) {
                if (manager.hasPermission(accessories[0])) {
                    scanStatus.setText("Android USB accessory permission already granted. Run Deep Scan.");
                    return;
                }
                manager.requestPermission(accessories[0], response);
            } else {
                if (manager.hasPermission(firstDevice)) {
                    scanStatus.setText("Android USB device permission already granted. Run Deep Scan.");
                    return;
                }
                manager.requestPermission(firstDevice, response);
            }
            scanStatus.setText("Android permission request submitted. Approve or deny the system prompt, then run Deep Scan.");
        } catch (Exception ex) {
            scanStatus.setText("USB permission request failed: " + ex.getClass().getSimpleName());
        }
    }


    /** Optional user-confirmed open/close only. No stream reads, writes, or protocol commands. */
    private void confirmConnectionTest() {
        if (scanActive || connectionActive) {
            scanStatus.setText("Another USB test is in progress."); return;
        }
        new AlertDialog.Builder(this)
            .setTitle("USB accessory open/close test")
            .setMessage("Only run while safely parked. Android Auto may disconnect. The app will attempt to open the accessory and immediately close it. No bytes or commands will be sent. Continue?")
            .setNegativeButton("Cancel", (d,w)->{})
            .setPositiveButton("Run test", (d,w)->runConnectionTest())
            .show();
    }
    private void runConnectionTest() {
        if (scanActive || connectionActive) { scanStatus.setText("Another USB test is in progress."); return; }
        connectionActive = true;
        summary.setText("USB connection test running");
        scanStatus.setText("Opening and closing accessory; parked test only.");
        new Thread(this::performConnectionTest, "VehiclePortConnectionTest").start();
    }
    private void performConnectionTest() {
        JSONObject report = new JSONObject();
        String outcome = "unknown";
        long started = SystemClock.elapsedRealtime();
        try {
            report.put("report_format", "Vehicle USB Port Tester communication v1");
            report.put("test_label", "usb_connection_open_close");
            report.put("app_version", "0.8.1");
            report.put("scanned_at_utc", timeUtc());
            report.put("permission_required", true);
            report.put("scan_is_read_only", true);
            report.put("vehicle_data_confirmed", false);
            report.put("obd_codes_scanned", false);
            report.put("bytes_read", 0);
            report.put("bytes_written", 0);
            report.put("vehicle_commands_sent", 0);
            report.put("connection_opened", false);
            report.put("connection_closed", false);
            report.put("android_auto_session_observed", false);
            report.put("note", "Opening an accessory may interrupt Android Auto. An open file descriptor does not establish Ford SYNC diagnostic access.");
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (manager == null) {
                outcome = "usb_manager_unavailable";
            } else {
                UsbAccessory[] accessories = manager.getAccessoryList();
                report.put("accessory_count", accessories == null ? 0 : accessories.length);
                if (accessories == null || accessories.length == 0) {
                    outcome = "no_accessory_detected";
                } else {
                    UsbAccessory accessory = accessories[0];
                    report.put("accessory_model", accessory.getModel());
                    report.put("accessory_manufacturer", accessory.getManufacturer());
                    boolean granted = manager.hasPermission(accessory);
                    report.put("permission_granted", granted);
                    if (!granted) {
                        outcome = "permission_required";
                    } else {
                        ParcelFileDescriptor fd = null;
                        try {
                            fd = manager.openAccessory(accessory);
                            if (fd == null) {
                                outcome = "open_returned_null";
                            } else {
                                report.put("connection_opened", true);
                                outcome = "opened_and_closed";
                            }
                        } catch (Exception ex) {
                            outcome = "open_error";
                            report.put("error_type", ex.getClass().getSimpleName());
                            report.put("error_message", String.valueOf(ex.getMessage()));
                        } finally {
                            if (fd != null) {
                                try {
                                    fd.close();
                                    report.put("connection_closed", true);
                                } catch (Exception ex) {
                                    outcome = "close_error";
                                    report.put("close_error_type", ex.getClass().getSimpleName());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            outcome = "test_error";
            try { report.put("error_type",ex.getClass().getSimpleName()); } catch (Exception ignored) {}
        }
        try {
            report.put("outcome",outcome);
            report.put("duration_ms", SystemClock.elapsedRealtime()-started);
        } catch (Exception ignored) {}
        String finalOutcome = outcome;
        String json = report.toString();
        getPreferences(MODE_PRIVATE).edit().putString("usb_connection",json).apply();
        runOnUiThread(() -> {
            connectionActive = false;
            try { lastReport = new JSONObject(json).toString(2); }
            catch (Exception ignored) { lastReport = json; }
            latestFilename = "vehicle-usbc-connection-"+fileTimeUtc()+".json";
            summary.setText("USB connection test: "+finalOutcome);
            details.setText("Opened: "+report.optBoolean("connection_opened",false)
                +"\\nClosed: "+report.optBoolean("connection_closed",false)
                +"\\nBytes read: 0, bytes sent: 0"
                +"\\nVehicle commands: 0"
                +"\\nOutcome: "+finalOutcome
                +"\\nThis does not identify Ford SYNC or access engine diagnostics.");
            saveLocalReport();
            scanStatus.setText("Connection test saved. Use EXPORT SAVED USB CONNECTION TEST.");
        });
    }
    private void exportSavedConnectionTest() {
        JSONObject saved = stored("usb_connection");
        if (saved == null) {
            scanStatus.setText("No saved connection test. Run TEST USB OPEN / CLOSE while parked.");
            return;
        }
        try {
            lastReport = saved.toString(2);
            latestFilename = "vehicle-usbc-connection-"+fileTimeUtc()+".json";
            summary.setText("Saved USB connection test");
            exportJson();
        } catch (Exception ex) {
            scanStatus.setText("Could not export connection test: "+ex.getClass().getSimpleName());
        }
    }

    /**
     * Read-only, opt-in listen: no application-level payloads are ever written.
     * The accessory might be used by Android Auto; testing is PARKED ONLY.
     * Poll provides a hard deadline rather than an indefinitely blocking read.
     */

    private void confirmDeepAudit() {
        if (auditRunning || scanActive || connectionActive || screenCaptureRunning || experimentRunning) {
            scanStatus.setText("Wait for the running test to complete before starting a deep audit.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Deep communication audit • PARKED ONLY")
            .setMessage("This optional diagnostic can take around two minutes. It samples the Android USB connection, opens/closes the accessory three times, and makes five limited attempts to read incoming data. It SENDS NO vehicle commands, but opening the connection can interrupt Android Auto. Raw captured data may be private. Keep the app visible and the car parked. Start?")
            .setNegativeButton("Cancel", (d, which) -> {})
            .setPositiveButton("Start deep audit", (d, which) -> startDeepAudit())
            .show();
    }

    private void startDeepAudit() {
        if (auditRunning || scanActive || connectionActive || screenCaptureRunning || experimentRunning) return;
        auditRunning = true;
        auditWorkerPid = 0;
        auditStartedAt = SystemClock.elapsedRealtime();
        scanProgress.setVisibility(View.VISIBLE);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(0);
        stopAuditButton.setVisibility(View.VISIBLE);
        summary.setText("Deep communication audit starting...");
        details.setText("Waiting for isolated diagnostic worker. Stay parked.");
        scanStatus.setText("Preparing deep USB audit...");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setKeepScreenOn(true);
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("audit_in_progress", true)
            .putLong("audit_started_wall_ms", System.currentTimeMillis()).apply();
        auditWatchdog = () -> {
            if (auditRunning) stopDeepAudit("hard_timeout_160_seconds");
        };
        uiHandler.postDelayed(auditWatchdog, 160000);
        try {
            Intent i = new Intent(this, UsbAuditService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else if (startService(i) == null) {
                throw new IllegalStateException("Android did not start audit service");
            }
        } catch (Exception ex) {
            stopDeepAudit("unable_to_start_worker");
            scanStatus.setText("Could not start deep audit: " + ex.getClass().getSimpleName());
        }
    }

    private void resumeDeepAuditIfNeeded() {
        boolean persisted = getPreferences(MODE_PRIVATE).getBoolean("audit_in_progress", false);
        if (!auditRunning && !persisted) return;
        if (!auditRunning) {
            long startedWall = getPreferences(MODE_PRIVATE)
                    .getLong("audit_started_wall_ms", System.currentTimeMillis());
            long age = Math.max(0L, System.currentTimeMillis() - startedWall);
            auditRunning = true;
            auditStartedAt = SystemClock.elapsedRealtime() - age;
            stopAuditButton.setVisibility(View.VISIBLE);
            scanProgress.setVisibility(View.VISIBLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setKeepScreenOn(true);
            if (auditWatchdog != null) uiHandler.removeCallbacks(auditWatchdog);
            long remaining = 160000L - age;
            auditWatchdog = () -> {
                if (auditRunning) stopDeepAudit("hard_timeout_160_seconds");
            };
            if (remaining > 0) uiHandler.postDelayed(auditWatchdog, remaining);
            else {
                stopDeepAudit("timeout_before_return_to_app");
                return;
            }
        }
        JSONObject saved = loadDeepAuditJson();
        if (saved != null && !"in_progress".equals(saved.optString("status", "in_progress"))) {
            finishDeepAudit(null);
        } else {
            scanStatus.setText("Deep USB audit continues. Keep this screen visible; STOP remains available.");
            if (saved != null) {
                summary.setText("Deep audit running: " + saved.optString("current_test", "connection monitoring"));
                details.setText("Last USB operation: " + saved.optString("current_operation", "not yet started")
                    + "\nRead attempt: " + saved.optInt("current_attempt", 0)
                    + "\nBytes observed: " + saved.optInt("total_bytes_read", 0));
            }
        }
    }

    private void terminateDeepAuditWorker() {
        int pid = auditWorkerPid;
        if (pid > 0 && pid != android.os.Process.myPid()) {
            // The Android service is in a DIFFERENT PROCESS under the same UID.
            try { android.os.Process.killProcess(pid); } catch (Exception ignored) {}
        } else {
            // Service main thread can handle STOP even when its reader thread hangs.
            try {
                startService(new Intent(this, UsbAuditService.class)
                    .setAction(UsbAuditService.ACTION_STOP));
            } catch (Exception ignored) {}
        }
        try { stopService(new Intent(this, UsbAuditService.class)); }
        catch (Exception ignored) {}
        auditWorkerPid = 0;
    }

    private void stopDeepAudit(String reason) {
        if (!auditRunning) return;
        terminateDeepAuditWorker();
        // The service saves each completed step to disk before a new step starts.
        finishDeepAudit(reason);
    }

    private JSONObject loadDeepAuditJson() {
        try {
            java.io.File file = new java.io.File(getFilesDir(), UsbAuditService.REPORT_FILE);
            if (!file.exists()) return null;
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return null;
        }
    }

    private void finishDeepAudit(String forcedReason) {
        if (!auditRunning) return;
        auditRunning = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setKeepScreenOn(false);
        getPreferences(MODE_PRIVATE).edit().putBoolean("audit_in_progress", false).apply();
        if (auditWatchdog != null) uiHandler.removeCallbacks(auditWatchdog);
        stopAuditButton.setVisibility(View.GONE);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(100);
        JSONObject report = loadDeepAuditJson();
        if (report == null) {
            report = new JSONObject();
            try {
                report.put("report_format", "Vehicle Port Tester deep communication audit v1");
                report.put("app_version", "0.8.1");
                report.put("status", "report_unavailable");
            } catch (Exception ignored) {}
        }
        try {
            if (forcedReason != null) {
                report.put("status", forcedReason);
                report.put("terminated_by_main_app", true);
                report.put("reader_process_closed", true);
                report.put("partial_results_preserved", true);
            }
            report.put("main_app_elapsed_ms", SystemClock.elapsedRealtime() - auditStartedAt);
        } catch (Exception ignored) {}
        try { lastReport = report.toString(2); } catch (Exception ignored) { lastReport = report.toString(); }
        latestFilename = "vehicle-deep-usb-audit-" + fileTimeUtc() + ".json";
        summary.setText("Deep audit: " + report.optString("status", "finished"));
        details.setText("Tests completed: " + (report.optJSONArray("tests") == null ? 0 : report.optJSONArray("tests").length())
            + "\nTotal bytes received: " + report.optInt("total_bytes_read", 0)
            + "\nWorker status: " + report.optString("status", "unknown")
            + "\nNo vehicle commands sent. Ford SYNC internal access has NOT been established."
            + "\nExport the detailed deep audit JSON to inspect each stage.");
        scanStatus.setText("Deep audit stopped/finished. EXPORT SAVED DEEP AUDIT REPORT.");
        try {
            java.io.File file = new java.io.File(getFilesDir(), UsbAuditService.REPORT_FILE);
            java.nio.file.Files.write(file.toPath(), lastReport.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void exportDeepAudit() {
        JSONObject report = loadDeepAuditJson();
        if (report == null) {
            scanStatus.setText("No deep audit report yet. Run test 7 while parked.");
            return;
        }
        try { lastReport = report.toString(2); } catch (Exception ignored) { lastReport = report.toString(); }
        latestFilename = "vehicle-deep-usb-audit-" + fileTimeUtc() + ".json";
        exportJson();
    }

    private void confirmListenTest() {
        if (listenReaderStalled) {
            scanStatus.setText("Previous USB read was unresponsive. Force stop and reopen this app before retesting.");
            return;
        }
        if (scanActive || connectionActive) {
            scanStatus.setText("Another USB test is already running.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Analyze incoming USB data?")
            .setMessage("PARKED ONLY. This may disconnect Android Auto. The app opens the authorized accessory and captures up to 4,096 incoming bytes or ten seconds, whichever comes first. It sends NO commands. The report includes a raw hex capture, which could contain personal information. Continue?")
            .setNegativeButton("Cancel", (d, w) -> {})
            .setPositiveButton("Analyze stream", (d, w) -> runListenTest())
            .show();
    }

    private void runListenTest() {
        if (listenReaderStalled) {
            scanStatus.setText("Restart the app before retesting after a blocked read.");
            return;
        }
        if (scanActive || connectionActive) {
            scanStatus.setText("A test is still running.");
            return;
        }
        connectionActive = true;
        listenCancelRequested = false;
        listenCompleted.set(false);
        listenBytesObserved = 0;
        listenOpened = false;
        listenDescriptor = null;
        listenStartAt = SystemClock.elapsedRealtime();
        summary.setText("Analyzing incoming USB stream");
        scanProgress.setVisibility(View.VISIBLE);
        scanProgress.setMax(100);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(0);
        stopListenButton.setVisibility(View.VISIBLE);
        scanStatus.setText("Checking Android accessory permission...");
        listenWatchdog = () -> { if (!listenCompleted.get()) abortStalledListen("hard_timeout_12_seconds"); };
        uiHandler.postDelayed(listenWatchdog, 12000);
        new Thread(this::performListenTest, "VehiclePortListenTest").start();
    }

    private void stopListenTest() {
        if (!connectionActive || listenCompleted.get()) return;
        listenCancelRequested = true;
        scanStatus.setText("Stopping USB read...");
        closeListenFdAsync();
        uiHandler.postDelayed(() -> {
            if (!listenCompleted.get()) abortStalledListen("cancelled_reader_unresponsive");
        }, 1500);
    }

    private void closeListenFdAsync() {
        ParcelFileDescriptor fd = listenDescriptor;
        if (fd == null) return;
        new Thread(() -> {
            try { fd.close(); } catch (Exception ignored) {}
        }, "VehicleUsbClose").start();
    }

    private void abortStalledListen(String reason) {
        if (!listenCompleted.compareAndSet(false, true)) return;
        listenCancelRequested = true;
        listenReaderStalled = true;
        closeListenFdAsync();
        if (listenWatchdog != null) uiHandler.removeCallbacks(listenWatchdog);
        JSONObject timeout = new JSONObject();
        try {
            timeout.put("report_format", "Vehicle USB Port Tester protocol analysis v1");
            timeout.put("test_label", "usb_accessory_passive_protocol_analysis");
            timeout.put("app_version", "0.8.1");
            timeout.put("scanned_at_utc", timeUtc());
            timeout.put("outcome", reason);
            timeout.put("scan_is_read_only", true);
            timeout.put("connection_opened", listenOpened);
            timeout.put("connection_closed", false);
            timeout.put("close_status", "requested_not_confirmed");
            timeout.put("bytes_observed_before_abort", listenBytesObserved);
            timeout.put("bytes_written", 0);
            timeout.put("vehicle_commands_sent", 0);
            timeout.put("vehicle_data_confirmed", false);
            timeout.put("reader_thread_stalled", true);
            timeout.put("duration_ms", SystemClock.elapsedRealtime() - listenStartAt);
            timeout.put("note", "USB read did not return within safety deadline; restart app before retesting.");
        } catch (Exception ignored) {}
        String json = timeout.toString();
        getPreferences(MODE_PRIVATE).edit().putString("usb_listen", json).apply();
        lastReport = json;
        latestFilename = "vehicle-usbc-protocol-timeout-" + fileTimeUtc() + ".json";
        saveLocalReport();
        connectionActive = false;
        stopListenButton.setVisibility(View.GONE);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(100);
        summary.setText("USB test stopped: " + reason);
        details.setText("USB read did not return in time. Restart the app before another capture. Export the saved report for details.");
        scanStatus.setText("Stopped. Export saved USB protocol report.");
    }

    private void updateListenProgress(long elapsedMs, int bytes) {
        int percent = Math.min(99, Math.max((int) (elapsedMs / 100), bytes * 100 / 4096));
        runOnUiThread(() -> {
            if (!listenCompleted.get()) {
                scanProgress.setIndeterminate(false);
                scanProgress.setProgress(percent);
                scanStatus.setText("Reading: " + bytes + "/4096 bytes, " + (elapsedMs / 1000) + " of 10 sec");
            }
        });
    }

    /**
     * Safe observations of the Android USB state. These are snapshots of what
     * Android exposes, not reads of the vehicle ECU or Ford SYNC firmware.
     */
    private JSONObject getUsbSessionSnapshot() {
        JSONObject result = new JSONObject();
        try {
            result.put("captured_at_utc", timeUtc());
            Intent sticky = usbStateIntent();
            JSONObject usb = new JSONObject();
            if (sticky != null) {
                for (String key : new String[]{"connected", "configured", "host_connected", "accessory"}) {
                    if (sticky.hasExtra(key)) usb.put(key, sticky.getBooleanExtra(key, false));
                }
            }
            result.put("android_usb_state", usb);
            result.put("state_broadcast_available", sticky != null);
            UsbManager m = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (m != null) {
                UsbAccessory[] aa = m.getAccessoryList();
                result.put("accessory_count", aa == null ? 0 : aa.length);
                result.put("usb_host_device_count", m.getDeviceList().size());
                if (aa != null && aa.length > 0) {
                    result.put("accessory_model", aa[0].getModel());
                    result.put("accessory_permission", m.hasPermission(aa[0]));
                }
            } else result.put("usb_manager_available", false);
        } catch (Exception e) {
            try { result.put("snapshot_error", e.getClass().getSimpleName()); }
            catch (Exception ignored) {}
        }
        return result;
    }

    private boolean usbSessionChanged(JSONObject before, JSONObject after) {
        if (before == null || after == null) return false;
        if (before.optInt("accessory_count", -1) != after.optInt("accessory_count", -1)) return true;
        JSONObject a = before.optJSONObject("android_usb_state");
        JSONObject b = after.optJSONObject("android_usb_state");
        if (a == null || b == null) return a != b;
        for (String key : new String[]{"connected", "configured", "host_connected", "accessory"}) {
            if (a.has(key) != b.has(key) || a.optBoolean(key) != b.optBoolean(key)) return true;
        }
        return false;
    }

    private String explanationForListenOutcome(String outcome) {
        switch (outcome) {
            case "usb_read_io_error":
                return "Android returned EIO during USB reading. This is a communication error, not a timeout or proof of a bad cable. Android Auto session contention, USB reconfiguration, or accessory behavior are possible.";
            case "usb_io_error":
                return "The Android USB driver returned EIO before or during a USB operation. No diagnostic data was established.";
            case "accessory_disconnected_or_error":
            case "accessory_disconnected_or_unavailable":
                return "The accessory became unavailable or Android reported a USB disconnect/error. Check the connection while parked.";
            case "timed_out_no_data":
                return "The ten-second timeout elapsed without receiving data. The app stopped on schedule.";
            case "captured_until_timeout":
                return "Some incoming bytes were captured before the ten-second limit.";
            case "capture_limit_reached":
                return "The 4096-byte capture limit was reached. Captured data is unclassified.";
            case "cancelled_by_user":
                return "The Stop button ended this capture.";
            case "no_accessory_detected":
                return "Android did not expose an automotive USB accessory at the start of the test.";
            case "permission_required":
                return "Android has not granted USB accessory access. Request permission before retrying.";
            case "received_unclassified_data":
                return "Data was received, but no Ford diagnostic information was confirmed.";
            default:
                return "This result does not confirm access to vehicle computers or Ford SYNC internals.";
        }
    }

    private void performListenTest() {
        long started = SystemClock.elapsedRealtime();
        JSONObject report = new JSONObject();
        ParcelFileDescriptor connection = null;
        String outcome = "unknown";
        String stage = "preflight";
        int bytesRead = 0;
        boolean limitReached = false;
        byte[] capture = new byte[4096];
        JSONArray events = new JSONArray();
        long firstByteDelay = -1L;
        long lastByteDelay = -1L;
        try {
            report.put("report_format", "Vehicle USB Port Tester protocol analysis v1");
            report.put("test_label", "usb_accessory_passive_protocol_analysis");
            report.put("scanned_at_utc", timeUtc());
            report.put("app_version", "0.8.1");
            report.put("usb_state_before", getUsbSessionSnapshot());
            report.put("listen_timeout_ms", 10000);
            report.put("max_capture_bytes", capture.length);
            report.put("scan_is_read_only", true);
            report.put("bytes_written", 0);
            report.put("vehicle_commands_sent", 0);
            report.put("vehicle_data_confirmed", false);
            report.put("obd_codes_scanned", false);
            report.put("connection_opened", false);
            report.put("connection_closed", false);
            report.put("android_auto_session_observed", false);
            report.put("note", "Incoming USB frames may be Android Auto traffic, not Ford diagnostic data. TLS-like framing does not reveal encrypted contents. Capturing can interrupt Android Auto.");
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (manager == null) {
                outcome = "usb_manager_unavailable";
            } else {
                UsbAccessory[] accessories = manager.getAccessoryList();
                report.put("accessory_count", accessories == null ? 0 : accessories.length);
                if (accessories == null || accessories.length == 0) {
                    outcome = "no_accessory_detected";
                } else {
                    UsbAccessory accessory = accessories[0];
                    report.put("accessory_model", accessory.getModel());
                    report.put("accessory_manufacturer", accessory.getManufacturer());
                    boolean permitted = manager.hasPermission(accessory);
                    report.put("permission_granted", permitted);
                    if (!permitted) {
                        outcome = "permission_required";
                    } else {
                        runOnUiThread(() -> scanStatus.setText("Opening the authorized USB accessory..."));
                        stage = "open_accessory";
                        connection = manager.openAccessory(accessory);
                        listenDescriptor = connection;
                        if (connection == null) {
                            outcome = "open_returned_null";
                        } else {
                            report.put("connection_opened", true);
                            listenOpened = true;
                            // Require kernel nonblocking reads. Poll alone is not a guarantee
                            // that a subsequent USB accessory read cannot block indefinitely.
                            stage = "configure_nonblocking";
                            report.put("usb_state_after_open", getUsbSessionSnapshot());
                            int oldFlags = Os.fcntlInt(connection.getFileDescriptor(), OsConstants.F_GETFL, 0);
                            Os.fcntlInt(connection.getFileDescriptor(), OsConstants.F_SETFL,
                                    oldFlags | OsConstants.O_NONBLOCK);
                            int confirmedFlags = Os.fcntlInt(connection.getFileDescriptor(), OsConstants.F_GETFL, 0);
                            if ((confirmedFlags & OsConstants.O_NONBLOCK) == 0) {
                                throw new IllegalStateException("USB accessory fd does not support nonblocking reads");
                            }
                            report.put("nonblocking_fd_verified", true);
                            long listenStarted = SystemClock.elapsedRealtime();
                            runOnUiThread(() -> scanStatus.setText("Connected. Listening for up to 10 seconds / 4,096 bytes..."));
                            long deadline = listenStarted + 10000;
                            byte[] buffer = new byte[256];
                            StructPollfd pollDescriptor = new StructPollfd();
                            pollDescriptor.fd = connection.getFileDescriptor();
                            pollDescriptor.events = (short) (OsConstants.POLLIN | OsConstants.POLLERR | OsConstants.POLLHUP);
                            outcome = "timed_out_no_data";
                            while (!listenCancelRequested && SystemClock.elapsedRealtime() < deadline && bytesRead < capture.length) {
                                updateListenProgress(SystemClock.elapsedRealtime() - listenStarted, bytesRead);
                                long remaining = deadline - SystemClock.elapsedRealtime();
                                int waitMs = (int) Math.min(250L, remaining);
                                if (waitMs <= 0) break;
                                stage = "poll";
                                int polls = Os.poll(new StructPollfd[]{pollDescriptor}, waitMs);
                                if (polls == 0) continue;
                                int flags = pollDescriptor.revents;
                                pollDescriptor.revents = 0;
                                if ((flags & OsConstants.POLLIN) != 0) {
                                    int received;
                                    try {
                                        stage = "read";
                                        received = Os.read(connection.getFileDescriptor(), buffer, 0,
                                                Math.min(buffer.length, capture.length - bytesRead));
                                    } catch (ErrnoException ex) {
                                        if (ex.errno == OsConstants.EAGAIN)
                                            continue;
                                        throw ex;
                                    }
                                    if (received <= 0) {
                                        outcome = "connection_closed_by_peer";
                                        break;
                                    }
                                    long eventMs = SystemClock.elapsedRealtime() - listenStarted;
                                    if (firstByteDelay < 0) firstByteDelay = eventMs;
                                    lastByteDelay = eventMs;
                                    System.arraycopy(buffer, 0, capture, bytesRead, received);
                                    bytesRead += received;
                                    listenBytesObserved = bytesRead;
                                    updateListenProgress(SystemClock.elapsedRealtime() - listenStarted, bytesRead);
                                    if (events.length() < 100) {
                                        JSONObject event = new JSONObject();
                                        event.put("at_ms", eventMs);
                                        event.put("bytes", received);
                                        event.put("total_bytes", bytesRead);
                                        events.put(event);
                                    }
                                    outcome = "received_unclassified_data";
                                    // Display is updated by updateListenProgress using real elapsed time and bytes.
                                } else if ((flags & (OsConstants.POLLERR | OsConstants.POLLHUP)) != 0) {
                                    outcome = "accessory_disconnected_or_error";
                                    break;
                                }
                            }
                            limitReached = bytesRead >= capture.length;
                            if (listenCancelRequested) outcome = "cancelled_by_user";
                            else if (limitReached) outcome = "capture_limit_reached";
                            else if (bytesRead > 0 && "timed_out_no_data".equals(outcome)) outcome = "captured_until_timeout";
                        }
                    }
                }
            }
        } catch (Exception ex) {
            outcome = "listen_error";
            if (ex instanceof ErrnoException) {
                int code = ((ErrnoException) ex).errno;
                if (code == OsConstants.EIO) {
                    outcome = "read".equals(stage) ? "usb_read_io_error" : "usb_io_error";
                } else if (code == OsConstants.ENODEV || code == OsConstants.EPIPE
                        || code == OsConstants.ENXIO) {
                    outcome = "accessory_disconnected_or_unavailable";
                } else {
                    outcome = "usb_os_error";
                }
                try { report.put("error_errno", code); } catch (Exception ignored) {}
            }
            try {
                report.put("error_stage", stage);
                report.put("error_type", ex.getClass().getSimpleName());
                report.put("error_message", String.valueOf(ex.getMessage()));
            } catch (Exception ignored) {}
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                    report.put("connection_closed", true);
                } catch (Exception ex) {
                    try { report.put("close_error", ex.getClass().getSimpleName()); } catch (Exception ignored) {}
                }
            }
            listenDescriptor = null;
            try {
                JSONObject after = getUsbSessionSnapshot();
                report.put("usb_state_after", after);
                report.put("usb_state_changed_during_test",
                        usbSessionChanged(report.optJSONObject("usb_state_before"), after));
            } catch (Exception ignored) {}
        }
        try {
            StringBuilder hexCapture = new StringBuilder(bytesRead * 3);
            final char[] digits = "0123456789ABCDEF".toCharArray();
            for (int i = 0; i < bytesRead; i++) {
                if (i > 0) hexCapture.append(' ');
                int value = capture[i] & 255;
                hexCapture.append(digits[value >>> 4]).append(digits[value & 15]);
            }
            String fullHex = hexCapture.toString();
            int previewChars = Math.min(fullHex.length(), 256 * 3 - 1);
            report.put("bytes_read", bytesRead);
            report.put("hex_preview", fullHex.substring(0, previewChars));
            report.put("hex_capture", fullHex);
            report.put("hex_capture_privacy_note", "May contain private application traffic. Share the report only when you intend to disclose the received bytes.");
            report.put("capture_limit_reached", limitReached);
            report.put("first_byte_latency_ms", firstByteDelay < 0 ? JSONObject.NULL : firstByteDelay);
            report.put("last_byte_latency_ms", lastByteDelay < 0 ? JSONObject.NULL : lastByteDelay);
            report.put("read_events", events);
            report.put("protocol_analysis", UsbProtocolAnalyzer.analyze(capture, bytesRead));
            report.put("outcome", outcome);
            report.put("outcome_explanation", explanationForListenOutcome(outcome));
            report.put("duration_ms", SystemClock.elapsedRealtime() - started);
        } catch (Exception ex) {
            try { report.put("analysis_error", ex.getClass().getSimpleName()); } catch (Exception ignored) {}
        }
        final String result = outcome;
        final int finalBytes = bytesRead;
        final String json = report.toString();
        if (!listenCompleted.compareAndSet(false, true)) {
            // Watchdog has already saved an error report and restored the UI.
            listenReaderStalled = false;
            return;
        }
        if (listenWatchdog != null) uiHandler.removeCallbacks(listenWatchdog);
        getPreferences(MODE_PRIVATE).edit().putString("usb_listen", json).apply();
        runOnUiThread(() -> {
            stopListenButton.setVisibility(View.GONE);
            connectionActive = false;
            scanProgress.setIndeterminate(false);
            scanProgress.setProgress(100);
            try { lastReport = new JSONObject(json).toString(2); }
            catch (Exception ignored) { lastReport = json; }
            latestFilename = "vehicle-usbc-protocol-" + fileTimeUtc() + ".json";
            JSONObject protocol = report.optJSONObject("protocol_analysis");
            JSONObject framing = protocol == null ? null : protocol.optJSONObject("framing_analysis");
            summary.setText("USB protocol analysis: " + result);
            details.setText("Connection opened: " + report.optBoolean("connection_opened", false)
                + "\nConnection closed: " + report.optBoolean("connection_closed", false)
                + "\nIncoming bytes: " + finalBytes
                + "\nMatching structured frames: " + (framing == null ? 0 : framing.optInt("candidate_frames", 0))
                + "\nOutgoing bytes and vehicle commands: 0"
                + "\nOutcome: " + result
                + "\nExplanation: " + explanationForListenOutcome(result)
                + "\nHeader matches are not confirmed Ford diagnostic data."
                + "\nThe exported hex may contain personal information.");
            saveLocalReport();
            scanStatus.setText("Protocol analysis saved. Use EXPORT SAVED USB PROTOCOL REPORT.");
        });
    }

    private void exportSavedListenTest() {
        JSONObject saved = stored("usb_listen");
        if (saved == null) {
            scanStatus.setText("No protocol report saved. Run ANALYZE USB STREAM while parked.");
            return;
        }
        try {
            lastReport = saved.toString(2);
            latestFilename = "vehicle-usbc-protocol-" + fileTimeUtc() + ".json";
            summary.setText("Saved USB Protocol test");
            exportJson();
        } catch (Exception ex) {
            scanStatus.setText("Could not export Listen report: " + ex.getClass().getSimpleName());
        }
    }

    private void runScan(String trigger, boolean save) {
        if (scanActive || connectionActive || screenCaptureRunning || experimentRunning || fullAutoRunning) {
            scanStatus.setText("Another USB test is running. Please finish or stop that test first.");
            return;
        }
        scanActive = true;
        scanProgress.setProgress(0);
        scanProgress.setIndeterminate(true);
        scanProgress.setVisibility(View.VISIBLE);
        scanStatus.setText("Scanning " + trigger + " — checking USB connection...");
        summary.setText("New scan in progress...");
        long startedMs = SystemClock.elapsedRealtime();
        new Thread(() -> captureScan(trigger, save, startedMs), "VehiclePortScan").start();
    }

    private void captureScan(String trigger, boolean save, long startedMs) {
        try {
            JSONObject report = new JSONObject();
            report.put("report_format", "Vehicle USB Port Tester v2");
            report.put("test_label", trigger.startsWith("Test: ") ? trigger.substring(6) : "unlabeled");
            report.put("scanned_at_utc", timeUtc());
            report.put("trigger", trigger);
            report.put("app_version", "0.8.1");
            report.put("device_manufacturer", Build.MANUFACTURER);
            report.put("device_model", Build.MODEL);
            report.put("android_api_level", Build.VERSION.SDK_INT);
            report.put("android_usb_host_supported", getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST));
            report.put("android_usb_accessory_supported", getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY));
            report.put("scan_is_read_only", true);
            report.put("obd_codes_scanned", false);
            report.put("vehicle_data_confirmed", false);
            report.put("note", "USB connectivity is not proof that a vehicle exposes diagnostic data. This app cannot read OBD-II codes from a standard media/charging USB-C port.");
            scanStage(15, "Phone capabilities checked");

            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int plugged = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            String charging = plugged == BatteryManager.BATTERY_PLUGGED_USB ? "USB power detected" :
                    plugged == BatteryManager.BATTERY_PLUGGED_AC ? "AC power" :
                    plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ? "Wireless charging" :
                    plugged == 0 ? "Not charging" : "Unknown";
            report.put("power_state", charging);
            scanStage(30, "USB power checked");

            Intent state = usbStateIntent();
            JSONObject usbState = new JSONObject();
            if (state != null) {
                for (String key : new String[]{"connected","configured","host_connected","mtp","ptp","adb","rndis","midi","accessory","audio_source","ncm"}) {
                    if (state.hasExtra(key)) usbState.put(key, state.getBooleanExtra(key, false));
                }
            }
            report.put("android_usb_state", usbState);
            report.put("usb_state_broadcast_available", state != null);
            scanStage(50, "USB connection state checked");

            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            Map<String,UsbDevice> devices = manager == null ? null : manager.getDeviceList();
            JSONArray found = new JSONArray();
            if (devices != null) {
                for (UsbDevice d : devices.values()) {
                    JSONObject device = new JSONObject();
                    device.put("vendor_id", d.getVendorId());
                    device.put("product_id", d.getProductId());
                    device.put("usb_device_class", d.getDeviceClass());
                    device.put("usb_device_subclass", d.getDeviceSubclass());
                    device.put("interface_count", d.getInterfaceCount());
                    device.put("android_permission_granted", manager.hasPermission(d));
                    try { device.put("manufacturer", String.valueOf(d.getManufacturerName())); }
                    catch (Exception e) { device.put("manufacturer", "unavailable"); }
                    try { device.put("product", String.valueOf(d.getProductName())); }
                    catch (Exception e) { device.put("product", "unavailable"); }
                    JSONArray interfaces = new JSONArray();
                    for (int i = 0; i < d.getInterfaceCount(); i++) {
                        UsbInterface iface = d.getInterface(i);
                        JSONObject inf = new JSONObject();
                        inf.put("id", iface.getId());
                        inf.put("class", iface.getInterfaceClass());
                        inf.put("subclass", iface.getInterfaceSubclass());
                        inf.put("protocol", iface.getInterfaceProtocol());
                        inf.put("endpoint_count", iface.getEndpointCount());
                        interfaces.put(inf);
                    }
                    device.put("interfaces", interfaces);
                    found.put(device);
                }
            }
            report.put("usb_host_device_count", found.length());
            report.put("usb_host_devices", found);
            scanStage(75, "USB devices inspected");
            JSONArray accessibleAccessories = new JSONArray();
            try {
                UsbAccessory[] accessories = manager == null ? null : manager.getAccessoryList();
                if (accessories != null) {
                    for (UsbAccessory a : accessories) {
                        JSONObject obj = new JSONObject();
                        obj.put("manufacturer", a.getManufacturer());
                        obj.put("model", a.getModel());
                        obj.put("version", a.getVersion());
                        obj.put("description", a.getDescription());
                        obj.put("android_permission_granted", manager.hasPermission(a));
                        accessibleAccessories.put(obj);
                    }
                }
            } catch (Exception ignored) { /* Some phones restrict USB accessory enumeration. */ }
            report.put("usb_accessory_count", accessibleAccessories.length());
            report.put("usb_accessories", accessibleAccessories);
            scanStage(90, "USB accessories inspected");
            JSONObject deepReport = null;
            if ("Test: usb_c_deep".equals(trigger)) {
                scanStage(92, "Inspecting USB descriptors and interfaces");
                deepReport = DeepDiagnostics.capture(this, manager);
                android.content.SharedPreferences permissionLog =
                    getSharedPreferences("usb_permission_results", MODE_PRIVATE);
                JSONObject requestedAccess = new JSONObject();
                requestedAccess.put("previous_request_received", permissionLog.contains("last_granted"));
                requestedAccess.put("last_request_granted", permissionLog.contains("last_granted")
                    ? permissionLog.getBoolean("last_granted", false) : JSONObject.NULL);
                requestedAccess.put("last_checked_at_ms", permissionLog.getLong("last_checked_at_ms", 0L));
                deepReport.put("latest_permission_request", requestedAccess);
                report.put("deep_diagnostics", deepReport);
                scanStage(97, "Discovering phone-side car and media apps");
            }
            report.put("communication_path", usbState.optBoolean("connected", false)
                ? "Android reports USB attached; vehicle diagnostic APIs not established"
                : "Android does not currently report an attached USB data connection");
            report.put("limitations", new JSONArray()
                .put("On Android Auto or phone-peripheral connections, Android USB host enumeration can be empty even with a working data cable.")
                .put("USB power alone does not prove there is a USB data link.")
                .put("No engine, ABS, transmission, or other vehicle data was queried.")
                .put("If vehicle data is not provided to Android via a supported API or adapter, it cannot be obtained by this app.")
                .put("This is a capability test, not a vehicle health check."));

            final String reportText = report.toString(2);
            final String capturedLabel = report.optString("test_label", "unlabeled");
            String visible = "Scan time: " + report.getString("scanned_at_utc") + "\n"
                + "Phone: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Power: " + charging + "\n"
                + "USB state visible to Android: " + (state == null ? "Not reported" : usbState.toString()) + "\n"
                + "USB-host devices listed: " + found.length() + "\n"
                + "USB accessories listed: " + accessibleAccessories.length() + "\n"
                + "Vehicle information confirmed: NO\n"
                + "Engine fault codes read: NO\n\n"
                + (found.length() > 0
                    ? "Android lists USB device interfaces. Details are saved in the report; they are not automatically vehicle diagnostics."
                    : "No USB-host devices listed. This is common when the vehicle acts as USB host. It does not mean the cable is broken.")
                + "\n\nCapture all three labeled tests, then VIEW AND EXPORT COMPARISON."
                + (deepReport == null ? "" : "\n\nDEEP USB DISCOVERY:"
                    + "\nHost devices: " + deepReport.optJSONArray("host_devices").length()
                    + "\nAccessory descriptors: " + deepReport.optJSONArray("accessories").length()
                    + "\nPhone apps with car/media services (visible subset): "
                    + deepReport.optJSONArray("phone_apps_with_car_or_media_services").length()
                    + "\nFord SYNC internal apps: NOT ACCESSIBLE via standard Android USB"
                    + "\nNo vehicle control commands sent. Full details in JSON report.");
            if (trigger.startsWith("Test: ")) {
                getPreferences(MODE_PRIVATE).edit()
                    .putString(trigger.substring(6), report.toString()).apply();
            }
            runOnUiThread(() -> {
                lastReport = reportText;
                summary.setText("Scan captured: " + capturedLabel);
                details.setText(visible);
                if (save) saveLocalReport();
                finishScan(startedMs, ("usb_c_deep".equals(capturedLabel) ? "DEEP SCAN COMPLETE — use EXPORT SAVED DEEP USB-C REPORT" : "Scan complete") + " • " + timeUtc());
            });
        } catch (Exception ex) {
            runOnUiThread(() -> {
                summary.setText("Scan could not complete");
                details.setText("The scanner hit an Android access error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\nNo vehicle commands were sent.");
                finishScan(startedMs, "Scan finished with an error • " + timeUtc());
            });
        }
    }

    private JSONObject stored(String key) {
        String json = getPreferences(MODE_PRIVATE).getString(key, "");
        if (json.isEmpty()) return null;
        try { return new JSONObject(json); } catch (Exception ignored) { return null; }
    }

    private String describe(String label, JSONObject item) {
        if (item == null) return label + ": NOT TESTED YET";
        JSONObject state = item.optJSONObject("android_usb_state");
        if (state == null) state = new JSONObject();
        String connected = state.has("connected") ? String.valueOf(state.optBoolean("connected")) : "not reported";
        String configured = state.has("configured") ? String.valueOf(state.optBoolean("configured")) : "not reported";
        return label + " | connected=" + connected +
            ", configured=" + configured + ", power=" + item.optString("power_state", "unknown") +
            ", host devices=" + item.optInt("usb_host_device_count", 0) +
            ", accessories=" + item.optInt("usb_accessory_count", 0);
    }

    private void exportSavedDeepReport() {
        JSONObject deep = stored("usb_c_deep");
        if (deep == null) {
            scanStatus.setText("No Deep USB-C report saved yet. Tap DEEP USB-C DISCOVERY, wait for 100%, then export.");
            return;
        }
        try {
            lastReport = deep.toString(2);
            latestFilename = "vehicle-usbc-deep-" + fileTimeUtc() + ".json";
            summary.setText("Saved Deep USB-C Scan selected");
            scanStatus.setText("Exporting full Deep Scan captured " + deep.optString("scanned_at_utc", "earlier") + ". USB change notifications cannot replace it.");
            exportJson();
        } catch (Exception ex) {
            scanStatus.setText("Could not export saved Deep Scan: " + ex.getClass().getSimpleName());
        }
    }

    private void showComparison() {
        try {
            JSONObject unplug = stored("disconnected");
            JSONObject usbc = stored("usb_c");
            JSONObject usba = stored("usb_a");
            JSONObject combined = new JSONObject();
            combined.put("report_format", "Vehicle USB Port Tester comparison v2");
            combined.put("app_version", "0.8.1");
            combined.put("created_at_utc", timeUtc());
            combined.put("baseline_unplugged", unplug == null ? JSONObject.NULL : unplug);
            combined.put("usb_c_port", usbc == null ? JSONObject.NULL : usbc);
            combined.put("regular_usb_port", usba == null ? JSONObject.NULL : usba);
            JSONObject deep = stored("usb_c_deep");
            combined.put("deep_usb_c_port", deep == null ? JSONObject.NULL : deep);
            JSONObject connection = stored("usb_connection");
            combined.put("usb_connection_open_close", connection == null ? JSONObject.NULL : connection);
            combined.put("vehicle_diagnostic_access_confirmed", false);
            combined.put("explanation", "USB configured indicates a link, not an OBD-II connection. Apps cannot assume Android Auto exposes engine or fault-code data.");
            lastReport = combined.toString(2);
            latestFilename = "vehicle-usb-comparison-" + fileTimeUtc() + ".json";
            int count = (unplug == null ? 0 : 1) + (usbc == null ? 0 : 1) + (usba == null ? 0 : 1);
            summary.setText("Comparison: " + count + " of 3 scans saved");
            details.setText(describe("1 UNPLUGGED", unplug) + "\n\n" +
                describe("2 USB-C", usbc) + "\n\n" +
                describe("3 REGULAR USB", usba) +
                (deep == null ? "" : "\n\n4 DEEP USB-C: last captured " + deep.optString("scanned_at_utc", "unknown")) +
                "\n\nEngine trouble codes: NOT SCANNED\n" +
                "Android Auto data link does not imply engine diagnostic access. " +
                "Tap EXPORT JSON REPORT to save all available results in one file.");
            saveLocalReport();
        } catch (Exception ex) {
            summary.setText("Unable to compare scans");
            details.setText(ex.getMessage());
        }
    }

    private void saveLocalReport() {
        try {
            File dir = new File(getFilesDir(), "reports");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create reports directory");
            latestFilename = "vehicle-usbc-" + fileTimeUtc() + ".json";
            File file = new File(dir, latestFilename);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(lastReport.getBytes(StandardCharsets.UTF_8));
            }
            footer.setText("Saved locally: " + latestFilename + "\nTap Export JSON Report to save a shareable copy. Reports remain private unless you share them.");
        } catch (Exception ex) {
            footer.setText("Could not save locally: " + ex.getMessage() + ". You can still share or export the report.");
        }
    }


    private static String screenLabel(String mode) {
        return UsbScreenCompareService.MODE_AUTO.equals(mode)
                ? "Android Auto screen" : "Ford SYNC home screen";
    }

    private JSONObject readScreenCapture(String mode) {
        try {
            File f = new File(getFilesDir(), UsbScreenCompareService.filename(mode));
            if (!f.exists()) return null;
            return new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    StandardCharsets.UTF_8));
        } catch (Exception ignored) { return null; }
    }

    private void persistScreenCapture(JSONObject report, String mode) {
        try {
            File f = new File(getFilesDir(), UsbScreenCompareService.filename(mode));
            java.nio.file.Files.write(f.toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void confirmScreenCapture(String mode) {
        if (auditRunning || screenCaptureRunning || scanActive || connectionActive || experimentRunning) {
            scanStatus.setText("Stop the current test before starting a screen comparison.");
            return;
        }
        String title = screenLabel(mode);
        new AlertDialog.Builder(this)
            .setTitle("Capture " + title + " • PARKED ONLY")
            .setMessage("First show " + title + " on the Explorer. This runs a 20-second read-only USB capture with a wake lock and STOP button. It can interrupt Android Auto. Incoming bytes could be private. It cannot independently verify the Ford screen or read vehicle ECU data. Ready?")
            .setNegativeButton("Cancel", (d,w) -> {})
            .setPositiveButton("Start capture", (d,w) -> startScreenCapture(mode))
            .show();
    }

    private void startScreenCapture(String mode) {
        if (auditRunning || screenCaptureRunning || scanActive || connectionActive || experimentRunning) return;
        screenCaptureRunning = true;
        screenCaptureMode = mode;
        screenWorkerPid = 0;
        screenStartedAt = SystemClock.elapsedRealtime();
        try { new File(getFilesDir(), UsbScreenCompareService.filename(mode)).delete(); }
        catch (Exception ignored) {}
        scanProgress.setVisibility(View.VISIBLE);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(0);
        stopScreenCapture.setVisibility(View.VISIBLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setKeepScreenOn(true);
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("screen_capture_running", true)
            .putString("screen_capture_mode", mode)
            .putLong("screen_capture_wall_start", System.currentTimeMillis()).apply();
        summary.setText("Capturing " + screenLabel(mode));
        details.setText("20-second sampling, read-only. Please keep the vehicle parked.");
        scanStatus.setText("Starting isolated USB capture...");
        screenWatchdog = () -> {
            if (screenCaptureRunning) stopScreenCapture("hard_timeout_70_seconds");
        };
        uiHandler.postDelayed(screenWatchdog, 70000);
        try {
            Intent i = new Intent(this, UsbScreenCompareService.class);
            i.putExtra(UsbScreenCompareService.EXTRA_MODE, mode);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else if (startService(i) == null) throw new IllegalStateException("Service was not started");
        } catch (Exception ex) {
            stopScreenCapture("unable_to_start_service");
            scanStatus.setText("USB capture startup error: " + ex.getClass().getSimpleName());
        }
    }

    private void resumeScreenCaptureIfNeeded() {
        boolean active = getPreferences(MODE_PRIVATE).getBoolean("screen_capture_running", false);
        if (!active && !screenCaptureRunning) return;
        if (!screenCaptureRunning) {
            screenCaptureMode = getPreferences(MODE_PRIVATE)
                    .getString("screen_capture_mode", UsbScreenCompareService.MODE_SYNC);
            long wallStart = getPreferences(MODE_PRIVATE)
                    .getLong("screen_capture_wall_start", System.currentTimeMillis());
            long elapsed = Math.max(0, System.currentTimeMillis() - wallStart);
            screenStartedAt = SystemClock.elapsedRealtime() - elapsed;
            screenCaptureRunning = true;
            stopScreenCapture.setVisibility(View.VISIBLE);
            scanProgress.setVisibility(View.VISIBLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setKeepScreenOn(true);
            if (screenWatchdog != null) uiHandler.removeCallbacks(screenWatchdog);
            screenWatchdog = () -> {
                if (screenCaptureRunning) stopScreenCapture("hard_timeout_70_seconds");
            };
            if (elapsed >= 70000) { stopScreenCapture("hard_timeout_70_seconds"); return; }
            uiHandler.postDelayed(screenWatchdog, 70000-elapsed);
        }
        JSONObject saved = readScreenCapture(screenCaptureMode);
        if (saved != null && !"in_progress".equals(saved.optString("status","in_progress"))) {
            finishScreenCapture(null);
        } else {
            scanStatus.setText("USB comparison capture continuing; screen stays awake. STOP is available.");
            if (saved != null) {
                summary.setText("Capturing " + screenLabel(screenCaptureMode));
                details.setText("Last operation: " + saved.optString("current_operation", "starting")
                    + "\nBytes: " + saved.optInt("bytes_received",0));
            }
        }
    }

    private void stopScreenCapture(String reason) {
        if (!screenCaptureRunning) return;
        int pid = screenWorkerPid;
        if (pid > 0 && pid != android.os.Process.myPid()) {
            try { android.os.Process.killProcess(pid); } catch (Exception ignored) {}
        } else {
            try { startService(new Intent(this,UsbScreenCompareService.class)
                    .setAction(UsbScreenCompareService.ACTION_STOP)); }
            catch (Exception ignored) {}
        }
        try { stopService(new Intent(this,UsbScreenCompareService.class)); }
        catch (Exception ignored) {}
        finishScreenCapture(reason);
    }

    private void finishScreenCapture(String stopReason) {
        if (!screenCaptureRunning) return;
        screenCaptureRunning=false;
        if (screenWatchdog != null) uiHandler.removeCallbacks(screenWatchdog);
        getPreferences(MODE_PRIVATE).edit().putBoolean("screen_capture_running",false).apply();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setKeepScreenOn(false);
        stopScreenCapture.setVisibility(View.GONE);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(100);
        JSONObject saved = readScreenCapture(screenCaptureMode);
        if (saved == null) saved = new JSONObject();
        try {
            saved.put("app_version","0.8.1");
            saved.put("user_selected_screen",screenCaptureMode);
            if (stopReason != null) {
                saved.put("status",stopReason);
                saved.put("interrupted_by_main_app",true);
                saved.put("last_operation_before_abort",
                        saved.optString("current_operation","unknown"));
            }
            saved.put("main_app_elapsed_ms",SystemClock.elapsedRealtime()-screenStartedAt);
        } catch (Exception ignored) {}
        persistScreenCapture(saved,screenCaptureMode);
        try { lastReport=saved.toString(2); }
        catch (Exception ignored) { lastReport=saved.toString(); }
        latestFilename = "vehicle-screen-" + screenCaptureMode + "-" + fileTimeUtc() + ".json";
        summary.setText(screenLabel(screenCaptureMode) + ": "
                + saved.optString("status","incomplete"));
        details.setText("Received: " + saved.optInt("bytes_received",0)
            + " bytes\nLast USB operation: " + saved.optString("current_operation","unknown")
            + "\nOutcome: " + saved.optString("outcome","unknown")
            + "\nRun BOTH screen tests and press 10 to compare.");
        scanStatus.setText("Saved " + screenLabel(screenCaptureMode)
            + " capture. EXPORT JSON REPORT exports this individual test.");
    }

    private static JSONObject metricsForScreen(JSONObject capture) {
        JSONObject m=new JSONObject();
        if (capture == null) return m;
        try {
            JSONObject analysis = capture.optJSONObject("protocol_analysis");
            JSONObject framing = analysis == null ? null : analysis.optJSONObject("framing_analysis");
            int bytes = capture.optInt("bytes_received",0);
            long millis = capture.optLong("actual_capture_ms",0L);
            m.put("label",capture.optString("user_selected_screen"));
            m.put("test_status",capture.optString("status"));
            m.put("capture_outcome",capture.optString("outcome"));
            m.put("captured_utc",capture.optString("started_utc"));
            m.put("total_received_bytes",bytes);
            m.put("saved_payload_bytes",capture.optInt("stored_bytes",0));
            m.put("capture_ms",millis);
            m.put("received_bytes_per_second",millis > 0
                ? Math.round(bytes*1000.0/millis*100.0)/100.0 : JSONObject.NULL);
            m.put("candidate_structured_frames",framing == null ? 0 : framing.optInt("candidate_frames",0));
            m.put("complete_structured_frames",framing == null ? 0 : framing.optInt("complete_frames",0));
            m.put("tls_like_headers",framing == null ? 0 : framing.optInt("tls_like_record_headers",0));
            m.put("entropy_bits_per_byte",analysis == null ? JSONObject.NULL :
                analysis.opt("shannon_entropy_bits_per_byte"));
            m.put("accessory_before",capture.optJSONObject("before"));
            m.put("accessory_after",capture.optJSONObject("after"));
            m.put("error_at_operation",capture.optString("error_at_operation",""));
        } catch (Exception ignored) {}
        return m;
    }

    private void showScreenTrafficComparison() {
        if (screenCaptureRunning || auditRunning) {
            scanStatus.setText("Please wait until the running audit finishes.");
            return;
        }
        JSONObject sync=readScreenCapture(UsbScreenCompareService.MODE_SYNC);
        JSONObject auto=readScreenCapture(UsbScreenCompareService.MODE_AUTO);
        if (sync==null || auto==null) {
            scanStatus.setText("Both screen tests are required: run 8 (SYNC home) and 9 (Android Auto).");
            return;
        }
        JSONObject result = new JSONObject();
        try {
            result.put("report_format","Vehicle USB screen traffic comparison summary v1");
            result.put("app_version","0.8.1");
            result.put("generated_utc",timeUtc());
            result.put("vehicle_screen_state_is_user_selected_not_verified",true);
            result.put("ford_sync_or_vehicle_ecu_access_confirmed",false);
            result.put("bytes_written",0);
            result.put("ford_sync_home",metricsForScreen(sync));
            result.put("android_auto_display",metricsForScreen(auto));
            JSONObject differences=new JSONObject();
            int syncBytes=sync.optInt("bytes_received");
            int autoBytes=auto.optInt("bytes_received");
            differences.put("total_bytes_difference_auto_minus_sync", autoBytes-syncBytes);
            JSONObject syncAnalysis=sync.optJSONObject("protocol_analysis");
            JSONObject autoAnalysis=auto.optJSONObject("protocol_analysis");
            JSONObject sf=syncAnalysis==null ? null : syncAnalysis.optJSONObject("framing_analysis");
            JSONObject af=autoAnalysis==null ? null : autoAnalysis.optJSONObject("framing_analysis");
            differences.put("structured_frame_count_difference_auto_minus_sync",
                (af==null?0:af.optInt("complete_frames"))-(sf==null?0:sf.optInt("complete_frames")));
            result.put("differences",differences);
            result.put("interpretation","Differences in accessory bytes and frame rates can correlate with user-selected screens, but do not identify decrypted content, confirm the display state, or demonstrate access to Ford SYNC internals.");
            result.put("individual_captures_note","The individual capture reports include raw hex; press each screen capture followed by EXPORT JSON REPORT if you intend to share them.");
        } catch (Exception ignored) {}
        try {lastReport=result.toString(2);}
        catch (Exception ignored) {lastReport=result.toString();}
        latestFilename="vehicle-usb-screen-comparison-"+fileTimeUtc()+".json";
        summary.setText("Screen comparison report ready");
        details.setText("Ford SYNC home: " + sync.optInt("bytes_received") + " bytes"
            + "\nAndroid Auto screen: " + auto.optInt("bytes_received") + " bytes"
            + "\nSYNC test outcome: " + sync.optString("outcome","not finished")
            + "\nAndroid Auto outcome: " + auto.optString("outcome","not finished")
            + "\nNo vehicle diagnostics or internal SYNC access confirmed.");
        scanStatus.setText("Comparison assembled. Choose where to save the JSON report.");
        exportJson();
    }


    private void showMarkerControls(boolean show) {
        int vis=show?View.VISIBLE:View.GONE;
        if(experimentMarkMaps!=null)experimentMarkMaps.setVisibility(vis);
        if(experimentMarkMusic!=null)experimentMarkMusic.setVisibility(vis);
        if(experimentMarkScreen!=null)experimentMarkScreen.setVisibility(vis);
        if(experimentMarkOther!=null)experimentMarkOther.setVisibility(vis);
    }

    private void confirmExperiment(String mode) {
        if(experimentRunning||auditRunning||screenCaptureRunning||
                scanActive||connectionActive) {
            scanStatus.setText("Finish the current USB test first.");
            return;
        }
        String display=UsbTrafficExperimentService.labelFor(mode);
        new AlertDialog.Builder(this)
            .setTitle(display+" — PARKED ONLY")
            .setMessage("This opt-in read-only test " +
                (UsbTrafficExperimentService.ACTIONS.equals(mode)
                    ?"runs for 65 seconds. After the 5-second warm-up, press a marker button just before performing an action in the car."
                    :"runs for 40 seconds: 5-second buffered traffic exclusion followed by three 10-second windows.") +
                "\n\nOpening the USB accessory may interrupt Android Auto. The tester does NOT read Ford ECUs or SYNC internals. Incoming bytes can be private. Keep parked and press STOP to cancel. Ready?")
            .setNegativeButton("Cancel",(d,w)->{})
            .setPositiveButton("Start test",(d,w)->startExperiment(mode))
            .show();
    }

    private JSONObject readExperiment(String mode) {
        try {
            File f=new File(getFilesDir(),UsbTrafficExperimentService.fileFor(mode));
            if(!f.exists())return null;
            return new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    StandardCharsets.UTF_8));
        }catch(Exception ignored){return null;}
    }

    private void saveExperiment(String mode,JSONObject value) {
        try {
            java.nio.file.Files.write(
                new File(getFilesDir(),UsbTrafficExperimentService.fileFor(mode)).toPath(),
                value.toString(2).getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){}
    }

    private void startExperiment(String mode) {
        if(experimentRunning||auditRunning||screenCaptureRunning||scanActive||connectionActive)
            return;
        experimentRunning=true;
        experimentMode=mode;
        experimentWorkerPid=0;
        experimentStartedAt=SystemClock.elapsedRealtime();
        try{new File(getFilesDir(),UsbTrafficExperimentService.fileFor(mode)).delete();}
        catch(Exception ignored){}
        scanProgress.setVisibility(View.VISIBLE);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(0);
        experimentStopButton.setVisibility(View.VISIBLE);
        showMarkerControls(UsbTrafficExperimentService.ACTIONS.equals(mode));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setKeepScreenOn(true);
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("v076_running",true)
            .putString("v076_mode",mode)
            .putLong("v076_start_wall",System.currentTimeMillis()).apply();
        summary.setText("Starting "+UsbTrafficExperimentService.labelFor(mode));
        details.setText("Do not drive. The first five seconds of USB data are excluded from steady-state comparisons.");
        scanStatus.setText("Starting isolated read-only USB experiment...");
        experimentWatchdog=()->{
            if(experimentRunning)stopExperiment("main_app_timeout_110_seconds");
        };
        uiHandler.postDelayed(experimentWatchdog,110000);
        try {
            Intent i=new Intent(this,UsbTrafficExperimentService.class);
            i.putExtra(UsbTrafficExperimentService.EXTRA_MODE,mode);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i);
            else if(startService(i)==null) throw new IllegalStateException("Worker not started");
        }catch(Exception ex) {
            stopExperiment("worker_start_failed");
            scanStatus.setText("Could not start controlled experiment: "
                    +ex.getClass().getSimpleName());
        }
    }

    private void sendActionMarker(String marker) {
        if(!experimentRunning || !UsbTrafficExperimentService.ACTIONS.equals(experimentMode)){
            scanStatus.setText("Start test 13 before marking an action.");
            return;
        }
        try {
            Intent i=new Intent(this,UsbTrafficExperimentService.class);
            i.setAction(UsbTrafficExperimentService.ACTION_MARK);
            i.putExtra(UsbTrafficExperimentService.EXTRA_MARK,marker);
            startService(i);
            scanStatus.setText("Marked "+marker+". You can perform the action now.");
        }catch(Exception ex){
            scanStatus.setText("Marker failed: "+ex.getClass().getSimpleName());
        }
    }

    private void resumeExperimentIfNeeded() {
        boolean stored=getPreferences(MODE_PRIVATE).getBoolean("v076_running",false);
        if(!experimentRunning&&!stored)return;
        if(!experimentRunning) {
            experimentMode=getPreferences(MODE_PRIVATE).getString("v076_mode",
                    UsbTrafficExperimentService.SYNC);
            long wall=getPreferences(MODE_PRIVATE).getLong("v076_start_wall",
                    System.currentTimeMillis());
            long age=Math.max(0,System.currentTimeMillis()-wall);
            experimentStartedAt=SystemClock.elapsedRealtime()-age;
            experimentRunning=true;
            experimentStopButton.setVisibility(View.VISIBLE);
            showMarkerControls(UsbTrafficExperimentService.ACTIONS.equals(experimentMode));
            scanProgress.setVisibility(View.VISIBLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setKeepScreenOn(true);
            if(experimentWatchdog!=null)uiHandler.removeCallbacks(experimentWatchdog);
            experimentWatchdog=()->{
                if(experimentRunning)stopExperiment("main_app_timeout_110_seconds");
            };
            if(age>=110000) {
                stopExperiment("timeout_before_return");
                return;
            }
            uiHandler.postDelayed(experimentWatchdog,110000-age);
        }
        JSONObject saved=readExperiment(experimentMode);
        if(saved!=null&&!"in_progress".equals(saved.optString("status","in_progress"))) {
            finishExperiment(null);
        }else{
            scanStatus.setText("Controlled experiment continues in a foreground service.");
            summary.setText(UsbTrafficExperimentService.labelFor(experimentMode));
            if(saved!=null)details.setText(
                "Last USB operation: "+saved.optString("current_operation","starting")
                +"\nCaptured bytes so far: "+saved.optInt("bytes_received_so_far",0));
        }
    }

    private void stopExperiment(String reason) {
        if(!experimentRunning)return;
        int pid=experimentWorkerPid;
        if(pid>0&&pid!=android.os.Process.myPid()) {
            try{android.os.Process.killProcess(pid);}catch(Exception ignored){}
        }else{
            try{startService(new Intent(this,UsbTrafficExperimentService.class)
                .setAction(UsbTrafficExperimentService.ACTION_STOP));}
            catch(Exception ignored){}
        }
        try{stopService(new Intent(this,UsbTrafficExperimentService.class));}
        catch(Exception ignored){}
        finishExperiment(reason);
    }

    private void finishExperiment(String forcedReason) {
        if(!experimentRunning)return;
        experimentRunning=false;
        if(experimentWatchdog!=null)uiHandler.removeCallbacks(experimentWatchdog);
        getPreferences(MODE_PRIVATE).edit().putBoolean("v076_running",false).apply();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setKeepScreenOn(false);
        experimentStopButton.setVisibility(View.GONE);
        showMarkerControls(false);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(100);
        JSONObject saved=readExperiment(experimentMode);
        if(saved==null)saved=new JSONObject();
        try {
            saved.put("app_version","0.8.1");
            saved.put("main_app_elapsed_ms",
                SystemClock.elapsedRealtime()-experimentStartedAt);
            if(forcedReason!=null){
                saved.put("status",forcedReason);
                saved.put("terminated_by_main_process",true);
                saved.put("last_operation_before_abort",
                    saved.optString("current_operation","unknown"));
            }
        }catch(Exception ignored){}
        saveExperiment(experimentMode,saved);
        try{lastReport=saved.toString(2);}
        catch(Exception ignored){lastReport=saved.toString();}
        latestFilename="vehicle-v076-"+experimentMode+"-"+fileTimeUtc()+".json";
        summary.setText(UsbTrafficExperimentService.labelFor(experimentMode)
                +": "+saved.optString("status","unknown"));
        JSONObject analysis=saved.optJSONObject("analysis");
        JSONObject steady=analysis==null?null:analysis.optJSONObject("steady_total_30_seconds");
        details.setText("Test outcome: "+saved.optString("outcome","unknown")
            +"\nTotal bytes: "+saved.optInt("total_bytes_including_warmup",0)
            +"\nSteady 30-sec bytes: "+(steady==null?0:steady.optInt("bytes_received",0))
            +"\nLast USB operation: "+saved.optString("current_operation","unknown")
            +"\nUse EXPORT JSON REPORT to save this detailed test.");
        scanStatus.setText("Experiment saved. Export JSON for analysis.");
    }

    private void compareExperiments() {
        if(experimentRunning||auditRunning||screenCaptureRunning) {
            scanStatus.setText("Finish the current test first.");
            return;
        }
        JSONObject sync=readExperiment(UsbTrafficExperimentService.SYNC);
        JSONObject auto=readExperiment(UsbTrafficExperimentService.AUTO);
        if(sync==null||auto==null) {
            scanStatus.setText("Run BOTH tests 11 and 12 before comparing.");
            return;
        }
        JSONObject comparisonData=new JSONObject();
        try{
            comparisonData.put("report_format","Vehicle controlled USB steady-state comparison v1");
            comparisonData.put("app_version","0.8.1");
            comparisonData.put("created_at_utc",timeUtc());
            comparisonData.put("ford_sync_user_selected",true);
            comparisonData.put("android_auto_user_selected",true);
            comparisonData.put("ford_screen_state_independently_verified",false);
            comparisonData.put("ford_sync_internal_access_confirmed",false);
            comparisonData.put("vehicle_diagnostic_data_confirmed",false);
            JSONObject sa=sync.optJSONObject("analysis");
            JSONObject aa=auto.optJSONObject("analysis");
            JSONObject sm=sa==null?new JSONObject():sa.optJSONObject("steady_total_30_seconds");
            JSONObject am=aa==null?new JSONObject():aa.optJSONObject("steady_total_30_seconds");
            int sb=sm==null?0:sm.optInt("bytes_received",0);
            int ab=am==null?0:am.optInt("bytes_received",0);
            comparisonData.put("sync_status",sync.optString("status"));
            comparisonData.put("auto_status",auto.optString("status"));
            comparisonData.put("ford_sync_steady_30sec",sm);
            comparisonData.put("android_auto_steady_30sec",am);
            comparisonData.put("difference_auto_minus_sync_bytes",ab-sb);
            comparisonData.put("sync_repeated_windows",sa==null?new JSONArray():
                sa.optJSONArray("steady_windows"));
            comparisonData.put("auto_repeated_windows",aa==null?new JSONArray():
                aa.optJSONArray("steady_windows"));
            JSONObject actions=readExperiment(UsbTrafficExperimentService.ACTIONS);
            if(actions!=null){
                comparisonData.put("optional_action_markers",actions.optJSONArray("user_action_markers"));
                JSONObject an=actions.optJSONObject("analysis");
                comparisonData.put("optional_action_before_after_rates",
                    an==null?new JSONArray():an.optJSONArray("action_comparisons"));
            }
            comparisonData.put("interpretation","These measurements reflect Android USB accessory traffic after excluding initial buffering. Differences are associations only, not evidence of decrypted Ford SYNC or vehicle diagnostic data.");
        }catch(Exception ignored){}
        try{lastReport=comparisonData.toString(2);}
        catch(Exception ignored){lastReport=comparisonData.toString();}
        latestFilename="vehicle-v076-steady-screen-comparison-"+fileTimeUtc()+".json";
        summary.setText("v0.8.1 Steady-state comparison assembled");
        details.setText("Both 40-second experiments have saved statistics. "
            +"The first 5 seconds are excluded; each screen has three 10-second measurement windows. "
            +"The combined JSON includes action markers if test 13 has been run.");
        scanStatus.setText("Choose a file location to export your comparison.");
        exportJson();
    }


    private JSONObject readFullAutoReport(){
        try{
            File file=new File(getFilesDir(),FULL_REPORT);
            if(!file.exists())return null;
            byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());
            return new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        }catch(Exception ignored){return null;}
    }


    private void beginFullScanWithPermissions(){
        if(fullAutoRunning||auditRunning||experimentRunning||screenCaptureRunning||
                scanActive||connectionActive){
            scanStatus.setText("Stop the current test before starting another scan.");
            return;
        }
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("v081_scan_waiting_permissions",true)
            .putBoolean("v081_usb_prompted",false)
            .putBoolean("v081_bt_prompted",false)
            .putLong("v081_usb_request_epoch",0).apply();
        scanStatus.setText("Checking USB and Bluetooth permissions before scanning.");
        continueFullScanPermissionSetup();
    }

    private void continueFullScanPermissionSetup(){
        if(!getPreferences(MODE_PRIVATE).getBoolean("v081_scan_waiting_permissions",false))return;
        try{
            UsbManager usb=(UsbManager)getSystemService(Context.USB_SERVICE);
            UsbAccessory[] accessories=usb==null?null:usb.getAccessoryList();
            boolean attached=accessories!=null&&accessories.length>0;
            if(attached&&!usb.hasPermission(accessories[0])){
                boolean requested=getPreferences(MODE_PRIVATE)
                    .getBoolean("v081_usb_prompted",false);
                if(!requested){
                    Intent responseIntent=new Intent(UsbPermissionReceiver.ACTION);
                    responseIntent.setComponent(new ComponentName(this,UsbPermissionReceiver.class));
                    PendingIntent response=PendingIntent.getBroadcast(this,8081,responseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
                    getPreferences(MODE_PRIVATE).edit()
                        .putBoolean("v081_usb_prompted",true)
                        .putLong("v081_usb_request_epoch",System.currentTimeMillis()).apply();
                    scanStatus.setText("Approve Android's USB-access prompt to record the actual data.");
                    usb.requestPermission(accessories[0],response);
                    return;
                }
                long requestedAt=getPreferences(MODE_PRIVATE)
                    .getLong("v081_usb_request_epoch",0);
                long answeredAt=getSharedPreferences("usb_permission_results",MODE_PRIVATE)
                    .getLong("last_checked_at_ms",0);
                if(answeredAt<requestedAt &&
                        System.currentTimeMillis()-requestedAt<90000){
                    scanStatus.setText("Waiting for Android USB permission response...");
                    return;
                }
                scanStatus.setText("USB data permission not approved; the scan will mark traffic unavailable.");
            }
            if(Build.VERSION.SDK_INT>=31 &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    !=PackageManager.PERMISSION_GRANTED &&
                !getPreferences(MODE_PRIVATE).getBoolean("v081_bt_prompted",false)){
                getPreferences(MODE_PRIVATE).edit().putBoolean("v081_bt_prompted",true).apply();
                scanStatus.setText("Approve Bluetooth permission to check for paired OBD-II adapters.");
                requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_SCAN_BLUETOOTH);
                return;
            }
        }catch(Exception ex){
            scanStatus.setText("Some permission checks unavailable ("+
                ex.getClass().getSimpleName()+"). Running supported parts of scan.");
        }
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("v081_scan_waiting_permissions",false).apply();
        startFullAuto();
    }

    @Override public void onRequestPermissionsResult(int code,String[] permissions,
            int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==REQ_SCAN_BLUETOOTH&&
                getPreferences(MODE_PRIVATE).getBoolean("v081_scan_waiting_permissions",false)){
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)
                scanStatus.setText("Bluetooth discovery permission granted.");
            else
                scanStatus.setText("Bluetooth permission declined: adapter check will be marked skipped.");
            getPreferences(MODE_PRIVATE).edit()
                .putBoolean("v081_scan_waiting_permissions",false).apply();
            startFullAuto();
        }
    }

    private void startFullAuto(){
        if(fullAutoRunning||auditRunning||experimentRunning||screenCaptureRunning||
                scanActive||connectionActive){
            scanStatus.setText("Stop or finish another USB test before starting.");
            return;
        }
        fullAutoRunning=true;
        fullAutoPid=0;
        fullAutoStarted=SystemClock.elapsedRealtime();
        try{new File(getFilesDir(),FULL_REPORT).delete();}catch(Exception ignored){}
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("v077_oneclick_running",true)
            .putLong("v077_oneclick_start_wall",System.currentTimeMillis()).apply();
        stopFullAutoButton.setVisibility(View.VISIBLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setKeepScreenOn(true);
        scanProgress.setVisibility(View.VISIBLE);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(0);
        summary.setText("ONE-CLICK AUDIT — STARTING");
        details.setText("Runs automatically; no additional buttons needed. The report will be saved in Downloads/VehiclePortTester.");
        scanStatus.setText("Starting read-only full vehicle USB audit...");
        fullAutoWatchdog=()->{
            if(fullAutoRunning)stopFullAutoWithReason("ui_hard_timeout_160_seconds");
        };
        uiHandler.postDelayed(fullAutoWatchdog,160000);
        try{
            Intent i=new Intent(this,OneClickAuditService.class);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);
            else if(startService(i)==null)throw new IllegalStateException("Service did not start");
        }catch(Exception e){
            stopFullAutoWithReason("service_start_failed");
            scanStatus.setText("Full audit could not start: "+e.getClass().getSimpleName());
        }
    }

    private void resumeFullAutoIfNeeded(){
        boolean saved=getPreferences(MODE_PRIVATE).getBoolean("v077_oneclick_running",false);
        if(!saved&&!fullAutoRunning)return;
        if(!fullAutoRunning){
            long wall=getPreferences(MODE_PRIVATE).getLong(
                "v077_oneclick_start_wall",System.currentTimeMillis());
            long elapsed=Math.max(0,System.currentTimeMillis()-wall);
            fullAutoStarted=SystemClock.elapsedRealtime()-elapsed;
            fullAutoRunning=true;
            stopFullAutoButton.setVisibility(View.VISIBLE);
            scanProgress.setVisibility(View.VISIBLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setKeepScreenOn(true);
            if(fullAutoWatchdog!=null)uiHandler.removeCallbacks(fullAutoWatchdog);
            if(elapsed<160000){
                fullAutoWatchdog=()->{
                    if(fullAutoRunning)stopFullAutoWithReason("ui_hard_timeout_160_seconds");
                };
                uiHandler.postDelayed(fullAutoWatchdog,160000-elapsed);
            }else{
                stopFullAutoWithReason("timeout_on_return_to_app");
                return;
            }
        }
        JSONObject file=readFullAutoReport();
        if(file!=null&&!"in_progress".equals(file.optString("status","in_progress"))
            &&(file.has("completed_utc")||file.optBoolean("auto_download_success",false))){
            showCompletedFullAuto(null);
        }else{
            scanStatus.setText("The full automatic USB audit continues in the background.");
        }
    }

    private void stopFullAuto(){
        stopFullAutoWithReason("stopped_by_user");
    }

    private void stopFullAutoWithReason(String reason){
        if(!fullAutoRunning)return;
        int pid=fullAutoPid;
        if(pid>0&&pid!=android.os.Process.myPid()){
            try{android.os.Process.killProcess(pid);}catch(Exception ignored){}
        }else{
            try{startService(new Intent(this,OneClickAuditService.class)
                .setAction(OneClickAuditService.STOP));}
            catch(Exception ignored){}
        }
        try{stopService(new Intent(this,OneClickAuditService.class));}
        catch(Exception ignored){}
        showCompletedFullAuto(reason);
    }

    private void showCompletedFullAuto(String forced){
        if(!fullAutoRunning)return;
        fullAutoRunning=false;
        if(fullAutoWatchdog!=null)uiHandler.removeCallbacks(fullAutoWatchdog);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setKeepScreenOn(false);
        getPreferences(MODE_PRIVATE).edit().putBoolean("v077_oneclick_running",false).apply();
        stopFullAutoButton.setVisibility(View.GONE);
        scanProgress.setIndeterminate(false);
        scanProgress.setProgress(100);
        JSONObject file=readFullAutoReport();
        if(file==null)file=new JSONObject();
        if(forced!=null){
            try{
                file.put("status",forced);
                file.put("partial_checkpoint_preserved",true);
                java.nio.file.Files.write(new File(getFilesDir(),FULL_REPORT).toPath(),
                    file.toString(2).getBytes(StandardCharsets.UTF_8));
            }catch(Exception ignored){}
        }
        renderFullAuto(file);
        if(forced==null && file.optJSONObject("capability_map")!=null) {
            startActivity(new Intent(this,CapabilityResultsActivity.class));
        }
    }

    private void renderFullAuto(JSONObject file){
        try{lastReport=file.toString(2);}
        catch(Exception ignored){lastReport=file.toString();}
        latestFilename="Vehicle-Capability-Explorer-v0.8.1-FULL-SCAN-"+fileTimeUtc()+".json";
        JSONObject read=file.optJSONObject("usb_stream_and_steady_analysis");
        JSONObject rate=read==null?null:read.optJSONObject("all_45s_steady");
        summary.setText("VEHICLE CAPABILITY SCAN: "+file.optString("status","unknown"));
        details.setText("Received USB bytes: "+file.optInt("total_usb_bytes",0)
            +"\nSteady data rate: "+(rate==null?"not available":rate.optString("bytes_per_second","not available"))+" B/s"
            +"\nUSB capture: "+(read==null?"not performed":read.optString("outcome","unknown"))
            +"\nFord internal/ECU access: NOT established"
            +"\nAuto-saved: "+file.optBoolean("auto_download_success",false)
            +"\n"+file.optString("saved_downloads_path",
                    "The full report remains available in the app; tap EXPORT JSON REPORT if not in Downloads."));
        scanStatus.setText(file.optBoolean("auto_download_success",false)
            ?"Complete JSON saved automatically in Downloads/VehiclePortTester."
            :"Full audit result preserved. Check Downloads or tap EXPORT JSON REPORT.");
    }

    private void loadLastFullAuto(){
        JSONObject file=readFullAutoReport();
        if(file==null){
            scanStatus.setText("No one-click full audit report saved yet.");
            return;
        }
        renderFullAuto(file);
        if(file.optJSONObject("capability_map")!=null)
            startActivity(new Intent(this,CapabilityResultsActivity.class));
        else
            scanStatus.setText("Report predates capability map. Press SCAN EVERYTHING to generate new results.");
    }

    private void exportJson() {
        if (lastReport.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, latestFilename.isEmpty() ? "vehicle-usbc-report.json" : latestFilename);
        startActivityForResult(intent, EXPORT_JSON);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == EXPORT_JSON && result == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("No output stream");
                out.write(lastReport.getBytes(StandardCharsets.UTF_8));
                footer.setText("JSON report exported successfully. No vehicle data was modified.");
            } catch (Exception ex) {
                footer.setText("Export failed: " + ex.getMessage());
            }
        }
    }

    private void shareReport() {
        if (lastReport.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Vehicle USB-C Connection Report");
        intent.putExtra(Intent.EXTRA_TEXT, lastReport);
        startActivity(Intent.createChooser(intent, "Share connection report"));
    }
}
