package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.util.Log;

/**
 * Global (session 0) audio effects for the XY AUTO unit. The effect objects must stay alive,
 * otherwise the system drops the effect, so they live in static fields.
 */
final class XyEq {

    private XyEq() { }

    private static final String TAG = "XyEq";

    static final String[] PRESET_NAMES = {"Estándar", "Pop", "Rock", "Clásica", "Jazz", "Dance",
            "Película", "Voz/Radio", "Personalizado"};
    static final int CUSTOM = 8;

    /** Reference curves in dB at 60, 230, 910, 3600, 14000 Hz; interpolated onto the real bands. */
    private static final int[] REF_HZ = {60, 230, 910, 3600, 14000};
    private static final float[][] CURVES = {
            {0, 0, 0, 0, 0},
            {-1, 3, 4, 2, -1},
            {4, 2, -2, 2, 4},
            {4, 3, -2, 2, 3},
            {3, 1, -1, 2, 4},
            {6, 4, 0, 3, 2},
            {4, 2, 0, 3, 4},
            {-3, -1, 3, 3, -1},
    };

    private static Equalizer eq;
    private static BassBoost bass;
    private static Virtualizer virt;
    private static LoudnessEnhancer loud;
    private static boolean tried;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("xyeq", Context.MODE_PRIVATE);
    }

    /** Creates the effects once; each one is optional. Returns true if at least the EQ works. */
    static synchronized boolean ensure() {
        if (tried) return eq != null;
        tried = true;
        try { eq = new Equalizer(0, 0); } catch (Throwable t) { Log.w(TAG, "no equalizer: " + t); }
        try { bass = new BassBoost(0, 0); } catch (Throwable t) { Log.w(TAG, "no bassboost: " + t); }
        try { virt = new Virtualizer(0, 0); } catch (Throwable t) { Log.w(TAG, "no virtualizer: " + t); }
        try { loud = new LoudnessEnhancer(0); } catch (Throwable t) { Log.w(TAG, "no loudness: " + t); }
        return eq != null;
    }

    static boolean hasBass() { return bass != null; }
    static boolean hasVirtualizer() { return virt != null; }
    static boolean hasLoudness() { return loud != null; }

    static int bandCount() {
        return eq == null ? 0 : eq.getNumberOfBands();
    }

    /** Centre frequency of a band in Hz. */
    static int centerHz(int band) {
        return eq.getCenterFreq((short) band) / 1000;
    }

    static String bandLabel(int band) {
        int hz = centerHz(band);
        if (hz < 1000) return String.valueOf(hz);
        float k = hz / 1000f;
        return k >= 10 ? Math.round(k) + "k" : String.format(java.util.Locale.US, "%.1fk", k);
    }

    static int minMb() { return eq.getBandLevelRange()[0]; }
    static int maxMb() { return eq.getBandLevelRange()[1]; }

    // ---------- Settings ----------

    static int preset(Context c) { return prefs(c).getInt("preset", 0); }
    static int bassStrength(Context c) { return prefs(c).getInt("bass", 0); }
    static int virtStrength(Context c) { return prefs(c).getInt("virt", 0); }
    static boolean loudOn(Context c) { return prefs(c).getBoolean("loud", false); }

    /** Saved level (millibels) of a band for the custom preset. */
    static int customLevel(Context c, int band) {
        String s = prefs(c).getString("levels", "");
        String[] p = s.split(",");
        try {
            return band < p.length && !p[band].isEmpty() ? Integer.parseInt(p[band]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Level in millibels a preset gives to a band (log-frequency interpolation of its curve). */
    static int presetLevel(Context c, int preset, int band) {
        if (preset == CUSTOM) return customLevel(c, band);
        float[] cv = CURVES[preset];
        double f = Math.max(REF_HZ[0], Math.min(REF_HZ[4], centerHz(band)));
        int i = 0;
        while (i < 3 && f > REF_HZ[i + 1]) i++;
        double t = (Math.log(f) - Math.log(REF_HZ[i])) / (Math.log(REF_HZ[i + 1]) - Math.log(REF_HZ[i]));
        return clampMb(Math.round((cv[i] + (cv[i + 1] - cv[i]) * (float) t) * 100f));
    }

    private static int clampMb(int mb) {
        return Math.max(minMb(), Math.min(maxMb(), mb));
    }

    static void setPreset(Context c, int preset) {
        SharedPreferences.Editor e = prefs(c).edit().putBoolean("configured", true).putInt("preset", preset);
        e.apply();
        apply(c);
    }

    static void setCustomLevel(Context c, int band, int mb) {
        int n = bandCount();
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < n; b++) {
            if (b > 0) sb.append(',');
            sb.append(b == band ? mb : presetLevel(c, preset(c), b));
        }
        prefs(c).edit().putBoolean("configured", true).putInt("preset", CUSTOM)
                .putString("levels", sb.toString()).apply();
        apply(c);
    }

    static void setBass(Context c, int strength) {
        prefs(c).edit().putBoolean("configured", true).putInt("bass", strength).apply();
        apply(c);
    }

    static void setVirtualizer(Context c, int strength) {
        prefs(c).edit().putBoolean("configured", true).putInt("virt", strength).apply();
        apply(c);
    }

    static void setLoudness(Context c, boolean on) {
        prefs(c).edit().putBoolean("configured", true).putBoolean("loud", on).apply();
        apply(c);
    }

    /**
     * Applies the saved settings to the global mix. Call at boot (GreeterService) and from
     * MainActivity; does nothing until the user has touched the equalizer once.
     */
    static void apply(Context c) {
        if (!prefs(c).getBoolean("configured", false)) return;
        if (!ensure()) return;
        try {
            int p = preset(c);
            for (int b = 0; b < bandCount(); b++) {
                eq.setBandLevel((short) b, (short) presetLevel(c, p, b));
            }
            eq.setEnabled(true);
        } catch (Throwable t) {
            Log.w(TAG, "eq apply failed: " + t);
        }
        try {
            if (bass != null) {
                int s = bassStrength(c);
                if (bass.getStrengthSupported()) bass.setStrength((short) s);
                bass.setEnabled(s > 0);
            }
        } catch (Throwable t) {
            Log.w(TAG, "bass apply failed: " + t);
        }
        try {
            if (virt != null) {
                int s = virtStrength(c);
                if (virt.getStrengthSupported()) virt.setStrength((short) s);
                virt.setEnabled(s > 0);
            }
        } catch (Throwable t) {
            Log.w(TAG, "virtualizer apply failed: " + t);
        }
        try {
            if (loud != null) {
                loud.setTargetGain(600);
                loud.setEnabled(loudOn(c));
            }
        } catch (Throwable t) {
            Log.w(TAG, "loudness apply failed: " + t);
        }
    }
}
