package com.nikonautoupload;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small shared status helper. It does not own any connection. */
public final class ConnectionStatus {
    private ConnectionStatus() {}

    public static String cameraConnection(SharedPreferences p) {
        String usb = p.getString("usb_state", "").toLowerCase(Locale.US);
        if (usb.contains("connected") || usb.contains("import") || usb.contains("ready")) return "USB-C Wired";

        String handoff = p.getString("nikon_handoff_state", "").toLowerCase(Locale.US);
        if (handoff.contains("wi-fi connected") || handoff.contains("ptp") || handoff.contains("ready for new") || handoff.contains("download")) {
            return "Bluetooth → Camera Wi-Fi";
        }

        String bt = p.getString("nikon_bt_state", "").toLowerCase(Locale.US);
        if (bt.contains("connected")) return "Nikon Bluetooth";

        String ftp = p.getString("ftp_state", "").toLowerCase(Locale.US);
        if (ftp.contains("connected") || ftp.contains("logged") || ftp.contains("receiv") || ftp.contains("transfer") || ftp.contains("photo")) {
            return "Wi-Fi / FTP";
        }
        return "Waiting for camera";
    }

    public static String ipv4(Context context, SharedPreferences p) {
        if ("USB-C Wired".equals(cameraConnection(p))) return "Not required for USB-C";
        List<String> values = ipv4Addresses(context);
        return values.isEmpty() ? "No IPv4 address detected" : String.join(" • ", values);
    }

    public static String networkSummary(Context context) {
        Set<String> out = new LinkedHashSet<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network[] networks = cm.getAllNetworks();
                for (Network n : networks) {
                    NetworkCapabilities c = cm.getNetworkCapabilities(n);
                    if (c == null) continue;
                    if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        String label = "Wi-Fi";
                        if (Build.VERSION.SDK_INT >= 31) {
                            Object t = c.getTransportInfo();
                            if (t instanceof WifiInfo) {
                                String ssid = ((WifiInfo) t).getSSID();
                                if (ssid != null && !ssid.isEmpty() && !"<unknown ssid>".equalsIgnoreCase(ssid)) {
                                    label += ": " + stripQuotes(ssid);
                                }
                            }
                        }
                        out.add(label);
                    }
                    if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) out.add("Cellular / 5G-LTE");
                    if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) out.add("Ethernet");
                    if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) out.add("VPN");
                }
            }
        } catch (Exception ignored) {}
        return out.isEmpty() ? "No active network reported" : String.join(" • ", out);
    }

    private static List<String> ipv4Addresses(Context context) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities c = cm.getNetworkCapabilities(n);
                    LinkProperties lp = cm.getLinkProperties(n);
                    if (lp == null) continue;
                    String kind = c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" :
                            c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Cellular" : lp.getInterfaceName();
                    for (LinkAddress la : lp.getLinkAddresses()) {
                        InetAddress a = la.getAddress();
                        if (a instanceof Inet4Address && !a.isLoopbackAddress()) out.add(a.getHostAddress() + " (" + kind + ")");
                    }
                }
            }
        } catch (Exception ignored) {}

        if (out.isEmpty()) {
            try {
                Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                if (en != null) {
                    for (NetworkInterface ni : Collections.list(en)) {
                        for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                            if (a instanceof Inet4Address && !a.isLoopbackAddress()) out.add(a.getHostAddress() + " (" + ni.getName() + ")");
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(out);
    }

    private static String stripQuotes(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s == null ? "" : s;
    }
}
