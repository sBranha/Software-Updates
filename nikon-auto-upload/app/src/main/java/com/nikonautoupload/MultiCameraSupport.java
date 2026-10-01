package com.nikonautoupload;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MultiCameraSupport {
    public static final String PREF_BRAND="camera_brand";
    public static final String PREF_MODEL="camera_model";
    public static final String MODE_CAMERA_AP="camera_ap";
    public static final String MODE_PHONE_HOTSPOT="phone_hotspot";

    public static final class Profile {
        public final String brand, model, mode, setupTitle, setupSteps, accessory;
        public final boolean integratedGrip;
        Profile(String brand,String model,String mode,boolean grip,String title,String steps,String accessory){
            this.brand=brand;this.model=model;this.mode=mode;this.integratedGrip=grip;this.setupTitle=title;this.setupSteps=steps;this.accessory=accessory==null?"":accessory;
        }
        public String displayName(){return brand+" "+model;}
        public String accessoryLabel(){return accessory.isEmpty()?"Built-in FTP/Wi-Fi":accessory;}
    }

    private static String commonServer(){
        return "Use plain FTP (not FTPS/SFTP). Server = the phone IP shown by the app, port 2121, user nikon, password = Advanced FTP password. Use Passive/PASV mode when the camera offers it. Destination folder = root. For this app/Flickr workflow, transfer JPEG files.";
    }

    private static Profile nikon(String model, boolean grip){
        return new Profile("Nikon",model,MODE_CAMERA_AP,grip,"Direct Wi-Fi FTP",
                "Network menu → Connect to FTP server → Network settings → Create profile → Connection wizard → Direct connection / Wi-Fi access point mode.\n\n"+
                "Join the camera's Wi-Fi network on this Android phone. On the camera choose FTP, enter port 2121, choose Enter user ID, use user nikon and the password shown in Advanced FTP. Choose the server home/root folder.\n\n"+
                "Then enable Connect to FTP server → Options → Auto upload. For RAW+JPEG, choose JPEG only if that option is available.\n\n"+commonServer(),"");
    }
    private static Profile nikonAccessory(String model, boolean grip, String accessory){
        return new Profile("Nikon",model,MODE_CAMERA_AP,grip,"Wireless transmitter + FTP",
                "Attach and enable the "+accessory+". Create an FTP upload profile on the camera/transmitter. Use access-point mode if available so the transmitter/camera creates the Wi-Fi network; join that network on the Android phone.\n\n"+
                "Enter the phone FTP server information shown by the app: port 2121, user nikon, Advanced FTP password, root folder. Enable automatic upload / upload as taken on the camera.\n\n"+commonServer(),accessory);
    }
    private static Profile canon(String model, boolean grip){
        return new Profile("Canon",model,MODE_PHONE_HOTSPOT,grip,"Phone hotspot + FTP",
                "Turn on Android Mobile Hotspot and connect the camera to that hotspot.\n\n"+
                "Camera: Communication functions / Wireless features → Transfer images to FTP server. Create or add a connection, choose FTP, and enter the phone hotspot IP shown by this app. Set the port to 02121/2121, Passive mode Enable, login user nikon, and the Advanced FTP password. Choose Root folder.\n\n"+
                "Open FTP transfer settings and set Automatic transfer to Enable. Set Images to transfer to JPEG when shooting RAW+JPEG.\n\n"+commonServer(),"");
    }
    private static Profile canonAccessory(String model, boolean grip, String accessory){
        return new Profile("Canon",model,MODE_PHONE_HOTSPOT,grip,"Phone hotspot + wireless transmitter FTP",
                "Attach and enable the "+accessory+". Turn on Android Mobile Hotspot and connect the transmitter/camera to it.\n\n"+
                "Create an FTP transfer connection, use FTP, enter the phone hotspot IP shown by the app, port 2121, Passive mode Enable, user nikon, and the Advanced FTP password. Choose the root folder and enable automatic transfer after shooting.\n\n"+commonServer(),accessory);
    }
    private static Profile sony(String model, boolean grip){
        return new Profile("Sony",model,MODE_PHONE_HOTSPOT,grip,"Phone hotspot + FTP",
                "Turn on Android Mobile Hotspot and connect the camera to that hotspot using Network → Wi-Fi / Access Point Set.\n\n"+
                "Camera: Network → FTP Transfer → FTP Transfer Func. → Server Setting. Set Host Name to the phone hotspot IP shown by this app, Port 2121, Secure Protocol Off, user nikon, and the Advanced FTP password. Use Passive mode if offered.\n\n"+
                "Set FTP Function On, then Auto FTP Transfer / Auto Trans When Shot to On. For RAW+JPEG, choose JPEG as the transfer target.\n\n"+commonServer(),"");
    }
    private static Profile fuji(String model, boolean grip){
        return new Profile("Fujifilm",model,MODE_PHONE_HOTSPOT,grip,"Phone hotspot + FTP",
                "Turn on Android Mobile Hotspot and connect the camera to it.\n\n"+
                "Camera: Network/USB Setting → Create/Edit Connection Setting → Create Using Wizard → FTP Transfer → Wireless LAN. Create the wireless communication profile for the phone hotspot.\n\n"+
                "Create the FTP server profile: FTP server type FTP, address = phone hotspot IP shown by the app, port 2121, proxy Disable, PASV Enable, User Name & Password = nikon plus the Advanced FTP password, destination Root Folder.\n\n"+
                "Enable FTP Optional Setting → Auto Image Transfer Order so new photos are marked and sent automatically. Use JPEG for the Flickr workflow.\n\n"+commonServer(),"");
    }
    private static Profile fujiAccessory(String model, boolean grip, String accessory){
        return new Profile("Fujifilm",model,MODE_PHONE_HOTSPOT,grip,"FT-XH + phone hotspot + FTP",
                "Attach the "+accessory+" and make sure it has power. Turn on Android Mobile Hotspot and connect the file transmitter to it.\n\n"+
                "Camera: Network/USB Setting → Create/Edit Connection Setting → Create Using Wizard → FTP Transfer → Wireless LAN. Configure the hotspot, then create the FTP server profile using phone IP, port 2121, proxy Disable, PASV Enable, user nikon, and the Advanced FTP password.\n\n"+
                "Set destination to Root Folder and enable FTP Optional Setting → Auto Image Transfer Order.\n\n"+commonServer(),accessory);
    }

    public static final Profile[] PROFILES={
        nikon("Z9",true), nikon("Z8",false), nikon("Z6III",false), nikon("Z5II",false), nikon("Zf",false), nikon("Z50II",false), nikon("ZR",false),
        nikonAccessory("Z7II",false,"WT-7"), nikonAccessory("Z6II",false,"WT-7"), nikonAccessory("Z7",false,"WT-7"), nikonAccessory("Z6",false,"WT-7"),
        nikonAccessory("D850",false,"WT-7"), nikonAccessory("D6",true,"WT-6"),

        canon("EOS R1",true), canon("EOS R5 Mark II",false), canon("EOS R3",true), canon("EOS R5",false),
        canon("EOS R6 Mark III",false), canon("EOS R6 Mark II",false), canon("EOS R6 V",false), canon("EOS R6",false),
        canonAccessory("EOS-1D X Mark III",true,"WFT-E9"), canonAccessory("EOS-1D X Mark II",true,"WFT-E8"),

        sony("α7 V",false), sony("α1 II",false), sony("α1",false), sony("α9 III",false), sony("α9 II",false), sony("α9",false),
        sony("α7 IV",false), sony("α7 III",false), sony("α7S III",false), sony("α7R V",false), sony("α7R IV",false), sony("α7R IVA",false),
        sony("α7R III",false), sony("α7R IIIA",false), sony("α7C II",false), sony("α7CR",false), sony("α7C",false),
        sony("FX3",false), sony("FX30",false),

        fuji("GFX100 II",true), fuji("GFX100S II",false), fujiAccessory("X-H2",false,"FT-XH"), fujiAccessory("X-H2S",false,"FT-XH")
    };

    public static Profile selected(SharedPreferences p){
        String brand=p.getString(PREF_BRAND,"Nikon"),model=p.getString(PREF_MODEL,"Z8");
        for(Profile x:PROFILES)if(x.brand.equals(brand)&&x.model.equals(model))return x;
        return find("Nikon","Z8");
    }
    private static Profile find(String brand,String model){
        for(Profile x:PROFILES)if(x.brand.equals(brand)&&x.model.equals(model))return x;
        return PROFILES[0];
    }

    public static void ensureDefaults(SharedPreferences p){
        if(!p.contains(PREF_BRAND)||!p.contains(PREF_MODEL))p.edit().putString(PREF_BRAND,"Nikon").putString(PREF_MODEL,"Z8").apply();
    }

    public static String cameraName(SharedPreferences p){return selected(p).displayName();}
    public static boolean usesPhoneHotspot(SharedPreferences p){return MODE_PHONE_HOTSPOT.equals(selected(p).mode);}
    public static String networkLabel(SharedPreferences p){return usesPhoneHotspot(p)?"Phone Hotspot FTP":"Direct Wi-Fi FTP";}
    public static String waitingText(SharedPreferences p){
        Profile x=selected(p);
        String extra=x.accessory.isEmpty()?"":" "+x.accessory+" required.";
        return usesPhoneHotspot(p)?"Turn on Android Mobile Hotspot and connect the "+x.model+" to it."+extra:"Turn on the "+x.model+" FTP Wi-Fi access-point profile."+extra+" No outside Wi-Fi is required.";
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
        for(int i=0;i<PROFILES.length;i++){
            labels[i]=PROFILES[i].displayName()+(PROFILES[i].accessory.isEmpty()?"":"  •  "+PROFILES[i].accessory+" required");
            if(PROFILES[i].brand.equals(cur.brand)&&PROFILES[i].model.equals(cur.model))checked=i;
        }
        final int[] choice={checked};
        new AlertDialog.Builder(a).setTitle("Choose FTP camera").setSingleChoiceItems(labels,checked,(d,w)->choice[0]=w)
                .setNegativeButton("Cancel",null).setPositiveButton("Use camera",(d,w)->{
                    Profile x=PROFILES[choice[0]];p.edit().putString(PREF_BRAND,x.brand).putString(PREF_MODEL,x.model).apply();
                    if(redraw!=null)redraw.run();
                }).show();
    }

    public static void showSetup(Activity a, SharedPreferences p){
        Profile x=selected(p);String ip=findBestLocalIp();
        String accessory=x.accessory.isEmpty()?"":"\n\nHardware note: "+x.accessory+" is required.";
        String msg=x.setupSteps+accessory+"\n\nCurrent phone FTP address: "+ip+":2121";
        scrollDialog(a,x.displayName()+" — "+x.setupTitle,msg);
    }

    public static void showHelpFile(Activity a){
        try(InputStream in=a.getAssets().open("CAMERA_SETUP_HELP.txt")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
            scrollDialog(a,"Camera FTP Setup Help",out.toString(StandardCharsets.UTF_8.name()));
        }catch(Exception e){
            scrollDialog(a,"Camera FTP Setup Help","Help file could not be opened: "+e.getMessage());
        }
    }

    private static void scrollDialog(Activity a,String title,String text){
        ScrollView sv=new ScrollView(a);TextView tv=new TextView(a);tv.setText(text);tv.setTextSize(14);tv.setTextColor(Color.rgb(235,239,244));tv.setPadding(dp(a,20),dp(a,14),dp(a,20),dp(a,24));sv.addView(tv);
        AlertDialog d=new AlertDialog.Builder(a).setTitle(title).setView(sv).setPositiveButton("Close",null).create();
        d.setOnShowListener(x->{Window w=d.getWindow();if(w!=null)w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);});
        d.show();
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
            float cx=w/2f;
            boolean compact=profile.model.contains("7C")||profile.model.contains("7CR")||profile.model.contains("Z50");
            boolean cinema=profile.model.startsWith("FX");
            float bodyTop=compact?h*.27f:h*.22f,bodyBottom=cinema?h*.74f:h*.79f,bodyLeft=w*.13f,bodyRight=w*.87f;
            paint.setShadowLayer(18,0,10,Color.argb(120,0,0,0));paint.setColor(Color.rgb(25,27,30));
            RectF body=new RectF(bodyLeft,bodyTop,bodyRight,bodyBottom);c.drawRoundRect(body,26,26,paint);paint.clearShadowLayer();
            if(profile.integratedGrip){paint.setColor(Color.rgb(22,24,27));RectF grip=new RectF(w*.66f,h*.45f,w*.88f,h*.92f);c.drawRoundRect(grip,22,22,paint);}
            if(!compact&&!cinema){paint.setColor(Color.rgb(19,21,24));Path hump=new Path();hump.moveTo(w*.35f,bodyTop);hump.lineTo(w*.43f,h*.10f);hump.lineTo(w*.59f,h*.10f);hump.lineTo(w*.67f,bodyTop);hump.close();c.drawPath(hump,paint);}
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,w*.008f));paint.setColor(Color.rgb(72,76,82));c.drawRoundRect(body,26,26,paint);paint.setStyle(Paint.Style.FILL);
            float r=Math.min(w,h)*(cinema?.19f:.225f);paint.setColor(Color.rgb(8,10,12));c.drawCircle(cx,h*.49f,r,paint);paint.setColor(Color.rgb(55,60,66));c.drawCircle(cx,h*.49f,r*.86f,paint);paint.setColor(Color.rgb(5,9,12));c.drawCircle(cx,h*.49f,r*.69f,paint);paint.setColor(Color.rgb(9,27,38));c.drawCircle(cx,h*.49f,r*.48f,paint);paint.setColor(Color.rgb(24,62,79));c.drawCircle(cx-r*.12f,h*.45f,r*.25f,paint);paint.setColor(Color.rgb(6,11,15));c.drawCircle(cx,h*.49f,r*.19f,paint);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);paint.setColor(Color.WHITE);paint.setTextSize(Math.max(18,w*.055f));c.drawText(profile.brand,cx,h*.19f,paint);paint.setTextSize(Math.max(15,w*.044f));paint.setColor(Color.rgb(220,224,228));c.drawText(profile.model,cx,h*.88f,paint);
            paint.setColor(Color.rgb(160,165,171));c.drawRoundRect(new RectF(w*.18f,h*.27f,w*.29f,h*.34f),8,8,paint);paint.setColor(Color.rgb(48,52,57));c.drawCircle(w*.75f,h*.30f,w*.035f,paint);
        }
    }

    private static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    private MultiCameraSupport(){}
}
