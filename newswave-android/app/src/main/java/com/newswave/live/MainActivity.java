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
  private String tab="All";
  private String query="";
  private final String[] tabs={"All","Radar","National","Weather","Local","Favorites"};

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    prefs=getSharedPreferences("newswave-favorites",MODE_PRIVATE);
    home();
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
  private void home(){
    current=null;
    if(web!=null){web.destroy();web=null;}
    root=column();root.setBackgroundColor(BG);setContentView(root);
    LinearLayout header=column();pad(header,20,20);
    TextView brand=text("◉  NEWSWAVE LIVE",22,FG,true);header.addView(brand);
    TextView subtitle=text("News channels, local coverage & free NOAA radar.",13,MUTED,false);
    LinearLayout.LayoutParams sl=lp(-1,-2);sl.topMargin=dp(7);header.addView(subtitle,sl);
    root.addView(header);
    TextView notice=text("OFFICIAL CHANNEL LINKS  •  Live coverage depends on the broadcaster",11,ACCENT,true);
    pad(notice,20,8);root.addView(notice);
    search=new EditText(this);
    search.setSingleLine(true);search.setHint("Search news channels…");search.setHintTextColor(MUTED);
    search.setTextColor(FG);search.setTextSize(16);search.setBackground(box(PANEL,13));pad(search,14,10);
    LinearLayout.LayoutParams sp=lp(-1,52);sp.setMargins(dp(16),dp(12),dp(16),dp(8));
    root.addView(search,sp);search.setText(query);
    search.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int a,int c,int d){}
      public void onTextChanged(CharSequence s,int a,int before,int count){query=s.toString();render();}
      public void afterTextChanged(Editable s){}
    });
    HorizontalScrollView tabsScroller=new HorizontalScrollView(this);tabsScroller.setHorizontalScrollBarEnabled(false);
    tabBar=row();pad(tabBar,12,8);tabsScroller.addView(tabBar);
    root.addView(tabsScroller);makeTabs();
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
    for(Channel ch:channels){
      if(!tab.equals("All")&&!tab.equals(ch.category)&&!(tab.equals("Favorites")&&fav(ch)))continue;
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
    if(!tab.equals("All")||!query.isEmpty()){tab="All";query="";home();return;}
    super.onBackPressed();
  }
  @Override protected void onDestroy(){if(web!=null)web.destroy();super.onDestroy();}
}