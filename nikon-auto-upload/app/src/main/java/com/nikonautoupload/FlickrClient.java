package com.nikonautoupload;

import android.content.*;
import android.content.res.AssetFileDescriptor;
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

    /** All album lookups/creation run one at a time so parallel uploads never create duplicate albums. */
    private static final Object ALBUM_LOCK=new Object();

    private final Context ctx;
    private final SharedPreferences p;

    /** Flickr answered, but refused the request (stat="fail"). Network problems stay IOException. */
    public static class FlickrApiException extends Exception {
        public final int code;
        public final boolean permanent;
        FlickrApiException(String message,int code,boolean permanent){super(message);this.code=code;this.permanent=permanent;}
    }

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
        String token=safe(r.get("oauth_token")),secret=safe(r.get("oauth_token_secret"));
        if(token.isEmpty()||secret.isEmpty())throw new Exception("Flickr did not accept the API key/secret. Check them and try again.");
        p.edit().putString("req_token",token).putString("req_secret",secret).apply();
        return AUTHORIZE+"?oauth_token="+OAuth1.enc(token)+"&perms=write";
    }

    /**
     * Completes sign-in. Ignores a callback that is stale (already used, e.g. replayed after the
     * screen rotates) instead of overwriting a working login with empty tokens.
     * @return true when a new login was saved.
     */
    public boolean finishAuth(Uri callback) throws Exception {
        String token=callback.getQueryParameter("oauth_token"),verifier=callback.getQueryParameter("oauth_verifier");
        if(token==null||token.isEmpty()||verifier==null||verifier.isEmpty())throw new Exception("Flickr sign-in was cancelled.");
        String expected=p.getString("req_token","");
        if(expected.isEmpty()||!expected.equals(token))return false;
        String sec=p.getString("flickr_secret",""),reqSec=p.getString("req_secret","");
        Map<String,String> m=base(token);m.put("oauth_verifier",verifier);m.put("oauth_signature",OAuth1.signature("POST",ACCESS_TOKEN,m,sec,reqSec));
        Map<String,String> r=parse(postForm(ACCESS_TOKEN,m,null));
        String access=safe(r.get("oauth_token")),secret=safe(r.get("oauth_token_secret"));
        if(access.isEmpty()||secret.isEmpty())throw new Exception("Flickr did not return a login. Please connect again.");
        p.edit().putString("access_token",access).putString("access_secret",secret)
                .putString("flickr_name",safe(r.get("username"))).putString("flickr_nsid",safe(r.get("user_nsid")))
                .remove("req_token").remove("req_secret").apply();
        return true;
    }

    public String upload(Uri uri,String title,String tags,boolean isPublic) throws Exception{return upload(uri,title,tags,isPublic,null);}

    /**
     * Upload one photo and return its Flickr photo id. isPublic=false means "Only me"
     * (is_public=0, is_friend=0, is_family=0). The photo is streamed with a fixed length so
     * a 45 MP JPEG is never held in memory.
     */
    public String upload(Uri uri,String title,String tags,boolean isPublic,Network network) throws Exception {
        String sec=p.getString("flickr_secret",""),token=p.getString("access_token",""),tokSec=p.getString("access_secret","");
        if(token.isEmpty())throw new FlickrApiException("Flickr is not connected.",-1,false);
        Map<String,String> m=base(token);m.put("title",title);if(tags!=null&&!tags.isEmpty())m.put("tags",tags);
        m.put("is_public",isPublic?"1":"0");m.put("is_friend","0");m.put("is_family","0");m.put("oauth_signature",OAuth1.signature("POST",UPLOAD,m,sec,tokSec));
        String boundary="----NikonAuto"+Long.toHexString(System.nanoTime());
        ByteArrayOutputStream head=new ByteArrayOutputStream();
        for(Map.Entry<String,String> e:m.entrySet())writeField(head,boundary,e.getKey(),e.getValue());
        String filename=getName(uri).replace("\"","").replace("\r","").replace("\n","");
        head.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"photo\"; filename=\""+filename+"\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] tail=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        long fileSize=contentLength(uri);

        URL url=new URL(UPLOAD);
        HttpURLConnection c=(HttpURLConnection)(network==null?url.openConnection():network.openConnection(url));
        try{
            c.setDoOutput(true);c.setRequestMethod("POST");c.setUseCaches(false);
            c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);c.setConnectTimeout(20000);c.setReadTimeout(180000);
            if(fileSize>0)c.setFixedLengthStreamingMode(head.size()+fileSize+tail.length);else c.setChunkedStreamingMode(64*1024);
            long sent=0;
            try(OutputStream out=new BufferedOutputStream(c.getOutputStream(),64*1024)){
                head.writeTo(out);
                try(InputStream in=ctx.getContentResolver().openInputStream(uri)){if(in==null)throw new FileNotFoundException("Could not open photo");byte[] buf=new byte[64*1024];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);sent+=n;}}
                if(fileSize>0&&sent!=fileSize)throw new IOException("Photo changed size while uploading");
                out.write(tail);
            }
            int code=c.getResponseCode();String body=read(c);
            if(code==429||code>=500)throw new IOException("Flickr is busy (HTTP "+code+")");
            if(code/100!=2||!body.contains("stat=\"ok\""))throw apiError("Flickr upload failed",body,true);
            String photoId=firstTagText(body,"photoid");if(photoId.isEmpty())throw new IOException("Flickr uploaded the photo but did not return a photo id.");
            return photoId;
        }finally{c.disconnect();}
    }

    /** Confirm Flickr can read the uploaded photo before the app marks it complete. */
    public void verifyPhoto(String photoId,Network network) throws Exception {
        Map<String,String> a=new LinkedHashMap<>();a.put("photo_id",photoId);String body=rest("flickr.photos.getInfo",a,network);
        String id=firstAttribute(body,"photo","id");if(!photoId.equals(id))throw new IOException("Flickr photo verification failed.");
    }

    public static String albumBaseTitle(long when,String suffix){
        String base=new SimpleDateFormat("M-d-yy",Locale.US).format(new Date(when));String s=suffix==null?"":suffix.trim();if(!s.isEmpty())base+=" "+s;return base;
    }

    /**
     * Add to M-d-yy, then M-d-yy-2, -3, etc. Flickr's stat="ok" on addPhoto/create is the
     * membership confirmation; "already in album" (code 3) counts as success so a retry after
     * a lost response never fails. Serialized so parallel uploads can't create twin albums.
     */
    public AlbumResult addToDailyAlbum(String photoId,String baseTitle,int maxPhotos,Network network) throws Exception {
        synchronized(ALBUM_LOCK){
            maxPhotos=Math.max(1,Math.min(999,maxPhotos));
            String cachedBase=p.getString("flickr_album_cache_base",""),cachedId=p.getString("flickr_album_cache_id",""),cachedTitle=p.getString("flickr_album_cache_title","");
            int cachedCount=p.getInt("flickr_album_cache_count",0);
            if(baseTitle.equals(cachedBase)&&!cachedId.isEmpty()&&cachedCount>0&&cachedCount<maxPhotos){
                try{boolean added=addPhotoToAlbum(cachedId,photoId,network);int count=cachedCount+(added?1:0);cacheAlbum(baseTitle,cachedId,cachedTitle,count);return new AlbumResult(cachedId,cachedTitle,count);}
                catch(FlickrApiException stale){clearAlbumCache();}
            }
            AlbumInfo latest=findLatestAlbum(baseTitle,network);
            if(latest==null){AlbumResult r=createAlbum(baseTitle,photoId,network);cacheAlbum(baseTitle,r.id,r.title,r.count);return r;}
            int idx=rolloverIndex(baseTitle,latest.title);
            if(latest.photos>=maxPhotos){String title=baseTitle+"-"+(idx+1);AlbumResult r=createAlbum(title,photoId,network);cacheAlbum(baseTitle,r.id,r.title,r.count);return r;}
            boolean added=addPhotoToAlbum(latest.id,photoId,network);
            AlbumResult r=new AlbumResult(latest.id,latest.title,latest.photos+(added?1:0));cacheAlbum(baseTitle,r.id,r.title,r.count);return r;
        }
    }

    private AlbumInfo findLatestAlbum(String baseTitle,Network network) throws Exception {
        AlbumInfo best=null;int bestIndex=0;
        for(int page=1;page<=20;page++){
            Map<String,String> a=new LinkedHashMap<>();a.put("per_page","500");a.put("page",String.valueOf(page));
            String body=rest("flickr.photosets.getList",a,network);
            for(AlbumInfo x:parseAlbums(body)){int idx=rolloverIndex(baseTitle,x.title);if(idx>bestIndex){bestIndex=idx;best=x;}}
            int pages=1;try{pages=Integer.parseInt(firstAttribute(body,"photosets","pages"));}catch(Exception ignored){}
            if(page>=pages)break;
        }
        return best;
    }
    private int rolloverIndex(String base,String title){if(title==null)return 0;if(title.equals(base))return 1;String prefix=base+"-";if(!title.startsWith(prefix))return 0;try{int n=Integer.parseInt(title.substring(prefix.length()));return n>=2?n:0;}catch(Exception e){return 0;}}
    private AlbumResult createAlbum(String title,String photoId,Network network) throws Exception {Map<String,String>a=new LinkedHashMap<>();a.put("title",title);a.put("description","Created automatically by Nikon Auto Upload");a.put("primary_photo_id",photoId);String body=rest("flickr.photosets.create",a,network);String id=firstAttribute(body,"photoset","id");if(id.isEmpty())throw new IOException("Flickr created an album but did not return its id.");return new AlbumResult(id,title,1);}

    /** @return true if newly added, false if Flickr says it was already in the album. */
    private boolean addPhotoToAlbum(String albumId,String photoId,Network network) throws Exception {
        Map<String,String>a=new LinkedHashMap<>();a.put("photoset_id",albumId);a.put("photo_id",photoId);
        try{rest("flickr.photosets.addPhoto",a,network);return true;}
        catch(FlickrApiException e){if(e.code==3)return false;throw e;}
    }

    private String rest(String method,Map<String,String> args,Network network) throws Exception {
        String key=p.getString("flickr_key",""),sec=p.getString("flickr_secret",""),token=p.getString("access_token",""),tokSec=p.getString("access_secret","");
        if(key.isEmpty()||sec.isEmpty()||token.isEmpty())throw new FlickrApiException("Flickr is not connected.",-1,false);
        Map<String,String> m=base(token);m.put("api_key",key);m.put("method",method);if(args!=null)m.putAll(args);m.put("oauth_signature",OAuth1.signature("POST",REST,m,sec,tokSec));
        String body=postForm(REST,m,network);if(!body.contains("stat=\"ok\""))throw apiError("Flickr API",body,false);return body;
    }

    private static Exception apiError(String prefix,String body,boolean upload){
        String msg=flickrError(body);int code=-1;try{code=Integer.parseInt(firstAttribute(body,"err","code"));}catch(Exception ignored){}
        if(body==null||body.trim().isEmpty()||code==-1&&!body.contains("stat=\"fail\""))return new IOException(prefix+": unexpected reply");
        // 95-100 = sign-in/key problems: stop the whole queue (not per-photo) until fixed or retried.
        if(code>=95&&code<=100)return new FlickrApiException("Flickr sign-in problem: "+msg+" — reconnect Flickr in Settings",code,false);
        // 105/106 = Flickr temporarily unavailable. Upload 3 = general failure (retry); 2/4/5 = bad file.
        boolean permanent=upload?(code==2||code==4||code==5):(code!=105&&code!=106&&code!=0);
        if(!permanent)return new IOException(prefix+": "+msg);
        return new FlickrApiException(prefix+": "+msg,code,true);
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

    private long contentLength(Uri uri){
        try(AssetFileDescriptor fd=ctx.getContentResolver().openAssetFileDescriptor(uri,"r")){if(fd!=null&&fd.getLength()>0)return fd.getLength();}catch(Exception ignored){}
        try(android.database.Cursor c=ctx.getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getLong(0);}catch(Exception ignored){}
        return -1;
    }

    private static String firstTagText(String xml,String tag){try{XmlPullParser x=Xml.newPullParser();x.setInput(new StringReader(xml));for(int e=x.getEventType();e!=XmlPullParser.END_DOCUMENT;e=x.next())if(e==XmlPullParser.START_TAG&&tag.equals(x.getName()))return safe(x.nextText()).trim();}catch(Exception ignored){}return "";}
    private static String firstAttribute(String xml,String tag,String attr){try{XmlPullParser x=Xml.newPullParser();x.setInput(new StringReader(xml));for(int e=x.getEventType();e!=XmlPullParser.END_DOCUMENT;e=x.next())if(e==XmlPullParser.START_TAG&&tag.equals(x.getName()))return safe(x.getAttributeValue(null,attr));}catch(Exception ignored){}return "";}
    private static String flickrError(String body){String msg=firstAttribute(body,"err","msg");if(!msg.isEmpty())return msg;String s=body==null?"":body.replace('\n',' ').replace('\r',' ').trim();return s.length()>180?s.substring(0,180):s;}
    private static void writeField(OutputStream out,String b,String k,String v)throws IOException{out.write(("--"+b+"\r\nContent-Disposition: form-data; name=\""+k+"\"\r\n\r\n"+v+"\r\n").getBytes(StandardCharsets.UTF_8));}
    private String getName(Uri u){try(android.database.Cursor c=ctx.getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "photo.jpg";}
    private static String postForm(String url,Map<String,String> m,Network network)throws Exception {
        StringBuilder b=new StringBuilder();for(Map.Entry<String,String>e:m.entrySet()){if(b.length()>0)b.append('&');b.append(OAuth1.enc(e.getKey())).append('=').append(OAuth1.enc(e.getValue()));}
        byte[] data=b.toString().getBytes(StandardCharsets.UTF_8);URL u=new URL(url);HttpURLConnection c=(HttpURLConnection)(network==null?u.openConnection():network.openConnection(u));
        try{
            c.setDoOutput(true);c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(60000);c.setFixedLengthStreamingMode(data.length);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            try(OutputStream o=c.getOutputStream()){o.write(data);}
            int code=c.getResponseCode();String body=read(c);
            if(code==429||code>=500)throw new IOException("Flickr is busy (HTTP "+code+")");
            return body;
        }finally{c.disconnect();}
    }
    private static String read(HttpURLConnection c)throws IOException{InputStream in=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream();if(in==null)return "";try(InputStream s=in){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=s.read(x))!=-1)b.write(x,0,n);return b.toString("UTF-8");}}
    private static Map<String,String> parse(String s){Map<String,String>m=new HashMap<>();for(String part:s.split("&")){String[]kv=part.split("=",2);if(kv.length==2)try{m.put(URLDecoder.decode(kv[0],"UTF-8"),URLDecoder.decode(kv[1],"UTF-8"));}catch(Exception ignored){}}return m;}
    private static String safe(String s){return s==null?"":s;}
}
