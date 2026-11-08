package com.newswire.vehicleporttester;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/**
 * Phone-camera tachometer reader for a CYAN analog needle.
 * Uses ONLY Android Camera2 and local bitmap processing.
 * Three taps calibrate (center, 0 mark, upper RPM mark), and a guided
 * color/line detector estimates pointer angle. No Ford/USB/ECU APIs.
 * Explicit confidence rejection: no needle = NO READING, not demo data.
 */
public final class CameraTachometerActivity extends Activity {
    private static final int BACK=0xff07101d, PANEL=0xff142538;
    private static final int CYAN=0xff32d8ee, ORANGE=0xffffb75c;
    private static final int MUTED=0xffa5bac8, WHITE=0xfff3f9fc;
    private static final int CAMERA_PERMISSION=501;
    private TextureView preview;
    private GuideOverlay guide;
    private TextView message, valueText, sourceText, confidenceText;
    private Button pauseButton, maxButton;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private Surface outputSurface;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayDeque<Float> history=new ArrayDeque<>();
    private String selectedCamera;
    private boolean resumed=false, readingEnabled=true, previewAvailable=false;
    private PointF center, zeroMark, maxMark;
    private int calibrationStep=0;
    private int maxRpm=8000;
    private float lastRpm=Float.NaN, lastAngle=Float.NaN, lastConfidence=0;
    private String readingSource="none";
    private long lastSample=0;
    private String lastCameraError="";

    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView label(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(null,1);
        return t;
    }
    private Button button(String text){
        Button b=new Button(this);
        b.setText(text);b.setTextSize(13);b.setAllCaps(false);
        b.setTextColor(WHITE);b.setBackgroundTintList(ColorStateList.valueOf(0xff19637b));
        return b;
    }
    private void pad(LinearLayout root,int h){View spacer=new View(this);root.addView(spacer,new LinearLayout.LayoutParams(1,dp(h)));}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(BACK);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root=vertical();root.setPadding(dp(14),dp(13),dp(14),dp(20));
        scroll.addView(root);
        setContentView(scroll);
        root.addView(label("CAMERA RPM READER  •  v0.8.5",16,CYAN,true));
        pad(root,5);
        root.addView(label("Phone camera only — optical estimate. No Ford RPM sensor or USB commands.",12,MUTED,false));
        pad(root,7);
        root.addView(label("PARKED ONLY. Secure the phone so its rear camera sees the entire LEFT tachometer dial. Do not operate or reposition it while driving.",12,ORANGE,true));
        pad(root,9);

        FrameLayout cameraBox=new FrameLayout(this);
        cameraBox.setBackgroundColor(Color.BLACK);
        preview=new TextureView(this);
        guide=new GuideOverlay(this);
        cameraBox.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        cameraBox.addView(guide,new FrameLayout.LayoutParams(-1,-1));
        root.addView(cameraBox,new LinearLayout.LayoutParams(-1,dp(390)));
        guide.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                pointTapped(e.getX(),e.getY());return true;
            }
            return true;
        });
        pad(root,8);
        message=label("Tap the CENTER of the RPM gauge in the camera preview.",14,WHITE,true);
        root.addView(message);
        pad(root,7);
        valueText=label("— RPM",34,CYAN,true);root.addView(valueText);
        sourceText=label("NOT CALIBRATED",12,ORANGE,true);root.addView(sourceText);
        confidenceText=label("No optical needle reading yet.",12,MUTED,false);
        root.addView(confidenceText);
        pad(root,9);
        LinearLayout choices=new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        maxButton=button("MAX: 8,000 RPM");
        choices.addView(maxButton,new LinearLayout.LayoutParams(0,dp(52),1));
        Button reset=button("RESET 3 TAPS");
        choices.addView(reset,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(choices);
        maxButton.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Highest RPM label on tachometer")
            .setItems(new String[]{"6,000 RPM","7,000 RPM","8,000 RPM"},(d,which)->{
                maxRpm=(which+6)*1000;
                maxButton.setText(String.format(Locale.US,"MAX: %,d RPM",maxRpm));
                history.clear();lastRpm=Float.NaN;guide.invalidate();
                setPrompt();
            }).show());
        reset.setOnClickListener(v->resetCalibration());
        pad(root,7);
        pauseButton=button("PAUSE LIVE CAMERA READING");
        root.addView(pauseButton,new LinearLayout.LayoutParams(-1,dp(52)));
        pauseButton.setOnClickListener(v->{
            readingEnabled=!readingEnabled;
            pauseButton.setText(readingEnabled?"PAUSE LIVE CAMERA READING":"RESUME LIVE CAMERA READING");
            history.clear();
            if(readingEnabled)handler.post(this::updateFrame);
            else{sourceText.setText("PAUSED • No live estimate");confidenceText.setText("Camera preview remains open.");}
        });
        pad(root,7);
        Button manual=button("MANUAL: TAP NEEDLE TIP FOR ONE READING");
        root.addView(manual,new LinearLayout.LayoutParams(-1,dp(52)));
        manual.setOnClickListener(v->{
            if(calibrationStep<3){
                message.setText("First calibrate CENTER, ZERO mark and MAX mark.");
            }else{
                readingEnabled=false;pauseButton.setText("RESUME LIVE CAMERA READING");
                calibrationStep=4;
                message.setText("Tap the CYAN NEEDLE TIP in the preview. This will be a manual angle estimate.");
                guide.invalidate();
            }
        });
        pad(root,7);
        Button accept=button("USE LAST ESTIMATE ON DASHBOARD");
        root.addView(accept,new LinearLayout.LayoutParams(-1,dp(52)));
        accept.setOnClickListener(v->{
            if(Float.isNaN(lastRpm)||!Float.isFinite(lastRpm)){
                new AlertDialog.Builder(this).setMessage("No reliable estimate has been obtained yet. Calibrate the tachometer and aim at its cyan needle.")
                    .setPositiveButton("OK",null).show();
                return;
            }
            Intent data=new Intent();
            data.putExtra("optical_rpm",lastRpm);
            data.putExtra("optical_confidence",lastConfidence);
            data.putExtra("optical_source",readingSource);
            setResult(RESULT_OK,data);
            finish();
        });
        pad(root,8);
        root.addView(label("The app does not know your actual engine RPM. It calculates an estimate from the needle's visible angle. Glare, color, focus or calibration can make it inaccurate. Don't use it for diagnostic or driving decisions.",11,MUTED,false));

        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int w,int h){
                previewAvailable=true;openCameraIfReady();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s){
                previewAvailable=false;closeCamera();return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s){}
        });
        setPrompt();
    }

    @Override public void onResume(){
        super.onResume();resumed=true;
        handler.postDelayed(this::openCameraIfReady,160);
    }
    @Override public void onPause(){
        resumed=false;handler.removeCallbacksAndMessages(null);closeCamera();
        super.onPause();
    }
    private void openCameraIfReady(){
        if(!resumed||!previewAvailable||camera!=null)return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);
            return;
        }
        try{
            CameraManager mgr=(CameraManager)getSystemService(CAMERA_SERVICE);
            if(mgr==null)throw new IllegalStateException("Camera service unavailable");
            String back=null;
            for(String id:mgr.getCameraIdList()){
                CameraCharacteristics ch=mgr.getCameraCharacteristics(id);
                Integer facing=ch.get(CameraCharacteristics.LENS_FACING);
                if(facing!=null&&facing==CameraCharacteristics.LENS_FACING_BACK){back=id;break;}
            }
            if(back==null)throw new IllegalStateException("No rear camera found");
            selectedCamera=back;
            mgr.openCamera(back,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice device){
                    if(!resumed||!previewAvailable){device.close();return;}
                    camera=device;startPreview(device);
                }
                @Override public void onDisconnected(CameraDevice device){
                    if(camera==device)camera=null;device.close();
                    message.setText("Camera disconnected. Exit and reopen the reader.");
                }
                @Override public void onError(CameraDevice device,int error){
                    if(camera==device)camera=null;device.close();
                    message.setText("Camera error "+error+". Close other camera apps and retry.");
                }
            },handler);
        }catch(Exception ex){
            lastCameraError=ex.getClass().getSimpleName();
            message.setText("Cannot start rear camera: "+lastCameraError);
        }
    }
    private void startPreview(CameraDevice device){
        try{
            CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);
            CameraCharacteristics chars=manager.getCameraCharacteristics(selectedCamera);
            StreamConfigurationMap config=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes=config==null?null:config.getOutputSizes(SurfaceTexture.class);
            Size chosen=null;
            if(sizes!=null){
                double best=Double.POSITIVE_INFINITY;
                for(Size s:sizes){
                    if(s.getWidth()>1920||s.getHeight()>1080)continue;
                    // Prefer 4:3 ratio to make dashboard circles closer to circles.
                    double score=Math.abs((double)s.getWidth()/s.getHeight()-4.0/3.0)
                        +Math.abs(s.getWidth()-960)/9600.0;
                    if(score<best){best=score;chosen=s;}
                }
                if(chosen==null && sizes.length>0)chosen=sizes[0];
            }
            SurfaceTexture texture=preview.getSurfaceTexture();
            if(texture==null)return;
            if(chosen!=null)texture.setDefaultBufferSize(chosen.getWidth(),chosen.getHeight());
            outputSurface=new Surface(texture);
            CaptureRequest.Builder builder=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(outputSurface);
            builder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
            device.createCaptureSession(Arrays.asList(outputSurface),
                new CameraCaptureSession.StateCallback(){
                    @Override public void onConfigured(CameraCaptureSession c){
                        if(!resumed||device!=camera){c.close();return;}
                        session=c;
                        try{
                            c.setRepeatingRequest(builder.build(),null,handler);
                            handler.postDelayed(CameraTachometerActivity.this::updateFrame,500);
                        }catch(CameraAccessException e){message.setText("Camera preview failed: "+e.getReason());}
                    }
                    @Override public void onConfigureFailed(CameraCaptureSession c){
                        message.setText("Camera preview configuration failed.");
                    }
                },handler);
        }catch(Exception ex){message.setText("Camera preview error: "+ex.getClass().getSimpleName());}
    }
    private void closeCamera(){
        if(session!=null){try{session.close();}catch(Exception ignored){}session=null;}
        if(camera!=null){try{camera.close();}catch(Exception ignored){}camera=null;}
        if(outputSurface!=null){outputSurface.release();outputSurface=null;}
    }
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] grants){
        super.onRequestPermissionsResult(request,p,grants);
        if(request==CAMERA_PERMISSION){
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)
                openCameraIfReady();
            else message.setText("Camera permission declined. Allow Camera to estimate tachometer RPM.");
        }
    }
    private void resetCalibration(){
        center=null;zeroMark=null;maxMark=null;
        calibrationStep=0;readingEnabled=true;history.clear();
        lastRpm=Float.NaN;lastAngle=Float.NaN;lastConfidence=0;
        valueText.setText("— RPM");sourceText.setText("NOT CALIBRATED");
        pauseButton.setText("PAUSE LIVE CAMERA READING");
        setPrompt();guide.invalidate();
        handler.post(this::updateFrame);
    }
    private void pointTapped(float x,float y){
        if(!previewAvailable)return;
        PointF p=new PointF(x,y);
        if(calibrationStep==0){center=p;calibrationStep=1;}
        else if(calibrationStep==1){
            if(center!=null&&dist(center,p)<dp(35)){
                message.setText("Zero tick must be away from the dial center. Try again.");return;
            }
            zeroMark=p;calibrationStep=2;
        }else if(calibrationStep==2){
            if(center!=null&&dist(center,p)<dp(35)){
                message.setText("Maximum tick must be away from the dial center. Try again.");return;
            }
            maxMark=p;calibrationStep=3;history.clear();readingEnabled=true;
        }else if(calibrationStep==4){
            if(center==null||zeroMark==null||maxMark==null)return;
            float angle=angle(center,p);
            lastRpm=mapRpm(angle);
            lastAngle=angle;
            lastConfidence=0;
            readingSource="manual_tip";
            valueText.setText(String.format(Locale.US,"%,.0f RPM",lastRpm));
            sourceText.setText("MANUAL CAMERA ESTIMATE — NOT VEHICLE DATA");
            confidenceText.setText("Needle tip was selected by you; not an automatic detector.");
            calibrationStep=3;
        }
        setPrompt();guide.invalidate();
    }
    private void setPrompt(){
        if(message==null)return;
        switch(calibrationStep){
            case 0:message.setText("STEP 1/3: Tap the CENTER HUB of the LEFT tachometer.");break;
            case 1:message.setText("STEP 2/3: Tap the tick at 0 RPM on the tachometer arc.");break;
            case 2:message.setText("STEP 3/3: Tap the HIGHEST RPM tick on the tachometer arc.");break;
            case 3:
                float sweep=sweep();
                if(sweep<140||sweep>340){
                    message.setText("Unusual dial angle ("+Math.round(sweep)+"°). Check CENTER, ZERO and MAX positions; use RESET if readings look wrong.");
                }else{
                    message.setText("CALIBRATED. Keep phone steady facing the cyan needle. Live optical RPM updates below.");
                }
                break;
            case 4:message.setText("MANUAL MODE: Tap the tip of the cyan RPM needle.");break;
        }
    }
    private static float dist(PointF a,PointF b){
        return (float)Math.hypot(a.x-b.x,a.y-b.y);
    }
    private static float angle(PointF c,PointF p){
        return (float)((Math.toDegrees(Math.atan2(p.y-c.y,p.x-c.x))+360)%360);
    }
    private float sweep(){
        if(center==null||zeroMark==null||maxMark==null)return 0;
        return (angle(center,maxMark)-angle(center,zeroMark)+360)%360;
    }
    private float mapRpm(float angle){
        float start=angle(center,zeroMark);
        float span=Math.max(1,sweep());
        float travel=(angle-start+360)%360;
        // Allow a small below-zero margin to reduce gauge jitter at idle.
        if(travel>span+30&&travel>330)travel=0;
        return Math.max(0,Math.min(maxRpm,maxRpm*travel/span));
    }

    private void updateFrame(){
        if(!resumed||camera==null||preview==null||!readingEnabled)return;
        try{
            if(calibrationStep==3&&preview.isAvailable()){
                // Bitmap is sampled from the preview surface only; no storage,
                // network upload or video capture. All processing remains local.
                int vw=preview.getWidth(),vh=preview.getHeight();
                if(vw>0&&vh>0){
                    final int width=Math.min(540,vw);
                    final int height=Math.max(1,Math.round((float)vh*width/vw));
                    Bitmap frame=preview.getBitmap(width,height);
                    if(frame!=null){
                        VisionResult estimate=detect(frame,vw,vh);
                        frame.recycle();
                        if(estimate!=null&&estimate.confidence>=0.22f){
                            history.addLast(mapRpm(estimate.angle));
                            if(history.size()>5)history.removeFirst();
                            ArrayList<Float> sorted=new ArrayList<>(history);
                            Collections.sort(sorted);
                            lastRpm=sorted.get(sorted.size()/2);
                            lastAngle=estimate.angle;
                            lastConfidence=estimate.confidence;
                            readingSource="auto_cyan_needle";
                            valueText.setText(String.format(Locale.US,"%,.0f RPM",lastRpm));
                            sourceText.setText("LIVE CAMERA ESTIMATE • NOT ECU RPM");
                            confidenceText.setText(String.format(Locale.US,
                                "Cyan needle match: %.0f%% • calibrated range %,d RPM",
                                estimate.confidence*100,maxRpm));
                            guide.invalidate();
                        }else{
                            history.clear();
                            // Do not leave a stale number that seems live.
                            lastRpm=Float.NaN;
                            lastAngle=Float.NaN;
                            lastConfidence=0;
                            valueText.setText("— RPM");
                            sourceText.setText("NO RELIABLE NEEDLE DETECTED");
                            confidenceText.setText("Aim at the LEFT tachometer; avoid glare, reposition and recalibrate.");
                            guide.invalidate();
                        }
                    }
                }
            }
        }catch(Exception ex){
            confidenceText.setText("Frame analysis unavailable: "+ex.getClass().getSimpleName());
        }finally{
            if(resumed&&readingEnabled)
                handler.postDelayed(this::updateFrame,450);
        }
    }

    private VisionResult detect(Bitmap b,int viewWidth,int viewHeight){
        if(center==null||zeroMark==null||maxMark==null)return null;
        float sx=(float)b.getWidth()/viewWidth,sy=(float)b.getHeight()/viewHeight;
        float cx=center.x*sx, cy=center.y*sy;
        float radius=(dist(center,zeroMark)+dist(center,maxMark))*0.5f*sx;
        float zero=angle(center,zeroMark),range=sweep();
        if(radius<25||range<90)return null;
        int w=b.getWidth(),h=b.getHeight();
        int[] pixels=new int[w*h];b.getPixels(pixels,0,w,0,0,w,h);
        int steps=30;
        float bestScore=0,bestAngle=0;
        // Search only along the calibrated gauge sweep; the camera sees
        // white tick marks too, so color + radial continuity are required.
        for(int deg=0;deg<Math.min(341,(int)Math.ceil(range));deg+=2){
            float theta=(float)Math.toRadians((zero+deg)%360);
            float dx=(float)Math.cos(theta),dy=(float)Math.sin(theta);
            int matched=0,visited=0;
            for(int i=0;i<steps;i++){
                float r=radius*(0.28f+i*(0.59f/(steps-1)));
                float x=cx+dx*r,y=cy+dy*r;
                // Three-pixel cross section tolerant of antialiasing.
                boolean cyan=false;
                for(int cross=-2;cross<=2;cross+=2){
                    int ix=Math.round(x-dy*cross),iy=Math.round(y+dx*cross);
                    if(ix<0||ix>=w||iy<0||iy>=h)continue;
                    int pixel=pixels[iy*w+ix];
                    int red=(pixel>>16)&255,green=(pixel>>8)&255,blue=pixel&255;
                    if(green>=68&&blue>=98
                       &&green>red+30&&blue>red+42
                       &&blue>=green-54&&blue<=green+130){
                        cyan=true;break;
                    }
                }
                visited++;
                if(cyan)matched++;
            }
            float score=visited>0?(float)matched/visited:0;
            if(score>bestScore){bestScore=score;bestAngle=(zero+deg)%360;}
        }
        return bestScore>=0.22f?new VisionResult(bestAngle,bestScore):null;
    }
    private static final class VisionResult{
        final float angle,confidence;
        VisionResult(float angle,float confidence){this.angle=angle;this.confidence=confidence;}
    }

    private final class GuideOverlay extends View{
        final Paint p=new Paint(3);
        GuideOverlay(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            if(center!=null){
                p.setColor(ORANGE);p.setStrokeWidth(dp(2));p.setStyle(Paint.Style.STROKE);
                c.drawCircle(center.x,center.y,dp(10),p);
                c.drawCircle(center.x,center.y,dp(4),p);
            }
            if(zeroMark!=null){marker(c,zeroMark,ORANGE,"0");}
            if(maxMark!=null){marker(c,maxMark,CYAN,"MAX");}
            if(center!=null&&calibrationStep>=3){
                p.setStyle(Paint.Style.STROKE);p.setColor(0x7732d8ee);p.setStrokeWidth(dp(2));
                float r=dist(center,zeroMark);
                RectF arc=new RectF(center.x-r,center.y-r,center.x+r,center.y+r);
                c.drawArc(arc,angle(center,zeroMark),sweep(),false,p);
                if(!Float.isNaN(lastAngle)){
                    float theta=(float)Math.toRadians(lastAngle);
                    p.setColor(0xff70ff8a);p.setStrokeWidth(dp(3));
                    c.drawLine(center.x,center.y,center.x+(float)Math.cos(theta)*r*0.85f,
                        center.y+(float)Math.sin(theta)*r*0.85f,p);
                }
            }
        }
        private void marker(Canvas c,PointF at,int color,String label){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(color);
            c.drawCircle(at.x,at.y,dp(10),p);
            p.setStyle(Paint.Style.FILL);p.setTextSize(dp(13));p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(label,at.x+dp(10),at.y-dp(10),p);
        }
    }
}
