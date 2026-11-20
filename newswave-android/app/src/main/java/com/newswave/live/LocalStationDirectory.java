package com.newswave.live;
import android.content.Context;
import org.json.*;
import java.util.*;

/** Offline, attributed directory: location coordinates never leave the device. */
final class LocalStationDirectory {
    static final class Station {
        final String name,url; final double distance;
        Station(String n,String u,double d){name=n;url=u;distance=d;}
    }
    static List<Station> nearby(Context context,double lat,double lon)throws Exception {
        List<Station> results=new ArrayList<>();
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return results;
        String json;
        try(java.io.InputStream in=context.getAssets().open("tv_stations.json")){
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] block=new byte[8192];int count;while((count=in.read(block))!=-1)bytes.write(block,0,count);
            json=bytes.toString("UTF-8");
        }
        JSONArray rows=new JSONObject(json).getJSONArray("stations");
        Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            double distance=LocationMath.miles(lat,lon,row.getDouble("latitude"),row.getDouble("longitude"));
            String name=row.getString("name"),url=row.getString("url");
            if(distance<=100&&ArticleReaderActivity.isWebUrl(url)&&seen.add(name))results.add(new Station(name,url,distance));
        }
        results.sort(Comparator.comparingDouble(s->s.distance));
        return results.subList(0,Math.min(12,results.size()));
    }
}
