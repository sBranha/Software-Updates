package com.newswave.live;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Phone-only, publisher-controlled live television listings and embedded
 * broadcaster web players. This is NOT redistribution or a TV tuner.
 * Some networks require sign-in, or do not support WebView playback.
 */
public final class LiveTVActivity extends Activity {
    private static final int BG=0xff071321,WHITE=Color.WHITE,
        CYAN=0xff85e9ef,MUTED=0xffb1c8d9,GOLD=0xffffcb8b;
    private static final String PREF="newswire-live-tv";
    private final List<TVChannel> catalog=new ArrayList<>();
    private LinearLayout root,list;
    private ScrollView scroll;
    private EditText search;
    private TextView status,filterAction;
    private EditText streamLink;
    private FrameLayout playerBox,videoFullscreen;
    private WebView player;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private String filter="all";
    private TVChannel selected;
    private double lat=Double.NaN,lon=Double.NaN;
    private String city="",state="";
    private boolean closing=false;

    static final class TVChannel {
        final String id,name,network,url,market;
        final boolean local,siteOnly;
        final double lat,lon;
        TVChannel(String id,String name,String network,String url,String market,
            boolean local,boolean siteOnly,double lat,double lon){
            this.id=id;this.name=name;this.network=network;this.url=url;this.market=market;
            this.local=local;this.siteOnly=siteOnly;this.lat=lat;this.lon=lon;
        }
    }
    private int dp(int px){return (int)(px*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable surface(int first,int second){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});
        d.setCornerRadius(dp(16));d.setStroke(dp(1),0xff3b7893);return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private TextView button(String label,Runnable action){
        TextView t=text(label,13,WHITE,true);t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12),dp(13),dp(12),dp(13));t.setBackground(surface(0xff143b53,0xff092032));
        t.setOnClickListener(v->action.run());return t;
    }
    private void add(LinearLayout box,View item,int gap){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(gap);box.addView(item,p);
    }
    private void register(String id,String name,String network,String link,String market,
        boolean local,boolean siteOnly,double lat,double lon){
        catalog.add(new TVChannel(id,name,network,link,market,local,siteOnly,lat,lon));
    }
    private void setupCatalog(){
        // National channels have a free official live news stream. Playback may
        // redirect outside WebView or require cookies according to publisher.
        register("abc","ABC News Live","ABC News","https://abcnews.com/live",
            "National · live news",false,false,0,0);
        register("cbs","CBS News 24/7","CBS News","https://www.cbsnews.com/live/",
            "National · live news",false,false,0,0);
        register("nbc","NBC News NOW","NBC News","https://www.nbcnews.com/now",
            "National · live news",false,false,0,0);
        register("foxweather","FOX Weather","FOX Weather","https://www.foxweather.com/live",
            "National · weather",false,false,0,0);
        // Ryan Hall publishes live weather coverage only during active events.
        // A channel /live URL may show past content when the channel is offline.
        // The publisher's own page determines real live status and playback.
        register("ryanhall-youtube","Ryan Hall Y'all · YouTube","Ryan Hall Y'all",
            "https://www.youtube.com/@RyanHallYall/live",
            "Official channel · live when broadcasting",false,true,0,0);
        register("ryanhall-facebook","Ryan Hall Y'all · Facebook","Ryan Hall Y'all",
            "https://www.facebook.com/ryanhallyall/videos/",
            "Official page · live when broadcasting",false,true,0,0);

        // Regional entries point to the broadcaster's own site. A site-only
        // entry is not misrepresented as continuous video.
        register("waff","WAFF 48 News","NBC","https://www.waff.com/livestream/",
            "Huntsville / Tennessee Valley",true,false,34.7304,-86.5861);
        register("waay","WAAY 31","ABC","https://www.waaytv.com/",
            "Huntsville / Tennessee Valley",true,true,34.7304,-86.5861);
        register("whnt","WHNT News 19","CBS","https://whnt.com/",
            "Huntsville / Tennessee Valley",true,true,34.7304,-86.5861);
        register("wbrc","WBRC 6 News","FOX","https://www.wbrc.com/livestream/",
            "Birmingham",true,false,33.5186,-86.8104);
        register("wvtm","WVTM 13","NBC","https://www.wvtm13.com/nowcast",
            "Birmingham",true,false,33.5186,-86.8104);
        register("fox10","FOX 10 Mobile","FOX","https://www.fox10tv.com/livestream/",
            "Mobile / Gulf Coast",true,false,30.6954,-88.0399);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        SharedPreferences p=getSharedPreferences("newswire-location",MODE_PRIVATE);
        city=getIntent().getStringExtra("city");this.state=getIntent().getStringExtra("state");
        if(city==null||city.isEmpty())city=p.getString("city","");
        if(this.state==null||this.state.isEmpty())this.state=p.getString("state","");
        lat=getIntent().getDoubleExtra("latitude",Double.NaN);
        lon=getIntent().getDoubleExtra("longitude",Double.NaN);
        if(!Double.isFinite(lat))lat=Double.longBitsToDouble(p.getLong("lat",Double.doubleToLongBits(Double.NaN)));
        if(!Double.isFinite(lon))lon=Double.longBitsToDouble(p.getLong("lon",Double.doubleToLongBits(Double.NaN)));
        setupCatalog();
        build();
    }
    private Set<String> favorites(){
        return new HashSet<>(getSharedPreferences(PREF,MODE_PRIVATE)
            .getStringSet("favorites",Collections.emptySet()));
    }
    private void toggle(String id){
        Set<String> ids=favorites();
        if(!ids.add(id))ids.remove(id);
        getSharedPreferences(PREF,MODE_PRIVATE).edit().putStringSet("favorites",ids).apply();
        render();
    }
    private double miles(TVChannel item){
        if(!item.local||!Double.isFinite(lat)||!Double.isFinite(lon))return Double.NaN;
        double a=Math.toRadians(item.lat-lat),b=Math.toRadians(item.lon-lon);
        double x=Math.pow(Math.sin(a/2),2)+Math.cos(Math.toRadians(lat))*
            Math.cos(Math.toRadians(item.lat))*Math.pow(Math.sin(b/2),2);
        return 3958.8*2*Math.asin(Math.min(1,Math.sqrt(x)));
    }
    private boolean nearby(TVChannel item){
        if(!item.local)return false;
        double distance=miles(item);
        return Double.isFinite(distance)&&distance<=125;
    }
    private void build(){
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(15),dp(20),dp(15),dp(28));
        scroll.addView(root);setContentView(scroll);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);
        hero.setBackground(surface(0xff185272,0xff0b2339));
        hero.setPadding(dp(16),dp(18),dp(16),dp(19));
        hero.addView(text("NEWSWIRE  /  LIVE TV",11,CYAN,true));
        add(hero,text("WATCH LOCAL & NATIONAL NEWS",23,WHITE,true),8);
        add(hero,text(city.isEmpty()?"Set ZIP for nearby stations":
            "⌖  "+city+", "+state+" · nearby stations first",12,MUTED,false),9);
        add(root,hero,0);

        status=text("Choose a station to open its official live page.",12,CYAN,false);
        add(root,status,12);
        playerBox=new FrameLayout(this);playerBox.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams video=new LinearLayout.LayoutParams(-1,dp(250));
        video.topMargin=dp(12);root.addView(playerBox,video);
        playerBox.setVisibility(View.GONE);
        add(root,button("↗  OPEN SELECTED STATION IN BROWSER",()->{
            if(selected!=null)openBrowser(selected.url);
        }),9);
        add(root,button("■  CLOSE TV PLAYER",this::closePlayer),8);
        add(root,text("Official broadcaster pages may require a Play tap, sign-in, or "+
            "external browser; NewsWire does not bypass broadcast rights.",11,MUTED,false),8);

        add(root,text("STORM CHASER LIVE LINK",15,CYAN,true),17);
        add(root,text("Paste a YouTube, Facebook or Twitch live-video link. "+
            "The phone opens the official source; the selected channel is saved "+
            "for the Android Auto information screen. Standard Android Auto "+
            "cannot display this video on the dashboard.",11,MUTED,false),6);
        streamLink=new EditText(this);
        streamLink.setSingleLine(true);
        streamLink.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_VARIATION_URI);
        streamLink.setTextColor(WHITE);streamLink.setHintTextColor(MUTED);
        streamLink.setHint("https://www.youtube.com/watch?v=...");
        streamLink.setText(getSharedPreferences(PREF,MODE_PRIVATE)
            .getString("last_stream_url",""));
        streamLink.setPadding(dp(12),dp(13),dp(12),dp(13));
        streamLink.setBackground(surface(0xff123047,0xff0b2032));
        add(root,streamLink,9);
        add(root,button("▶  OPEN LIVE LINK ON PHONE",this::watchCustomLink),9);
        add(root,button("↗  OPEN STREAM IN ITS OFFICIAL APP",()->{
            String link=streamLink.getText().toString().trim();
            if(isApprovedVideoLink(link))openBrowser(link);
            else status.setText("Use an HTTPS YouTube, Facebook or Twitch URL.");
        }),8);

        LinearLayout filters=new LinearLayout(this);filters.setOrientation(0);
        for(String type:new String[]{"all","local","national","saved"}){
            TextView action=button(type.toUpperCase(Locale.US),()->{
                filter=type;render();
            });
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
            p.setMargins(dp(2),0,dp(2),0);filters.addView(action,p);
        }
        add(root,filters,17);
        search=new EditText(this);search.setSingleLine(true);
        search.setTextColor(WHITE);search.setHintTextColor(MUTED);
        search.setHint("Search station or network");
        search.setPadding(dp(12),dp(12),dp(12),dp(12));
        search.setBackground(surface(0xff123047,0xff0b2032));
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int c){render();}
            public void afterTextChanged(Editable e){}
        });
        add(root,search,10);
        filterAction=text("",12,CYAN,true);add(root,filterAction,12);
        list=new LinearLayout(this);list.setOrientation(1);add(root,list,4);
        add(root,button("⌕  FIND OTHER TV STATIONS NEAR ME",()->
            startActivity(getIntentForFinder())),13);
        add(root,button("←  BACK TO NEWSWIRE",this::finish),10);
        render();
    }
    private Intent getIntentForFinder(){
        Intent intent=new Intent(this,StationFinderActivity.class);
        intent.putExtra("city",city).putExtra("state",state);
        if(Double.isFinite(lat)&&Double.isFinite(lon)){
            intent.putExtra("latitude",lat).putExtra("longitude",lon);
        }
        return intent;
    }
    private void render(){
        if(list==null)return;
        list.removeAllViews();
        String term=search==null?"":search.getText().toString().trim().toLowerCase(Locale.US);
        Set<String> saved=favorites();
        ArrayList<TVChannel> sorted=new ArrayList<>(catalog);
        sorted.sort((a,b)->{
            boolean favA=saved.contains(a.id),favB=saved.contains(b.id);
            if(favA!=favB)return favA?-1:1;
            boolean nearA=nearby(a),nearB=nearby(b);
            if(nearA!=nearB)return nearA?-1:1;
            return a.name.compareToIgnoreCase(b.name);
        });
        int shown=0;
        for(TVChannel item:sorted){
            boolean favorite=saved.contains(item.id);
            if(filter.equals("saved")&&!favorite)continue;
            if(filter.equals("local")&&(!item.local||!nearby(item)))continue;
            if(filter.equals("national")&&item.local)continue;
            if(filter.equals("all")&&item.local&&!nearby(item)&&!favorite)continue;
            if(!term.isEmpty()&&!(item.name+" "+item.network+" "+item.market)
                .toLowerCase(Locale.US).contains(term))continue;
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);
            card.setPadding(dp(13),dp(13),dp(13),dp(13));
            card.setBackground(surface(0xff163c55,0xff0d2439));
            LinearLayout top=new LinearLayout(this);top.setOrientation(0);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=text((item==selected?"▶  ":"▣  ")+item.name,17,WHITE,true);
            top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            title.setOnClickListener(v->watch(item));
            TextView star=text(favorite?"★":"☆",25,favorite?GOLD:CYAN,true);
            star.setPadding(dp(12),dp(4),dp(6),dp(4));
            star.setContentDescription((favorite?"Remove ":"Save ")+item.name+" favorite");
            star.setOnClickListener(v->toggle(item.id));top.addView(star);
            card.addView(top);
            add(card,text(item.network+" · "+item.market,12,MUTED,false),5);
            add(card,text(item.siteOnly?"OFFICIAL STATION WEBSITE · VIDEO NOT GUARANTEED":
                item.local?"OFFICIAL LIVE NEWS PAGE · SCHEDULED COVERAGE":
                           "OFFICIAL LIVE NEWS SERVICE",10,CYAN,true),7);
            card.setOnClickListener(v->watch(item));
            add(list,card,9);shown++;
        }
        filterAction.setText(filter.toUpperCase(Locale.US)+"  ·  "+shown+" stations");
        if(shown==0)add(list,text(filter.equals("saved")?"No favorite TV stations yet.":
            "No verified live TV stations for this area. Use FIND OTHER TV STATIONS "+
            "to search nearby broadcasters.",12,GOLD,false),10);
    }
    private boolean isApprovedVideoLink(String address){
        try{
            Uri uri=Uri.parse(address);
            if(!"https".equalsIgnoreCase(uri.getScheme()))return false;
            if(uri.getUserInfo()!=null)return false;
            String host=uri.getHost();
            if(host==null)return false;
            host=host.toLowerCase(Locale.US);
            for(String domain:new String[]{"youtube.com","youtu.be","facebook.com",
                "fb.watch","twitch.tv"}){
                if(host.equals(domain)||host.endsWith("."+domain))return true;
            }
        }catch(RuntimeException ignored){}
        return false;
    }
    private void watchCustomLink(){
        String link=streamLink.getText().toString().trim();
        if(!isApprovedVideoLink(link)){
            status.setText("Only HTTPS links from YouTube, Facebook or Twitch are supported.");
            return;
        }
        Uri uri=Uri.parse(link);
        String host=uri.getHost()==null?"Storm video":uri.getHost();
        getSharedPreferences(PREF,MODE_PRIVATE).edit()
            .putString("last_stream_url",link).apply();
        watch(new TVChannel("custom","My storm stream",host,link,
            "User-selected official streaming link",false,true,0,0));
    }
    // This saves the user's selection for Android Auto but does not claim
    // video projection: video is not available in the weather-map template.
    private void saveForCar(TVChannel channel){
        getSharedPreferences("newswire-car-stream",MODE_PRIVATE).edit()
            .putString("title",channel.name)
            .putString("url",channel.url)
            .putLong("selected_at",System.currentTimeMillis())
            .apply();
    }
    private void watch(TVChannel channel){
        if(!isApprovedVideoLink(channel.url)&&channel.id.equals("custom"))return;
        selected=channel;
        saveForCar(channel);
        closeWebView();
        status.setText("Opening "+channel.name+
            " on the phone · saved for car info. Dashboard video requires a "+
            "compatible parked-video system or external adapter.");
        playerBox.setVisibility(View.VISIBLE);
        player=new WebView(this);
        player.setBackgroundColor(Color.BLACK);
        player.getSettings().setJavaScriptEnabled(true);
        player.getSettings().setDomStorageEnabled(true);
        player.getSettings().setMediaPlaybackRequiresUserGesture(true);
        player.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        player.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                String scheme=request.getUrl().getScheme();
                if("https".equalsIgnoreCase(scheme))return false;
                if(!request.isForMainFrame())return true;
                return true; // Never open non-HTTPS schemes without user action.
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame()&&selected==channel)
                    status.setText("Embedded player unavailable. Tap OPEN IN BROWSER above.");
            }
        });
        player.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(customView!=null){callback.onCustomViewHidden();return;}
                customView=view;customCallback=callback;
                videoFullscreen=new FrameLayout(LiveTVActivity.this);
                videoFullscreen.setBackgroundColor(Color.BLACK);
                videoFullscreen.addView(view,new FrameLayout.LayoutParams(-1,-1));
                addContentView(videoFullscreen,new ViewGroup.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView(){closeFullscreen();}
        });
        playerBox.addView(player,new FrameLayout.LayoutParams(-1,-1));
        player.loadUrl(channel.url);
        render();
        scroll.smoothScrollTo(0,0);
    }
    private void openBrowser(String url){
        if(url==null||!url.startsWith("https://"))return;
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception error){status.setText("Browser unavailable on this phone.");}
    }
    private void closeFullscreen(){
        if(customView==null)return;
        try{((ViewGroup)videoFullscreen.getParent()).removeView(videoFullscreen);}
        catch(Exception ignored){}
        videoFullscreen=null;customView=null;
        if(customCallback!=null){
            customCallback.onCustomViewHidden();customCallback=null;
        }
    }
    private void closeWebView(){
        closeFullscreen();
        if(player!=null){
            player.stopLoading();
            player.loadUrl("about:blank");
            player.setWebChromeClient(null);player.setWebViewClient(null);
            playerBox.removeView(player);
            player.destroy();player=null;
        }
    }
    private void closePlayer(){
        closeWebView();selected=null;
        if(playerBox!=null)playerBox.setVisibility(View.GONE);
        if(status!=null)status.setText("TV player closed.");
        render();
    }
    @Override public void onBackPressed(){
        if(customView!=null){closeFullscreen();return;}
        if(player!=null){closePlayer();return;}
        super.onBackPressed();
    }
    @Override protected void onStop(){
        if(player!=null)player.onPause();
        super.onStop();
    }
    @Override protected void onResume(){
        super.onResume();
        if(player!=null)player.onResume();
    }
    @Override protected void onDestroy(){
        closing=true;closeWebView();super.onDestroy();
    }
}