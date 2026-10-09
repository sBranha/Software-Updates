package com.newswave.live;

import android.app.Activity;
import android.os.Bundle;
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
    radar.loadUrl("file:///android_asset/radar.html");
  }
  @Override public void onBackPressed() {
    if (radar != null && radar.canGoBack()) radar.goBack(); else super.onBackPressed();
  }
  @Override protected void onDestroy() {
    if (radar!=null) { ((FrameLayout)radar.getParent()); radar.destroy(); radar=null; }
    super.onDestroy();
  }
}
