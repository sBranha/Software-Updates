package com.newswave.live;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Looper;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.graphics.Color;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

public class RadarActivity extends Activity {
  private WebView radar;
  private LocationManager locationManager;
  private LocationListener listener;
  private boolean loaded=false;
  private Location pendingPosition;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.rgb(9,15,29));
    getWindow().setNavigationBarColor(Color.rgb(9,15,29));
    radar = new WebView(this);
    radar.setBackgroundColor(Color.rgb(9,15,29));
    radar.getSettings().setJavaScriptEnabled(true);
    radar.getSettings().setDomStorageEnabled(true);
    radar.getSettings().setAllowFileAccess(true);
    radar.getSettings().setLoadsImagesAutomatically(true);
    radar.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    radar.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
    radar.setWebChromeClient(new WebChromeClient());
    radar.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
        Uri uri=request.getUrl();
        if ("file".equalsIgnoreCase(uri.getScheme())) return false;
        if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null) {
          try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); }
          catch(Exception e){ Toast.makeText(RadarActivity.this,"Could not open browser",Toast.LENGTH_SHORT).show(); }
          return true;
        }
        return true;
      }
    });
    setContentView(radar);
    radar.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView view,String url){
        loaded=true;if(pendingPosition!=null)deliver(pendingPosition);
      }
      @Override public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest request){
        Uri uri=request.getUrl();
        if("file".equalsIgnoreCase(uri.getScheme()))return false;
        if("https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null){
          try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}
          catch(Exception ex){Toast.makeText(RadarActivity.this,"Could not open browser",Toast.LENGTH_SHORT).show();}
        }
        return true;
      }
    });
    radar.loadUrl("file:///android_asset/radar.html");
    startLocation();
  }

  private void deliver(Location loc){
    pendingPosition=loc;
    if(loaded&&radar!=null){
      radar.evaluateJavascript("window.updateNewsWirePosition&&window.updateNewsWirePosition("
          +loc.getLatitude()+","+loc.getLongitude()+")",null);
    }
  }
  private void startLocation(){
    if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
        &&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},71);return;
    }
    try{
      locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
      listener=location->deliver(location);
      for(String provider:locationManager.getProviders(true)){
        Location last=locationManager.getLastKnownLocation(provider);
        if(last!=null&&(pendingPosition==null||last.getTime()>pendingPosition.getTime()))deliver(last);
        locationManager.requestLocationUpdates(provider,8000,20,listener,Looper.getMainLooper());
      }
    }catch(SecurityException ex){Toast.makeText(this,"Allow location to enable radar follow",Toast.LENGTH_LONG).show();}
      catch(Exception ex){android.util.Log.w("NewswireRadar","GPS not available",ex);}
  }
  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
    super.onRequestPermissionsResult(requestCode,permissions,results);
    if(requestCode==71){
      boolean allowed=false;
      for(int result:results)if(result==PackageManager.PERMISSION_GRANTED)allowed=true;
      if(allowed)startLocation();
      else Toast.makeText(this,"Location disabled. Radar still works without GPS.",Toast.LENGTH_LONG).show();
    }
  }

  @Override public void onBackPressed() {
    if (radar != null && radar.canGoBack()) radar.goBack(); else super.onBackPressed();
  }
  @Override protected void onDestroy() {
    if(locationManager!=null&&listener!=null)locationManager.removeUpdates(listener);
    if (radar!=null) { radar.destroy(); radar=null; }
    super.onDestroy();
  }
}
