package com.newswave.live;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** On-device, account-free personalization and private reading list. */
final class NewsPreferences {
    static final String PREF="newswire-personalization";
    static final class SavedArticle {
        final String title,source,url;
        SavedArticle(String title,String source,String url){
            this.title=title;this.source=source;this.url=url;
        }
    }
    private NewsPreferences(){}
    static SharedPreferences prefs(Context ctx){return ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    static String favoriteCity(Context ctx){return prefs(ctx).getString("favorite_city","").trim();}
    static String favoriteState(Context ctx){return prefs(ctx).getString("favorite_state","").trim();}
    static String favoritePublisher(Context ctx){return prefs(ctx).getString("favorite_publisher","").trim();}
    static void setFavoritePlace(Context ctx,String city,String state){
        prefs(ctx).edit().putString("favorite_city",city.trim())
            .putString("favorite_state",state.trim()).apply();
    }
    static void setFavoritePublisher(Context ctx,String name){
        prefs(ctx).edit().putString("favorite_publisher",name.trim()).apply();
    }
    static boolean muted(Context ctx,String category){
        return prefs(ctx).getBoolean("mute_"+category,false);
    }
    static void setMuted(Context ctx,String category,boolean hidden){
        prefs(ctx).edit().putBoolean("mute_"+category,hidden).apply();
    }
    static boolean matchesCategory(String text,String category){
        String lower=text.toLowerCase(Locale.US);
        switch(category){
            case "sports":
                return lower.matches(".*(football|basketball|baseball|softball|volleyball|soccer|sports|playoff|athletics).*");
            case "crime":
                return lower.matches(".*(police|arrest|crime|shooting|charged|murder|robbery|investigation).*");
            case "politics":
                return lower.matches(".*(election|senator|congress|governor|politic|campaign|legislature).*");
            case "weather":
                return lower.matches(".*(weather|thunderstorm|tornado|flood|storm|rainfall|heat wave|snow).*");
            default:return false;
        }
    }
    static boolean allow(Context ctx,String title){
        for(String category:new String[]{"sports","crime","politics","weather"}){
            if(muted(ctx,category)&&matchesCategory(title,category))return false;
        }
        return true;
    }
    static List<SavedArticle> saved(Context ctx){
        ArrayList<SavedArticle> found=new ArrayList<>();
        try{
            JSONArray items=new JSONArray(prefs(ctx).getString("saved_articles","[]"));
            for(int i=0;i<items.length()&&found.size()<60;i++){
                JSONObject item=items.optJSONObject(i);
                if(item==null)continue;
                String url=item.optString("url","");
                if(!url.startsWith("https://"))continue;
                found.add(new SavedArticle(item.optString("title",""),
                    item.optString("source",""),url));
            }
        }catch(Exception ignored){}
        return found;
    }
    static boolean isSaved(Context ctx,String url){
        for(SavedArticle item:saved(ctx))if(url.equals(item.url))return true;
        return false;
    }
    static boolean toggleBookmark(Context ctx,String title,String source,String url){
        if(url==null||!url.startsWith("https://"))return false;
        List<SavedArticle> items=saved(ctx);
        boolean wasSaved=false;
        for(int i=items.size()-1;i>=0;i--)if(url.equals(items.get(i).url)){
            items.remove(i);wasSaved=true;
        }
        if(!wasSaved)items.add(0,new SavedArticle(title,source,url));
        while(items.size()>50)items.remove(items.size()-1);
        JSONArray json=new JSONArray();
        for(SavedArticle article:items){
            JSONObject item=new JSONObject();
            try{
                item.put("title",article.title);
                item.put("source",article.source);
                item.put("url",article.url);
                json.put(item);
            }catch(Exception ignored){}
        }
        prefs(ctx).edit().putString("saved_articles",json.toString()).apply();
        return !wasSaved;
    }
}