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
import android.os.Looper;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Four integrated phone features: Next 12 Hours, School Closings,
 * Community Events, and My Day. Official data vs publisher reports
 * are always labelled; no unverified closure or event date is asserted.
 */
public final class DayPlannerActivity extends Activity {
    private static final int BG=0xff071728,FG=Color.WHITE,
        CYAN=0xff8ae5ee,MUTED=0xffb4c9d7,AMBER=0xffffcd85,SOFT=0xff8ce5bb;
    private final ExecutorService workers=Executors.newFixedThreadPool(4);
    private final Handler refreshWatchdog=new Handler(Looper.getMainLooper());
    private ScrollView scroll;
    private LinearLayout root,overview,hoursContainer,schoolContainer,eventContainer;
    private TextView status;
    private View hoursAnchor,schoolAnchor,eventAnchor,dayAnchor;
    private double lat=Double.NaN,lon=Double.NaN;
    private String city="",region="",requestedSection="day";
    private MyAreaData.Snapshot weather;
    private DayPlannerData.Forecast hours;
    private List<LocalNewspapers.Headline> schoolReports,communityReports;
    private boolean closed=false,refreshing=false,sourceError=false;
    private int generation=0,pending=0;
    private long updatedAt=0;
    private long refreshStartedAt=0;

    private int dp(int v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private GradientDrawable background(int a,int b,int stroke){
        GradientDrawable drawable=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,new int[]{a,b});
        drawable.setCornerRadius(dp(18));drawable.setStroke(dp(1),stroke);
        return drawable;
    }
    private TextView text(String value,int size,int tone,boolean strong){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(tone);t.setTextSize(size);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView button(String title,Runnable action){
        TextView b=text(title,12,FG,true);b.setGravity(Gravity.CENTER);
        b.setBackground(background(0xff1b4861,0xff123048,0xff39778f));
        b.setPadding(dp(11),dp(12),dp(11),dp(12));
        b.setOnClickListener(v->action.run());return b;
    }
    private void add(LinearLayout parent,View child,int marginTop){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(marginTop);parent.addView(child,lp);
    }
    private LinearLayout card(LinearLayout parent,String caption,int highlight){
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);
        card.setPadding(dp(15),dp(15),dp(15),dp(16));
        card.setBackground(background(0xff153951,0xff0b2133,0xff336a80));
        card.addView(text(caption,12,highlight,true));
        add(parent,card,12);return card;
    }
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Intent from=getIntent();
        city=from.getStringExtra("city");region=from.getStringExtra("state");
        requestedSection=from.getStringExtra("section");
        if(requestedSection==null)requestedSection="day";
        if(city==null)city="";if(region==null)region="";
        android.content.SharedPreferences prefs=getSharedPreferences("newswire-location",MODE_PRIVATE);
        if(city.isEmpty())city=prefs.getString("city","");
        if(region.isEmpty())region=prefs.getString("state","");
        lat=from.getDoubleExtra("latitude",Double.NaN);
        lon=from.getDoubleExtra("longitude",Double.NaN);
        if(Double.isNaN(lat))
            lat=Double.longBitsToDouble(prefs.getLong("lat",Double.doubleToLongBits(Double.NaN)));
        if(Double.isNaN(lon))
            lon=Double.longBitsToDouble(prefs.getLong("lon",Double.doubleToLongBits(Double.NaN)));
        build();
        refresh();
        // Read current position if it was already granted, never prompt unexpectedly.
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
            ||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)
            locateOnce();
    }
    private void build(){
        scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(14),dp(17),dp(14),dp(35));
        scroll.addView(root);setContentView(scroll);

        LinearLayout hero=card(root,"NEWSWIRE / LOCAL INTELLIGENCE",CYAN);
        TextView title=text("MY DAY / LIVE",29,FG,true);add(hero,title,8);
        add(hero,text("Forecast • Schools • Community • Your daily overview",13,MUTED,false),7);
        status=text("Connecting to sources…",12,CYAN,false);add(hero,status,12);
        LinearLayout controls=new LinearLayout(this);
        TextView back=button("← BACK",this::finish);
        TextView refresh=button("↻ REFRESH ALL",this::refresh);
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);
        half.rightMargin=dp(5);controls.addView(back,half);
        LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,-2,1);
        second.leftMargin=dp(5);controls.addView(refresh,second);add(root,controls,12);

        LinearLayout daily=card(root,"01  /  AT A GLANCE",CYAN);
        dayAnchor=daily;overview=new LinearLayout(this);overview.setOrientation(1);add(daily,overview,7);
        LinearLayout forecast=card(root,"02  /  YOUR NEXT 12 HOURS",CYAN);
        hoursAnchor=forecast;hoursContainer=new LinearLayout(this);
        hoursContainer.setOrientation(1);add(forecast,hoursContainer,8);
        add(forecast,button("◎ SATELLITE RADAR",()->
            startActivity(new Intent(this,RadarActivity.class))),12);

        LinearLayout schools=card(root,"03  /  SCHOOL CLOSINGS & DELAYS",AMBER);
        schoolAnchor=schools;schoolContainer=new LinearLayout(this);schoolContainer.setOrientation(1);
        add(schools,text("Official notices are separate from publisher reporting. Never assume school is open from an empty news feed.",12,MUTED,false),8);
        add(schools,schoolContainer,8);
        add(schools,button("↗ CHECK OFFICIAL SCHOOL CLOSURES",this::openOfficialSchoolBoard),12);
        add(schools,button("★ FOLLOW MY SCHOOL / DISTRICT",this::followSchool),8);

        LinearLayout eventCard=card(root,"04  /  WHAT'S HAPPENING AROUND ME",CYAN);
        eventAnchor=eventCard;eventContainer=new LinearLayout(this);
        eventContainer.setOrientation(1);
        add(eventCard,text("Local event coverage is news reporting, not a confirmed event calendar. Always verify event times and cancellations.",12,MUTED,false),8);
        add(eventCard,eventContainer,8);
        add(eventCard,button("↗ VERIFIED TOURISM & EVENT CALENDAR",this::openOfficialEvents),12);
        add(eventCard,button("⌖ FOLLOW ANOTHER TOWN",this::followCity),8);
        add(root,text("Sources: NOAA/NWS forecasts and warnings, official school notification portals, state tourism listings and attributed news reports. Availability varies by location. Weather and closure information can change without notice.",11,MUTED,false),18);
        renderAll();
        View target="hours".equals(requestedSection)?hoursAnchor:
            "schools".equals(requestedSection)?schoolAnchor:
            "events".equals(requestedSection)?eventAnchor:dayAnchor;
        scroll.postDelayed(()->scroll.smoothScrollTo(0,target.getTop()),160);
    }
    private void addLine(LinearLayout box,String value,int color){
        TextView t=text(value,13,color,false);add(box,t,9);
    }
    private void renderAll(){
        if(overview==null)return;
        overview.removeAllViews();hoursContainer.removeAllViews();
        schoolContainer.removeAllViews();eventContainer.removeAllViews();
        String place=city.isEmpty()?"Your current area":city+(region.isEmpty()?"":", "+region);
        addLine(overview,"⌖  "+place,CYAN);
        if(weather!=null){
            String conditions=weather.conditionsAvailable?
                weather.temperature+" · "+weather.condition+" · Wind "+weather.wind:
                "NWS observations unavailable";
            addLine(overview,"☀  "+conditions,FG);
            String warning=!weather.warningsAvailable?
                "NWS warning service unavailable — verify official alerts":
                weather.warnings.isEmpty()?"NWS reports no active alerts at this point":
                "⚠ ACTIVE ALERT: "+weather.warnings.get(0);
            addLine(overview,warning,weather.warningsAvailable&&weather.warnings.isEmpty()?SOFT:AMBER);
            if(!weather.headlines.isEmpty()){
                LocalNewspapers.Headline headline=weather.headlines.get(0);
                TextView h=button("NEWS · "+headline.title,()->open(headline.url));
                add(overview,h,12);
            }
        }else addLine(overview,"Weather & NWS warnings loading or unavailable",MUTED);
        if(hours!=null&&!hours.hours.isEmpty()){
            DayPlannerData.Hour first=hours.hours.get(0);
            addLine(overview,"NEXT HOUR · "+first.temperature+" · Rain "+first.rain,FG);
        }
        addLine(overview,"SCHOOLS · Check your district or official state board",AMBER);
        addLine(overview,"EVENTS · See current local listings and coverage below",MUTED);
        String roads=TrafficSources.forState(region).agency;
        add(overview,button("⇢ ROAD CONDITIONS · "+roads,()->
            startActivity(new Intent(this,TrafficActivity.class).putExtra("state",region))),13);

        if(hours==null){
            addLine(hoursContainer,"Official NWS hourly forecast is loading or unavailable.",MUTED);
        }else{
            for(DayPlannerData.Hour h:hours.hours){
                LinearLayout row=new LinearLayout(this);row.setOrientation(0);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView time=text(h.time,12,CYAN,true);
                time.setWidth(dp(65));row.addView(time);
                LinearLayout details=new LinearLayout(this);details.setOrientation(1);
                row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
                details.addView(text(h.temperature+"  ·  "+h.condition,13,FG,true));
                details.addView(text("Rain "+h.rain+"   ·   Wind "+h.wind,12,MUTED,false));
                add(hoursContainer,row,11);
            }
            addLine(hoursContainer,"NOAA/NWS hourly forecast; future conditions are estimates, not observed weather.",MUTED);
        }
        String school= getSharedPreferences("newswire-day",MODE_PRIVATE).getString("favorite_school","");
        if(!school.isEmpty())addLine(schoolContainer,"★ Following: "+school,CYAN);
        if("AL".equalsIgnoreCase(region)||"Alabama".equalsIgnoreCase(region))
            addLine(schoolContainer,"Alabama has an official state Department of Education closure board. Tap the official link to confirm date and district.",FG);
        else addLine(schoolContainer,"The official Alabama board is only for Alabama. For other states, confirm with your district directly.",MUTED);
        if(schoolReports==null)addLine(schoolContainer,"Local closure coverage loading or unavailable.",MUTED);
        else if(schoolReports.isEmpty())
            addLine(schoolContainer,"No relevant publisher articles returned. This does NOT mean every school is open.",MUTED);
        else{
            addLine(schoolContainer,"RECENT PUBLISHER REPORTING · VERIFY DATES",AMBER);
            int count=0;
            for(LocalNewspapers.Headline story:schoolReports){
                if(story.publishedMillis>0&&
                    System.currentTimeMillis()-story.publishedMillis>7L*24*60*60*1000)continue;
                add(schoolContainer,button(story.title,()->open(story.url)),8);
                if(++count>=5)break;
            }
            if(count==0)addLine(schoolContainer,"No recent closure reports returned. Verify via official school sources.",MUTED);
        }
        if(communityReports==null)addLine(eventContainer,"Nearby event reporting loading or unavailable.",MUTED);
        else if(communityReports.isEmpty())addLine(eventContainer,"No event coverage returned. Open the official tourism event calendar for confirmed listings.",MUTED);
        else{
            int count=0;
            for(LocalNewspapers.Headline story:communityReports){
                if(story.publishedMillis>0&&System.currentTimeMillis()-story.publishedMillis>30L*24*60*60*1000)
                    continue;
                add(eventContainer,button(story.title+" · "+story.source,()->open(story.url)),8);
                if(++count>=6)break;
            }
            if(count==0)addLine(eventContainer,"No recent reports returned. Open the live event calendar below.",MUTED);
        }
    }
    private void refresh(){
        if(refreshing){
            long seconds=(System.currentTimeMillis()-refreshStartedAt)/1000;
            if(seconds<35){
                android.widget.Toast.makeText(this,
                    "Still checking live sources ("+seconds+" seconds).",0).show();
                return;
            }
            // An old network task may be stuck. Allow retry and reject its late result.
            generation++;
        }
        refreshing=true;sourceError=false;pending=4;
        refreshStartedAt=System.currentTimeMillis();
        // Keep the previous information visible, but label it as stale until updated.
        // If a feed fails, the user can still read the previous content with its timestamp.
        final int rev=++generation;
        // Release the refresh control even if one feed never returns.
        refreshWatchdog.postDelayed(()->{
            if(closed||rev!=generation||!refreshing)return;
            generation++; // Ignore late results from this check.
            refreshing=false;
            sourceError=true;
            status.setText("Refresh timed out. Some sources are unavailable; previous data may be outdated. Tap REFRESH ALL to retry.");
            renderAll();
        },35000L);
        status.setText("Refreshing official sources… Previous check: "+
            (updatedAt==0?"never":new SimpleDateFormat("h:mm a",Locale.US)
                .format(new Date(updatedAt)))+" · updating");
        renderAll();
        workers.execute(()->{
            MyAreaData.Snapshot data=null;
            try{
                if(Double.isFinite(lat)&&Double.isFinite(lon))
                    data=MyAreaData.load(lat,lon,city,region);
            }catch(Exception error){android.util.Log.w("NewsWireDay","Current weather unavailable",error);}
            final MyAreaData.Snapshot result=data;
            runOnUiThread(()->{
                if(closed||rev!=generation)return;
                if(result!=null)weather=result;
                complete(rev,result==null);
            });
        });
        workers.execute(()->{
            DayPlannerData.Forecast data=null;
            try{data=DayPlannerData.hourly(lat,lon);}
            catch(Exception error){android.util.Log.w("NewsWireDay","Hourly NWS forecast unavailable",error);}
            final DayPlannerData.Forecast result=data;
            runOnUiThread(()->{
                if(closed||rev!=generation)return;
                if(result!=null)hours=result;
                complete(rev,result==null);
            });
        });
        workers.execute(()->{
            List<LocalNewspapers.Headline> result=null;
            try{result=LocalNewspapers.topicHeadlines(city,region,"schools");}
            catch(Exception error){android.util.Log.w("NewsWireDay","School reports unavailable",error);}
            final List<LocalNewspapers.Headline> found=result;
            runOnUiThread(()->{
                if(closed||rev!=generation)return;
                if(found!=null)schoolReports=found;
                complete(rev,found==null);
            });
        });
        workers.execute(()->{
            List<LocalNewspapers.Headline> result=null;
            try{result=LocalNewspapers.topicHeadlines(city,region,"events");}
            catch(Exception error){android.util.Log.w("NewsWireDay","Events reporting unavailable",error);}
            final List<LocalNewspapers.Headline> found=result;
            runOnUiThread(()->{
                if(closed||rev!=generation)return;
                if(found!=null)communityReports=found;
                complete(rev,found==null);
            });
        });
    }
    private void complete(int rev,boolean unavailable){
        if(closed||rev!=generation)return;
        sourceError|=unavailable;
        pending--;
        if(pending<=0){
            refreshing=false;updatedAt=System.currentTimeMillis();
            status.setText("Checked "+
                new SimpleDateFormat("h:mm a",Locale.US).format(new Date(updatedAt))+
                (sourceError?" · Some sources unavailable; earlier results may remain":
                    " · All four sources checked"));
        }else status.setText("Refreshing… "+(4-pending)+" of 4 source checks completed");
        renderAll();
    }
    private void locateOnce(){
        workers.execute(()->{
            try{
                LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
                Location newest=null;
                for(String provider:lm.getProviders(true)){
                    Location old=lm.getLastKnownLocation(provider);
                    if(old!=null&&(newest==null||old.getTime()>newest.getTime()))newest=old;
                }
                if(newest==null||System.currentTimeMillis()-newest.getTime()>10*60*1000)return;
                double newLat=newest.getLatitude(),newLon=newest.getLongitude();
                String newCity="",newRegion="";
                try{
                    List<Address> addresses=new Geocoder(this,Locale.US)
                        .getFromLocation(newLat,newLon,1);
                    if(addresses!=null&&!addresses.isEmpty()){
                        newCity=addresses.get(0).getLocality();
                        newRegion=addresses.get(0).getAdminArea();
                    }
                }catch(Exception ignored){}
                final String newTown=newCity==null?"":newCity,
                    newState=newRegion==null?"":newRegion;
                runOnUiThread(()->{
                    if(closed)return;
                    boolean moved=Double.isNaN(lat)||Math.abs(newLat-lat)>.035
                        ||Math.abs(newLon-lon)>.035;
                    if(moved){
                        lat=newLat;lon=newLon;city=newTown;region=newState;
                        if(refreshing){generation++;refreshing=false;}
                        refresh();
                    }
                });
            }catch(Exception error){android.util.Log.w("NewsWireDay","GPS check unavailable",error);}
        });
    }
    private void followSchool(){
        android.widget.EditText entry=new android.widget.EditText(this);
        entry.setSingleLine(true);
        entry.setHint("School or district name");
        entry.setText(getSharedPreferences("newswire-day",MODE_PRIVATE)
            .getString("favorite_school",""));
        new AlertDialog.Builder(this).setTitle("Follow a school")
            .setMessage("Save a school/district as your reference. For verified changes, always check the district or official board.")
            .setView(entry)
            .setPositiveButton("Save",(dialog,which)->{
                getSharedPreferences("newswire-day",MODE_PRIVATE).edit()
                    .putString("favorite_school",entry.getText().toString().trim()).apply();
                renderAll();
            }).setNegativeButton("Cancel",null).show();
    }
    private void followCity(){
        android.widget.Toast.makeText(this,
            "NewsWire follows your GPS when allowed. Change the saved ZIP on the main screen.",1).show();
    }
    private void openOfficialSchoolBoard(){
        if("AL".equalsIgnoreCase(region)||"Alabama".equalsIgnoreCase(region)){
            open("https://schoolnotification.alsde.edu/SchoolClosuresPublic.aspx");
        }else{
            // No unsupported state link is claimed to be an official closure feed.
            android.widget.Toast.makeText(this,
                "Select your local school district website to verify closures. Alabama's official state feed is supported.",1).show();
        }
    }
    private void openOfficialEvents(){
        String state=region.toUpperCase(Locale.US);
        if("AL".equals(state)||"ALABAMA".equals(state))open("https://alabama.travel/events/");
        else if("FL".equals(state)||"FLORIDA".equals(state))
            open("https://www.visitflorida.com/things-to-do/events/calendar/");
        else open("https://www.ticketmaster.com/");
    }
    private void open(String uri){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(uri)));}
        catch(Exception ignored){android.widget.Toast.makeText(this,"Browser unavailable",0).show();}
    }
    @Override protected void onDestroy(){
        closed=true;generation++;workers.shutdownNow();super.onDestroy();
    }
}