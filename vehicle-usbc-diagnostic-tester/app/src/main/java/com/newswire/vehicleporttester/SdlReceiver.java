package com.newswire.vehicleporttester;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.smartdevicelink.transport.SdlBroadcastReceiver;
import com.smartdevicelink.transport.TransportConstants;

public final class SdlReceiver extends SdlBroadcastReceiver {
    @Override public void onSdlEnabled(Context context, Intent intent) {
        Intent serviceIntent = new Intent(intent);
        serviceIntent.setClass(context, SdlService.class);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent token = intent.getParcelableExtra(TransportConstants.PENDING_INTENT_EXTRA);
                if (token != null) { token.send(context, 0, serviceIntent); SdlService.record(context,"SDL_ROUTER_SIGNAL","Router signaled an SDL head-unit connection."); }
                else SdlService.record(context,"SDL_ROUTER_NO_START_TOKEN","Router notification had no Android 12+ service start token.");
            } else if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception error) {
            SdlService.record(context,"SDL_RECEIVER_FAILED",error.getClass().getSimpleName());
            // A launch error is visible to the user through the app's local test screen;
            // never attempt fallback takeover of the Android Auto USB accessory.
        }
    }
    @Override public Class<? extends com.smartdevicelink.transport.SdlRouterService> defineLocalSdlRouterClass() {
        return SdlRouterService.class;
    }
}
