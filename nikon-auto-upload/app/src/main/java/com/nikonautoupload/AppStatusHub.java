package com.nikonautoupload;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * Shared status layer used by every camera connector. Existing Nikon/FTP/USB
 * state is read as a fallback, while future connectors can publish directly
 * through the report* methods below without changing the dashboard UI.
 */
public final class AppStatusHub {
    public static final int GREEN=0, YELLOW=1, RED=2;
    private AppStatusHub() {}

    public static final class Snapshot {
        public String cameraModel="Camera";
        public boolean cameraConnected;
        public String connectionType="Disconnected";
        public String network="No active network";
        public String ipAddress="—";
        public String internet="Unavailable";
        public boolean internetAvailable;
        public String cellular="Unavailable";
        public String transfer="Idle";
        public long transferSpeedBps;
        public int transferCurrent;
        public int transferTotal;
        public String transferPhoto="";
        public int waitingUploads;
        public int uploadingUploads;
        public int completedUploads;
        public int failedUploads;
        public String uploadDestination="Flickr";
        public String uploadState="Idle";
        public int overallColor=RED;
        public int cameraColor=RED;
        public int internetColor=RED;
        public int transferColor=YELLOW;
        public int uploadColor=YELLOW;
    }

    public static Snapshot snapshot(Context context, SharedPreferences p) {
        Snapshot s=new Snapshot();
        try{s.cameraModel=MultiCameraSupport.cameraName(p);}catch(Exception ignored){}
        String reportedModel=p.getString("status_camera_model","");if(!reportedModel.isEmpty())s.cameraModel=reportedModel;

        String connection=ConnectionStatus.cameraConnection(p);
        String reportedConnection=p.getString("status_connection_type","");
        if(!reportedConnection.isEmpty())connection=reportedConnection;
        s.connectionType=connection;
        s.cameraConnected=!connection.toLowerCase(Locale.US).contains("waiting")&&!connection.toLowerCase(Locale.US).contains("disconnected");
        s.cameraColor=s.cameraConnected?GREEN:RED;

        s.network=ConnectionStatus.networkSummary(context);
        s.ipAddress=ConnectionStatus.ipv4(context,p);
        s.internetAvailable=hasInternet(context);
        s.internet=s.internetAvailable?internetLabel(context):"Unavailable";
        s.internetColor=s.internetAvailable?GREEN:YELLOW;
        String cell=p.getString("cellular_state","");
        s.cellular=cell.isEmpty()?"Not requested":cell;

        String reportedTransfer=p.getString("status_transfer_state","");
        s.transfer=reportedTransfer.isEmpty()?inferTransferState(p,connection):reportedTransfer;
        s.transferSpeedBps=p.getLong("status_transfer_speed_bps",0L);
        s.transferCurrent=p.getInt("status_transfer_current",0);
        s.transferTotal=p.getInt("status_transfer_total",0);
        s.transferPhoto=p.getString("status_transfer_photo","");
        s.transferColor=colorForState(s.transfer,s.cameraConnected);

        Set<String> pending=p.getStringSet("pending_uploads", Collections.emptySet());
        Set<String> failed=p.getStringSet("failed_upload_uris", Collections.emptySet());
        s.waitingUploads=pending.size();
        s.failedUploads=failed.size();
        s.completedUploads=p.getInt("total_uploaded",0);
        String last=p.getString("last_status","").toLowerCase(Locale.US);
        s.uploadingUploads=p.getInt("status_uploading_count",0);
        if(s.uploadingUploads<=0&&last.contains("uploading"))s.uploadingUploads=1;
        s.uploadDestination=p.getBoolean("flickr_upload_enabled",true)?"Flickr":"Phone only";
        if(!p.getBoolean("flickr_upload_enabled",true))s.uploadState="Off — phone only";
        else if(p.getString("access_token","").isEmpty())s.uploadState="Flickr not connected";
        else if(s.uploadingUploads>0)s.uploadState="Uploading";
        else if(s.failedUploads>0&&!s.internetAvailable)s.uploadState="Queued — waiting for internet";
        else if(s.failedUploads>0)s.uploadState="Retrying / needs attention";
        else if(s.waitingUploads>0&&!s.internetAvailable)s.uploadState="Queued — waiting for internet";
        else if(s.waitingUploads>0)s.uploadState="Queued";
        else s.uploadState="Up to date";
        s.uploadColor=!p.getBoolean("flickr_upload_enabled",true)?YELLOW:(s.failedUploads>0&&s.internetAvailable?RED:(s.internetAvailable?GREEN:YELLOW));

        // Camera importing and internet uploading are intentionally independent.
        // A good USB connection stays green even if internet is down.
        if(s.cameraConnected){
            if(s.transferColor==RED)s.overallColor=RED;
            else if(!s.internetAvailable&&p.getBoolean("flickr_upload_enabled",true))s.overallColor=YELLOW;
            else s.overallColor=GREEN;
        }else s.overallColor=RED;
        return s;
    }

    public static void reportCamera(SharedPreferences p,String model,String type,boolean connected){
        SharedPreferences.Editor e=p.edit().putString("status_camera_model",model==null?"":model).putString("status_connection_type",connected?(type==null?"Connected":type):"Disconnected");e.apply();
    }
    public static void clearCameraReport(SharedPreferences p){p.edit().remove("status_camera_model").remove("status_connection_type").apply();}
    public static void reportTransfer(SharedPreferences p,String state,String photo,long speedBps,int current,int total){
        p.edit().putString("status_transfer_state",state==null?"":state).putString("status_transfer_photo",photo==null?"":photo).putLong("status_transfer_speed_bps",Math.max(0,speedBps)).putInt("status_transfer_current",Math.max(0,current)).putInt("status_transfer_total",Math.max(0,total)).apply();
    }
    public static void clearTransfer(SharedPreferences p){p.edit().remove("status_transfer_state").remove("status_transfer_photo").remove("status_transfer_speed_bps").remove("status_transfer_current").remove("status_transfer_total").apply();}

    private static String inferTransferState(SharedPreferences p,String connection){
        String usb=p.getString("usb_state","");
        if(connection.startsWith("USB-C")&&!usb.isEmpty())return normalize(usb);
        String handoff=p.getString("nikon_handoff_state","");
        if(connection.startsWith("Bluetooth →")&&!handoff.isEmpty())return normalize(handoff);
        String ftp=p.getString("ftp_state","");
        if(connection.contains("FTP")&&!ftp.isEmpty())return normalize(ftp);
        return "Idle";
    }

    private static String normalize(String raw){
        String x=raw==null?"":raw.toLowerCase(Locale.US);
        if(x.contains("fail")||x.contains("error")||x.contains("rejected"))return "Failed";
        if(x.contains("retry"))return "Retrying";
        if(x.contains("download")||x.contains("import")||x.contains("receiv")||x.contains("transfer"))return x.contains("complete")?"Complete":"Transferring";
        if(x.contains("complete")||x.contains("photo received"))return "Complete";
        if(x.contains("ready")||x.contains("connected")||x.contains("waiting"))return "Idle";
        return raw==null||raw.trim().isEmpty()?"Idle":raw;
    }

    private static int colorForState(String state,boolean connected){
        String x=state==null?"":state.toLowerCase(Locale.US);
        if(x.contains("fail")||x.contains("error")||x.contains("attention"))return RED;
        if(x.contains("retry")||(!connected&&x.contains("idle")))return YELLOW;
        if(x.contains("transf")||x.contains("complete")||x.contains("idle")||x.contains("ready"))return GREEN;
        return YELLOW;
    }

    private static boolean hasInternet(Context context){
        try{
            ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return false;
            for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))return true;}
        }catch(Exception ignored){}
        return false;
    }

    private static String internetLabel(Context context){
        try{
            ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return "Available";
            for(Network n:cm.getAllNetworks()){
                NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c==null||!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))continue;
                if(c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return "5G/LTE Connected";
                if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return "Wi-Fi Connected";
                if(c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return "Ethernet Connected";
            }
        }catch(Exception ignored){}
        return "Available";
    }
}
