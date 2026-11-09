package com.newswire.vehicleporttester;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Parked-only proof of concept: live NWS warning polygons on a separate
 * OpenStreetMap/Leaflet phone map. Does not modify Ford factory navigation.
 * NWS warning boundaries are NOT radar storm tracks and NOT forecasts of path.
 * No native vehicle, diagnostic bus or Android Auto USB connection is opened.
 */
public final class StormWarningMapLabActivity extends Activity implements LocationListener {
    private static final int FINE_REQUEST=961;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private LocationManager locations;
    private WebView browser;
    private TextView status,warningSummary;
    private boolean waitingForFix=false,webReady=false;
    private double lastLat=Double.NaN,lastLon=Double.NaN;
    private String pendingScript="";
    private long fixRequestedAt=0;

    private int dp(float a){return Math.round(getResources().getDisplayMetrics().density*a);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xff07101b);
        getWindow().setNavigationBarColor(0xff07101b);
        locations=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff07101b);root.setPadding(dp(12),dp(14),dp(12),dp(10));
        setContentView(root);
        TextView title=new TextView(this);
        title.setText("NWS WARNING MAP • EXPERIMENT");
        title.setTextSize(17);title.setTextColor(0xff42dce9);title.setTypeface(null,1);
        root.addView(title);
        TextView note=new TextView(this);
        note.setText("PHONE MAP ONLY • Not the Ford SYNC navigation map. NWS warning polygons show warned areas, NOT a moving-storm track. Do not use this experiment for driving or safety decisions.");
        note.setTextColor(0xffffbf73);note.setTextSize(12);
        root.addView(note);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(13);
        status.setText("Ready • parked-only test of public warning-map technology.");
        root.addView(status);
        Button load=new Button(this);load.setText("LOAD LIVE WARNING AREAS NEAR PHONE");
        load.setAllCaps(false);load.setTextColor(Color.WHITE);
        load.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff176377));
        root.addView(load,new LinearLayout.LayoutParams(-1,dp(49)));
        load.setOnClickListener(v->loadFromPhoneGps());
        warningSummary=new TextView(this);warningSummary.setTextColor(0xffa9c1d0);
        warningSummary.setTextSize(12);
        warningSummary.setText("The map starts empty. Tap LOAD while parked. Data comes from the National Weather Service.");
        root.addView(warningSummary);
        browser=new WebView(this);
        browser.setBackgroundColor(0xff0e1b2e);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(false);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){
                webReady=true;
                if(!pendingScript.isEmpty()){v.evaluateJavascript(pendingScript,null);pendingScript="";}
            }
        });
        root.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        browser.loadDataWithBaseURL("https://unpkg.com/",html(),"text/html","UTF-8",null);
    }
    @Override public void onDestroy(){
        cancelLocation();
        if(browser!=null){browser.destroy();browser=null;}
        super.onDestroy();
    }
    private String html(){
        return "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0'>"+
        "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>"+
        "<style>html,body,#map{height:100%;margin:0;background:#152537;font:12px sans-serif;} .leaflet-control-attribution{font-size:9px!important;}</style></head>"+
        "<body><div id='map'></div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"+
        "<script>let map,warningLayer,positionPin; try{"+
        "map=L.map('map',{zoomControl:true}).setView([34.2,-86.4],8);"+
        "L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:17,attribution:'&copy; OpenStreetMap contributors'}).addTo(map);"+
        "}catch(e){document.body.innerHTML='<p style=\"color:white;padding:12px\">Map library could not load. Check internet connection.</p>';};"+
        "function showWarnings(lat,lon,features){ if(!map)return;"+
        "map.setView([lat,lon],10);if(warningLayer)map.removeLayer(warningLayer);"+
        "if(positionPin)map.removeLayer(positionPin);"+
        "positionPin=L.circleMarker([lat,lon],{radius:6,weight:2,color:'#00dceb',fillColor:'#fff',fillOpacity:1}).addTo(map).bindPopup('Phone location (approximate)');"+
        "warningLayer=L.geoJSON({type:'FeatureCollection',features:features},{style:function(f){"+
        "let event=(f.properties&&f.properties.event||'').toLowerCase();"+
        "let c=event.indexOf('tornado')>=0?'#ff245a':event.indexOf('severe thunderstorm')>=0?'#ffa500':'#f5dd53';"+
        "return {color:c,fillColor:c,fillOpacity:0.20,weight:3};},onEachFeature:function(f,layer){"+
        "let txt=(f.properties&&f.properties.event||'NWS warning area');"+
        "layer.bindPopup(txt.replace(/[<>&\"']/g,''));}}).addTo(map);"+
        "};</script></body></html>";
    }
    private void js(String script){
        if(browser==null)return;
        if(webReady)browser.evaluateJavascript(script,null);
        else pendingScript=script;
    }
    private void loadFromPhoneGps(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},FINE_REQUEST);
            return;
        }
        if(locations==null||!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            status.setText("GPS disabled • enable Location in Android settings.");return;
        }
        try{
            Location last=locations.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(last!=null&&SystemClock.elapsedRealtimeNanos()-last.getElapsedRealtimeNanos()<120_000_000_000L
                &&last.hasAccuracy()&&last.getAccuracy()<=100){
                onLocationChanged(last);
                return;
            }
            waitingForFix=true;fixRequestedAt=SystemClock.elapsedRealtime();
            status.setText("Waiting for a current phone GPS fix…");
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);
            ui.postDelayed(()->{
                if(waitingForFix){
                    cancelLocation();
                    status.setText("No accurate GPS fix in 20 seconds. Try outside while parked.");
                }
            },20000);
        }catch(SecurityException e){status.setText("Phone location permission not available.");}
    }
    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] results){
        super.onRequestPermissionsResult(req,perms,results);
        if(req==FINE_REQUEST){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)loadFromPhoneGps();
            else status.setText("Location denied • map cannot look up nearby warning areas.");
        }
    }
    @Override public void onLocationChanged(Location loc){
        if(loc==null||!loc.hasAccuracy()||loc.getAccuracy()>100)return;
        cancelLocation();
        lastLat=loc.getLatitude();lastLon=loc.getLongitude();
        String pos=String.format(Locale.US,"showWarnings(%.7f,%.7f,[])",lastLat,lastLon);
        js(pos);
        status.setText("Phone GPS positioned • checking NWS active alerts…");
        fetchWarnings(lastLat,lastLon);
    }
    private void cancelLocation(){
        waitingForFix=false;
        try{if(locations!=null)locations.removeUpdates(this);}catch(Exception ignored){}
    }
    private void fetchWarnings(double lat,double lon){
        new Thread(()->{
            HttpURLConnection connection=null;
            try{
                String url=String.format(Locale.US,
                    "https://api.weather.gov/alerts/active?point=%.5f,%.5f",lat,lon);
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent","VehicleCapabilityExplorer/0.8.7 (weather map integration prototype)");
                connection.setRequestProperty("Accept","application/geo+json");
                int code=connection.getResponseCode();
                if(code!=200)throw new IllegalStateException("NWS HTTP "+code);
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=connection.getInputStream()){
                    byte[] chunk=new byte[4096];int n;
                    while((n=in.read(chunk))!=-1){
                        out.write(chunk,0,n);
                        if(out.size()>1_250_000)throw new IllegalStateException("NWS data too large");
                    }
                }
                JSONObject payload=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
                JSONArray features=payload.optJSONArray("features");
                if(features==null)features=new JSONArray();
                JSONArray polygons=new JSONArray();
                StringBuilder events=new StringBuilder();
                int count=0;
                for(int i=0;i<features.length();i++){
                    JSONObject f=features.optJSONObject(i);
                    if(f==null)continue;
                    JSONObject properties=f.optJSONObject("properties");
                    String label=properties==null?"NWS alert":properties.optString("event","NWS alert");
                    if(events.length()<1200)events.append("• ").append(label).append("\n");
                    JSONObject geometry=f.optJSONObject("geometry");
                    if(geometry!=null&&!"null".equals(geometry.toString())){
                        JSONObject safeFeature=new JSONObject();
                        safeFeature.put("type","Feature");
                        safeFeature.put("geometry",geometry);
                        safeFeature.put("properties",new JSONObject().put("event",label));
                        polygons.put(safeFeature);
                    }
                    count++;
                }
                final int alerts=count;
                final int withPolygons=polygons.length();
                final String eventLabels=events.toString();
                final String script=String.format(Locale.US,
                   "showWarnings(%.7f,%.7f,%s)",lat,lon,polygons.toString());
                ui.post(()->{
                    if(isFinishing())return;
                    js(script);
                    warningSummary.setText(alerts==0?
                        "No active NWS alerts returned for this point at request time. This is NOT an all-clear or substitute for official warnings.":
                        alerts+" active NWS alert(s) affecting selected point, "+withPolygons+
                        " polygon geometry provided:\n"+eventLabels+
                        "Some alerts may have no polygon. These are warning areas, NOT predicted storm tracks.");
                    status.setText("Live NWS request completed • separate phone map • no Ford overlay");
                });
            }catch(Exception e){
                final String error=e.getClass().getSimpleName()+": "+e.getMessage();
                ui.post(()->{
                    if(isFinishing())return;
                    status.setText("NWS request unavailable • "+error);
                    warningSummary.setText("No new warning result available. Do not interpret this as no warnings.");
                });
            }finally{
                if(connection!=null)connection.disconnect();
            }
        },"nws-warning-readonly-test").start();
    }
}
