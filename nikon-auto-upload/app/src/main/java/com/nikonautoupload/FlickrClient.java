package com.nikonautoupload;

import android.content.*;
import android.net.Network;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Xml;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;

public class FlickrClient {
    public static final String REQUEST_TOKEN="https://www.flickr.com/services/oauth/request_token";
    public static final String AUTHORIZE="https://www.flickr.com/services/oauth/authorize";
    public static final String ACCESS_TOKEN="https://www.flickr.com/services/oauth/access_token";
    public static final String UPLOAD="https://up.flickr.com/services/upload/";
    public static final String REST="https://www.flickr.com/services/rest/";
    private final Context ctx;
    private final SharedPreferences p;

    public static class AlbumResult {
        public final String id,title;
        public final int count;
        AlbumResult(String i,String t,int c){id=i;title=t;count=c;}
    }
    private static class AlbumInfo { String id="",title=""; int photos=0; }

    public FlickrClient(Context c){ctx=c;p=c.getSharedPreferences("settings",Context.MODE_PRIVATE);}

    private Map<String,String> base(String token){
        Map<String,String> m=new LinkedHashMap<>();
        m.put("oauth_consumer_key",p.getString("flickr_key",""));
        m.put("oauth_nonce",OAuth1.nonce());m.put("oauth_signature_method","HMAC-SHA1");
        m.put("oauth_timestamp",String.valueOf(System.currentTimeMillis()/1000));m.put("oauth_version","1.0");
        if(token!=null&&!token.isEmpty())m.put("oauth_token",token);return m;
    }

    public String beginAuth() throws Exception {
        String key=p.getString("flickr_key",""),sec=p.getString("flickr_secret","");
        if(key.isEmpty()||sec.isEmpty())throw new Exception("Enter Flickr API key and secret first.");
        Map<String,String> m=base(null);m.put("oauth_callback","nikonautoupload://flickr");m.put("oauth_signature",OAuth1.signature("POST",REQUEST_TOKEN,m,sec,""));
        Map<String,String> r=parse(postForm(REQUEST_TOKEN,m,null));
        p.edit().putString("req_token",safe(r.get("oauth_token"))).putString("req_secret",safe(r.get("oauth_token_secret"))).apply();
        return AUTHORIZE+"?oauth_token="+OAuth1.enc(r.get("oauth_token"))+"&perms=write";
    }

    public void finishAuth(Uri callback) throws Exception {
        String token=callback.getQueryParameter("oauth_token"),verifier=callback.getQueryParameter("oauth_verifier");
        String sec=p.getString("flickr_secret",""),reqSec=p.getString("req_secret","");
        Map<String,String> m=base(token);m.put("oauth_verifier",verifier);m.put("oauth_signature",OAuth1.signature("POST",ACCESS_TOKEN,m,sec,reqSec));
        Map<String,String> r=parse(postForm(ACCESS_TOKEN,m,null));
        p.edit().putString("access_token",safe(r.get("oauth_token"))).putString("access_secret",safe(r.get("oauth_token_secret")))
                .putString("flickr_name",safe(r.get("username"))).putString("flickr_nsid",safe(r.get("user_nsid"))).apply();
    }

    public String upload(Uri uri,String title,String tags,boolean isPublic) throws Exception{return upload(uri,title,tags,isPublic,null);}

    /** Upload one photo and return its Flickr photo id. */
    public String upload(Uri uri,String title,String tags,boolean isPublic,Network network) throws Exception {
        String sec=p.getString("flickr_secret",""),token=p.getString("access_token",""),tokSec=p.getString("access_secret","");
        if(token.isEmpty())throw new Exception("Flickr is not connected.");
        Map<String,String> m=base(token);m.put("title",title);if(tags!=null&&!tags.isEmpty())m.put("tags",tags);
        m.put("is_public",isPublic?"1":"0");m.put("is_friend","0");m.put("is_family","0");m.put("oauth_signature",OAuth1.signature("POST",UPLOAD,m,sec,tokSec));
        String boundary="----NikonAuto"+System.currentTimeMillis();URL url=new URL(UPLOAD);
        HttpURLConnection c=(HttpURLConnection)(network==null?url.openConnection():network.openConnection(url));
        c.setDoOutput(true);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);c.setConnectTimeout(20000);c.setReadTimeout(120000);
        try(OutputStream out=c.getOutputStream()){
            for(Map.Entry<String,String> e:m.entrySet())writeField(out,boundary,e.getKey(),e.getValue());
            String filename=getName(uri);out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"photo\"; filename=\""+filename.replace("\"","")+"\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            try(InputStream in=ctx.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Could not open photo");byte[] buf=new byte[64*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
            out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        String body=read(c);if(c.getResponseCode()/100!=2||!body.contains("stat=\"ok\""))throw new IOException("Flickr upload failed: "+flickrError(body));
        String photoId=firstTagText(body,"photoid");if(photoId.isEmpty())throw new IOException("Flickr uploaded the photo but did not return a photo id.");
        return photoId;
    }

    /** Confirm Flickr can read the uploaded photo before the app marks it complete. */
    public void verifyPhoto(String photoId,Network network) throws Exception {
        Map<String,String> a=new LinkedHashMap<>();a.put("photo_id",photoId);String body=rest("flickr.photos.getInfo",a,network);
        String id=firstAttribute(body,"photo","id");if(!photoId.equals(id))throw new IOException("Flickr photo verification failed.");
    }

    public static String albumBaseTitle(long when,String suffix){
        String base=new SimpleDateFormat("M-d-yy",Locale.US).format(new Date(when));String s=suffix==null?"":suffix.trim();if(!s.isEmpty())base+=" "+s;return base;
    }

    /** Add to M-d-yy, then M-d-yy-2, -3, etc. and verify membership. */
    public AlbumResult addToDailyAlbum(String photoId,String baseTitle,int maxPhotos,Network network) throws Exception {
        maxPhotos=Math.max(1,Math.min(999,maxPhotos));
        String cachedBase=p.getString("flickr_album_cache_base",""),cachedId=p.getString("flickr_album_cache_id",""),cachedTitle=p.getString("flickr_album_cache_title","");
        int cachedCount=p.getInt("flickr_album_cache_count",0);
        if(baseTitle.equals(cachedBase)&&!cachedId.isEmpty()&&cachedCount>0&&cachedCount<maxPhotos){
            try{addPhotoToAlbum(cachedId,photoId,network);verifyInAlbum(cachedId,photoId,network);int count=cachedCount+1;cacheAlbum(baseTitle,cachedId,cachedTitle,count);return new AlbumResult(cachedId,cachedTitle,count);}catch(Exception stale){clearAlbumCache();}
        }
        AlbumInfo latest=findLatestAlbum(baseTitle,network);
        if(latest==null){AlbumResult r=createAlbum(baseTitle,photoId,network);verifyInAlbum(r.id,photoId,network);cacheAlbum(baseTitle,r.id,r.title,r.count);return r;}
        int idx=rolloverIndex(baseTitle,latest.title);
        if(latest.photos>=maxPhotos){String title=baseTitle+"-"+(idx+1);AlbumResult r=createAlbum(title,photoId,network);verifyInAlbum(r.id,photoId,network);cacheAlbum(baseTitle,r.id,r.title,r.count);return r;}
        addPhotoToAlbum(latest.id,photoId,network);verifyInAlbum(latest.id,photoId,network);
        AlbumResult r=new AlbumResult(latest.id,latest.title,latest.photos+1);cacheAlbum(baseTitle,r.id,r.title,r.count);return r;
    }

    private AlbumInfo findLatestAlbum(String baseTitle,Network network) throws Exception {
        Map<String,String> a=new LinkedHashMap<>();a.put("per_page","500");List<AlbumInfo> albums=parseAlbums(rest("flickr.photosets.getList",a,network));AlbumInfo best=null;int bestIndex=0;
        for(AlbumInfo x:albums){int idx=rolloverIndex(baseTitle,x.title);if(idx>bestIndex){bestIndex=idx;best=x;}}return best;
    }
    private int rolloverIndex(String base,String title){if(title==null)return 0;if(title.equals(base))return 1;String prefix=base+"-";if(!title.startsWith(prefix))return 0;try{int n=Integer.parseInt(title.substring(prefix.length()));return n>=2?n:0;}catch(Exception e){return 0;}}
    private AlbumResult createAlbum(String title,String photoId,Network network) throws Exception {Map<String,String>a=new LinkedHashMap<>();a.put("title",title);a.put("description","Created automatically by Nikon Auto Upload");a.put("primary_photo_id",photoId);String body=rest("flickr.photosets.create",a,network);String id=firstAttribute(body,"photoset","id");if(id.isEmpty())throw new IOException("Flickr created an album but did not return its id.");return new AlbumResult(id,title,1);}
    private void addPhotoToAlbum(String albumId,String photoId,Network network) throws Exception {Map<String,String>a=new LinkedHashMap<>();a.put("photoset_id",albumId);a.put("photo_id",photoId);rest("flickr.photosets.addPhoto",a,network);}

    private void verifyInAlbum(String albumId,String photoId,Network network) throws Exception {
        for(int page=1;page<=3;page++){
            Map<String,String>a=new LinkedHashMap<>();a.put("photoset_id",albumId);a.put("per_page","500");a.put("page",String.valueOf(page));String body=rest("flickr.photosets.getPhotos",a,network);
            if(hasPhotoId(body,photoId))return;int pages=1;try{pages=Integer.parseInt(firstAttribute(body,"photoset","pages"));}catch(Exception ignored){}if(page>=pages)break;
        }
        throw new IOException("Flickr album verification failed.");
    }

    private static boolean hasPhotoId(String xml,String wanted){
        try{XmlPullParser x=Xml.newPullParser();x.setInput(new StringReader(xml));for(int e=x.getEventType();e!=XmlPullParser.END_DOCUMENT;e=x.next())if(e==XmlPullParser.START_TAG&&"photo".equals(x.getName())&&wanted.equals(safe(x.getAttributeValue(null,"id"))))return true;}catch(Exception ignored){}return false;
    }

    private String rest(String method,Map<String,String> args,Network network) throws Exception {
        String key=p.getString("flickr_key",""),sec=p.getString("flickr_secret",""),token=p.getString("access_token",""),tokSec=p.getString("access_secret","");
        if(key.isEmpty()||sec.isEmpty()||token.isEmpty())throw new Exception("Flickr is not connected.");
        Map<String,String> m=base(token);m.put("api_key",key);m.put("method",method);if(args!=null)m.putAll(args);m.put("oauth_signature",OAuth1.signature("POST",REST,m,sec,tokSec));
        String body=postForm(REST,m,network);if(!body.contains("stat=\"ok\""))throw new IOException("Flickr API: "+flickrError(body));return body;
    }

    private void cacheAlbum(String base,String id,String title,int count){p.edit().putString("flickr_album_cache_base",base).putString("flickr_album_cache_id",id).putString("flickr_album_cache_title",title).putInt("flickr_album_cache_count",count).apply();}
    private void clearAlbumCache(){p.edit().remove("flickr_album_cache_base").remove("flickr_album_cache_id").remove("flickr_album_cache_title").remove("flickr_album_cache_count").apply();}

    private List<AlbumInfo> parseAlbums(String xml) throws Exception {
        ArrayList<AlbumInfo> out=new ArrayList<>();XmlPullParser x=Xml.newPullParser();x.setInput(new StringReader(xml));AlbumInfo cur=null;
        for(int e=x.getEventType();e!=XmlPullParser.END_DOCUMENT;e=x.next()){
            if(e==XmlPullParser.START_TAG&&"photoset".equals(x.getName())){cur=new AlbumInfo();cur.id=safe(x.getAttributeValue(null,"id"));try{cur.photos=Integer.parseInt(safe(x.getAttributeValue(null,"photos")));}catch(Exception ignored){}}
            else if(e==XmlPullParser.START_TAG&&"title".equals(x.getName())&&cur!=null)cur.title=x.nextText();
            else if(e==XmlPullParser.END_TAG&&"photoset".equals(x.getName())&&cur!=null){out.add(cur);cur=null;}
        }return out;
    }

    private static String firstTagText(String xml,String tag){try{XmlPullParser x=Xml.newPullParser();x.setInput(new StringReader(xml));for(int e=x.getEventType();e!=XmlPullParser.END_DOCUMENT;e=x.next())if(e==XmlPullParser.START_TAG&&tag.equals(x.getName()))return safe(x.nextText()).trim();}catch(Exception ignored){}return "";}
    private static String firstAttribute(String xml,String tag,String attr){try{XmlPullParser x=Xml.newPullParser();x.setInput(new StringReader(xml));for(int e=x.getEventType();e!=XmlPullParser.END_DOCUMENT;e=x.next())if(e==XmlPullParser.START_TAG&&tag.equals(x.getName()))return safe(x.getAttributeValue(null,attr));}catch(Exception ignored){}return "";}
    private static String flickrError(String body){String msg=firstAttribute(body,"err","msg");if(!msg.isEmpty())return msg;String s=body==null?"":body.replace('\n',' ').replace('\r',' ').trim();return s.length()>180?s.substring(0,180):s;}
    private static void writeField(OutputStream out,String b,String k,String v)throws IOException{out.write(("--"+b+"\r\nContent-Disposition: form-data; name=\""+k+"\"\r\n\r\n"+v+"\r\n").getBytes(StandardCharsets.UTF_8));}
    private String getName(Uri u){try(android.database.Cursor c=ctx.getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "photo.jpg";}
    private static String postForm(String url,Map<String,String> m,Network network)throws Exception {StringBuilder b=new StringBuilder();for(Map.Entry<String,String>e:m.entrySet()){if(b.length()>0)b.append('&');b.append(OAuth1.enc(e.getKey())).append('=').append(OAuth1.enc(e.getValue()));}byte[] data=b.toString().getBytes(StandardCharsets.UTF_8);URL u=new URL(url);HttpURLConnection c=(HttpURLConnection)(network==null?u.openConnection():network.openConnection(u));c.setDoOutput(true);c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(60000);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");try(OutputStream o=c.getOutputStream()){o.write(data);}return read(c);}
    private static String read(HttpURLConnection c)throws IOException{InputStream in=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream();if(in==null)return "";ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[4096];int n;while((n=in.read(x))!=-1)b.write(x,0,n);return b.toString("UTF-8");}
    private static Map<String,String> parse(String s){Map<String,String>m=new HashMap<>();for(String part:s.split("&")){String[]kv=part.split("=",2);if(kv.length==2)try{m.put(URLDecoder.decode(kv[0],"UTF-8"),URLDecoder.decode(kv[1],"UTF-8"));}catch(Exception ignored){}}return m;}
    private static String safe(String s){return s==null?"":s;}
}
