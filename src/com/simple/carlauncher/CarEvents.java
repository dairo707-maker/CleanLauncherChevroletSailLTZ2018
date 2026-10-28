package com.simple.carlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Wakes the launcher's services from the head unit's own signals. The XY AUTO unit "quick boots":
 * ignition off suspends it and kills most apps, ignition on resumes without BOOT_COMPLETED, so the
 * odometer, speedometer and headlight tracking used to stay dead until the launcher was restarted.
 * Declared in the manifest (works because the app targets API 25), so it runs even if we were killed.
 */
public class CarEvents extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        Log.d("CarEvents", String.valueOf(a));
        if (XyAuto.LAMP.equals(a)) {
            XyAuto.onLamp(c, i);
            return;
        }
        if (XyAuto.ACC_OFF.equals(a)) return;
        startAll(c);
    }

    /** Idempotent: each service ignores a start while already running. */
    static void startAll(Context c) {
        try {
            Ui.startService(c, GreeterService.class);
            Ui.startService(c, Odometer.class);
            SpeedVolume.sync(c);
            DriverAssist.sync(c);
        } catch (Exception e) {
            Log.w("CarEvents", "startAll", e);
        }
    }
}
