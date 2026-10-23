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
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/**
 * Phone-side weather-centric Drive Mode and user-requested spoken briefing.
 * Does not attempt to display unsupported media controls in Android Auto's
 * weather category or cover its safe radar view.
 * Spoken briefings require one explicit tap, and stop when the screen exits.
 */
public final class NewsWireDriveActivity extends Activity {
    private static final int BG=0xff071321,INK=Color.WHITE,CYAN=0xff77e7ed,
        MUTED=0xffacc5d3,GOLD=0xffffc47c;
    private static final int REQUEST_LOC=151;
    private LinearLayout main,details;
    private TextView status,locationLabel;
    private String city="",region="";
    private double latitude=Double.NaN,longitude=Double.NaN;
    private String lookupKey="";
    private boolean loading=false,closed=false;
    private MyAreaData.Snapshot data;
    private TextToSpeech speaker;
    private boolean speakerReady=false;
    private boolean pendingRead=false;
    private int generation=0;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        city=getIntent().getStringExtra("city");
        region=getIntent().getStringExtra("state");
        if(city==null)city="";if(region==null)region="";
        android.content.SharedPreferences saved=
            getSharedPreferences("newswire-location",MODE_PRIVATE);
        if(city.isEmpty())city=saved.getString("city","");
        if(region.isEmpty())region=saved.getString("state","");
        latitude=getIntent().getDoubleExtra("latitude",Double.NaN);
        longitude=getIntent().getDoubleExtra("longitude",Double.NaN);
        if(Double.isNaN(latitude))
            latitude=Double.longBitsToDouble(saved.getLong("lat",
                Double.doubleToLongBits(Double.NaN)));
        if(Double.isNaN(longitude))
            longitude=Double.longBitsToDouble(saved.getLong("lon",
                Double.doubleToLongBits(Double.NaN)));
        speaker=new TextToSpeech(this,code->runOnUiThread(()->{
            if(closed||speaker==null)return;
            speakerReady=code==TextToSpeech.SUCCESS;
            if(speakerReady){
                speaker.setLanguage(Locale.US);
                speaker.setSpeechRate(.94f);
                if(pendingRead){pendingRead=false;speak();}
            }else status.setText("Text-to-speech isn't available on this phone.");
        }));
        render();
        usePhoneLocation(false);
    }
    private int dp(float px){return (int)(px*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable surface(int from,int to,int edge){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{from,to});
        d.setCornerRadius(dp(18));d.setStroke(dp(1),edge);return d;
    }
    private TextView text(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);
        t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView button(String title,Runnable click){
        TextView t=text(title,13,INK,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14),dp(14),dp(14),dp(14));
        t.setBackground(surface(0xff1a425a,0xff0e273f,0xff31798c));
        t.setOnClickListener(v->click.run());return t;
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        main=new LinearLayout(this);main.setOrientation(1);
        main.setPadding(dp(15),dp(18),dp(15),dp(32));
        scroll.addView(main);setContentView(scroll);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);
        hero.setPadding(dp(19),dp(20),dp(19),dp(20));
        hero.setBackground(surface(0xff173c55,0xff0a1b2b,0xff417d93));
        hero.addView(text("NEWSWIRE / DRIVE",12,CYAN,true));
        TextView title=text("KNOW WHAT'S AHEAD",25,INK,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);
        tp.topMargin=dp(8);hero.addView(title,tp);
        TextView subtitle=text("Weather, official alerts, and a spoken local briefing.",
            13,MUTED,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin=dp(8);hero.addView(subtitle,sp);
        LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,-2);
        hl.bottomMargin=dp(13);main.addView(hero,hl);

        locationLabel=text(city.isEmpty()?"⌖  FOLLOWING GPS / ZIP BACKUP":
            "⌖  "+city+", "+region,13,CYAN,true);
        locationLabel.setPadding(dp(7),dp(10),dp(7),dp(10));
        main.addView(locationLabel);
        LinearLayout weather=new LinearLayout(this);weather.setOrientation(1);
        weather.setBackground(surface(0xff133548,0xff0c2434,0xff32687e));
        weather.setPadding(dp(16),dp(14),dp(16),dp(14));
        weather.addView(text("WEATHER ALONG YOUR DRIVE",11,CYAN,true));
        details=new LinearLayout(this);details.setOrientation(1);
        weather.addView(details);
        LinearLayout.LayoutParams wc=new LinearLayout.LayoutParams(-1,-2);
        wc.bottomMargin=dp(12);main.addView(weather,wc);
        updateDetails();

        status=text("Choose REFRESH LOCATION for current weather, or use saved ZIP.",
            12,MUTED,false);
        status.setPadding(dp(5),dp(8),dp(5),dp(14));main.addView(status);

        TextView speakButton=button("▶  SPEAK WEATHER & NEWS BRIEFING",this::speak);
        LinearLayout.LayoutParams speakLp=new LinearLayout.LayoutParams(-1,-2);
        speakLp.bottomMargin=dp(10);main.addView(speakButton,speakLp);
        LinearLayout row=new LinearLayout(this);
        TextView stop=button("■  STOP VOICE",()->{
            pendingRead=false;
            if(speaker!=null)speaker.stop();
        });
        TextView gps=button("⌖  REFRESH GPS",()->usePhoneLocation(true));
        LinearLayout.LayoutParams half1=new LinearLayout.LayoutParams(0,-2,1);
        half1.rightMargin=dp(5);row.addView(stop,half1);
        LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,-2,1);
        half2.leftMargin=dp(5);row.addView(gps,half2);
        main.addView(row);
        TextView map=button("◎  SATELLITE WEATHER RADAR",()->
            startActivity(new Intent(this,RadarActivity.class)));
        LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,-2);
        ml.topMargin=dp(10);main.addView(map,ml);
        TextView traffic=button("⇢  TRAFFIC & ROAD CLOSURES",()->
            startActivity(new Intent(this,TrafficActivity.class)
                .putExtra("state",region)));
        LinearLayout.LayoutParams tr=new LinearLayout.LayoutParams(-1,-2);
        tr.topMargin=dp(10);main.addView(traffic,tr);
        TextView back=button("←  BACK TO NEWSWIRE",this::finish);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(-1,-2);
        b.topMargin=dp(10);main.addView(back,b);
        TextView notice=text("Set this up while parked. Voice playback is manually started, " +
            "not automatic Android Auto audio. Live GPS and NWS data can be delayed. " +
            "Use official emergency alerts and obey local road closures.",
            11,MUTED,false);
        notice.setPadding(dp(4),dp(18),dp(4),0);main.addView(notice);
    }
    private void updateDetails(){
        if(details==null)return;
        details.removeAllViews();
        if(data==null){
            TextView wait=text(loading?"Loading official weather & warnings…":
                "Waiting for NWS forecast and local news.",13,MUTED,false);
            wait.setPadding(0,dp(14),0,0);details.addView(wait);return;
        }
        TextView temperature=text(data.temperature,42,INK,true);
        temperature.setPadding(0,dp(8),0,0);details.addView(temperature);
        details.addView(text(data.condition+"  •  Wind "+data.wind,13,MUTED,false));
        TextView warning=text(!data.warningsAvailable?
            "⚠ NWS warning lookup unavailable — check official alerts":
            data.warnings.isEmpty()?"✓ NWS reports no active alerts at this GPS point":
                "⚠ "+data.warnings.get(0),13,
            !data.warningsAvailable||!data.warnings.isEmpty()?GOLD:0xff8be0c4,true);
        warning.setPadding(0,dp(11),0,dp(5));details.addView(warning);
        if(!data.headlines.isEmpty()){
            TextView top=text("LOCAL HEADLINE  /  "+data.headlines.get(0).source,
                11,CYAN,true);
            top.setPadding(0,dp(12),0,dp(5));details.addView(top);
            details.addView(text(data.headlines.get(0).title,13,INK,false));
        }else{
            TextView none=text("Local headline feed not available for this area.",
                12,MUTED,false);
            none.setPadding(0,dp(12),0,dp(4));details.addView(none);
        }
    }
    private void usePhoneLocation(boolean prompt){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
            &&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            if(prompt)requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION},REQUEST_LOC);
            else refresh();
            return;
        }
        new Thread(()->{
            Location fix=null;
            try{
                LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
                for(String provider:lm.getProviders(true)){
                    Location previous=lm.getLastKnownLocation(provider);
                    if(previous!=null&&(fix==null||previous.getTime()>fix.getTime()))fix=previous;
                }
            }catch(Exception ex){android.util.Log.w("NewsWireDrive","No recent GPS fix",ex);}
            final Location recent=fix;
            if(recent!=null&&System.currentTimeMillis()-recent.getTime()<10*60*1000){
                latitude=recent.getLatitude();longitude=recent.getLongitude();
                try{
                    Geocoder geocoder=new Geocoder(this,Locale.US);
                    List<Address> addresses=geocoder.getFromLocation(latitude,longitude,1);
                    if(addresses!=null&&!addresses.isEmpty()){
                        String foundCity=addresses.get(0).getLocality();
                        String foundState=addresses.get(0).getAdminArea();
                        if(foundCity!=null&&!foundCity.isEmpty())city=foundCity;
                        if(foundState!=null&&!foundState.isEmpty())region=foundState;
                    }
                }catch(Exception ex){android.util.Log.w("NewsWireDrive","City lookup unavailable",ex);}
            }
            runOnUiThread(()->{
                if(closed)return;
                locationLabel.setText("⌖  "+(city.isEmpty()?"Current GPS location":
                    city+", "+region)+(recent==null?" · SAVED ZIP":" · GPS"));
                refresh();
            });
        },"newswire-drive-location").start();
    }
    private void refresh(){
        if(closed||Double.isNaN(latitude)||Double.isNaN(longitude)){
            status.setText("No GPS fix or saved ZIP. Set your local area in NewsWire.");
            return;
        }
        final double lat=latitude,lon=longitude;
        final String cityName=city,stateName=region;
        String key=String.format(Locale.US,"%.4f/%.4f/%s/%s",lat,lon,cityName,stateName);
        if(loading&&lookupKey.equals(key))return;
        lookupKey=key;loading=true;updateDetails();
        int rev=++generation;
        new Thread(()->{
            MyAreaData.Snapshot snapshot=MyAreaData.load(lat,lon,cityName,stateName);
            runOnUiThread(()->{
                if(closed||generation!=rev)return;
                loading=false;data=snapshot;
                status.setText("Updated NWS weather and local headlines. Tap SPEAK to hear them.");
                updateDetails();
            });
        },"newswire-drive-nws").start();
    }
    private void speak(){
        if(data==null){
            status.setText("Waiting for an official NWS update before speaking.");
            return;
        }
        if(!speakerReady){pendingRead=true;
            status.setText("Preparing phone speech. Please wait…");return;}
        StringBuilder speech=new StringBuilder("NewsWire driving weather briefing. ");
        if(!city.isEmpty())speech.append("Near ").append(city).append(". ");
        if(data.conditionsAvailable)speech.append("Current temperature ")
            .append(data.temperature.replace("°F"," degrees Fahrenheit"))
            .append(". ").append(data.condition)
            .append(". Wind ").append(data.wind).append(". ");
        else speech.append("Current conditions unavailable. ");
        if(!data.warningsAvailable)
            speech.append("The National Weather Service warning check is unavailable. Check official alerts. ");
        else if(!data.warnings.isEmpty())
            speech.append("Attention. Active National Weather Service alert: ")
                .append(data.warnings.get(0)).append(". ");
        else speech.append("NWS reports no active alerts at this point at the time checked. This is not a guarantee of safety. ");
        if(!data.headlines.isEmpty())
            speech.append("Latest local headline. ").append(data.headlines.get(0).title).append(". ");
        speech.append("Conditions can change. Stay alert.");
        speaker.speak(speech.toString(),TextToSpeech.QUEUE_FLUSH,null,"newswire_drive_briefing");
        status.setText("Speaking your latest local briefing…");
    }
    @Override public void onRequestPermissionsResult(
        int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQUEST_LOC){
            boolean allowed=false;
            for(int result:results)if(result==PackageManager.PERMISSION_GRANTED)allowed=true;
            if(allowed)usePhoneLocation(false);
            else{status.setText("GPS permission denied. Using saved ZIP area.");refresh();}
        }
    }
    @Override protected void onDestroy(){
        closed=true;generation++;
        if(speaker!=null){speaker.stop();speaker.shutdown();speaker=null;}
        super.onDestroy();
    }
}