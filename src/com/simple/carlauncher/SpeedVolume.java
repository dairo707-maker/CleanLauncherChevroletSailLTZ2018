package com.simple.carlauncher;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * Raises the music volume with speed to compensate road noise.
 *
 * The base volume is the one the user has while slow/stopped. Every "band" km/h adds one
 * volume step (up to MAX_STEPS). Hysteresis avoids flapping around a threshold and changes are
 * spaced at least 3 s apart. If the volume is not what we last set, the user changed it by hand:
 * the new base becomes that volume minus the current offset.
 */
public class SpeedVolume extends Service implements LocationListener {

    private static final String TAG = "SpeedVolume";
    static final int DEFAULT_BAND_KMH = 25;
    static final int MAX_STEPS = 4;
    private static final float STOP_KMH = 5f;
    private static final float HYST_KMH = 5f;
    private static final long MIN_CHANGE_MS = 3000;

    private LocationManager lm;
    private AudioManager audio;
    private int base = -1;      // volume without the speed boost
    private int offset;         // steps currently added
    private int expected = -1;  // volume we believe the stream has
    private long lastChange;

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            } catch (Exception e) {
                Log.w(TAG, "no GPS", e);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onLocationChanged(Location l) {
        if (!enabled(this) || !l.hasSpeed()) return;
        if (l.hasAccuracy() && l.getAccuracy() > 40) return;
        try {
            float kmh = l.getSpeed() * 3.6f;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (base < 0) {
                base = cur;
                offset = 0;
                expected = cur;
            } else if (cur != expected) { // changed by hand (or another app)
                base = Math.max(0, Math.min(max, cur - offset));
                expected = cur;
            }

            int band = band(this);
            int target = offset;
            while (target < MAX_STEPS && kmh >= (target + 1) * band) target++;
            while (target > 0 && kmh < target * band - HYST_KMH) target--;
            if (kmh < STOP_KMH) target = 0;

            long now = SystemClock.elapsedRealtime();
            if (target != offset && now - lastChange >= MIN_CHANGE_MS) {
                int vol = Math.max(0, Math.min(max, base + target));
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                offset = target;
                expected = vol;
                lastChange = now;
            }
        } catch (Exception e) {
            Log.w(TAG, "volume", e);
        }
    }

    public void onStatusChanged(String p, int s, Bundle b) { }
    public void onProviderEnabled(String p) { }
    public void onProviderDisabled(String p) { }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            lm.removeUpdates(this);
            // Leave the volume as the user had it.
            if (offset > 0 && base >= 0
                    && audio.getStreamVolume(AudioManager.STREAM_MUSIC) == expected) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, base, 0);
            }
        } catch (Exception e) {
            Log.w(TAG, "destroy", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- Settings ----------

    static boolean enabled(Context c) {
        return Profile.prefs(c).getBoolean("speed_volume", false);
    }

    /** km/h per extra volume step. */
    static int band(Context c) {
        int b = Profile.prefs(c).getInt("speed_volume_band", DEFAULT_BAND_KMH);
        return Math.max(10, Math.min(60, b));
    }

    /** Starts or stops the service according to the preference. */
    static void sync(Context c) {
        try {
            Intent i = new Intent(c, SpeedVolume.class);
            if (enabled(c)) c.startService(i);
            else c.stopService(i);
        } catch (Exception e) {
            Log.w(TAG, "sync", e);
        }
    }
}
