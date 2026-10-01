package com.nikonautoupload;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;

/** Minimal passive-mode FTP server designed for Nikon camera uploads. */
public class SimpleFtpServer {
    public interface Listener {
        void onPhotoReceived(File file, String originalName);
        void onStatus(String text);
    }

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
        controlServer.bind(new InetSocketAddress(port));
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "NikonFTP-Accept");
        acceptThread.start();
        listener.onStatus("FTP receiver listening on port " + port);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = controlServer.accept();
                s.setSoTimeout(120000);
                clients.submit(() -> handleClient(s));
            } catch (IOException e) {
                if (running) listener.onStatus("FTP receiver error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        ServerSocket dataServer = null;
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

                switch (cmd) {
                    case "USER":
                        pendingUser = arg;
                        reply(out, "331 Password required");
                        break;
                    case "PASS":
                        loggedIn = username.equals(pendingUser) && password.equals(arg);
                        reply(out, loggedIn ? "230 Logged in" : "530 Login incorrect");
                        break;
                    case "SYST": reply(out, "215 UNIX Type: L8"); break;
                    case "FEAT":
                        raw(out, "211-Features\r\n UTF8\r\n EPSV\r\n SIZE\r\n211 End\r\n");
                        break;
                    case "OPTS": reply(out, "200 OPTS accepted"); break;
                    case "PWD": case "XPWD": reply(out, "257 \"/\" is current directory"); break;
                    case "CWD": case "XCWD": reply(out, loggedIn ? "250 Directory changed" : "530 Login first"); break;
                    case "CDUP": reply(out, "250 Directory changed"); break;
                    case "MKD": case "XMKD": reply(out, "257 Directory created"); break;
                    case "TYPE": reply(out, "200 Type set to I"); break;
                    case "MODE": reply(out, "200 Mode S"); break;
                    case "STRU": reply(out, "200 Structure F"); break;
                    case "NOOP": reply(out, "200 OK"); break;
                    case "ALLO": reply(out, "202 No storage allocation necessary"); break;
                    case "PASV": {
                        closeQuietly(dataServer);
                        dataServer = new ServerSocket(0, 1);
                        dataServer.setSoTimeout(30000);
                        byte[] a = s.getLocalAddress().getAddress();
                        if (a.length != 4) { reply(out, "425 IPv4 required"); break; }
                        int p = dataServer.getLocalPort();
                        String host = (a[0]&255)+","+(a[1]&255)+","+(a[2]&255)+","+(a[3]&255);
                        reply(out, "227 Entering Passive Mode ("+host+","+(p/256)+","+(p%256)+")");
                        break;
                    }
                    case "EPSV": {
                        closeQuietly(dataServer);
                        dataServer = new ServerSocket(0, 1);
                        dataServer.setSoTimeout(30000);
                        reply(out, "229 Entering Extended Passive Mode (|||"+dataServer.getLocalPort()+"|)");
                        break;
                    }
                    case "STOR": {
                        if (!loggedIn) { reply(out, "530 Login first"); break; }
                        if (dataServer == null) { reply(out, "425 Use PASV or EPSV first"); break; }
                        String safeName = sanitize(arg);
                        if (safeName.isEmpty()) safeName = "DSC_" + System.currentTimeMillis() + ".JPG";
                        File part = new File(tempDir, safeName + ".part");
                        File done = new File(tempDir, safeName);
                        reply(out, "150 Opening binary data connection");
                        try (Socket data = dataServer.accept();
                             InputStream din = new BufferedInputStream(data.getInputStream());
                             OutputStream fout = new BufferedOutputStream(new FileOutputStream(part))) {
                            byte[] buf = new byte[128 * 1024];
                            int n;
                            while ((n = din.read(buf)) != -1) fout.write(buf, 0, n);
                        }
                        closeQuietly(dataServer); dataServer = null;
                        if (done.exists()) done.delete();
                        if (!part.renameTo(done)) {
                            copy(part, done);
                            part.delete();
                        }
                        reply(out, "226 Transfer complete");
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
