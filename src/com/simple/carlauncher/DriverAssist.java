package com.simple.carlauncher;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * Spoken driver alerts from GPS: fatigue (continuous driving time, long shifts) and a speed limit.
 *
 * Driving time only counts while moving above 10 km/h; a stop of 15 minutes or more resets it.
 * The speed alert fires once after 5 s over the limit and re-arms below limit - 5 km/h.
 */
public class DriverAssist extends Service implements LocationListener {

    private static final String TAG = "DriverAssist";
    static final int DEFAULT_LIMIT_KMH = 80;
    private static final float MOVING_KMH = 10f;
    private static final long REST_MS = 15 * 60000L;
    private static final long OVER_MS = 5000;

    private static volatile long drivingMs;
    private static volatile float kmh;
    private static volatile long lastFixAt;

    private LocationManager lm;
    private long lastFix;      // elapsedRealtime of the previous fix
    private long stopSince;    // when the current stop began (0 = moving)
    private int nextAlertMin = 120;
    private long overSince;
    private boolean armed = true;
    private long alertedShift; // shift start for which 8 h / 10 h warnings were handled
    private boolean warned8, warned10;

    @Override
    public void onCreate() {
        super.onCreate();
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
        Ui.keepRunning(this); // required after every startForegroundService()
        return START_STICKY;
    }

    @Override
    public void onLocationChanged(Location l) {
        if (!l.hasSpeed() || (l.hasAccuracy() && l.getAccuracy() > 40)) return;
        long now = SystemClock.elapsedRealtime();
        float v = l.getSpeed() * 3.6f;
        kmh = v;
        lastFixAt = now;
        try {
            if (fatigueEnabled(this)) fatigue(now, v);
            if (speedEnabled(this)) speed(now, v);
        } catch (Exception e) {
            Log.w(TAG, "alert", e);
        }
        lastFix = now;
    }

    private void fatigue(long now, float v) {
        long gap = lastFix == 0 ? 0 : now - lastFix;
        if (gap >= REST_MS) reset(); // no fixes for a long time: treat as a rest
        if (v > MOVING_KMH) {
            stopSince = 0;
            drivingMs += Math.min(gap, 10000); // cap so a GPS hole does not inflate the time
        } else {
            if (stopSince == 0) stopSince = now;
            if (now - stopSince >= REST_MS) reset();
        }
        int min = drivingMinutes();
        if (min >= nextAlertMin) {
            int h = min / 60;
            Tts.say(this, "Llevas " + (h == 2 ? "dos" : String.valueOf(h))
                    + " horas manejando. Toma una pausa de quince minutos.");
            nextAlertMin = (h + 1) * 60;
        }
        if (ShiftLog.isActive(this)) {
            long id = ShiftLog.startMs(this);
            if (id != alertedShift) {
                alertedShift = id;
                warned8 = warned10 = false;
            }
            double hrs = ShiftLog.hours(this);
            if (hrs >= 10 && !warned10) {
                warned10 = warned8 = true;
                Tts.say(this, "Llevas diez horas de turno. Es hora de terminar y descansar.");
            } else if (hrs >= 8 && !warned8) {
                warned8 = true;
                Tts.say(this, "Llevas ocho horas de turno. Considera terminar pronto.");
            }
        }
    }

    private void reset() {
        drivingMs = 0;
        stopSince = 0;
        nextAlertMin = 120;
    }

    private void speed(long now, float v) {
        int limit = limit(this);
        if (v < limit - 5) {
            armed = true;
            overSince = 0;
        } else if (v > limit) {
            if (overSince == 0) overSince = now;
            if (armed && now - overSince >= OVER_MS) {
                armed = false;
                Tts.say(this, "Velocidad alta");
            }
        } else {
            overSince = 0; // between limit-5 and limit: not over, but not re-armed yet
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
        } catch (Exception e) {
            Log.w(TAG, "destroy", e);
        }
        drivingMs = 0;
        kmh = 0;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- Shared state ----------

    /** Minutes of continuous driving so far (0 when the service is off). */
    static int drivingMinutes() {
        return (int) (drivingMs / 60000);
    }

    /** Last GPS speed in km/h; 0 when there is no recent fix. */
    static int currentKmh() {
        return SystemClock.elapsedRealtime() - lastFixAt > 5000 ? 0 : Math.round(kmh);
    }

    // ---------- Settings ----------

    static boolean fatigueEnabled(Context c) {
        return Profile.prefs(c).getBoolean("fatigue_alert", true);
    }

    static boolean speedEnabled(Context c) {
        return Profile.prefs(c).getBoolean("speed_alert", false);
    }

    static int limit(Context c) {
        return Math.max(30, Math.min(200, Profile.prefs(c).getInt("speed_limit", DEFAULT_LIMIT_KMH)));
    }

    /** Starts or stops the service according to the preferences. */
    static void sync(Context c) {
        try {
            Intent i = new Intent(c, DriverAssist.class);
            if (fatigueEnabled(c) || speedEnabled(c)) Ui.startService(c, DriverAssist.class);
            else c.stopService(i);
        } catch (Exception e) {
            Log.w(TAG, "sync", e);
        }
    }
}
