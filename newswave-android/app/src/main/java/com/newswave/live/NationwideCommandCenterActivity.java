package com.newswave.live;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/** Free nationwide warning viewer shared with the desktop command center.
 * All polling is foreground-only, and GPS is explicitly requested by the user.
 */
public final class NationwideCommandCenterActivity extends Activity {
    private WebView browser;
    private LocationManager locations;
    private LocationListener locationListener;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private boolean closed=false;
    private static final int GPS_PERMISSION=94;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(0xff07111e);
        TextView back=new TextView(this);
        back.setText("‹  NEWSWIRE HOME      ·      STORM COMMAND CENTER");
        back.setTextColor(Color.WHITE);back.setTextSize(13);
        back.setPadding(24,18,12,18);back.setGravity(Gravity.CENTER_VERTICAL);
        back.setBackgroundColor(0xff123247);
        back.setOnClickListener(v->finish());
        shell.addView(back,new LinearLayout.LayoutParams(-1,-2));

        browser=new WebView(this);
        browser.setBackgroundColor(0xff07111e);
        WebSettings w=browser.getSettings();
        w.setJavaScriptEnabled(true);w.setDomStorageEnabled(true);
        w.setAllowFileAccess(true);w.setLoadsImagesAutomatically(true);
        w.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // No JavaScript bridge: a deliberately selected newswire://gps action
        // is intercepted here and triggers Android's normal runtime permissions.
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest req){
                Uri uri=req.getUrl();
                if("newswire".equalsIgnoreCase(uri.getScheme())){
                    if("gps".equalsIgnoreCase(uri.getHost()))requestGps();
                    return true;
                }
                if("file".equalsIgnoreCase(uri.getScheme()))return false;
                if("https".equalsIgnoreCase(uri.getScheme())){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}
                    catch(Exception ex){locationError("No browser available for that link.");}
                }
                return true;
            }
        });
        shell.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell);
        // Additional Gradle asset directory bundles the exact same index.html,
        // app.js and engine.js used by the Mac/Windows web-based command center.
        browser.loadUrl("file:///android_asset/index.html");
    }
    private void locationError(String message){
        if(closed||browser==null)return;
        browser.evaluateJavascript("window.NewsWireLocationError("+org.json.JSONObject.quote(message)+")",null);
    }
    private void requestGps(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
           checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION},GPS_PERMISSION);
            return;
        }
        findLocation();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] names,int[] grants){
        super.onRequestPermissionsResult(requestCode,names,grants);
        if(requestCode!=GPS_PERMISSION)return;
        boolean allowed=false;
        for(int g:grants)if(g==PackageManager.PERMISSION_GRANTED)allowed=true;
        if(allowed)findLocation();else locationError("GPS permission not granted. Enter your ZIP instead.");
    }
    private void findLocation(){
        try{
            locations=(LocationManager)getSystemService(LOCATION_SERVICE);
            Location best=null;
            List<String> providers=locations.getProviders(true);
            for(String provider:providers){
                Location fix=locations.getLastKnownLocation(provider);
                if(fix!=null&&(best==null||fix.getTime()>best.getTime()))best=fix;
            }
            if(best!=null&&System.currentTimeMillis()-best.getTime()<5*60*1000L){
                deliver(best);return;
            }
            if(locationListener!=null){locations.removeUpdates(locationListener);locationListener=null;}
            locationListener=fix->{
                if(fix==null||closed)return;
                deliver(fix);
                if(locations!=null&&locationListener!=null)locations.removeUpdates(locationListener);
                locationListener=null;
            };
            if(providers.isEmpty()){locationError("Location services are off. Enter your ZIP.");return;}
            for(String provider:providers){
                try{locations.requestLocationUpdates(provider,1000L,5f,locationListener,Looper.getMainLooper());}
                catch(Exception error){android.util.Log.w("NewsWireNationwide","GPS provider unavailable",error);}
            }
            ui.postDelayed(()->{
                if(locationListener==null||closed)return;
                if(locations!=null)locations.removeUpdates(locationListener);
                locationListener=null;
                locationError("No recent GPS position. You can enter your ZIP.");
            },18000);
        }catch(SecurityException error){locationError("GPS permission missing. Enter a ZIP.");}
        catch(Exception error){locationError("Could not retrieve a GPS position. Enter a ZIP.");}
    }
    private void deliver(Location fix){
        if(fix==null||closed||browser==null)return;
        if(!Double.isFinite(fix.getLatitude())||!Double.isFinite(fix.getLongitude()))return;
        browser.evaluateJavascript(String.format(Locale.US,
            "window.updateNewsWirePosition&&window.updateNewsWirePosition(%.6f,%.6f)",
            fix.getLatitude(),fix.getLongitude()),null);
    }
    @Override protected void onDestroy(){
        closed=true;
        if(locations!=null&&locationListener!=null){
            try{locations.removeUpdates(locationListener);}catch(Exception ignored){}
        }
        ui.removeCallbacksAndMessages(null);
        if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}
        super.onDestroy();
    }
}
