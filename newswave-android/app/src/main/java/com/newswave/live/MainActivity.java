package com.newswave.live;

import android.app.Activity;
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
  private static final int NEWS_NOTIFICATION_REQUEST=145;
  private AppUpdateManager playUpdates;
  private boolean updateAvailable=false;
  private boolean checkingUpdate=false;
  private long lastUpdatePoll=0;
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
  private final String[] tabs={"All","Radar","National","Weather","Local","Favorites"};

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
    NewsMonitorWorker.schedule(this,localNewsNotificationEnabled());
    playUpdates=AppUpdateManagerFactory.create(this);
    checkForUpdates(false);
  }
  private void checkForUpdates(boolean interactive){
    if(playUpdates==null)playUpdates=AppUpdateManagerFactory.create(this);
    if(checkingUpdate)return;
    long now=System.currentTimeMillis();
    if(!interactive && now-lastUpdatePoll<5*60*1000L)return;
    checkingUpdate=true;lastUpdatePoll=now;
    playUpdates.getAppUpdateInfo().addOnSuccessListener(info->{
      checkingUpdate=false;
      boolean available=info.updateAvailability()==UpdateAvailability.UPDATE_AVAILABLE;
      if(available && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)){
        updateAvailable=true;
        if(interactive){
          playUpdates.startUpdateFlow(info,this,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
        }else if(tab.equals("News")||tab.equals("Weather")||tab.equals("Sports")){
          android.widget.Toast.makeText(this,"NewsWire update available in Google Play",1).show();
        }else dashboard();
      }else{
        updateAvailable=false;
        if(interactive)android.widget.Toast.makeText(this,
          "You're on the latest Google Play version of NewsWire.",1).show();
      }
    }).addOnFailureListener(error->{
      checkingUpdate=false;
      if(interactive)android.widget.Toast.makeText(this,
        "Update check unavailable. Verify your Google Play connection.",1).show();
    });
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
    newsScreenVisible=false;
    current=null;query="";stopNewsGps();if(web!=null){respondToLocationRequest(false);web.destroy();web=null;}
    root=column();root.setBackground(atmosphere());setContentView(root);
    ScrollView sc=new ScrollView(this);LinearLayout content=column();pad(content,18,20);
    sc.addView(content);root.addView(sc);
    LinearLayout hero=column();hero.setBackground(radiantCard(0xff152f4c,0xff0d192b,0xff2e6174));
    pad(hero,19,20);
    TextView eyebrow=text("NEWSWIRE   /   LIVE",11,0xff62dbe6,true);hero.addView(eyebrow);
    TextView brand=text("YOUR WORLD.\\nIN THE KNOW.",27,FG,true);
    LinearLayout.LayoutParams bp0=lp(-1,-2);bp0.topMargin=dp(8);hero.addView(brand,bp0);
    TextView sub=text("Local news   •   Storm radar   •   Your teams",13,0xffbfd4e6,false);
    LinearLayout.LayoutParams su=lp(-1,-2);su.topMargin=dp(9);hero.addView(sub,su);
    LinearLayout.LayoutParams heroLp=lp(-1,-2);heroLp.bottomMargin=dp(17);content.addView(hero,heroLp);
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
    categoryTile(content,"NEWS","Your local headlines, live","◉",0xff2b65c3,()->openSection("News"));
    categoryTile(content,"WEATHER","Satellite radar & NWS warnings","☁",0xff097e98,()->openSection("Weather"));
    categoryTile(content,"SPORTS","Follow local high-school teams","★",0xffa15620,()->openSection("Sports"));
    TextView utilities=text("MAKE NEWSWIRE YOURS",12,0xff62dbe6,true);
    LinearLayout.LayoutParams utilityLp=lp(-1,-2);utilityLp.topMargin=dp(13);
    utilityLp.bottomMargin=dp(9);content.addView(utilities,utilityLp);
    TextView alertToggle=button(localNewsNotificationEnabled()?
      "◉  LOCAL NEWS ALERTS ON · TAP TO TURN OFF":"◉  TURN ON LOCAL NEWS ALERTS",
      this::toggleNewsNotifications);
    LinearLayout.LayoutParams alertLp=lp(-1,-2);alertLp.bottomMargin=dp(10);
    content.addView(alertToggle,alertLp);
    TextView updates=button(updateAvailable?
      "↑  NEWSWIRE UPDATE READY IN GOOGLE PLAY":
      "↑  CHECK FOR APP UPDATES",()->checkForUpdates(true));
    content.addView(updates);
    TextView alertInfo=text("Optional local headline notifications appear on your phone, not over the car radar. Updates are handled by Google Play.",11,MUTED,false);
    pad(alertInfo,5,12);content.addView(alertInfo);
    TextView foot=text("NewsWire v"+installedVersion()+"  •  More sections arrive through official app updates.",12,MUTED,false);
    pad(foot,5,20);content.addView(foot);
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
  private void openSection(String name){tab=name;query="";home();}
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
    newsScreenVisible=tab.equals("News");
    current=null;
    if(web!=null){respondToLocationRequest(false);web.destroy();web=null;}
    root=column();root.setBackgroundColor(BG);setContentView(root);
    LinearLayout header=column();pad(header,20,20);
    TextView back=button("‹  MENU",()->dashboard());header.addView(back);
    TextView brand=text("NEWSWIRE  /  "+tab.toUpperCase(Locale.US),24,FG,true);
    LinearLayout.LayoutParams bh=lp(-1,-2);bh.topMargin=dp(13);header.addView(brand,bh);
    TextView subtitle=text(tab.equals("News")?"Local papers follow your location • saved ZIP is backup":zip.isEmpty()?(tab.equals("Sports")?"Search by ZIP below for local football scores":"Set a ZIP on the home screen for local results"):city+", "+state+"  •  "+zip,13,MUTED,false);
    LinearLayout.LayoutParams sl=lp(-1,-2);sl.topMargin=dp(7);header.addView(subtitle,sl);
    root.addView(header);
    TextView notice=text(tab.equals("News")?"LOCAL HEADLINES  •  NEWSPAPERS  •  NETWORK NEWS":"OFFICIAL CHANNEL LINKS  •  Live coverage depends on the broadcaster",11,ACCENT,true);
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
      TextView radar=button("◎  LIVE RADAR  •  GPS FOLLOW",()->startActivity(new Intent(MainActivity.this,RadarActivity.class)));
      LinearLayout.LayoutParams rp=lp(-1,-2);rp.bottomMargin=dp(13);list.addView(radar,rp);
      if(!Double.isNaN(postalLat)){
        card(localLink("zipweather","Local NWS forecast — "+zip,"Weather","Forecast for "+city+", "+state,
          String.format(Locale.US,"https://forecast.weather.gov/MapClick.php?lat=%.5f&lon=%.5f",postalLat,postalLon)));
        count++;
      }
    }
    if(tab.equals("News"))count+=renderLocalNews();
    if(tab.equals("Sports")){
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
      if(tab.equals("News")&&!ch.category.equals("National")&&!ch.category.equals("Local"))continue;
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
    if(tab.equals("News")||tab.equals("Weather")||tab.equals("Sports")){dashboard();return;}
    super.onBackPressed();
  }
  @Override protected void onResume(){super.onResume();if(newsScreenVisible&&tab.equals("News")&&current==null)startNewsGps(false);}
  @Override protected void onStop(){stopNewsGps();super.onStop();}
  @Override protected void onDestroy(){stopNewsGps();respondToLocationRequest(false);if(web!=null)web.destroy();super.onDestroy();}
}