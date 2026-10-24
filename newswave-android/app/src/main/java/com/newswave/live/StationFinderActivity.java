package com.newswave.live;

import android.Manifest;
import android.app.Activity;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/** GPS-aware local TV station guide. Curated stream links are verified for
 * Huntsville market; elsewhere offer attributed local news and source discovery.
 * Never label an external broadcaster page as an automatically playing stream.
 */
public final class StationFinderActivity extends Activity {
    private static final int BG=0xff091622,WHITE=Color.WHITE,CYAN=0xff86e7f1,
        MUTED=0xffb1c7d8,AMBER=0xffffc47a;
    private LinearLayout root,stations,localSources;
    private TextView area;
    private String city="",region="";
    private double lat=Double.NaN,lon=Double.NaN;
    private boolean closed=false;
    private int generation=0;
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5);}
    private GradientDrawable panel(){
        GradientDrawable b=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff163b51,0xff0b2031});
        b.setCornerRadius(dp(18));b.setStroke(dp(1),0xff356d85);return b;
    }
    private TextView text(String title,int size,int color,boolean strong){
        TextView t=new TextView(this);t.setText(title);t.setTextSize(size);t.setTextColor(color);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private TextView button(String label,Runnable action){
        TextView t=text(label,13,WHITE,true);t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14),dp(13),dp(14),dp(13));
        t.setBackground(panel());t.setOnClickListener(v->action.run());return t;
    }
    private void add(LinearLayout box,android.view.View view,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(top);box.addView(view,p);
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        android.content.SharedPreferences prefs=getSharedPreferences("newswire-location",MODE_PRIVATE);
        city=getIntent().getStringExtra("city");region=getIntent().getStringExtra("state");
        if(city==null||city.isEmpty())city=prefs.getString("city","");
        if(region==null||region.isEmpty())region=prefs.getString("state","");
        lat=getIntent().getDoubleExtra("latitude",Double.NaN);
        lon=getIntent().getDoubleExtra("longitude",Double.NaN);
        if(Double.isNaN(lat))lat=Double.longBitsToDouble(prefs.getLong("lat",Double.doubleToLongBits(Double.NaN)));
        if(Double.isNaN(lon))lon=Double.longBitsToDouble(prefs.getLong("lon",Double.doubleToLongBits(Double.NaN)));
        render();
        maybeGps();
        loadPublisherSources();
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(15),dp(18),dp(15),dp(28));
        scroll.addView(root);setContentView(scroll);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);
        hero.setPadding(dp(17),dp(18),dp(17),dp(18));hero.setBackground(panel());
        hero.addView(text("NEWSWIRE / YOUR AIRWAVES",11,CYAN,true));
        add(hero,text("LOCAL TV FINDER",27,WHITE,true),6);
        area=text(city.isEmpty()?"⌖ Save ZIP or allow location":"⌖ "+city+", "+region,12,MUTED,false);
        add(hero,area,8);add(root,hero,0);
        add(root,button("← BACK TO NEWSWIRE",this::finish),12);
        add(root,text("VERIFIED REGIONAL STATIONS",12,CYAN,true),17);
        stations=new LinearLayout(this);stations.setOrientation(1);add(root,stations,10);
        add(root,text("Local broadcasters stream only when their own schedules or rights allow it. "+
            "Some links lead to a station website instead of continuous live video.",12,MUTED,false),10);
        add(root,text("OTHER LOCAL PUBLISHERS",12,CYAN,true),22);
        localSources=new LinearLayout(this);localSources.setOrientation(1);add(root,localSources,9);
        add(root,button("⌕ SEARCH TV STATIONS FOR MY CITY",()->open(
            "https://www.google.com/search?q="+Uri.encode(city+" "+region+" local TV news stations live"))),13);
        add(root,button("↗ FCC BROADCAST STATION DIRECTORY",()->open("https://publicfiles.fcc.gov/")),9);
        add(root,text("NewsWire shows curated broadcasters only where we have sourced regional links. "+
            "Other locally reported publishers may include newspapers; they are labeled separately.",11,MUTED,false),15);
        refreshKnown();
    }
    private void station(String label,String affiliation,String source,String url){
        LinearLayout line=new LinearLayout(this);line.setOrientation(1);
        line.setPadding(dp(14),dp(14),dp(14),dp(14));line.setBackground(panel());
        line.addView(text(label+" · "+affiliation,15,WHITE,true));
        add(line,text(source,12,MUTED,false),5);
        line.setOnClickListener(v->open(url));add(stations,line,9);
    }
    private void refreshKnown(){
        stations.removeAllViews();
        if(!Double.isNaN(lat)&&!Double.isNaN(lon)){
            double dlat=lat-34.7304,dlon=(lon+86.5861)*Math.cos(Math.toRadians(lat));
            double miles=Math.sqrt(dlat*dlat+dlon*dlon)*69.0;
            if(miles<95){
                station("WAAY 31","ABC","Huntsville broadcaster · official website",
                    "https://www.waaytv.com/");
                station("WHNT News 19","CBS","Huntsville broadcaster · official website",
                    "https://whnt.com/");
                station("WAFF 48","NBC","Huntsville live news when scheduled",
                    "https://www.waff.com/livestream/");
                station("FOX 54 / WZDX","FOX","Huntsville region · official news website",
                    "https://www.rocketcitynow.com/");
                return;
            }
        }
        add(stations,text("Verified station shortcuts for this TV market are not yet installed. "+
            "Use the location-based directory and publisher links below; no unsupported station is invented.",
            13,AMBER,false),6);
    }
    private void loadPublisherSources(){
        if(city.isEmpty()||region.isEmpty()){
            add(localSources,text("Save a ZIP or enable GPS to see publishers reporting in your area.",
                13,MUTED,false),2);return;
        }
        final String c=city,s=region;
        final int rev=++generation;
        new Thread(()->{
            List<LocalNewspapers.Headline> feed=null;
            try{feed=LocalNewspapers.headlines(c,s);}
            catch(Exception e){android.util.Log.w("NewsWireTV","Local news feed unavailable",e);}
            final List<LocalNewspapers.Headline> result=feed;
            runOnUiThread(()->{
                if(closed||rev!=generation)return;
                localSources.removeAllViews();
                if(result==null||result.isEmpty()){
                    add(localSources,text("Publisher discovery unavailable. Try searching the TV directory.",13,MUTED,false),4);
                    return;
                }
                java.util.HashSet<String> seen=new java.util.HashSet<>();
                for(LocalNewspapers.Headline h:result){
                    if(h.source.isEmpty()||!seen.add(h.source))continue;
                    add(localSources,button(h.source+" · REPORTED IN "+c.toUpperCase(Locale.US),
                        ()->open(h.url)),8);
                    if(seen.size()>=8)break;
                }
            });
        },"newswire-stations").start();
    }
    private void maybeGps(){
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED
            &&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        new Thread(()->{
            try{
                LocationManager mgr=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
                Location fix=null;
                for(String p:mgr.getProviders(true)){
                    Location q=mgr.getLastKnownLocation(p);
                    if(q!=null&&(fix==null||q.getTime()>fix.getTime()))fix=q;
                }
                if(fix==null||System.currentTimeMillis()-fix.getTime()>600000)return;
                final double a=fix.getLatitude(),b=fix.getLongitude();
                String c="",s="";
                try{
                    List<Address> addresses=new Geocoder(this,Locale.US).getFromLocation(a,b,1);
                    if(addresses!=null&&!addresses.isEmpty()){
                        c=addresses.get(0).getLocality();s=addresses.get(0).getAdminArea();
                    }
                }catch(Exception ignored){}
                final String town=c==null?"":c,state=s==null?"":s;
                runOnUiThread(()->{
                    if(closed)return;
                    boolean moved=Double.isNaN(lat)||Math.abs(lat-a)>.035||Math.abs(lon-b)>.035;
                    if(!moved)return;
                    lat=a;lon=b;city=town;region=state;
                    area.setText("⌖ "+(city.isEmpty()?"Current GPS area":city+", "+region));
                    refreshKnown();loadPublisherSources();
                });
            }catch(Exception ex){android.util.Log.w("NewsWireTV","GPS unavailable",ex);}
        },"newswire-tv-location").start();
    }
    private void open(String url){
        if(!url.startsWith("https://"))return;
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){android.widget.Toast.makeText(this,"Browser unavailable",0).show();}
    }
    @Override protected void onDestroy(){closed=true;generation++;super.onDestroy();}
}