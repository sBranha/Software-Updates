package com.newswave.live;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.net.Uri;
import android.view.View;
import java.util.ArrayList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Handler;
import android.os.Looper;
import java.util.HashSet;
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
    private LinearLayout stationList;
    private LinearLayout noaaList;
    private TextView noaaStatus;
    private List<LocalRadioDirectory.Station> noaaStations=new ArrayList<>();
    private boolean noaaLoading=false;
    private int noaaGeneration=0;
    private TextView stationStatus;
    private List<LocalRadioDirectory.Station> localStations=new ArrayList<>();
    private boolean stationsLoading=false;
    private int stationGeneration=0;
    private MediaPlayer streamPlayer;
    private LocalRadioDirectory.Station playing;
    private int playerGeneration=0;
    private EditText stationSearch;
    private TextView favoritesButton;
    private boolean favoritesOnly=false;
    private final Handler playerHandler=new Handler(Looper.getMainLooper());
    private Runnable connectionTimeout;
    private AudioManager manager;
    private boolean hasAudioFocus=false;
    private final AudioManager.OnAudioFocusChangeListener focusChange=code->{
        if(code==AudioManager.AUDIOFOCUS_LOSS||
           code==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            runOnUiThread(()->stopStream("Playback stopped for another audio app."));
    };
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
        draw();refresh();loadLocalStations();loadNoaaStations();
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
        add(root,text("NOAA WEATHER RADIO · LIVE AUDIO",13,CYAN,true),21);
        add(root,text("Select a third-party internet rebroadcast of NOAA Weather Radio where available. "+
            "Stream availability varies by area and provider. NewsWire cannot tune 162 MHz radio signals, "+
            "and streamed audio is not an emergency alert receiver.",12,MUTED,false),9);
        noaaStatus=text("Finding NOAA Weather Radio rebroadcasts for your area…",12,CYAN,false);
        add(root,noaaStatus,10);
        add(root,button("↻  FIND NOAA WEATHER RADIO STREAMS",this::loadNoaaStations),9);
        add(root,button("⌖  OFFICIAL NOAA RADIO STATION DIRECTORY",()->
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.weather.gov/nwr/stations"))),9);
        noaaList=new LinearLayout(this);noaaList.setOrientation(1);
        add(root,noaaList,10);
        add(root,text("LOCAL AM/FM STATIONS · LIVE STREAMS",13,CYAN,true),21);
        add(root,text("Nearby stations are discovered automatically from your current area. "+
            "Tap a station to listen live; broadcaster availability varies.",12,MUTED,false),9);
        stationSearch=new EditText(this);
        stationSearch.setSingleLine(true);
        stationSearch.setTextColor(INK);
        stationSearch.setHintTextColor(MUTED);
        stationSearch.setTextSize(15);
        stationSearch.setHint("Search stations by name or type");
        stationSearch.setPadding(dp(12),dp(12),dp(12),dp(12));
        stationSearch.setBackground(card());
        stationSearch.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                renderStations();
            }
            public void afterTextChanged(Editable e){}
        });
        add(root,stationSearch,10);
        favoritesButton=button("☆  SAVED STATIONS",()->{
            favoritesOnly=!favoritesOnly;
            renderStations();
        });
        add(root,favoritesButton,8);
        stationStatus=text("Finding nearby stations…",12,CYAN,false);
        add(root,stationStatus,9);
        add(root,button("↻  FIND LOCAL STATIONS",this::loadLocalStations),9);
        add(root,button("■  STOP LIVE RADIO",()->stopStream("Live station stopped.")),9);
        stationList=new LinearLayout(this);stationList.setOrientation(1);
        add(root,stationList,12);
        add(root,button("←  BACK TO NEWSWIRE",this::finish),13);
        details=new LinearLayout(this);details.setOrientation(1);add(root,details,16);
        renderStations();renderNoaaStations();
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
        stopStream(null);
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
    private void renderStations(){
        if(stationList==null)return;
        stationList.removeAllViews();
        List<LocalRadioDirectory.Station> bookmarks=RadioStationFavorites.list(this);
        if(favoritesButton!=null)favoritesButton.setText(favoritesOnly?
            "★  SAVED ONLY · TAP FOR ALL":
            "☆  SAVED STATIONS ("+bookmarks.size()+")");
        List<LocalRadioDirectory.Station> candidates=new ArrayList<>();
        HashSet<String> used=new HashSet<>();
        for(LocalRadioDirectory.Station s:bookmarks){
            if(used.add(s.stream))candidates.add(s);
        }
        if(!favoritesOnly)for(LocalRadioDirectory.Station s:localStations){
            if(used.add(s.stream))candidates.add(s);
        }
        String term=stationSearch==null?"":stationSearch.getText().toString()
            .trim().toLowerCase(Locale.US);
        int count=0;
        for(LocalRadioDirectory.Station station:candidates){
            if(!term.isEmpty()&&!station.name.toLowerCase(Locale.US).contains(term)
                &&!station.genre.toLowerCase(Locale.US).contains(term)
                &&!station.region.toLowerCase(Locale.US).contains(term))continue;
            LinearLayout block=new LinearLayout(this);
            block.setOrientation(1);block.setPadding(dp(12),dp(10),dp(12),dp(10));
            block.setBackground(card());
            LinearLayout heading=new LinearLayout(this);heading.setOrientation(0);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=text((playing!=null&&playing.stream.equals(station.stream)?
                "▶  ":"◉  ")+station.name,16,INK,true);
            heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            title.setOnClickListener(v->playStation(station));
            boolean saved=RadioStationFavorites.contains(this,station.stream);
            TextView star=text(saved?"★":"☆",26,saved?GOLD:CYAN,true);
            star.setContentDescription((saved?"Remove ":"Save ")+station.name+" favorite");
            star.setPadding(dp(12),dp(4),dp(3),dp(4));
            star.setOnClickListener(v->{
                boolean added=RadioStationFavorites.toggle(this,station);
                android.widget.Toast.makeText(this,
                    added?"Station saved":"Station removed from favorites",0).show();
                renderStations();
            });
            heading.addView(star);block.addView(heading);
            TextView info=text(station.subtitle(),11,MUTED,false);
            add(block,info,4);
            info.setOnClickListener(v->playStation(station));
            block.setOnClickListener(v->playStation(station));
            add(stationList,block,8);count++;
        }
        if(count==0){
            String empty=stationsLoading?"Finding stations near you…":
                favoritesOnly?"No saved stations match. Tap the star to save a station.":
                !term.isEmpty()?"No station matches your search.":
                "No local stations listed. Tap FIND LOCAL STATIONS to retry.";
            add(stationList,text(empty,12,MUTED,false),6);
        }
    }
    private void renderNoaaStations(){
        if(noaaList==null)return;
        noaaList.removeAllViews();
        if(noaaStations.isEmpty()){
            String message=noaaLoading?"Looking for rebroadcasts…":
                "No nearby HTTPS NOAA radio rebroadcast listed. "+
                "Use the official NOAA station directory for terrestrial frequencies. "+
                "Official NewsWire NWS alerts work separately from audio.";
            add(noaaList,text(message,12,MUTED,false),6);
            return;
        }
        for(LocalRadioDirectory.Station station:noaaStations){
            LinearLayout block=new LinearLayout(this);
            block.setOrientation(1);block.setPadding(dp(13),dp(12),dp(13),dp(12));
            block.setBackground(card());
            String marker=playing!=null&&playing.stream.equals(station.stream)?"▶  ":"◉  ";
            add(block,text(marker+station.name,15,INK,true),0);
            add(block,text(station.subtitle()+" · Third-party internet stream",11,MUTED,false),5);
            block.setOnClickListener(v->playStation(station));
            add(noaaList,block,8);
        }
    }
    private void loadNoaaStations(){
        if(noaaLoading)return;
        noaaLoading=true;
        if(noaaStatus!=null)noaaStatus.setText("Looking up nearby NOAA radio rebroadcasts…");
        renderNoaaStations();
        final int requested=++noaaGeneration;
        new Thread(()->{
            List<LocalRadioDirectory.Station> result=null;
            String error=null;
            try{result=NoaaRadioDirectory.nearby(lat,lon,state);}
            catch(Exception problem){
                android.util.Log.w("NewsWireRadio","NOAA directory unavailable",problem);
                error="NOAA stream directory unavailable. Try again or use the official station directory.";
            }
            final List<LocalRadioDirectory.Station> found=result;
            final String problem=error;
            runOnUiThread(()->{
                if(closed||requested!=noaaGeneration)return;
                noaaLoading=false;
                noaaStations=found==null?new ArrayList<>():found;
                if(noaaStatus!=null)noaaStatus.setText(problem!=null?problem:
                    noaaStations.isEmpty()?
                        "No geographically verified HTTPS NOAA rebroadcast found for this area.":
                        noaaStations.size()+" available NOAA radio rebroadcast(s) listed; tap to listen.");
                renderNoaaStations();
            });
        },"newswire-noaa-streams").start();
    }
    private void loadLocalStations(){
        if(stationsLoading)return;
        stationsLoading=true;
        if(stationStatus!=null)stationStatus.setText("Discovering local stations…");
        renderStations();
        final int current=++stationGeneration;
        // Only a foreground-opened radio page starts station discovery.
        new Thread(()->{
            List<LocalRadioDirectory.Station> found=null;
            String error=null;
            try{found=LocalRadioDirectory.nearby(lat,lon,city,state);}
            catch(Exception problem){
                android.util.Log.w("NewsWireRadio","Station directory unavailable",problem);
                error="Station directory unavailable. Check internet and retry.";
            }
            final List<LocalRadioDirectory.Station> stations=found;
            final String message=error;
            runOnUiThread(()->{
                if(closed||current!=stationGeneration)return;
                stationsLoading=false;
                localStations=stations==null?new ArrayList<>():stations;
                stationStatus.setText(message!=null?message:
                    localStations.isEmpty()?"No HTTPS radio streams listed for this area.":
                    localStations.size()+" station streams found near "+
                        (city.isEmpty()?"your location":city)+". Tap to listen.");
                renderStations();
            });
        },"newswire-radio-stations").start();
    }
    private void stopStream(String message){
        playerGeneration++;
        if(connectionTimeout!=null){
            playerHandler.removeCallbacks(connectionTimeout);
            connectionTimeout=null;
        }
        if(streamPlayer!=null){
            try{streamPlayer.reset();streamPlayer.release();}
            catch(Exception ignored){}
            streamPlayer=null;
        }
        playing=null;
        if(hasAudioFocus&&manager!=null){
            manager.abandonAudioFocus(focusChange);
            hasAudioFocus=false;
        }
        if(message!=null&&stationStatus!=null)stationStatus.setText(message);
        renderStations();renderNoaaStations();
    }
    private void playStation(LocalRadioDirectory.Station station){
        if(station==playing&&streamPlayer!=null){
            stopStream("Live station stopped.");
            return;
        }
        if(!station.stream.startsWith("https://")){
            stationStatus.setText("Stream unavailable in NewsWire.");
            return;
        }
        pendingSpeak=false;
        if(voice!=null)voice.stop();
        stopStream(null);
        final int revision=++playerGeneration;
        try{
            manager=(AudioManager)getSystemService(AUDIO_SERVICE);
            if(manager!=null){
                int granted=manager.requestAudioFocus(focusChange,
                    AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
                if(granted!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                    stationStatus.setText("Another app has audio. Try again.");
                    return;
                }
                hasAudioFocus=true;
            }
            MediaPlayer player=new MediaPlayer();
            streamPlayer=player;
            playing=station;
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(this,Uri.parse(station.stream));
            player.setOnPreparedListener(p->{
                if(closed||revision!=playerGeneration)return;
                if(connectionTimeout!=null){
                    playerHandler.removeCallbacks(connectionTimeout);
                    connectionTimeout=null;
                }
                try{
                    p.start();
                    if(stationStatus!=null)stationStatus.setText("▶ Live · "+station.name);
                    renderStations();renderNoaaStations();
                }catch(RuntimeException error){
                    stopStream("Station playback failed. Try another.");
                }
            });
            player.setOnErrorListener((p,what,extra)->{
                if(!closed&&revision==playerGeneration){
                    stopStream("This station's live stream could not play. Try another.");
                }
                return true;
            });
            player.prepareAsync();
            connectionTimeout=()->{
                if(!closed&&revision==playerGeneration)
                    stopStream("Station connection timed out. Try another.");
            };
            playerHandler.postDelayed(connectionTimeout,20000L);
            stationStatus.setText("Connecting to "+station.name+"…");
            renderStations();renderNoaaStations();
        }catch(Exception problem){
            android.util.Log.w("NewsWireRadio","Stream failed to start",problem);
            stopStream("Station unavailable right now. Choose a different station.");
        }
    }
    @Override protected void onStop(){
        // Don't unexpectedly keep station audio running in the background.
        stopStream(null);
        super.onStop();
    }
    @Override protected void onDestroy(){
        closed=true;generation++;stationGeneration++;noaaGeneration++;
        stopStream(null);
        if(voice!=null){voice.stop();voice.shutdown();voice=null;}
        super.onDestroy();
    }
}