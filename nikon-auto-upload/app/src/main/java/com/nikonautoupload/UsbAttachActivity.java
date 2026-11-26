package com.nikonautoupload;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * Invisible screen Android opens when a camera is plugged in (USB_DEVICE_ATTACHED).
 * Opening the app this way gives it USB access without a second prompt (and "always" when the
 * user ticks that box), then the background service takes over and this closes immediately.
 */
public class UsbAttachActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try{
            Intent i=new Intent(this,DirectTransferService.class).putExtra("command","usb_check");
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("receiver_running",true).apply();
        }catch(Exception ignored){}
        finish();
    }
}
