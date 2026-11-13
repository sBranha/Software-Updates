package com.newswire.vehicleporttester;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Owner-configured, read-only FordConnect Query HTTP client. No dependency on
 * the Ford Android app, Ford home-screen widget, OBD-II, or Android Auto USB.
 *
 * The endpoints and token parameters follow published community integrations
 * that use FordConnect Query; the Ford portal's approved documentation governs
 * your application. A real Ford developer client ID, client secret, registered
 * callback, successful OAuth owner consent, and supported vehicle are required.
 *
 * Ford app passwords, vehicle controls and unpublished FordPass APIs are NOT used.
 * Tokens and developer secret are AES-GCM encrypted with Android Keystore.
 * Only validated scalar vehicle readings and non-sensitive status are persisted.
 */
final class FordConnectClient {
    static final String PREF="fordconnect_standalone_v098";
    static final String AUTH_URL="https://api.vehicle.ford.com/fcon-public/v1/auth/init";
    static final String TOKEN_URL="https://api.vehicle.ford.com/dah2vb2cprod.onmicrosoft.com/oauth2/v2.0/token?p=B2C_1A_FCON_AUTHORIZE";
    static final String GARAGE_URL="https://api.vehicle.ford.com/fcon-query/v1/garage";
    static final String TELEMETRY_URL="https://api.vehicle.ford.com/fcon-query/v1/telemetry";
    static final String REDIRECT="http://localhost:8765/callback";
    private static final String KEY_ALIAS="VehicleExplorerFordConnectV098";
    private static final long RATE_GAP_MS=65000;
    private static final long AUTO_REFRESH_MS=900000;
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    interface StatusListener { void onStatus(String status); }
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static String status(Context c){return prefs(c).getString("status","NOT_CONFIGURED");}
    static boolean hasConfiguration(Context c){
        return !prefs(c).getString("client_id","").isEmpty() &&
                !prefs(c).getString("secret_enc","").isEmpty();
    }
    static boolean authorized(Context c){
        return !prefs(c).getString("refresh_enc","").isEmpty() ||
                !prefs(c).getString("access_enc","").isEmpty();
    }
    static String clientId(Context c){return prefs(c).getString("client_id","");}
    static long lastRead(Context c){return prefs(c).getLong("read_ms",0);}
    static long lastAttempt(Context c){return prefs(c).getLong("attempt_ms",0);}
    static void updateStatus(Context c,String status){
        prefs(c).edit().putString("status",status)
                .putLong("status_at_ms",System.currentTimeMillis()).apply();
    }
    private static String encrypt(String plain) throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey key;
        if(!store.containsAlias(KEY_ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            key=generator.generateKey();
        }else {
            key=(SecretKey)store.getKey(KEY_ALIAS,null);
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key);
        String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        String bytes=Base64.encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        return iv+"."+bytes;
    }
    private static String decrypt(String data) throws Exception {
        if(data==null||data.isEmpty())return "";
        String[] fields=data.split("\\.",2);
        if(fields.length!=2)throw new IllegalArgumentException("Encrypted data corrupt");
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey key=(SecretKey)store.getKey(KEY_ALIAS,null);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,
                new GCMParameterSpec(128,Base64.decode(fields[0],Base64.DEFAULT)));
        return new String(cipher.doFinal(Base64.decode(fields[1],Base64.DEFAULT)),StandardCharsets.UTF_8);
    }
    static void configure(Context c,String id,String secret) throws Exception {
        id=id==null?"":id.trim();
        if(!id.matches("[a-zA-Z0-9_-]{6,128}"))
            throw new IllegalArgumentException("Ford client ID is missing or malformed");
        String encrypted=secret==null||secret.isEmpty()?"":encrypt(secret);
        SharedPreferences old=prefs(c);
        if(encrypted.isEmpty()){
            if(!id.equals(old.getString("client_id",""))||
                    old.getString("secret_enc","").isEmpty())
                throw new IllegalArgumentException("Enter the secret issued by Ford");
            // Reuse secret on this phone only when client ID is unchanged.
        }else if(!id.equals(old.getString("client_id",""))){
            old.edit().remove("access_enc").remove("refresh_enc")
                    .remove("auth_state").remove("token_expires_ms").apply();
        }
        SharedPreferences.Editor edit=old.edit().putString("client_id",id)
                .putString("redirect_uri",REDIRECT)
                .putString("status","DEVELOPER_CREDENTIALS_SAVED_NOT_AUTHORIZED")
                .putLong("config_ms",System.currentTimeMillis());
        if(!encrypted.isEmpty())edit.putString("secret_enc",encrypted);
        edit.apply();
    }
    static String authorizationUrl(Context c) throws Exception {
        if(!hasConfiguration(c))throw new IllegalStateException("Save Ford developer credentials first");
        byte[] bytes=new byte[8];new SecureRandom().nextBytes(bytes);
        StringBuilder st=new StringBuilder();
        for(byte b:bytes)st.append(String.format(Locale.US,"%02x",b&255));
        prefs(c).edit().putString("auth_state",st.toString())
                .putLong("auth_start_ms",System.currentTimeMillis())
                .putString("status","AWAITING_FORD_OWNER_CONSENT")
                .apply();
        return AUTH_URL+"?client_id="+enc(clientId(c))+
                "&response_type=code&redirect_uri="+enc(REDIRECT)+
                "&scope="+enc("openid offline_access")+
                "&state="+enc(st.toString());
    }
    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value,"UTF-8");
    }
    static boolean validRedirect(Context c,String url){
        try {
            android.net.Uri u=android.net.Uri.parse(url);
            String expected=prefs(c).getString("auth_state","");
            if(expected.isEmpty()||System.currentTimeMillis()-
                    prefs(c).getLong("auth_start_ms",0)>600000)return false;
            if(!"http".equalsIgnoreCase(u.getScheme())||!"localhost".equalsIgnoreCase(u.getHost())
                    ||u.getPort()!=8765||!"/callback".equals(u.getPath()))return false;
            return expected.equals(u.getQueryParameter("state")) &&
                    !u.getQueryParameter("code").isEmpty();
        }catch(Exception ignored){return false;}
    }
    static void exchangeRedirectAsync(Context c,String url,StatusListener listener){
        if(!BUSY.compareAndSet(false,true)){
            notify(listener,"Another FordConnect request is running");return;
        }
        final Context app=c.getApplicationContext();
        new Thread(()->{
            String response;
            try{
                if(!validRedirect(app,url))throw new IllegalStateException("OAUTH_REDIRECT_OR_STATE_INVALID");
                String code=android.net.Uri.parse(url).getQueryParameter("code");
                String id=clientId(app);
                String secret=decrypt(prefs(app).getString("secret_enc",""));
                String body="grant_type=authorization_code&client_id="+enc(id)+
                        "&client_secret="+enc(secret)+"&redirect_uri="+enc(REDIRECT)+
                        "&code="+enc(code)+"&scope="+enc(id+" offline_access openid");
                JSONObject token=postForm(TOKEN_URL,body);
                storeTokens(app,token);
                prefs(app).edit().remove("auth_state").apply();
                updateStatus(app,"FORD_OAUTH_CONNECTED");
                response="Ford owner authorization succeeded. Tap REFRESH FORD DATA.";
            }catch(Exception error){
                response="Ford login not completed: "+error.getMessage();
                updateStatus(app,"OAUTH_FAILED_"+safeError(error));
            }finally{BUSY.set(false);}
            notify(listener,response);
        },"FordConnectOAuth").start();
    }
    static void refreshAsync(Context c,boolean force,StatusListener listener){
        if(!BUSY.compareAndSet(false,true)){
            notify(listener,"Another FordConnect request is running");return;
        }
        final Context app=c.getApplicationContext();
        new Thread(()->{
            String result;
            try {
                if(!authorized(app))throw new IllegalStateException("NO_OAUTH_TOKEN");
                long now=System.currentTimeMillis();
                long gap=now-lastAttempt(app);
                long required=force?RATE_GAP_MS:AUTO_REFRESH_MS;
                if(lastAttempt(app)>0 && gap<required){
                    result="Ford rate-limit safety: wait "+Math.max(1,(required-gap+999)/1000)+
                        " seconds before another cloud refresh.";
                    updateStatus(app,"RATE_LIMIT_COOLDOWN");
                }else{
                    prefs(app).edit().putLong("attempt_ms",now).apply();
                    updateStatus(app,"FORD_CLOUD_REQUESTING_TELEMETRY");
                    String token=validAccessToken(app);
                    JSONObject telemetry=getJson(TELEMETRY_URL,token);
                    String parseStatus=recordTelemetry(app,telemetry);
                    result="Ford telemetry: "+parseStatus;
                }
            }catch(Exception error){
                updateStatus(app,"FORD_CLOUD_ERROR_"+safeError(error));
                result="Ford cloud refresh failed: "+safeError(error);
            }finally{BUSY.set(false);}
            notify(listener,result);
        },"FordConnectReadOnly").start();
    }
    private static String safeError(Exception e){
        String name=e.getMessage();
        if(name==null||name.length()>85)name=e.getClass().getSimpleName();
        // Never reveal URLs, access codes, client secrets, VINs or tokens.
        return name.matches("[A-Z0-9_:-]{2,85}")?name:e.getClass().getSimpleName();
    }
    private static void notify(StatusListener listener,String value){
        if(listener!=null)MAIN.post(()->listener.onStatus(value));
    }
    private static JSONObject postForm(String url,String body) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(15000);conn.setReadTimeout(25000);
        conn.setRequestMethod("POST");conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        byte[] payload=body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(payload.length);
        try{
            try(OutputStream out=conn.getOutputStream()){out.write(payload);}
            int status=conn.getResponseCode();
            if(status!=200)throw new IllegalStateException("TOKEN_HTTP_"+status);
            return new JSONObject(readBody(conn));
        }finally{conn.disconnect();}
    }
    private static JSONObject getJson(String url,String bearer) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(15000);conn.setReadTimeout(25000);
        conn.setRequestMethod("GET");conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Authorization","Bearer "+bearer);
        try{
            int status=conn.getResponseCode();
            if(status!=200)throw new IllegalStateException("FORD_API_HTTP_"+status);
            return new JSONObject(readBody(conn));
        }finally{conn.disconnect();}
    }
    private static String readBody(HttpURLConnection c) throws Exception {
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1){
                if(out.size()+n>1024*1024)throw new IllegalStateException("FORD_RESPONSE_TOO_LARGE");
                out.write(buf,0,n);
            }
            return out.toString("UTF-8");
        }
    }
    private static void storeTokens(Context c,JSONObject token) throws Exception {
        String access=token.optString("access_token","");
        if(access.isEmpty())throw new IllegalStateException("FORD_TOKEN_EMPTY");
        SharedPreferences p=prefs(c);
        SharedPreferences.Editor ed=p.edit().putString("access_enc",encrypt(access))
                .putLong("token_expires_ms",System.currentTimeMillis()+
                        Math.max(30,token.optLong("expires_in",1200))*1000L);
        String refresh=token.optString("refresh_token","");
        if(!refresh.isEmpty())ed.putString("refresh_enc",encrypt(refresh));
        ed.apply();
    }
    private static String validAccessToken(Context c) throws Exception {
        SharedPreferences p=prefs(c);
        String access=p.getString("access_enc","");
        if(!access.isEmpty() && System.currentTimeMillis()+60000<
                p.getLong("token_expires_ms",0))return decrypt(access);
        String refresh=p.getString("refresh_enc","");
        if(refresh.isEmpty())throw new IllegalStateException("FORD_RELOGIN_REQUIRED");
        String id=clientId(c);
        String body="grant_type=refresh_token&client_id="+enc(id)+
                "&client_secret="+enc(decrypt(p.getString("secret_enc","")))+
                "&refresh_token="+enc(decrypt(refresh))+
                "&redirect_uri="+enc(REDIRECT)+"&scope="+enc(id+" offline_access openid");
        JSONObject next=postForm(TOKEN_URL,body);
        storeTokens(c,next);
        updateStatus(c,"FORD_OAUTH_REFRESHED");
        return decrypt(prefs(c).getString("access_enc",""));
    }
    private static JSONObject findMetrics(JSONObject data){
        JSONObject direct=data.optJSONObject("metrics");
        if(direct!=null)return direct;
        JSONArray vehicles=data.optJSONArray("vehicles");
        if(vehicles!=null&&vehicles.length()==1){
            JSONObject vehicle=vehicles.optJSONObject(0);
            if(vehicle!=null){
                JSONObject metrics=vehicle.optJSONObject("metrics");
                if(metrics!=null)return metrics;
            }
        }
        JSONObject nested=data.optJSONObject("data");
        return nested==null?null:findMetrics(nested);
    }
    private static Double metric(JSONObject metrics,String... keys){
        for(String key:keys){
            JSONObject wrapper=metrics.optJSONObject(key);
            if(wrapper==null)continue;
            Object value=wrapper.opt("value");
            if(value instanceof Number){
                double number=((Number)value).doubleValue();
                if(Double.isFinite(number))return number;
            }else if(value instanceof String){
                try{
                    double number=Double.parseDouble((String)value);
                    if(Double.isFinite(number))return number;
                }catch(Exception ignored){}
            }
        }
        return null;
    }
    private static String recordTelemetry(Context c,JSONObject payload){
        JSONObject metrics=findMetrics(payload);
        if(metrics==null){
            updateStatus(c,"FORD_RESPONSE_NO_METRICS");
            return "Authenticated Ford response, no recognized vehicle metrics.";
        }
        Double fuel=metric(metrics,"fuelLevel");
        Double rangeKm=metric(metrics,"fuelRange","distanceToEmpty");
        Double odometerKm=metric(metrics,"odometer");
        Double oil=metric(metrics,"engineOilLife","oilLife","oilLifeRemaining");
        SharedPreferences.Editor ed=prefs(c).edit();
        int count=0;
        if(fuel!=null && fuel>=0 && fuel<=120){
            ed.putInt("fuel_pct",(int)Math.round(fuel));count++;
        }
        if(rangeKm!=null && rangeKm>=0 && rangeKm<=3000){
            ed.putInt("range_mi",(int)Math.round(rangeKm*0.621371));count++;
        }
        if(odometerKm!=null && odometerKm>=0 && odometerKm<=2000000){
            ed.putInt("odometer_mi",(int)Math.round(odometerKm*0.621371));count++;
        }
        if(oil!=null && oil>=0 && oil<=100){
            ed.putInt("oil_pct",(int)Math.round(oil));count++;
        }
        if(count==0){
            updateStatus(c,"FORD_RESPONSE_NO_SUPPORTED_VALUES");
            return "Ford telemetry received; expected fuel/range fields absent.";
        }
        // Only store values that were actually returned in THIS cloud response.
        if(fuel==null)ed.remove("fuel_pct");
        if(rangeKm==null)ed.remove("range_mi");
        if(odometerKm==null)ed.remove("odometer_mi");
        if(oil==null)ed.remove("oil_pct");
        ed.putString("status","FORD_CONNECT_AUTHENTICATED_VALUES")
                .putString("source","FORDCONNECT_QUERY_READ_ONLY_NOT_WIDGET")
                .putInt("verified_fields",count)
                .putLong("read_ms",System.currentTimeMillis())
                .apply();
        return count+" verified cloud readings (not local SYNC ECU).";
    }
    static void disconnect(Context c){
        prefs(c).edit().remove("client_id").remove("secret_enc")
                .remove("access_enc").remove("refresh_enc").remove("auth_state")
                .remove("fuel_pct").remove("range_mi").remove("odometer_mi")
                .remove("oil_pct").remove("read_ms").remove("attempt_ms")
                .putString("status","DISCONNECTED").apply();
    }
}
