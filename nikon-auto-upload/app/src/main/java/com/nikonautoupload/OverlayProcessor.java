package com.nikonautoupload;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;

import org.json.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Separate post-import photo-processing module.
 *
 * IMPORTANT: this class does not own or modify camera transports. USB-C, FTP,
 * Wi-Fi, Bluetooth and camera files remain independent. The original imported
 * MediaStore item is never edited. When enabled, this module creates a second
 * JPEG and returns queue instructions to DirectTransferService.
 */
public final class OverlayProcessor {
    private OverlayProcessor() {}

    public static final String PREF_AUTO="overlay_auto_enabled";
    public static final String PREF_UPLOAD_CHOICE="overlay_upload_choice";
    public static final String PREF_HOLD_PREVIEW="overlay_hold_preview";
    public static final String PREF_PORTRAIT_TEMPLATE="overlay_portrait_template";
    public static final String PREF_LANDSCAPE_TEMPLATE="overlay_landscape_template";
    public static final String PREF_TEMPLATES="overlay_templates_json";
    public static final String CHOICE_ORIGINAL="original";
    public static final String CHOICE_OVERLAY="overlay";
    public static final String CHOICE_BOTH="both";
    private static final long RENDER_MAX_PIXELS=50_000_000L;
    private static final long PREVIEW_MAX_PIXELS=3_000_000L;

    public static final class Element {
        public String id=UUID.randomUUID().toString();
        public String type="text"; // text, image, rect
        public String text="Text";
        public String assetPath="";
        public String font="sans-serif";
        public String align="left";
        public float x=.08f,y=.82f,w=.32f,h=.12f,rotation=0f,alpha=1f,textSize=.055f;
        public int color=Color.WHITE;
        public boolean bold=true,fill=false;

        JSONObject toJson() throws JSONException {
            JSONObject o=new JSONObject();
            o.put("id",id).put("type",type).put("text",text).put("assetPath",assetPath).put("font",font).put("align",align);
            o.put("x",x).put("y",y).put("w",w).put("h",h).put("rotation",rotation).put("alpha",alpha).put("textSize",textSize);
            o.put("color",color).put("bold",bold).put("fill",fill);return o;
        }
        static Element fromJson(JSONObject o){
            Element e=new Element();e.id=o.optString("id",e.id);e.type=o.optString("type","text");e.text=o.optString("text","Text");e.assetPath=o.optString("assetPath","");e.font=o.optString("font","sans-serif");e.align=o.optString("align","left");
            e.x=(float)o.optDouble("x",.08);e.y=(float)o.optDouble("y",.82);e.w=(float)o.optDouble("w",.32);e.h=(float)o.optDouble("h",.12);e.rotation=(float)o.optDouble("rotation",0);e.alpha=(float)o.optDouble("alpha",1);e.textSize=(float)o.optDouble("textSize",.055);e.color=o.optInt("color",Color.WHITE);e.bold=o.optBoolean("bold",true);e.fill=o.optBoolean("fill",false);return e;
        }
        public Element copy(){try{return fromJson(toJson());}catch(Exception ignored){return new Element();}}
    }

    public static final class Template {
        public String id=UUID.randomUUID().toString();
        public String name="New Template";
        public String category="Custom";
        public String orientation="landscape"; // portrait / landscape
        public final ArrayList<Element> elements=new ArrayList<>();
        JSONObject toJson() throws JSONException {JSONObject o=new JSONObject();o.put("id",id).put("name",name).put("category",category).put("orientation",orientation);JSONArray a=new JSONArray();for(Element e:elements)a.put(e.toJson());o.put("elements",a);return o;}
        static Template fromJson(JSONObject o){Template t=new Template();t.id=o.optString("id",t.id);t.name=o.optString("name","Template");t.category=o.optString("category","Custom");t.orientation=o.optString("orientation","landscape");JSONArray a=o.optJSONArray("elements");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)t.elements.add(Element.fromJson(x));}return t;}
        public Template copy(){try{Template t=fromJson(toJson());t.id=UUID.randomUUID().toString();t.name=name+" Copy";return t;}catch(Exception ignored){return new Template();}}
    }

    public static final class ProcessResult {
        public Uri overlayUri;
        public String overlayName="";
        public boolean queueOriginal=true;
        public boolean queueOverlay=false;
        public boolean holdOverlay=false;
        public String message="";
    }

    public static synchronized ArrayList<Template> loadTemplates(Context c){
        SharedPreferences p=c.getSharedPreferences("settings",Context.MODE_PRIVATE);ensureDefaults(c,p);ArrayList<Template> out=new ArrayList<>();
        try{JSONArray a=new JSONArray(p.getString(PREF_TEMPLATES,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(Template.fromJson(o));}}catch(Exception ignored){}
        return out;
    }

    public static synchronized void saveTemplate(Context c,Template template){
        ArrayList<Template> all=loadTemplates(c);boolean found=false;for(int i=0;i<all.size();i++)if(all.get(i).id.equals(template.id)){all.set(i,template);found=true;break;}if(!found)all.add(template);saveTemplates(c,all);
    }

    public static synchronized void deleteTemplate(Context c,String id){ArrayList<Template> all=loadTemplates(c);Iterator<Template> it=all.iterator();while(it.hasNext())if(it.next().id.equals(id))it.remove();saveTemplates(c,all);}

    private static void saveTemplates(Context c,List<Template> all){try{JSONArray a=new JSONArray();for(Template t:all)a.put(t.toJson());c.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString(PREF_TEMPLATES,a.toString()).apply();}catch(Exception ignored){}}

    public static Template findTemplate(Context c,String id){if(id==null)return null;for(Template t:loadTemplates(c))if(id.equals(t.id))return t;return null;}

    private static void ensureDefaults(Context c,SharedPreferences p){
        if(p.contains(PREF_TEMPLATES))return;ArrayList<Template> all=new ArrayList<>();
        String[] cats={"Baseball","Softball","Football","Basketball","Team / Event","Watermark"};
        for(String cat:cats){all.add(seed(cat+" Card - Landscape",cat,"landscape"));all.add(seed(cat+" Card - Portrait",cat,"portrait"));}
        try{JSONArray a=new JSONArray();for(Template t:all)a.put(t.toJson());SharedPreferences.Editor e=p.edit().putString(PREF_TEMPLATES,a.toString());if(!all.isEmpty()){e.putString(PREF_LANDSCAPE_TEMPLATE,all.get(0).id);e.putString(PREF_PORTRAIT_TEMPLATE,all.get(1).id);}if(!p.contains(PREF_UPLOAD_CHOICE))e.putString(PREF_UPLOAD_CHOICE,CHOICE_OVERLAY);if(!p.contains(PREF_AUTO))e.putBoolean(PREF_AUTO,false);if(!p.contains(PREF_HOLD_PREVIEW))e.putBoolean(PREF_HOLD_PREVIEW,false);e.apply();}catch(Exception ignored){}
    }

    private static Template seed(String name,String category,String orientation){
        Template t=new Template();t.name=name;t.category=category;t.orientation=orientation;
        Element band=new Element();band.type="rect";band.x=.02f;band.y=.76f;band.w=.96f;band.h=.21f;band.color=Color.argb(185,0,0,0);band.fill=true;band.alpha=.85f;t.elements.add(band);
        Element team=new Element();team.text="{TEAM}";team.x=.06f;team.y=.80f;team.textSize=.038f;team.bold=false;team.color=Color.LTGRAY;t.elements.add(team);
        Element player=new Element();player.text="{PLAYER_NAME}";player.x=.06f;player.y=.87f;player.textSize=.065f;player.bold=true;t.elements.add(player);
        Element num=new Element();num.text="#{NUMBER}";num.x=.92f;num.y=.88f;num.textSize=.080f;num.align="right";num.bold=true;t.elements.add(num);
        Element event=new Element();event.text="{EVENT}  •  {DATE}";event.x=.06f;event.y=.94f;event.textSize=.030f;event.bold=false;event.color=Color.LTGRAY;t.elements.add(event);
        if("Watermark".equals(category)){t.elements.clear();Element mark=new Element();mark.text="{PHOTOGRAPHER}";mark.x=.96f;mark.y=.95f;mark.align="right";mark.textSize=.028f;mark.alpha=.72f;t.elements.add(mark);}
        return t;
    }

    public static String copyAssetIntoModule(Context c,Uri source,String displayName) throws IOException {
        File dir=new File(c.getFilesDir(),"overlay_assets");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create overlay asset folder");String ext=".png";if(displayName!=null){int dot=displayName.lastIndexOf('.');if(dot>=0&&dot<displayName.length()-1)ext=displayName.substring(dot).replaceAll("[^A-Za-z0-9.]","");}
        File out=new File(dir,"asset_"+System.currentTimeMillis()+ext);try(InputStream in=c.getContentResolver().openInputStream(source);OutputStream os=new FileOutputStream(out)){if(in==null)throw new IOException("Could not open selected graphic");byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1)os.write(b,0,n);}return out.getAbsolutePath();
    }

    public static ProcessResult onImported(Context c,SharedPreferences p,Uri original,String originalName){
        ProcessResult r=new ProcessResult();boolean enabled=p.getBoolean(PREF_AUTO,false);String choice=p.getString(PREF_UPLOAD_CHOICE,CHOICE_ORIGINAL);
        if(!enabled){r.queueOriginal=true;r.message="Overlay off";return r;}
        r.queueOriginal=CHOICE_ORIGINAL.equals(choice)||CHOICE_BOTH.equals(choice);
        if(!isJpeg(originalName)){r.message="Overlay skipped for RAW/non-JPEG; original preserved";return r;}
        try{
            boolean portrait=isPortrait(c,original);String id=p.getString(portrait?PREF_PORTRAIT_TEMPLATE:PREF_LANDSCAPE_TEMPLATE,"");Template t=findTemplate(c,id);if(t==null){r.message="No "+(portrait?"portrait":"landscape")+" overlay template selected";p.edit().putString("overlay_last_error",r.message).apply();return r;}
            Bitmap b=render(c,original,t,p,RENDER_MAX_PIXELS);String outName=overlayName(originalName);Uri u=saveProcessed(c,b,outName);b.recycle();r.overlayUri=u;r.overlayName=outName;
            boolean wantsOverlay=CHOICE_OVERLAY.equals(choice)||CHOICE_BOTH.equals(choice);boolean hold=wantsOverlay&&p.getBoolean(PREF_HOLD_PREVIEW,false);r.holdOverlay=hold;r.queueOverlay=wantsOverlay&&!hold;r.message=hold?"Card ready for preview":(wantsOverlay?"Card ready and queued":"Card copy created");
            p.edit().putString("overlay_last_uri",u.toString()).putString("overlay_last_name",outName).putString("overlay_last_status",r.message).remove("overlay_last_error").apply();
            if(hold)addToStringSet(p,"overlay_preview_pending_uris",u.toString());
        }catch(Throwable e){r.message="Overlay failed: "+shortMessage(e);p.edit().putString("overlay_last_error",r.message).putString("overlay_last_status","Failed").apply();}
        return r;
    }

    public static Bitmap preview(Context c,Uri original,String templateId) throws Exception {Template t=findTemplate(c,templateId);if(t==null)throw new IOException("Template not found");return render(c,original,t,c.getSharedPreferences("settings",Context.MODE_PRIVATE),PREVIEW_MAX_PIXELS);}

    public static void approvePreview(Context c,Uri uri){SharedPreferences p=c.getSharedPreferences("settings",Context.MODE_PRIVATE);removeFromStringSet(p,"overlay_preview_pending_uris",uri.toString());addToStringSet(p,"pending_uploads",uri.toString());String base=FlickrClient.albumBaseTitle(System.currentTimeMillis(),effectiveSuffix(p));Set<String>s=new HashSet<>(p.getStringSet("pending_album_bases",Collections.emptySet()));Iterator<String>it=s.iterator();while(it.hasNext())if(it.next().startsWith(uri.toString()+"\u001f"))it.remove();s.add(uri.toString()+"\u001f"+base);p.edit().putStringSet("pending_album_bases",s).putString("overlay_last_status","Approved for Flickr").apply();Intent i=new Intent(c,DirectTransferService.class);i.putExtra("command","retry_failed");if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}

    public static void rejectPreview(Context c,Uri uri){SharedPreferences p=c.getSharedPreferences("settings",Context.MODE_PRIVATE);removeFromStringSet(p,"overlay_preview_pending_uris",uri.toString());addToStringSet(p,"phone_only_uris",uri.toString());p.edit().putString("overlay_last_status","Card kept on phone only").apply();}

    private static String effectiveSuffix(SharedPreferences p){String s=p.getBoolean("session_active",false)?p.getString("session_name",""):p.getString("album_suffix","");return s==null?"":s.replace('\n',' ').replace('\r',' ').replace('\t',' ').trim();}

    private static Bitmap render(Context c,Uri original,Template t,SharedPreferences p,long maxPixels) throws Exception {
        Bitmap src=decode(c,original,maxPixels);Bitmap out=Bitmap.createBitmap(src.getWidth(),src.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);canvas.drawBitmap(src,0,0,null);float w=out.getWidth(),h=out.getHeight(),base=Math.min(w,h);
        for(Element e:t.elements){if(e==null)continue;int alpha=Math.max(0,Math.min(255,(int)(255f*e.alpha)));canvas.save();float cx=e.x*w,cy=e.y*h;canvas.rotate(e.rotation,cx,cy);
            if("text".equals(e.type)){
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);paint.setColor(e.color);paint.setAlpha(alpha);paint.setTextSize(Math.max(10,e.textSize*base));paint.setTypeface(Typeface.create(e.font,e.bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextAlign("right".equals(e.align)?Paint.Align.RIGHT:("center".equals(e.align)?Paint.Align.CENTER:Paint.Align.LEFT));
                String text=resolveTokens(e.text,p);String[] lines=text.split("\\n",-1);float line=paint.getTextSize()*1.08f;for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],cx,cy+i*line,paint);
            }else if("image".equals(e.type)){
                File f=new File(e.assetPath==null?"":e.assetPath);if(f.exists()){Bitmap img=BitmapFactory.decodeFile(f.getAbsolutePath());if(img!=null){Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);paint.setAlpha(alpha);RectF dst=new RectF((e.x-e.w/2f)*w,(e.y-e.h/2f)*h,(e.x+e.w/2f)*w,(e.y+e.h/2f)*h);canvas.drawBitmap(img,null,dst,paint);img.recycle();}}
            }else if("rect".equals(e.type)){
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(e.color);paint.setAlpha(alpha);paint.setStyle(e.fill?Paint.Style.FILL:Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,base*.006f));RectF rr=new RectF((e.x)*w,(e.y)*h,(e.x+e.w)*w,(e.y+e.h)*h);canvas.drawRect(rr,paint);
            }
            canvas.restore();
        }
        src.recycle();return out;
    }

    private static Bitmap decode(Context c,Uri uri,long maxPixels) throws IOException {
        ImageDecoder.Source src=ImageDecoder.createSource(c.getContentResolver(),uri);return ImageDecoder.decodeBitmap(src,(decoder,info,source)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();double scale=Math.min(1d,Math.sqrt((double)maxPixels/Math.max(1d,(double)w*h)));if(scale<.999d)decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);});
    }

    private static Uri saveProcessed(Context c,Bitmap b,String name) throws IOException {ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Camera Auto Upload/Overlays");v.put(MediaStore.Images.Media.IS_PENDING,1);}Uri u=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Could not create overlay photo");boolean ok=false;try(OutputStream out=c.getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Could not open overlay output");ok=b.compress(Bitmap.CompressFormat.JPEG,96,out);}if(!ok){c.getContentResolver().delete(u,null,null);throw new IOException("Could not encode overlay JPEG");}if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);c.getContentResolver().update(u,done,null,null);}return u;}

    private static boolean isPortrait(Context c,Uri u){try(Cursor q=c.getContentResolver().query(u,new String[]{MediaStore.Images.Media.WIDTH,MediaStore.Images.Media.HEIGHT},null,null,null)){if(q!=null&&q.moveToFirst()){int w=q.getInt(0),h=q.getInt(1);if(w>0&&h>0)return h>w;}}catch(Exception ignored){}return false;}
    private static boolean isJpeg(String name){String n=name==null?"":name.toLowerCase(Locale.US);return n.endsWith(".jpg")||n.endsWith(".jpeg");}
    private static String overlayName(String original){String n=original==null?"photo.jpg":original;int dot=n.lastIndexOf('.');String b=dot>0?n.substring(0,dot):n;return b+"_CARD.jpg";}
    private static String resolveTokens(String text,SharedPreferences p){String x=text==null?"":text;String date=new SimpleDateFormat("MMM d, yyyy",Locale.getDefault()).format(new Date());return x.replace("{PLAYER_NAME}",p.getString("overlay_player","")).replace("{NUMBER}",p.getString("overlay_number","")).replace("{TEAM}",p.getString("overlay_team","")).replace("{EVENT}",p.getString("overlay_event","")).replace("{PHOTOGRAPHER}",p.getString("overlay_photographer","")).replace("{DATE}",date);}
    private static String shortMessage(Throwable e){String s=e==null?"Unknown error":e.getMessage();if(s==null||s.trim().isEmpty())s=e.getClass().getSimpleName();return s.length()>140?s.substring(0,140):s;}
    private static synchronized void addToStringSet(SharedPreferences p,String key,String value){Set<String>s=new HashSet<>(p.getStringSet(key,Collections.emptySet()));s.add(value);p.edit().putStringSet(key,s).apply();}
    private static synchronized void removeFromStringSet(SharedPreferences p,String key,String value){Set<String>s=new HashSet<>(p.getStringSet(key,Collections.emptySet()));s.remove(value);p.edit().putStringSet(key,s).apply();}
}
