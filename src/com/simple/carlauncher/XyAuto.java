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

    /** System setting the factory FM app updates, e.g. "FM   88.50". */
    static final String FM_TEXT = "fmradio_text";

    /** {band, frequency} from the factory FM app, or null if it has not run yet. */
    static String[] fmStation(Context c) {
        String s = android.provider.Settings.System.getString(c.getContentResolver(), FM_TEXT);
        if (s == null) return null;
        String[] p = s.trim().split("\\s+");
        return p.length == 2 ? p : null;
    }

    /**
     * Next/previous saved station without opening the factory app: its FmBroadcastReceiver takes
     * media buttons. If the radio is not playing yet, open it instead (that turns the tuner on).
     */
    static void radioStep(Context c, boolean next) {
        if (fmStation(c) == null) {
            Ui.launch(c, RADIO);
            return;
        }
        int code = next ? android.view.KeyEvent.KEYCODE_MEDIA_NEXT : android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        for (int action : new int[]{android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP}) {
            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage("com.android.fmradio");
            i.putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(action, code));
            c.sendBroadcast(i);
        }
        Log.d(TAG, "radio step next=" + next);
    }

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
