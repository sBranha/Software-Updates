package com.newswave.live;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/**
 * Alabama ScoreStream live community score widget + local, attributed football
 * reporting. Never fabricate a score or confuse a news article with live score.
 */
public final class FootballBoardActivity extends Activity {
    private static final int BG=0xff081727,PANEL=0xff11263d,INK=Color.WHITE,
        CYAN=0xff80e7eb,MUTED=0xffa1bdd0,ORANGE=0xffffb36d;
    private static final String ALABAMA_BOARD=
        "https://scorestream.com/widgets/scoreboards/vert?userWidgetId=2070";
    private static final String EXTERNAL_SCORES="https://scorestream.com/";
    private String city="",state="",zip="",favorite="";
    private LinearLayout outer,stories;
    private WebView scoreboard;
    private boolean loading=false;
    private String chosenSport="football";
    private TextView sportLabel,lastCheckedLabel;
    private int generation=0;
    private long lastFetchedAt=0;

    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private GradientDrawable card(int bg,int border){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{bg,0xff0b1d31});
        d.setCornerRadius(dp(17));d.setStroke(dp(1),border);return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView button(String label,Runnable action){
        TextView b=text(label,13,INK,true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12),dp(12),dp(12),dp(12));
        b.setBackground(card(PANEL,0xff285771));
        b.setOnClickListener(v->action.run());
        return b;
    }
    private void launch(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception error){android.widget.Toast.makeText(this,"No browser available",1).show();}
    }
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        city=getIntent().getStringExtra("city");
        state=getIntent().getStringExtra("state");
        zip=getIntent().getStringExtra("zip");
        if(city==null)city="";if(state==null)state="";if(zip==null)zip="";
        SharedPreferences prefs=getSharedPreferences("newswire-football",MODE_PRIVATE);
        favorite=prefs.getString("favorite_team","");
        build();
        refresh(false);
    }
    private boolean isAlabama(){
        return "AL".equalsIgnoreCase(state)
            ||"Alabama".equalsIgnoreCase(state);
    }
    private void build(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        outer=new LinearLayout(this);outer.setOrientation(1);
        outer.setPadding(dp(16),dp(20),dp(16),dp(30));scroll.addView(outer);
        setContentView(scroll);

        LinearLayout header=new LinearLayout(this);header.setOrientation(1);
        header.setPadding(dp(17),dp(17),dp(17),dp(17));
        header.setBackground(card(0xff143f58,0xff44819a));
        TextView over=text("NEWSWIRE / FRIDAY NIGHT LIGHTS",11,CYAN,true);
        header.addView(over);
        TextView title=text("FOOTBALL LIVE",28,INK,true);
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,-2);
        titleParams.topMargin=dp(7);header.addView(title,titleParams);
        TextView sub=text(city.isEmpty()?"Find scores and local football coverage":
            city+", "+state+" • Nearby football coverage",13,MUTED,false);
        header.addView(sub);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.bottomMargin=dp(14);outer.addView(header,hp);

        LinearLayout actionRow=new LinearLayout(this);
        TextView back=button("‹ BACK",this::finish);
        TextView refresh=button("↻ REFRESH",()->refresh(true));
        actionRow.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);
        rp.leftMargin=dp(10);actionRow.addView(refresh,rp);
        outer.addView(actionRow);
        TextView sportButton=button("★ CHANGE SPORT · FOOTBALL / BASKETBALL / BASEBALL / SOCCER",()->
            new android.app.AlertDialog.Builder(this).setTitle("School sports coverage")
                .setItems(new String[]{"Football","Basketball","Baseball & Softball","Soccer"},
                    (dialog,which)->{
                        chosenSport=new String[]{"football","basketball","baseball","soccer"}[which];
                        storiesCache=null;lastFetchedAt=0;
                        if(sportLabel!=null)sportLabel.setText(
                            "NEARBY "+chosenSport.toUpperCase(Locale.US)+" REPORTING");
                        refresh(true);
                    }).setNegativeButton("Cancel",null).show());
        LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(-1,-2);
        sb.topMargin=dp(11);outer.addView(sportButton,sb);
        TextView nearTitle=text("NEARBY FOOTBALL REPORTS",13,CYAN,true);
        nearTitle.setPadding(0,dp(22),0,dp(8));outer.addView(nearTitle);
        sportLabel=nearTitle;
        lastCheckedLabel=text("Publisher feed not checked yet.",11,MUTED,false);
        outer.addView(lastCheckedLabel);
        TextView note=text("Publisher-reported football results near your saved area. " +
            "Titles link to the original sources; score articles are not verified live game feeds.",
            12,MUTED,false);outer.addView(note);
        stories=new LinearLayout(this);stories.setOrientation(1);
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);
        sl.topMargin=dp(8);outer.addView(stories,sl);

        LinearLayout favoriteRow=new LinearLayout(this);
        favoriteRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText favoriteInput=new EditText(this);
        favoriteInput.setSingleLine(true);
        favoriteInput.setTextColor(INK);favoriteInput.setHintTextColor(MUTED);
        favoriteInput.setBackground(card(PANEL,0xff31546d));
        favoriteInput.setText(favorite);
        favoriteInput.setHint("Favorite team (e.g. Albertville)");
        favoriteInput.setTextSize(13);
        favoriteInput.setPadding(dp(12),dp(9),dp(12),dp(9));
        favoriteRow.addView(favoriteInput,new LinearLayout.LayoutParams(0,dp(49),1));
        TextView save=button("FOLLOW",()->{
            favorite=favoriteInput.getText().toString().trim();
            getSharedPreferences("newswire-football",MODE_PRIVATE).edit()
                .putString("favorite_team",favorite).apply();
            renderStoriesAgain();
            android.widget.Toast.makeText(this,
                favorite.isEmpty()?"Team preference cleared":"Following "+favorite,0).show();
        });
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);
        sp.leftMargin=dp(8);favoriteRow.addView(save,sp);
        LinearLayout.LayoutParams fr=new LinearLayout.LayoutParams(-1,-2);
        fr.topMargin=dp(14);outer.addView(favoriteRow,fr);

        TextView boardTitle=text("LIVE SCOREBOARD",14,ORANGE,true);
        boardTitle.setPadding(0,dp(23),0,dp(6));outer.addView(boardTitle);
        TextView provider=text(isAlabama()?
            "Alabama high-school scores via ScoreStream's public scoreboard. " +
            "Community-reported; scores may be late or corrected.":
            "A regional live-score widget is not available for this state yet. " +
            "Open ScoreStream to find current scores; no scores are invented.",
            12,MUTED,false);outer.addView(provider);

        if(isAlabama()){
            scoreboard=new WebView(this);
            scoreboard.setBackgroundColor(BG);
            scoreboard.getSettings().setJavaScriptEnabled(true);
            scoreboard.getSettings().setDomStorageEnabled(true);
            scoreboard.getSettings().setMediaPlaybackRequiresUserGesture(true);
            scoreboard.setWebChromeClient(new WebChromeClient());
            scoreboard.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
                    if(!url.startsWith("https://scorestream.com/") &&
                        !url.startsWith("https://api.scorestream.com/")){
                        launch(url);return true;
                    }
                    return false;
                }
            });
            LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(480));
            wp.topMargin=dp(12);outer.addView(scoreboard,wp);
            scoreboard.loadUrl(ALABAMA_BOARD);
        }
        TextView source=button(isAlabama()?"OPEN FULL SCORESTREAM BOARD ↗":
            "SEARCH SCORESTREAM SCORES ↗",()->launch(isAlabama()?ALABAMA_BOARD:EXTERNAL_SCORES));
        LinearLayout.LayoutParams src=new LinearLayout.LayoutParams(-1,-2);
        src.topMargin=dp(11);outer.addView(source,src);
        TextView footer=text("Scores and schedule data come from third-party reporters. " +
            "NewsWire doesn't control when a school updates its results.",11,MUTED,false);
        footer.setPadding(0,dp(18),0,0);outer.addView(footer);
    }
    private volatile List<LocalNewspapers.Headline> storiesCache;
    private void renderStoriesAgain(){renderStories(storiesCache);}
    private void renderStories(List<LocalNewspapers.Headline> found){
        if(stories==null)return;
        stories.removeAllViews();
        if(found==null){
            TextView load=text(loading?"Loading local football coverage…":
                "No local sports stories returned. The live football scoreboard below is separate.",
                13,MUTED,false);
            load.setPadding(dp(8),dp(10),dp(8),dp(10));stories.addView(load);return;
        }
        int shown=0;
        // Favorites are an emphasis, not a false promise that every team is indexed.
        if(!favorite.isEmpty()){
            for(LocalNewspapers.Headline h:found){
                if(h.title.toLowerCase(Locale.US).contains(favorite.toLowerCase(Locale.US))){
                    showArticle(h,true);shown++;
                }
            }
        }
        for(LocalNewspapers.Headline h:found){
            if(shown>=8)break;
            if(!favorite.isEmpty()&&h.title.toLowerCase(Locale.US)
                    .contains(favorite.toLowerCase(Locale.US)))continue;
            showArticle(h,false);shown++;
        }
        if(shown==0)renderStories(null);
    }
    private void showArticle(LocalNewspapers.Headline article,boolean pinned){
        LinearLayout row=new LinearLayout(this);row.setOrientation(1);
        row.setBackground(card(pinned?0xff1b4359:PANEL,
            pinned?0xff3ac1e0:0xff284f69));
        row.setPadding(dp(14),dp(12),dp(14),dp(12));
        TextView label=text((pinned?"★ YOUR TEAM · ":"")+
            article.source.toUpperCase(Locale.US),10,CYAN,true);row.addView(label);
        TextView name=text(article.title,14,INK,true);
        name.setPadding(0,dp(6),0,0);row.addView(name);
        row.setOnClickListener(v->launch(article.url));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(9);stories.addView(row,lp);
    }
    private void refresh(boolean forced){
        long now=System.currentTimeMillis();
        if(loading||!forced&&storiesCache!=null&&now-lastFetchedAt<5*60*1000L)return;
        if(city.isEmpty()||state.isEmpty()){
            storiesCache=null;
            renderStories(null);return;
        }
        loading=true;renderStories(null);
        final int generationId=++generation;
        final String lookupCity=city,lookupState=state;
        new Thread(()->{
            List<LocalNewspapers.Headline> found=null;
            final String sport=chosenSport;
            try{found=LocalNewspapers.topicHeadlines(lookupCity,lookupState,sport);}
            catch(Exception error){android.util.Log.w("NewswireFootball","Football articles unavailable",error);}
            final List<LocalNewspapers.Headline> result=found;
            runOnUiThread(()->{
                if(generation!=generationId)return;
                loading=false;storiesCache=result;lastFetchedAt=System.currentTimeMillis();
                renderStories(result);
                if(lastCheckedLabel!=null)lastCheckedLabel.setText(
                    "Publisher feed checked "+new java.text.SimpleDateFormat(
                    "h:mm a",Locale.US).format(new java.util.Date(lastFetchedAt))+
                    " · "+chosenSport+" reporting, not verified live scores");
            });
        },"newswire-football").start();
        if(scoreboard!=null&&forced)scoreboard.reload();
    }
    @Override protected void onDestroy(){
        if(scoreboard!=null){scoreboard.stopLoading();scoreboard.destroy();scoreboard=null;}
        super.onDestroy();
    }
}
