package com.newswire.vehicleporttester;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Parked-only Explorer inventory and investigation activity. */
public final class ExplorerResearchActivity extends Activity {
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xff07101c);
        getWindow().setNavigationBarColor(0xff07101c);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(0xff07101c);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14),dp(18),dp(14),dp(18));
        scroll.addView(content);
        setContentView(scroll);
        TextView title=new TextView(this);
        title.setText("MY EXPLORER • RESEARCH");
        title.setTextSize(23);title.setTextColor(0xfff0f7fc);title.setTypeface(null,1);
        content.addView(title);
        TextView sub=new TextView(this);sub.setTextSize(12);sub.setTextColor(0xffa5bdcf);
        sub.setText("Vehicle/software facts from your photos, phone-connected capabilities and every remaining test. Not a live ECU enumeration.");
        content.addView(sub);
        View gap=new View(this);content.addView(gap,new LinearLayout.LayoutParams(1,dp(13)));
        ExplorerResearchPanel panel=new ExplorerResearchPanel(this);
        content.addView(panel.view());
        View gap2=new View(this);content.addView(gap2,new LinearLayout.LayoutParams(1,dp(14)));
        Button warning=new Button(this);
        warning.setText("OPEN NWS WARNING MAP TEST");
        warning.setAllCaps(false);warning.setTextColor(Color.WHITE);
        warning.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff176579));
        content.addView(warning,new LinearLayout.LayoutParams(-1,dp(55)));
        warning.setOnClickListener(v->startActivity(new Intent(this,StormWarningMapLabActivity.class)));
        TextView caution=new TextView(this);
        caution.setText("WARNING MAP: separate phone map, not the factory Ford navigation map. The NWS publishes warning areas, not moving-storm tracks. NewsWire Android Auto overlay integration requires a separate supported weather-app build and in-car testing.");
        caution.setTextSize(12);caution.setTextColor(0xffffb96b);
        content.addView(caution);
    }
}
