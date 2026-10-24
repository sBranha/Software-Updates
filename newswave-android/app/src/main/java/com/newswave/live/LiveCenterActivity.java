package com.newswave.live;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Satellite-first Live Map and official NWS Emergency Center.
 * Does not place news pins without a verified article address. 
 * Official alerts with missing geometry remain accessible in the list.
 */
public final class LiveCenterActivity extends Activity {
    private static final int BG=0xff071521,INK=Color.WHITE,
        MUTED=0xffadc5d8,CYAN=0xff85e8ed,GOLD=0xffffd18a,RED=0xffff7895;
    private LinearLayout root,alertsPanel,newsPanel;
    private TextView status,areaLabel,mapTab,alertsTab;
    private WebView map;
    private boolean mapReady=false,closed=false,loading=false,showMap=true;
    private double lat=Double.NaN,lon=Double.NaN;
    private String city="",state="";
    private int generation=0;
    private ActiveAlertData.Result warnings;
    private long checkedAt=0;
    private final Handler updates=new Handler(Looper.getMainLooper());
    private LocationManager gpsManager;
    private LocationListener gpsListener;
    private boolean visible=false;
    private long lastAreaLookup=0;
    private final Runnable periodicCheck=new Runnable(){
        @Override public void run(){
            if(!visible||closed)return;
            if(!loading)updateAlerts();
            updates.postDelayed(this,5L*60*1000);
        }
    };
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        android.content.SharedPreferences prefs=
            getSharedPreferences("newswire-location",MODE_PRIVATE);
        city=getIntent().getStringExtra("city");state=getIntent().getStringExtra("state");
        if(city==null||city.isEmpty())city=prefs.getString("city","");
        if(state==null||state.isEmpty())state=prefs.getString("state","");
        lat=getIntent().getDoubleExtra("latitude",Double.NaN);
        lon=getIntent().getDoubleExtra("longitude",Double.NaN);
        if(Double.isNaN(lat))lat=Double.longBitsToDouble(prefs.getLong("lat",
            Double.doubleToLongBits(Double.NaN)));
        if(Double.isNaN(lon))lon=Double.longBitsToDouble(prefs.getLong("lon",
            Double.doubleToLongBits(Double.NaN)));
        showMap=!"alerts".equals(getIntent().getStringExtra("section"));
        build();
        lookupGps();
        updateAlerts();
        loadNews();
    }
    private int dp(int d){return (int)(d*getResources().getDisplayMetrics().density+.5);}
    private GradientDrawable surface(int start,int end,int edge){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{start,end});
        g.setCornerRadius(dp(16));g.setStroke(dp(1),edge);return g;
    }
    private TextView text(String label,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(label);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private TextView button(String label,Runnable action){
        TextView t=text(label,12,INK,true);t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12),dp(13),dp(12),dp(13));
        t.setBackground(surface(0xff15374b,0xff102439,0xff38738a));
        t.setOnClickListener(v->action.run());return t;
    }
    private void add(LinearLayout parent,View child,int top){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(top);parent.addView(child,lp);
    }
    private void build(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(12),dp(15),dp(12),dp(26));scroll.addView(root);
        setContentView(scroll);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);
        hero.setPadding(dp(17),dp(17),dp(17),dp(17));
        hero.setBackground(surface(0xff17465d,0xff0a2133,0xff3b87a0));
        hero.addView(text("NEWSWIRE / LIVE AWARENESS",11,CYAN,true));
        add(hero,text("YOUR WORLD, MAPPED",23,INK,true),7);
        areaLabel=text(city.isEmpty()?"⌖ Current location or saved ZIP":"⌖ "+city+", "+state,
            12,MUTED,false);add(hero,areaLabel,9);
        status=text("Retrieving official National Weather Service warnings…",11,CYAN,false);
        add(hero,status,8);add(root,hero,0);

        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(0);
        mapTab=button("◈ LIVE SATELLITE MAP",()->switchMode(true));
        alertsTab=button("⚠ EMERGENCY CENTER",()->switchMode(false));
        LinearLayout.LayoutParams one=new LinearLayout.LayoutParams(0,-2,1);
        one.rightMargin=dp(5);nav.addView(mapTab,one);
        LinearLayout.LayoutParams two=new LinearLayout.LayoutParams(0,-2,1);
        two.leftMargin=dp(5);nav.addView(alertsTab,two);
        add(root,nav,12);
        LinearLayout quick=new LinearLayout(this);
        TextView back=button("← BACK",this::finish);
        TextView refresh=button("↻ CHECK NWS",this::updateAlerts);
        LinearLayout.LayoutParams qb=new LinearLayout.LayoutParams(0,-2,1);
        qb.rightMargin=dp(6);quick.addView(back,qb);
        quick.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));add(root,quick,9);
        map=new WebView(this);map.setBackgroundColor(BG);
        map.getSettings().setJavaScriptEnabled(true);
        map.getSettings().setDomStorageEnabled(true);
        map.getSettings().setAllowFileAccess(true);
        map.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        map.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                mapReady=true;
                if(!Double.isNaN(lat)&&!Double.isNaN(lon))
                    map.evaluateJavascript("setPosition("+lat+","+lon+",true)",null);
                injectWarningMap();
            }
        });
        LinearLayout.LayoutParams web=new LinearLayout.LayoutParams(-1,dp(395));
        web.topMargin=dp(10);root.addView(map,web);
        if(Double.isNaN(lat)||Double.isNaN(lon))
            map.loadUrl("file:///android_asset/live_center_map.html");
        else map.loadUrl(String.format(Locale.US,
            "file:///android_asset/live_center_map.html?lat=%.5f&lon=%.5f",lat,lon));

        alertsPanel=new LinearLayout(this);alertsPanel.setOrientation(1);
        add(root,alertsPanel,12);
        newsPanel=new LinearLayout(this);newsPanel.setOrientation(1);
        add(root,newsPanel,12);
        TextView tip=text("Warning information may be delayed or unavailable. "+
            "Always use Wireless Emergency Alerts and official NWS guidance; this app cannot guarantee advance notice.",
            11,MUTED,false);add(root,tip,17);
        switchMode(showMap);
        renderAlerts();
    }
    private void switchMode(boolean viewMap){
        showMap=viewMap;map.setVisibility(viewMap?View.VISIBLE:View.GONE);
        mapTab.setText(viewMap?"◈ LIVE MAP ●":"◈ LIVE SATELLITE MAP");
        alertsTab.setText(viewMap?"⚠ EMERGENCY CENTER":"⚠ EMERGENCY ●");
        if(alertsPanel!=null)alertsPanel.setVisibility(viewMap?View.GONE:View.VISIBLE);
        if(viewMap&&mapReady)map.postDelayed(()->map.evaluateJavascript(
            "map&&map.invalidateSize()",null),200);
    }
    private void injectWarningMap(){
        if(!mapReady||map==null||warnings==null)return;
        String geo=warnings.geoJson();
        map.evaluateJavascript("showWarnings("+geo+","+warnings.items.size()+")",null);
    }
    private void renderAlerts(){
        if(alertsPanel==null)return;
        alertsPanel.removeAllViews();
        add(alertsPanel,text("OFFICIAL NWS ALERTS",13,CYAN,true),8);
        if(loading){
            add(alertsPanel,text("Checking the official NWS alerts feed…",12,MUTED,false),7);
            return;
        }
        if(warnings==null){
            add(alertsPanel,text("NWS alert data unavailable. An unknown warning status "+
                "does not mean the area is safe.",13,GOLD,false),8);
            return;
        }
        if(warnings.items.isEmpty()){
            add(alertsPanel,text("No active NWS alerts reported for the selected area at "+
                "the last check. This is not a safety guarantee.",12,MUTED,false),8);
            return;
        }
        int shown=0;
        for(ActiveAlertData.Item alert:warnings.items){
            if(shown++>=45)break;
            LinearLayout pane=new LinearLayout(this);pane.setOrientation(1);
            pane.setPadding(dp(14),dp(14),dp(14),dp(14));
            pane.setBackground(surface(0xff26344a,0xff102133,
                ActiveAlertData.rank(alert)<=2?0xffda6d76:0xff3c7b8d));
            pane.addView(text(alert.event.toUpperCase(Locale.US),13,
                ActiveAlertData.rank(alert)<=2?RED:GOLD,true));
            add(pane,text(alert.area,12,INK,false),8);
            add(pane,text("Expires: "+ActiveAlertData.time(alert.expires)+
                (alert.geometry==null?" · Area text only":" · Mapped boundary"),11,MUTED,false),8);
            pane.setOnClickListener(v->showDetail(alert));
            add(alertsPanel,pane,9);
        }
    }
    private void showDetail(ActiveAlertData.Item item){
        StringBuilder message=new StringBuilder();
        message.append(item.area).append("\n\n");
        message.append("Severity: ").append(item.severity).append("\n");
        message.append("Issued: ").append(ActiveAlertData.time(item.issued)).append("\n");
        message.append("Expires: ").append(ActiveAlertData.time(item.expires)).append("\n\n");
        if(!item.headline.isEmpty())message.append(item.headline).append("\n\n");
        if(!item.description.isEmpty())message.append(item.description).append("\n\n");
        if(!item.instruction.isEmpty())message.append("NWS Guidance:\n").append(item.instruction);
        new AlertDialog.Builder(this).setTitle(item.event)
            .setMessage(message.toString())
            .setPositiveButton("Close",null)
            .setNeutralButton("NWS ALERTS",(dialog,which)->
                open("https://www.weather.gov/alerts"))
            .show();
    }
    private void updateAlerts(){
        if(loading){android.widget.Toast.makeText(this,"Still checking NWS…",0).show();return;}
        loading=true;status.setText("Checking current NWS warnings…");
        renderAlerts();
        final int revision=++generation;
        final String region=state;
        final double latitude=lat,longitude=lon;
        new Thread(()->{
            ActiveAlertData.Result result=null;
            try{result=ActiveAlertData.load(region,latitude,longitude);}
            catch(Exception error){android.util.Log.w("NewsWireLive","NWS lookup unavailable",error);}
            final ActiveAlertData.Result ready=result;
            runOnUiThread(()->{
                if(closed||revision!=generation)return;
                loading=false;warnings=ready;
                if(ready==null){
                    status.setText("NWS service unavailable — status unknown");
                    if(mapReady)map.evaluateJavascript("mapError('NWS service unavailable; check official alerts')",null);
                }else{
                    String time=new java.text.SimpleDateFormat("h:mm a",Locale.US)
                        .format(new java.util.Date(ready.fetchedAt));
                    status.setText(ready.items.size()+" current NWS alerts · Checked "+time+
                        " · "+(state.isEmpty()?"Your point":state));
                    injectWarningMap();
                }
                renderAlerts();
            });
        },"newswire-nws-alerts").start();
    }
    private void loadNews(){
        newsPanel.removeAllViews();
        add(newsPanel,text("NEWS, ROADS & EVENTS AROUND YOU",12,CYAN,true),9);
        add(newsPanel,button("◎ LOCAL NEWS · SOURCED HEADLINES",()->
            startActivity(new Intent(this,PersonalizedNewsActivity.class))),7);
        add(newsPanel,button("⇢ TRAFFIC & OFFICIAL ROAD CLOSURES",()->
            startActivity(new Intent(this,TrafficActivity.class).putExtra("state",state))),8);
        add(newsPanel,button("✦ LOCAL COMMUNITY EVENTS",()->
            startActivity(new Intent(this,DayPlannerActivity.class)
                .putExtra("section","events").putExtra("city",city).putExtra("state",state)
                .putExtra("latitude",lat).putExtra("longitude",lon))),8);
        add(newsPanel,text("News and events require a verified address before they can "+
            "appear as map pins. Use the sourced lists above until location data is available.",
            11,MUTED,false),9);
    }
    private void lookupGps(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
            &&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        new Thread(()->{
            try{
                LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
                Location fix=null;
                for(String provider:lm.getProviders(true)){
                    Location old=lm.getLastKnownLocation(provider);
                    if(old!=null&&(fix==null||old.getTime()>fix.getTime()))fix=old;
                }
                if(fix==null||System.currentTimeMillis()-fix.getTime()>600000)return;
                final double a=fix.getLatitude(),b=fix.getLongitude();
                String foundCity="",foundState="";
                try{
                    List<Address> address=new Geocoder(this,Locale.US)
                        .getFromLocation(a,b,1);
                    if(address!=null&&!address.isEmpty()){
                        foundCity=address.get(0).getLocality();
                        foundState=address.get(0).getAdminArea();
                    }
                }catch(Exception ignored){}
                final String c=foundCity==null?"":foundCity,
                    st=foundState==null?"":foundState;
                runOnUiThread(()->{
                    if(closed)return;
                    boolean moved=Double.isNaN(lat)||Math.abs(lat-a)>.02||Math.abs(lon-b)>.02;
                    if(!moved)return;
                    lat=a;lon=b;city=c;state=st;
                    areaLabel.setText("⌖ "+(city.isEmpty()?"Your location":city+", "+state)+" · GPS");
                    if(mapReady)map.evaluateJavascript("setPosition("+a+","+b+",true)",null);
                    if(loading){generation++;loading=false;}
                    updateAlerts();loadNews();
                });
            }catch(Exception error){android.util.Log.w("NewsWireLive","GPS unavailable",error);}
        },"newswire-live-gps").start();
    }
    @Override protected void onStart(){
        super.onStart();
        visible=true;
        updates.removeCallbacks(periodicCheck);
        updates.postDelayed(periodicCheck,5L*60*1000);
        followLocation();
    }
    @Override protected void onStop(){
        visible=false;
        updates.removeCallbacks(periodicCheck);
        if(gpsManager!=null&&gpsListener!=null){
            try{gpsManager.removeUpdates(gpsListener);}catch(Exception ignored){}
        }
        gpsListener=null;
        super.onStop();
    }
    private void followLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
          &&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        if(gpsListener!=null)return;
        try{
            gpsManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
            gpsListener=fix->{
                if(fix==null||!visible||closed||
                    fix.hasAccuracy()&&fix.getAccuracy()>1000f)return;
                double nextLat=fix.getLatitude(),nextLon=fix.getLongitude();
                if(!Double.isFinite(nextLat)||!Double.isFinite(nextLon))return;
                boolean different=Double.isNaN(lat)||Math.abs(lat-nextLat)>.001||
                    Math.abs(lon-nextLon)>.001;
                boolean newArea=Double.isNaN(lat)||Math.abs(lat-nextLat)>.035||
                    Math.abs(lon-nextLon)>.035;
                if(!different)return;
                lat=nextLat;lon=nextLon;
                if(mapReady)map.evaluateJavascript(
                    "setPosition("+nextLat+","+nextLon+",true)",null);
                if(newArea&&System.currentTimeMillis()-lastAreaLookup>60*1000){
                    lastAreaLookup=System.currentTimeMillis();
                    lookupGps();
                }
            };
            for(String provider:gpsManager.getProviders(true)){
                try{
                    gpsManager.requestLocationUpdates(provider,15000L,150f,
                        gpsListener,Looper.getMainLooper());
                }catch(Exception error){android.util.Log.w("NewsWireLive","GPS provider unavailable",error);}
            }
        }catch(Exception error){android.util.Log.w("NewsWireLive","Could not follow GPS",error);}
    }
    private void open(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){android.widget.Toast.makeText(this,"Browser unavailable",0).show();}
    }
    @Override protected void onDestroy(){
        closed=true;generation++;
        updates.removeCallbacks(periodicCheck);
        if(gpsManager!=null&&gpsListener!=null){
            try{gpsManager.removeUpdates(gpsListener);}catch(Exception ignored){}
        }
        if(map!=null){map.stopLoading();map.destroy();map=null;}
        super.onDestroy();
    }
}