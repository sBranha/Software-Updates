package com.nikonautoupload;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;

/** FTP receiver tuned for Nikon Z8 direct Wi-Fi transfers. */
public class SimpleFtpServer {
    public interface Listener {
        void onPhotoReceived(File file, String originalName);
        void onStatus(String text);
    }

    private static final int PASSIVE_MIN = 32768;
    private static final int PASSIVE_MAX = 61000;

    private final int port;
    private final String username;
    private final String password;
    private final File tempDir;
    private final Listener listener;
    private volatile boolean running;
    private ServerSocket controlServer;
    private final ExecutorService clients = Executors.newCachedThreadPool();

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
        controlServer.bind(new InetSocketAddress((InetAddress) null, port), 4);
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "NikonFTP-Accept");
        acceptThread.start();
        listener.onStatus("FTP port " + port + " listening — waiting for Z8 TCP connection");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = controlServer.accept();
                s.setSoTimeout(120000);
                s.setTcpNoDelay(true);
                listener.onStatus("Z8 TCP connected from " + s.getInetAddress().getHostAddress());
                clients.submit(() -> handleClient(s));
            } catch (IOException e) {
                if (running) listener.onStatus("FTP receiver error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        ServerSocket dataServer = null;
        InetSocketAddress activeTarget = null;
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {

            reply(out, "220 Nikon Auto Upload FTP ready");
            boolean loggedIn = false;
            String pendingUser = "";
            String line;
            while (running && (line = in.readLine()) != null) {
                String trimmed = line.trim();
                int sp = trimmed.indexOf(' ');
                String cmd = (sp < 0 ? trimmed : trimmed.substring(0, sp)).toUpperCase(Locale.US);
                String arg = sp < 0 ? "" : trimmed.substring(sp + 1).trim();
                if (!"PASS".equals(cmd)) listener.onStatus("Z8 FTP command: " + cmd);

                switch (cmd) {
                    case "USER":
                        pendingUser = arg;
                        reply(out, "331 Password required");
                        break;
                    case "PASS":
                        loggedIn = username.equals(pendingUser) && password.equals(arg);
                        reply(out, loggedIn ? "230 Logged in" : "530 Login incorrect");
                        listener.onStatus(loggedIn ? "Z8 FTP login accepted" : "Z8 FTP login rejected");
                        break;
                    case "SYST": reply(out, "215 UNIX Type: L8"); break;
                    case "FEAT":
                        raw(out, "211-Features\r\n UTF8\r\n EPSV\r\n SIZE\r\n MDTM\r\n211 End\r\n");
                        break;
                    case "OPTS": reply(out, "200 OPTS accepted"); break;
                    case "CLNT": reply(out, "200 Client noted"); break;
                    case "PWD": case "XPWD": reply(out, "257 \"/\" is current directory"); break;
                    case "CWD": case "XCWD": reply(out, loggedIn ? "250 Directory changed" : "530 Login first"); break;
                    case "CDUP": reply(out, "250 Directory changed"); break;
                    case "MKD": case "XMKD": reply(out, "257 Directory created"); break;
                    case "TYPE": reply(out, "200 Type set"); break;
                    case "MODE": reply(out, "200 Mode S"); break;
                    case "STRU": reply(out, "200 Structure F"); break;
                    case "NOOP": reply(out, "200 OK"); break;
                    case "ALLO": reply(out, "202 No storage allocation necessary"); break;
                    case "REST": reply(out, "350 Restart position accepted"); break;
                    case "PASV": {
                        closeQuietly(dataServer);
                        activeTarget = null;
                        dataServer = openPassiveServer(s.getLocalAddress());
                        byte[] a = s.getLocalAddress().getAddress();
                        if (a.length != 4) { closeQuietly(dataServer); dataServer = null; reply(out, "425 IPv4 required"); break; }
                        int p = dataServer.getLocalPort();
                        String host = (a[0]&255)+","+(a[1]&255)+","+(a[2]&255)+","+(a[3]&255);
                        reply(out, "227 Entering Passive Mode ("+host+","+(p/256)+","+(p%256)+")");
                        listener.onStatus("Z8 passive data port ready: " + p);
                        break;
                    }
                    case "EPSV": {
                        closeQuietly(dataServer);
                        activeTarget = null;
                        dataServer = openPassiveServer(s.getLocalAddress());
                        reply(out, "229 Entering Extended Passive Mode (|||"+dataServer.getLocalPort()+"|)");
                        listener.onStatus("Z8 passive data port ready: " + dataServer.getLocalPort());
                        break;
                    }
                    case "PORT": {
                        closeQuietly(dataServer); dataServer = null;
                        activeTarget = parsePort(arg);
                        if (activeTarget == null) reply(out, "501 Bad PORT command");
                        else { reply(out, "200 PORT accepted"); listener.onStatus("Z8 active data connection selected"); }
                        break;
                    }
                    case "EPRT": {
                        closeQuietly(dataServer); dataServer = null;
                        activeTarget = parseEprt(arg);
                        if (activeTarget == null) reply(out, "501 Bad EPRT command");
                        else { reply(out, "200 EPRT accepted"); listener.onStatus("Z8 active data connection selected"); }
                        break;
                    }
                    case "LIST": case "NLST": {
                        if (!loggedIn) { reply(out, "530 Login first"); break; }
                        reply(out, "150 Opening data connection");
                        try (Socket data = openDataSocket(dataServer, activeTarget);
                             OutputStream dout = data.getOutputStream()) {
                            if ("LIST".equals(cmd)) dout.write("drwxr-xr-x 1 nikon nikon 0 Jan 01 00:00 .\r\n".getBytes(StandardCharsets.UTF_8));
                        }
                        closeQuietly(dataServer); dataServer = null; activeTarget = null;
                        reply(out, "226 Transfer complete");
                        break;
                    }
                    case "STOR": {
                        if (!loggedIn) { reply(out, "530 Login first"); break; }
                        if (dataServer == null && activeTarget == null) { reply(out, "425 Use PASV/EPSV or PORT/EPRT first"); break; }
                        String safeName = sanitize(arg);
                        if (safeName.isEmpty()) safeName = "DSC_" + System.currentTimeMillis() + ".JPG";
                        File part = new File(tempDir, safeName + ".part");
                        File done = new File(tempDir, safeName);
                        reply(out, "150 Opening binary data connection");
                        listener.onStatus("Receiving from Z8: " + safeName);
                        try (Socket data = openDataSocket(dataServer, activeTarget);
                             InputStream din = new BufferedInputStream(data.getInputStream());
                             OutputStream fout = new BufferedOutputStream(new FileOutputStream(part))) {
                            byte[] buf = new byte[128 * 1024];
                            int n;
                            while ((n = din.read(buf)) != -1) fout.write(buf, 0, n);
                        }
                        closeQuietly(dataServer); dataServer = null; activeTarget = null;
                        if (done.exists()) done.delete();
                        if (!part.renameTo(done)) {
                            copy(part, done);
                            part.delete();
                        }
                        reply(out, "226 Transfer complete");
                        listener.onStatus("Z8 transfer complete: " + safeName);
                        listener.onPhotoReceived(done, safeName);
                        break;
                    }
                    case "SIZE": reply(out, "550 File not found"); break;
                    case "MDTM": reply(out, "550 File not found"); break;
                    case "DELE": reply(out, "250 Deleted"); break;
                    case "RNFR": reply(out, "350 Ready for RNTO"); break;
                    case "RNTO": reply(out, "250 Rename successful"); break;
                    case "QUIT": reply(out, "221 Goodbye"); return;
                    default: reply(out, "200 OK"); break;
                }
            }
        } catch (Exception e) {
            listener.onStatus("Camera FTP session ended: " + e.getMessage());
        } finally {
            closeQuietly(dataServer);
        }
    }

    private ServerSocket openPassiveServer(InetAddress bindAddress) throws IOException {
        int span = PASSIVE_MAX - PASSIVE_MIN + 1;
        int start = (int)(System.nanoTime() & 0x7fffffff) % span;
        IOException last = null;
        for (int i = 0; i < Math.min(span, 512); i++) {
            int p = PASSIVE_MIN + ((start + i) % span);
            ServerSocket ss = new ServerSocket();
            try {
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(bindAddress, p), 1);
                ss.setSoTimeout(30000);
                return ss;
            } catch (IOException e) {
                last = e;
                closeQuietly(ss);
            }
        }
        throw new IOException("No Nikon passive port available in 32768-61000", last);
    }

    private static Socket openDataSocket(ServerSocket passive, InetSocketAddress active) throws IOException {
        if (passive != null) return passive.accept();
        if (active != null) {
            Socket s = new Socket();
            s.connect(active, 30000);
            s.setSoTimeout(120000);
            return s;
        }
        throw new IOException("No FTP data connection configured");
    }

    private static InetSocketAddress parsePort(String arg) {
        try {
            String[] p = arg.split(",");
            if (p.length != 6) return null;
            String host = p[0]+"."+p[1]+"."+p[2]+"."+p[3];
            int port = Integer.parseInt(p[4]) * 256 + Integer.parseInt(p[5]);
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

    private static String sanitize(String s) {
        if (s == null) return "";
        s = s.replace('\\','/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void copy(File a, File b) throws IOException {
        try (InputStream in = new FileInputStream(a); OutputStream out = new FileOutputStream(b)) {
            byte[] buf = new byte[128 * 1024]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf,0,n);
        }
    }

    private static void reply(BufferedWriter out, String s) throws IOException { raw(out, s + "\r\n"); }
    private static void raw(BufferedWriter out, String s) throws IOException { out.write(s); out.flush(); }
    private static void closeQuietly(ServerSocket s) { if (s != null) try { s.close(); } catch (Exception ignored) {} }

    public void stop() {
        running = false;
        closeQuietly(controlServer);
        clients.shutdownNow();
    }
}
