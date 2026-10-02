package com.nikonautoupload;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Compact, auto-refreshing dashboard shown on the Home screen. */
public final class ConnectionStatusDashboard extends LinearLayout {
    private final SharedPreferences p;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final int panel=Color.rgb(16,23,31), line=Color.rgb(42,54,66), white=Color.rgb(245,247,250), muted=Color.rgb(155,166,178), green=Color.rgb(55,206,108), amber=Color.rgb(255,193,7), red=Color.rgb(239,83,80), blue=Color.rgb(33,150,243);
    private final TextView header,camera,internet,transfer,upload,details,toggle;
    private boolean expanded=false;

    private final Runnable refreshTask=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,1500);}};

    public ConnectionStatusDashboard(Context context, SharedPreferences prefs){
        super(context);p=prefs;setOrientation(VERTICAL);setPadding(dp(14),dp(12),dp(14),dp(10));setBackground(card(panel,16,line,1));
        header=text("CONNECTION STATUS",12,blue,true);addView(header);
        camera=text("",16,white,true);camera.setPadding(0,dp(6),0,0);addView(camera);
        internet=text("",14,white,false);addView(internet);
        transfer=text("",14,white,false);addView(transfer);
        upload=text("",14,white,false);addView(upload);
        details=text("",12,muted,false);details.setPadding(0,dp(4),0,dp(4));details.setVisibility(GONE);
        toggle=text("SHOW DETAILS  ▾",12,blue,true);toggle.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);toggle.setPadding(0,dp(8),0,dp(6));toggle.setOnClickListener(v->{expanded=!expanded;details.setVisibility(expanded?VISIBLE:GONE);toggle.setText(expanded?"HIDE DETAILS  ▴":"SHOW DETAILS  ▾");});addView(toggle);
        addView(details);
        refresh();
    }

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();handler.removeCallbacks(refreshTask);handler.post(refreshTask);}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(refreshTask);super.onDetachedFromWindow();}

    public void refresh(){
        AppStatusHub.Snapshot s=AppStatusHub.snapshot(getContext(),p);
        int overall=color(s.overallColor);setBackground(card(panel,16,overall,1));
        camera.setText("●  "+s.cameraModel+" — "+(s.cameraConnected?s.connectionType+" Connected":"Disconnected"));camera.setTextColor(color(s.cameraColor));
        internet.setText("●  Internet — "+s.internet);internet.setTextColor(color(s.internetColor));
        String transferText="●  Transfer — "+s.transfer;
        if(s.transferTotal>0)transferText+=" • "+s.transferCurrent+"/"+s.transferTotal+" Photos";
        if(s.transferSpeedBps>0)transferText+=" • "+formatSpeed(s.transferSpeedBps);
        transfer.setText(transferText);transfer.setTextColor(color(s.transferColor));
        String uploadText="●  "+s.uploadDestination+" — "+s.uploadState;
        if(s.waitingUploads>0)uploadText+=" • "+s.waitingUploads+" Remaining";
        upload.setText(uploadText);upload.setTextColor(color(s.uploadColor));

        StringBuilder d=new StringBuilder();
        d.append("Camera: ").append(s.cameraModel).append('\n');
        d.append("Connection type: ").append(s.connectionType).append('\n');
        d.append("Wi-Fi / network: ").append(s.network).append('\n');
        d.append("Phone IP: ").append(s.ipAddress).append('\n');
        String cameraIp=p.getString("nikon_handoff_camera_ip","");if(!cameraIp.isEmpty())d.append("Camera IP: ").append(cameraIp).append('\n');
        d.append("Internet: ").append(s.internet).append('\n');
        d.append("Cellular/data: ").append(s.cellular).append('\n');
        d.append("Transfer: ").append(s.transfer);
        if(!s.transferPhoto.isEmpty())d.append(" • ").append(s.transferPhoto);
        if(s.transferSpeedBps>0)d.append(" • ").append(formatSpeed(s.transferSpeedBps));
        d.append('\n');
        d.append("Upload queue: waiting ").append(s.waitingUploads).append(" • uploading ").append(s.uploadingUploads).append(" • completed ").append(s.completedUploads).append(" • failed ").append(s.failedUploads).append('\n');
        d.append("Destination: ").append(s.uploadDestination).append(" • ").append(s.uploadState);
        if(!s.internetAvailable&&s.cameraConnected&&s.connectionType.startsWith("USB-C"))d.append("\n\nUSB-C importing is still working offline. Flickr uploads will remain queued until internet returns.");
        details.setText(d.toString());
    }

    private int color(int code){return code==AppStatusHub.GREEN?green:code==AppStatusHub.YELLOW?amber:red;}
    private String formatSpeed(long bps){double v=bps;String[]u={"B/s","KB/s","MB/s","GB/s"};int i=0;while(v>=1024&&i<u.length-1){v/=1024;i++;}return String.format(Locale.US,i>=2?"%.1f %s":"%.0f %s",v,u[i]);}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(0,1.08f);return t;}
    private GradientDrawable card(int fill,int radius,int stroke,int width){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(width>0)d.setStroke(dp(width),stroke);return d;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
