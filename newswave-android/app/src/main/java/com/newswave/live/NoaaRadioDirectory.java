package com.newswave.live;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Search volunteer-maintained internet rebroadcasts of NOAA Weather Radio.
 * NOAA does not operate an official nationwide web audio stream directory.
 * A radio transmitter's RF signal cannot be received through a phone's
 * ordinary network player; only verified HTTPS audio URLs are selectable.
 */
final class NoaaRadioDirectory {
    private NoaaRadioDirectory(){}
    private static final String[] HOSTS = {
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info"
    };
    private static String encode(String term){
        try{return URLEncoder.encode(term,"UTF-8");}
        catch(Exception error){return term;}
    }
    private static JSONArray query(String name)throws Exception{
        Exception failure=null;
        for(String host:HOSTS){
            HttpURLConnection connection=null;
            try{
                String path="/json/stations/search?countrycode=US&name="+encode(name)
                    +"&hidebroken=true&is_https=true&limit=170";
                connection=(HttpURLConnection)new URL(host+path).openConnection();
                connection.setConnectTimeout(5500);
                connection.setReadTimeout(7500);
                connection.setRequestProperty("Accept","application/json");
                connection.setRequestProperty("User-Agent","NewsWire NOAA Weather Radio/0.6.7");
                if(connection.getResponseCode()!=200)
                    throw new java.io.IOException("NOAA directory HTTP "+connection.getResponseCode());
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=connection.getInputStream()){
                    byte[] b=new byte[8192];int n;
                    while((n=in.read(b))!=-1){
                        if(out.size()+n>2_000_000)throw new java.io.IOException("NOAA directory too large");
                        out.write(b,0,n);
                    }
                }
                return new JSONArray(out.toString("UTF-8"));
            }catch(Exception problem){failure=problem;}
            finally{if(connection!=null)connection.disconnect();}
        }
        throw failure==null?new java.io.IOException("Radio directory unavailable"):failure;
    }
    private static double distanceKm(double lat,double lon,double b,double c){
        double dLat=Math.toRadians(b-lat),dLon=Math.toRadians(c-lon);
        double q=Math.sin(dLat/2)*Math.sin(dLat/2)
            +Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(b))
                *Math.sin(dLon/2)*Math.sin(dLon/2);
        return 6371*2*Math.asin(Math.min(1,Math.sqrt(q)));
    }
    private static void collect(JSONArray entries,List<LocalRadioDirectory.Station> stations,
            Set<String> urls,double lat,double lon,String state){
        String stateName=LocalRadioDirectory.stateName(state);
        for(int i=0;i<entries.length();i++){
            JSONObject o=entries.optJSONObject(i);
            if(o==null || o.optInt("lastcheckok",0)!=1)continue;
            String name=o.optString("name","").trim();
            String test=name.toLowerCase(Locale.US);
            if(!(test.contains("noaa")||test.contains("weather radio")
                  ||test.contains("nwr ")))continue;
            String link=o.optString("url_resolved","").trim();
            if(!link.startsWith("https://"))link=o.optString("url","").trim();
            if(!link.startsWith("https://")||link.length()>1900||name.isEmpty())continue;
            double broadcastLat=o.optDouble("geo_lat",Double.NaN);
            double broadcastLon=o.optDouble("geo_long",Double.NaN);
            boolean hasGeo=WidgetAreaResolver.valid(broadcastLat,broadcastLon);
            boolean hasUserGeo=WidgetAreaResolver.valid(lat,lon);
            double distance=hasGeo&&hasUserGeo?
                distanceKm(lat,lon,broadcastLat,broadcastLon):Double.NaN;
            if(Double.isFinite(distance)&&distance>240)continue;
            String broadcastState=o.optString("state","").trim();
            // No known coordinates: require station to match the selected state.
            // Never claim a stream is nearby based solely on its network hostname.
            if(!Double.isFinite(distance)&&!stateName.isEmpty()&&
               !broadcastState.equalsIgnoreCase(stateName)&&
               !broadcastState.equalsIgnoreCase(state))continue;
            if(!urls.add(link))continue;
            String site=o.optString("homepage","");
            if(!site.startsWith("https://"))site="";
            stations.add(new LocalRadioDirectory.Station(name,link,site,
                "NOAA rebroadcast (third party)",broadcastState,distance));
        }
    }
    static List<LocalRadioDirectory.Station> nearby(double lat,double lon,String state)
        throws Exception {
        ArrayList<LocalRadioDirectory.Station> results=new ArrayList<>();
        HashSet<String> seen=new HashSet<>();
        Exception failure=null;
        for(String name:new String[]{"NOAA","Weather Radio"}){
            try{collect(query(name),results,seen,lat,lon,state);}
            catch(Exception error){failure=error;}
        }
        Collections.sort(results,Comparator.comparingDouble(s->
            Double.isFinite(s.distanceKm)?s.distanceKm:Double.POSITIVE_INFINITY));
        if(results.size()>12)return new ArrayList<>(results.subList(0,12));
        if(results.isEmpty()&&failure!=null)throw failure;
        return results;
    }
}
