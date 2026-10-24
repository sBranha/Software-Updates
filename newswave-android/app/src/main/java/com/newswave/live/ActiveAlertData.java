package com.newswave.live;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Official National Weather Service active alerts. Do not interpret
 * provider/network failures as an all-clear. Geometry can be null for alerts
 * targeting forecast zones, so the text list is never inferred from map pins.
 */
final class ActiveAlertData {
    static final class Item {
        final String id,event,severity,headline,area,description,instruction,issued,expires,url;
        final JSONObject geometry;
        Item(String id,String event,String severity,String headline,String area,String description,
             String instruction,String issued,String expires,String url,JSONObject geometry){
            this.id=id;this.event=event;this.severity=severity;this.headline=headline;
            this.area=area;this.description=description;this.instruction=instruction;
            this.issued=issued;this.expires=expires;this.url=url;this.geometry=geometry;
        }
    }
    static final class Result {
        final List<Item> items;
        final long fetchedAt;
        Result(List<Item> items){this.items=items;this.fetchedAt=System.currentTimeMillis();}
        String geoJson(){
            JSONObject result=new JSONObject();
            JSONArray features=new JSONArray();
            try{
                result.put("type","FeatureCollection");
                for(Item warning:items){
                    if(warning.geometry==null)continue;
                    JSONObject f=new JSONObject(),p=new JSONObject();
                    p.put("event",warning.event);
                    p.put("area",warning.area);
                    p.put("severity",warning.severity);
                    p.put("expires",warning.expires);
                    p.put("id",warning.id);
                    f.put("type","Feature");f.put("geometry",warning.geometry);
                    f.put("properties",p);features.put(f);
                }
                result.put("features",features);
            }catch(Exception ignored){}
            return result.toString();
        }
    }
    private static JSONObject fetch(String address)throws Exception {
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(address).openConnection();
            c.setConnectTimeout(7500);c.setReadTimeout(10000);
            c.setRequestProperty("Accept","application/geo+json, application/json");
            c.setRequestProperty("User-Agent","NewsWire/0.5.7 (NWS active alerts mobile viewer)");
            if(c.getResponseCode()!=200)throw new java.io.IOException("NWS HTTP "+c.getResponseCode());
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] block=new byte[8192];int len;
                while((len=in.read(block))!=-1){
                    if(output.size()+len>3*1024*1024)
                        throw new java.io.IOException("NWS response too large");
                    output.write(block,0,len);
                }
            }
            return new JSONObject(output.toString("UTF-8"));
        }finally{if(c!=null)c.disconnect();}
    }
    static String stateCode(String state){
        if(state==null)return "";
        switch(state.toLowerCase(Locale.US).trim()){
            case "al":case "alabama":return "AL";
            case "fl":case "florida":return "FL";
            case "ga":case "georgia":return "GA";
            case "tn":case "tennessee":return "TN";
            case "ms":case "mississippi":return "MS";
            case "nc":case "north carolina":return "NC";
            case "sc":case "south carolina":return "SC";
            case "ky":case "kentucky":return "KY";
            case "tx":case "texas":return "TX";
            case "ca":case "california":return "CA";
            case "ny":case "new york":return "NY";
            case "va":case "virginia":return "VA";
            default:return state.length()==2?state.toUpperCase(Locale.US):"";
        }
    }
    static Result load(String region,double lat,double lon)throws Exception {
        String code=stateCode(region);
        String request;
        if(!code.isEmpty())request="https://api.weather.gov/alerts/active?area="+code;
        else if(!Double.isNaN(lat)&&!Double.isNaN(lon))
            request=String.format(Locale.US,
                "https://api.weather.gov/alerts/active?point=%.4f,%.4f",lat,lon);
        else throw new IllegalArgumentException("Set ZIP or allow location to retrieve warnings");
        JSONObject data=fetch(request);
        JSONArray features=data.optJSONArray("features");
        if(features==null)throw new IllegalStateException("NWS response missing features");
        ArrayList<Item> items=new ArrayList<>();
        for(int i=0;i<features.length()&&items.size()<100;i++){
            JSONObject feature=features.optJSONObject(i);
            if(feature==null)continue;
            JSONObject p=feature.optJSONObject("properties");
            if(p==null)continue;
            if("Cancel".equalsIgnoreCase(p.optString("messageType","")))continue;
            if(!"Actual".equalsIgnoreCase(p.optString("status","Actual")))continue;
            String event=p.optString("event","Weather alert");
            if(event.isEmpty())event="Weather alert";
            String id=feature.optString("id",p.optString("id",""));
            JSONObject g=feature.optJSONObject("geometry");
            items.add(new Item(id,event,p.optString("severity","Unknown"),
                p.optString("headline",event),p.optString("areaDesc","Area unspecified"),
                p.optString("description",""),p.optString("instruction",""),
                p.optString("sent",""),p.optString("expires",""),
                p.optString("@id",id),g));
        }
        Collections.sort(items,(x,y)->Integer.compare(rank(x),rank(y)));
        return new Result(items);
    }
    static int rank(Item i){
        if("Tornado Warning".equals(i.event))return 0;
        if("Flash Flood Warning".equals(i.event))return 1;
        if("Severe Thunderstorm Warning".equals(i.event))return 2;
        if("Extreme".equalsIgnoreCase(i.severity))return 3;
        if("Severe".equalsIgnoreCase(i.severity))return 4;
        return 5;
    }
    static String time(String raw){
        if(raw==null||raw.isEmpty()||"null".equals(raw))return "Not stated";
        try{
            java.time.OffsetDateTime parsed=java.time.OffsetDateTime.parse(raw);
            return parsed.atZoneSameInstant(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a",Locale.US));
        }catch(Exception error){return raw;}
    }
}
