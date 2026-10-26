package com.newswave.live;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.location.Geocoder;
import android.location.Address;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.os.Bundle;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;
import android.webkit.GeolocationPermissions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends Activity {
  private static final int BG=Color.rgb(10,15,29), PANEL=Color.rgb(22,31,49), FG=Color.WHITE,
      MUTED=Color.rgb(155,174,198), ACCENT=Color.rgb(56,195,222), EDGE=Color.rgb(45,60,78);
  private final Channel[] channels = {
    new Channel("foxweather","FOX Weather","Weather","24/7 free weather coverage","https://www.foxweather.com/live",false),
    new Channel("weatherwise","WeatherWise","Weather","Forecast, radar, lightning and alerts","https://www.weatherwise.live/",false),
    new Channel("nwsradar","National Weather Service Radar","Weather","Official U.S. radar and warnings","https://radar.weather.gov/",false),
    new Channel("nws","NWS Huntsville","Weather","Tennessee Valley forecast and alerts","https://www.weather.gov/hun/",false),
    new Channel("abc","ABC News Live","National","Free 24/7 national news","https://abcnews.com/live",false),
    new Channel("cbs","CBS News 24/7","National","Free national and regional news","https://www.cbsnews.com/live/",false),
    new Channel("nbc","NBC News NOW","National","Free live national news coverage","https://www.nbcnews.com/now",false),
    new Channel("fox","FOX News","National","Live channel; subscription or TV login required","https://www.fox.com/stream/fox-news",true),
    new Channel("cnn","CNN","National","Live channel; subscription or TV login required","https://www.cnn.com/",true),
    new Channel("msnow","MS NOW (formerly MSNBC)","National","Live channel; provider access may be required","https://www.ms.now/live",true),
    new Channel("fox10","FOX 10 Mobile","Local","Mobile, Alabama newscasts and storm radar","https://www.fox10tv.com/livestream/",false),
    new Channel("waff","WAFF 48 Huntsville","Local","Tennessee Valley live news and forecast","https://www.waff.com/livestream/",false),
    new Channel("wkrg","WKRG News 5","Local","Gulf Coast and Mobile news","https://www.wkrg.com/",false),
    new Channel("whnt","WHNT News 19","Local","North Alabama weather and news","https://whnt.com/",false)
  };
  static class Channel {
    final String id,name,category,note,url;
    final boolean signIn;
    Channel(String a,String b,String c,String d,String e,boolean f){id=a;name=b;category=c;note=d;url=e;signIn=f;}
  }
  private SharedPreferences prefs;
  private static final int MORNING_NOTIFICATION_PERMISSION=149;
  private void openPersonalized(){
    startActivity(new Intent(this,PersonalizedNewsActivity.class));
  }
  private Intent localIntent(Class<?> target){
    Intent output=new Intent(this,target);
    String areaCity=city,areaRegion=state;
    if(myAreaSnapshot!=null&&!Double.isNaN(dashboardLatitude)){
      String resolved=myAreaSnapshot.area;
      int comma=resolved.lastIndexOf(',');
      if(comma>0){
        areaCity=resolved.substring(0,comma).trim();
        areaRegion=resolved.substring(comma+1).trim();
      }
    }
    output.putExtra("city",areaCity);output.putExtra("state",areaRegion);
    if(!Double.isNaN(dashboardLatitude)&&!Double.isNaN(dashboardLongitude)){
      output.putExtra("latitude",dashboardLatitude);
      output.putExtra("longitude",dashboardLongitude);
    }
    return output;
  }
  private void openLiveCenter(String section){
    Intent display=localIntent(LiveCenterActivity.class);
    display.putExtra("section",section);
    startActivity(display);
  }
  private void openTV(){
    startActivity(localIntent(LiveTVActivity.class));
  }
  private void openRadio(){
    startActivity(localIntent(NewsRadioActivity.class));
  }
  private void addHomeWidget(){
    android.appwidget.AppWidgetManager manager=
      android.appwidget.AppWidgetManager.getInstance(this);
    android.content.ComponentName widget=new android.content.ComponentName(
      this,NewsWireWidget.class);
    try{
      if(Build.VERSION.SDK_INT>=26 &&manager.isRequestPinAppWidgetSupported()){
        if(manager.requestPinAppWidget(widget,null,null))return;
      }
    }catch(RuntimeException error){
      android.util.Log.w("NewsWireWidget","Launcher declined widget pin request",error);
    }
    // Samsung/other launchers may refuse programmatic pinning, or the current
    // Home screen may lack enough free cells. Offer the reliable manual path.
    new AlertDialog.Builder(this).setTitle("Add the NewsWire widget")
      .setMessage("On your Home screen, press and hold an empty area, choose Widgets, then NewsWire. The compact widget needs a free 2 by 2 space. If the screen is full, choose another Home page.")
      .setPositiveButton("Got it",null).show();
  }
  private void openDay(String section){
    Intent day=new Intent(this,DayPlannerActivity.class);
    String areaCity=city,areaRegion=state;
    if(myAreaSnapshot!=null&&!Double.isNaN(dashboardLatitude)){
      String resolved=myAreaSnapshot.area;
      int comma=resolved.lastIndexOf(',');
      if(comma>0){
        areaCity=resolved.substring(0,comma).trim();
        areaRegion=resolved.substring(comma+1).trim();
      }
    }
    day.putExtra("city",areaCity);
    day.putExtra("state",areaRegion);
    day.putExtra("section",section);
    if(!Double.isNaN(dashboardLatitude)&&!Double.isNaN(dashboardLongitude)){
      day.putExtra("latitude",dashboardLatitude);
      day.putExtra("longitude",dashboardLongitude);
    }
    startActivity(day);
  }
  private void openMorning(){
    startActivity(new Intent(this,MorningBriefingActivity.class));
  }
  private void toggleMorningBriefing(){
    if(!MorningBriefingWorker.enabled(this)
        &&Build.VERSION.SDK_INT>=33
        &&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            !=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
          MORNING_NOTIFICATION_PERMISSION);
      return;
    }
    boolean enabled=!MorningBriefingWorker.enabled(this);
    MorningBriefingWorker.configure(this,enabled);
    dashboard();
    android.widget.Toast.makeText(this,enabled?
      "Good Morning notifications on. Android chooses an approximate morning delivery time.":
      "Good Morning notifications turned off.",1).show();
  }
  // The live My Area dashboard is driven by a foreground GPS fix when allowed.
  // Saved ZIP coordinates are the reliable fallback.
  private LinearLayout myAreaCard;
  private MyAreaData.Snapshot myAreaSnapshot;
  private boolean dashboardActive=false,areaLoading=false;
  private String loadedAreaKey="";
  private String areaRefreshMessage="";
  private int areaRequestGeneration=0;
  private final android.os.Handler areaRefreshHandler=
      new android.os.Handler(android.os.Looper.getMainLooper());
  private double dashboardLatitude=Double.NaN,dashboardLongitude=Double.NaN;
  private LocationManager dashboardLocationManager;
  private LocationListener dashboardLocationListener;
  private static final int DASHBOARD_LOCATION_REQUEST=146;
  private static final int DRIVE_FINE_LOCATION_REQUEST=147;
  private static final int DRIVE_NOTIFICATION_REQUEST=148;
  private boolean driveAlertsEnabled(){
    return getSharedPreferences(DriveWarningService.PREF,MODE_PRIVATE)
      .getBoolean("enabled",false);
  }
  private void requestDriveAlerts(){
    if(driveAlertsEnabled()){
      getSharedPreferences(DriveWarningService.PREF,MODE_PRIVATE)
        .edit().putBoolean("enabled",false).apply();
      Intent stop=new Intent(this,DriveWarningService.class)
        .setAction(DriveWarningService.ACTION_STOP);
      startService(stop);
      dashboard();
      return;
    }
    if(Build.VERSION.SDK_INT>=23
       &&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION},DRIVE_FINE_LOCATION_REQUEST);
      return;
    }
    if(Build.VERSION.SDK_INT>=33
       &&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},DRIVE_NOTIFICATION_REQUEST);
      return;
    }
    getSharedPreferences(DriveWarningService.PREF,MODE_PRIVATE)
      .edit().putBoolean("enabled",true).apply();
    try{
      startForegroundService(new Intent(this,DriveWarningService.class));
      dashboard();
    }catch(Exception error){
      getSharedPreferences(DriveWarningService.PREF,MODE_PRIVATE)
        .edit().putBoolean("enabled",false).apply();
      android.widget.Toast.makeText(this,
        "Couldn't start Drive Alerts. Check GPS and notification permissions.",1).show();
      dashboard();
    }
  }
  private static final int NEWS_NOTIFICATION_REQUEST=145;
  private AppUpdateManager playUpdates;
  private boolean updateAvailable=false;
  private boolean checkingUpdate=false;
  private boolean updateDownloaded=false;
  private boolean updatePromptVisible=false;
  private TextView updateAction;
  private long lastUpdatePoll=0;
  // The Google Play Store owns ordinary background auto-updates. This listener
  // only completes optional in-app flexible updates initiated by the user.
  private final InstallStateUpdatedListener playUpdateListener=state->{
    if(state.installStatus()==InstallStatus.DOWNLOADED){
      runOnUiThread(()->{
        updateDownloaded=true;
        renderUpdateAction();
        promptToFinishUpdate();
      });
    }else if(state.installStatus()==InstallStatus.FAILED){
      runOnUiThread(()->{
        checkingUpdate=false;
        android.widget.Toast.makeText(this,
          "Google Play download failed; you can retry in Play Store.",1).show();
      });
    }
  };
  private boolean localNewsNotificationEnabled(){
    return getSharedPreferences("newswire-news-alerts",MODE_PRIVATE).getBoolean("enabled",false);
  }
  private LinearLayout root, list, tabBar;
  private EditText search;
  private WebView web;
  private static final int WEB_LOCATION_REQUEST = 143;
  private static final int NEWS_LOCATION_REQUEST = 144;
  private GeolocationPermissions.Callback pendingLocationCallback;
  private String pendingLocationOrigin;
  private FrameLayout fullscreen;
  private View custom;
  private WebChromeClient.CustomViewCallback customCallback;
  private Channel current;
  private String tab="News";
  private String zip="",city="",state="";
  private double postalLat=Double.NaN,postalLon=Double.NaN;
  private String query="";
  // Foreground-only location-following for the News tab; saved ZIP remains unchanged.
  private LocationManager newsLocationManager;
  private LocationListener newsLocationListener;
  private boolean newsPermissionRequested=false;
  private boolean newsScreenVisible=false;
  private boolean newsGpsActive=false;
  private boolean newsGeocoding=false;
  private String newsGpsCity="",newsGpsRegion="";
  private double newsGpsLatitude=Double.NaN,newsGpsLongitude=Double.NaN;
  private Location lastNewsLookup;
  private int newsGeoGeneration=0;
  private String newsFeedKey="",newsFeedError="";
  private boolean newsFeedLoading=false;
  private List<LocalNewspapers.Headline> newsHeadlines=new ArrayList<>();
  private final String[] tabs={"Local","National","Weather","Radar","Favorites"};
  private List<LocalNewspapers.Headline> nationalHeadlines=new ArrayList<>();
  private boolean nationalLoading=false;
  private long nationalFetchedAt=0;
  private String nationalFeedError="";

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    prefs=getSharedPreferences("newswave-favorites",MODE_PRIVATE);
    android.content.SharedPreferences loc=getSharedPreferences("newswire-location",MODE_PRIVATE);
    zip=loc.getString("zip","");city=loc.getString("city","");state=loc.getString("state","");
    postalLat=Double.longBitsToDouble(loc.getLong("lat",Double.doubleToLongBits(Double.NaN)));
    postalLon=Double.longBitsToDouble(loc.getLong("lon",Double.doubleToLongBits(Double.NaN)));
    dashboard();
    if(getIntent()!=null && getIntent().getBooleanExtra("open_news",false)){
      openSection("News");
      getIntent().removeExtra("open_news");
    }
    NewsMonitorWorker.schedule(this,localNewsNotificationEnabled());
    if(MorningBriefingWorker.enabled(this))
      MorningBriefingWorker.configure(this,true);
    playUpdates=AppUpdateManagerFactory.create(this);
    playUpdates.registerListener(playUpdateListener);
    checkForUpdates(false);
  }
  private void renderUpdateAction(){
    if(updateAction==null)return;
    updateAction.setText(updateDownloaded?"✓  UPDATE DOWNLOADED · INSTALL NOW":
      updateAvailable?"↑  NEWSWIRE UPDATE READY IN GOOGLE PLAY":
      "↑  CHECK FOR APP UPDATES");
  }
  private void openNewsWirePlayListing(){
    Uri market=Uri.parse("market://details?id="+getPackageName());
    try{startActivity(new Intent(Intent.ACTION_VIEW,market).setPackage("com.android.vending"));}
    catch(Exception first){
      openBrowser("https://play.google.com/store/apps/details?id="+getPackageName());
    }
  }
  private void promptToFinishUpdate(){
    if(!updateDownloaded || updatePromptVisible || isFinishing() ||
       (Build.VERSION.SDK_INT>=17 && isDestroyed()))return;
    updatePromptVisible=true;
    new AlertDialog.Builder(this)
      .setTitle("NewsWire update downloaded")
      .setMessage("Google Play finished downloading the update. Install it and restart NewsWire now?")
      .setPositiveButton("Install", (dialog,which)->{
        updatePromptVisible=false;
        playUpdates.completeUpdate().addOnFailureListener(error->{
          android.widget.Toast.makeText(this,
            "Could not finish the update. Try again or open Google Play.",1).show();
        });
      })
      .setNegativeButton("Later",(dialog,which)->updatePromptVisible=false)
      .setOnCancelListener(dialog->updatePromptVisible=false)
      .show();
  }
  private void checkForUpdates(boolean interactive){
    if(playUpdates==null)playUpdates=AppUpdateManagerFactory.create(this);
    if(interactive&&updateDownloaded){promptToFinishUpdate();return;}
    if(checkingUpdate)return;
    long now=System.currentTimeMillis();
    if(!interactive&&now-lastUpdatePoll<5*60*1000L)return;
    checkingUpdate=true;lastUpdatePoll=now;
    playUpdates.getAppUpdateInfo().addOnSuccessListener(info->{
      checkingUpdate=false;
      if(info.installStatus()==InstallStatus.DOWNLOADED){
        updateDownloaded=true;renderUpdateAction();
        if(interactive)promptToFinishUpdate();
        return;
      }
      int availability=info.updateAvailability();
      boolean available=availability==UpdateAvailability.UPDATE_AVAILABLE;
      // Flexible is less disruptive; fall back to immediate only when Play permits it.
      int type=info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)?
        AppUpdateType.FLEXIBLE:
        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)?
          AppUpdateType.IMMEDIATE:-1;
      updateAvailable=available;
      renderUpdateAction();
      if(!interactive)return; // Never interrupt Google's automatic background update policy.
      if(available && type>=0){
        playUpdates.startUpdateFlow(info,this,
          AppUpdateOptions.newBuilder(type).build())
          .addOnFailureListener(error->{
            android.widget.Toast.makeText(this,
              "Could not start in-app update. Open the NewsWire Play Store page.",1).show();
            openNewsWirePlayListing();
          });
      }else if(available){
        android.widget.Toast.makeText(this,
          "New version available; open Google Play to install it.",1).show();
        openNewsWirePlayListing();
      }else if(availability==UpdateAvailability.UPDATE_NOT_AVAILABLE){
        android.widget.Toast.makeText(this,
          "Google Play has not offered a newer NewsWire version to this device yet.",1).show();
      }else if(availability==UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS){
        android.widget.Toast.makeText(this,
          "An in-app update is already in progress.",1).show();
      }else{
        android.widget.Toast.makeText(this,
          "Google Play could not confirm your update status. Open Play Store.",1).show();
        openNewsWirePlayListing();
      }
    }).addOnFailureListener(error->{
      checkingUpdate=false;
      if(interactive){
        android.widget.Toast.makeText(this,
          "Cannot check updates through Google Play. Opening the store.",1).show();
        openNewsWirePlayListing();
      }
    });
  }
  private void showSmartNewsSettings(){
    final String[] labels={
      "Important headlines (only when the publisher headline indicates urgency)",
      "School closures and delays",
      "General local headlines"
    };
    final String[] topic={"breaking","schools","local"};
    SharedPreferences prefs=getSharedPreferences("newswire-news-alerts",MODE_PRIVATE);
    boolean[] values=new boolean[3];
    for(int i=0;i<3;i++)values[i]=prefs.getBoolean("topic_"+topic[i],true);
    new AlertDialog.Builder(this)
      .setTitle("Smart News Alerts")
      .setMultiChoiceItems(labels,values,(dialog,index,checked)->values[index]=checked)
      .setPositiveButton("Save preferences",(dialog,which)->{
        SharedPreferences.Editor editor=prefs.edit();
        for(int i=0;i<3;i++)editor.putBoolean("topic_"+topic[i],values[i]);
        editor.apply();
        NewsMonitorWorker.schedule(this,localNewsNotificationEnabled());
        android.widget.Toast.makeText(this,
          "News alert preferences saved. Checks are periodic, not immediate.",1).show();
      })
      .setNegativeButton("Cancel",null).show();
  }
  private void toggleNewsNotifications(){
    if(!localNewsNotificationEnabled() && Build.VERSION.SDK_INT>=33
       &&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NEWS_NOTIFICATION_REQUEST);
      return;
    }
    boolean enabled=!localNewsNotificationEnabled();
    getSharedPreferences("newswire-news-alerts",MODE_PRIVATE)
      .edit().putBoolean("enabled",enabled).apply();
    NewsMonitorWorker.schedule(this,enabled);
    dashboard();
    android.widget.Toast.makeText(this,enabled?
      "Local headline alerts are on. Updates are checked hourly.":
      "Local headline notifications are off.",1).show();
  }
  // Always show the version of the package actually installed on the device.
  // Never hardcode a release string in this UI again.
  private String installedVersion(){
    try{
      android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
      return info.versionName!=null ? info.versionName : "unknown";
    }catch(Exception ignored){return "unknown";}
  }
  private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
  private GradientDrawable atmosphere(){
    return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      new int[]{0xff0c2540,0xff081320,0xff0c101f});
  }
  private GradientDrawable radiantCard(int left,int right,int edge){
    GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      new int[]{left,right});
    g.setCornerRadius(dp(22));g.setStroke(dp(1),edge);
    return g;
  }
  private GradientDrawable box(int color,int radius){
    GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
    d.setStroke(dp(1),EDGE);return d;
  }
  private TextView text(String s,int size,int color,boolean bold){
    TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
    if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    return t;
  }
  private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
  private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
  private TextView button(String label,Runnable onTap){
    TextView t=text(label,14,FG,true);t.setGravity(Gravity.CENTER);
    t.setBackground(box(PANEL,12));pad(t,12,12);t.setOnClickListener(v->onTap.run());return t;
  }

  // Categories are intentionally publisher-controlled. Public users cannot add or edit menu buttons.
  private void dashboard(){
    stopDashboardGps();
    dashboardActive=true;
    newsScreenVisible=false;
    current=null;query="";stopNewsGps();if(web!=null){respondToLocationRequest(false);web.destroy();web=null;}
    root=column();root.setBackground(atmosphere());setContentView(root);
    ScrollView sc=new ScrollView(this);LinearLayout content=column();pad(content,18,20);
    sc.addView(content);root.addView(sc);
    LinearLayout hero=column();hero.setBackground(radiantCard(0xff152f4c,0xff0d192b,0xff2e6174));
    pad(hero,19,20);
    TextView eyebrow=text("NEWSWIRE   /   LIVE",11,0xff62dbe6,true);hero.addView(eyebrow);
    TextView brand=text("YOUR WORLD.\nIN THE KNOW.",27,FG,true);
    LinearLayout.LayoutParams bp0=lp(-1,-2);bp0.topMargin=dp(8);hero.addView(brand,bp0);
    TextView sub=text("Local news   •   Storm radar   •   Your teams",13,0xffbfd4e6,false);
    LinearLayout.LayoutParams su=lp(-1,-2);su.topMargin=dp(9);hero.addView(sub,su);
    LinearLayout.LayoutParams heroLp=lp(-1,-2);heroLp.bottomMargin=dp(17);content.addView(hero,heroLp);
    myAreaCard=column();
    myAreaCard.setBackground(radiantCard(0xff10364b,0xff102235,0xff2b6373));
    pad(myAreaCard,17,17);
    LinearLayout.LayoutParams areaLp=lp(-1,-2);areaLp.bottomMargin=dp(17);
    content.addView(myAreaCard,areaLp);
    renderMyArea();
    LinearLayout locale=column();locale.setBackground(box(PANEL,18));pad(locale,15,16);
    TextView label=text(zip.isEmpty()?"SET YOUR LOCAL AREA":"LOCAL AREA  •  "+city+", "+state,15,FG,true);
    locale.addView(label);
    TextView hint=text("Enter your ZIP once for regional news, weather and school scores.",12,MUTED,false);
    LinearLayout.LayoutParams hp=lp(-1,-2);hp.topMargin=dp(7);locale.addView(hint,hp);
    LinearLayout ziprow=row();EditText entry=new EditText(this);entry.setSingleLine(true);
    entry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    entry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
    entry.setText(zip);entry.setHint("5-digit ZIP");entry.setTextSize(16);entry.setTextColor(FG);
    entry.setHintTextColor(MUTED);entry.setBackground(box(BG,12));pad(entry,11,8);
    ziprow.addView(entry,new LinearLayout.LayoutParams(0,dp(50),1));
    TextView save=button("SAVE",()->resolveZip(entry.getText().toString().trim()));
    LinearLayout.LayoutParams bp=lp(-2,-2);bp.leftMargin=dp(9);ziprow.addView(save,bp);
    LinearLayout.LayoutParams zr=lp(-1,-2);zr.topMargin=dp(12);locale.addView(ziprow,zr);
    LinearLayout.LayoutParams cp=lp(-1,-2);cp.bottomMargin=dp(20);content.addView(locale,cp);
    // Local first, National second; leave the rest accessible without crowding the home screen.
    categoryTile(content,"LOCAL NEWS","Headlines following your location","⌖",
      0xff2b65c3,()->openSection("News"));
    categoryTile(content,"NATIONAL NEWS","Today's U.S. headlines & broadcasters","▣",
      0xff215d88,()->openSection("National"));
    LinearLayout weatherTools=dashboardGroup(content,"WEATHER & WARNINGS",
      "Forecast • radar • satellite map • emergency center","☁",0xff097e98);
    categoryTile(weatherTools,"WEATHER","Local NWS forecasts & radar","☀",
      0xff097e98,()->openSection("Weather"));
    categoryTile(weatherTools,"LIVE SATELLITE MAP","Warning polygons • roads • radar","◈",
      0xff075f71,()->openLiveCenter("map"));
    categoryTile(weatherTools,"EMERGENCY CENTER","Official warnings and expiry times","⚠",
      0xff81414a,()->openLiveCenter("alerts"));
    LinearLayout roadTools=dashboardGroup(content,"ON THE ROAD",
      "Driving weather • road closures • GPS storm alerts","↗",0xff135879);
    categoryTile(roadTools,"NEWSWIRE DRIVE","Spoken weather • GPS • storm alerts","↗",
      0xff135879,this::openDrive);
    categoryTile(roadTools,"TRAFFIC & ROAD CLOSURES","DOT incidents and maps","⇢",
      0xff684124,this::openTraffic);
    LinearLayout listenTools=dashboardGroup(content,"WATCH & LISTEN",
      "Live TV • local radio • NOAA Weather Radio","▶",0xff624277);
    categoryTile(listenTools,"LIVE TV","Local and national TV news","▣",
      0xff2a5281,this::openTV);
    categoryTile(listenTools,"NEWSWIRE RADIO","Local radio and NOAA weather broadcasts","▶",
      0xff624277,this::openRadio);
    LinearLayout dayTools=dashboardGroup(content,"MY DAY & COMMUNITY",
      "School news • sports • events • personal briefing","★",0xff146577);
    categoryTile(dayTools,"SPORTS","Friday night scores • nearby teams","★",
      0xffa15620,this::openFootballBoard);
    categoryTile(dayTools,"MY NEWS","Follow towns and favorite publishers","★",
      0xff235286,this::openPersonalized);
    categoryTile(dayTools,"GOOD MORNING","Weather and local news briefing","☀",
      0xff8a542e,this::openMorning);
    categoryTile(dayTools,"MY DAY AT A GLANCE","Weather • notices • roads • events","◈",
      0xff146577,()->openDay("day"));
    categoryTile(dayTools,"NEXT 12 HOURS","Hourly NWS forecast • rain • wind","☀",
      0xff235c86,()->openDay("hours"));
    categoryTile(dayTools,"SCHOOL CLOSINGS","Official notices and local coverage","⚠",
      0xff88602a,()->openDay("schools"));
    categoryTile(dayTools,"AROUND ME","Festivals • concerts • community events","✦",
      0xff4b3889,()->openDay("events"));
    categoryTile(dayTools,"HOME SCREEN WIDGET","Weather, alerts and headlines","▤",
      0xff235b55,this::addHomeWidget);
    LinearLayout driveCard=column();
    driveCard.setBackground(radiantCard(0xff58321f,0xff1b2638,0xff91663d));
    pad(driveCard,16,16);
    TextView driveTitle=text("⚠  STORM AHEAD ALERTS",15,0xffffdfac,true);
    driveCard.addView(driveTitle);
    TextView driveExplain=text(
      "Optional driving warning monitor: checks active NWS tornado warnings at your GPS position and along your current direction of travel.",12,FG,false);
    LinearLayout.LayoutParams explp=lp(-1,-2);explp.topMargin=dp(8);
    driveCard.addView(driveExplain,explp);
    TextView driveToggle=button(driveAlertsEnabled()?
      "■  STOP DRIVE ALERTS":"▶  START DRIVE ALERTS",this::requestDriveAlerts);
    LinearLayout.LayoutParams dl=lp(-1,-2);dl.topMargin=dp(12);driveCard.addView(driveToggle,dl);
    TextView safety=text("Starts only when you choose it. Displays an ongoing phone notification. It cannot see your complete navigation route or replace Wireless Emergency Alerts.",11,0xffc9c5bc,false);
    LinearLayout.LayoutParams slp=lp(-1,-2);slp.topMargin=dp(9);driveCard.addView(safety,slp);
    LinearLayout.LayoutParams drlp=lp(-1,-2);drlp.topMargin=dp(10);drlp.bottomMargin=dp(12);
    content.addView(driveCard,drlp);
    TextView morningAction=button(MorningBriefingWorker.enabled(this)?
        "☀  GOOD MORNING NOTIFICATIONS ON · TAP TO TURN OFF":
        "☀  ENABLE MORNING BRIEFING NOTIFICATIONS",this::toggleMorningBriefing);
    LinearLayout.LayoutParams morningParams=lp(-1,-2);
    morningParams.bottomMargin=dp(13);content.addView(morningAction,morningParams);
    TextView utilities=text("MAKE NEWSWIRE YOURS",12,0xff62dbe6,true);
    LinearLayout.LayoutParams utilityLp=lp(-1,-2);utilityLp.topMargin=dp(13);
    utilityLp.bottomMargin=dp(9);content.addView(utilities,utilityLp);
    TextView alertToggle=button(localNewsNotificationEnabled()?
      "◉  LOCAL NEWS ALERTS ON · TAP TO TURN OFF":"◉  TURN ON LOCAL NEWS ALERTS",
      this::toggleNewsNotifications);
    LinearLayout.LayoutParams alertLp=lp(-1,-2);alertLp.bottomMargin=dp(10);
    content.addView(alertToggle,alertLp);
    TextView alertSettings=button("⚙  SMART ALERT TOPICS · BREAKING / SCHOOLS / LOCAL",
      this::showSmartNewsSettings);
    LinearLayout.LayoutParams settingsLp=lp(-1,-2);
    settingsLp.bottomMargin=dp(10);content.addView(alertSettings,settingsLp);
    TextView updates=button(updateDownloaded?"✓  UPDATE DOWNLOADED · INSTALL NOW":
      updateAvailable?"↑  NEWSWIRE UPDATE READY IN GOOGLE PLAY":
      "↑  CHECK FOR APP UPDATES",()->checkForUpdates(true));
    updateAction=updates;
    content.addView(updates);
    TextView alertInfo=text("Optional local headline notifications appear on your phone, not over the car radar. Updates are handled by Google Play.",11,MUTED,false);
    pad(alertInfo,5,12);content.addView(alertInfo);
    TextView foot=text("NewsWire v"+installedVersion()+"  •  More sections arrive through official app updates.",12,MUTED,false);
    pad(foot,5,20);content.addView(foot);
    startDashboardGps();
    refreshMyArea(false);
  }
  private void stopDashboardGps(){
    if(dashboardLocationManager!=null&&dashboardLocationListener!=null){
      try{dashboardLocationManager.removeUpdates(dashboardLocationListener);}catch(Exception ignored){}
    }
    dashboardLocationListener=null;
  }
  private void useDashboardLocation(){
    if(!hasLocationAccess()){
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION},DASHBOARD_LOCATION_REQUEST);
      return;
    }
    startDashboardGps();
    refreshMyArea(true);
  }
  private void startDashboardGps(){
    if(!dashboardActive||!hasLocationAccess()||dashboardLocationListener!=null)return;
    try{
      dashboardLocationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
      dashboardLocationListener=loc->{
        if(loc==null||loc.hasAccuracy()&&loc.getAccuracy()>1500f)return;
        boolean newPlace=Double.isNaN(dashboardLatitude)
            ||Math.abs(dashboardLatitude-loc.getLatitude())>.028
            ||Math.abs(dashboardLongitude-loc.getLongitude())>.035;
        dashboardLatitude=loc.getLatitude();
        dashboardLongitude=loc.getLongitude();
        if(newPlace)refreshMyArea(false);
      };
      Location latest=null;
      for(String provider:dashboardLocationManager.getProviders(true)){
        Location prev=dashboardLocationManager.getLastKnownLocation(provider);
        if(prev!=null&&(latest==null||prev.getTime()>latest.getTime()))latest=prev;
        dashboardLocationManager.requestLocationUpdates(provider,25000,400,
          dashboardLocationListener,Looper.getMainLooper());
      }
      if(latest!=null && System.currentTimeMillis()-latest.getTime()<10*60*1000
          &&(!latest.hasAccuracy()||latest.getAccuracy()<1500f)){
        dashboardLatitude=latest.getLatitude();dashboardLongitude=latest.getLongitude();
      }
    }catch(Exception error){android.util.Log.w("NewsWireArea","Dashboard GPS unavailable",error);}
  }
  private void renderMyArea(){
    if(!dashboardActive||myAreaCard==null)return;
    myAreaCard.removeAllViews();
    TextView label=text("⌖  MY AREA / LIVE",12,0xff70edf1,true);
    myAreaCard.addView(label);
    boolean gps=!Double.isNaN(dashboardLatitude);
    String place=gps?"Following your location":(!city.isEmpty()?city+", "+state+" · ZIP backup":"Set a ZIP or enable location");
    TextView placeLabel=text(place,13,0xffbed9e8,false);
    LinearLayout.LayoutParams pp=lp(-1,-2);pp.topMargin=dp(5);myAreaCard.addView(placeLabel,pp);
    if(areaLoading && myAreaSnapshot==null){
      TextView loading=text("Checking current weather, NWS alerts and headlines…",13,MUTED,false);
      pad(loading,4,12);myAreaCard.addView(loading);
    }else if(myAreaSnapshot!=null){
      MyAreaData.Snapshot data=myAreaSnapshot;
      TextView area=text(data.area.toUpperCase(Locale.US),12,0xff67d7e8,true);
      LinearLayout.LayoutParams ap=lp(-1,-2);ap.topMargin=dp(10);myAreaCard.addView(area,ap);
      TextView temperature=text(data.temperature,37,FG,true);
      LinearLayout.LayoutParams tp=lp(-1,-2);tp.topMargin=dp(4);myAreaCard.addView(temperature,tp);
      TextView desc=text(data.condition+"  •  Wind "+data.wind,13,0xffc7dce9,false);
      myAreaCard.addView(desc);
      if(data.observationAt>0){
        String observedAt=new java.text.SimpleDateFormat("h:mm a",Locale.US)
            .format(new java.util.Date(data.observationAt));
        TextView observed=text("NWS station observation: "+observedAt,11,MUTED,false);
        myAreaCard.addView(observed);
      }else if(data.conditionsAvailable){
        TextView estimated=text("Estimated temperature • not a live station measurement",
            11,0xffffc373,false);
        myAreaCard.addView(estimated);
      }
      boolean stale=System.currentTimeMillis()-data.checkedAt>30*60*1000L;
      TextView warning=text(stale?"⚠  Weather alert check is outdated — tap Refresh":
        !data.warningsAvailable?"⚠  NWS alerts unavailable — check official warnings":
        data.warnings.isEmpty()?"✓  No active NWS alerts reported at last check":
        "⚠  "+data.warnings.size()+" ACTIVE NWS ALERT"+(data.warnings.size()==1?"":"S")+" • "+data.warnings.get(0),
        12,stale||!data.warningsAvailable?0xffffc373:
          data.warnings.isEmpty()?0xff77dfb6:0xffffc373,true);
      LinearLayout.LayoutParams wp=lp(-1,-2);wp.topMargin=dp(12);
      myAreaCard.addView(warning,wp);
      TextView emergency=button("⚠  SEE OFFICIAL WARNINGS ON THE MAP",
        ()->openLiveCenter("alerts"));
      LinearLayout.LayoutParams em=lp(-1,-2);em.topMargin=dp(9);
      myAreaCard.addView(emergency,em);
      if(!data.headlines.isEmpty()){
        LocalNewspapers.Headline headline=data.headlines.get(0);
        TextView top=text("LOCAL HEADLINE  /  "+headline.source,11,0xff70edf1,true);
        LinearLayout.LayoutParams titleLp=lp(-1,-2);titleLp.topMargin=dp(14);
        myAreaCard.addView(top,titleLp);
        TextView story=text(headline.title+"  ›",14,FG,true);
        pad(story,0,6);myAreaCard.addView(story);
        story.setOnClickListener(v->openBrowser(headline.url));
      }else{
        TextView missing=text("Open News for current regional headlines.",12,MUTED,false);
        LinearLayout.LayoutParams msg=lp(-1,-2);msg.topMargin=dp(10);myAreaCard.addView(missing,msg);
      }
    }else{
      TextView hint=text("Enable location for weather and alerts around you, or save a ZIP below.",13,MUTED,false);
      pad(hint,4,12);myAreaCard.addView(hint);
    }
    String refreshStatus=areaLoading?"↻ Updating weather and alerts…":
      !areaRefreshMessage.isEmpty()?areaRefreshMessage:
      myAreaSnapshot==null?"Not checked yet":
      "Last checked "+new java.text.SimpleDateFormat("MMM d, h:mm a",Locale.US)
          .format(new java.util.Date(myAreaSnapshot.checkedAt));
    TextView status=text(refreshStatus,12,areaLoading?ACCENT:MUTED,false);
    LinearLayout.LayoutParams statusLp=lp(-1,-2);
    statusLp.topMargin=dp(12);myAreaCard.addView(status,statusLp);
    LinearLayout actions=row();
    TextView radar=button("◎  LIVE RADAR",()->
      startActivity(new Intent(MainActivity.this,RadarActivity.class)));
    actions.addView(radar,new LinearLayout.LayoutParams(0,-2,1));
    TextView location=button(areaLoading?"↻ CHECKING…":gps?"↻ REFRESH":"⌖ MY LOCATION",
      this::useDashboardLocation);
    LinearLayout.LayoutParams l=lp(-2,-2);l.leftMargin=dp(8);
    actions.addView(location,l);
    LinearLayout.LayoutParams ctrl=lp(-1,-2);ctrl.topMargin=dp(12);
    myAreaCard.addView(actions,ctrl);
  }
  private void refreshMyArea(boolean force){
    if(!dashboardActive)return;
    final double lat=!Double.isNaN(dashboardLatitude)?dashboardLatitude:postalLat;
    final double lon=!Double.isNaN(dashboardLongitude)?dashboardLongitude:postalLon;
    if(!WidgetAreaResolver.valid(lat,lon)){
      areaRefreshMessage="Set a ZIP code or allow location to update weather.";
      areaLoading=false;renderMyArea();return;
    }
    final boolean isGps=!Double.isNaN(dashboardLatitude);
    final String key=String.format(Locale.US,"%.3f:%.3f:%s:%s",lat,lon,city,state);
    if(!force && areaLoading && key.equals(loadedAreaKey)){
      renderMyArea();return;
    }
    if(!force && key.equals(loadedAreaKey) && myAreaSnapshot!=null
        &&System.currentTimeMillis()-myAreaSnapshot.checkedAt<60*1000L){
      renderMyArea();return;
    }
    // Every press makes a new request. Older responses may never overwrite it,
    // even when the coordinates or ZIP are unchanged.
    final int request=++areaRequestGeneration;
    loadedAreaKey=key;
    areaLoading=true;
    areaRefreshMessage="";
    renderMyArea();
    areaRefreshHandler.postDelayed(()->{
      if(!dashboardActive||request!=areaRequestGeneration||!areaLoading)return;
      ++areaRequestGeneration;
      areaLoading=false;
      // Keep the old reading only as an explicitly marked last known value.
      areaRefreshMessage="Refresh timed out. Weather shown may be old; tap Refresh to retry.";
      renderMyArea();
    },60_000L);
    final String savedCity=city,savedState=state;
    new Thread(()->{
      String currentCity=savedCity,currentState=savedState;
      try{
        if(isGps){
          currentCity="";currentState="";
          try{
            Geocoder geocoder=new Geocoder(this,Locale.US);
            List<Address> places=geocoder.getFromLocation(lat,lon,1);
            if(places!=null&&!places.isEmpty()){
              Address address=places.get(0);
              currentCity=address.getLocality();
              if(currentCity==null||currentCity.isEmpty())currentCity=address.getSubAdminArea();
              currentState=address.getAdminArea();
            }
          }catch(Exception error){android.util.Log.w("NewsWireArea","City unavailable",error);}
        }
        if(currentCity==null)currentCity="";
        if(currentState==null)currentState="";
        MyAreaData.Snapshot result=MyAreaData.load(lat,lon,currentCity,currentState);
        runOnUiThread(()->{
          if(!dashboardActive||request!=areaRequestGeneration)return;
          areaLoading=false;
          myAreaSnapshot=result;
          String now=new java.text.SimpleDateFormat("h:mm a",Locale.US)
              .format(new java.util.Date(result.checkedAt));
          areaRefreshMessage=result.conditionsAvailable||result.warningsAvailable?
              "Updated at "+now+
              (!result.conditionsAvailable?" · Temperature unavailable":
              !result.warningsAvailable?" · Alert status unavailable":""):
              "Checked at "+now+" · Weather services unavailable; retry";
          if(isGps)WidgetAreaResolver.rememberForegroundFix(
              this,lat,lon,result.area.equals("Current location")?"":result.area.split(",")[0],
              result.area.contains(",")?result.area.substring(result.area.lastIndexOf(',')+1).trim():"");
          WidgetAreaResolver.Area widgetArea=WidgetAreaResolver.resolve(this);
          if(widgetArea!=null&&widgetArea.fromGps==isGps)
              WidgetRefreshWorker.publish(this,widgetArea,result);
          renderMyArea();
        });
      }catch(Exception error){
        android.util.Log.w("NewsWireArea","Area refresh failed",error);
        runOnUiThread(()->{
          if(!dashboardActive||request!=areaRequestGeneration)return;
          areaLoading=false;
          areaRefreshMessage="Weather refresh failed. Check connection and tap Refresh again.";
          renderMyArea();
        });
      }
    },"newswire-area").start();
  }
  private LinearLayout dashboardGroup(LinearLayout parent,String title,String detail,
      String icon,int color){
    LinearLayout children=column();
    children.setPadding(dp(10),0,0,0);
    children.setVisibility(View.GONE);
    categoryTile(parent,title+"  ▾",detail,icon,color,()->
      children.setVisibility(children.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
    LinearLayout.LayoutParams holder=lp(-1,-2);
    holder.bottomMargin=dp(6);
    parent.addView(children,holder);
    return children;
  }
  private void categoryTile(LinearLayout parent,String title,String detail,String icon,int tone,Runnable click){
    LinearLayout tile=row();
    GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{tone,PANEL});
    bg.setCornerRadius(dp(21));bg.setStroke(dp(1),EDGE);
    tile.setBackground(radiantCard(tone,0xff132137,tone));
    pad(tile,19,20);
    TextView symbol=text(icon,33,FG,true);symbol.setGravity(Gravity.CENTER);
    tile.addView(symbol,lp(49,-2));
    LinearLayout labels=column();TextView name=text(title,22,FG,true);labels.addView(name);
    TextView info=text(detail,12,0xffd0e3f1,false);
    LinearLayout.LayoutParams ip=lp(-1,-2);ip.topMargin=dp(5);labels.addView(info,ip);
    LinearLayout.LayoutParams labelsLp=new LinearLayout.LayoutParams(0,-2,1);labelsLp.leftMargin=dp(13);
    tile.addView(labels,labelsLp);TextView arrow=text("›",29,FG,true);tile.addView(arrow);
    tile.setContentDescription(title+". "+detail);tile.setOnClickListener(v->click.run());
    LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(13);parent.addView(tile,p);
  }
  private String activeState(){
    if(myAreaSnapshot!=null&&!Double.isNaN(dashboardLatitude)){
      String label=myAreaSnapshot.area;
      int at=label.lastIndexOf(',');
      if(at>=0&&at<label.length()-1)return label.substring(at+1).trim();
    }
    return state;
  }
  private void openDrive(){
    Intent intent=new Intent(this,NewsWireDriveActivity.class);
    String currentCity=city;
    if(myAreaSnapshot!=null&&!Double.isNaN(dashboardLatitude)){
      String currentArea=myAreaSnapshot.area;
      int comma=currentArea.indexOf(',');
      if(comma>0)currentCity=currentArea.substring(0,comma).trim();
    }
    intent.putExtra("city",currentCity);
    intent.putExtra("state",activeState());
    if(!Double.isNaN(dashboardLatitude)&&!Double.isNaN(dashboardLongitude)){
      intent.putExtra("latitude",dashboardLatitude);
      intent.putExtra("longitude",dashboardLongitude);
    }
    startActivity(intent);
  }
  private void openTraffic(){
    Intent intent=new Intent(this,TrafficActivity.class);
    intent.putExtra("state",activeState());
    startActivity(intent);
  }
  private void openFootballBoard(){
    Intent football=new Intent(this,FootballBoardActivity.class);
    String areaCity=city,areaState=state;
    if(myAreaSnapshot!=null&&!Double.isNaN(dashboardLatitude)){
      String label=myAreaSnapshot.area;
      int comma=label.indexOf(',');
      if(comma>0){
        areaCity=label.substring(0,comma).trim();
        areaState=label.substring(comma+1).trim();
      }
    }
    football.putExtra("city",areaCity);
    football.putExtra("state",areaState);
    football.putExtra("zip",zip);
    startActivity(football);
  }
  private void openSection(String name){
    if("Sports".equals(name)){openFootballBoard();return;}
    tab=name;query="";home();
  }
  private void resolveZip(String postal){resolveZip(postal,false);}
  private void resolveZip(String postal,boolean showSportsAfterSave){
    if(!postal.matches("[0-9]{5}")){android.widget.Toast.makeText(this,"Enter a 5-digit ZIP code",0).show();return;}
    new Thread(()->{
      HttpURLConnection con=null;
      try{
        con=(HttpURLConnection)new URL("https://api.zippopotam.us/us/"+postal).openConnection();
        con.setConnectTimeout(9000);con.setReadTimeout(9000);
        if(con.getResponseCode()!=200)throw new IllegalArgumentException("ZIP not found");
        java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
        try(java.io.InputStream input=con.getInputStream()){
          byte[] block=new byte[2048];int n;while((n=input.read(block))!=-1)buf.write(block,0,n);
        }
        JSONObject obj=new JSONObject(buf.toString("UTF-8"));
        JSONObject place=obj.getJSONArray("places").getJSONObject(0);
        String foundCity=place.getString("place name"),foundState=place.getString("state abbreviation");
        double foundLat=Double.parseDouble(place.getString("latitude"));
        double foundLon=Double.parseDouble(place.getString("longitude"));
        runOnUiThread(()->{
          zip=postal;city=foundCity;state=foundState;postalLat=foundLat;postalLon=foundLon;
          getSharedPreferences("newswire-location",MODE_PRIVATE).edit()
             .putString("zip",zip).putString("city",city).putString("state",state)
             .putLong("lat",Double.doubleToLongBits(postalLat))
             .putLong("lon",Double.doubleToLongBits(postalLon)).apply();
          // Refresh widget when the ZIP backup changes; the last foreground
          // GPS fix remains preferred until it expires.
          NewsWireWidget.refresh(this);
          if(showSportsAfterSave){tab="Sports";query="";home();}
          else dashboard();
        });
      }catch(Exception ex){runOnUiThread(()->android.widget.Toast.makeText(this,"Could not look up ZIP. Check connection.",1).show());}
      finally{if(con!=null)con.disconnect();}
    }).start();
  }
  private Channel localLink(String id,String name,String category,String note,String url){
    return new Channel(id,name,category,note,url,false);
  }

  private void home(){
    dashboardActive=false;stopDashboardGps();
    newsScreenVisible=tab.equals("News");
    current=null;
    if(web!=null){respondToLocationRequest(false);web.destroy();web=null;}
    root=column();root.setBackgroundColor(BG);setContentView(root);
    LinearLayout header=column();pad(header,20,20);
    TextView back=button("‹  MENU",()->dashboard());header.addView(back);
    TextView brand=text("NEWSWIRE  /  "+tab.toUpperCase(Locale.US),24,FG,true);
    LinearLayout.LayoutParams bh=lp(-1,-2);bh.topMargin=dp(13);header.addView(brand,bh);
    TextView subtitle=text(tab.equals("News")?"Local papers follow your location • saved ZIP is backup":
      tab.equals("National")?"Top U.S. stories from attributed news publishers":zip.isEmpty()?(tab.equals("Sports")?"Search by ZIP below for local football scores":"Set a ZIP on the home screen for local results"):city+", "+state+"  •  "+zip,13,MUTED,false);
    LinearLayout.LayoutParams sl=lp(-1,-2);sl.topMargin=dp(7);header.addView(subtitle,sl);
    root.addView(header);
    TextView notice=text(tab.equals("News")?"LOCAL HEADLINES  •  NEWSPAPERS":
      tab.equals("National")?"NATIONAL HEADLINES  •  BROADCASTERS":"OFFICIAL CHANNEL LINKS  •  Live coverage depends on the broadcaster",11,ACCENT,true);
    pad(notice,20,8);root.addView(notice);
    if(tab.equals("Sports")){
      LinearLayout sportZip=column();pad(sportZip,16,12);
      TextView instruction=text("FIND HIGH-SCHOOL FOOTBALL SCORES BY ZIP CODE",12,ACCENT,true);
      sportZip.addView(instruction);
      LinearLayout controls=row();
      EditText sportsZipEntry=new EditText(this);
      sportsZipEntry.setSingleLine(true);
      sportsZipEntry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
      sportsZipEntry.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
      sportsZipEntry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
      sportsZipEntry.setText(zip);
      sportsZipEntry.setSelectAllOnFocus(true);
      sportsZipEntry.setHint("Enter ZIP e.g. 35950");sportsZipEntry.setHintTextColor(MUTED);
      sportsZipEntry.setTextColor(FG);sportsZipEntry.setTextSize(16);
      sportsZipEntry.setBackground(box(PANEL,13));pad(sportsZipEntry,12,10);
      controls.addView(sportsZipEntry,new LinearLayout.LayoutParams(0,dp(55),1));
      Runnable doSportsSearch=()->{
        String postal=sportsZipEntry.getText().toString().trim();
        resolveZip(postal,true);
      };
      TextView find=button("SEARCH",doSportsSearch);
      LinearLayout.LayoutParams searchLp=lp(-2,55);searchLp.leftMargin=dp(9);
      controls.addView(find,searchLp);
      LinearLayout.LayoutParams ctrlLp=lp(-1,-2);ctrlLp.topMargin=dp(9);
      sportZip.addView(controls,ctrlLp);
      sportsZipEntry.setOnEditorActionListener((v,actionId,event)->{
        if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            || (event!=null && event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER
                && event.getAction()==android.view.KeyEvent.ACTION_UP)){
          doSportsSearch.run();return true;
        }
        return false;
      });
      TextView detail=text("Search saves your ZIP for News, Weather and Sports. Choose an area or state scoreboard below.",12,MUTED,false);
      LinearLayout.LayoutParams detailLp=lp(-1,-2);detailLp.topMargin=dp(11);
      sportZip.addView(detail,detailLp);
      root.addView(sportZip);
    }else{
      search=new EditText(this);
      search.setSingleLine(true);search.setHint("Search "+tab.toLowerCase(Locale.US)+" links…");search.setHintTextColor(MUTED);
      search.setTextColor(FG);search.setTextSize(16);search.setBackground(box(PANEL,13));pad(search,14,10);
      LinearLayout.LayoutParams sp=lp(-1,52);sp.setMargins(dp(16),dp(12),dp(16),dp(8));
      root.addView(search,sp);search.setText(query);
      search.addTextChangedListener(new TextWatcher(){
        public void beforeTextChanged(CharSequence s,int a,int c,int d){}
        public void onTextChanged(CharSequence s,int a,int before,int count){query=s.toString();render();}
        public void afterTextChanged(Editable s){}
      });
    }
    // Category menu is publisher-defined; users cannot add buttons.
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
    list=column();pad(list,16,7);scroll.addView(list);
    root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));render();
    if(tab.equals("News"))startNewsGps(false);
  }
  private void makeTabs(){
    tabBar.removeAllViews();
    for(String name:tabs){
      boolean selected=tab.equals(name);
      TextView v=text(name,13,selected?BG:FG,true);v.setGravity(Gravity.CENTER);
      v.setBackground(box(selected?ACCENT:PANEL,15));pad(v,13,11);
      LinearLayout.LayoutParams p=lp(-2,-2);p.rightMargin=dp(8);tabBar.addView(v,p);
      v.setOnClickListener(x->{if(name.equals("Radar")){startActivity(new Intent(MainActivity.this,RadarActivity.class));return;}tab=name;makeTabs();render();});
    }
  }
  private void render(){
    if(list==null)return;
    list.removeAllViews();
    int count=0;
    if(tab.equals("Weather")){
      TextView driveLink=button("↗  NEWSWIRE DRIVE • SPOKEN WEATHER",this::openDrive);
      LinearLayout.LayoutParams driveLp=lp(-1,-2);driveLp.bottomMargin=dp(11);
      list.addView(driveLink,driveLp);
      TextView radar=button("◎  LIVE RADAR  •  GPS FOLLOW",()->startActivity(new Intent(MainActivity.this,RadarActivity.class)));
      LinearLayout.LayoutParams rp=lp(-1,-2);rp.bottomMargin=dp(13);list.addView(radar,rp);
      if(!Double.isNaN(postalLat)){
        card(localLink("zipweather","Local NWS forecast — "+zip,"Weather","Forecast for "+city+", "+state,
          String.format(Locale.US,"https://forecast.weather.gov/MapClick.php?lat=%.5f&lon=%.5f",postalLat,postalLon)));
        count++;
      }
    }
    if(tab.equals("News"))count+=renderLocalNews();
    if(tab.equals("National"))count+=renderNationalNews();
    if(tab.equals("Sports")){
      TextView live=button("🏈  FRIDAY NIGHT FOOTBALL LIVE",this::openFootballBoard);
      LinearLayout.LayoutParams flp=lp(-1,-2);flp.bottomMargin=dp(13);
      list.addView(live,flp);
      if(state.matches("[A-Z]{2}")&&!city.isEmpty()&&!zip.isEmpty()){
        try{
          String local=URLEncoder.encode(city+" "+state+" high school football scores","UTF-8");
          card(localLink("nearby"+zip,"Football scores near "+city,"Sports",
              "Local school and team results for ZIP "+zip+" (external search)",
              "https://www.google.com/search?q="+local));
          count++;
        }catch(Exception ignored){}
        card(localLink("scores"+state,"Statewide football scoreboard — "+state,"Sports",
          "MaxPreps schedules and scores; select schools in your area",
          "https://www.maxpreps.com/"+state.toLowerCase(Locale.US)+"/football/scores/"));
        count++;
      }else{
        TextView help=text("Enter your ZIP above and tap SEARCH to find high-school football scores near you.",15,MUTED,false);
        pad(help,8,13);list.addView(help);
      }
    }
    for(Channel ch:channels){
      if(tab.equals("Sports"))continue;
      if(tab.equals("Weather")&&!ch.category.equals("Weather"))continue;
      if(tab.equals("News")&&!ch.category.equals("Local"))continue;
      if(tab.equals("National")&&!ch.category.equals("National"))continue;
      String areaState=tab.equals("News")&&newsGpsActive?newsGpsRegion:state;
      if(ch.category.equals("Local")&&!("AL".equalsIgnoreCase(areaState)||"Alabama".equalsIgnoreCase(areaState)))continue;
      String needle=query.toLowerCase(Locale.US);
      if(!ch.name.toLowerCase(Locale.US).contains(needle)&&!ch.note.toLowerCase(Locale.US).contains(needle))continue;
      count++; card(ch);
    }
    if(count==0 && !tab.equals("Sports")){
      TextView empty=text("No stations found.\nTry another search or tab.",16,MUTED,false);
      empty.setGravity(Gravity.CENTER);pad(empty,12,35);list.addView(empty);
    }
    TextView footer=text(tab.equals("Sports")?"Score links are supplied by external providers; school coverage and update times vary.":"Independent channel guide. Video belongs to each broadcaster. Some channels need a paid login or must open in your browser.",12,MUTED,false);
    pad(footer,8,22);list.addView(footer);
  }

  private boolean newsMatch(String title,String extra){
    String filter=query.trim().toLowerCase(Locale.US);
    return filter.isEmpty()||title.toLowerCase(Locale.US).contains(filter)
        ||extra.toLowerCase(Locale.US).contains(filter);
  }
  private void section(LinearLayout target,String name){
    TextView header=text(name,12,ACCENT,true);
    LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(15);p.bottomMargin=dp(12);
    target.addView(header,p);
  }
  private int renderNationalNews(){
    section(list,"TOP STORIES ACROSS THE UNITED STATES");
    if(!nationalLoading && (nationalFetchedAt==0 ||
        System.currentTimeMillis()-nationalFetchedAt>10L*60*1000)){
      nationalLoading=true;
      nationalFetchedAt=System.currentTimeMillis();
      nationalFeedError="";
      new Thread(()->{
        try{
          List<LocalNewspapers.Headline> fresh=LocalNewspapers.nationalHeadlines();
          runOnUiThread(()->{
            if(isFinishing()||isDestroyed())return;
            nationalHeadlines=fresh; nationalLoading=false; nationalFeedError="";
            if(tab.equals("National")&&current==null)render();
          });
        }catch(Exception e){
          runOnUiThread(()->{
            if(isFinishing()||isDestroyed())return;
            nationalLoading=false;
            nationalFeedError="National feed unavailable. Try refresh or use broadcaster links below.";
            if(tab.equals("National")&&current==null)render();
          });
        }
      },"newswire-national-headlines").start();
    }
    TextView refresh=button("↻ REFRESH NATIONAL HEADLINES",()->{
      nationalFetchedAt=0;render();
    });
    LinearLayout.LayoutParams rp=lp(-1,-2);
    rp.bottomMargin=dp(10);list.addView(refresh,rp);
    if(nationalLoading)list.addView(text("Loading national headlines…",13,MUTED,false));
    else if(!nationalFeedError.isEmpty())
      list.addView(text(nationalFeedError,13,MUTED,false));
    else if(nationalHeadlines.isEmpty())
      list.addView(text("No headlines returned. National source links are below.",13,MUTED,false));
    int count=0;
    for(LocalNewspapers.Headline item:nationalHeadlines){
      if(!newsMatch(item.title,item.source))continue;
      card(localLink("national"+item.url.hashCode(),item.title,"National headline",
          item.source+" • Opens attributed source",item.url));
      count++;
    }
    section(list,"NATIONAL NEWS CHANNELS");
    return count;
  }
  private int renderLocalNews(){
    String activeCity=newsGpsActive?newsGpsCity:city;
    String activeRegion=newsGpsActive?newsGpsRegion:state;
    double lat=newsGpsActive?newsGpsLatitude:postalLat;
    double lon=newsGpsActive?newsGpsLongitude:postalLon;
    String areaLabel=activeCity.isEmpty()?"No local area set":activeCity+", "+activeRegion;
    TextView where=text((newsGpsActive?"● FOLLOWING PHONE  •  ":"⌖ SAVED ZIP / BACKUP  •  ")+areaLabel,14,
        newsGpsActive?ACCENT:MUTED,true);
    pad(where,7,9);list.addView(where);
    TextView locationHelp=text("Your news changes as you travel. Location is used only while News is open. City lookup may share coordinates with a geocoding provider.",12,MUTED,false);
    pad(locationHelp,7,5);list.addView(locationHelp);
    TextView retry=button(newsGpsActive?"◎ REFRESH CURRENT LOCATION":"◎ USE CURRENT LOCATION",()->startNewsGps(true));
    LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(8);
    rp.bottomMargin=dp(4);list.addView(retry,rp);
    int count=0;
    if(activeCity.isEmpty()){
      TextView hint=text("Allow location to find news near you, or save a ZIP on the main menu.",14,MUTED,false);
      pad(hint,8,12);list.addView(hint);
      return 0;
    }
    section(list,"NEWSPAPERS NEAR "+activeCity.toUpperCase(Locale.US));
    // Verified paper shortcuts are shown by distance; the discovery link works for any city.
    for(LocalNewspapers.Paper paper:LocalNewspapers.nearby(lat,lon)){
      if(!newsMatch(paper.name,paper.town))continue;
      card(localLink("paper"+paper.url.hashCode(),paper.name,"Newspaper",
          paper.town+" • Official newspaper website",paper.url));
      count++;
    }
    String location=activeCity+" "+activeRegion;
    if(newsMatch("Find newspapers",location)){
      card(localLink("findpapers"+location,"Find more local newspapers in "+activeCity,"Local",
          "Discover additional publishers serving this area (external search)",
          LocalNewspapers.paperDiscovery(activeCity,activeRegion)));
      count++;
    }
    section(list,"CURRENT LOCAL HEADLINES");
    if(!newsFeedKey.equals(location)){
      newsFeedKey=location;newsFeedLoading=true;newsFeedError="";
      newsHeadlines=new ArrayList<>();
      final String requestedKey=location, cityName=activeCity, region=activeRegion;
      new Thread(()->{
        try{
          List<LocalNewspapers.Headline> latest=LocalNewspapers.headlines(cityName,region);
          runOnUiThread(()->{
            if(!requestedKey.equals(newsFeedKey))return;
            newsHeadlines=latest;newsFeedLoading=false;newsFeedError="";
            if(tab.equals("News")&&current==null)render();
          });
        }catch(Exception ex){
          runOnUiThread(()->{
            if(!requestedKey.equals(newsFeedKey))return;
            newsFeedLoading=false;newsFeedError="Feed unavailable; use local news search below.";
            if(tab.equals("News")&&current==null)render();
          });
        }
      }).start();
    }
    if(newsFeedLoading){
      TextView loading=text("Updating news for "+activeCity+"…",13,MUTED,false);
      pad(loading,8,10);list.addView(loading);
    }else if(!newsFeedError.isEmpty()){
      TextView unavailable=text(newsFeedError,13,MUTED,false);
      pad(unavailable,8,10);list.addView(unavailable);
    }else if(newsHeadlines.isEmpty()){
      TextView empty=text("No recent headlines returned for this area. Try the full news search.",13,MUTED,false);
      pad(empty,8,10);list.addView(empty);
    }else{
      for(LocalNewspapers.Headline h:newsHeadlines){
        if(!newsMatch(h.title,h.source))continue;
        card(localLink("story"+h.url.hashCode(),h.title,"Headline",
            h.source+" • Opens source coverage",h.url));
        count++;
      }
    }
    if(newsMatch("All local news",location)){
      card(localLink("allnews"+location,"More local news in "+activeCity,"Local",
          "Browse additional regional coverage and sources",
          LocalNewspapers.localNewsSearch(activeCity,activeRegion)));
      count++;
    }
    return count;
  }
  private void stopNewsGps(){
    if(newsLocationManager!=null&&newsLocationListener!=null){
      try{newsLocationManager.removeUpdates(newsLocationListener);}catch(Exception ignored){}
    }
    newsLocationListener=null;
  }
  private void startNewsGps(boolean userRequested){
    if(!newsScreenVisible||!"News".equals(tab)||current!=null)return;
    if(!hasLocationAccess()){
      if(Build.VERSION.SDK_INT>=23&&(!newsPermissionRequested||userRequested)){
        newsPermissionRequested=true;
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION},NEWS_LOCATION_REQUEST);
      }
      return;
    }
    if(newsLocationListener!=null&&!userRequested)return;
    stopNewsGps();
    try{
      newsLocationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
      newsLocationListener=location->resolveNewsLocation(location);
      Location best=null;
      for(String provider:newsLocationManager.getProviders(true)){
        Location last=newsLocationManager.getLastKnownLocation(provider);
        if(last!=null&&(best==null||last.getTime()>best.getTime()))best=last;
        newsLocationManager.requestLocationUpdates(provider,20000,300,newsLocationListener,Looper.getMainLooper());
      }
      if(best!=null&&System.currentTimeMillis()-best.getTime()<10*60*1000)resolveNewsLocation(best);
    }catch(SecurityException ex){stopNewsGps();}
      catch(Exception ex){stopNewsGps();}
  }
  private void resolveNewsLocation(Location point){
    if(point==null||newsGeocoding)return;
    if(lastNewsLookup!=null&&point.distanceTo(lastNewsLookup)<2500
        && System.currentTimeMillis()-lastNewsLookup.getTime()<120000)return;
    lastNewsLookup=new Location(point);
    lastNewsLookup.setTime(System.currentTimeMillis());
    newsGeocoding=true;
    int generation=++newsGeoGeneration;
    final double lat=point.getLatitude(),lon=point.getLongitude();
    new Thread(()->{
      String foundCity="",foundRegion="";
      try{
        Geocoder g=new Geocoder(this,Locale.US);
        List<Address> result=g.getFromLocation(lat,lon,1);
        if(result!=null&&!result.isEmpty()){
          Address item=result.get(0);
          foundCity=item.getLocality();
          if(foundCity==null||foundCity.trim().isEmpty())foundCity=item.getSubAdminArea();
          foundRegion=item.getAdminArea();
        }
      }catch(Exception ignored){}
      // Fallback client-side reverse geocoding only after permission is granted.
      // Coordinates are sent directly from the phone to the provider, not via NewsWire servers.
      if(foundCity==null||foundCity.trim().isEmpty()){
        HttpURLConnection conn=null;
        try{
          String url=String.format(Locale.US,
             "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.5f&longitude=%.5f&localityLanguage=en",lat,lon);
          conn=(HttpURLConnection)new URL(url).openConnection();
          conn.setConnectTimeout(7000);conn.setReadTimeout(7000);
          if(conn.getResponseCode()==200){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()){
              byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);
            }
            JSONObject data=new JSONObject(bytes.toString("UTF-8"));
            foundCity=data.optString("city",data.optString("locality",""));
            if(foundCity.isEmpty())foundCity=data.optString("locality","");
            foundRegion=data.optString("principalSubdivision","");
          }
        }catch(Exception ignored){}
        finally{if(conn!=null)conn.disconnect();}
      }
      final String newCity=foundCity==null?"":foundCity.trim(),newRegion=foundRegion==null?"":foundRegion.trim();
      runOnUiThread(()->{
        if(generation!=newsGeoGeneration)return;
        newsGeocoding=false;
        if(!newCity.isEmpty()){
          boolean changed=!newCity.equalsIgnoreCase(newsGpsCity)||!newRegion.equalsIgnoreCase(newsGpsRegion);
          newsGpsCity=newCity;newsGpsRegion=newRegion;newsGpsLatitude=lat;newsGpsLongitude=lon;
          newsGpsActive=true;
          if(changed){
            WidgetAreaResolver.rememberForegroundFix(this,lat,lon,newCity,newRegion);
            NewsWireWidget.refresh(this);
          }
          if(changed&&tab.equals("News")&&current==null)render();
        }
      });
    }).start();
  }
  private boolean fav(Channel c){return prefs.getBoolean(c.id,false);}
  private void card(Channel ch){
    LinearLayout c=column();c.setBackground(box(PANEL,18));pad(c,16,14);
    LinearLayout heading=row();
    TextView n=text(ch.name,18,FG,true);heading.addView(n,new LinearLayout.LayoutParams(0,-2,1));
    TextView star=text(fav(ch)?"★":"☆",29,fav(ch)?ACCENT:MUTED,true);
    pad(star,8,0);heading.addView(star);
    star.setContentDescription("Toggle favorite "+ch.name);
    star.setOnClickListener(v->{prefs.edit().putBoolean(ch.id,!fav(ch)).apply();render();});
    c.addView(heading);
    TextView description=text(ch.note,13,MUTED,false);
    LinearLayout.LayoutParams nd=lp(-1,-2);nd.topMargin=dp(3);c.addView(description,nd);
    TextView badge=text(ch.signIn?"SIGN-IN / SUBSCRIPTION":ch.category.equals("Newspaper")?"OFFICIAL NEWSPAPER WEBSITE":ch.category.equals("Headline")?"CURRENT HEADLINE":"OFFICIAL LINK",11,ch.signIn?Color.rgb(253,199,102):ACCENT,true);
    LinearLayout.LayoutParams bd=lp(-1,-2);bd.topMargin=dp(12);c.addView(badge,bd);
    c.setOnClickListener(v->watch(ch));
    LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(12);list.addView(c,p);
  }
  private boolean hasLocationAccess(){
    return Build.VERSION.SDK_INT<23
        || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
  }
  // WebView's geolocation permission is separate from Android's runtime permission.
  // Only WeatherWise gets access; never approve third-party frames automatically.
  private void respondToLocationRequest(boolean allow){
    if(pendingLocationCallback!=null){
      pendingLocationCallback.invoke(pendingLocationOrigin,allow,false);
      pendingLocationCallback=null;
      pendingLocationOrigin=null;
    }
  }
  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
    super.onRequestPermissionsResult(requestCode,permissions,results);
    if(requestCode==DRIVE_FINE_LOCATION_REQUEST){
      if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
        requestDriveAlerts();
      else android.widget.Toast.makeText(this,
        "Drive Alerts require precise location to detect warning areas.",1).show();
    }
    if(requestCode==DRIVE_NOTIFICATION_REQUEST){
      if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)
        requestDriveAlerts();
      else android.widget.Toast.makeText(this,
        "Drive Alerts require permission to show tornado-warning notifications.",1).show();
    }
    if(requestCode==MORNING_NOTIFICATION_PERMISSION){
      if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){
        MorningBriefingWorker.configure(this,true);
        dashboard();
      }else android.widget.Toast.makeText(this,
        "Good Morning notifications remain disabled.",1).show();
    }
    if(requestCode==DASHBOARD_LOCATION_REQUEST){
      boolean ok=false;
      for(int result:results)if(result==PackageManager.PERMISSION_GRANTED)ok=true;
      if(ok){startDashboardGps();refreshMyArea(true);}
      else android.widget.Toast.makeText(this,"Location is off. Saved ZIP is still available.",1).show();
    }
    if(requestCode==NEWS_NOTIFICATION_REQUEST){
      if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){
        getSharedPreferences("newswire-news-alerts",MODE_PRIVATE)
          .edit().putBoolean("enabled",true).apply();
        NewsMonitorWorker.schedule(this,true);
        dashboard();
      }else android.widget.Toast.makeText(this,"Notifications are off until permitted.",1).show();
    }
    if(requestCode==NEWS_LOCATION_REQUEST){
      boolean granted=false;
      for(int result:results)if(result==PackageManager.PERMISSION_GRANTED)granted=true;
      if(granted)startNewsGps(false);
      else if(tab.equals("News")&&current==null)render();
    }
    if(requestCode==WEB_LOCATION_REQUEST){
      boolean granted=false;
      for(int result:results) if(result==PackageManager.PERMISSION_GRANTED) granted=true;
      respondToLocationRequest(granted && hasLocationAccess());
      if(!granted) android.widget.Toast.makeText(this,
          "Location was denied. You can enter your city in WeatherWise instead.",
          android.widget.Toast.LENGTH_LONG).show();
    }
  }
  private void watch(Channel ch){
    newsScreenVisible=false;
    stopNewsGps();
    current=ch;
    root=column();root.setBackgroundColor(BG);setContentView(root);
    LinearLayout header=row();pad(header,12,12);
    TextView back=button("‹  Back",()->home());header.addView(back);
    TextView title=text(ch.name,16,FG,true);title.setGravity(Gravity.CENTER);
    header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    TextView browser=button("↗  Browser",()->openBrowser(ch.url));header.addView(browser);
    root.addView(header);
    TextView info=text(ch.signIn?"A subscription or TV-provider sign-in may be needed.":"Viewing the broadcaster’s official website.",12,MUTED,false);
    pad(info,18,8);root.addView(info);
    FrameLayout container=new FrameLayout(this);
    web=new WebView(this);web.setBackgroundColor(BG);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setGeolocationEnabled(true);
    web.getSettings().setMediaPlaybackRequiresUserGesture(false);
    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest req){
        String scheme=req.getUrl().getScheme();
        if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme)){
          try{startActivity(new Intent(Intent.ACTION_VIEW,req.getUrl()));}catch(Exception ignored){}
          return true;
        }
        return false;
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public void onGeolocationPermissionsShowPrompt(String origin,
          GeolocationPermissions.Callback callback){
        Uri address=Uri.parse(origin);
        String host=address.getHost();
        boolean trusted=current!=null && "weatherwise".equals(current.id)
            && "https".equalsIgnoreCase(address.getScheme())
            && host!=null
            && ("weatherwise.live".equalsIgnoreCase(host)
                || "www.weatherwise.live".equalsIgnoreCase(host));
        if(!trusted){callback.invoke(origin,false,false);return;}
        if(hasLocationAccess()){callback.invoke(origin,true,false);return;}
        if(Build.VERSION.SDK_INT>=23){
          respondToLocationRequest(false);
          pendingLocationCallback=callback;
          pendingLocationOrigin=origin;
          MainActivity.this.requestPermissions(new String[]{
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION},WEB_LOCATION_REQUEST);
        } else callback.invoke(origin,false,false);
      }
      @Override public void onShowCustomView(View view,CustomViewCallback cb){
        if(custom!=null){cb.onCustomViewHidden();return;}
        custom=view;customCallback=cb;
        fullscreen=new FrameLayout(MainActivity.this);
        fullscreen.setBackgroundColor(Color.BLACK);
        fullscreen.addView(view,new FrameLayout.LayoutParams(-1,-1));
        addContentView(fullscreen,new ViewGroup.LayoutParams(-1,-1));
      }
      @Override public void onHideCustomView(){exitFullscreen();}
    });
    container.addView(web,new FrameLayout.LayoutParams(-1,-1));
    root.addView(container,new LinearLayout.LayoutParams(-1,0,1));
    TextView bottom=text("If playback is blocked here, choose Browser above to watch from the official provider.",12,MUTED,false);
    pad(bottom,16,13);root.addView(bottom);
    web.loadUrl(ch.url);
  }
  private void exitFullscreen(){
    if(custom==null)return;
    ((ViewGroup)fullscreen.getParent()).removeView(fullscreen);
    fullscreen=null;custom=null;
    if(customCallback!=null){customCallback.onCustomViewHidden();customCallback=null;}
  }
  private void openBrowser(String url){
    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
    catch(Exception e){android.widget.Toast.makeText(this,"No browser available",0).show();}
  }
  @Override public void onBackPressed(){
    if(custom!=null){exitFullscreen();return;}
    if(current!=null){if(web!=null&&web.canGoBack()){web.goBack();return;}home();return;}
    if(tab.equals("News")||tab.equals("National")||tab.equals("Weather")||tab.equals("Sports")){dashboard();return;}
    super.onBackPressed();
  }
  @Override protected void onResume(){
    super.onResume();
    if(dashboardActive){startDashboardGps();refreshMyArea(false);}
    if(newsScreenVisible&&tab.equals("News")&&current==null)startNewsGps(false);
    if(playUpdates!=null){
      // A download can finish while the app is away; recover interrupted flows.
      playUpdates.getAppUpdateInfo().addOnSuccessListener(info->{
        if(info.installStatus()==InstallStatus.DOWNLOADED){
          updateDownloaded=true;renderUpdateAction();promptToFinishUpdate();
        }else if(info.updateAvailability()==
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)){
          playUpdates.startUpdateFlow(info,this,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
        }
      });
    }
  }
  @Override protected void onStop(){
    stopDashboardGps();stopNewsGps();super.onStop();
  }
  @Override protected void onDestroy(){
    if(playUpdates!=null)playUpdates.unregisterListener(playUpdateListener);
    stopDashboardGps();stopNewsGps();respondToLocationRequest(false);
    areaRefreshHandler.removeCallbacksAndMessages(null);
    if(web!=null)web.destroy();super.onDestroy();
  }
}