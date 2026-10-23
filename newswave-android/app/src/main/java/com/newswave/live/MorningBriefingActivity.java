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

/** On-demand Good Morning dashboard. GPS only when already permitted;
 * background notification uses saved ZIP to avoid hidden location collection. */
public final class MorningBriefingActivity extends Activity {
    private static final int BG=0xff091426,INK=Color.WHITE,
        CYAN=0xff8deef3,MUTED=0xffb3cbdc,GOLD=0xffffca80;
    private LinearLayout content;
    private boolean loading=false,closed=false;
    private double lat=Double.NaN,lon=Double.NaN;
    private String city="",region="";
    private MyAreaData.Snapshot snapshot;
    private int generation=0;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        android.content.SharedPreferences area=
            getSharedPreferences("newswire-location",MODE_PRIVATE);
        city=area.getString("city","");region=area.getString("state","");
        lat=Double.longBitsToDouble(area.getLong("lat",Double.doubleToLongBits(Double.NaN)));
        lon=Double.longBitsToDouble(area.getLong("lon",Double.doubleToLongBits(Double.NaN)));
        render();
        refreshFromGps();
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5);}
    private GradientDrawable card(int first,int second,int stroke){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});
        g.setCornerRadius(dp(20));g.setStroke(dp(1),stroke);return g;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private TextView button(String label,Runnable click){
        TextView b=text(label,13,INK,true);b.setGravity(Gravity.CENTER);
        b.setBackground(card(0xff143953,0xff10253a,0xff36798d));
        b.setPadding(dp(14),dp(13),dp(14),dp(13));b.setOnClickListener(v->click.run());return b;
    }
    private void addLabel(String tag,String value,int accent){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);
        box.setBackground(card(0xff152d44,0xff0d2134,0xff315a70));
        box.setPadding(dp(17),dp(16),dp(17),dp(16));
        box.addView(text(tag,11,accent,true));
        TextView info=text(value,16,INK,true);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);
        ip.topMargin=dp(8);box.addView(info,ip);
        LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);
        outer.topMargin=dp(10);content.addView(box,outer);
    }
    private void render(){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        content=new LinearLayout(this);content.setOrientation(1);
        content.setPadding(dp(16),dp(22),dp(16),dp(35));sc.addView(content);setContentView(sc);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);
        hero.setPadding(dp(19),dp(23),dp(19),dp(23));
        hero.setBackground(card(0xff194963,0xff0b2538,0xff3689a3));
        hero.addView(text("NEWSWIRE / TODAY",11,CYAN,true));
        TextView title=text("GOOD MORNING",29,INK,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);
        tp.topMargin=dp(10);hero.addView(title,tp);
        hero.addView(text("Weather. Your town. Your headlines.",13,MUTED,false));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.bottomMargin=dp(13);content.addView(hero,hp);
        content.addView(button("↻  REFRESH THIS MORNING",this::refreshFromGps));
        String area=city.isEmpty()?"Current location / ZIP fallback":city+
            (region.isEmpty()?"":", "+region);
        TextView place=text("⌖ "+area,13,CYAN,true);
        place.setPadding(dp(4),dp(15),dp(4),dp(2));content.addView(place);
        if(loading && snapshot==null){
            addLabel("PREPARING YOUR BRIEFING","Checking NWS weather and local publishers…",CYAN);
        }else if(snapshot!=null){
            MyAreaData.Snapshot d=snapshot;
            addLabel("WEATHER RIGHT NOW",d.temperature+"  ·  "+d.condition+
                "\nWind "+d.wind,CYAN);
            String warnings=!d.warningsAvailable?
                "NWS warning check unavailable — see official alerts":
                d.warnings.isEmpty()?
                    "No active NWS alerts reported for this point at the last check":
                    "Active NWS alert: "+d.warnings.get(0);
            addLabel("OFFICIAL NWS ALERTS",warnings,
                d.warnings.isEmpty()&&d.warningsAvailable?CYAN:GOLD);
            if(!d.headlines.isEmpty()){
                int shown=0;
                for(LocalNewspapers.Headline headline:d.headlines){
                    if(!NewsPreferences.allow(this,headline.title))continue;
                    final LocalNewspapers.Headline link=headline;
                    LinearLayout row=new LinearLayout(this);row.setOrientation(1);
                    row.setBackground(card(0xff152c46,0xff101e33,0xff2d617d));
                    row.setPadding(dp(14),dp(14),dp(14),dp(14));
                    row.addView(text(headline.source.toUpperCase(Locale.US),11,CYAN,true));
                    TextView story=text(headline.title,14,INK,true);
                    story.setPadding(0,dp(7),0,dp(7));row.addView(story);
                    TextView save=button(NewsPreferences.isSaved(this,headline.url)?
                        "★ SAVED":"☆ SAVE THIS STORY",()->
                    {
                        boolean added=NewsPreferences.toggleBookmark(
                            this,link.title,link.source,link.url);
                        android.widget.Toast.makeText(this,
                            added?"Story saved":"Bookmark removed",0).show();
                        render();
                    });
                    row.addView(save);
                    story.setOnClickListener(v->open(link.url));
                    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
                    rp.topMargin=dp(10);content.addView(row,rp);
                    if(++shown==3)break;
                }
                if(shown==0)addLabel("YOUR NEWS","All returned headlines match topics you've hidden.",CYAN);
            }else addLabel("LOCAL NEWS","No current headlines returned; check NewsWire's full news feed.",CYAN);
        }else{
            addLabel("SET YOUR AREA","Allow GPS in NewsWire or save a five-digit ZIP on the home screen.",GOLD);
        }
        TextView personalized=button("★  MY PERSONALIZED NEWS",()->
            startActivity(new Intent(this,PersonalizedNewsActivity.class)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(13);content.addView(personalized,p);
        TextView back=button("←  BACK TO NEWSWIRE",this::finish);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);
        bp.topMargin=dp(9);content.addView(back,bp);
        TextView privacy=text("Morning notifications are optional and scheduled approximately by Android. " +
            "No background GPS is collected for a morning notification; the saved ZIP is used. " +
            "Warning information can be unavailable or delayed.",11,MUTED,false);
        privacy.setPadding(dp(4),dp(15),dp(4),0);content.addView(privacy);
    }
    private void open(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception error){android.widget.Toast.makeText(this,"No browser available",0).show();}
    }
    private void refreshFromGps(){
        if(loading)return;
        loading=true;
        final int revision=++generation;
        new Thread(()->{
            double nextLat=lat,nextLon=lon;
            String nextCity=city,nextRegion=region;
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||
               checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                try{
                    LocationManager mgr=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
                    Location recent=null;
                    for(String provider:mgr.getProviders(true)){
                        Location value=mgr.getLastKnownLocation(provider);
                        if(value!=null&&(recent==null||recent.getTime()<value.getTime()))recent=value;
                    }
                    if(recent!=null&&System.currentTimeMillis()-recent.getTime()<10*60*1000){
                        nextLat=recent.getLatitude();nextLon=recent.getLongitude();
                        nextCity="";nextRegion="";
                        try{
                            Geocoder geo=new Geocoder(this,Locale.US);
                            List<Address> list=geo.getFromLocation(nextLat,nextLon,1);
                            if(list!=null&&!list.isEmpty()){
                                Address addr=list.get(0);
                                nextCity=addr.getLocality();nextRegion=addr.getAdminArea();
                            }
                        }catch(Exception ignored){}
                    }
                }catch(Exception ignored){}
            }
            if(nextCity==null)nextCity="";
            if(nextRegion==null)nextRegion="";
            MyAreaData.Snapshot result=Double.isNaN(nextLat)||Double.isNaN(nextLon)?
                null:MyAreaData.load(nextLat,nextLon,nextCity,nextRegion);
            final String foundCity=nextCity,foundRegion=nextRegion;
            final double foundLat=nextLat,foundLon=nextLon;
            runOnUiThread(()->{
                if(closed||revision!=generation)return;
                city=foundCity;region=foundRegion;lat=foundLat;lon=foundLon;
                loading=false;snapshot=result;render();
            });
        },"newswire-morning").start();
        render();
    }
    @Override protected void onDestroy(){closed=true;generation++;super.onDestroy();}
}