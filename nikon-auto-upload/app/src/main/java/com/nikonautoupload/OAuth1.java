package com.nikonautoupload;

import android.util.Base64;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class OAuth1 {
    private OAuth1() {}
    public static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+","%20").replace("%7E","~"); }
        catch (Exception e) { return ""; }
    }
    public static String nonce() { return Long.toHexString(new SecureRandom().nextLong()) + Long.toHexString(System.nanoTime()); }
    public static String signature(String method, String url, Map<String,String> params, String apiSecret, String tokenSecret) throws Exception {
        List<String> keys = new ArrayList<>(params.keySet()); Collections.sort(keys);
        StringBuilder ps = new StringBuilder();
        for (String k: keys) { if (ps.length()>0) ps.append('&'); ps.append(enc(k)).append('=').append(enc(params.get(k))); }
        String base = method.toUpperCase(Locale.US)+"&"+enc(url)+"&"+enc(ps.toString());
        String key = enc(apiSecret)+"&"+enc(tokenSecret == null ? "" : tokenSecret);
        Mac mac=Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));
        return Base64.encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }
    public static String authHeader(Map<String,String> p) {
        StringBuilder b=new StringBuilder("OAuth "); boolean first=true;
        for (Map.Entry<String,String> e:p.entrySet()) if(e.getKey().startsWith("oauth_")) { if(!first)b.append(", "); first=false; b.append(enc(e.getKey())).append("=\"").append(enc(e.getValue())).append("\""); }
        return b.toString();
    }
}
