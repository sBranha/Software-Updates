package com.newswave.live;

import android.Manifest;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
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
import java.io.File;
import java.io.FileOutputStream;
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
        private boolean nationwide=true; // Use NOAA national mosaic by default; local products are opt-in.
        private boolean follow=true;
        private volatile Location lastFix;
        private LocationManager locationManager;
        private LocationListener locationListener;
        private final RadarRenderer renderer;
        private boolean warningsEnabled=true;
        private String mapStyle="road";
        private boolean perspective3d=true;
        RadarScreen(CarContext context){
            super(context);
            renderer=new RadarRenderer(context.getCacheDir());
            stations=readStations(context);
            String old=context.getSharedPreferences("newswave-car",Context.MODE_PRIVATE).getString("station","KHTX");
            chosen=find(old);
            mapStyle=context.getSharedPreferences("newswave-car",Context.MODE_PRIVATE)
                .getString("map_style","road");
            perspective3d=context.getSharedPreferences("newswave-car",Context.MODE_PRIVATE)
                .getBoolean("perspective_3d",true);
            renderer.setMapStyle(mapStyle);
            renderer.setPerspective(perspective3d);
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
              locationManager.requestLocationUpdates(provider,1500,3,locationListener,Looper.getMainLooper());
            }
          }catch(Exception ex){Log.w(LOG,"Car GPS unavailable",ex);}
        }
        private void onPosition(Location loc){
          if(loc==null||!loc.hasAccuracy()||loc.getAccuracy()>120f||
             System.currentTimeMillis()-loc.getTime()>45000L)return;
          Location previous=lastFix;
          if(previous!=null){
            // Reject older network fixes, or coarse fixes that would yank the
            // vehicle marker away from a fresh accurate GPS measurement.
            if(loc.getElapsedRealtimeNanos()<=previous.getElapsedRealtimeNanos())return;
            if(loc.getAccuracy()>previous.getAccuracy()*3f &&
               loc.getTime()-previous.getTime()<10000L)return;
          }
          lastFix=new Location(loc);
          if(!follow)return;
          float course=Float.NaN;
          float traveled=previous==null?0f:previous.distanceTo(loc);
          float elapsed=previous==null?0f:(loc.getTime()-previous.getTime())/1000f;
          float displacementSpeed=elapsed>0f && elapsed<30f ? traveled/elapsed:0f;
          boolean moving=(loc.hasSpeed() && loc.getSpeed()>=1.5f)
              ||(traveled>=12f && displacementSpeed>=1.8f && loc.getAccuracy()<35f);
          if(moving && loc.hasBearing() && loc.hasSpeed() && loc.getSpeed()>=1.5f)
              course=loc.getBearing();
          else if(moving && previous!=null && traveled>=8f)
              course=previous.bearingTo(loc);
          renderer.setPosition(loc.getLatitude(),loc.getLongitude(),course,
              loc.hasAccuracy()?loc.getAccuracy():20f,!moving);
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
            // National mosaic supports reflectivity only. Other products
            // require the selected local NOAA station.
            if(!"SR_BREF".equals(mode))nationwide=false;
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
        // Icon-only settings action keeps the host controls compact and legible.
        private CarIcon settingsIcon(){
            Bitmap bmp=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bmp);
            Paint gear=new Paint(Paint.ANTI_ALIAS_FLAG);
            gear.setColor(Color.WHITE);
            gear.setStyle(Paint.Style.STROKE);
            gear.setStrokeWidth(4.5f);
            gear.setStrokeCap(Paint.Cap.ROUND);
            for(int n=0;n<8;n++){
                double radians=Math.toRadians(n*45.);
                float cos=(float)Math.cos(radians),sin=(float)Math.sin(radians);
                canvas.drawLine(24+16*cos,24+16*sin,24+21*cos,24+21*sin,gear);
            }
            canvas.drawCircle(24,24,14,gear);
            canvas.drawCircle(24,24,5,gear);
            return new CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build();
        }
        // The host decides whether icon actions sit beside or below each other.
        // Draw the information glyph directly, avoiding font-dependent Unicode.
        private CarIcon infoIcon(){
            Bitmap bmp=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bmp);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(3.7f);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(24,24,18,paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(24,15,2.4f,paint);
            canvas.drawRoundRect(22,21,26,35,2,2,paint);
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
            // MapWithContentTemplate requires genuine pane content. The empty
            // pane introduced in v0.7.4 violates the host template contract and
            // can trigger "NewsWire has encountered an unexpected error" on Auto.
            // Keep one minimal valid row until a supported map-only weather
            // template is available. Do not disguise this as NavigationTemplate.
            Pane status=new Pane.Builder()
                .addRow(new Row.Builder().setTitle("●").build())
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
                .addAction(new Action.Builder().setIcon(settingsIcon())
                    .setOnClickListener(()->getScreenManager().push(
                        new RadarOptionsScreen(getCarContext(),this))).build())
                .addAction(new Action.Builder().setIcon(infoIcon())
                    .setOnClickListener(()->getScreenManager().push(
                        new RadarInfoScreen(getCarContext(),this))).build())
                .build();
            return new MapWithContentTemplate.Builder()
                .setContentTemplate(pane)
                .setMapController(mapControls)
                .setActionStrip(settings)
                .build();
        }
    }


    // On-demand information screen opened by the ⓘ action beside settings.
    // Four short rows avoid cluttering the car display while driving.
    // The NWS status is UNKNOWN until the official point-based lookup runs.
    private static final class RadarInfoScreen extends Screen {
        private final RadarScreen radar;
        RadarInfoScreen(CarContext context,RadarScreen radar){
            super(context);this.radar=radar;
        }
        @NonNull @Override public Template onGetTemplate(){
            ItemList.Builder items=new ItemList.Builder();
            items.addItem(new Row.Builder().setTitle("NWS warning information")
                .addText("Tap to check active official alerts for your GPS location")
                .setOnClickListener(()->getScreenManager().push(
                    new SafetyDashboardScreen(getCarContext(),radar))).build());
            Location fix=radar.lastFix;
            boolean fresh=fix!=null&&fix.hasAccuracy()
                &&System.currentTimeMillis()-fix.getTime()<120000L;
            items.addItem(new Row.Builder()
                .setTitle(fresh?"GPS available":"GPS unavailable or stale")
                .addText(fresh
                    ?(radar.follow?"Following vehicle":"Map panning")+
                        " · Fix "+Math.max(0,(System.currentTimeMillis()-fix.getTime())/1000)+"s old"
                    :"Enable phone location; current position not confirmed")
                .build());
            items.addItem(new Row.Builder()
                .setTitle("Radar: "+radar.chosen.id)
                .addText((radar.nationwide?"National mosaic":"Local station")+
                    " · "+radar.productName()).build());
            String selectedStream=getCarContext()
                .getSharedPreferences("newswire-car-stream",Context.MODE_PRIVATE)
                .getString("title","");
            if(!selectedStream.isEmpty()){
                items.addItem(new Row.Builder()
                    .setTitle("Phone-selected: "+selectedStream)
                    .addText("Video plays on phone; standard Android Auto weather view cannot play videos")
                    .build());
            }
            items.addItem(new Row.Builder()
                .setTitle("Map: "+RadarRenderer.styleName(radar.mapStyle))
                .addText((radar.perspective3d?"3D driving view":"Flat view")+
                    " · Weather NOAA/NWS; not a route forecast").build());
            return new ListTemplate.Builder().setSingleList(items.build())
                .setHeader(new Header.Builder().setTitle("Map information")
                    .setStartHeaderAction(Action.BACK).build()).build();
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
                .setTitle("3D map perspective: "+(radar.perspective3d?"ON":"OFF"))
                .addText("Ford-inspired tilted road view, or flat 2D map")
                .setOnClickListener(()->{
                    radar.perspective3d=!radar.perspective3d;
                    radar.getCarContext().getSharedPreferences("newswave-car",Context.MODE_PRIVATE)
                        .edit().putBoolean("perspective_3d",radar.perspective3d).apply();
                    radar.renderer.setPerspective(radar.perspective3d);
                    invalidate();radar.invalidate();
                }).build());
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
            for(String style:new String[]{"road","satellite","terrain"}){
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
        private static final String USER_AGENT="NewsWire/0.7.3 (+https://github.com/sBranha/Software-Updates)";
        private static final long ROAD_CACHE_MS=7L*24*60*60*1000;
        private final File roadCacheDir;
        RadarRenderer(File cacheRoot){
            roadCacheDir=new File(cacheRoot,"newswire-road-tiles-v2");
            if(!roadCacheDir.isDirectory() && !roadCacheDir.mkdirs())
                Log.w(LOG,"Road tile cache unavailable");
        }
        private final ExecutorService executor=Executors.newSingleThreadExecutor();
        private final ExecutorService tileWorkers=Executors.newFixedThreadPool(6);
        private final ExecutorService warningExecutor=Executors.newSingleThreadExecutor();
        private volatile WeatherWarningOverlay.Snapshot warnings=null;
        private long lastWarningRequest=0;
        private boolean warningsOn=true;
        private boolean followGps=true;
        private double manualLat=Double.NaN,manualLon=Double.NaN;
        private float scaleAccum=1f;
        private int zoom=16; // Closer, forward-looking driving view; zoom remains adjustable.
        private int lastDisplayedZoom=16;
        private double lastDisplayedLat=Double.NaN, lastDisplayedLon=Double.NaN;
        private String mapStyle="road";
        private boolean perspective3d=true;
        static String styleName(String style){
            if("road".equals(style))return "Street Map";
            if("terrain".equals(style))return "Terrain";
            return "Satellite";
        }
        void setMapStyle(String style){
            if(!"road".equals(style)&&!"terrain".equals(style)&&!"satellite".equals(style))style="satellite";
            synchronized(lock){mapStyle=style;}
            refresh();
        }
        void setPerspective(boolean on){
            synchronized(lock){perspective3d=on;}
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
        private Float userBearing=null;
        void setPosition(double latitude,double longitude,float bearing,
                         float accuracy,boolean stationary){
            boolean redraw=true;
            synchronized(lock){
                double oldLat=userLat==null?Double.NaN:userLat;
                double oldLon=userLon==null?Double.NaN:userLon;
                Float oldBearing=userBearing;
                if(stationary && userLat!=null && userLon!=null){
                    float[] displacement=new float[1];
                    Location.distanceBetween(userLat,userLon,latitude,longitude,displacement);
                    // Avoid creeping/rotating across the road while parked.
                    if(displacement[0] < Math.max(9f,Math.min(25f,accuracy*.6f))){
                        latitude=userLat;longitude=userLon;
                    }
                }
                userLat=latitude;userLon=longitude;
                if(Float.isFinite(bearing)){
                    float course=(bearing%360f+360f)%360f;
                    if(userBearing==null)userBearing=course;
                    else {
                        float difference=(course-userBearing+540f)%360f-180f;
                        // Smooth ordinary GPS noise but turn quickly on real bends.
                        float alpha=Math.abs(difference)>50f?0.85f:0.45f;
                        userBearing=(userBearing+alpha*difference+360f)%360f;
                    }
                }
                // Invalid or stationary fixes never clear the last good heading.
                if(!Double.isNaN(oldLat) && !Double.isNaN(oldLon)){
                    float[] drift=new float[1];
                    Location.distanceBetween(oldLat,oldLon,userLat,userLon,drift);
                    float angular=oldBearing==null || userBearing==null ? 0f :
                         Math.abs((userBearing-oldBearing+540f)%360f-180f);
                    // Avoid re-downloading imagery for sub-pixel GPS movement
                    // when the driver is stopped or cruising straight.
                    redraw=drift[0]>=5f || angular>=2f;
                }
            }
            if(redraw)refresh();
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
                // Return to close-up navigation view after regional radar browsing.
                if(zoom<15)zoom=16;
            }
            refresh();
        }
        void zoomIn(){setZoom(1);}
        void zoomOut(){setZoom(-1);}
        private void setZoom(int delta){
            final int next;
            synchronized(lock){
                next=Math.max(3,Math.min(17,zoom+delta));
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
                final boolean isFollowing;
                synchronized(lock){isFollowing=followGps;}
                matrix.setScale((float)scale,(float)scale,w/2f,
                                isFollowing?h*.67f:h/2f);
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
                // Pan gestures are in screen coordinates. Convert back to
                // geographic coordinates when the driving map is heading-up.
                float worldX=dx,worldY=dy;
                if(followGps&&userBearing!=null){
                    double rad=Math.toRadians(userBearing);
                    worldX=(float)(Math.cos(rad)*dx-Math.sin(rad)*dy);
                    worldY=(float)(Math.sin(rad)*dx+Math.cos(rad)*dy);
                }
                double px=mx(centerLon,z)+worldX;
                double py=my(centerLat,z)+worldY;
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
                // Switching nearest radar stations must not zoom a moving map out.
                if(!followGps && national && zoom>5)zoom=4;
                if(!followGps && !national && zoom<5)zoom=7;
                revision++;
            }
            refresh();
        }
        void refresh(){
            fetchWarningsIfDue();
            final Station s;final String p;final boolean national;final int rev,w,h,z;
            final boolean auto,drawWarnings,tiltRoads;
            final String selectedStyle;
            final double mLat,mLon;
            synchronized(lock){
                s=station;p=product;national=usa;rev=++revision;
                w=width;h=height;z=zoom;
                auto=followGps;drawWarnings=warningsOn;tiltRoads=perspective3d;
                mLat=manualLat;mLon=manualLon;
                selectedStyle=mapStyle;
            }
            executor.execute(()->{
                synchronized(lock){if(rev!=revision)return;}
                render(s,p,national,rev,Math.max(480,w),Math.max(320,h),
                       z,auto,mLat,mLon,drawWarnings,selectedStyle,tiltRoads);
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
        // The public OSM tile service requires attribution, an identifiable
        // User-Agent and a seven-day local cache. Fetch only visible tiles.
        private Bitmap osmRoadTile(int x,int y,int z){
            File file=new File(roadCacheDir,z+"-"+x+"-"+y+".png");
            if(file.isFile()){
                if(System.currentTimeMillis()-file.lastModified()<ROAD_CACHE_MS){
                    Bitmap saved=BitmapFactory.decodeFile(file.getAbsolutePath());
                    if(saved!=null)return saved;
                }
                if(!file.delete())Log.w(LOG,"Old road tile could not be removed");
            }
            Bitmap image=download("https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png");
            if(image!=null && roadCacheDir.isDirectory()){
                try(FileOutputStream stream=new FileOutputStream(file)){
                    if(!image.compress(Bitmap.CompressFormat.PNG,100,stream)){
                        Log.w(LOG,"Road tile cache write failed");
                        if(!file.delete())Log.w(LOG,"Failed cache file cleanup");
                    }
                }catch(Exception ex){Log.w(LOG,"Road tile cache unavailable",ex);}
            }
            return image;
        }
        private Bitmap tile(int x,int y,int z,String style){
            String key=style+"/"+z+"/"+x+"/"+y;
            synchronized(cache){Bitmap cached=cache.get(key);if(cached!=null&&!cached.isRecycled())return cached;}
            if(x<0||y<0||y>=(1<<z)||x>=(1<<z))return null;
            Bitmap b;
            if("road".equals(style))b=osmRoadTile(x,y,z);
            else {
                String service="terrain".equals(style)?"World_Topo_Map":"World_Imagery";
                String server="https://services.arcgisonline.com/ArcGIS/rest/services/"+service
                    +"/MapServer/tile/"+z+"/"+y+"/"+x;
                b=download(server);
                if(b==null)b=osmRoadTile(x,y,z);
            }
            if(b!=null){synchronized(cache){cache.put(key,b);}}
            return b;
        }
        /**
         * A valid transparent NOAA radar image can be laid over roads.
         * Some station WMS responses contain opaque blue rectangular no-data
         * blocks; these are NOT weather, and must never hide the basemap.
         */
        private static boolean suspiciousRadarOverlay(Bitmap image){
            if(image==null || image.isRecycled())return true;
            int samples=0,opaqueBlue=0;
            int sx=Math.max(1,image.getWidth()/44);
            int sy=Math.max(1,image.getHeight()/28);
            for(int y=sy/2;y<image.getHeight();y+=sy){
                for(int x=sx/2;x<image.getWidth();x+=sx){
                    int pixel=image.getPixel(x,y);
                    int a=Color.alpha(pixel),r=Color.red(pixel),
                        g=Color.green(pixel),b=Color.blue(pixel);
                    samples++;
                    if(a>=235 && b>75 && b>r*1.30f && b>g*1.12f && r<145)
                        opaqueBlue++;
                }
            }
            return samples>0 && opaqueBlue>samples*.12f;
        }
        private void render(Station s,String p,boolean national,int rev,int w,int h,
                            int z,boolean auto,double manualLatitude,double manualLongitude,
                            boolean drawWarnings,String style,boolean perspective){
            Bitmap out=null;
            try{
                Double gLat,gLon;Float bearing;
                synchronized(lock){gLat=userLat;gLon=userLon;bearing=userBearing;}
                double centerLat=auto&&gLat!=null?gLat:!Double.isNaN(manualLatitude)?manualLatitude:(national?39.0:s.lat);
                double centerLon=auto&&gLon!=null?gLon:!Double.isNaN(manualLongitude)?manualLongitude:(national?-98.0:s.lon);
                boolean headingUp=auto && gLat!=null && bearing!=null;
                float degrees=headingUp?bearing:0f;
                float anchorX=w*.5f,anchorY=auto?h*.67f:h*.5f;
                double x0=mx(centerLon,z)-anchorX,y0=my(centerLat,z)-anchorY;

                // Rotate the entire weather map (roads, radar and warning boundaries),
                // not just the position arrow. Cover all corners after rotation.
                // For a perspective projection, sample beyond the horizon's visible
                // rectangle. The source trapezoid maps onto the full car display.
                boolean drivingPerspective=perspective&&auto&&gLat!=null;
                float left=drivingPerspective?-w*.34f:0f;
                float top=drivingPerspective?-h*.28f:0f;
                float right=drivingPerspective?w*1.34f:w;
                float bottom=h;
                if(headingUp){
                    double radians=Math.toRadians(degrees);
                    double cs=Math.cos(radians),sn=Math.sin(radians);
                    float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE;
                    float maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
                    float[] corners={left,top,right,top,right,bottom,left,bottom};
                    for(int i=0;i<corners.length;i+=2){
                        double dx=corners[i]-anchorX,dy=corners[i+1]-anchorY;
                        float ux=(float)(anchorX+cs*dx-sn*dy);
                        float uy=(float)(anchorY+sn*dx+cs*dy);
                        minX=Math.min(minX,ux);maxX=Math.max(maxX,ux);
                        minY=Math.min(minY,uy);maxY=Math.max(maxY,uy);
                    }
                    left=(float)Math.floor(minX)-3;
                    top=(float)Math.floor(minY)-3;
                    right=(float)Math.ceil(maxX)+3;
                    bottom=(float)Math.ceil(maxY)+3;
                }
                int mapWidth=(int)Math.ceil(right-left),mapHeight=(int)Math.ceil(bottom-top);
                double mapLeft=x0+left,mapTop=y0+top;

                out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(out);
                canvas.drawColor(Color.rgb(13,26,39));
                Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                // Darken only the street basemap tiles; radar colors and warning
                // polygons must remain true to the official hazard products.
                // Color matrix transforms OSM's light tiles to a dark neutral
                // palette while retaining bright road names and route boundaries.
                Paint streetBrush=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                if("road".equals(style)){
                    streetBrush.setColorFilter(new android.graphics.ColorMatrixColorFilter(
                        new float[]{
                            -.15945f,-.53640f,-.05415f,0f,218f,
                            -.16583f,-.55786f,-.05631f,0f,225f,
                            -.17221f,-.57931f,-.05848f,0f,236f,
                            0f,0f,0f,1f,0f
                        }));
                }
                // Android Canvas supports a real four-corner projective transform.
                // This is a tilted raster view, NOT 3D buildings or Ford's maps.
                Matrix tilt=null;
                if(drivingPerspective){
                    tilt=new Matrix();
                    float[] source={-w*.34f,-h*.28f,w*1.34f,-h*.28f,w,h,0,h};
                    float[] target={0,0,w,0,w,h,0,h};
                    if(!tilt.setPolyToPoly(source,0,target,0,4))tilt=null;
                }
                canvas.save();
                if(tilt!=null)canvas.concat(tilt);
                if(headingUp)canvas.rotate(-degrees,anchorX,anchorY);
                canvas.translate(left,top);

                int tx0=(int)Math.floor(mapLeft/256),tx1=(int)Math.floor((mapLeft+mapWidth-1)/256);
                int ty0=(int)Math.floor(mapTop/256),ty1=(int)Math.floor((mapTop+mapHeight-1)/256);
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
                        if(t!=null)canvas.drawBitmap(t,(float)(txy[0]*256-mapLeft),
                            (float)(txy[1]*256-mapTop),"road".equals(style)?streetBrush:brush);
                    }catch(Exception err){
                        pendingTiles.get(n).cancel(true);
                        Log.w(LOG,"Map tile unavailable: "+err.getMessage());
                    }
                }
                double west=lon(mapLeft,z),east=lon(mapLeft+mapWidth,z);
                double north=lat(mapTop,z),south=lat(mapTop+mapHeight,z);
                String bbox=String.format(Locale.US,"%.6f,%.6f,%.6f,%.6f",west,south,east,north);
                String url;
                if(national){
                    url="https://mapservices.weather.noaa.gov/eventdriven/rest/services/radar/radar_base_reflectivity/MapServer/export"
                       +"?bbox="+bbox+"&bboxSR=4326&imageSR=4326&size="+mapWidth+","+mapHeight
                       +"&format=png32&transparent=true&layers=show%3A0&f=image";
                }else{
                    String id=s.id.toLowerCase(Locale.US);
                    url="https://opengeo.ncep.noaa.gov/geoserver/"+id+"/ows?service=WMS"
                       +"&version=1.1.1&request=GetMap&layers="+id+"_"+p.toLowerCase(Locale.US)
                       +"&styles=&srs=EPSG%3A4326&bbox="+bbox
                       +"&width="+mapWidth+"&height="+mapHeight
                       +"&format=image%2Fpng&transparent=true";
                }
                Bitmap radar=download(url);
                boolean radarAvailable=radar!=null&&!suspiciousRadarOverlay(radar);
                if(radarAvailable){
                    canvas.drawBitmap(radar,null,new Rect(0,0,mapWidth,mapHeight),brush);
                }else if(radar!=null){
                    Log.w(LOG,"Skipped invalid NOAA radar overlay (opaque blue no-data blocks)");
                }
                int warningsVisible=drawWarnings?
                    WeatherWarningOverlay.draw(canvas,warnings,z,mapLeft,mapTop,mapWidth,mapHeight):-2;
                canvas.restore();

                if(gLat!=null){
                    float pinX=headingUp?anchorX:(float)(mx(gLon,z)-x0);
                    float pinY=headingUp?anchorY:(float)(my(gLat,z)-y0);
                    if(tilt!=null){
                        float[] projected={pinX,pinY};tilt.mapPoints(projected);
                        pinX=projected[0];pinY=projected[1];
                    }
                    Paint pin=new Paint(Paint.ANTI_ALIAS_FLAG);
                    pin.setColor(0x99ffffff);
                    canvas.drawCircle(pinX,pinY,18,pin);
                    pin.setColor(0xfffc652c);
                    canvas.drawCircle(pinX,pinY,13,pin);
                    pin.setColor(Color.WHITE);
                    pin.setStyle(Paint.Style.STROKE);
                    pin.setStrokeWidth(3);
                    canvas.drawCircle(pinX,pinY,13,pin);
                    if(headingUp){
                        pin.setStyle(Paint.Style.FILL);
                        pin.setColor(0xffff9f30);
                        android.graphics.Path pointer=new android.graphics.Path();
                        pointer.moveTo(pinX,pinY-36);
                        pointer.lineTo(pinX-12,pinY-9);
                        pointer.lineTo(pinX+12,pinY-9);
                        pointer.close();
                        canvas.drawPath(pointer,pin);
                    }else if(bearing!=null){
                        pin.setStyle(Paint.Style.FILL);
                        pin.setColor(0xff2389ff);
                        android.graphics.Path pointer=new android.graphics.Path();
                        pointer.moveTo(pinX,pinY-34);
                        pointer.lineTo(pinX-9,pinY-15);
                        pointer.lineTo(pinX+9,pinY-15);
                        pointer.close();
                        canvas.save();
                        canvas.rotate(bearing,pinX,pinY);
                        canvas.drawPath(pointer,pin);
                        canvas.restore();
                    }
                }else if(!national){
                    Paint pin=new Paint(Paint.ANTI_ALIAS_FLAG);
                    pin.setColor(Color.WHITE);pin.setStyle(Paint.Style.STROKE);
                    pin.setStrokeWidth(2.5f);
                    canvas.drawCircle(w/2f,h/2f,9,pin);
                }
                if(headingUp){
                    // Small upright compass; its north needle indicates orientation
                    // as the map rotates to keep the driving direction at the top.
                    float cx=45,cy=h-81;
                    Paint compass=new Paint(Paint.ANTI_ALIAS_FLAG);
                    compass.setColor(0xd4152739);
                    canvas.drawCircle(cx,cy,29,compass);
                    compass.setColor(Color.WHITE);
                    compass.setStyle(Paint.Style.STROKE);
                    compass.setStrokeWidth(2);
                    canvas.drawCircle(cx,cy,28,compass);
                    double r=Math.toRadians(degrees);
                    float nx=cx-(float)Math.sin(r)*19f;
                    float ny=cy-(float)Math.cos(r)*19f;
                    compass.setStyle(Paint.Style.FILL);
                    compass.setStrokeWidth(3f);
                    canvas.drawLine(cx,cy,nx,ny,compass);
                    compass.setTextAlign(Paint.Align.CENTER);
                    compass.setTextSize(11);
                    canvas.drawText("N",nx,ny-5,compass);
                }
                if(warningsVisible>0){
                    Paint banner=new Paint(Paint.ANTI_ALIAS_FLAG);
                    banner.setColor(0xd01a2435);
                    canvas.drawRoundRect(12,10,Math.min(w-12,360),42,8,8,banner);
                    banner.setColor(Color.WHITE);
                    banner.setTextSize(16);
                    canvas.drawText(warningsVisible+" NWS warning areas",22,32,banner);
                }
                if(!radarAvailable){
                    Paint unavailable=new Paint(Paint.ANTI_ALIAS_FLAG);
                    unavailable.setColor(0xd01a2435);
                    canvas.drawRoundRect(12,h-65,Math.min(w-12,410),h-36,5,5,unavailable);
                    unavailable.setColor(Color.WHITE);
                    unavailable.setTextSize(14);
                    canvas.drawText("Radar unavailable · Map and NWS warnings remain",20,h-46,unavailable);
                }
                brush.setColor(0xca09111b);
                canvas.drawRoundRect(8,h-30,Math.min(w-8,490),h-5,5,5,brush);
                brush.setColor(Color.WHITE);brush.setTextSize(12);
                canvas.drawText("Map © OpenStreetMap contributors / Esri · Weather NOAA/NWS",14,h-13,brush);
                synchronized(lock){
                    if(rev!=revision){out.recycle();return;}
                    finished=out;
                    lastDisplayedZoom=z;
                    lastDisplayedLat=centerLat;
                    lastDisplayedLon=centerLon;
                }
                display();
            }catch(Throwable e){
                Log.e(LOG,"Radar rendering failed",e);
                if(out!=null && out!=finished && !out.isRecycled())out.recycle();
            }
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
