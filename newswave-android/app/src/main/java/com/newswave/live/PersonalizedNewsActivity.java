package com.newswave.live;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Gravity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Account-free local feed, with favorite towns, publishers, muted topic
 * filters and a persistent reading list. Every link opens the publisher. */
public final class PersonalizedNewsActivity extends Activity {
    private static final int BG=0xff0a1425,WHITE=Color.WHITE,
        CYAN=0xff86ebf3,MUTED=0xffaec5d7,GOLD=0xffffc27f;
    private LinearLayout root,feed;
    private boolean loading=false,closed=false;
    private int generation=0;
    private List<LocalNewspapers.Headline> headlines=new ArrayList<>();
    private String lastKey="";
    @Override public void onCreate(Bundle b){super.onCreate(b);draw();loadNews(false);}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5);}
    private GradientDrawable card(int a,int b,int edge){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});
        g.setCornerRadius(dp(18));g.setStroke(dp(1),edge);return g;
    }
    private TextView text(String label,int size,int tone,boolean bold){
        TextView v=new TextView(this);v.setText(label);v.setTextSize(size);
        v.setTextColor(tone);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private TextView button(String label,Runnable action){
        TextView v=text(label,12,WHITE,true);v.setGravity(Gravity.CENTER);
        v.setBackground(card(0xff1b3d57,0xff0e2439,0xff2e728b));
        v.setPadding(dp(12),dp(13),dp(12),dp(13));v.setOnClickListener(x->action.run());return v;
    }
    private void draw(){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(16),dp(20),dp(16),dp(30));
        sc.addView(root);setContentView(sc);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);
        hero.setBackground(card(0xff183f5b,0xff102235,0xff397e96));
        hero.setPadding(dp(18),dp(19),dp(18),dp(19));
        hero.addView(text("NEWSWIRE / JUST FOR YOU",11,CYAN,true));
        TextView title=text("MY NEWS",28,WHITE,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);
        tp.topMargin=dp(8);hero.addView(title,tp);
        TextView sub=text("Follow towns. Prioritize publishers. Save what matters.",13,MUTED,false);
        hero.addView(sub);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.bottomMargin=dp(13);root.addView(hero,hp);
        String city=NewsPreferences.favoriteCity(this),
            state=NewsPreferences.favoriteState(this),
            publisher=NewsPreferences.favoritePublisher(this);
        String savedCity=getSharedPreferences("newswire-location",MODE_PRIVATE)
            .getString("city","");
        String savedRegion=getSharedPreferences("newswire-location",MODE_PRIVATE)
            .getString("state","");
        String area=city.isEmpty()?
            (savedCity.isEmpty()?"YOUR AREA / SAVE A ZIP ON HOME":savedCity+", "+savedRegion+" · ZIP"):
            city+", "+state+" · FOLLOWING";
        TextView where=text("⌖  "+area,12,CYAN,true);
        where.setPadding(dp(4),dp(10),dp(4),dp(9));root.addView(where);

        TextView cityButton=button("⌖  FOLLOW A TOWN / CHANGE MY NEWS AREA",this::editFavoriteArea);
        root.addView(cityButton);
        TextView sourceButton=button("★  PREFERRED PUBLISHER"+(publisher.isEmpty()?"":": "+publisher),
            this::editPublisher);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(9);root.addView(sourceButton,lp);
        TextView topics=button("⚙  HIDE TOPICS I DON'T WANT",this::editMutedTopics);
        LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(-1,-2);
        m.topMargin=dp(9);root.addView(topics,m);
        TextView bookmarks=button("☆  READING LIST ("+NewsPreferences.saved(this).size()+")",
            this::showSavedArticles);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(-1,-2);
        b.topMargin=dp(9);root.addView(bookmarks,b);
        TextView refresh=button("↻  REFRESH MY HEADLINES",()->loadNews(true));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
        rp.topMargin=dp(9);root.addView(refresh,rp);

        TextView feedTitle=text("LATEST STORIES FOR YOU",12,CYAN,true);
        feedTitle.setPadding(dp(5),dp(22),0,dp(10));root.addView(feedTitle);
        feed=new LinearLayout(this);feed.setOrientation(1);root.addView(feed);
        renderFeed();
        TextView note=text("Headlines are attributed to original news publishers. " +
            "Publisher preferences affect ordering, not coverage guarantees. " +
            "Your bookmarks and preferences stay on this device.",
            11,MUTED,false);
        note.setPadding(dp(5),dp(15),dp(5),0);root.addView(note);
    }
    private void editFavoriteArea(){
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);
        fields.setPadding(dp(20),dp(8),dp(20),dp(4));
        EditText town=new EditText(this),region=new EditText(this);
        town.setText(NewsPreferences.favoriteCity(this));town.setHint("Town, e.g. Albertville");
        region.setText(NewsPreferences.favoriteState(this));region.setHint("State, e.g. AL");
        fields.addView(town);fields.addView(region);
        new AlertDialog.Builder(this).setTitle("Follow a town")
            .setMessage("Choose a favorite town, or clear both fields to follow your saved ZIP area.")
            .setView(fields)
            .setPositiveButton("Save",(dialog,which)->{
                String c=town.getText().toString().trim();
                String s=region.getText().toString().trim();
                if((c.isEmpty())!=(s.isEmpty())){
                    android.widget.Toast.makeText(this,"Enter both town and state, or clear both.",0).show();
                    return;
                }
                NewsPreferences.setFavoritePlace(this,c,s);
                lastKey="";headlines.clear();draw();loadNews(true);
            }).setNegativeButton("Cancel",null).show();
    }
    private void editPublisher(){
        EditText publisher=new EditText(this);
        publisher.setSingleLine(true);
        publisher.setText(NewsPreferences.favoritePublisher(this));
        publisher.setHint("e.g. WHNT");
        new AlertDialog.Builder(this).setTitle("Preferred publisher")
            .setMessage("Stories from this publisher appear first when available. Clear to remove.")
            .setView(publisher)
            .setPositiveButton("Save",(dialog,which)->{
                NewsPreferences.setFavoritePublisher(this,publisher.getText().toString());
                draw();
            }).setNegativeButton("Cancel",null).show();
    }
    private void editMutedTopics(){
        final String[] keys={"sports","crime","politics","weather"};
        final String[] labels={"Sports","Crime & policing","Politics","Weather"};
        boolean[] muted=new boolean[keys.length];
        for(int i=0;i<keys.length;i++)muted[i]=NewsPreferences.muted(this,keys[i]);
        new AlertDialog.Builder(this).setTitle("Hide these topics")
            .setMultiChoiceItems(labels,muted,(dialog,index,checked)->muted[index]=checked)
            .setPositiveButton("Save",(dialog,which)->{
                for(int i=0;i<keys.length;i++)
                    NewsPreferences.setMuted(this,keys[i],muted[i]);
                draw();
            }).setNegativeButton("Cancel",null).show();
    }
    private void showSavedArticles(){
        List<NewsPreferences.SavedArticle> bookmarks=NewsPreferences.saved(this);
        String[] titles=new String[bookmarks.size()];
        for(int i=0;i<bookmarks.size();i++)
            titles[i]=bookmarks.get(i).title+" · "+bookmarks.get(i).source;
        if(titles.length==0){android.widget.Toast.makeText(this,
            "No saved stories yet. Tap ☆ on an article to save it.",1).show();return;}
        new AlertDialog.Builder(this).setTitle("Your saved stories")
            .setItems(titles,(dialog,index)->open(bookmarks.get(index).url))
            .setPositiveButton("Close",null).show();
    }
    private void renderFeed(){
        if(feed==null)return;
        feed.removeAllViews();
        if(loading){feed.addView(text("Finding stories from your chosen area…",13,MUTED,false));return;}
        if(headlines.isEmpty()){
            feed.addView(text("No stories loaded. Check your area and tap Refresh. " +
                "Feeds may be unavailable temporarily.",13,MUTED,false));
            return;
        }
        String pref=NewsPreferences.favoritePublisher(this).toLowerCase(Locale.US);
        List<LocalNewspapers.Headline> sorted=new ArrayList<>(headlines);
        if(!pref.isEmpty())Collections.sort(sorted,(a,b)->{
            int aa=a.source.toLowerCase(Locale.US).contains(pref)?0:1;
            int bb=b.source.toLowerCase(Locale.US).contains(pref)?0:1;
            return aa-bb;
        });
        int shown=0;
        for(LocalNewspapers.Headline article:sorted){
            if(!NewsPreferences.allow(this,article.title))continue;
            LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);
            tile.setPadding(dp(15),dp(15),dp(15),dp(15));
            tile.setBackground(card(0xff173149,0xff101e31,0xff315e7d));
            tile.addView(text(article.source.toUpperCase(Locale.US),10,CYAN,true));
            TextView headline=text(article.title+"  ›",15,WHITE,true);
            headline.setPadding(0,dp(8),0,dp(10));
            tile.addView(headline);
            headline.setOnClickListener(v->open(article.url));
            TextView save=button(NewsPreferences.isSaved(this,article.url)?
                "★ SAVED · TAP TO REMOVE":"☆ SAVE FOR LATER",()->{
                boolean added=NewsPreferences.toggleBookmark(this,
                    article.title,article.source,article.url);
                android.widget.Toast.makeText(this,added?"Saved for later":"Removed bookmark",0).show();
                draw();
            });
            tile.addView(save);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.bottomMargin=dp(11);feed.addView(tile,p);
            if(++shown>=15)break;
        }
        if(shown==0)feed.addView(text(
            "All returned stories matched a hidden topic. Change topic filters to see more.",13,MUTED,false));
    }
    private void loadNews(boolean force){
        String city=NewsPreferences.favoriteCity(this),state=NewsPreferences.favoriteState(this);
        if(city.isEmpty()||state.isEmpty()){
            android.content.SharedPreferences saved=
                getSharedPreferences("newswire-location",MODE_PRIVATE);
            city=saved.getString("city","");state=saved.getString("state","");
        }
        if(city.isEmpty()||state.isEmpty()){renderFeed();return;}
        String key=city+"|"+state;
        if(!force && key.equals(lastKey) && !headlines.isEmpty()){renderFeed();return;}
        if(loading)return;
        lastKey=key;loading=true;renderFeed();
        final int rev=++generation;
        final String c=city,s=state;
        new Thread(()->{
            List<LocalNewspapers.Headline> result;
            try{result=LocalNewspapers.headlines(c,s);}
            catch(Exception e){result=Collections.emptyList();
                android.util.Log.w("NewsWirePersonal","News feed unavailable",e);}
            final List<LocalNewspapers.Headline> finalItems=result;
            runOnUiThread(()->{
                if(closed||generation!=rev)return;
                loading=false;headlines=new ArrayList<>(finalItems);
                renderFeed();
            });
        },"newswire-personal").start();
    }
    private void open(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception error){android.widget.Toast.makeText(this,"No browser available",0).show();}
    }
    @Override protected void onResume(){super.onResume();if(root!=null)draw();}
    @Override protected void onDestroy(){closed=true;generation++;super.onDestroy();}
}