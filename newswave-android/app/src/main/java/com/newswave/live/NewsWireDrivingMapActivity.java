package com.newswave.live;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Looper;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import androidx.webkit.WebViewAssetLoader;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.widget.Toast;
import java.util.Locale;

/**
 * Phone-only vector driving map. Ford-inspired presentation, not Ford software.
 * Free/OpenStreetMap-based OpenFreeMap dark vector style; Esri satellite optional.
 * Live GPS position/course only. NO road matching, routes, TBT or tornado ETA.
 */
public final class NewsWireDrivingMapActivity extends Activity {
    private static final int LOCATION_REQUEST=307;
    private WebView view;
    private LocationManager locationManager;
    private LocationListener listener;
    private Location lastFix;
    private boolean pageReady=false;
    private boolean leaving=false;
    private float lastGoodCourse=Float.NaN;
    private TextView title;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff05111e);
        getWindow().setNavigationBarColor(0xff061421);
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(0xff05111e);
        title=new TextView(this);
        title.setText("‹   NEWSWIRE     /     3D DRIVE MAP");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(null,1);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16),0,dp(12),0);
        title.setBackgroundColor(0xff0c263c);
        title.setOnClickListener(v->finish());
        shell.addView(title,new LinearLayout.LayoutParams(-1,dp(53)));
        view=new WebView(this);
        view.setBackgroundColor(0xff061421);
        WebSettings settings=view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.setWebChromeClient(new WebChromeClient());
        final WebViewAssetLoader localAssets=new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        view.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView web,android.webkit.WebResourceRequest request){
                return localAssets.shouldInterceptRequest(request.getUrl());
            }
            @Override public void onPageFinished(WebView web,String url){
                pageReady=true;
                if(lastFix!=null)push(lastFix);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView web,android.webkit.WebResourceRequest request){
                Uri uri=request.getUrl();
                if("https".equalsIgnoreCase(uri.getScheme())&&
                   "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost()))return false;
                if("https".equalsIgnoreCase(uri.getScheme())){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
                }
                return true;
            }
        });
        shell.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell);
        view.loadUrl("https://appassets.androidplatform.net/assets/drive_map.html");
    }

    @Override protected void onResume(){
        super.onResume();startGps();
    }
    private void startGps(){
        if(leaving)return;
        if(Build.VERSION.SDK_INT>=23 &&
           checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
           checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);
            return;
        }
        stopGps();
        try{
            locationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
            listener=this::fix;
            for(String provider:locationManager.getProviders(true)){
                Location old=locationManager.getLastKnownLocation(provider);
                if(old!=null&&System.currentTimeMillis()-old.getTime()<120000L)fix(old);
                locationManager.requestLocationUpdates(provider,1600L,3f,listener,Looper.getMainLooper());
            }
        }catch(SecurityException ex){
            Toast.makeText(this,"Allow location to follow your vehicle.",Toast.LENGTH_LONG).show();
        }catch(Exception ex){
            android.util.Log.w("NewsWireDriveMap","GPS temporarily unavailable",ex);
        }
    }
    private void fix(Location location){
        if(location==null||leaving||!Double.isFinite(location.getLatitude())||
           !Double.isFinite(location.getLongitude())||
           System.currentTimeMillis()-location.getTime()>60000L)return;
        if(location.hasAccuracy()&&location.getAccuracy()>150f)return;
        if(lastFix!=null){
            if(location.getElapsedRealtimeNanos()<=lastFix.getElapsedRealtimeNanos())return;
            if(location.hasAccuracy()&&lastFix.hasAccuracy()&&
                location.getAccuracy()>lastFix.getAccuracy()*3f &&
                location.getTime()-lastFix.getTime()<10000L)return;
        }
        Location previous=lastFix;
        lastFix=new Location(location);
        float speed=location.hasSpeed()?location.getSpeed():0f;
        if(speed>=1.5f&&location.hasBearing()){
            lastGoodCourse=location.getBearing();
        }else if(previous!=null&&location.distanceTo(previous)>=8f &&
            location.getTime()-previous.getTime()<30000L){
            float elapsed=(location.getTime()-previous.getTime())/1000f;
            if(elapsed>0.1f&&location.distanceTo(previous)/elapsed>=1.5f){
                lastGoodCourse=previous.bearingTo(location);
                speed=location.distanceTo(previous)/elapsed;
            }
        }
        push(location,speed);
    }
    private void push(Location loc){push(loc,loc.hasSpeed()?loc.getSpeed():0f);}
    private void push(Location loc,float speed){
        if(!pageReady||view==null)return;
        String js=String.format(Locale.US,
            "window.updateNewsWireDrivePosition&&window.updateNewsWireDrivePosition(%.7f,%.7f,%s,%.2f,%.2f)",
            loc.getLatitude(),loc.getLongitude(),
            Float.isFinite(lastGoodCourse)?String.format(Locale.US,"%.2f",lastGoodCourse):"null",
            Math.max(0,speed),loc.hasAccuracy()?loc.getAccuracy():20f);
        view.evaluateJavascript(js,null);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==LOCATION_REQUEST){
            for(int r:results)if(r==PackageManager.PERMISSION_GRANTED){startGps();return;}
            Toast.makeText(this,"GPS unavailable; the map can still be explored manually.",Toast.LENGTH_LONG).show();
        }
    }
    private void stopGps(){
        if(locationManager!=null&&listener!=null)
            try{locationManager.removeUpdates(listener);}catch(Exception ignored){}
        listener=null;
    }
    @Override protected void onPause(){stopGps();super.onPause();}
    @Override protected void onDestroy(){
        leaving=true;stopGps();
        if(view!=null){view.destroy();view=null;}
        super.onDestroy();
    }
}
