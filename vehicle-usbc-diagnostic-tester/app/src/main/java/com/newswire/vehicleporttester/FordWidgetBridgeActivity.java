package com.newswire.vehicleporttester;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owner-authorized Android widget HOST for Ford's publicly installed home widget.
 *
 * This does not call Ford's private API, access Ford app storage, collect account
 * credentials, or claim to provide live cloud readings. Only TextViews in the
 * specific user-approved Ford widget hosted in this app are examined.
 *
 * Whether Ford renders fuel/range as accessible TextViews can only be confirmed
 * by an on-device test; image-only/canvas widget content is not parsed.
 */
public final class FordWidgetBridgeActivity extends Activity {
    static final String PREF = "ford_widget_bridge_v097";
    private static final String FORD_PACKAGE = "com.ford.fordpass";
    private static final int HOST_ID = 97631;
    private static final int REQ_BIND = 9761;
    private static final int REQ_CONFIG = 9762;
    private static final int BG = 0xff081421;
    private static final int WHITE = 0xffeef6fd;
    private static final int MUTED = 0xffa6bbc9;
    private static final int GREEN = 0xff67e0ae;
    private static final Pattern RANGE = Pattern.compile("(?i)\\b(\\d{1,4})\\s*(?:mi|miles)\\b");
    private static final Pattern PERCENT = Pattern.compile("\\b(\\d{1,3})\\s*%");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable monitor = new Runnable(){
        @Override public void run(){
            if(hostView != null && !isFinishing()){
                inspectWidget(false);
                handler.postDelayed(this,5000);
            }
        }
    };
    private AppWidgetManager widgetManager;
    private BridgeHost host;
    private AppWidgetHostView hostView;
    private AppWidgetProviderInfo pendingInfo;
    private int pendingId=-1;
    private LinearLayout widgetFrame;
    private TextView state, results;
    private boolean listening=false;
    private int previousViewCount=-1;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(color);t.setPadding(0,dp(7),0,dp(7));return t;
    }
    private Button button(String label,int color){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));return b;
    }
    private SharedPreferences prefs(){return getSharedPreferences(PREF,MODE_PRIVATE);}
    @Override public void onCreate(Bundle stateBundle){
        super.onCreate(stateBundle);
        getWindow().setStatusBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        widgetManager=AppWidgetManager.getInstance(this);
        host=new BridgeHost(this,HOST_ID);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(18),dp(18),dp(28));
        scroll.addView(body);setContentView(scroll);
        body.addView(text("FORD WIDGET BRIDGE • v0.9.7",23,0xff4fd6ec));
        body.addView(text("Read your installed Ford home-screen widget with Android's normal widget-host permission. No OBD-II adapter and no Ford password required.",15,WHITE));
        body.addView(text("This is a TEST: Ford may draw the widget as graphics instead of exposing fuel and range as text. In that case the app will report the limitation rather than invent readings.",13,0xffffc888));
        Button connect=button("1. CONNECT MY FORD WIDGET",0xff13896c);
        body.addView(connect);
        connect.setOnClickListener(v->connect());
        Button read=button("2. READ FUEL & RANGE FROM WIDGET",0xff186d92);
        body.addView(read);
        read.setOnClickListener(v->inspectWidget(true));
        Button reset=button("DISCONNECT FORD WIDGET BRIDGE",0xff5a4f65);
        body.addView(reset);
        reset.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Disconnect Ford widget?")
                .setMessage("This removes only Vehicle Explorer's widget permission. Your Ford home-screen widget remains unchanged. The saved snapshot will also be cleared.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Disconnect",(d,w)->disconnect()).show());
        state=text("Waiting for Ford widget setup.",16,GREEN);body.addView(state);
        results=text("No widget reading captured.",14,WHITE);body.addView(results);
        body.addView(text("APPROVED FORD WIDGET PREVIEW",17,0xff4fd6ec));
        widgetFrame=new LinearLayout(this);
        widgetFrame.setOrientation(LinearLayout.VERTICAL);
        widgetFrame.setMinimumHeight(dp(160));
        body.addView(widgetFrame,new LinearLayout.LayoutParams(-1,-2));
        body.addView(text("Only the chosen Ford widget is examined. No reading of other apps' screens, messages, passwords, or files. Values stay on this phone, then appear as a saved snapshot under Vehicle Explorer's SYNC 3 menu.",13,MUTED));
        restoreWidget();
        updateUi();
    }
    private void connect(){
        if(hostView!=null){inspectWidget(true);return;}
        List<AppWidgetProviderInfo> providers=new ArrayList<>();
        try{
            for(AppWidgetProviderInfo info:widgetManager.getInstalledProvidersForPackage(FORD_PACKAGE,null)){
                if(info!=null && info.provider!=null && FORD_PACKAGE.equals(info.provider.getPackageName()))
                    providers.add(info);
            }
            if(providers.isEmpty()){
                for(AppWidgetProviderInfo info:widgetManager.getInstalledProviders()){
                    if(info!=null && info.provider!=null &&
                        FORD_PACKAGE.equals(info.provider.getPackageName()))providers.add(info);
                }
            }
        }catch(Exception e){
            setState("PROVIDER_DISCOVERY_ERROR",e.getClass().getSimpleName());
            updateUi();return;
        }
        prefs().edit().putInt("provider_count",providers.size()).apply();
        if(providers.isEmpty()){
            setState("FORD_WIDGET_PROVIDER_NOT_FOUND",
                "No Ford widget provider exposed by Android. Check the Ford app is installed and updated.");
            updateUi();return;
        }
        if(providers.size()==1){choose(providers.get(0));return;}
        String[] labels=new String[providers.size()];
        for(int i=0;i<providers.size();i++){
            AppWidgetProviderInfo info=providers.get(i);
            CharSequence name=info.loadLabel(getPackageManager());
            labels[i]=(name==null?"Ford widget":name.toString())+" ("+info.provider.getShortClassName()+")";
        }
        new AlertDialog.Builder(this).setTitle("Choose a Ford widget")
            .setItems(labels,(d,which)->choose(providers.get(which)))
            .setNegativeButton("Cancel",null).show();
    }
    private void choose(AppWidgetProviderInfo info){
        pendingInfo=info;
        pendingId=host.allocateAppWidgetId();
        try{
            if(widgetManager.bindAppWidgetIdIfAllowed(pendingId,info.provider)){
                configureIfNeeded(info,pendingId);
            }else{
                Intent bind=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingId);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider);
                setState("AWAITING_OWNER_WIDGET_PERMISSION",
                    "Approve Android's request to display the Ford widget inside Vehicle Explorer.");
                updateUi();
                startActivityForResult(bind,REQ_BIND);
            }
        }catch(Exception error){
            setState("WIDGET_BIND_ERROR",error.getClass().getSimpleName());
            clearPending();updateUi();
        }
    }
    private void configureIfNeeded(AppWidgetProviderInfo info,int widgetId){
        try{
            if(info.configure!=null){
                setState("WIDGET_CONFIGURATION_REQUIRED","Complete Ford's widget setup screen.");
                updateUi();
                host.startAppWidgetConfigureActivityForResult(this,widgetId,0,REQ_CONFIG,null);
            }else{
                commitBoundWidget(widgetId);
            }
        }catch(Exception ex){
            setState("CONFIGURATION_UNAVAILABLE",ex.getClass().getSimpleName());
            // Configuration was not completed; do not claim the widget is connected.
            clearPending();updateUi();
        }
    }
    private void commitBoundWidget(int widgetId){
        if(widgetManager.getAppWidgetInfo(widgetId)==null){
            setState("WIDGET_NOT_BOUND","Android did not confirm the Ford widget binding.");
            clearPending();updateUi();return;
        }
        prefs().edit().putInt("widget_id",widgetId).putString("provider",
                widgetManager.getAppWidgetInfo(widgetId).provider.flattenToString())
                .putString("status","BOUND_NOT_YET_READ").apply();
        pendingId=-1;pendingInfo=null;
        showWidget(widgetId);
    }
    private void restoreWidget(){
        int widgetId=prefs().getInt("widget_id",-1);
        if(widgetId<=0)return;
        AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(widgetId);
        if(info==null||info.provider==null||!FORD_PACKAGE.equals(info.provider.getPackageName())){
            prefs().edit().remove("widget_id").putString("status","WIDGET_BINDING_LOST").apply();
            return;
        }
        showWidget(widgetId);
    }
    private void showWidget(int widgetId){
        AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(widgetId);
        if(info==null)return;
        try{
            widgetFrame.removeAllViews();
            hostView=host.createView(this,widgetId,info);
            hostView.setMinimumHeight(dp(190));
            widgetFrame.addView(hostView,new LinearLayout.LayoutParams(-1,dp(240)));
            Bundle options=new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,260);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,430);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,190);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,280);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
            widgetManager.updateAppWidgetOptions(widgetId,options);
            setState("FORD_WIDGET_ATTACHED","Ford widget hosted; waiting for its rendered contents.");
            updateUi();
            handler.postDelayed(()->inspectWidget(false),1000);
        }catch(Exception error){
            hostView=null;
            setState("WIDGET_RENDER_ERROR",error.getClass().getSimpleName());updateUi();
        }
    }
    private void inspectWidget(boolean userRequested){
        if(hostView==null){
            if(userRequested){
                setState("NO_WIDGET_BOUND","First tap CONNECT MY FORD WIDGET.");
                updateUi();
            }
            return;
        }
        List<String> texts=new ArrayList<>();
        collectText(hostView,texts,0);
        previousViewCount=texts.size();
        Integer fuel=null,range=null;
        for(int i=0;i<texts.size();i++){
            String token=texts.get(i).trim();
            Matcher percent=PERCENT.matcher(token);
            while(percent.find()){
                int n=parse(percent.group(1));
                if(n>=0&&n<=100) fuel=n;
            }
            Matcher miles=RANGE.matcher(token);
            while(miles.find()){
                int n=parse(miles.group(1));
                if(n>=0&&n<=2000) range=n;
            }
            if(i+1<texts.size()){
                String following=texts.get(i+1).trim().toLowerCase(Locale.US);
                int numeric=parse(token);
                if(numeric>=0 && numeric<=100 && following.equals("%"))fuel=numeric;
                if(numeric>=0 && numeric<=2000 && (following.equals("mi") || following.equals("miles")))range=numeric;
            }
        }
        SharedPreferences p=prefs();
        if(fuel!=null||range!=null){
            int f=fuel==null?-1:fuel, r=range==null?-1:range;
            int oldF=p.getInt("fuel_pct",-1),oldR=p.getInt("range_mi",-1);
            boolean changed=oldF!=f||oldR!=r;
            if(changed||userRequested||p.getLong("captured_ms",0)==0){
                p.edit().putInt("fuel_pct",f).putInt("range_mi",r)
                    .putLong("captured_ms",System.currentTimeMillis())
                    .putString("status",f>=0&&r>=0?"TEXT_VALUES_CAPTURED":"PARTIAL_TEXT_CAPTURE")
                    .putString("source","FORD_WIDGET_HOST_RENDERED_TEXT_NOT_LIVE_API")
                    .putInt("readable_text_nodes",texts.size())
                    .apply();
                SdlService.record(this,"FORD_WIDGET_CAPTURE",
                    "Ford widget text displayed: fuel "+(f>=0?"available":"absent")
                    +", range "+(r>=0?"available":"absent")+". No Ford API used.");
            }
        }else if(userRequested||(!"TEXT_VALUES_CAPTURED".equals(p.getString("status","")) &&
                !"PARTIAL_TEXT_CAPTURE".equals(p.getString("status","")))){
            setState(texts.isEmpty()?"WIDGET_TEXT_NOT_EXPOSED":"FORD_VALUES_NOT_READABLE",
                "Ford widget rendered "+texts.size()+" text nodes; no verified fuel/range values.");
        }
        updateUi();
    }
    private static int parse(String text){
        try{
            if(!text.matches("\\d{1,5}"))return -1;
            return Integer.parseInt(text);
        }catch(Exception error){return -1;}
    }
    private static void collectText(View root,List<String> out,int depth){
        if(root==null||depth>24||out.size()>100)return;
        if(root instanceof TextView){
            CharSequence chars=((TextView)root).getText();
            if(chars!=null && chars.length()>0)out.add(chars.toString().trim());
        }
        if(root instanceof ViewGroup){
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++) collectText(g.getChildAt(i),out,depth+1);
        }
    }
    private void updateUi(){
        if(state==null||results==null)return;
        SharedPreferences p=prefs();
        state.setText("WIDGET STATUS: "+p.getString("status","NOT_CONNECTED")+
            "\n"+p.getString("detail",""));
        int f=p.getInt("fuel_pct",-1),r=p.getInt("range_mi",-1);
        long at=p.getLong("captured_ms",0);
        if(at<=0){
            results.setText("Fuel: not captured\nRange: not captured\nRendered text nodes: "+
                Math.max(0,previousViewCount)+"\nNo values are being sent to SYNC 3.");
        }else{
            results.setText("Saved FORD WIDGET snapshot (NOT LIVE)\nFuel: "+
                (f>=0?f+"%":"not exposed")+
                "\nRemaining range: "+(r>=0?r+" miles":"not exposed")+
                "\nCaptured on phone: "+new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",Locale.US).format(new java.util.Date(at))+
                "\nView under Vehicle Explorer > Ford widget snapshot on SYNC 3.");
        }
    }
    private void setState(String status,String detail){
        prefs().edit().putString("status",status).putString("detail",detail)
            .putLong("state_time_ms",System.currentTimeMillis()).apply();
    }
    private void clearPending(){
        if(pendingId>0)try{host.deleteAppWidgetId(pendingId);}catch(Exception ignored){}
        pendingId=-1;pendingInfo=null;
    }
    private void disconnect(){
        int id=prefs().getInt("widget_id",-1);
        if(id>0)try{host.deleteAppWidgetId(id);}catch(Exception ignored){}
        clearPending();
        if(widgetFrame!=null)widgetFrame.removeAllViews();
        hostView=null;previousViewCount=-1;
        prefs().edit().clear().putString("status","DISCONNECTED").apply();
        updateUi();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQ_BIND){
            if(result==RESULT_OK && pendingInfo!=null && pendingId>0){
                configureIfNeeded(pendingInfo,pendingId);
            }else{
                setState("OWNER_PERMISSION_NOT_GRANTED","Android widget permission denied or canceled.");
                clearPending();updateUi();
            }
        }else if(request==REQ_CONFIG){
            if(result==RESULT_OK && pendingId>0)commitBoundWidget(pendingId);
            else{
                setState("FORD_WIDGET_SETUP_CANCELED","Widget configuration not finished.");
                clearPending();updateUi();
            }
        }
    }
    @Override protected void onStart(){
        super.onStart();
        try{host.startListening();listening=true;}
        catch(Exception ex){setState("HOST_LISTEN_ERROR",ex.getClass().getSimpleName());}
        handler.removeCallbacks(monitor);
        handler.postDelayed(monitor,2000);
    }
    @Override protected void onStop(){
        handler.removeCallbacks(monitor);
        if(listening){try{host.stopListening();}catch(Exception ignored){}listening=false;}
        super.onStop();
    }
    private final class BridgeHost extends AppWidgetHost {
        BridgeHost(Context c,int id){super(c,id);}
        @Override protected AppWidgetHostView onCreateView(Context c,int widgetId,AppWidgetProviderInfo info){
            return new AppWidgetHostView(c){
                @Override public void updateAppWidget(RemoteViews remoteViews){
                    super.updateAppWidget(remoteViews);
                    handler.postDelayed(()->inspectWidget(false),600);
                }
            };
        }
    }
}
