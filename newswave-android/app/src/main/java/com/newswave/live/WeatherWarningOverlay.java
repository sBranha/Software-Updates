package com.newswave.live;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** NOAA/NWS active alert polygons drawn on the car's existing (non-WebView) radar surface. */
final class WeatherWarningOverlay {
    private static final String LOG = "NewsWireWarnings";
    private static final String ENDPOINT = "https://api.weather.gov/alerts/active?status=actual";
    static final long REFRESH_MS = 5 * 60 * 1000L;
    static final long STALE_MS = 15 * 60 * 1000L;

    static final class Area {
        final String event;
        final int outline;
        final List<double[][]> rings;
        Area(String event, int outline, List<double[][]> rings) {
            this.event=event; this.outline=outline; this.rings=rings;
        }
    }
    static final class Snapshot {
        final List<Area> areas;
        final long updatedAt;
        final String error;
        Snapshot(List<Area> areas, long updatedAt, String error) {
            this.areas=areas; this.updatedAt=updatedAt; this.error=error;
        }
        boolean fresh() { return System.currentTimeMillis()-updatedAt < STALE_MS; }
    }

    private WeatherWarningOverlay(){}

    private static int colorFor(String event) {
        switch(event) {
            case "Tornado Warning": return Color.rgb(255,38,54);
            case "Severe Thunderstorm Warning": return Color.rgb(255,174,20);
            case "Flash Flood Warning": return Color.rgb(40,208,238);
            case "Flood Warning": return Color.rgb(38,140,230);
            case "Extreme Wind Warning": return Color.rgb(238,78,226);
            case "Winter Storm Warning": return Color.rgb(132,180,255);
            case "Snow Squall Warning": return Color.rgb(160,225,255);
            case "Tornado Watch": return Color.rgb(254,85,123);
            case "Severe Thunderstorm Watch": return Color.rgb(246,223,54);
            default: return Color.TRANSPARENT;
        }
    }

    static Snapshot fetch(Snapshot previous) {
        HttpURLConnection conn=null;
        try {
            conn=(HttpURLConnection)new URL(ENDPOINT).openConnection();
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(17000);
            conn.setRequestProperty("User-Agent","NewsWireWeather/0.4.8 (https://github.com/sBranha/Software-Updates)");
            conn.setRequestProperty("Accept","application/geo+json");
            if(conn.getResponseCode()!=200)throw new IllegalStateException("NWS HTTP "+conn.getResponseCode());
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(InputStream input=conn.getInputStream()){
                byte[] chunk=new byte[8192];int read;
                while((read=input.read(chunk))!=-1){
                    if(buffer.size()+read > 12*1024*1024)throw new IllegalStateException("NWS data too large");
                    buffer.write(chunk,0,read);
                }
            }
            JSONArray features=new JSONObject(buffer.toString("UTF-8")).getJSONArray("features");
            ArrayList<Area> areas=new ArrayList<>();
            long now=System.currentTimeMillis();
            for(int i=0;i<features.length() && areas.size()<500;i++){
                JSONObject feature=features.optJSONObject(i);
                if(feature==null)continue;
                JSONObject props=feature.optJSONObject("properties");
                JSONObject geometry=feature.optJSONObject("geometry");
                if(props==null||geometry==null)continue;
                String event=props.optString("event","");
                int outline=colorFor(event);
                if(outline==Color.TRANSPARENT)continue;
                String status=props.optString("status","Actual");
                if(!"Actual".equalsIgnoreCase(status))continue;
                if("Cancel".equalsIgnoreCase(props.optString("messageType","")))continue;
                String expiry=props.optString("expires","");
                if(!expiry.isEmpty() && !"null".equals(expiry)){
                    try{if(Instant.parse(expiry).toEpochMilli()<now)continue;}catch(Exception ignored){}
                }
                List<double[][]> rings=rings(geometry);
                if(!rings.isEmpty())areas.add(new Area(event,outline,rings));
            }
            return new Snapshot(Collections.unmodifiableList(areas),now,"");
        } catch(Exception ex) {
            Log.w(LOG,"Could not refresh NWS warning polygons",ex);
            return new Snapshot(previous==null?Collections.emptyList():previous.areas,
                previous==null?0:previous.updatedAt,"Warnings temporarily unavailable");
        } finally {
            if(conn!=null)conn.disconnect();
        }
    }

    static List<double[][]> rings(JSONObject geometry) {
        ArrayList<double[][]> result=new ArrayList<>();
        try{
            String type=geometry.optString("type","");
            JSONArray coordinates=geometry.optJSONArray("coordinates");
            if(coordinates==null)return result;
            if("Polygon".equals(type)){
                addExterior(result,coordinates);
            }else if("MultiPolygon".equals(type)){
                for(int i=0;i<coordinates.length()&&i<35;i++)addExterior(result,coordinates.getJSONArray(i));
            }
        }catch(Exception ex){Log.w(LOG,"Invalid NWS warning geometry",ex);}
        return result;
    }
    private static void addExterior(List<double[][]> result,JSONArray polygon) throws Exception{
        if(polygon.length()==0)return;
        JSONArray exterior=polygon.getJSONArray(0);
        if(exterior.length()<3||exterior.length()>2500)return;
        double[][] ring=new double[exterior.length()][2];
        for(int j=0;j<exterior.length();j++){
            JSONArray pair=exterior.getJSONArray(j);
            ring[j][0]=pair.getDouble(0); // longitude
            ring[j][1]=pair.getDouble(1); // latitude
        }
        result.add(ring);
    }

    /** Draw polygons from GeoJSON [longitude,latitude] onto the exact map projection. */
    static int draw(Canvas canvas, Snapshot snap, int z, double worldLeft, double worldTop, int width, int height){
        if(snap==null||!snap.fresh())return -1;
        Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG), stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(3.5f);
        int visibleCount=0;
        for(Area area:snap.areas){
            boolean visible=false;
            for(double[][] ring:area.rings){
                Path outline=new Path();
                float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
                for(int i=0;i<ring.length;i++){
                    double latitude=Math.max(-85,Math.min(85,ring[i][1]));
                    double phi=Math.toRadians(latitude);
                    float x=(float)((ring[i][0]+180.)/360.*256*(1<<z)-worldLeft);
                    float y=(float)((1-Math.log(Math.tan(phi)+1/Math.cos(phi))/Math.PI)/2*256*(1<<z)-worldTop);
                    if(i==0)outline.moveTo(x,y);else outline.lineTo(x,y);
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                    minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                }
                if(maxX<0||minX>width||maxY<0||minY>height)continue;
                visible=true;
                outline.close();
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(Color.argb(42,Color.red(area.outline),Color.green(area.outline),Color.blue(area.outline)));
                stroke.setColor(area.outline);
                canvas.drawPath(outline,fill);
                canvas.drawPath(outline,stroke);
            }
            if(visible)visibleCount++;
        }
        return visibleCount;
    }
}
