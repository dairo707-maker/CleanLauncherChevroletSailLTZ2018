package com.simple.carlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
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

    // ---------- Factory FM app (com.android.fmradio), from its disassembled receivers ----------

    private static final String FM_PKG = "com.android.fmradio";
    private static final String FM_ACTIVITY = "com.android.fmradio/.FmMainActivity";
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    /*
     * Disassembly of the factory FM app: next/previous/seek are handled by a receiver that lives
     * inside FmMainActivity (registered in onCreate, removed in onDestroy), so they only work while
     * that screen exists, even in the background. "xy.setfm.freq" also goes to a manifest receiver
     * that always brings FmMainActivity to the front. So: keep FmMainActivity alive behind ours,
     * and after anything that surfaces it, bring our screen back.
     */

    /** Sends a control action; if the station did not change, the FM screen was gone: revive and retry. */
    private static void fmAction(Context c, String action) {
        String before = Settings.System.getString(c.getContentResolver(), FM_TEXT);
        c.sendBroadcast(new Intent(action).setPackage(FM_PKG));
        Log.d(TAG, action);
        main.postDelayed(() -> {
            String after = Settings.System.getString(c.getContentResolver(), FM_TEXT);
            if (after != null && !after.equals(before)) return;
            Log.d(TAG, "no change after " + action + ": reviving FM screen");
            wakeFm(c);
            main.postDelayed(() -> c.sendBroadcast(new Intent(action).setPackage(FM_PKG)), 2500);
        }, 1800);
    }

    /** Starts the factory FM screen (tuner on, its receiver registered) and returns to the caller's screen. */
    static void wakeFm(Context c) {
        Ui.launch(c, FM_ACTIVITY);
        bringBack(c, 1200);
    }

    private static void bringBack(Context c, long delayMs) {
        if (!(c instanceof android.app.Activity)) return;
        android.app.Activity a = (android.app.Activity) c;
        main.postDelayed(() -> a.startActivity(new Intent(a, a.getClass())
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)), delayMs);
    }

    /** Next/previous saved station (the factory list). */
    static void radioStep(Context c, boolean next) {
        fmAction(c, next ? "xy.android.nextmedia" : "xy.android.previousmedia");
    }

    /** Hardware seek to the next/previous station with signal. */
    static void radioSeek(Context c, boolean up) {
        fmAction(c, up ? "xy.android.seek_next" : "xy.android.seek_prev");
    }

    /**
     * Tunes to freq10kHz (9450 = 94.50 MHz). The FM app takes extra "fm" as digits in 100 kHz
     * ("945"), range 875-1080. Its manifest receiver surfaces the FM screen, so come back after.
     */
    static boolean tune(Context c, int freq10kHz) {
        Intent i = new Intent("xy.setfm.freq").setPackage(FM_PKG);
        i.putExtra("fm", String.valueOf(freq10kHz / 10));
        c.sendBroadcast(i);
        bringBack(c, 900);
        Log.d(TAG, "tune " + freq10kHz);
        return true;
    }

    // ---------- Dialing ----------

    /**
     * Calls through the phone connected to the factory Bluetooth app: its BluetoothReceiver dials
     * the number in extra "XY_BLUETOOTHCALL_OUT" over the hands-free profile.
     */
    static void dial(Context c, String number) {
        if (number == null || number.isEmpty()) return;
        Intent i = new Intent("xy.bluetoothcall.out").setPackage("com.autochips.bluetooth");
        i.putExtra("XY_BLUETOOTHCALL_OUT", number);
        c.sendBroadcast(i);
        Log.d(TAG, "dial " + number);
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
    private static long lastLampAt;

    static synchronized void onLamp(Context c, Intent i) {
        // Both CarEvents and GreeterService hear the broadcast: handle each change once.
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastLampAt < 500) return;
        lastLampAt = now;
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
