package com.nikonautoupload;

import android.content.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FlickrClient {
    public static final String REQUEST_TOKEN="https://www.flickr.com/services/oauth/request_token";
    public static final String AUTHORIZE="https://www.flickr.com/services/oauth/authorize";
    public static final String ACCESS_TOKEN="https://www.flickr.com/services/oauth/access_token";
    public static final String UPLOAD="https://up.flickr.com/services/upload/";
    private final Context ctx; private final SharedPreferences p;
    public FlickrClient(Context c){ctx=c; p=c.getSharedPreferences("settings",Context.MODE_PRIVATE);}
    private Map<String,String> base(String token){
        Map<String,String> m=new LinkedHashMap<>(); m.put("oauth_consumer_key",p.getString("flickr_key","")); m.put("oauth_nonce",OAuth1.nonce()); m.put("oauth_signature_method","HMAC-SHA1"); m.put("oauth_timestamp",String.valueOf(System.currentTimeMillis()/1000)); m.put("oauth_version","1.0"); if(token!=null&&!token.isEmpty())m.put("oauth_token",token); return m;
    }
    public String beginAuth() throws Exception {
        String key=p.getString("flickr_key",""); String sec=p.getString("flickr_secret",""); if(key.isEmpty()||sec.isEmpty())throw new Exception("Enter Flickr API key and secret first.");
        Map<String,String> m=base(null); m.put("oauth_callback","nikonautoupload://flickr"); m.put("oauth_signature",OAuth1.signature("POST",REQUEST_TOKEN,m,sec,""));
        String s=postForm(REQUEST_TOKEN,m); Map<String,String> r=parse(s); p.edit().putString("req_token",r.get("oauth_token")).putString("req_secret",r.get("oauth_token_secret")).apply();
        return AUTHORIZE+"?oauth_token="+OAuth1.enc(r.get("oauth_token"))+"&perms=write";
    }
    public void finishAuth(Uri callback) throws Exception {
        String token=callback.getQueryParameter("oauth_token"), verifier=callback.getQueryParameter("oauth_verifier"); String sec=p.getString("flickr_secret",""); String reqSec=p.getString("req_secret","");
        Map<String,String> m=base(token); m.put("oauth_verifier",verifier); m.put("oauth_signature",OAuth1.signature("POST",ACCESS_TOKEN,m,sec,reqSec));
        Map<String,String> r=parse(postForm(ACCESS_TOKEN,m)); p.edit().putString("access_token",r.get("oauth_token")).putString("access_secret",r.get("oauth_token_secret")).putString("flickr_name",r.get("username")).apply();
    }
    public String upload(Uri uri, String title, String tags, boolean isPublic) throws Exception {
        String key=p.getString("flickr_key",""), sec=p.getString("flickr_secret",""), token=p.getString("access_token",""), tokSec=p.getString("access_secret",""); if(token.isEmpty())throw new Exception("Flickr is not connected.");
        Map<String,String> m=base(token); m.put("title",title); if(tags!=null&&!tags.isEmpty())m.put("tags",tags); m.put("is_public",isPublic?"1":"0"); m.put("is_friend","0"); m.put("is_family","0");
        m.put("oauth_signature",OAuth1.signature("POST",UPLOAD,m,sec,tokSec));
        String boundary="----NikonAuto"+System.currentTimeMillis(); HttpURLConnection c=(HttpURLConnection)new URL(UPLOAD).openConnection(); c.setDoOutput(true); c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary); c.setConnectTimeout(20000); c.setReadTimeout(120000);
        try(OutputStream out=c.getOutputStream()){
            for(Map.Entry<String,String> e:m.entrySet()) writeField(out,boundary,e.getKey(),e.getValue());
            String filename=getName(uri); out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"photo\"; filename=\""+filename.replace("\"","")+"\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            try(InputStream in=ctx.getContentResolver().openInputStream(uri)){ byte[] buf=new byte[64*1024]; int n; while((n=in.read(buf))!=-1)out.write(buf,0,n); }
            out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        String body=read(c); if(c.getResponseCode()/100!=2 || !body.contains("stat=\"ok\"")) throw new IOException("Flickr upload failed: "+body); return body;
    }
    private static void writeField(OutputStream out,String b,String k,String v)throws IOException{out.write(("--"+b+"\r\nContent-Disposition: form-data; name=\""+k+"\"\r\n\r\n"+v+"\r\n").getBytes(StandardCharsets.UTF_8));}
    private String getName(Uri u){try(android.database.Cursor c=ctx.getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "photo.jpg";}
    private static String postForm(String url,Map<String,String> m)throws Exception{StringBuilder b=new StringBuilder();for(Map.Entry<String,String>e:m.entrySet()){if(b.length()>0)b.append('&');b.append(OAuth1.enc(e.getKey())).append('=').append(OAuth1.enc(e.getValue()));}byte[] data=b.toString().getBytes(StandardCharsets.UTF_8);HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setDoOutput(true);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");try(OutputStream o=c.getOutputStream()){o.write(data);}return read(c);}
    private static String read(HttpURLConnection c)throws IOException{InputStream in=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream();if(in==null)return "";ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[4096];int n;while((n=in.read(x))!=-1)b.write(x,0,n);return b.toString("UTF-8");}
    private static Map<String,String> parse(String s){Map<String,String>m=new HashMap<>();for(String p:s.split("&")){String[]kv=p.split("=",2);if(kv.length==2)try{m.put(URLDecoder.decode(kv[0],"UTF-8"),URLDecoder.decode(kv[1],"UTF-8"));}catch(Exception ignored){}}return m;}
}
