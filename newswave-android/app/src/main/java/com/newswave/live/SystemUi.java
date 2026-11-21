package com.newswave.live;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.window.OnBackInvokedDispatcher;

/**
 * Shared platform handling for apps that target Android 16 (API 36):
 *  - Edge-to-edge is enforced on Android 15+, so content must be padded away
 *    from the status bar, navigation bar, display cutout and keyboard.
 *  - onBackPressed() is no longer called on Android 16 for targetSdk 36, so
 *    screens register an OnBackInvokedCallback instead (API 33+). Older
 *    Android versions keep using each activity's onBackPressed().
 */
final class SystemUi {
    private SystemUi(){}
    static final int DEFAULT_BG=0xff071422;

    /** Call once from onCreate, after super.onCreate(). */
    static void install(Activity activity){install(activity,DEFAULT_BG);}
    static void install(Activity activity,int background){
        if(Build.VERSION.SDK_INT<35)return; // edge-to-edge is only enforced from Android 15
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(background|0xff000000));
        View content=activity.findViewById(android.R.id.content);
        if(content==null)return;
        content.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()
                |WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            return insets;
        });
        content.requestApplyInsets();
    }

    /**
     * Routes the system Back gesture/button to handler on Android 13+.
     * The handler must always finish or navigate; it replaces the default.
     */
    static void onBack(Activity activity,Runnable handler){
        if(Build.VERSION.SDK_INT<33)return; // onBackPressed() covers older versions
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,handler::run);
    }
}
