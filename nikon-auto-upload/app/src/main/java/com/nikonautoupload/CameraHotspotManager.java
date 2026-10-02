package com.nikonautoupload;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Starts a LocalOnlyHotspot owned by this app for cameras that need to join a phone-created Wi-Fi network.
 * This avoids relying on OEM tethering-hotspot firewall behavior. The phone itself can continue using
 * cellular data for Flickr while the camera uses this local-only Wi-Fi link for FTP.
 */
public final class CameraHotspotManager {
    public interface Callback { void onChanged(String message); }

    private static WifiManager.LocalOnlyHotspotReservation reservation;
    private static boolean starting;

    public static boolean isRunning() { return reservation != null; }

    public static void start(Activity activity, SharedPreferences prefs, Callback callback) {
        if (isRunning()) {
            updateAddress(prefs);
            callback.onChanged("Camera hotspot already running");
            return;
        }
        if (starting) {
            callback.onChanged("Camera hotspot is starting");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, 93);
            callback.onChanged("Allow Nearby devices, then tap Start Camera Hotspot again");
            return;
        }
        if (Build.VERSION.SDK_INT <= 32 && activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 93);
            callback.onChanged("Allow Location for Wi-Fi hotspot setup, then tap again");
            return;
        }

        starting = true;
        WifiManager wm = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            wm.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                @Override public void onStarted(WifiManager.LocalOnlyHotspotReservation r) {
                    reservation = r;
                    starting = false;
                    String ssid = "", pass = "";
                    try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            SoftApConfiguration c = r.getSoftApConfiguration();
                            if (c != null) {
                                ssid = c.getSsid() == null ? "" : c.getSsid();
                                pass = c.getPassphrase() == null ? "" : c.getPassphrase();
                            }
                        } else {
                            WifiConfiguration c = r.getWifiConfiguration();
                            if (c != null) {
                                ssid = stripQuotes(c.SSID);
                                pass = stripQuotes(c.preSharedKey);
                            }
                        }
                    } catch (Exception ignored) {}
                    prefs.edit().putBoolean("camera_hotspot_running", true)
                            .putString("camera_hotspot_ssid", ssid)
                            .putString("camera_hotspot_password", pass).apply();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        updateAddress(prefs);
                        callback.onChanged("Camera hotspot ready");
                    }, 1200);
                }
                @Override public void onStopped() {
                    reservation = null;
                    starting = false;
                    prefs.edit().putBoolean("camera_hotspot_running", false).apply();
                    callback.onChanged("Camera hotspot stopped");
                }
                @Override public void onFailed(int reason) {
                    reservation = null;
                    starting = false;
                    prefs.edit().putBoolean("camera_hotspot_running", false)
                            .putString("camera_hotspot_error", failureText(reason)).apply();
                    callback.onChanged("Camera hotspot failed: " + failureText(reason));
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (SecurityException e) {
            starting = false;
            callback.onChanged("Camera hotspot permission denied");
        } catch (Exception e) {
            starting = false;
            callback.onChanged("Camera hotspot could not start: " + e.getMessage());
        }
    }

    public static void stop(SharedPreferences prefs, Callback callback) {
        try { if (reservation != null) reservation.close(); } catch (Exception ignored) {}
        reservation = null;
        starting = false;
        prefs.edit().putBoolean("camera_hotspot_running", false).apply();
        if (callback != null) callback.onChanged("Camera hotspot stopped");
    }

    public static void updateAddress(SharedPreferences prefs) {
        ArrayList<String> preferred = new ArrayList<>();
        ArrayList<String> other = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            while (all != null && all.hasMoreElements()) {
                NetworkInterface ni = all.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName().toLowerCase(Locale.US);
                if (name.contains("rmnet") || name.contains("pdp") || name.contains("ccmni") || name.contains("cell")) continue;
                Enumeration<InetAddress> aa = ni.getInetAddresses();
                while (aa.hasMoreElements()) {
                    InetAddress a = aa.nextElement();
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || !a.isSiteLocalAddress()) continue;
                    String value = a.getHostAddress();
                    if (name.contains("ap") || name.contains("swlan") || name.contains("softap") || name.contains("wlan") || name.contains("wifi")) preferred.add(value);
                    else other.add(value);
                }
            }
        } catch (Exception ignored) {}
        String ip = !preferred.isEmpty() ? preferred.get(0) : (!other.isEmpty() ? other.get(0) : "waiting for hotspot address");
        StringBuilder all = new StringBuilder();
        for (String s : preferred) { if (all.length() > 0) all.append(" • "); all.append(s); }
        for (String s : other) { if (all.length() > 0) all.append(" • "); all.append(s); }
        prefs.edit().putString("camera_hotspot_ip", ip).putString("camera_hotspot_all_ips", all.toString()).apply();
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length()-1);
        return s;
    }

    private static String failureText(int r) {
        if (r == WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE) return "turn off the regular Android Mobile Hotspot first";
        if (r == WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL) return "no Wi-Fi channel available";
        if (r == WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED) return "hotspot is not allowed by this phone/carrier";
        return "Android hotspot error " + r;
    }

    private CameraHotspotManager() {}
}
