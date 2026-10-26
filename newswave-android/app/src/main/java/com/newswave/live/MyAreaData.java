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
        final long observationAt;
        final boolean conditionsAvailable;
        final boolean warningsAvailable;
        Snapshot(String area,String condition,String temperature,String feelsLike,String wind,
                 List<String> warnings,List<LocalNewspapers.Headline> headlines,boolean ok,boolean alertsOk,long observedAt){
            this.area=area;this.condition=condition;this.temperature=temperature;
            this.feelsLike=feelsLike;this.wind=wind;
            this.warnings=warnings;this.headlines=headlines;
            this.checkedAt=System.currentTimeMillis();this.observationAt=observedAt;
            this.conditionsAvailable=ok;
            this.warningsAvailable=alertsOk;
        }
    }
    private static JSONObject fetch(String url) throws Exception {
        HttpURLConnection conn=null;
        try {
            conn=(HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(7000);
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
        long observedAt=0;
        ArrayList<String> warnings=new ArrayList<>();
        List<LocalNewspapers.Headline> headlines=new ArrayList<>();
        String point=String.format(Locale.US,"%.4f,%.4f",lat,lon);
        // Read only active, actual official NWS weather alerts for this location.
        try {
            JSONObject active=fetch("https://api.weather.gov/alerts/active?point="+point);
            JSONArray features=active.optJSONArray("features");
            alertsOk=features!=null;
            if(features!=null)for(int n=0;n<Math.min(80,features.length());n++){
                JSONObject props=features.optJSONObject(n)==null?null:
                    features.optJSONObject(n).optJSONObject("properties");
                if(props==null)continue;
                String type=props.optString("messageType","");
                if("Cancel".equalsIgnoreCase(type)||"Test".equalsIgnoreCase(type)||
                    "Exercise".equalsIgnoreCase(type))continue;
                if(!"Actual".equalsIgnoreCase(props.optString("status","Actual")))continue;
                String title=props.optString("event","").trim();
                if(!title.isEmpty()&&!warnings.contains(title))warnings.add(title);
            }
            // Put life-threatening warnings first in compact phone views.
            java.util.Collections.sort(warnings,(a,b)->{
                int priorityA=a.equalsIgnoreCase("Tornado Warning")?0:
                    a.equalsIgnoreCase("Flash Flood Warning")?1:
                    a.equalsIgnoreCase("Severe Thunderstorm Warning")?2:3;
                int priorityB=b.equalsIgnoreCase("Tornado Warning")?0:
                    b.equalsIgnoreCase("Flash Flood Warning")?1:
                    b.equalsIgnoreCase("Severe Thunderstorm Warning")?2:3;
                return Integer.compare(priorityA,priorityB);
            });
        }catch(Exception error){android.util.Log.w("NewsWireArea","NWS alerts unavailable",error);}
        // Nearby station timestamps may be stale or ahead of the phone's clock.
        // Try up to five official station observations before a labeled fallback.
        boolean stationOutdated=false;
        String hourlyForecastUrl="";
        try {
            JSONObject points=fetch("https://api.weather.gov/points/"+point);
            JSONObject props=points.getJSONObject("properties");
            hourlyForecastUrl=props.optString("forecastHourly","");
            String stationUrl=props.optString("observationStations","");
            if(stationUrl.startsWith("https://api.weather.gov/")){
                JSONObject stationData=fetch(stationUrl);
                JSONArray features=stationData.optJSONArray("features");
                if(features!=null)for(int n=0;n<Math.min(5,features.length());n++){
                    JSONObject feature=features.optJSONObject(n);
                    JSONObject stationProps=feature==null?null:feature.optJSONObject("properties");
                    String station=stationProps==null?"":stationProps.optString("stationIdentifier","");
                    if(station.isEmpty())continue;
                    try {
                        JSONObject latest=fetch("https://api.weather.gov/stations/"+station+"/observations/latest");
                        JSONObject observation=latest.getJSONObject("properties");
                        long candidateAt=0;
                        try{
                            candidateAt=java.time.OffsetDateTime.parse(
                                observation.optString("timestamp","")).toInstant().toEpochMilli();
                        }catch(Exception ignored){stationOutdated=true;continue;}
                        long age=System.currentTimeMillis()-candidateAt;
                        if(candidateAt<=0||age< -5*60*1000L||age>2L*60*60*1000L){
                            stationOutdated=true;
                            continue;
                        }
                        JSONObject value=observation.optJSONObject("temperature");
                        double c=value==null?Double.NaN:value.optDouble("value",Double.NaN);
                        if(!Double.isFinite(c))continue;
                        observedAt=candidateAt;
                        temp=f(c*1.8+32)+"°F";
                        description=observation.optString("textDescription","Current conditions");
                        if(description.isEmpty())description="Current conditions";
                        JSONObject apparentValue=observation.optJSONObject("heatIndex");
                        double apparent=apparentValue==null?Double.NaN:
                            apparentValue.optDouble("value",Double.NaN);
                        if(!Double.isFinite(apparent)){
                            apparentValue=observation.optJSONObject("windChill");
                            apparent=apparentValue==null?Double.NaN:
                                apparentValue.optDouble("value",Double.NaN);
                        }
                        if(Double.isFinite(apparent))feel=f(apparent*1.8+32)+"°F";
                        JSONObject speed=observation.optJSONObject("windSpeed");
                        double kph=speed==null?Double.NaN:speed.optDouble("value",Double.NaN);
                        if(Double.isFinite(kph))wind=f(kph*.621371)+" mph";
                        ok=true;
                        break;
                    }catch(Exception stationError){
                        android.util.Log.w("NewsWireArea","Skipping unavailable NWS station",stationError);
                    }
                }
            }
        }catch(Exception error){android.util.Log.w("NewsWireArea","NWS conditions unavailable",error);}
        // Official NWS forecast: an estimate, not a measured station reading.
        if(!ok && hourlyForecastUrl.startsWith("https://api.weather.gov/")){
            try {
                JSONObject hourly=fetch(hourlyForecastUrl);
                JSONObject forecastProps=hourly.optJSONObject("properties");
                JSONArray periods=forecastProps==null?null:forecastProps.optJSONArray("periods");
                if(periods!=null&&periods.length()>0){
                    JSONObject period=periods.optJSONObject(0);
                    if(period!=null&&period.has("temperature")&&!period.isNull("temperature")){
                        int degrees=period.optInt("temperature",Integer.MIN_VALUE);
                        if(degrees> -150&&degrees<160){
                            temp=f("C".equalsIgnoreCase(period.optString("temperatureUnit","F"))?
                                degrees*1.8+32:degrees)+"°F";
                            description="NWS hourly forecast estimate · "+
                                period.optString("shortForecast","Local conditions");
                            wind=period.optString("windSpeed","—");
                            observedAt=0;
                            ok=true;
                        }
                    }
                }
            }catch(Exception forecastError){
                android.util.Log.w("NewsWireArea","Hourly forecast unavailable",forecastError);
            }
        }
        // Backup is modeled weather, explicitly labeled rather than called a station reading.
        if(!ok){
            try{
                String url=String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"+
                    "&current=temperature_2m,wind_speed_10m&temperature_unit=fahrenheit"+
                    "&wind_speed_unit=mph",lat,lon);
                JSONObject current=fetch(url).optJSONObject("current");
                double degrees=current==null?Double.NaN:
                    current.optDouble("temperature_2m",Double.NaN);
                if(Double.isFinite(degrees)){
                    temp=f(degrees)+"°F";
                    double mph=current.optDouble("wind_speed_10m",Double.NaN);
                    if(Double.isFinite(mph))wind=f(mph)+" mph";
                    description="Open-Meteo temperature estimate · station unavailable";
                    observedAt=0;
                    ok=true;
                }
            }catch(Exception backupError){
                android.util.Log.w("NewsWireArea","Backup temperature unavailable",backupError);
            }
        }
        if(!ok && stationOutdated){
            description="Weather station reading outdated";
            temp="—";feel="—";wind="—";observedAt=0;
        }
        if(!city.isEmpty()&&!region.isEmpty()){
            try{headlines=LocalNewspapers.headlines(city,region);}
            catch(Exception error){android.util.Log.w("NewsWireArea","Local headlines unavailable",error);}
        }
        return new Snapshot(place,description,temp,feel,wind,warnings,headlines,ok,alertsOk,observedAt);
    }
}
