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
import android.graphics.Typeface;
import androidx.core.graphics.drawable.IconCompat;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.navigation.model.MapController;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Looper;
import androidx.lifecycle.Lifecycle;
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
import java.util.concurrent.Future;
import java.util.concurrent.Callable;

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
        private boolean follow=true;
        private volatile Location lastFix;
        private LocationManager locationManager;
        private LocationListener locationListener;
        private final RadarRenderer renderer=new RadarRenderer();
        private boolean warningsEnabled=true;
        private String mapStyle="satellite";
        RadarScreen(CarContext context){
            super(context);
            stations=readStations(context);
            String old=context.getSharedPreferences("newswave-car",Context.MODE_PRIVATE).getString("station","KHTX");
            chosen=find(old);
            mapStyle=context.getSharedPreferences("newswave-car",Context.MODE_PRIVATE)
                .getString("map_style","satellite");
            renderer.setMapStyle(mapStyle);
            try{context.getCarService(AppManager.class).setSurfaceCallback(renderer);}catch(Exception e){Log.w(LOG,"No map surface yet",e);}
            renderer.change(chosen,mode,nationwide);
            renderer.setWarningVisibility(true);
            startFollowing();
            getLifecycle().addObserver((androidx.lifecycle.LifecycleEventObserver)(source,event)->{
              if(event==Lifecycle.Event.ON_DESTROY&&locationManager!=null&&locationListener!=null){
                locationManager.removeUpdates(locationListener);
              }
            });
        }
        private void startFollowing(){
          if(locationListener!=null && locationManager!=null){
            try{locationManager.removeUpdates(locationListener);}catch(Exception ignored){}
            locationListener=null;
          }
          Location start=recentLocation(getCarContext());
          if(start!=null)onPosition(start);
          Context context=getCarContext();
          if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
            &&context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
          try{
            locationManager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            locationListener=this::onPosition;
            for(String provider:locationManager.getProviders(true)){
              locationManager.requestLocationUpdates(provider,12000,35,locationListener,Looper.getMainLooper());
            }
          }catch(Exception ex){Log.w(LOG,"Car GPS unavailable",ex);}
        }
        private void onPosition(Location loc){
          if(loc==null||!loc.hasAccuracy()||loc.getAccuracy()>250f||
             System.currentTimeMillis()-loc.getTime()>120000L)return;
          lastFix=new Location(loc); // GPS is independent of manual map panning.
          if(!follow)return;
          renderer.setPosition(loc.getLatitude(),loc.getLongitude());
          Station nearest=chosen;double best=Double.MAX_VALUE;
          for(Station station:stations){
            double d=sqDist(station,loc.getLatitude(),loc.getLongitude());
            if(d<best){best=d;nearest=station;}
          }
          if(nearest!=chosen){chosen=nearest;renderer.change(chosen,mode,nationwide);invalidate();}
        }
        private Station find(String id){
            for(Station s:stations)if(s.id.equals(id))return s;
            return stations.get(0);
        }
        void choose(Station s){
            // Station selection is explicit map navigation. A previously panned
            // center (e.g. Florida) must not override the selected radar site.
            follow=false;
            chosen=s;
            nationwide=false;
            renderer.focusStation(s);
            getCarContext().getSharedPreferences("newswave-car",Context.MODE_PRIVATE)
                .edit().putString("station",s.id).apply();
            renderer.change(chosen,mode,nationwide);
            invalidate();
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

        // Android Auto owns the panel position. Keep the panel minimal, and use
        // its standard PAN control for a map-focused view on supported hosts.
        private CarIcon mapIcon(String symbol){
            Bitmap bmp=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(bmp);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(symbol.length()>1?24:36);
            c.drawText(symbol,24,35,paint);
            return new CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build();
        }
        private void setFollow(boolean enabled){
            follow=enabled;
            if(enabled){
                renderer.recenter();
                startFollowing();
            }else renderer.disableFollow();
            invalidate();
        }
        @NonNull @Override public Template onGetTemplate() {
            // Weather-category MapWithContentTemplate requires a content pane:
            // one tiny row is the minimum Google-approved host overlay.
            // No header and no radar controls/details here; those live in Options.
            Pane status=new Pane.Builder()
                .addRow(new Row.Builder().setTitle("Map").build())
                .build();
            PaneTemplate pane=new PaneTemplate.Builder(status).build();
            ActionStrip controls=new ActionStrip.Builder()
                .addAction(new Action.Builder(Action.PAN).setIcon(mapIcon("↔")).build())
                .addAction(new Action.Builder().setIcon(mapIcon("◎"))
                    .setOnClickListener(()->setFollow(true)).build())
                .addAction(new Action.Builder().setIcon(mapIcon("+"))
                    .setOnClickListener(renderer::zoomIn).build())
                .addAction(new Action.Builder().setIcon(mapIcon("−"))
                    .setOnClickListener(renderer::zoomOut).build())
                .build();
            MapController mapControls=new MapController.Builder()
                .setMapActionStrip(controls)
                .setPanModeListener(isPanning->{ if(isPanning)setFollow(false); })
                .build();
            ActionStrip settings=new ActionStrip.Builder()
                .addAction(new Action.Builder().setTitle("Options")
                    .setOnClickListener(()->getScreenManager().push(
                        new RadarOptionsScreen(getCarContext(),this))).build())
                .build();
            return new MapWithContentTemplate.Builder()
                .setContentTemplate(pane)
                .setMapController(mapControls)
                .setActionStrip(settings)
                .build();
        }
    }


    // Settings are a separate screen: the main radar map is no longer hidden
    // behind five interactive list rows.
    private static final class RadarOptionsScreen extends Screen{
        private final RadarScreen radar;
        RadarOptionsScreen(CarContext context,RadarScreen radar){super(context);this.radar=radar;}
        @NonNull @Override public Template onGetTemplate(){
            ItemList.Builder list=new ItemList.Builder();
            list.addItem(new Row.Builder().setTitle("Safety dashboard")
                .addText("GPS location, NWS warnings and refresh")
                .setOnClickListener(()->getScreenManager().push(
                    new SafetyDashboardScreen(getCarContext(),radar))).build());
            list.addItem(new Row.Builder()
                .setTitle("Map type: "+RadarRenderer.styleName(radar.mapStyle))
                .addText("Satellite, terrain, or road map")
                .setOnClickListener(()->getScreenManager().push(
                    new MapStyleScreen(getCarContext(),radar))).build());
            list.addItem(new Row.Builder().setTitle("Radar station: "+radar.chosen.id)
                .addText(radar.chosen.name)
                .setOnClickListener(()->getScreenManager().push(new StationScreen(
                    getCarContext(),radar,radar.stations,0))).build());
            list.addItem(new Row.Builder().setTitle("Layer: "+radar.productName())
                .addText("Tap to change NOAA weather radar product")
                .setOnClickListener(()->{radar.changeMode();invalidate();}).build());
            list.addItem(new Row.Builder().setTitle(radar.nationwide?"National radar":"Local radar")
                .addText("Switch radar coverage; keep current zoom")
                .setOnClickListener(()->{
                    radar.nationwide=!radar.nationwide;
                    radar.renderer.change(radar.chosen,radar.mode,radar.nationwide);
                    invalidate();radar.invalidate();
                }).build());
            list.addItem(new Row.Builder()
                .setTitle("NWS warning outlines: "+(radar.warningsEnabled?"ON":"OFF"))
                .addText("Tornado, severe storm, flood and winter warnings")
                .setOnClickListener(()->{
                    radar.warningsEnabled=!radar.warningsEnabled;
                    radar.renderer.setWarningVisibility(radar.warningsEnabled);
                    invalidate();radar.invalidate();
                }).build());
            list.addItem(new Row.Builder()
                .setTitle("Follow my location: "+(radar.follow?"ON":"OFF"))
                .addText("Keep the GPS position centered when moving")
                .setOnClickListener(()->{radar.setFollow(!radar.follow);invalidate();}).build());
            list.addItem(new Row.Builder().setTitle("Refresh radar and warnings")
                .addText("Recheck NOAA radar and NWS alerts")
                .setOnClickListener(()->{
                    radar.renderer.forceWarningsRefresh();radar.renderer.refresh();
                }).build());
            return new ListTemplate.Builder().setSingleList(list.build())
                .setHeader(new Header.Builder().setTitle("Radar options")
                    .setStartHeaderAction(Action.BACK).build()).build();
        }
    }

    /** Android Auto warning summary. Point-based NWS data; never a route prediction. */
    private static final class SafetyDashboardScreen extends Screen {
        private final RadarScreen radar;
        private final ExecutorService worker=Executors.newSingleThreadExecutor();
        private final android.os.Handler ui=new android.os.Handler(Looper.getMainLooper());
        private boolean disposed=false,loading=false,checked=false;
        private long checkedAt=0;
        private String message="No official warning data retrieved";
        private List<ActiveAlertData.Item> alerts=Collections.emptyList();
        SafetyDashboardScreen(CarContext context,RadarScreen radar){
            super(context);this.radar=radar;
            getLifecycle().addObserver((androidx.lifecycle.LifecycleEventObserver)(source,event)->{
                if(event==Lifecycle.Event.ON_DESTROY){disposed=true;worker.shutdownNow();}
            });
            refreshSafety();
        }
        private Location currentGps(){
            Location loc=radar.lastFix;
            if(loc==null||!loc.hasAccuracy()||loc.getAccuracy()>250||
                System.currentTimeMillis()-loc.getTime()>120000L)return null;
            return new Location(loc);
        }
        private void refreshSafety(){
            if(disposed||loading)return;
            final Location fix=currentGps();
            checked=false;checkedAt=0;alerts=Collections.emptyList();
            if(fix==null){message="No recent accurate GPS. Enable phone location.";invalidate();return;}
            loading=true;message="";invalidate();
            worker.execute(()->{
                try{
                    final ActiveAlertData.Result result=ActiveAlertData.load("",
                        fix.getLatitude(),fix.getLongitude());
                    ui.post(()->{
                        if(disposed)return;
                        loading=false;checked=true;checkedAt=result.fetchedAt;
                        alerts=result.items;message="";invalidate();
                    });
                }catch(Exception error){
                    Log.w(LOG,"Car NWS check unavailable",error);
                    ui.post(()->{
                        if(disposed)return;
                        loading=false;checked=false;alerts=Collections.emptyList();
                        message="NWS unavailable; warning status UNKNOWN. Use official alerts.";
                        invalidate();
                    });
                }
            });
        }
        @NonNull @Override public Template onGetTemplate(){
            ItemList.Builder items=new ItemList.Builder();
            final Location fix=currentGps();
            if(fix==null)items.addItem(new Row.Builder().setTitle("GPS unavailable")
                .addText("Allow location and refresh").build());
            else items.addItem(new Row.Builder().setTitle("Current GPS")
                .addText("Fix "+Math.max(0,(System.currentTimeMillis()-fix.getTime())/1000)+" sec old").build());
            if(loading)items.addItem(new Row.Builder().setTitle("Checking NWS warnings")
                .addText("Fetching official alerts for GPS position").build());
            else if(!checked)items.addItem(new Row.Builder().setTitle("Warning status: UNKNOWN")
                .addText(message).build());
            else{
                items.addItem(new Row.Builder().setTitle(alerts.isEmpty()?
                    "No active NWS alerts returned":alerts.size()+" NWS alerts")
                    .addText("Checked "+new java.text.SimpleDateFormat("h:mm a",Locale.US)
                        .format(new java.util.Date(checkedAt))+" · not a safety guarantee").build());
                for(int n=0;n<Math.min(3,alerts.size());n++){
                    ActiveAlertData.Item alert=alerts.get(n);
                    items.addItem(new Row.Builder().setTitle(alert.event)
                        .addText(alert.area+" · expires "+ActiveAlertData.time(alert.expires)).build());
                }
            }
            items.addItem(new Row.Builder().setTitle("Refresh NWS status")
                .addText("Check official alerts for current location")
                .setOnClickListener(this::refreshSafety).build());
            items.addItem(new Row.Builder().setTitle("Storm Ahead is opt-in")
                .addText("Enable driving alerts on phone; no route or arrival forecast").build());
            return new ListTemplate.Builder().setSingleList(items.build())
                .setHeader(new Header.Builder().setTitle("Safety dashboard")
                    .setStartHeaderAction(Action.BACK).build()).build();
        }
    }

    /** Choose a background map; saved for every subsequent car session. */
    private static final class MapStyleScreen extends Screen {
        private final RadarScreen radar;
        MapStyleScreen(CarContext context,RadarScreen radar){super(context);this.radar=radar;}
        @NonNull @Override public Template onGetTemplate(){
            ItemList.Builder list=new ItemList.Builder();
            for(String style:new String[]{"satellite","terrain","road"}){
                String display=RadarRenderer.styleName(style);
                list.addItem(new Row.Builder()
                    .setTitle((style.equals(radar.mapStyle)?"✓ ":"")+display)
                    .addText(style.equals("satellite")?"Aerial satellite imagery":
                        style.equals("terrain")?"Topographic terrain and contour detail":
                        "Roads, cities and road labels")
                    .setOnClickListener(()->{
                        radar.mapStyle=style;
                        radar.getCarContext().getSharedPreferences("newswave-car",Context.MODE_PRIVATE)
                            .edit().putString("map_style",style).apply();
                        radar.renderer.setMapStyle(style);
                        radar.invalidate();
                        getScreenManager().pop();
                    }).build());
            }
            return new ListTemplate.Builder().setSingleList(list.build())
                .setHeader(new Header.Builder().setTitle("Map type")
                    .setStartHeaderAction(Action.BACK).build()).build();
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
        private static final String USER_AGENT="NewsWire/0.5.0 (https://github.com/sBranha/Software-Updates)";
        private final ExecutorService executor=Executors.newSingleThreadExecutor();
        private final ExecutorService tileWorkers=Executors.newFixedThreadPool(6);
        private final ExecutorService warningExecutor=Executors.newSingleThreadExecutor();
        private volatile WeatherWarningOverlay.Snapshot warnings=null;
        private long lastWarningRequest=0;
        private boolean warningsOn=true;
        private boolean followGps=true;
        private double manualLat=Double.NaN,manualLon=Double.NaN;
        private float scaleAccum=1f;
        private int zoom=7; // Zoom is independent of GPS-follow status.
        private int lastDisplayedZoom=7;
        private double lastDisplayedLat=Double.NaN, lastDisplayedLon=Double.NaN;
        private String mapStyle="satellite";
        static String styleName(String style){
            if("road".equals(style))return "Road Map";
            if("terrain".equals(style))return "Terrain";
            return "Satellite";
        }
        void setMapStyle(String style){
            if(!"road".equals(style)&&!"terrain".equals(style))style="satellite";
            synchronized(lock){mapStyle=style;}
            refresh();
        }
        private final Map<String,Bitmap> cache=new LinkedHashMap<String,Bitmap>(80,.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> eldest){return size()>80;}
        };
        private Surface surface;
        private int width=1024,height=600,revision=0;
        private Station station=new Station("KHTX","Huntsville, AL",34.93056,-86.08361);
        private String product="SR_BREF";private boolean usa=false;
        private Bitmap finished;
        private Double userLat=null,userLon=null;
        void setPosition(double latitude,double longitude){
            synchronized(lock){userLat=latitude;userLon=longitude;}
            refresh();
        }
        void disableFollow(){synchronized(lock){followGps=false;}}
        void focusStation(Station s){
            synchronized(lock){
                followGps=false;manualLat=s.lat;manualLon=s.lon;
                zoom=7;
            }
            refresh();
        }
        void recenter(){
            synchronized(lock){
                followGps=true;manualLat=Double.NaN;manualLon=Double.NaN;
            }
            refresh();
        }
        void zoomIn(){setZoom(1);}
        void zoomOut(){setZoom(-1);}
        private void setZoom(int delta){
            final int next;
            synchronized(lock){
                next=Math.max(3,Math.min(13,zoom+delta));
                if(next==zoom)return;
                zoom=next;
            }
            // Give immediate visual feedback from the existing map image rather
            // than waiting for tile + NOAA radar network downloads to finish.
            previewZoom();
            refresh();
        }
        private void previewZoom(){
            final Bitmap previous;
            final int oldZoom,newZoom;
            synchronized(lock){
                previous=finished;
                oldZoom=lastDisplayedZoom;
                newZoom=zoom;
            }
            if(previous==null || previous.isRecycled() || Math.abs(newZoom-oldZoom)>4)return;
            double scale=Math.pow(2,newZoom-oldZoom);
            if(scale==1.)return;
            int w=previous.getWidth(),h=previous.getHeight();
            // Large Android Auto surfaces plus rapid pinch gestures can
            // exhaust memory if we allocate an unbounded preview bitmap.
            if(w<=0||h<=0||(long)w*h>4_000_000L)return;
            try{
                Bitmap provisional=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(provisional);
                canvas.drawColor(Color.rgb(13,26,39));
                android.graphics.Matrix matrix=new android.graphics.Matrix();
                matrix.setScale((float)scale,(float)scale,w/2f,h/2f);
                canvas.drawBitmap(previous,matrix,new Paint(Paint.FILTER_BITMAP_FLAG));
                synchronized(lock){finished=provisional;}
                display();
            }catch(OutOfMemoryError memory){
                Log.w(LOG,"Car zoom preview skipped due to memory pressure");
                // The actual tiles will still render through refresh().
            }
        }
        @Override public void onScale(float x,float y,float factor){
            // Host supplies a relative gesture scaling factor. Some hosts issue
            // small increments instead of a single large value; accumulate them.
            if(!Float.isFinite(factor)||factor<=0)return;
            scaleAccum*=factor;
            if(scaleAccum>=1.30f){setZoom(1);scaleAccum=1f;}
            else if(scaleAccum<=0.77f){setZoom(-1);scaleAccum=1f;}
        }
        @Override public void onScroll(float dx,float dy){
            synchronized(lock){
                int z=zoom;
                double centerLat=followGps&&userLat!=null?userLat:
                    Double.isNaN(manualLat)?station.lat:manualLat;
                double centerLon=followGps&&userLon!=null?userLon:
                    Double.isNaN(manualLon)?station.lon:manualLon;
                double px=mx(centerLon,z)+dx;
                double py=my(centerLat,z)+dy;
                px=Math.max(0,Math.min(256*(1<<z),px));
                py=Math.max(0,Math.min(256*(1<<z),py));
                manualLat=lat(py,z);manualLon=lon(px,z);
                followGps=false;
            }
            refresh();
        }
        void setWarningVisibility(boolean visible){
            synchronized(lock){warningsOn=visible;}
            if(visible)forceWarningsRefresh();else refresh();
        }
        void forceWarningsRefresh(){
            synchronized(lock){lastWarningRequest=0;}
            fetchWarningsIfDue();
        }
        private void fetchWarningsIfDue(){
            synchronized(lock){
                if(!warningsOn||System.currentTimeMillis()-lastWarningRequest<WeatherWarningOverlay.REFRESH_MS)
                    return;
                lastWarningRequest=System.currentTimeMillis();
            }
            warningExecutor.execute(()->{
                warnings=WeatherWarningOverlay.fetch(warnings);
                refresh();
            });
        }
        private final Object lock=new Object();
        void change(Station s,String p,boolean national){
            synchronized(lock){
                station=s;product=p;usa=national;
                if(national && zoom>5)zoom=4;
                if(!national && zoom<5)zoom=7;
                revision++;
            }
            refresh();
        }
        void refresh(){
            fetchWarningsIfDue();
            final Station s;final String p;final boolean national;final int rev,w,h,z;
            final boolean auto,drawWarnings;
            final String selectedStyle;
            final double mLat,mLon;
            synchronized(lock){
                s=station;p=product;national=usa;rev=++revision;
                w=width;h=height;z=zoom;
                auto=followGps;drawWarnings=warningsOn;
                mLat=manualLat;mLon=manualLon;
                selectedStyle=mapStyle;
            }
            executor.execute(()->{
                synchronized(lock){if(rev!=revision)return;}
                render(s,p,national,rev,Math.max(480,w),Math.max(320,h),
                       z,auto,mLat,mLon,drawWarnings,selectedStyle);
            });
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
        private Bitmap tile(int x,int y,int z,String style){
            String key=style+"/"+z+"/"+x+"/"+y;
            synchronized(cache){Bitmap cached=cache.get(key);if(cached!=null&&!cached.isRecycled())return cached;}
            if(x<0||y<0||y>=(1<<z)||x>=(1<<z))return null;
            String server;
            if("road".equals(style))server="https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png";
            else {
                String service="terrain".equals(style)?"World_Topo_Map":"World_Imagery";
                server="https://services.arcgisonline.com/ArcGIS/rest/services/"+service
                    +"/MapServer/tile/"+z+"/"+y+"/"+x;
            }
            Bitmap b=download(server);
            if(b==null && !"road".equals(style))
                b=download("https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png");
            if(b!=null){synchronized(cache){cache.put(key,b);}}
            return b;
        }
        private void render(Station s,String p,boolean national,int rev,int w,int h,
                            int z,boolean auto,double manualLatitude,double manualLongitude,
                            boolean drawWarnings,String style){
            try{
                Double gLat,gLon; synchronized(lock){gLat=userLat;gLon=userLon;}
                double centerLat=auto&&gLat!=null?gLat:!Double.isNaN(manualLatitude)?manualLatitude:(national?39.0:s.lat);
                double centerLon=auto&&gLon!=null?gLon:!Double.isNaN(manualLongitude)?manualLongitude:(national?-98.0:s.lon);
                double x0=mx(centerLon,z)-w/2.,y0=my(centerLat,z)-h/2.;
                Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(out);
                canvas.drawColor(Color.rgb(13,26,39));
                Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                int tx0=(int)Math.floor(x0/256),tx1=(int)Math.ceil((x0+w)/256);
                int ty0=(int)Math.floor(y0/256),ty1=(int)Math.ceil((y0+h)/256);
                // Fetch map tiles concurrently: sequential network fetches made
                // a single zoom gesture appear frozen for many seconds.
                ArrayList<Future<Bitmap>> pendingTiles=new ArrayList<>();
                ArrayList<int[]> grid=new ArrayList<>();
                for(int ty=ty0;ty<=ty1;ty++){
                    for(int tx=tx0;tx<=tx1;tx++){
                        final int px=tx,py=ty;
                        grid.add(new int[]{tx,ty});
                        pendingTiles.add(tileWorkers.submit(()->tile(px,py,z,style)));
                    }
                }
                for(int n=0;n<pendingTiles.size();n++){
                    synchronized(lock){
                        if(rev!=revision){
                            for(Future<Bitmap> job:pendingTiles)job.cancel(true);
                            out.recycle();return;
                        }
                    }
                    try{
                        Bitmap t=pendingTiles.get(n).get(15,java.util.concurrent.TimeUnit.SECONDS);
                        int[] txy=grid.get(n);
                        if(t!=null)canvas.drawBitmap(t,(float)(txy[0]*256-x0),
                            (float)(txy[1]*256-y0),brush);
                    }catch(Exception err){
                        pendingTiles.get(n).cancel(true);
                        Log.w(LOG,"Map tile unavailable: "+err.getMessage());
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
                int warningsVisible=drawWarnings?
                    WeatherWarningOverlay.draw(canvas,warnings,z,x0,y0,w,h):-2;
                if(gLat!=null){
                    Paint halo=new Paint(Paint.ANTI_ALIAS_FLAG);halo.setColor(0x99ffffff);
                    float pinX=(float)(mx(gLon,z)-x0);
                    float pinY=(float)(my(gLat,z)-y0);
                    canvas.drawCircle(pinX,pinY,15,halo);
                    halo.setColor(0xff2389ff);canvas.drawCircle(pinX,pinY,10,halo);
                    halo.setColor(Color.WHITE);halo.setStyle(Paint.Style.STROKE);halo.setStrokeWidth(3);
                    canvas.drawCircle(pinX,pinY,10,halo);
                }
                if(!national&&gLat==null){
                    // Station center marker, white outlined radar reference point.
                    Paint pin=new Paint(Paint.ANTI_ALIAS_FLAG);
                    pin.setColor(Color.WHITE);pin.setStyle(Paint.Style.STROKE);pin.setStrokeWidth(2.5f);
                    canvas.drawCircle(w/2f,h/2f,9,pin);
                    canvas.drawLine(w/2f-15,h/2f,w/2f+15,h/2f,pin);
                    canvas.drawLine(w/2f,h/2f-15,w/2f,h/2f+15,pin);
                }
                // The primary radar view is map-first. Station and layer details
                // are available under Options instead of obscuring the map.
                if(warningsVisible>0){
                    Paint warningBanner=new Paint(Paint.ANTI_ALIAS_FLAG);
                    warningBanner.setColor(0xd01a2435);
                    canvas.drawRoundRect(12,10,Math.min(w-12,360),42,8,8,warningBanner);
                    warningBanner.setColor(Color.WHITE);
                    warningBanner.setTextSize(16);
                    canvas.drawText(warningsVisible+" NWS warning areas",22,32,warningBanner);
                }
                brush.setColor(0xca09111b);
                canvas.drawRoundRect(8,h-30,Math.min(w-8,490),h-5,5,5,brush);
                brush.setColor(Color.WHITE);brush.setTextSize(12);
                canvas.drawText("Imagery Esri / OSM · Radar and warnings NOAA/NWS",14,h-13,brush);
                synchronized(lock){
                    if(rev!=revision){out.recycle();return;}
                    finished=out;
                    lastDisplayedZoom=z;
                    lastDisplayedLat=centerLat;
                    lastDisplayedLon=centerLon;
                }
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
