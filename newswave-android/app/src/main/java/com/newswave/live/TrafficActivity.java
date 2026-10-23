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
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/** Phone-only, integrated official DOT traffic reports and incident maps.
 * Embeds the original DOT live service with attribution; it does not
 * scrape or copy uncertain incident details, and has a browser fallback.
 */
public final class TrafficActivity extends Activity {
    private static final int BG=0xff091321,INK=Color.WHITE,
        MUTED=0xffa5bdce,CYAN=0xff7eeaf0,ORANGE=0xffffc27a;
    private String state="";
    private TrafficSources.Source source;
    private WebView browser;
    private LinearLayout main,toolbar;
    private TextView heading,note,loading, mapButton, reportButton;
    private boolean reportsMode=false,locationLookup=false,manualSelection=false;

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        state=getIntent().getStringExtra("state");
        if(state==null||state.isEmpty())
            state=getSharedPreferences("newswire-location",MODE_PRIVATE)
                .getString("state","");
        source=TrafficSources.forState(state);
        renderLayout();
        detectStateFromGps();
    }
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private GradientDrawable background(int one,int two,int border){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{one,two});
        g.setCornerRadius(dp(18));g.setStroke(dp(1),border);return g;
    }
    private TextView text(String t,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private TextView button(String t,Runnable action){
        TextView v=text(t,12,INK,true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12),dp(13),dp(12),dp(13));
        v.setBackground(background(0xff173d56,0xff102338,0xff276b80));
        v.setOnClickListener(view->action.run());return v;
    }
    private void rowButton(String text,Runnable action){
        TextView v=button(text,action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(dp(3),0,dp(3),0);
        toolbar.addView(v,lp);
    }
    private void renderLayout(){
        main=new LinearLayout(this);main.setOrientation(1);
        main.setPadding(dp(13),dp(16),dp(13),dp(10));
        main.setBackgroundColor(BG);setContentView(main);
        LinearLayout banner=new LinearLayout(this);banner.setOrientation(1);
        banner.setPadding(dp(15),dp(15),dp(15),dp(15));
        banner.setBackground(background(0xff183c55,0xff102336,0xff398ca0));
        TextView kicker=text("NEWSWIRE / ROAD WATCH",11,CYAN,true);banner.addView(kicker);
        heading=text("TRAFFIC & ROAD CLOSURES",22,INK,true);
        LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,-2);
        hl.topMargin=dp(6);banner.addView(heading,hl);
        note=text("Road events are supplied directly by their state DOT. " +
            "Check before travel; road conditions can change without notice.",
            12,MUTED,false);
        LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(-1,-2);
        nl.topMargin=dp(7);banner.addView(note,nl);
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2);
        bl.bottomMargin=dp(11);main.addView(banner,bl);

        toolbar=new LinearLayout(this);
        toolbar.setOrientation(0);
        rowButton("← BACK",this::finish);
        rowButton("⌖ STATE",this::showStates);
        rowButton("↻ REFRESH",()->{if(browser!=null)browser.reload();});
        LinearLayout.LayoutParams toolLp=new LinearLayout.LayoutParams(-1,-2);
        toolLp.bottomMargin=dp(9);main.addView(toolbar,toolLp);

        LinearLayout sources=new LinearLayout(this);
        mapButton=button("LIVE MAP",()->select(false));
        reportButton=button("ROAD REPORTS",()->select(true));
        LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(0,-2,1);m.rightMargin=dp(5);
        sources.addView(mapButton,m);
        LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(0,-2,1);r.leftMargin=dp(5);
        sources.addView(reportButton,r);
        main.addView(sources);

        loading=text("",11,ORANGE,false);loading.setGravity(Gravity.CENTER);
        loading.setPadding(dp(4),dp(9),dp(4),dp(8));main.addView(loading);
        browser=new WebView(this);
        browser.setBackgroundColor(BG);
        WebSettings settings=browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                loading.setText("Official "+source.agency+" information · updated by the provider");
            }
            @Override public void onReceivedError(WebView view,
                WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame())
                    loading.setText("Official site could not load here. Tap OPEN IN BROWSER.");
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri url=request.getUrl();
                String host=url.getHost()==null?"":url.getHost().toLowerCase(Locale.US);
                if(!"https".equalsIgnoreCase(url.getScheme()))return true;
                if(host.endsWith("algotraffic.com")||host.endsWith("fl511.com")
                    ||host.endsWith("511ga.org")||host.endsWith("smartway.tn.gov")
                    ||host.endsWith("mdottraffic.com"))return false;
                if(request.isForMainFrame()&&request.hasGesture())openExternal(url.toString());
                return true;
            }
        });
        LinearLayout.LayoutParams webLp=new LinearLayout.LayoutParams(-1,0,1);
        webLp.topMargin=dp(6);main.addView(browser,webLp);
        TextView external=button("↗ OPEN OFFICIAL SITE IN BROWSER",()->
            openExternal(reportsMode?source.reportsUrl:source.mapUrl));
        LinearLayout.LayoutParams extLp=new LinearLayout.LayoutParams(-1,-2);
        extLp.topMargin=dp(8);main.addView(external,extLp);
        updateSource();
    }
    private void updateSource(){
        heading.setText("TRAFFIC & ROAD CLOSURES");
        note.setText(source.agency+
            " · Official traffic cameras, incidents and closures. This map comes from the DOT provider, not from a NewsWire incident database.");
        if(source.mapUrl.isEmpty()){
            loading.setText("No verified DOT live feed configured for "+
                (state.isEmpty()?"this state":state)+". Choose AL, FL, GA, TN or MS.");
            browser.loadDataWithBaseURL(null,
                "<html><body style='font-family:sans-serif;background:#091321;color:#bfd5e5;padding:20px'>"
                +"Live DOT traffic coverage for this region is not configured. "
                +"Please select a supported state.</body></html>","text/html","UTF-8",null);
        }else select(reportsMode);
    }
    private void select(boolean reports){
        reportsMode=reports;
        if(source.mapUrl.isEmpty())return;
        String url=reports?source.reportsUrl:source.mapUrl;
        if(url==null||url.isEmpty())url=source.mapUrl;
        loading.setText("Loading official "+source.agency+" "+(reports?"road reports":"traffic map")+"…");
        browser.loadUrl(url);
    }
    private void showStates(){
        String[] names={"Alabama · ALGO","Florida · FL511","Georgia · 511GA",
            "Tennessee · SmartWay","Mississippi · MDOT"};
        String[] codes={"AL","FL","GA","TN","MS"};
        new AlertDialog.Builder(this).setTitle("Choose traffic coverage")
            .setItems(names,(dialog,which)->{
                manualSelection=true;
                state=codes[which];
                source=TrafficSources.forState(state);
                updateSource();
            }).setNegativeButton("Cancel",null).show();
    }
    private void detectStateFromGps(){
        if(locationLookup||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            !=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            !=PackageManager.PERMISSION_GRANTED)return;
        locationLookup=true;
        new Thread(()->{
            try{
                LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
                Location found=null;
                for(String provider:lm.getProviders(true)){
                    Location last=lm.getLastKnownLocation(provider);
                    if(last!=null&&(found==null||last.getTime()>found.getTime()))found=last;
                }
                if(found==null||System.currentTimeMillis()-found.getTime()>600000)return;
                Geocoder geo=new Geocoder(this,Locale.US);
                List<Address> values=geo.getFromLocation(
                    found.getLatitude(),found.getLongitude(),1);
                if(values==null||values.isEmpty())return;
                String gpsState=values.get(0).getAdminArea();
                if(gpsState==null)return;
                TrafficSources.Source candidate=TrafficSources.forState(gpsState);
                runOnUiThread(()->{
                    if(!manualSelection&&!candidate.mapUrl.isEmpty()
                        &&!candidate.state.equals(source.state)){
                        source=candidate;state=candidate.state;
                        updateSource();
                    }
                });
            }catch(Exception ex){android.util.Log.w("NewsWireTraffic",
                "Auto location for traffic unavailable",ex);}
            finally{locationLookup=false;}
        },"newswire-traffic-area").start();
    }
    private void openExternal(String url){
        if(url==null||!url.startsWith("https://"))return;
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception ex){android.widget.Toast.makeText(this,
            "No browser available",1).show();}
    }
    @Override public void onBackPressed(){
        if(browser!=null&&browser.canGoBack())browser.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}
        super.onDestroy();
    }
}