package com.newswave.live;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Official NWS hourly point forecast. No forecast is extrapolated or invented. */
final class DayPlannerData {
    static final class Hour {
        final String time,temperature,condition,rain,wind;
        Hour(String time,String temperature,String condition,String rain,String wind){
            this.time=time;this.temperature=temperature;this.condition=condition;
            this.rain=rain;this.wind=wind;
        }
    }
    static final class Forecast {
        final List<Hour> hours;
        final String source;
        final long checkedAt;
        Forecast(List<Hour> hours,String source){
            this.hours=hours;this.source=source;this.checkedAt=System.currentTimeMillis();
        }
    }
    private static JSONObject json(String url) throws Exception {
        if(!url.startsWith("https://api.weather.gov/"))throw new IllegalArgumentException("Unsupported forecast source");
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setRequestProperty("Accept","application/geo+json, application/json");
            connection.setRequestProperty("User-Agent","NewsWire Android/0.5.6 (official NWS hourly forecasts)");
            connection.setConnectTimeout(8000);connection.setReadTimeout(11000);
            if(connection.getResponseCode()!=200)throw new IllegalStateException("NWS HTTP "+connection.getResponseCode());
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){
                    if(out.size()+count>1024*1024)throw new IllegalStateException("NWS response too large");
                    out.write(buffer,0,count);
                }
            }
            return new JSONObject(out.toString("UTF-8"));
        }finally{if(connection!=null)connection.disconnect();}
    }
    static Forecast hourly(double latitude,double longitude)throws Exception{
        if(Double.isNaN(latitude)||Double.isNaN(longitude)||
            latitude<24||latitude>50||longitude< -125||longitude> -66)
            throw new IllegalArgumentException("Hourly NWS forecasts require a continental US location");
        String point=String.format(Locale.US,"%.4f,%.4f",latitude,longitude);
        JSONObject props=json("https://api.weather.gov/points/"+point).getJSONObject("properties");
        String hourly=props.optString("forecastHourly","");
        if(!hourly.startsWith("https://api.weather.gov/"))
            throw new IllegalStateException("Hourly forecast is unavailable for this point");
        JSONObject forecast=json(hourly).getJSONObject("properties");
        JSONArray periods=forecast.optJSONArray("periods");
        if(periods==null)throw new IllegalStateException("NWS returned no hourly forecast periods");
        ArrayList<Hour> result=new ArrayList<>();
        DateTimeFormatter formatter=DateTimeFormatter.ofPattern("h a",Locale.US);
        for(int i=0;i<periods.length()&&result.size()<12;i++){
            JSONObject period=periods.optJSONObject(i);
            if(period==null)continue;
            String start=period.optString("startTime","");
            String time;
            try {
                time=OffsetDateTime.parse(start)
                    .atZoneSameInstant(ZoneId.systemDefault()).format(formatter);
            }catch(Exception ex){continue;}
            String temperature=period.has("temperature")&&!period.isNull("temperature")?
                period.optString("temperature")+"°"+period.optString("temperatureUnit","F"):"—";
            String rain="—";
            JSONObject probability=period.optJSONObject("probabilityOfPrecipitation");
            if(probability!=null&&!probability.isNull("value")){
                int chance=probability.optInt("value",-1);
                if(chance>=0&&chance<=100)rain=chance+"%";
            }
            String condition=period.optString("shortForecast","Forecast unavailable");
            String wind=period.optString("windSpeed","—");
            result.add(new Hour(time,temperature,condition,rain,wind));
        }
        if(result.isEmpty())throw new IllegalStateException("No NWS hourly periods available");
        return new Forecast(result,"National Weather Service forecast for this location");
    }
}