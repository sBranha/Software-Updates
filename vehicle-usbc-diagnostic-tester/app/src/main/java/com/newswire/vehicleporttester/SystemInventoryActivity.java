package com.newswire.vehicleporttester;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SystemInventoryActivity extends Activity {
    private static final int BACKGROUND=0xff0A1324;
    private static final int CARD=0xff14253B;
    private static final int BORDER=0xff304660;
    private static final int MINT=0xff4DE4C5;
    private static final int BLUE=0xff81BFFD;
    private static final int AMBER=0xffFFCE86;
    private static final int MUTED=0xffADC0D3;
    private JSONArray inventory=new JSONArray();
    private LinearLayout results;
    private TextView countLabel;
    private Button loadMore;
    private EditText search;
    private String filter="all";
    private int visible=30;

    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private GradientDrawable bg(int color){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1),BORDER);
        return d;
    }
    private LinearLayout column(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;
    }
    private TextView text(String str,int sp,int color,boolean strong){
        TextView t=new TextView(this);
        t.setText(str);t.setTextSize(sp);t.setTextColor(color);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void space(LinearLayout c,int dp){
        c.addView(new android.view.View(this),new LinearLayout.LayoutParams(1,dp(dp)));
    }
    private JSONObject report(){
        try{
            File file=new File(getFilesDir(),OneClickAuditService.FILE);
            return new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8));
        }catch(Exception e){return null;}
    }
    private static String readable(String status){
        if("read_verified".equals(status))return "USB BYTES VERIFIED";
        if("interface_observed".equals(status))return "INTERFACE FOUND";
        if("blocked_usb_permission".equals(status))return "USB PERMISSION REQUIRED";
        if("phone_service_declarations_observed".equals(status))return "PHONE APP SERVICE FOUND";
        if("phone_hardware_observed".equals(status))return "PHONE HARDWARE PRESENT";
        if("adapter_name_candidate".equals(status))return "ADAPTER NAME FOUND";
        if("not_observed".equals(status))return "NOT OBSERVED";
        return "NOT VERIFIED ON VEHICLE";
    }
    private static String writable(String status){
        if("not_tested_safety_critical".equals(status))return "NOT TESTED: SAFETY-CRITICAL";
        return "CONTROL NOT VERIFIED";
    }
    private static boolean measured(String status){
        return status.equals("read_verified")||status.equals("interface_observed")
            ||status.equals("phone_service_declarations_observed")
            ||status.equals("phone_hardware_observed");
    }
    private static boolean needs(String status){
        return status.equals("blocked_usb_permission")||status.equals("adapter_name_candidate");
    }
    private boolean accept(JSONObject obj,String phrase){
        if(obj==null)return false;
        String read=obj.optString("read_access","");
        if("observed".equals(filter)&&!measured(read))return false;
        if("needs_access".equals(filter)&&!needs(read))return false;
        if("unverified".equals(filter)&&(measured(read)||needs(read)))return false;
        if(phrase.isEmpty())return true;
        String searchable=(obj.optString("system")+" "+obj.optString("name")+" "
            +obj.optString("id")+" "+obj.optString("probable_access_path"))
            .toLowerCase(Locale.US);
        return searchable.contains(phrase);
    }
    private TextView groupTag(JSONObject obj){
        TextView group=text(obj.optString("system","System").toUpperCase(Locale.US),
            11,BLUE,true);
        group.setPadding(0,0,0,dp(6));
        return group;
    }
    private void addRow(JSONObject entry){
        LinearLayout card=column();
        card.setPadding(dp(15),dp(13),dp(15),dp(15));
        card.setBackground(bg(CARD));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.bottomMargin=dp(10);
        results.addView(card,cp);
        card.addView(groupTag(entry));
        card.addView(text(entry.optString("name"),16,Color.WHITE,true));
        space(card,8);
        String read=entry.optString("read_access");
        TextView readInfo=text("READ: "+readable(read),12,
            measured(read)?MINT:(needs(read)?AMBER:MUTED),true);
        card.addView(readInfo);
        TextView control=text("CONTROL: "+writable(entry.optString("control_access")),
            12,entry.optBoolean("safety_critical")?AMBER:MUTED,true);
        card.addView(control);
        space(card,7);
        card.addView(text(entry.optString("evidence"),12,0xffD1DFED,false));
        space(card,5);
        card.addView(text("Investigate via: "+entry.optString("probable_access_path"),
            12,MUTED,false));
        card.addView(text("Next: "+entry.optString("next_requirement"),12,MUTED,false));
    }
    private void refresh(){
        if(results==null)return;
        results.removeAllViews();
        String phrase=search.getText().toString().trim().toLowerCase(Locale.US);
        int matches=0,shown=0;
        for(int i=0;i<inventory.length();i++){
            JSONObject item=inventory.optJSONObject(i);
            if(!accept(item,phrase))continue;
            matches++;
            if(shown<visible){addRow(item);shown++;}
        }
        countLabel.setText(matches+" matching system/data candidates • "+shown+" shown");
        loadMore.setVisibility(matches>visible?android.view.View.VISIBLE:android.view.View.GONE);
        if(matches==0)results.addView(text("No matching items. Try another term.",14,MUTED,false));
    }
    @Override protected void onCreate(Bundle bundle){
        super.onCreate(bundle);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root=column();
        root.setPadding(dp(18),dp(25),dp(18),dp(20));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("VEHICLE CAPABILITY EXPLORER  •  v0.8.1",12,MINT,true));
        space(root,9);
        root.addView(text("Every system we can investigate",24,Color.WHITE,true));
        space(root,8);
        root.addView(text("This inventory is a test plan based on possible vehicle functions, "
            +"NOT proof they are installed. Only directly observed phone/USB data is shown "
            +"as verified. No commands were sent to the Explorer.",13,MUTED,false));
        space(root,16);

        JSONObject data=report();
        JSONObject catalog=data==null?null:data.optJSONObject("vehicle_system_inventory");
        inventory=catalog==null?new JSONArray():catalog.optJSONArray("entries");
        if(inventory==null)inventory=new JSONArray();
        TextView total=text(inventory.length()+" FUNCTIONS AND DATA POINTS",18,MINT,true);
        root.addView(total);
        space(root,10);
        search=new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("Search: cameras, RPM, lights, locks...");
        search.setHintTextColor(MUTED);
        search.setTextColor(Color.WHITE);
        search.setPadding(dp(13),dp(8),dp(13),dp(8));
        search.setBackground(bg(CARD));
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int b,int c){
                visible=30;refresh();
            }
            @Override public void afterTextChanged(Editable e){}
        });
        space(root,10);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(tabs);
        for(String[] button:new String[][]{
            {"ALL","all"},{"FOUND","observed"},{"ACCESS","needs_access"},{"UNKNOWN","unverified"}}){
            Button b=new Button(this);
            b.setText(button[0]);
            b.setAllCaps(false);
            b.setTextSize(10);
            b.setTextColor(Color.WHITE);
            b.setBackground(bg(CARD));
            LinearLayout.LayoutParams tab=new LinearLayout.LayoutParams(0,dp(43),1f);
            tab.rightMargin=dp(4);
            tabs.addView(b,tab);
            b.setOnClickListener(v->{filter=button[1];visible=30;refresh();});
        }
        space(root,12);
        countLabel=text("",12,MUTED,false);
        root.addView(countLabel);
        space(root,12);
        results=column();
        root.addView(results);
        loadMore=new Button(this);
        loadMore.setText("SHOW MORE SYSTEMS");
        loadMore.setAllCaps(false);
        loadMore.setTextColor(Color.WHITE);
        loadMore.setBackground(bg(CARD));
        root.addView(loadMore,new LinearLayout.LayoutParams(-1,dp(54)));
        loadMore.setOnClickListener(v->{visible+=30;refresh();});
        refresh();
        space(root,20);
        Button back=new Button(this);
        back.setAllCaps(false);
        back.setText("BACK TO SCAN RESULTS");
        back.setTextColor(BACKGROUND);
        GradientDrawable b=new GradientDrawable();
        b.setColor(MINT);b.setCornerRadius(dp(15));
        back.setBackground(b);
        root.addView(back,new LinearLayout.LayoutParams(-1,dp(54)));
        back.setOnClickListener(v->finish());
    }
}
