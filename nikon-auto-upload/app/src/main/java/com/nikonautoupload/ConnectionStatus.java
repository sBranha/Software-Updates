package com.nikonautoupload;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Shared, brand-neutral status model used by USB, Wi-Fi/FTP, Bluetooth and
 * future camera connectors. Existing modules can keep writing their legacy
 * preference keys; new connectors can also publish generic status_* values.
 */
public final class ConnectionStatus {
    public static final int GREEN=0, YELLOW=1, RED=2;

    public static final class Report {
        public String cameraName="Camera";
        public String cameraState="Disconnected";
        public String connectionType="None";
        public String wifi="Not connected";
        public String ip="No IPv4 address detected";
        public String internet="Unavailable";
        public String cellular="Unavailable";
        public String transfer="Idle";
        public String transferSpeed="—";
        public String photoProgress="—";
        public String uploadDestination="Flickr";
        public int waiting=0,uploading=0,completed=0,failed=0;
        public int cameraColor=RED,internetColor=RED,transferColor=GREEN,uploadColor=GREEN;

        public String cameraHeadline(){return cameraName+" — "+connectionType+" "+cameraState;}
        public String internetHeadline(){return "Internet — "+internet;}
        public String transferHeadline(){return "Transfer — "+photoProgress+" • "+transfer;}
        public String uploadHeadline(){
            if(uploading>0)return uploadDestination+" — Uploading • "+waiting+" Remaining";
            if(failed>0)return uploadDestination+" — "+failed+" Failed • "+waiting+" Waiting";
            if(waiting>0)return uploadDestination+" — "+waiting+" Waiting";
            return uploadDestination+" — Queue Clear";
        }
    }

    private ConnectionStatus() {}

    public static Report build(Context context, SharedPreferences p){
        Report r=new Report();
        try{MultiCameraSupport.ensureDefaults(p);r.cameraName=MultiCameraSupport.cameraName(p);}catch(Exception ignored){}
        String usbName=p.getString("usb_camera_name","").trim();
        String usb=p.getString("usb_state","").toLowerCase(Locale.US);
        boolean usbConnected=usb.contains("connected")||usb.contains("ready")||usb.contains("import")||usb.contains("transfer")||usb.contains("complete");
        if(usbConnected&&!usbName.isEmpty())r.cameraName=usbName;

        String handoff=p.getString("nikon_handoff_state","").toLowerCase(Locale.US);
        String bt=p.getString("nikon_bt_state","").toLowerCase(Locale.US);
        String ftp=p.getString("ftp_state","").toLowerCase(Locale.US);
        boolean ftpConnected=ftp.contains("connected")||ftp.contains("logged")||ftp.contains("receiv")||ftp.contains("transfer")||ftp.contains("photo");
        boolean btConnected=bt.contains("connected")||bt.contains("paired");
        boolean handoffWifi=handoff.contains("wi-fi")||handoff.contains("ptp")||handoff.contains("ready for new")||handoff.contains("download");

        if(usbConnected){r.connectionType="USB-C";r.cameraState="Connected";r.cameraColor=GREEN;}
        else if(handoffWifi){r.connectionType="Wi-Fi";r.cameraState="Connected";r.cameraColor=GREEN;}
        else if(ftpConnected){
            boolean hotspot=false;try{hotspot=MultiCameraSupport.usesPhoneHotspot(p);}catch(Exception ignored){}
            r.connectionType=hotspot?"Hotspot / FTP":"Wi-Fi / FTP";r.cameraState="Connected";r.cameraColor=GREEN;
        }else if(btConnected){r.connectionType="Bluetooth";r.cameraState="Connected";r.cameraColor=YELLOW;}
        else {r.connectionType="None";r.cameraState="Disconnected";r.cameraColor=RED;}

        NetworkInfo net=networkInfo(context);
        r.wifi=net.wifiLabel;
        r.ip="USB-C".equals(r.connectionType)?"Not required for USB-C":(net.ipv4.isEmpty()?ipv4(context,p):net.ipv4);
        r.internet=net.internetAvailable?(net.cellularAvailable?"5G/LTE Connected":"Available"):"Unavailable";
        r.cellular=net.cellularAvailable?"Available / 5G-LTE":"Unavailable";
        if(net.internetAvailable)r.internetColor=GREEN;
        else if(usbConnected)r.internetColor=YELLOW;
        else r.internetColor=RED;

        String genericTransfer=p.getString("status_transfer_state","").trim();
        String usbTransfer=p.getString("usb_transfer_state","").trim();
        String last=p.getString("last_status","").toLowerCase(Locale.US);
        if(!genericTransfer.isEmpty())r.transfer=genericTransfer;
        else if(usbConnected&&!usbTransfer.isEmpty())r.transfer=usbTransfer;
        else if(ftp.contains("receiv")||last.contains("receiv")||last.contains("transfer"))r.transfer="Transferring";
        else if(ftp.contains("complete")||last.contains("complete")||last.contains("received"))r.transfer="Complete";
        else if(last.contains("failed")||last.contains("error"))r.transfer="Failed";
        else if(last.contains("retry"))r.transfer="Retrying";
        else r.transfer="Idle";
        String tl=r.transfer.toLowerCase(Locale.US);
        r.transferColor=(tl.contains("failed")||tl.contains("error"))?RED:(tl.contains("retry")?YELLOW:GREEN);

        long speed=p.getLong("status_transfer_speed_bps",0L);r.transferSpeed=speed>0?formatSpeed(speed):"—";
        int cur=p.getInt("status_photo_current",0),total=p.getInt("status_photo_total",0);
        r.photoProgress=(total>0?cur+"/"+total+" Photos":(p.getString("last_received_name","").isEmpty()?"Idle":"Last: "+p.getString("last_received_name","")));

        r.waiting=p.getStringSet("pending_uploads",Collections.emptySet()).size();
        r.failed=p.getStringSet("failed_upload_uris",Collections.emptySet()).size();
        r.uploading=p.getInt("status_uploading_count",0);
        r.completed=p.getInt("total_uploaded",0);
        r.uploadDestination=p.getBoolean("flickr_upload_enabled",true)?"Flickr":"Flickr Paused";
        if(!p.getBoolean("flickr_upload_enabled",true))r.uploadColor=YELLOW;
        else if(r.failed>0)r.uploadColor=RED;
        else if(r.waiting>0&&!net.internetAvailable)r.uploadColor=YELLOW;
        else r.uploadColor=GREEN;
        return r;
    }

    public static String cameraConnection(SharedPreferences p) {
        String usb = p.getString("usb_state", "").toLowerCase(Locale.US);
        if (usb.contains("connected") || usb.contains("import") || usb.contains("ready") || usb.contains("transfer") || usb.contains("complete")) return "USB-C Wired";
        String handoff = p.getString("nikon_handoff_state", "").toLowerCase(Locale.US);
        if (handoff.contains("wi-fi connected") || handoff.contains("ptp") || handoff.contains("ready for new") || handoff.contains("download")) return "Bluetooth → Camera Wi-Fi";
        String bt = p.getString("nikon_bt_state", "").toLowerCase(Locale.US);
        if (bt.contains("connected")) return "Nikon Bluetooth";
        String ftp = p.getString("ftp_state", "").toLowerCase(Locale.US);
        if (ftp.contains("connected") || ftp.contains("logged") || ftp.contains("receiv") || ftp.contains("transfer") || ftp.contains("photo")) return "Wi-Fi / FTP";
        return "Waiting for camera";
    }

    public static String ipv4(Context context, SharedPreferences p) {
        if ("USB-C Wired".equals(cameraConnection(p))) return "Not required for USB-C";
        List<String> values = ipv4Addresses(context);
        return values.isEmpty() ? "No IPv4 address detected" : String.join(" • ", values);
    }

    public static String networkSummary(Context context) {
        NetworkInfo info=networkInfo(context);StringBuilder b=new StringBuilder();
        if(!"Not connected".equals(info.wifiLabel))b.append(info.wifiLabel);
        if(info.cellularAvailable){if(b.length()>0)b.append(" • ");b.append("Cellular / 5G-LTE");}
        if(b.length()==0)b.append("No active network reported");
        return b.toString();
    }

    private static final class NetworkInfo{
        String wifiLabel="Not connected",ipv4="";boolean cellularAvailable=false,internetAvailable=false;
    }
    private static NetworkInfo networkInfo(Context context){
        NetworkInfo out=new NetworkInfo();
        try{
            ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return out;
            for(Network n:cm.getAllNetworks()){
                NetworkCapabilities c=cm.getNetworkCapabilities(n);LinkProperties lp=cm.getLinkProperties(n);if(c==null)continue;
                if(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))out.internetAvailable=true;
                if(c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))out.cellularAvailable=true;
                if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
                    String label="Wi-Fi";
                    try{Object t=c.getTransportInfo();if(t instanceof WifiInfo){String ssid=((WifiInfo)t).getSSID();if(ssid!=null&&!ssid.isEmpty()&&!"<unknown ssid>".equalsIgnoreCase(ssid))label+=" • "+stripQuotes(ssid);}}catch(Exception ignored){}
                    out.wifiLabel=label;
                    if(lp!=null&&out.ipv4.isEmpty())for(LinkAddress la:lp.getLinkAddresses()){InetAddress a=la.getAddress();if(a instanceof Inet4Address&&!a.isLoopbackAddress()){out.ipv4=a.getHostAddress();break;}}
                }
            }
        }catch(Exception ignored){}
        return out;
    }

    private static List<String> ipv4Addresses(Context context) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities c = cm.getNetworkCapabilities(n); LinkProperties lp = cm.getLinkProperties(n); if (lp == null) continue;
                    String kind = c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Cellular" : lp.getInterfaceName();
                    for (LinkAddress la : lp.getLinkAddresses()) {InetAddress a = la.getAddress();if (a instanceof Inet4Address && !a.isLoopbackAddress()) out.add(a.getHostAddress() + " (" + kind + ")");}
                }
            }
        } catch (Exception ignored) {}
        if (out.isEmpty()) {
            try {Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();if (en != null) for (NetworkInterface ni : Collections.list(en)) for (InetAddress a : Collections.list(ni.getInetAddresses())) if (a instanceof Inet4Address && !a.isLoopbackAddress()) out.add(a.getHostAddress() + " (" + ni.getName() + ")");} catch (Exception ignored) {}
        }
        return new ArrayList<>(out);
    }

    public static String formatSpeed(long bps){
        if(bps<=0)return "—";double v=bps;if(v<1024)return String.format(Locale.US,"%d B/s",bps);v/=1024d;if(v<1024)return String.format(Locale.US,"%.1f KB/s",v);v/=1024d;if(v<1024)return String.format(Locale.US,"%.1f MB/s",v);return String.format(Locale.US,"%.2f GB/s",v/1024d);
    }

    private static String stripQuotes(String s) {if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);return s == null ? "" : s;}
}
