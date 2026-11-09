package com.newswire.vehicleporttester;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Read-only Android Bluetooth AUDIO profiles, not Ford's diagnostic network. */
final class BluetoothLinkPanel {
    static final int PERMISSION_REQUEST=804;
    private final Activity activity;
    private final BluetoothAdapter adapter;
    private final LinearLayout panel;
    private final TextView status;
    private final JSONArray connected=new JSONArray();
    private int requests=0,profilesReturned=0;
    private String lastStatus="not_scanned";
    BluetoothLinkPanel(Activity context){
        activity=context;
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter=manager==null?null:manager.getAdapter();
        panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);
        int pd=(int)(activity.getResources().getDisplayMetrics().density*14+0.5f);
        panel.setPadding(pd,pd,pd,pd);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff132b3a);
        bg.setCornerRadius(pd);bg.setStroke(1,0xff2d5164);panel.setBackground(bg);
        panel.addView(text("BLUETOOTH AUDIO CONNECTION TEST",14,0xff48d9ee,true));
        status=text("Tap CHECK to identify current audio profiles. This does not test Ford ECUs.",12,0xffaac0ce,false);
        panel.addView(status);
        Button b=new Button(activity);b.setText("CHECK CONNECTED BLUETOOTH AUDIO");
        b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff245d74));
        panel.addView(b,new LinearLayout.LayoutParams(-1,pd*4));
        b.setOnClickListener(v->check());
    }
    LinearLayout view(){return panel;}
    private TextView text(String s,int sz,int color,boolean bold){
        TextView t=new TextView(activity);t.setText(s);t.setTextSize(sz);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    void check(){
        if(adapter==null){lastStatus="no_bluetooth_adapter";status.setText("Bluetooth hardware not available.");return;}
        if(android.os.Build.VERSION.SDK_INT>=31&&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            lastStatus="permission_needed";
            status.setText("Bluetooth permission required to identify connected devices.");
            activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},PERMISSION_REQUEST);
            return;
        }
        try{
            if(!adapter.isEnabled()){lastStatus="bluetooth_off";status.setText("Bluetooth is off.");return;}
            requests++;profilesReturned=0;
            while(connected.length()>0)connected.remove(0);
            lastStatus="waiting_for_android_audio_profiles";
            status.setText("Checking phone audio connections (A2DP / headset)...");
            query(BluetoothProfile.A2DP);
            query(BluetoothProfile.HEADSET);
        }catch(SecurityException e){lastStatus="permission_denied";status.setText("Bluetooth permission denied.");}
        catch(Exception e){lastStatus="bluetooth_check_error";status.setText("Bluetooth audio inspection unavailable.");}
    }
    private void query(int profile){
        boolean started=adapter.getProfileProxy(activity,new BluetoothProfile.ServiceListener(){
            @Override public void onServiceConnected(int which,BluetoothProfile proxy){
                try{
                    profilesReturned++;
                    List<BluetoothDevice> devices=proxy.getConnectedDevices();
                    if(devices!=null)for(BluetoothDevice device:devices){
                        String name=device.getName();
                        if(name==null||name.trim().isEmpty())name="Unnamed Bluetooth audio device";
                        connected.put(new JSONObject()
                            .put("name",name)
                            .put("profile",which==BluetoothProfile.A2DP?"A2DP audio":"HEADSET handsfree")
                            .put("source","Android connected Bluetooth audio profile"));
                    }
                    lastStatus="audio_profiles_checked";
                    status.setText(connected.length()==0?
                        "No connected phone Bluetooth audio devices reported.":
                        "Connected AUDIO profiles:\n"+friendly());
                }catch(Exception e){lastStatus="profile_read_failed";status.setText("Audio connection permission or profile error.");}
                finally{try{adapter.closeProfileProxy(which,proxy);}catch(Exception ignored){}}
            }
            @Override public void onServiceDisconnected(int which){}
        },profile);
        if(!started){lastStatus="audio_profile_unavailable";status.setText("Bluetooth audio profile unavailable.");}
    }
    private String friendly(){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<connected.length();i++){
            JSONObject entry=connected.optJSONObject(i);
            if(entry!=null)s.append("• ").append(entry.optString("name")).append(" (")
                .append(entry.optString("profile")).append(")\n");
        }
        return s.toString();
    }
    JSONObject snapshot(){
        JSONObject j=new JSONObject();
        try{
            j.put("scan_status",lastStatus);
            j.put("active_bluetooth_audio_connections",new JSONArray(connected.toString()));
            j.put("profile_callbacks_received",profilesReturned);
            j.put("checks_requested",requests);
            j.put("profile_data_are_phone_audio_only",true);
            j.put("ford_vehicle_diagnostics_confirmed",false);
            j.put("note","Device audio name cannot establish a connected vehicle diagnostic protocol.");
        }catch(Exception ignored){}
        return j;
    }
}
