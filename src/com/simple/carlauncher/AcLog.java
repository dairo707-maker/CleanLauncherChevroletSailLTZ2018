package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The Sail's climate panel is mechanical (knobs and an A/C button), so the radio cannot see it:
 * the driver marks A/C on/off from the home screen. Each session goes to aire.csv for Power BI.
 */
final class AcLog {

    private AcLog() { }

    static final String FILE = "aire.csv";
    private static final String HEADER = "fecha,hora_inicio,minutos,km_inicio,km_fin";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("aire", Context.MODE_PRIVATE);
    }

    static boolean isOn(Context c) {
        return prefs(c).getLong("since", 0) != 0;
    }

    /** Minutes the current session has run (0 when off). */
    static int minutesOn(Context c) {
        long since = prefs(c).getLong("since", 0);
        return since == 0 ? 0 : (int) ((System.currentTimeMillis() - since) / 60000);
    }

    static void toggle(Context c) {
        if (isOn(c)) stop(c);
        else prefs(c).edit().putLong("since", System.currentTimeMillis())
                .putInt("km", Odometer.currentKm(c)).apply();
    }

    /** Closes the session, e.g. from the button or when the car is switched off. */
    static void stop(Context c) {
        long since = prefs(c).getLong("since", 0);
        if (since == 0) return;
        Date start = new Date(since);
        int minutes = minutesOn(c);
        String line = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(start) + ","
                + new SimpleDateFormat("HH:mm", Locale.US).format(start) + ","
                + minutes + "," + prefs(c).getInt("km", 0) + "," + Odometer.currentKm(c);
        FuelLog.append(new File(FuelLog.dir(c), FILE), HEADER, line);
        prefs(c).edit().remove("since").apply();
    }
}
