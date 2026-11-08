package com.newswire.vehicleporttester;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

/** Records Android's actual response to a user-initiated USB permission request. */
public final class UsbPermissionReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.newswire.vehicleporttester.USB_PERMISSION";
    public static final String RESULT = "com.newswire.vehicleporttester.USB_PERMISSION_RESULT";
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
        context.getSharedPreferences("usb_permission_results", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("last_granted", granted)
                .putLong("last_checked_at_ms", System.currentTimeMillis())
                .apply();
        Intent notice = new Intent(RESULT);
        notice.setPackage(context.getPackageName());
        notice.putExtra("granted",granted);
        context.sendBroadcast(notice);
    }
}
