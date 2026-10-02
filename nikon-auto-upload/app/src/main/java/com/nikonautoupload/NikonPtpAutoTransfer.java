package com.nikonautoupload;

import android.content.*;
import android.net.*;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Experimental Nikon Bluetooth -> camera Wi-Fi -> PTP/IP photo handoff.
 *
 * This implementation uses standard Android Wi-Fi APIs and ISO PTP/IP packet
 * framing. It is deliberately diagnostic-heavy because camera firmware varies.
 */
public final class NikonPtpAutoTransfer {
    public interface Listener {
        void onLog(String message);
        void onState(String message, boolean error);
        void onWifiReady(Network network, String cameraIp);
    }

    private static final int PTP_PORT = 15740;
    private static final int TYPE_INIT_COMMAND = 0x0001;
    private static final int TYPE_INIT_RESPONSE = 0x0002;
    private static final int TYPE_INIT_EVENT_REQUEST = 0x0003;
    private static final int TYPE_INIT_EVENT_RESPONSE = 0x0004;
    private static final int TYPE_INIT_FAIL = 0x0005;
    private static final int TYPE_COMMAND_REQUEST = 0x0006;
    private static final int TYPE_COMMAND_RESPONSE = 0x0007;
    private static final int TYPE_START_DATA = 0x0009;
    private static final int TYPE_DATA_PACKET = 0x000A;
    private static final int TYPE_END_DATA = 0x000C;
    private static final int TYPE_PING = 0x000D;
    private static final int TYPE_PONG = 0x000E;

    private static final int OP_OPEN_SESSION = 0x1002;
    private static final int OP_GET_OBJECT_HANDLES = 0x1007;
    private static final int OP_GET_OBJECT_INFO = 0x1008;
    private static final int OP_GET_OBJECT = 0x1009;
    private static final int RESPONSE_OK = 0x2001;
    private static final int FORMAT_EXIF_JPEG = 0x3801;

    private final Context app;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final ConnectivityManager cm;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ConnectivityManager.NetworkCallback networkCallback;
    private volatile Network network;
    private volatile Socket commandSocket;
    private volatile Socket eventSocket;
    private volatile InputStream commandIn;
    private volatile OutputStream commandOut;
    private volatile boolean ptpStarted;
    private volatile boolean baselineReady;
    private final Set<Integer> baselineHandles = Collections.synchronizedSet(new HashSet<>());
    private int transactionId = 1;

    public NikonPtpAutoTransfer(Context context, SharedPreferences prefs, Listener listener) {
        this.app = context.getApplicationContext();
        this.prefs = prefs;
        this.listener = listener;
        this.cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public boolean isRunning() { return running.get(); }
    public boolean isPtpStarted() { return ptpStarted; }

    public synchronized void startWifi(String ssid, String password, String hintedIp) {
        stopNetworkOnly();
        if (ssid == null || ssid.trim().isEmpty()) {
            state("Camera did not provide a Wi-Fi name", true);
            return;
        }
        running.set(true);
        ptpStarted = false;
        baselineReady = false;
        baselineHandles.clear();
        prefs.edit()
                .putString("nikon_handoff_state", "Joining camera Wi-Fi")
                .putString("nikon_handoff_ssid", ssid)
                .putString("nikon_handoff_ip_hint", hintedIp == null ? "" : hintedIp)
                .apply();

        try {
            WifiNetworkSpecifier.Builder sb = new WifiNetworkSpecifier.Builder().setSsid(ssid);
            if (password != null && !password.isEmpty()) sb.setWpa2Passphrase(password);
            WifiNetworkSpecifier specifier = sb.build();
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) {
                    network = n;
                    String resolved = chooseCameraIp(n, hintedIp);
                    prefs.edit().putString("nikon_handoff_state", "Camera Wi-Fi connected")
                            .putString("nikon_handoff_camera_ip", resolved).apply();
                    log("Camera Wi-Fi connected. PTP candidate: " + resolved + ":" + PTP_PORT);
                    state("Camera Wi-Fi connected — starting Nikon photo link", false);
                    listener.onWifiReady(n, resolved);
                }

                @Override public void onLost(Network n) {
                    if (n.equals(network)) {
                        network = null;
                        closeSockets();
                        ptpStarted = false;
                        prefs.edit().putString("nikon_handoff_state", "Camera Wi-Fi lost").apply();
                        log("Camera Wi-Fi network was lost.");
                        state("Camera Wi-Fi disconnected — Bluetooth can reconnect it", true);
                    }
                }

                @Override public void onUnavailable() {
                    prefs.edit().putString("nikon_handoff_state", "Camera Wi-Fi unavailable").apply();
                    log("Android could not join Nikon camera Wi-Fi.");
                    state("Could not join camera Wi-Fi", true);
                }
            };
            cm.requestNetwork(request, networkCallback, 30000);
            log("Android camera-Wi-Fi request started for SSID: " + ssid);
            state("Joining Nikon camera Wi-Fi…", false);
        } catch (Exception e) {
            log("Wi-Fi request failed: " + shortMsg(e));
            state("Wi-Fi handoff failed: " + shortMsg(e), true);
        }
    }

    /** Call after Bluetooth characteristic 0x2005 has been written. */
    public void startPtp(Network n, String hintedIp) {
        if (n == null || !running.get() || ptpStarted) return;
        ptpStarted = true;
        io.submit(() -> {
            try {
                Thread.sleep(700);
                connectAndPoll(n, hintedIp);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                ptpStarted = false;
                prefs.edit().putString("nikon_handoff_state", "PTP error: " + shortMsg(e)).apply();
                log("PTP/IP error: " + shortMsg(e));
                state("Nikon Wi-Fi connected, but photo link failed", true);
            }
        });
    }

    public void kickPoll() {
        if (running.get()) log("Nikon Bluetooth file event — checking camera for new JPEG.");
    }

    public synchronized void stop() {
        running.set(false);
        ptpStarted = false;
        baselineReady = false;
        closeSockets();
        stopNetworkOnly();
        io.shutdownNow();
    }

    private synchronized void stopNetworkOnly() {
        if (networkCallback != null && cm != null) {
            try { cm.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        networkCallback = null;
        network = null;
    }

    private void connectAndPoll(Network n, String hintedIp) throws Exception {
        List<String> candidates = cameraIpCandidates(n, hintedIp);
        Exception last = null;
        for (String host : candidates) {
            if (!running.get()) return;
            try {
                log("Trying Nikon PTP/IP at " + host + ":" + PTP_PORT);
                openPtp(n, host);
                prefs.edit().putString("nikon_handoff_camera_ip", host)
                        .putString("nikon_handoff_state", "PTP connected").apply();
                state("Nikon photo link connected — checking card", false);
                initializeBaseline();
                pollLoop();
                return;
            } catch (Exception e) {
                last = e;
                log("PTP candidate " + host + " failed: " + shortMsg(e));
                closeSockets();
            }
        }
        if (last != null) throw last;
        throw new IOException("No usable Nikon camera IP address");
    }

    private void openPtp(Network n, String host) throws Exception {
        commandSocket = n.getSocketFactory().createSocket();
        commandSocket.connect(new InetSocketAddress(host, PTP_PORT), 8000);
        commandSocket.setSoTimeout(15000);
        commandIn = new BufferedInputStream(commandSocket.getInputStream());
        commandOut = new BufferedOutputStream(commandSocket.getOutputStream());
        InputStream cin = commandIn;
        OutputStream cout = commandOut;

        byte[] guid = persistentGuid();
        byte[] name = "Nikon Auto Upload".getBytes(StandardCharsets.UTF_16LE);
        int initSize = 8 + 16 + name.length + 2 + 4;
        ByteBuffer init = ByteBuffer.allocate(initSize).order(ByteOrder.LITTLE_ENDIAN);
        init.putInt(initSize).putInt(TYPE_INIT_COMMAND).put(guid).put(name).putShort((short)0);
        init.putShort((short)0).putShort((short)1);
        cout.write(init.array()); cout.flush();

        Packet ack = readSmallPacket(cin, 1024 * 1024);
        if (ack.type == TYPE_INIT_FAIL) {
            int reason = ack.payload.length >= 4 ? leInt(ack.payload, 0) : -1;
            throw new IOException("Camera rejected PTP/IP init (reason " + reason + ")");
        }
        if (ack.type != TYPE_INIT_RESPONSE || ack.payload.length < 4) {
            throw new IOException("Unexpected PTP init response type " + ack.type);
        }
        int connectionNumber = leInt(ack.payload, 0);
        log("PTP/IP command channel accepted; connection #" + connectionNumber);

        eventSocket = n.getSocketFactory().createSocket();
        eventSocket.connect(new InetSocketAddress(host, PTP_PORT), 8000);
        eventSocket.setSoTimeout(10000);
        OutputStream eout = new BufferedOutputStream(eventSocket.getOutputStream());
        InputStream ein = new BufferedInputStream(eventSocket.getInputStream());
        ByteBuffer ev = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        ev.putInt(12).putInt(TYPE_INIT_EVENT_REQUEST).putInt(connectionNumber);
        eout.write(ev.array()); eout.flush();
        Packet eack = readSmallPacket(ein, 4096);
        if (eack.type != TYPE_INIT_EVENT_RESPONSE) {
            throw new IOException("PTP event channel rejected (type " + eack.type + ")");
        }
        log("PTP/IP event channel accepted.");

        transactionId = 1;
        int response = executeNoData(OP_OPEN_SESSION, 1);
        if (response != RESPONSE_OK && response != 0x201E) {
            throw new IOException("OpenSession response 0x" + Integer.toHexString(response));
        }
        log("PTP session open.");
    }

    private void initializeBaseline() throws Exception {
        Set<Integer> handles = getHandles();
        baselineHandles.clear();
        baselineHandles.addAll(handles);
        baselineReady = true;
        prefs.edit().putInt("nikon_handoff_baseline_count", handles.size())
                .putString("nikon_handoff_state", "Ready for new photos").apply();
        log("Camera baseline loaded: " + handles.size() + " existing objects. Existing card photos will NOT be downloaded.");
        state("READY — take a picture", false);
    }

    private void pollLoop() throws Exception {
        while (running.get() && network != null && commandSocket != null && !commandSocket.isClosed()) {
            Thread.sleep(2500);
            Set<Integer> now;
            try {
                now = getHandles();
            } catch (SocketTimeoutException timeout) {
                log("PTP poll timed out; retrying.");
                continue;
            }
            if (!baselineReady) {
                baselineHandles.addAll(now);
                baselineReady = true;
                continue;
            }
            List<Integer> added = new ArrayList<>();
            for (Integer h : now) if (!baselineHandles.contains(h)) added.add(h);
            Collections.sort(added, Comparator.comparingLong(Integer::toUnsignedLong));
            for (Integer handle : added) {
                baselineHandles.add(handle);
                if (!running.get()) return;
                try { processNewHandle(handle); }
                catch (Exception e) { log("New object 0x" + Integer.toHexString(handle) + " failed: " + shortMsg(e)); }
            }
            baselineHandles.retainAll(now);
            baselineHandles.addAll(now);
        }
    }

    private void processNewHandle(int handle) throws Exception {
        byte[] infoData = executeDataBytes(OP_GET_OBJECT_INFO, handle);
        ObjectInfo info = parseObjectInfo(infoData, handle);
        log("New camera object: " + info.name + " • format 0x" + Integer.toHexString(info.format) +
                " • " + info.size + " bytes");
        String lower = info.name.toLowerCase(Locale.US);
        if (info.format != FORMAT_EXIF_JPEG && !(lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
            log("Skipping non-JPEG object: " + info.name);
            return;
        }

        File dir = new File(app.getCacheDir(), "nikon_ptp");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create PTP cache");
        String safeName = safeFileName(info.name);
        if (safeName.isEmpty()) safeName = "DSC_" + Integer.toUnsignedString(handle) + ".JPG";
        File part = new File(dir, safeName + ".part");
        File done = new File(dir, safeName);
        if (part.exists()) part.delete();
        if (done.exists()) done.delete();

        prefs.edit().putString("nikon_handoff_state", "Downloading " + safeName).apply();
        state("Downloading " + safeName + " from Nikon…", false);
        log("Downloading full-resolution JPEG over Nikon Wi-Fi: " + safeName);
        executeDataToFile(OP_GET_OBJECT, part, handle);
        if (!part.renameTo(done)) {
            copyFile(part, done);
            part.delete();
        }
        log("Downloaded " + safeName + " (" + done.length() + " bytes). Sending into normal phone/Flickr pipeline.");
        importIntoApp(done, safeName);
        prefs.edit().putString("nikon_handoff_state", "Downloaded " + safeName)
                .putString("nikon_handoff_last_photo", safeName)
                .putLong("nikon_handoff_last_photo_time", System.currentTimeMillis()).apply();
        state("Photo received: " + safeName, false);
    }

    private void importIntoApp(File file, String name) {
        Intent i = new Intent(app, DirectTransferService.class);
        i.putExtra("command", "import_file");
        i.putExtra("file_path", file.getAbsolutePath());
        i.putExtra("file_name", name);
        if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i); else app.startService(i);
    }

    private Set<Integer> getHandles() throws Exception {
        byte[] data = executeDataBytes(OP_GET_OBJECT_HANDLES, 0xffffffff, 0, 0);
        if (data.length < 4) throw new IOException("GetObjectHandles returned no data");
        int count = leInt(data, 0);
        int available = Math.max(0, (data.length - 4) / 4);
        count = Math.min(count, available);
        Set<Integer> out = new HashSet<>(Math.max(16, count * 2));
        int off = 4;
        for (int i = 0; i < count; i++, off += 4) out.add(leInt(data, off));
        return out;
    }

    private int executeNoData(int operation, int... params) throws Exception {
        int tx = transactionId++;
        sendCommand(operation, tx, false, params);
        InputStream in = commandInput();
        while (running.get()) {
            Header h = readHeader(in);
            if (h.type == TYPE_COMMAND_RESPONSE) {
                byte[] payload = readExactly(in, h.length - 8);
                if (payload.length >= 6 && leInt(payload, 2) == tx) return leU16(payload, 0);
            } else if (h.type == TYPE_PING) {
                skipExactly(in, h.length - 8); sendPong();
            } else {
                skipExactly(in, h.length - 8);
            }
        }
        throw new IOException("PTP stopped");
    }

    private byte[] executeDataBytes(int operation, int... params) throws Exception {
        int tx = transactionId++;
        sendCommand(operation, tx, true, params);
        InputStream in = commandInput();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int response = -1;
        while (running.get()) {
            Header h = readHeader(in);
            int payloadLen = h.length - 8;
            if (payloadLen < 0) throw new IOException("Bad PTP packet length");
            if (h.type == TYPE_START_DATA) {
                skipExactly(in, payloadLen);
            } else if (h.type == TYPE_DATA_PACKET || h.type == TYPE_END_DATA) {
                if (payloadLen < 4) { skipExactly(in, payloadLen); continue; }
                byte[] txBytes = readExactly(in, 4);
                int packetTx = leInt(txBytes, 0);
                int body = payloadLen - 4;
                if (packetTx == tx) copyExactly(in, data, body); else skipExactly(in, body);
            } else if (h.type == TYPE_COMMAND_RESPONSE) {
                byte[] payload = readExactly(in, payloadLen);
                if (payload.length >= 6 && leInt(payload, 2) == tx) {
                    response = leU16(payload, 0);
                    break;
                }
            } else if (h.type == TYPE_PING) {
                skipExactly(in, payloadLen); sendPong();
            } else {
                skipExactly(in, payloadLen);
            }
        }
        if (response != RESPONSE_OK) throw new IOException("PTP operation 0x" +
                Integer.toHexString(operation) + " response 0x" + Integer.toHexString(response));
        return data.toByteArray();
    }

    private void executeDataToFile(int operation, File target, int... params) throws Exception {
        int tx = transactionId++;
        sendCommand(operation, tx, true, params);
        InputStream in = commandInput();
        int response = -1;
        try (OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(target))) {
            while (running.get()) {
                Header h = readHeader(in);
                int payloadLen = h.length - 8;
                if (payloadLen < 0) throw new IOException("Bad PTP packet length");
                if (h.type == TYPE_START_DATA) {
                    skipExactly(in, payloadLen);
                } else if (h.type == TYPE_DATA_PACKET || h.type == TYPE_END_DATA) {
                    if (payloadLen < 4) { skipExactly(in, payloadLen); continue; }
                    byte[] txBytes = readExactly(in, 4);
                    int packetTx = leInt(txBytes, 0);
                    int body = payloadLen - 4;
                    if (packetTx == tx) copyExactly(in, fileOut, body); else skipExactly(in, body);
                } else if (h.type == TYPE_COMMAND_RESPONSE) {
                    byte[] payload = readExactly(in, payloadLen);
                    if (payload.length >= 6 && leInt(payload, 2) == tx) {
                        response = leU16(payload, 0);
                        break;
                    }
                } else if (h.type == TYPE_PING) {
                    skipExactly(in, payloadLen); sendPong();
                } else {
                    skipExactly(in, payloadLen);
                }
            }
        }
        if (response != RESPONSE_OK) {
            target.delete();
            throw new IOException("GetObject response 0x" + Integer.toHexString(response));
        }
    }

    private void sendCommand(int operation, int tx, boolean dataIn, int... params) throws Exception {
        int size = 8 + 4 + 2 + 4 + params.length * 4;
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(size).putInt(TYPE_COMMAND_REQUEST);
        b.putInt(dataIn ? 2 : 1);
        b.putShort((short) operation).putInt(tx);
        for (int p : params) b.putInt(p);
        OutputStream out = commandOut;
        if (out == null) throw new IOException("PTP command output closed");
        out.write(b.array()); out.flush();
    }

    private void sendPong() {
        try {
            ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(8).putInt(TYPE_PONG);
            OutputStream out = commandOut;
            if (out != null) { out.write(b.array()); out.flush(); }
        } catch (Exception ignored) {}
    }

    private InputStream commandInput() throws IOException {
        if (commandSocket == null || commandIn == null) throw new IOException("PTP command socket closed");
        return commandIn;
    }

    private ObjectInfo parseObjectInfo(byte[] data, int handle) {
        int format = data.length >= 6 ? leU16(data, 4) : 0;
        long size = data.length >= 12 ? Integer.toUnsignedLong(leInt(data, 8)) : 0;
        String name = data.length > 52 ? readPtpString(data, 52) : "";
        if (name.isEmpty()) name = "DSC_" + Integer.toUnsignedString(handle) +
                (format == FORMAT_EXIF_JPEG ? ".JPG" : ".BIN");
        return new ObjectInfo(format, size, name);
    }

    private static String readPtpString(byte[] data, int offset) {
        if (offset >= data.length) return "";
        int count = data[offset] & 0xff;
        if (count <= 1) return "";
        int chars = count - 1;
        int bytes = Math.min(chars * 2, data.length - offset - 1);
        if (bytes <= 0) return "";
        return new String(data, offset + 1, bytes, StandardCharsets.UTF_16LE).replace("\u0000", "").trim();
    }

    private String chooseCameraIp(Network n, String hinted) {
        List<String> c = cameraIpCandidates(n, hinted);
        return c.isEmpty() ? (hinted == null ? "" : hinted) : c.get(0);
    }

    private List<String> cameraIpCandidates(Network n, String hinted) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (validIpv4(hinted)) out.add(hinted);
        try {
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) {
                for (RouteInfo route : lp.getRoutes()) {
                    InetAddress gw = route.getGateway();
                    if (gw instanceof Inet4Address) out.add(gw.getHostAddress());
                }
            }
        } catch (Exception ignored) {}
        out.add("192.168.1.1");
        out.add("192.168.0.1");
        out.remove("0.0.0.0");
        return new ArrayList<>(out);
    }

    private static boolean validIpv4(String s) {
        if (s == null || s.isEmpty() || "0.0.0.0".equals(s) || "255.255.255.255".equals(s)) return false;
        String[] p = s.split("\\.");
        if (p.length != 4) return false;
        try { for (String q : p) { int v = Integer.parseInt(q); if (v < 0 || v > 255) return false; } return true; }
        catch (Exception e) { return false; }
    }

    private byte[] persistentGuid() {
        String hex = prefs.getString("nikon_ptp_guid", "");
        if (hex.length() == 32) {
            try {
                byte[] b = new byte[16];
                for (int i=0;i<16;i++) b[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
                return b;
            } catch (Exception ignored) {}
        }
        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder(32);
        for (byte x : b) sb.append(String.format(Locale.US, "%02x", x & 0xff));
        prefs.edit().putString("nikon_ptp_guid", sb.toString()).apply();
        return b;
    }

    private static Packet readSmallPacket(InputStream in, int max) throws IOException {
        Header h = readHeader(in);
        int payload = h.length - 8;
        if (payload < 0 || payload > max) throw new IOException("PTP packet too large: " + h.length);
        return new Packet(h.type, readExactly(in, payload));
    }

    private static Header readHeader(InputStream in) throws IOException {
        byte[] h = readExactly(in, 8);
        int length = leInt(h, 0), type = leInt(h, 4);
        if (length < 8) throw new IOException("Invalid PTP/IP length " + length);
        return new Header(length, type);
    }

    private static byte[] readExactly(InputStream in, int len) throws IOException {
        byte[] b = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(b, off, len - off);
            if (n < 0) throw new EOFException("PTP/IP socket closed");
            off += n;
        }
        return b;
    }

    private static void copyExactly(InputStream in, OutputStream out, long len) throws IOException {
        byte[] b = new byte[128 * 1024];
        long left = len;
        while (left > 0) {
            int want = (int)Math.min(b.length, left);
            int n = in.read(b, 0, want);
            if (n < 0) throw new EOFException("PTP data ended early");
            out.write(b, 0, n);
            left -= n;
        }
    }

    private static void skipExactly(InputStream in, long len) throws IOException {
        byte[] b = new byte[32 * 1024];
        long left = len;
        while (left > 0) {
            int want = (int)Math.min(b.length, left);
            int n = in.read(b, 0, want);
            if (n < 0) throw new EOFException("PTP skip ended early");
            left -= n;
        }
    }

    private static int leInt(byte[] b, int o) {
        return (b[o]&255)|((b[o+1]&255)<<8)|((b[o+2]&255)<<16)|((b[o+3]&255)<<24);
    }
    private static int leU16(byte[] b, int o) { return (b[o]&255)|((b[o+1]&255)<<8); }

    private static String safeFileName(String s) {
        if (s == null) return "";
        s = s.replace('\\','/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void copyFile(File a, File b) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(a));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(b))) {
            byte[] buf = new byte[128 * 1024]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private synchronized void closeSockets() {
        try { if (commandSocket != null) commandSocket.close(); } catch (Exception ignored) {}
        try { if (eventSocket != null) eventSocket.close(); } catch (Exception ignored) {}
        commandSocket = null; eventSocket = null; commandIn = null; commandOut = null;
    }

    private void log(String s) { try { listener.onLog(s); } catch (Exception ignored) {} }
    private void state(String s, boolean error) { try { listener.onState(s, error); } catch (Exception ignored) {} }
    private static String shortMsg(Throwable e) {
        if (e == null) return "unknown";
        String s = e.getMessage();
        return (s == null || s.trim().isEmpty()) ? e.getClass().getSimpleName() : s;
    }

    private static final class Header { final int length,type; Header(int l,int t){length=l;type=t;} }
    private static final class Packet { final int type; final byte[] payload; Packet(int t,byte[] p){type=t;payload=p;} }
    private static final class ObjectInfo {
        final int format; final long size; final String name;
        ObjectInfo(int f,long s,String n){format=f;size=s;name=n;}
    }
}
