package com.newswire.vehicleporttester;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Evidence-based, read-only feature inventory for one-click scan report. */
public final class CapabilityResultsActivity extends Activity {
    private static final int BG=0xff0A1324;
    private static final int PANEL=0xff111F34;
    private static final int OUTLINE=0xff2A425E;
    private static final int MINT=0xff3DE4C5;
    private static final int BLUE=0xff6CB7FF;
    private static final int AMBER=0xffFFCD7A;
    private static final int MUTED=0xff9CB1C8;

    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private GradientDrawable background(int color,int stroke){
        GradientDrawable b=new GradientDrawable();
        b.setColor(color);b.setCornerRadius(dp(18));
        if(stroke!=0)b.setStroke(dp(1),stroke);
        return b;
    }
    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void space(LinearLayout root,int height){
        View v=new View(this);
        root.addView(v,new LinearLayout.LayoutParams(1,dp(height)));
    }
    private JSONObject load(){
        try{
            File f=new File(getFilesDir(),OneClickAuditService.FILE);
            if(!f.exists())return null;
            return new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                StandardCharsets.UTF_8));
        }catch(Exception ignored){return null;}
    }
    private String status(String code){
        switch(code){
            case "observed":return "CONFIRMED ON PHONE";
            case "supported_path_detected":return "SUPPORTED ANDROID PATH";
            case "phone_supported":return "PHONE HARDWARE";
            case "adapter_candidate":return "ADAPTER CANDIDATE";
            case "requires_obd_adapter":return "OBD ADAPTER REQUIRED";
            case "needs_permission":return "PERMISSION NEEDED";
            case "not_accessible_from_this_connection":return "NO PUBLIC USB ACCESS";
            case "not_tested_safety":return "NOT TESTED — SAFETY";
            case "not_tested":return "NOT TESTED";
            case "not_observed":return "NOT OBSERVED";
            default:return "NOT VERIFIED";
        }
    }
    private int statusColor(String code){
        if("observed".equals(code))return MINT;
        if(code.startsWith("supported")||"phone_supported".equals(code))return BLUE;
        if(code.startsWith("requires")||"adapter_candidate".equals(code)||
            "needs_permission".equals(code))return AMBER;
        return MUTED;
    }
    private void makeSection(LinearLayout root,JSONArray all,String category,String caption){
        TextView h=text(category.toUpperCase(Locale.US),15,BLUE,true);
        root.addView(h);
        TextView sub=text(caption,12,MUTED,false);
        sub.setPadding(0,dp(3),0,dp(12));
        root.addView(sub);
        if(all==null)return;
        for(int i=0;i<all.length();i++){
            JSONObject item=all.optJSONObject(i);
            if(item==null||!category.equals(item.optString("category")))continue;
            LinearLayout card=vertical();
            card.setPadding(dp(15),dp(13),dp(15),dp(14));
            card.setBackground(background(PANEL,OUTLINE));
            LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);
            margin.bottomMargin=dp(10);
            root.addView(card,margin);
            TextView title=text(item.optString("title",""),15,Color.WHITE,true);
            card.addView(title);
            TextView label=text(status(item.optString("status")),11,
                statusColor(item.optString("status")),true);
            label.setPadding(0,dp(5),0,dp(6));
            card.addView(label);
            TextView evidence=text(item.optString("evidence"),13,0xffC7D7E8,false);
            card.addView(evidence);
            String limitation=item.optString("limitation","");
            if(!limitation.isEmpty()){
                TextView small=text(limitation,12,MUTED,false);
                small.setPadding(0,dp(7),0,0);
                card.addView(small);
            }
        }
        space(root,13);
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(BG);
        LinearLayout root=vertical();
        root.setPadding(dp(20),dp(28),dp(20),dp(28));
        scroll.addView(root);
        setContentView(scroll);
        TextView eyebrow=text("VEHICLE CAPABILITY EXPLORER  •  v0.8.2",12,MINT,true);
        root.addView(eyebrow);
        space(root,8);
        TextView title=text("What can we actually do?",27,Color.WHITE,true);
        root.addView(title);
        space(root,8);
        TextView description=text(
            "Evidence from your phone and the Explorer's USB connection. "
            +"Only verified measurements are labeled confirmed — this is not "
            +"a claim of access to Ford's computers.",14,MUTED,false);
        root.addView(description);
        space(root,20);

        JSONObject report=load();
        if(report==null){
            TextView missing=text("No completed full scan has been saved. Return and press SCAN EVERYTHING.",16,AMBER,true);
            root.addView(missing);
        }else{
            JSONObject matrix=report.optJSONObject("capability_map");
            if(matrix==null){
                TextView missing=text("This report predates the new capability inventory. Run SCAN EVERYTHING again.",15,AMBER,true);
                root.addView(missing);
            }else{
                LinearLayout top=vertical();
                top.setBackground(background(0xff142D3D,0xff286C69));
                top.setPadding(dp(17),dp(17),dp(17),dp(17));
                root.addView(top);
                TextView overview=text("SCAN RESULTS",13,MINT,true);
                top.addView(overview);
                space(top,9);
                TextView counts=text(
                    matrix.optInt("observed_count")+" directly observed  •  "
                    +matrix.optInt("supported_path_count")+" possible Android paths",17,Color.WHITE,true);
                top.addView(counts);
                space(top,5);
                top.addView(text(
                    matrix.optInt("needs_adapter_or_permission_count")+" need hardware/permission  •  "
                    +matrix.optInt("unverified_or_unavailable_count")+" not verified",
                    13,MUTED,false));
                top.addView(text("Vehicle: 2021 Ford Explorer Limited (user-reported)",12,MUTED,false));
                space(root,20);

                JSONObject comparison=report.optJSONObject("guided_usb_comparison");
                if(comparison!=null){
                    TextView guidetitle=text("GUIDED SCREEN & CONTROL COMPARISON",15,BLUE,true);
                    root.addView(guidetitle);
                    space(root,7);
                    root.addView(text("Changes in USB message sizes or rates are clues, "
                        +"not decoded Ford data or evidence of control access.",
                        12,MUTED,false));
                    space(root,12);
                    JSONArray phases=comparison.optJSONArray("phases");
                    if(phases==null){
                        root.addView(text("Guided stages unavailable: "+
                            comparison.optString("reason","No sustained USB capture."),
                            13,AMBER,true));
                        space(root,16);
                    }else{
                        for(int n=0;n<phases.length();n++){
                            JSONObject p=phases.optJSONObject(n);
                            if(p==null)continue;
                            LinearLayout stageCard=vertical();
                            stageCard.setBackground(background(PANEL,OUTLINE));
                            stageCard.setPadding(dp(14),dp(12),dp(14),dp(13));
                            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                            lp.bottomMargin=dp(9);
                            root.addView(stageCard,lp);
                            stageCard.addView(text((n+1)+". "+p.optString("name"),14,Color.WHITE,true));
                            stageCard.addView(text(
                                p.optDouble("bytes_per_second",0)+" bytes/s  •  "+
                                p.optInt("read_events")+" reads",12,MINT,false));
                            String delta=p.optString("traffic_change_vs_baseline");
                            String indicator=p.optBoolean("action_confirmed_by_user",false)
                                ?"Action marked done by user"
                                :"Action NOT confirmed by user";
                            stageCard.addView(text(n==0?"Baseline window":
                                delta.replace('_',' ')+" • "+indicator,
                                12,MUTED,false));
                        }
                        space(root,11);
                    }
                }

                JSONObject inv=report.optJSONObject("vehicle_system_inventory");
                if(inv!=null) {
                    space(root,5);
                    LinearLayout inventoryCard=vertical();
                    inventoryCard.setBackground(background(0xff15293B,0xff256979));
                    inventoryCard.setPadding(dp(16),dp(16),dp(16),dp(17));
                    root.addView(inventoryCard);
                    inventoryCard.addView(text("EXPLORE EVERY SYSTEM",12,MINT,true));
                    space(inventoryCard,8);
                    inventoryCard.addView(text(
                        inv.optInt("total_feature_candidates")+
                        " potential data points & functions across Ford systems",
                        20,Color.WHITE,true));
                    inventoryCard.addView(text(
                        "Each has separate read and control access evidence. "
                        +"Only actual Android observations count as verified.",
                        12,MUTED,false));
                    space(inventoryCard,12);
                    Button browse=new Button(this);
                    browse.setText("VIEW ALL SYSTEMS & CONTROL POSSIBILITIES");
                    browse.setAllCaps(false);
                    browse.setTextSize(13);
                    browse.setTextColor(BG);
                    browse.setBackground(background(MINT,0));
                    browse.setOnClickListener(v->startActivity(
                        new android.content.Intent(this,SystemInventoryActivity.class)));
                    inventoryCard.addView(browse,new LinearLayout.LayoutParams(-1,dp(55)));
                    space(root,20);
                }

                JSONArray capabilities=matrix.optJSONArray("capabilities");
                makeSection(root,capabilities,"USB & CONNECTION",
                    "Measured using Android's public USB APIs");
                makeSection(root,capabilities,"SCREEN & APPS",
                    "Android Auto and phone-side possibilities; Ford screen not controlled");
                makeSection(root,capabilities,"VEHICLE SENSORS",
                    "Real sensor/ECU readings need a compatible diagnostic connection");
                makeSection(root,capabilities,"CONTROLS & SETTINGS",
                    "Vehicle control paths are not enabled by this USB connection");

                TextView saved=text("REPORT LOCATION",12,MINT,true);
                root.addView(saved);
                root.addView(text(
                    report.optString("saved_downloads_path",
                        "In-app copy available; use EXPORT JSON REPORT on main screen"),
                    13,MUTED,false));
                space(root,10);
                root.addView(text(
                    "No vehicle commands were transmitted. An Android USB accessory "
                    +"does not reveal SYNC internals, and this scan cannot authorize "
                    +"climate, locks, safety systems or ECU programming.",
                    12,AMBER,false));
            }
        }
        space(root,20);
        Button close=new Button(this);
        close.setAllCaps(false);
        close.setText("Back to Vehicle Capability Explorer");
        close.setTextColor(BG);
        close.setTextSize(15);
        close.setBackground(background(MINT,0));
        close.setOnClickListener(v->finish());
        root.addView(close,new LinearLayout.LayoutParams(-1,dp(56)));
    }
}
