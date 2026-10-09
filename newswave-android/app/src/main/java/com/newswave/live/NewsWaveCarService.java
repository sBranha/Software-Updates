package com.newswave.live;

import android.Manifest;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.MapWithContentTemplate;
import androidx.car.app.validation.HostValidator;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android Auto WEATHER app service. No video, animated radar loops, or browser UI on the car.
 * NOAA data remains freely accessible. A distinct Android Auto host supplies the safe controls.
 */
public final class NewsWaveCarService extends CarAppService {
    private static final String LOG="NewsWaveAuto";

    @NonNull @Override public HostValidator createHostValidator() {
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
        return new HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build();
    }
    @NonNull @Override public Session onCreateSession() { return new RadarSession(); }

    private final class RadarSession extends Session {
        @NonNull @Override public Screen onCreateScreen(@NonNull Intent intent) {
            return new RadarScreen(getCarContext());
        }
    }
    private static final class Station {
        final String id, name;
        final double lat, lon;
        Station(String i,String n,double a,double o){id=i;name=n;lat=a;lon=o;}
    }

    private static List<Station> readStations(Context context) {
        ArrayList<Station> found = new ArrayList<>();
        try (InputStream is=context.getAssets().open("stations.js")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] b=new byte[8192]; int n;
            while((n=is.read(b))!=-1)out.write(b,0,n);
            String js=new String(out.toByteArray(),StandardCharsets.UTF_8);
            int first=js.indexOf('['),last=js.lastIndexOf(']');
            JSONArray arr=new JSONArray(js.substring(first,last+1));
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.getJSONObject(i);
                String id=o.getString("id").toUpperCase(Locale.US);
                if(!id.matches("[A-Z0-9]{4}"))continue;
                found.add(new Station(id,o.optString("name",id),o.getDouble("lat"),o.getDouble("lon")));
            }
        }catch(Exception e){Log.w(LOG,"NOAA station catalog missing",e);}
        if(found.isEmpty())found.add(new Station("KHTX","Huntsville / Hytop, Alabama",34.93056,-86.08361));
        return found;
    }

    private static double sqDist(Station s,double lat,double lon){
        double dlat=(s.lat-lat), dlon=(s.lon-lon)*Math.cos(Math.toRadians(lat));
        return dlat*dlat+dlon*dlon;
    }
    private static Location recentLocation(Context context){
        if(Build.VERSION.SDK_INT>=23
                &&context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                &&context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null;
        try {
            LocationManager manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            Location best=null;
            for(String provider:manager.getProviders(true)){
                Location candidate=manager.getLastKnownLocation(provider);
                if(candidate!=null && (best==null || candidate.getTime()>best.getTime()))best=candidate;
            }
            if(best!=null && System.currentTimeMillis()-best.getTime()<30*60*1000L)return best;
        }catch(Exception e){Log.w(LOG,"Location not available",e);}
        return null;
    }

    private static final class RadarScreen extends Screen {
        private final List<Station> stations;
        private Station chosen;
        private String mode="SR_BREF";
        private boolean nationwide=false;
        private final RadarRenderer renderer=new RadarRenderer();
        RadarScreen(CarContext context){
            super(context);
            stations=readStations(context);
            String old=context.getSharedPreferences("newswave-car",Context.MODE_PRIVATE).getString("station","KHTX");
            chosen=find(old);
            try{context.getCarService(AppManager.class).setSurfaceCallback(renderer);}catch(Exception e){Log.w(LOG,"No map surface yet",e);}
            renderer.change(chosen,mode,nationwide);
        }
        private Station find(String id){
            for(Station s:stations)if(s.id.equals(id))return s;
            return stations.get(0);
        }
        void choose(Station s){
            chosen=s;nationwide=false;
            getCarContext().getSharedPreferences("newswave-car",Context.MODE_PRIVATE).edit().putString("station",s.id).apply();
            renderer.change(chosen,mode,nationwide);invalidate();
        }
        private void changeMode(){
            if("SR_BREF".equals(mode))mode="SR_BVEL";
            else if("SR_BVEL".equals(mode))mode="BDHC";
            else if("BDHC".equals(mode))mode="BOHA";
            else mode="SR_BREF";
            renderer.change(chosen,mode,nationwide);invalidate();
        }
        private String productName(){
            switch(mode){
                case "SR_BVEL":return "Radial wind velocity";
                case "BDHC":return "Precipitation type";
                case "BOHA":return "1-hour rainfall";
                default:return "Reflectivity";
            }
        }
        @NonNull @Override public Template onGetTemplate() {
            Pane pane=new Pane.Builder()
                .addRow(new Row.Builder()
                    .setTitle("Radar: "+chosen.id)
                    .addText(chosen.name)
                    .setOnClickListener(()->getScreenManager().push(new StationScreen(getCarContext(),this,stations,0)))
                    .build())
                .addRow(new Row.Builder()
                    .setTitle("Layer: "+productName())
                    .addText("Tap to change the radar layer")
                    .setOnClickListener(this::changeMode)
                    .build())
                .addRow(new Row.Builder()
                    .setTitle(nationwide?"Nationwide view":"Local radar view")
                    .addText("Tap to switch map coverage")
                    .setOnClickListener(()->{nationwide=!nationwide;renderer.change(chosen,mode,nationwide);invalidate();})
                    .build())
                .addRow(new Row.Builder()
                    .setTitle("Update latest scan")
                    .addText("NOAA / National Weather Service")
                    .setOnClickListener(()->{renderer.refresh();invalidate();})
                    .build())
                .build();
            PaneTemplate panel=new PaneTemplate.Builder(pane)
                .setHeader(new Header.Builder().setTitle("NewsWave Weather Radar")
                    .setStartHeaderAction(Action.APP_ICON).build()).build();
            return new MapWithContentTemplate.Builder().setContentTemplate(panel).build();
        }
    }

    private static final class StationScreen extends Screen {
        private final RadarScreen parent;
        private final List<Station> ordered;
        private final int page;
        StationScreen(CarContext c,RadarScreen owner,List<Station> all,int offset){
            super(c);parent=owner;page=offset;
            ordered=new ArrayList<>(all);
            Location loc=recentLocation(c);
            double lat=loc==null?34.93:loc.getLatitude(),lon=loc==null?-86.08:loc.getLongitude();
            // Closest first; convenient while traveling. Does not request permission on car.
            Collections.sort(ordered,Comparator.comparingDouble(s->sqDist(s,lat,lon)));
        }
        @NonNull @Override public Template onGetTemplate(){
            ItemList.Builder items=new ItemList.Builder();
            int end=Math.min(ordered.size(),page+6);
            for(int k=page;k<end;k++){
                Station s=ordered.get(k);
                items.addItem(new Row.Builder().setTitle(s.id+" · "+s.name)
                    .addText("NOAA Doppler radar")
                    .setOnClickListener(()->{parent.choose(s);getScreenManager().pop();}).build());
            }
            if(end<ordered.size()){
                items.addItem(new Row.Builder().setTitle("More radar stations  →")
                    .addText("Stations "+(end+1)+" – "+Math.min(end+6,ordered.size()))
                    .setOnClickListener(()->getScreenManager().push(new StationScreen(getCarContext(),parent,ordered,end))).build());
            }
            return new ListTemplate.Builder().setSingleList(items.build())
              .setHeader(new Header.Builder().setTitle("Choose radar station")
                 .setStartHeaderAction(Action.BACK).build()).build();
        }
    }

    private static final class RadarRenderer implements SurfaceCallback {
        private static final String USER_AGENT="NewsWaveLive/0.3 (https://github.com/sBranha/Software-Updates)";
        private final ExecutorService executor=Executors.newSingleThreadExecutor();
        private final Map<String,Bitmap> cache=new LinkedHashMap<String,Bitmap>(80,.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> eldest){return size()>80;}
        };
        private Surface surface;
        private int width=1024,height=600,revision=0;
        private Station station=new Station("KHTX","Huntsville, AL",34.93056,-86.08361);
        private String product="SR_BREF";private boolean usa=false;
        private Bitmap finished;
        private final Object lock=new Object();

        void change(Station s,String p,boolean national){
            synchronized(lock){station=s;product=p;usa=national;revision++;}
            refresh();
        }
        void refresh(){
            final Station s; final String p;final boolean national;final int rev,w,h;
            synchronized(lock){s=station;p=product;national=usa;rev=++revision;w=width;h=height;}
            executor.execute(()->render(s,p,national,rev,Math.max(480,w),Math.max(320,h)));
        }
        @Override public void onSurfaceAvailable(@NonNull SurfaceContainer c){
            synchronized(lock){surface=c.getSurface();width=c.getWidth();height=c.getHeight();}
            display();refresh();
        }
        @Override public void onSurfaceDestroyed(@NonNull SurfaceContainer c){
            synchronized(lock){surface=null;}
            // The Surface belongs to the car host. Do not retain it after destruction.
        }
        @Override public void onVisibleAreaChanged(@NonNull Rect area) {}
        @Override public void onStableAreaChanged(@NonNull Rect area) {}
        private static double mx(double lon,int z){return (lon+180.)/360.*256*(1<<z);}
        private static double my(double lat,int z){
            double r=Math.toRadians(Math.max(-85.,Math.min(85.,lat)));
            return (1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2*256*(1<<z);
        }
        private static double lon(double x,int z){return x/(256*(1<<z))*360-180;}
        private static double lat(double y,int z){
            return Math.toDegrees(Math.atan(Math.sinh(Math.PI*(1-2*y/(256*(1<<z))))));
        }
        private Bitmap download(String address){
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(address).openConnection();
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(13000);
                connection.setRequestProperty("User-Agent",USER_AGENT);
                connection.setRequestProperty("Accept","image/png,image/*;q=0.8");
                if(connection.getResponseCode()!=200)return null;
                try(InputStream stream=connection.getInputStream()){return BitmapFactory.decodeStream(stream);}
            }catch(Exception e){Log.w(LOG,"Weather tile unavailable: "+e.getMessage());return null;}
            finally{if(connection!=null)connection.disconnect();}
        }
        private Bitmap tile(int x,int y,int z){
            String key=z+"/"+x+"/"+y;
            synchronized(cache){Bitmap cached=cache.get(key);if(cached!=null&&!cached.isRecycled())return cached;}
            if(x<0||y<0||y>=(1<<z)||x>=(1<<z))return null;
            Bitmap b=download("https://tile.openstreetmap.org/"+key+".png");
            if(b!=null){synchronized(cache){cache.put(key,b);}}
            return b;
        }
        private void render(Station s,String p,boolean national,int rev,int w,int h){
            try{
                int z=national?3:7;
                double centerLat=national?39.0:s.lat,centerLon=national?-98.0:s.lon;
                double x0=mx(centerLon,z)-w/2.,y0=my(centerLat,z)-h/2.;
                Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(out);
                canvas.drawColor(Color.rgb(13,26,39));
                Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                int tx0=(int)Math.floor(x0/256),tx1=(int)Math.ceil((x0+w)/256);
                int ty0=(int)Math.floor(y0/256),ty1=(int)Math.ceil((y0+h)/256);
                for(int ty=ty0;ty<=ty1;ty++){
                    for(int tx=tx0;tx<=tx1;tx++){
                        Bitmap t=tile(tx,ty,z);
                        if(t!=null)canvas.drawBitmap(t, (float)(tx*256-x0),(float)(ty*256-y0),brush);
                    }
                }
                // WMS request overlays the matching NOAA radar image, not a browser website.
                double west=lon(x0,z),east=lon(x0+w,z),north=lat(y0,z),south=lat(y0+h,z);
                String bbox=String.format(Locale.US,"%.6f,%.6f,%.6f,%.6f",west,south,east,north);
                String url;
                if(national){
                    url="https://mapservices.weather.noaa.gov/eventdriven/rest/services/radar/radar_base_reflectivity/MapServer/export"
                       +"?bbox="+bbox+"&bboxSR=4326&imageSR=4326&size="+w+","+h
                       +"&format=png32&transparent=true&layers=show%3A0&f=image";
                }else{
                    String id=s.id.toLowerCase(Locale.US);
                    url="https://opengeo.ncep.noaa.gov/geoserver/"+id+"/ows?service=WMS"
                       +"&version=1.1.1&request=GetMap&layers="+id+"_"+p.toLowerCase(Locale.US)
                       +"&styles=&srs=EPSG%3A4326&bbox="+bbox
                       +"&width="+w+"&height="+h+"&format=image%2Fpng&transparent=true";
                }
                Bitmap radar=download(url);
                if(radar!=null)canvas.drawBitmap(radar,null,new Rect(0,0,w,h),brush);
                if(!national){
                    // Station center marker, white outlined radar reference point.
                    Paint pin=new Paint(Paint.ANTI_ALIAS_FLAG);
                    pin.setColor(Color.WHITE);pin.setStyle(Paint.Style.STROKE);pin.setStrokeWidth(2.5f);
                    canvas.drawCircle(w/2f,h/2f,9,pin);
                    canvas.drawLine(w/2f-15,h/2f,w/2f+15,h/2f,pin);
                    canvas.drawLine(w/2f,h/2f-15,w/2f,h/2f+15,pin);
                }
                // Basic product ID and attribution stay readable independent of map zoom.
                brush.setColor(0xe20b1727);canvas.drawRoundRect(12,12,Math.min(w-12,600),70,14,14,brush);
                brush.setColor(Color.WHITE);brush.setTextSize(20f);brush.setFakeBoldText(true);
                String label=national?"US RADAR · Reflectivity":s.id+" · "+(p.equals("SR_BVEL")?"Radial velocity":p.equals("BDHC")?"Precipitation type":p.equals("BOHA")?"Hourly rainfall":"Reflectivity");
                canvas.drawText(label,26,40,brush);
                brush.setFakeBoldText(false);brush.setTextSize(13);
                canvas.drawText(radar==null?"Radar overlay unavailable — map only":"NOAA latest available scan · static driving view",26,60,brush);
                brush.setColor(0xea09111b);canvas.drawRoundRect(8,h-31,Math.min(w-8,440),h-5,5,5,brush);
                brush.setColor(Color.WHITE);brush.setTextSize(12);
                canvas.drawText("Map © OpenStreetMap contributors  ·  Radar NOAA/NWS",14,h-13,brush);
                synchronized(lock){if(rev!=revision){out.recycle();return;}finished=out;}
                display();
            }catch(Throwable e){Log.e(LOG,"Radar rendering failed",e);}
        }
        private void display(){
            Bitmap bitmap;Surface sf;
            synchronized(lock){sf=surface;bitmap=finished;}
            if(sf==null||!sf.isValid())return;
            Canvas c=null;
            try{
                c=sf.lockCanvas(null);
                c.drawColor(Color.rgb(13,26,39));
                if(bitmap!=null)c.drawBitmap(bitmap,null,new Rect(0,0,c.getWidth(),c.getHeight()),new Paint(Paint.FILTER_BITMAP_FLAG));
                else {Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setTextSize(30);c.drawText("Loading NOAA radar…",30,75,p);}
            }catch(Exception e){Log.w(LOG,"Car surface not ready",e);}
            finally{if(c!=null){try{sf.unlockCanvasAndPost(c);}catch(Exception ignored){}}}
        }
    }
}
