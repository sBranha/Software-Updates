package com.newswave.live;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.webkit.*;
import android.widget.*;

/** Publisher pages stay in NewsWire; no scraping or injected native bridge. */
public final class ArticleReaderActivity extends Activity {
    private WebView web;
    private TextView status;
    public static void open(Context context,String url){
        if(!isWebUrl(url)){Toast.makeText(context,"This link cannot be opened",Toast.LENGTH_SHORT).show();return;}
        context.startActivity(new Intent(context,ArticleReaderActivity.class).putExtra("url",url));
    }
    static boolean isWebUrl(String url){
        return WebLinkPolicy.allowed(url);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);SystemUi.install(this);SystemUi.onBack(this,this::handleBack);
        String url=getIntent().getStringExtra("url");
        if(!isWebUrl(url)){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(android.widget.LinearLayout.VERTICAL);root.setBackgroundColor(0xff091622);
        LinearLayout controls=new LinearLayout(this);
        Button home=new Button(this);home.setText("‹ NewsWire");home.setOnClickListener(v->finish());controls.addView(home);
        Button back=new Button(this);back.setText("Back");back.setOnClickListener(v->{if(web.canGoBack())web.goBack();else finish();});controls.addView(back);
        Button reload=new Button(this);reload.setText("Reload");reload.setOnClickListener(v->web.reload());controls.addView(reload);
        root.addView(controls);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setPadding(16,12,16,12);root.addView(status);
        web=new WebView(this);web.setBackgroundColor(Color.WHITE);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);s.setSupportMultipleWindows(false);
        web.setWebViewClient(new NewsWireWebClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
                if(isWebUrl(req.getUrl().toString()))return false;
                status.setText("This publisher link cannot open here. Use Back to return.");return true;
            }
            @Override public void onPageStarted(WebView v,String address,android.graphics.Bitmap icon){
                status.setText("Loading · "+Uri.parse(address).getHost());
            }
            @Override public void onPageFinished(WebView v,String address){
                if(!status.getText().toString().startsWith("Page unavailable"))status.setText("Publisher: "+Uri.parse(address).getHost());
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError error){
                if(req.isForMainFrame())status.setText("Page unavailable. Check connection or try Reload.");
            }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest req,WebResourceResponse response){
                if(req.isForMainFrame())status.setText("Page unavailable from publisher ("+response.getStatusCode()+"). Try Reload.");
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);web.loadUrl(url);
    }
    @Override public void onBackPressed(){handleBack();}
    private void handleBack(){if(web!=null&&web.canGoBack())web.goBack();else finish();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
