package com.simple.carlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

/**
 * The Sail's XY AUTO head unit (MediaTek AC8227L, Android 8.1), as found in diagnostico/:
 * factory apps to open and the system broadcasts a normal app can listen to.
 */
final class XyAuto {

    private XyAuto() { }

    private static final String TAG = "XyAuto";

    static final String RADIO = "com.acloud.stub.localradio/.QtActivity";
    static final String BLUETOOTH = "com.autochips.bluetooth/.MainBluetoothActivity";
    static final String SETTINGS = "com.xyauto.Settings/.MainActivity";

    static final String ACC_ON = "xy.android.acc.on";
    static final String ACC_OFF = "xy.android.acc.off";
    /** Headlight switch; sent by system_server on every change. Its extras are not documented. */
    static final String LAMP = "xy.xygala.lamplet";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("xyauto", Context.MODE_PRIVATE);
    }

    static boolean lampKnown(Context c) {
        return prefs(c).contains("lamp_on");
    }

    static boolean lampOn(Context c) {
        return prefs(c).getBoolean("lamp_on", false);
    }

    /**
     * Works out the new headlight state: a boolean/number extra if the broadcast carries one, else
     * the "Headlamp" system setting, else flip the last state (each broadcast is one change).
     */
    static void onLamp(Context c, Intent i) {
        Bundle b = i.getExtras();
        Log.d(TAG, "lamplet extras=" + b);
        Boolean on = null;
        if (b != null) {
            for (String k : b.keySet()) {
                Object v = b.get(k);
                if (v instanceof Boolean) on = (Boolean) v;
                else if (v instanceof Number) on = ((Number) v).intValue() != 0;
                else if (v instanceof String && k.toLowerCase().contains("lamp")) on = ((String) v).contains("on");
                if (on != null) break;
            }
        }
        if (on == null) {
            int s = android.provider.Settings.System.getInt(c.getContentResolver(), "Headlamp", -1);
            int prevSetting = prefs(c).getInt("headlamp_setting", -1);
            if (s != -1 && s != prevSetting) on = s != 0;
            prefs(c).edit().putInt("headlamp_setting", s).apply();
        }
        if (on == null) on = !lampOn(c);
        prefs(c).edit().putBoolean("lamp_on", on).apply();
    }
}
