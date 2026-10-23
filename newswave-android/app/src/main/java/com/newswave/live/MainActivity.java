package com.newswave.live;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
  private LinearLayout root, list, tabBar;
  private EditText search;
  private WebView web;
  private FrameLayout fullscreen;
  private View custom;
  private WebChromeClient.CustomViewCallback customCallback;
  private Channel current;
  private String tab="News";
  private String zip="",city="",state="";
  private double postalLat=Double.NaN,postalLon=Double.NaN;
  private String query="";
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
  }
  private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
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
    current=null;query="";if(web!=null){web.destroy();web=null;}
    root=column();root.setBackgroundColor(BG);setContentView(root);
    ScrollView sc=new ScrollView(this);LinearLayout content=column();pad(content,18,20);
    sc.addView(content);root.addView(sc);
    TextView brand=text("◉  NEWSWIRE",29,FG,true);content.addView(brand);
    TextView sub=text("YOUR NEWS  •  YOUR WEATHER  •  YOUR TEAMS",11,ACCENT,true);
    LinearLayout.LayoutParams su=lp(-1,-2);su.topMargin=dp(5);su.bottomMargin=dp(22);content.addView(sub,su);
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
    categoryTile(content,"NEWS","Local & national coverage","◉",0xff2b65c3,()->openSection("News"));
    categoryTile(content,"WEATHER","Forecasts • Live radar • Alerts","☁",0xff097e98,()->openSection("Weather"));
    categoryTile(content,"SPORTS","Local high-school football • Scores","★",0xffa15620,()->openSection("Sports"));
    TextView foot=text("Newswire v0.4.0  •  More sections arrive through official app updates.",12,MUTED,false);
    pad(foot,5,20);content.addView(foot);
  }
  private void categoryTile(LinearLayout parent,String title,String detail,String icon,int tone,Runnable click){
    LinearLayout tile=row();
    GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{tone,PANEL});
    bg.setCornerRadius(dp(21));bg.setStroke(dp(1),EDGE);tile.setBackground(bg);pad(tile,19,20);
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
  private void resolveZip(String postal){
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
          dashboard();
        });
      }catch(Exception ex){runOnUiThread(()->android.widget.Toast.makeText(this,"Could not look up ZIP. Check connection.",1).show());}
      finally{if(con!=null)con.disconnect();}
    }).start();
  }
  private Channel localLink(String id,String name,String category,String note,String url){
    return new Channel(id,name,category,note,url,false);
  }

  private void home(){
    current=null;
    if(web!=null){web.destroy();web=null;}
    root=column();root.setBackgroundColor(BG);setContentView(root);
    LinearLayout header=column();pad(header,20,20);
    TextView back=button("‹  MENU",()->dashboard());header.addView(back);
    TextView brand=text("NEWSWIRE  /  "+tab.toUpperCase(Locale.US),24,FG,true);
    LinearLayout.LayoutParams bh=lp(-1,-2);bh.topMargin=dp(13);header.addView(brand,bh);
    TextView subtitle=text(zip.isEmpty()?"Set a ZIP on the home screen for local results":city+", "+state+"  •  "+zip,13,MUTED,false);
    LinearLayout.LayoutParams sl=lp(-1,-2);sl.topMargin=dp(7);header.addView(subtitle,sl);
    root.addView(header);
    TextView notice=text("OFFICIAL CHANNEL LINKS  •  Live coverage depends on the broadcaster",11,ACCENT,true);
    pad(notice,20,8);root.addView(notice);
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
    // Category menu is publisher-defined; users cannot add buttons.
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
    list=column();pad(list,16,7);scroll.addView(list);
    root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));render();
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
    if(tab.equals("News")&&!zip.isEmpty()){
      try{
        String searchTerm=URLEncoder.encode(city+" "+state+" local news","UTF-8");
        card(localLink("zipnews","Local news near "+city,"News","Regional news for your saved ZIP",
             "https://news.google.com/search?q="+searchTerm+"&hl=en-US&gl=US&ceid=US%3Aen"));
        count++;
      }catch(Exception ignored){}
    }
    if(tab.equals("Sports")){
      if(state.matches("[A-Z]{2}")){
        card(localLink("scores"+state,"High-school football — "+state,"Sports",
          "State scores & schedules; select nearby teams on the scoreboard",
          "https://www.maxpreps.com/"+state.toLowerCase(Locale.US)+"/football/scores/"));
        count++;
      }else{
        TextView help=text("Save your ZIP on the main menu to show high-school scores for your state.",15,MUTED,false);
        pad(help,8,13);list.addView(help);
      }
    }
    for(Channel ch:channels){
      if(tab.equals("Sports"))continue;
      if(tab.equals("Weather")&&!ch.category.equals("Weather"))continue;
      if(tab.equals("News")&&!ch.category.equals("National")&&!ch.category.equals("Local"))continue;
      if(ch.category.equals("Local")&&!state.equals("AL"))continue;
      String needle=query.toLowerCase(Locale.US);
      if(!ch.name.toLowerCase(Locale.US).contains(needle)&&!ch.note.toLowerCase(Locale.US).contains(needle))continue;
      count++; card(ch);
    }
    if(count==0){
      TextView empty=text("No stations found.\nTry another search or tab.",16,MUTED,false);
      empty.setGravity(Gravity.CENTER);pad(empty,12,35);list.addView(empty);
    }
    TextView footer=text("Independent channel guide. Video belongs to each broadcaster. Some channels need a paid login or must open in your browser.",12,MUTED,false);
    pad(footer,8,22);list.addView(footer);
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
    TextView badge=text(ch.signIn?"SIGN-IN / SUBSCRIPTION":"OFFICIAL WATCH PAGE",11,ch.signIn?Color.rgb(253,199,102):ACCENT,true);
    LinearLayout.LayoutParams bd=lp(-1,-2);bd.topMargin=dp(12);c.addView(badge,bd);
    c.setOnClickListener(v->watch(ch));
    LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(12);list.addView(c,p);
  }
  private void watch(Channel ch){
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
  @Override protected void onDestroy(){if(web!=null)web.destroy();super.onDestroy();}
}