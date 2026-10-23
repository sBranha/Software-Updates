package com.newswave.live;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads point-specific NOAA/NWS weather observations, active alert names, and
 * existing attributed local news. Network requests run only on worker threads.
 * A missing observation is displayed as unavailable rather than fabricated.
 */
final class MyAreaData {
    static final class Snapshot {
        final String area;
        final String condition;
        final String temperature;
        final String feelsLike;
        final String wind;
        final List<String> warnings;
        final List<LocalNewspapers.Headline> headlines;
        final long checkedAt;
        final boolean conditionsAvailable;
        final boolean warningsAvailable;
        Snapshot(String area,String condition,String temperature,String feelsLike,String wind,
                 List<String> warnings,List<LocalNewspapers.Headline> headlines,boolean ok,boolean alertsOk){
            this.area=area;this.condition=condition;this.temperature=temperature;
            this.feelsLike=feelsLike;this.wind=wind;
            this.warnings=warnings;this.headlines=headlines;
            this.checkedAt=System.currentTimeMillis();this.conditionsAvailable=ok;
            this.warningsAvailable=alertsOk;
        }
    }
    private static JSONObject fetch(String url) throws Exception {
        HttpURLConnection conn=null;
        try {
            conn=(HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept","application/geo+json, application/json");
            conn.setRequestProperty("User-Agent","NewsWire (newswire weather / local information app)");
            if(conn.getResponseCode()!=200)throw new java.io.IOException("HTTP "+conn.getResponseCode());
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream stream=conn.getInputStream()){
                byte[] b=new byte[8192];int count;
                while((count=stream.read(b))!=-1){
                    if(bytes.size()+count>1024*1024)throw new java.io.IOException("Response too large");
                    bytes.write(b,0,count);
                }
            }
            return new JSONObject(bytes.toString("UTF-8"));
        }finally{if(conn!=null)conn.disconnect();}
    }
    private static String f(double value){
        return String.format(Locale.US,"%.0f",value);
    }
    static Snapshot load(double lat,double lon,String city,String region){
        String place=city.isEmpty()?"Current location":city+(region.isEmpty()?"":", "+region);
        String temp="—",feel="—",description="Conditions unavailable",wind="—";
        boolean ok=false,alertsOk=false;
        ArrayList<String> warnings=new ArrayList<>();
        List<LocalNewspapers.Headline> headlines=new ArrayList<>();
        String point=String.format(Locale.US,"%.4f,%.4f",lat,lon);
        // Read only active, actual official NWS weather alerts for this location.
        try {
            JSONObject active=fetch("https://api.weather.gov/alerts/active?point="+point);
            JSONArray features=active.optJSONArray("features");
            alertsOk=features!=null;
            if(features!=null)for(int n=0;n<Math.min(12,features.length());n++){
                JSONObject props=features.getJSONObject(n).optJSONObject("properties");
                if(props==null)continue;
                if("Cancel".equalsIgnoreCase(props.optString("messageType","")))continue;
                String title=props.optString("event","").trim();
                if(!title.isEmpty()&&!warnings.contains(title))warnings.add(title);
            }
        }catch(Exception error){android.util.Log.w("NewsWireArea","NWS alerts unavailable",error);}
        try {
            JSONObject points=fetch("https://api.weather.gov/points/"+point);
            JSONObject props=points.getJSONObject("properties");
            String stationUrl=props.optString("observationStations","");
            if(stationUrl.startsWith("https://")){
                JSONObject stationData=fetch(stationUrl);
                JSONArray features=stationData.optJSONArray("features");
                if(features!=null&&features.length()>0){
                    String station=features.getJSONObject(0).getJSONObject("properties").optString("stationIdentifier","");
                    if(!station.isEmpty()){
                        JSONObject latest=fetch("https://api.weather.gov/stations/"+station+"/observations/latest");
                        JSONObject observation=latest.getJSONObject("properties");
                        description=observation.optString("textDescription","Current conditions");
                        if(description.isEmpty())description="Current conditions";
                        double c=observation.optJSONObject("temperature")==null?
                            Double.NaN:observation.getJSONObject("temperature").optDouble("value",Double.NaN);
                        if(!Double.isNaN(c)){temp=f(c*1.8+32)+"°F";ok=true;}
                        JSONObject chill=observation.optJSONObject("heatIndex");
                        double apparent=chill==null?Double.NaN:chill.optDouble("value",Double.NaN);
                        if(Double.isNaN(apparent)){
                            chill=observation.optJSONObject("windChill");
                            apparent=chill==null?Double.NaN:chill.optDouble("value",Double.NaN);
                        }
                        if(!Double.isNaN(apparent))feel=f(apparent*1.8+32)+"°F";
                        JSONObject speed=observation.optJSONObject("windSpeed");
                        double kph=speed==null?Double.NaN:speed.optDouble("value",Double.NaN);
                        if(!Double.isNaN(kph))wind=f(kph*.621371)+" mph";
                    }
                }
            }
        }catch(Exception error){android.util.Log.w("NewsWireArea","NWS conditions unavailable",error);}
        if(!city.isEmpty()&&!region.isEmpty()){
            try{headlines=LocalNewspapers.headlines(city,region);}
            catch(Exception error){android.util.Log.w("NewsWireArea","Local headlines unavailable",error);}
        }
        return new Snapshot(place,description,temp,feel,wind,warnings,headlines,ok,alertsOk);
    }
}
