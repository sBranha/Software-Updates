package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;

public final class MultiCameraSupport {
    public static final String PREF_BRAND="camera_brand";
    public static final String PREF_MODEL="camera_model";
    public static final String MODE_CAMERA_AP="camera_ap";
    public static final String MODE_PHONE_HOTSPOT="phone_hotspot";

    public static final class Profile {
        public final String brand, model, mode, setupTitle, setupSteps;
        public final boolean integratedGrip;
        Profile(String brand,String model,String mode,boolean grip,String title,String steps){
            this.brand=brand;this.model=model;this.mode=mode;this.integratedGrip=grip;this.setupTitle=title;this.setupSteps=steps;
        }
        public String displayName(){return brand+" "+model;}
    }

    private static Profile nikon(String model, boolean grip){
        return new Profile("Nikon",model,MODE_CAMERA_AP,grip,"Direct Wi‑Fi FTP",
                "On the camera: Network menu → Connect to FTP server → Create profile → Wi‑Fi access point mode.\n\n"+
                "Join the camera's Wi‑Fi network on this phone, then enter this phone's FTP address, port 2121, user nikon, and the FTP password shown in Advanced FTP.\n\n"+
                "Enable automatic upload on the camera. The phone can keep using 5G/LTE for Flickr while connected to the camera Wi‑Fi.");
    }
    private static Profile canon(String model, boolean grip){
        return new Profile("Canon",model,MODE_PHONE_HOTSPOT,grip,"Phone Hotspot + FTP",
                "Turn on this Android phone's Mobile Hotspot. Connect the camera to that hotspot.\n\n"+
                "On the camera, choose Transfer images to FTP server and enter the phone hotspot IP shown by this app, port 2121, user nikon, and the FTP password shown in Advanced FTP.\n\n"+
                "Enable Automatic transfer after each shot. The phone's cellular connection remains available for Flickr.");
    }
    private static Profile sony(String model, boolean grip){
        return new Profile("Sony",model,MODE_PHONE_HOTSPOT,grip,"Phone Hotspot + FTP",
                "Turn on this Android phone's Mobile Hotspot and connect the camera to it.\n\n"+
                "On the camera, open Network → FTP Transfer, configure the FTP destination with the phone hotspot IP shown by this app, port 2121, user nikon, and the FTP password shown in Advanced FTP.\n\n"+
                "Turn FTP Function on and enable automatic transfer for new images. Cellular data remains available for Flickr.");
    }

    public static final Profile[] PROFILES={
        nikon("Z8",false), nikon("Z9",true), nikon("Z6III",false), nikon("Z5II",false), nikon("Z50II",false),
        canon("EOS R1",true), canon("EOS R5 Mark II",false), canon("EOS R3",true), canon("EOS R5",false),
        sony("α1 II",false), sony("α9 III",false)
    };

    public static Profile selected(SharedPreferences p){
        String brand=p.getString(PREF_BRAND,"Nikon"),model=p.getString(PREF_MODEL,"Z8");
        for(Profile x:PROFILES)if(x.brand.equals(brand)&&x.model.equals(model))return x;
        return PROFILES[0];
    }

    public static void ensureDefaults(SharedPreferences p){
        if(!p.contains(PREF_BRAND)||!p.contains(PREF_MODEL))p.edit().putString(PREF_BRAND,"Nikon").putString(PREF_MODEL,"Z8").apply();
    }

    public static String cameraName(SharedPreferences p){return selected(p).displayName();}
    public static boolean usesPhoneHotspot(SharedPreferences p){return MODE_PHONE_HOTSPOT.equals(selected(p).mode);}
    public static String networkLabel(SharedPreferences p){return usesPhoneHotspot(p)?"Phone Hotspot FTP":"Direct Wi‑Fi FTP";}
    public static String waitingText(SharedPreferences p){
        Profile x=selected(p);
        return usesPhoneHotspot(p)?"Turn on Android Mobile Hotspot and connect the "+x.model+" to it.":"Turn on the "+x.model+" FTP Wi‑Fi access-point profile. No outside Wi‑Fi is required.";
    }

    public static View artwork(Context c, SharedPreferences p){
        Profile x=selected(p);
        if("Nikon".equals(x.brand)&&"Z8".equals(x.model)){
            ImageView image=new ImageView(c);image.setImageResource(R.drawable.z8_camera);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setAdjustViewBounds(true);return image;
        }
        return new CameraFrontView(c,x);
    }

    public static void showSelector(Activity a, SharedPreferences p, Runnable redraw){
        final String[] labels=new String[PROFILES.length];int checked=0;Profile cur=selected(p);
        for(int i=0;i<PROFILES.length;i++){labels[i]=PROFILES[i].displayName();if(PROFILES[i].brand.equals(cur.brand)&&PROFILES[i].model.equals(cur.model))checked=i;}
        final int[] choice={checked};
        new AlertDialog.Builder(a).setTitle("Choose camera").setSingleChoiceItems(labels,checked,(d,w)->choice[0]=w)
                .setNegativeButton("Cancel",null).setPositiveButton("Use camera",(d,w)->{
                    Profile x=PROFILES[choice[0]];p.edit().putString(PREF_BRAND,x.brand).putString(PREF_MODEL,x.model).apply();
                    if(redraw!=null)redraw.run();
                }).show();
    }

    public static void showSetup(Activity a, SharedPreferences p){
        Profile x=selected(p);String ip=findBestLocalIp();
        String msg=x.setupSteps+"\n\nCurrent phone FTP address: "+ip+":2121";
        new AlertDialog.Builder(a).setTitle(x.displayName()+" — "+x.setupTitle).setMessage(msg).setPositiveButton("OK",null).show();
    }

    public static String findBestLocalIp(){
        String fallback="waiting for camera / hotspot network";
        try{
            Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
            ArrayList<String> preferred=new ArrayList<>(),other=new ArrayList<>();
            while(en.hasMoreElements()){
                NetworkInterface ni=en.nextElement();if(!ni.isUp()||ni.isLoopback())continue;
                String n=ni.getName().toLowerCase(Locale.US);
                Enumeration<InetAddress> aa=ni.getInetAddresses();
                while(aa.hasMoreElements()){
                    InetAddress addr=aa.nextElement();if(!(addr instanceof Inet4Address)||addr.isLoopbackAddress()||!addr.isSiteLocalAddress())continue;
                    String ip=addr.getHostAddress();
                    if(n.contains("wlan")||n.contains("wifi")||n.contains("ap")||n.contains("swlan")||n.contains("softap")||n.contains("rndis"))preferred.add(ip);else other.add(ip);
                }
            }
            if(!preferred.isEmpty())return preferred.get(0);if(!other.isEmpty())return other.get(0);
        }catch(Exception ignored){}
        return fallback;
    }

    public static final class CameraFrontView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Profile profile;
        public CameraFrontView(Context c,Profile p){super(c);profile=p;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            float cx=w/2f,bodyTop=h*.22f,bodyBottom=h*.79f,bodyLeft=w*.13f,bodyRight=w*.87f;
            paint.setShadowLayer(18,0,10,Color.argb(120,0,0,0));paint.setColor(Color.rgb(25,27,30));
            RectF body=new RectF(bodyLeft,bodyTop,bodyRight,bodyBottom);c.drawRoundRect(body,26,26,paint);paint.clearShadowLayer();
            if(profile.integratedGrip){paint.setColor(Color.rgb(22,24,27));RectF grip=new RectF(w*.66f,h*.45f,w*.88f,h*.92f);c.drawRoundRect(grip,22,22,paint);}
            paint.setColor(Color.rgb(19,21,24));Path hump=new Path();hump.moveTo(w*.35f,bodyTop);hump.lineTo(w*.43f,h*.10f);hump.lineTo(w*.59f,h*.10f);hump.lineTo(w*.67f,bodyTop);hump.close();c.drawPath(hump,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,w*.008f));paint.setColor(Color.rgb(72,76,82));c.drawRoundRect(body,26,26,paint);paint.setStyle(Paint.Style.FILL);
            float r=Math.min(w,h)*.225f;paint.setColor(Color.rgb(8,10,12));c.drawCircle(cx,h*.49f,r,paint);paint.setColor(Color.rgb(55,60,66));c.drawCircle(cx,h*.49f,r*.86f,paint);paint.setColor(Color.rgb(5,9,12));c.drawCircle(cx,h*.49f,r*.69f,paint);paint.setColor(Color.rgb(9,27,38));c.drawCircle(cx,h*.49f,r*.48f,paint);paint.setColor(Color.rgb(24,62,79));c.drawCircle(cx-r*.12f,h*.45f,r*.25f,paint);paint.setColor(Color.rgb(6,11,15));c.drawCircle(cx,h*.49f,r*.19f,paint);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);paint.setColor(Color.WHITE);paint.setTextSize(Math.max(18,w*.055f));c.drawText(profile.brand,cx,h*.19f,paint);paint.setTextSize(Math.max(15,w*.044f));paint.setColor(Color.rgb(220,224,228));c.drawText(profile.model,cx,h*.88f,paint);
            paint.setColor(Color.rgb(160,165,171));c.drawRoundRect(new RectF(w*.18f,h*.27f,w*.29f,h*.34f),8,8,paint);paint.setColor(Color.rgb(48,52,57));c.drawCircle(w*.75f,h*.30f,w*.035f,paint);
        }
    }

    private MultiCameraSupport(){}
}
