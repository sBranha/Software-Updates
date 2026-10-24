package com.newswave.live;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Phone-side, one-tap spoken briefing. No autonomous audio, external
 * background playback or unsupported Android Auto media routing.
 */
public final class NewsRadioActivity extends Activity {
    private static final int BG=0xff071422,INK=Color.WHITE,
        CYAN=0xff84e9ed,MUTED=0xffb0c6d9,GOLD=0xffffcf89;
    private LinearLayout root,details;
    private TextView status;
    private String city="",state="";
    private double lat=Double.NaN,lon=Double.NaN;
    private MyAreaData.Snapshot snapshot;
    private List<LocalNewspapers.Headline> sports;
    private boolean loading=false,ready=false,pendingSpeak=false,closed=false;
    private TextToSpeech voice;
    private long fetchedAt=0;
    private int generation=0;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        android.content.SharedPreferences prefs=getSharedPreferences("newswire-location",MODE_PRIVATE);
        city=getIntent().getStringExtra("city");state=getIntent().getStringExtra("state");
        if(city==null||city.isEmpty())city=prefs.getString("city","");
        if(state==null||state.isEmpty())state=prefs.getString("state","");
        lat=getIntent().getDoubleExtra("latitude",Double.NaN);
        lon=getIntent().getDoubleExtra("longitude",Double.NaN);
        if(Double.isNaN(lat))lat=Double.longBitsToDouble(prefs.getLong("lat",Double.doubleToLongBits(Double.NaN)));
        if(Double.isNaN(lon))lon=Double.longBitsToDouble(prefs.getLong("lon",Double.doubleToLongBits(Double.NaN)));
        voice=new TextToSpeech(this,code->runOnUiThread(()->{
            if(closed||voice==null)return;
            ready=code==TextToSpeech.SUCCESS;
            if(ready){
                voice.setLanguage(Locale.US);voice.setSpeechRate(.95f);
                if(pendingSpeak){pendingSpeak=false;play();}
            }else status.setText("Android Text-to-Speech isn't available.");
        }));
        draw();refresh();
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5);}
    private GradientDrawable card(){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff184059,0xff0c2033});
        d.setCornerRadius(dp(18));d.setStroke(dp(1),0xff317c92);return d;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private TextView button(String title,Runnable action){
        TextView b=text(title,13,INK,true);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14),dp(15),dp(14),dp(15));b.setBackground(card());
        b.setOnClickListener(v->action.run());return b;
    }
    private void add(LinearLayout parent,android.view.View v,int margin){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(margin);parent.addView(v,p);
    }
    private void draw(){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(15),dp(19),dp(15),dp(28));sc.addView(root);setContentView(sc);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);
        hero.setPadding(dp(20),dp(22),dp(20),dp(20));hero.setBackground(card());
        hero.addView(text("NEWSWIRE / AUDIO",11,CYAN,true));
        add(hero,text("YOUR CITY. YOUR NEWS.",26,INK,true),8);
        add(hero,text("Tap Play to hear a short, source-attributed local briefing.",13,MUTED,false),8);
        add(root,hero,0);
        status=text("Preparing local briefing…",12,CYAN,false);add(root,status,14);
        add(root,button("▶  PLAY MY NEWSWIRE BRIEFING",this::play),10);
        add(root,button("■  STOP PLAYBACK",()->{
            pendingSpeak=false;if(voice!=null)voice.stop();
            status.setText("Audio stopped.");
        }),9);
        add(root,button("↻  REFRESH THE NEWS & WEATHER",this::refresh),9);
        add(root,button("←  BACK TO NEWSWIRE",this::finish),9);
        details=new LinearLayout(this);details.setOrientation(1);add(root,details,16);
        add(root,text("Spoken briefing requires an explicit Play tap on your phone. "+
            "News headlines are attributed to publishers; sports stories are not live score feeds. "+
            "This is not a substitute for NWS warning sirens or Wireless Emergency Alerts.",
            11,MUTED,false),17);
        updateDetails();
    }
    private void updateDetails(){
        if(details==null)return;details.removeAllViews();
        add(details,text("TODAY'S AUDIO LINEUP",12,CYAN,true),0);
        if(snapshot==null){add(details,text("Waiting for conditions and news sources…",13,MUTED,false),12);return;}
        add(details,text("WEATHER: "+(snapshot.conditionsAvailable?
            snapshot.temperature+" · "+snapshot.condition:"NWS observations unavailable"),14,INK,true),10);
        add(details,text("WARNINGS: "+(!snapshot.warningsAvailable?"Feed unavailable":
            snapshot.warnings.isEmpty()?"No warnings reported at last check":
            snapshot.warnings.get(0)),13,
            snapshot.warningsAvailable&&snapshot.warnings.isEmpty()?MUTED:GOLD,false),10);
        int total=0;
        for(LocalNewspapers.Headline story:snapshot.headlines){
            add(details,text("LOCAL: "+story.title+" · "+story.source,12,INK,false),10);
            if(++total==3)break;
        }
        if(sports!=null&&!sports.isEmpty())add(details,text(
            "SPORTS REPORT: "+sports.get(0).title,12,INK,false),10);
        add(details,text("Last checked "+new SimpleDateFormat("h:mm a",Locale.US)
            .format(new Date(fetchedAt)),11,MUTED,false),13);
    }
    private void refresh(){
        if(loading){status.setText("Still checking local weather and news…");return;}
        if(voice!=null)voice.stop();
        loading=true;status.setText("Checking official conditions and attributed news…");
        final int rev=++generation;
        new Thread(()->{
            MyAreaData.Snapshot weather=null;
            List<LocalNewspapers.Headline> sport=null;
            if(!Double.isNaN(lat)&&!Double.isNaN(lon))
                weather=MyAreaData.load(lat,lon,city,state);
            try{sport=LocalNewspapers.topicHeadlines(city,state,"football");}
            catch(Exception e){android.util.Log.w("NewsWireRadio","Sports news unavailable",e);}
            final MyAreaData.Snapshot result=weather;
            final List<LocalNewspapers.Headline> sportsNews=sport;
            runOnUiThread(()->{
                if(closed||rev!=generation)return;
                loading=false;snapshot=result;sports=sportsNews;fetchedAt=System.currentTimeMillis();
                status.setText(result==null?
                    "No location available: save your ZIP or enable GPS.":
                    "Ready · checked "+new SimpleDateFormat("h:mm a",Locale.US)
                        .format(new Date(fetchedAt))+" · tap PLAY");
                updateDetails();
            });
        },"newswire-radio-data").start();
    }
    private void play(){
        if(snapshot==null){
            status.setText("Waiting for the news and weather to load.");return;
        }
        if(!ready){pendingSpeak=true;status.setText("Preparing phone voice…");return;}
        StringBuilder script=new StringBuilder("NewsWire local briefing. ");
        if(!city.isEmpty())script.append("Around ").append(city).append(". ");
        if(snapshot.conditionsAvailable)
            script.append("Current weather: ").append(snapshot.temperature.replace("°F"," degrees Fahrenheit"))
                .append(", ").append(snapshot.condition).append(". Wind ").append(snapshot.wind).append(". ");
        else script.append("Current weather observations are unavailable. ");
        if(!snapshot.warningsAvailable)
            script.append("Official NWS warning lookup unavailable. Verify emergency alerts. ");
        else if(!snapshot.warnings.isEmpty())
            script.append("Attention, NWS active alert: ").append(snapshot.warnings.get(0))
                .append(". For instructions, check the Emergency Center. ");
        else script.append("The NWS reported no active alerts at the last check. ");
        int count=0;
        for(LocalNewspapers.Headline h:snapshot.headlines){
            if(!NewsPreferences.allow(this,h.title))continue;
            script.append("Local headline from ").append(h.source).append(". ")
                .append(h.title).append(". ");
            if(++count>=3)break;
        }
        if(sports!=null&&!sports.isEmpty())
            script.append("In school sports reporting: ").append(sports.get(0).title).append(". ");
        script.append("NewsWire audio uses live provider data that can change.");
        voice.speak(script.toString(),TextToSpeech.QUEUE_FLUSH,null,"newswire_radio_"+fetchedAt);
        status.setText("Now speaking your NewsWire local briefing…");
    }
    @Override protected void onDestroy(){
        closed=true;generation++;
        if(voice!=null){voice.stop();voice.shutdown();voice=null;}
        super.onDestroy();
    }
}