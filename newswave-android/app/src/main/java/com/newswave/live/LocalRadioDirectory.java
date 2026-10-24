package com.newswave.live;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Directory of broadcaster-operated live streams (not a terrestrial AM/FM tuner).
 * Radio Browser is a community catalog; availability can change at any time.
 * Only HTTPS stream URLs are eligible for playback inside NewsWire.
 */
final class LocalRadioDirectory {
    static final class Station {
        final String name,stream,homepage,genre,region;
        final double distanceKm;
        Station(String name,String stream,String homepage,String genre,String region,double distanceKm){
            this.name=name;this.stream=stream;this.homepage=homepage;
            this.genre=genre;this.region=region;this.distanceKm=distanceKm;
        }
        String subtitle(){
            String range=Double.isFinite(distanceKm)?
                String.format(Locale.US,"%.0f mi away",distanceKm*.621371):
                "Regional stream";
            return range+(genre.isEmpty()?"":" · "+genre);
        }
    }
    private static final String[] HOSTS={
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info"
    };
    private static final String STATES=
        "AL:Alabama,AK:Alaska,AZ:Arizona,AR:Arkansas,CA:California,CO:Colorado,CT:Connecticut,"+
        "DE:Delaware,FL:Florida,GA:Georgia,HI:Hawaii,ID:Idaho,IL:Illinois,IN:Indiana,"+
        "IA:Iowa,KS:Kansas,KY:Kentucky,LA:Louisiana,ME:Maine,MD:Maryland,MA:Massachusetts,"+
        "MI:Michigan,MN:Minnesota,MS:Mississippi,MO:Missouri,MT:Montana,NE:Nebraska,NV:Nevada,"+
        "NH:New Hampshire,NJ:New Jersey,NM:New Mexico,NY:New York,NC:North Carolina,"+
        "ND:North Dakota,OH:Ohio,OK:Oklahoma,OR:Oregon,PA:Pennsylvania,RI:Rhode Island,"+
        "SC:South Carolina,SD:South Dakota,TN:Tennessee,TX:Texas,UT:Utah,VT:Vermont,"+
        "VA:Virginia,WA:Washington,WV:West Virginia,WI:Wisconsin,WY:Wyoming,DC:District of Columbia";
    static String stateName(String abbrev){
        if(abbrev==null)return "";
        for(String token:STATES.split(",")){
            int colon=token.indexOf(':');
            if(colon>0&&(abbrev.equalsIgnoreCase(token.substring(0,colon))||
                          abbrev.equalsIgnoreCase(token.substring(colon+1))))
                return token.substring(colon+1);
        }
        return abbrev;
    }
    private static JSONArray fetch(String suffix)throws Exception{
        Exception last=null;
        for(String host:HOSTS){
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(host+suffix).openConnection();
                connection.setConnectTimeout(6500);
                connection.setReadTimeout(9000);
                connection.setRequestProperty("Accept","application/json");
                connection.setRequestProperty("User-Agent","NewsWire Local Radio/0.6.2");
                if(connection.getResponseCode()!=200)throw new java.io.IOException("HTTP "+connection.getResponseCode());
                ByteArrayOutputStream buf=new ByteArrayOutputStream();
                try(InputStream input=connection.getInputStream()){
                    byte[] bytes=new byte[8192];int length;
                    while((length=input.read(bytes))!=-1){
                        if(buf.size()+length>1024*1024)throw new java.io.IOException("Station list too large");
                        buf.write(bytes,0,length);
                    }
                }
                return new JSONArray(buf.toString("UTF-8"));
            }catch(Exception error){last=error;}
            finally{if(connection!=null)connection.disconnect();}
        }
        throw last==null?new java.io.IOException("Radio directory unavailable"):last;
    }
    private static String query(String value){
        try{return URLEncoder.encode(value,"UTF-8");}
        catch(Exception ignored){return "";}
    }
    private static double distance(double lat,double lon,double otherLat,double otherLon){
        double a=Math.toRadians(otherLat-lat),b=Math.toRadians(otherLon-lon);
        double x=Math.sin(a/2)*Math.sin(a/2)+Math.cos(Math.toRadians(lat))*
            Math.cos(Math.toRadians(otherLat))*Math.sin(b/2)*Math.sin(b/2);
        return 6371*2*Math.asin(Math.min(1,Math.sqrt(x)));
    }
    private static void append(JSONArray array,List<Station> out,Set<String> unique,
            double lat,double lon,String localState){
        for(int i=0;i<array.length()&&out.size()<32;i++){
            JSONObject entry=array.optJSONObject(i);
            if(entry==null||entry.optInt("lastcheckok",0)!=1)continue;
            String name=entry.optString("name","").trim();
            String resolved=entry.optString("url_resolved","").trim();
            if(!resolved.startsWith("https://"))resolved=entry.optString("url","").trim();
            if(name.isEmpty()||!resolved.startsWith("https://")||
                !(resolved.startsWith("https://")&&resolved.length()<1900))continue;
            if(!unique.add(resolved))continue;
            double latitude=entry.optDouble("geo_lat",Double.NaN),
                   longitude=entry.optDouble("geo_long",Double.NaN);
            boolean geo=WidgetAreaResolver.valid(latitude,longitude);
            double km=geo&&WidgetAreaResolver.valid(lat,lon)?
                distance(lat,lon,latitude,longitude):Double.NaN;
            // Do not present a far-away network stream as a local broadcaster.
            if(Double.isFinite(km)&&km>160)continue;
            String providerState=entry.optString("state","").trim();
            if(!Double.isFinite(km)&&!localState.isEmpty()&&
                !providerState.equalsIgnoreCase(localState))continue;
            String genre=entry.optString("tags","").trim();
            int comma=genre.indexOf(',');
            if(comma>=0)genre=genre.substring(0,comma);
            if(genre.length()>32)genre=genre.substring(0,32);
            String site=entry.optString("homepage","").trim();
            if(!site.startsWith("https://"))site="";
            out.add(new Station(name,resolved,site,genre,providerState,km));
        }
    }
    static List<Station> nearby(double lat,double lon,String city,String state)throws Exception{
        ArrayList<Station> stations=new ArrayList<>();
        HashSet<String> urls=new HashSet<>();
        String region=stateName(state);
        Exception failure=null;
        if(WidgetAreaResolver.valid(lat,lon)){
            String suffix=String.format(Locale.US,
                "/json/stations/search?geo_lat=%.5f&geo_long=%.5f&geo_distance=160000"+
                "&countrycode=US&hidebroken=true&is_https=true&limit=120",lat,lon);
            try{append(fetch(suffix),stations,urls,lat,lon,region);}
            catch(Exception error){failure=error;}
        }
        // The directory lacks coordinates for some regional stations. Use
        // publisher-listed state only when proximity results are sparse.
        if(stations.size()<8&&!region.isEmpty()){
            try{append(fetch("/json/stations/search?countrycode=US&state="+query(region)+
                    "&hidebroken=true&is_https=true&order=votes&reverse=true&limit=90"),
                stations,urls,lat,lon,region);
            }catch(Exception error){failure=error;}
        }
        stations.sort(Comparator.comparingDouble(s->Double.isFinite(s.distanceKm)?
            s.distanceKm:Double.POSITIVE_INFINITY));
        if(stations.isEmpty()&&failure!=null)throw failure;
        return stations.size()>24?new ArrayList<>(stations.subList(0,24)):stations;
    }
}