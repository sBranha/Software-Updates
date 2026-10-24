package com.newswave.live;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Saved on-device HTTPS radio bookmarks; can be shown without a directory response. */
final class RadioStationFavorites {
    private static final String PREF="newswire-radio-favorites";
    private static final String KEY="stations";
    static List<LocalRadioDirectory.Station> list(Context context){
        ArrayList<LocalRadioDirectory.Station> stations=new ArrayList<>();
        String text=context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]");
        try{
            JSONArray saved=new JSONArray(text);
            for(int i=0;i<saved.length()&&stations.size()<100;i++){
                JSONObject o=saved.optJSONObject(i);
                if(o==null)continue;
                String name=o.optString("name","").trim();
                String url=o.optString("stream","").trim();
                if(name.isEmpty()||!url.startsWith("https://")||url.length()>1900)continue;
                stations.add(new LocalRadioDirectory.Station(name,url,
                    o.optString("homepage",""),o.optString("genre",""),
                    o.optString("region",""),Double.NaN));
            }
        }catch(Exception problem){
            android.util.Log.w("NewsWireRadio","Invalid favorite station data",problem);
        }
        return stations;
    }
    static boolean contains(Context context,String url){
        for(LocalRadioDirectory.Station s:list(context))
            if(s.stream.equals(url))return true;
        return false;
    }
    static boolean toggle(Context context,LocalRadioDirectory.Station station){
        ArrayList<LocalRadioDirectory.Station> saved=new ArrayList<>(list(context));
        boolean existed=false;
        for(int i=saved.size()-1;i>=0;i--){
            if(saved.get(i).stream.equals(station.stream)){
                saved.remove(i);existed=true;
            }
        }
        if(!existed){
            if(saved.size()>=100)saved.remove(0);
            saved.add(station);
        }
        JSONArray items=new JSONArray();
        try{
            for(LocalRadioDirectory.Station s:saved){
                JSONObject o=new JSONObject();
                o.put("name",s.name);o.put("stream",s.stream);
                o.put("homepage",s.homepage);o.put("genre",s.genre);
                o.put("region",s.region);items.put(o);
            }
        }catch(Exception problem){
            android.util.Log.w("NewsWireRadio","Could not store station",problem);
            return !existed;
        }
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString(KEY,items.toString()).apply();
        return !existed;
    }
}