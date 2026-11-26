package com.nikonautoupload;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standards-based FTP receiver for supported Nikon, Canon, and Sony cameras.
 *
 * 0.8.9 burst changes:
 *  - A failed or aborted data transfer no longer ends the whole camera session. The partial
 *    file is deleted, the camera gets "426", and it can resend on the same login.
 *  - Files are only handed to the app after the full data stream closed normally, so a
 *    partial file can never be treated as a complete photo.
 *  - REST with a non-zero offset is refused, so a resumed upload can never produce a file
 *    that is missing its beginning.
 *  - SIZE reports the real size of files stored this session (some cameras verify with it).
 *  - The per-command status chatter was removed; at burst rates it flooded the UI,
 *    notifications and SharedPreferences.
 *  - stop() now closes live camera sockets so a restart really frees port 2121.
 */
public class SimpleFtpServer {
    public interface Listener {
        void onPhotoReceived(File file, String originalName);
        void onStatus(String text);
        /** Number of camera control connections currently open. */
        default void onSessionCountChanged(int activeSessions) {}
    }

    private static final int PASSIVE_MIN = 32768;
    private static final int PASSIVE_MAX = 61000;
    private static final int CONTROL_TIMEOUT_MS = 900_000;
    private static final int DATA_ACCEPT_TIMEOUT_MS = 120_000;
    private static final int DATA_READ_TIMEOUT_MS = 120_000;
    private static final int SOCKET_BUFFER = 1024 * 1024;

    private final int port;
    private final String username;
    private final String password;
    private final File tempDir;
    private final Listener listener;
    private volatile boolean running;
    private ServerSocket controlServer;
    private final ExecutorService clients = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "CameraFTP-Client");
        t.setPriority(Thread.NORM_PRIORITY + 1);
        return t;
    });
    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
    private final AtomicInteger sessions = new AtomicInteger();

    public SimpleFtpServer(int port, String username, String password, File tempDir, Listener listener) {
        this.port = port;
        this.username = username;
        this.password = password;
        this.tempDir = tempDir;
        this.listener = listener;
    }

    public void start() throws IOException {
        if (running) return;
        if (!tempDir.exists() && !tempDir.mkdirs()) throw new IOException("Could not create transfer folder");
        controlServer = new ServerSocket();
        controlServer.setReuseAddress(true);
        controlServer.bind(new InetSocketAddress((InetAddress) null, port), 8);
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "CameraFTP-Accept");
        acceptThread.start();
        listener.onStatus("FTP port " + port + " listening — waiting for camera");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = controlServer.accept();
                s.setSoTimeout(CONTROL_TIMEOUT_MS);
                s.setTcpNoDelay(true);
                s.setKeepAlive(true);
                openSockets.add(s);
                listener.onStatus("Camera TCP connected from " + s.getInetAddress().getHostAddress());
                clients.submit(() -> handleClient(s));
            } catch (IOException e) {
                if (running) {
                    listener.onStatus("FTP receiver error: " + e.getMessage());
                    try { Thread.sleep(250); } catch (InterruptedException ie) { return; }
                }
            } catch (RejectedExecutionException e) {
                return;
            }
        }
    }

    private void handleClient(Socket socket) {
        listener.onSessionCountChanged(sessions.incrementAndGet());
        ServerSocket dataServer = null;
        InetSocketAddress activeTarget = null;
        Map<String, Long> storedSizes = new HashMap<>();
        long restOffset = 0;
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {

            reply(out, "220 Camera Auto Upload FTP ready");
            boolean loggedIn = false;
            String pendingUser = "";
            String line;
            while (running && (line = in.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                int sp = trimmed.indexOf(' ');
                String cmd = (sp < 0 ? trimmed : trimmed.substring(0, sp)).toUpperCase(Locale.US);
                String arg = sp < 0 ? "" : trimmed.substring(sp + 1).trim();

                switch (cmd) {
                    case "USER":
                        pendingUser = arg;
                        loggedIn = false;
                        reply(out, "331 Password required");
                        break;
                    case "PASS":
                        loggedIn = username.equals(pendingUser) && password.equals(arg);
                        reply(out, loggedIn ? "230 Logged in" : "530 Login incorrect");
                        listener.onStatus(loggedIn ? "Camera FTP login accepted" : "Camera FTP login rejected");
                        break;
                    case "SYST": reply(out, "215 UNIX Type: L8"); break;
                    case "FEAT":
                        raw(out, "211-Features\r\n UTF8\r\n EPSV\r\n SIZE\r\n211 End\r\n");
                        break;
                    case "OPTS": reply(out, "200 OPTS accepted"); break;
                    case "CLNT": reply(out, "200 Client noted"); break;
                    case "PWD": case "XPWD": reply(out, "257 \"/\" is current directory"); break;
                    case "CWD": case "XCWD": reply(out, loggedIn ? "250 Directory changed" : "530 Login first"); break;
                    case "CDUP": case "XCUP": reply(out, "250 Directory changed"); break;
                    case "MKD": case "XMKD": reply(out, "257 Directory created"); break;
                    case "TYPE": reply(out, "200 Type set"); break;
                    case "MODE": reply(out, "S".equalsIgnoreCase(arg) || arg.isEmpty() ? "200 Mode S" : "504 Only stream mode supported"); break;
                    case "STRU": reply(out, "200 Structure F"); break;
                    case "NOOP": reply(out, "200 OK"); break;
                    case "ALLO": reply(out, "202 No storage allocation necessary"); break;
                    case "REST": {
                        long offset;
                        try { offset = Long.parseLong(arg); } catch (Exception e) { offset = -1; }
                        if (offset == 0) { restOffset = 0; reply(out, "350 Restart position accepted"); }
                        else { restOffset = 0; reply(out, "504 Resume is not supported; send the whole file"); }
                        break;
                    }
                    case "PASV": {
                        closeQuietly(dataServer);
                        dataServer = null;
                        activeTarget = null;
                        byte[] a = s.getLocalAddress().getAddress();
                        if (a.length != 4) { reply(out, "425 IPv4 required"); break; }
                        dataServer = openPassiveServer(s.getLocalAddress());
                        int p = dataServer.getLocalPort();
                        String host = (a[0]&255)+","+(a[1]&255)+","+(a[2]&255)+","+(a[3]&255);
                        reply(out, "227 Entering Passive Mode ("+host+","+(p/256)+","+(p%256)+")");
                        break;
                    }
                    case "EPSV": {
                        closeQuietly(dataServer);
                        activeTarget = null;
                        dataServer = openPassiveServer(s.getLocalAddress());
                        reply(out, "229 Entering Extended Passive Mode (|||"+dataServer.getLocalPort()+"|)");
                        break;
                    }
                    case "PORT": {
                        closeQuietly(dataServer); dataServer = null;
                        activeTarget = parsePort(arg);
                        reply(out, activeTarget == null ? "501 Bad PORT command" : "200 PORT accepted");
                        break;
                    }
                    case "EPRT": {
                        closeQuietly(dataServer); dataServer = null;
                        activeTarget = parseEprt(arg);
                        reply(out, activeTarget == null ? "501 Bad EPRT command" : "200 EPRT accepted");
                        break;
                    }
                    case "LIST": case "NLST": case "MLSD": {
                        if (!loggedIn) { reply(out, "530 Login first"); break; }
                        if (dataServer == null && activeTarget == null) { reply(out, "425 Use PASV/EPSV or PORT/EPRT first"); break; }
                        reply(out, "150 Opening data connection");
                        try (Socket data = openDataSocket(dataServer, activeTarget);
                             OutputStream dout = data.getOutputStream()) {
                            if ("LIST".equals(cmd)) dout.write("drwxr-xr-x 1 nikon nikon 0 Jan 01 00:00 .\r\n".getBytes(StandardCharsets.UTF_8));
                            reply(out, "226 Transfer complete");
                        } catch (IOException e) {
                            reply(out, "425 Could not open data connection");
                        } finally {
                            closeQuietly(dataServer); dataServer = null; activeTarget = null;
                        }
                        break;
                    }
                    case "APPE":
                        // Appending to a file we already handed to the app would create a corrupt photo.
                        reply(out, "502 APPE not supported; use STOR");
                        break;
                    case "STOR": case "STOU": {
                        if (!loggedIn) { reply(out, "530 Login first"); break; }
                        if (dataServer == null && activeTarget == null) { reply(out, "425 Use PASV/EPSV or PORT/EPRT first"); break; }
                        String safeName = sanitize(arg);
                        if (safeName.isEmpty()) safeName = "DSC_" + System.currentTimeMillis() + ".JPG";
                        String uniqueId = UUID.randomUUID().toString();
                        File part = new File(tempDir, uniqueId + "__" + safeName + ".part");
                        File done = new File(tempDir, uniqueId + "__" + safeName + ".ready");
                        reply(out, "150 Opening binary data connection");
                        long bytes = 0;
                        boolean ok = false;
                        String failure = "";
                        try (Socket data = openDataSocket(dataServer, activeTarget);
                             InputStream din = data.getInputStream();
                             FileOutputStream fout = new FileOutputStream(part)) {
                            byte[] buf = new byte[256 * 1024];
                            int n;
                            while ((n = din.read(buf)) != -1) { fout.write(buf, 0, n); bytes += n; }
                            ok = true;
                        } catch (IOException e) {
                            failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        } finally {
                            closeQuietly(dataServer); dataServer = null; activeTarget = null; restOffset = 0;
                        }
                        if (ok && bytes == 0) {
                            // Accept (some cameras send an empty test file) but never import an empty photo.
                            //noinspection ResultOfMethodCallIgnored
                            part.delete();
                            reply(out, "226 Transfer complete");
                            break;
                        }
                        if (ok && !part.renameTo(done)) { ok = false; failure = "could not finalize file"; }
                        if (!ok) {
                            //noinspection ResultOfMethodCallIgnored
                            part.delete();
                            reply(out, "426 Transfer aborted; please resend");
                            listener.onStatus("Camera transfer incomplete, waiting for resend: " + safeName + " (" + failure + ")");
                            break;
                        }
                        storedSizes.put(safeName.toLowerCase(Locale.US), bytes);
                        reply(out, "226 Transfer complete");
                        listener.onPhotoReceived(done, safeName);
                        break;
                    }
                    case "SIZE": {
                        Long size = storedSizes.get(sanitize(arg).toLowerCase(Locale.US));
                        reply(out, size == null ? "550 File not found" : "213 " + size);
                        break;
                    }
                    case "MDTM": reply(out, "550 File not found"); break;
                    case "DELE": reply(out, "250 Deleted"); break;
                    case "RNFR": reply(out, "350 Ready for RNTO"); break;
                    case "RNTO": reply(out, "250 Rename successful"); break;
                    case "ABOR": reply(out, "226 No transfer in progress"); break;
                    case "QUIT": reply(out, "221 Goodbye"); return;
                    default: reply(out, "200 OK"); break;
                }
            }
        } catch (Exception e) {
            if (running) listener.onStatus("Camera FTP session ended: " + e.getMessage());
        } finally {
            closeQuietly(dataServer);
            openSockets.remove(socket);
            int left = sessions.decrementAndGet();
            listener.onSessionCountChanged(Math.max(0, left));
            if (running && left <= 0) listener.onStatus("Camera FTP session ended — waiting for reconnection");
        }
    }

    private ServerSocket openPassiveServer(InetAddress bindAddress) throws IOException {
        int span = PASSIVE_MAX - PASSIVE_MIN + 1;
        int start = (int)((System.nanoTime() & 0x7fffffff) % span);
        IOException last = null;
        for (int i = 0; i < Math.min(span, 512); i++) {
            int p = PASSIVE_MIN + ((start + i) % span);
            ServerSocket ss = new ServerSocket();
            try {
                ss.setReuseAddress(true);
                ss.setReceiveBufferSize(SOCKET_BUFFER);
                ss.bind(new InetSocketAddress(bindAddress, p), 1);
                ss.setSoTimeout(DATA_ACCEPT_TIMEOUT_MS);
                return ss;
            } catch (IOException e) {
                last = e;
                closeQuietly(ss);
            }
        }
        throw new IOException("No passive FTP port available in 32768-61000", last);
    }

    private static Socket openDataSocket(ServerSocket passive, InetSocketAddress active) throws IOException {
        Socket s;
        if (passive != null) s = passive.accept();
        else if (active != null) {
            s = new Socket();
            s.setReceiveBufferSize(SOCKET_BUFFER);
            s.connect(active, 30000);
        } else throw new IOException("No FTP data connection configured");
        s.setSoTimeout(DATA_READ_TIMEOUT_MS);
        return s;
    }

    private static InetSocketAddress parsePort(String arg) {
        try {
            String[] p = arg.split(",");
            if (p.length != 6) return null;
            String host = p[0].trim()+"."+p[1].trim()+"."+p[2].trim()+"."+p[3].trim();
            int port = Integer.parseInt(p[4].trim()) * 256 + Integer.parseInt(p[5].trim());
            return new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (Exception e) { return null; }
    }

    private static InetSocketAddress parseEprt(String arg) {
        try {
            if (arg == null || arg.length() < 5) return null;
            char d = arg.charAt(0);
            String[] parts = arg.substring(1).split(java.util.regex.Pattern.quote(String.valueOf(d)));
            if (parts.length < 3) return null;
            return new InetSocketAddress(InetAddress.getByName(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception e) { return null; }
    }

    static String sanitize(String s) {
        if (s == null) return "";
        s = s.replace('\\','/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void reply(BufferedWriter out, String s) throws IOException { raw(out, s + "\r\n"); }
    private static void raw(BufferedWriter out, String s) throws IOException { out.write(s); out.flush(); }
    private static void closeQuietly(ServerSocket s) { if (s != null) try { s.close(); } catch (Exception ignored) {} }

    public void stop() {
        running = false;
        closeQuietly(controlServer);
        for (Socket s : openSockets) { try { s.close(); } catch (Exception ignored) {} }
        openSockets.clear();
        clients.shutdownNow();
    }
}
