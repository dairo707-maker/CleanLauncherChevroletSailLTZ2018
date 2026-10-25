package com.simple.carlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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

    // ---------- Direct tuning / dialing (exact factory APIs still unverified) ----------

    /** Pref "xy_tune_mode": 0 = disabled (open the factory app), 1..3 = which broadcast to try. */
    static final String PREF_TUNE_MODE = "xy_tune_mode";
    /** Pref "xy_dial_mode": 0 = ACTION_DIAL, 1 = xy.bluetoothcall.out broadcast, 2 = ACTION_CALL. */
    static final String PREF_DIAL_MODE = "xy_dial_mode";

    private static final String[] TUNE_ACTIONS = {
            "xy.setfm.freq", "xy.open.freq", "com.yunos.intent.FM_SET_CURRENT"};

    static int tuneMode(Context c) {
        return prefs(c).getInt(PREF_TUNE_MODE, 0);
    }

    static void setTuneMode(Context c, int mode) {
        prefs(c).edit().putInt(PREF_TUNE_MODE, mode).apply();
    }

    /**
     * Tunes the factory FM app to freq10kHz (9450 = 94.50 MHz) with the broadcast chosen by the
     * "xy_tune_mode" pref. Returns false when disabled, so the caller can open the factory app.
     */
    static boolean tune(Context c, int freq10kHz) {
        int mode = tuneMode(c);
        if (mode < 1 || mode > TUNE_ACTIONS.length) return false;
        Intent i = new Intent(TUNE_ACTIONS[mode - 1]);
        i.putExtra("freq", freq10kHz);
        i.putExtra("frequency", freq10kHz);
        c.sendBroadcast(i);
        Log.d(TAG, "tune mode=" + mode + " freq=" + freq10kHz);
        return true;
    }

    static int dialMode(Context c) {
        return prefs(c).getInt(PREF_DIAL_MODE, 0);
    }

    static void setDialMode(Context c, int mode) {
        prefs(c).edit().putInt(PREF_DIAL_MODE, mode).apply();
    }

    /** Starts a call by the "xy_dial_mode" pref; falls back to the dialer, then the factory BT app. */
    static void dial(Context c, String number) {
        if (number == null || number.isEmpty()) return;
        int mode = dialMode(c);
        try {
            if (mode == 1) {
                Intent i = new Intent("xy.bluetoothcall.out");
                // The extra key is unknown: send the likely ones.
                i.putExtra("number", number);
                i.putExtra("phone", number);
                i.putExtra("phonenumber", number);
                c.sendBroadcast(i);
                Log.d(TAG, "dial broadcast " + number);
                return;
            }
            Uri uri = Uri.fromParts("tel", number, null);
            Intent i = new Intent(mode == 2 ? Intent.ACTION_CALL : Intent.ACTION_DIAL, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return;
        } catch (Exception e) {
            Log.w(TAG, "dial mode " + mode + " failed: " + e);
        }
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            Ui.launch(c, BLUETOOTH);
        }
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
