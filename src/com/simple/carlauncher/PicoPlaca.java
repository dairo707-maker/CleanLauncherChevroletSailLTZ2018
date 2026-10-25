package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

/**
 * Cartagena "pico y placa" for private cars. The rotation (last plate digits per weekday) changes
 * every few months, so it is stored in the profile prefs with its validity dates and can be
 * edited in the setup screen. No restriction on weekends and Colombian public holidays.
 */
final class PicoPlaca {

    static final String DEFAULT_ROTATION = "1,2;3,4;5,6;7,8;9,0"; // Monday .. Friday
    static final String DEFAULT_FROM = "2026-10-05";
    static final String DEFAULT_TO = "2027-01-22";

    // Restricted hours (24 h): morning and evening windows.
    private static final int AM_FROM = 7, AM_TO = 9, PM_FROM = 18, PM_TO = 20;

    private PicoPlaca() { }

    // ---------- Stored settings ----------

    private static SharedPreferences prefs(Context c) {
        return Profile.prefs(c);
    }

    /** Last digit of the plate, or -1 when unset. */
    static int plateDigit(Context c) {
        return prefs(c).getInt("placa_digit", -1);
    }

    static void setPlateDigit(Context c, int digit) {
        prefs(c).edit().putInt("placa_digit", digit).apply();
    }

    static String rotation(Context c) {
        return prefs(c).getString("pp_rotation", DEFAULT_ROTATION);
    }

    static String validFrom(Context c) {
        return prefs(c).getString("pp_desde", DEFAULT_FROM);
    }

    static String validTo(Context c) {
        return prefs(c).getString("pp_hasta", DEFAULT_TO);
    }

    static void setRotation(Context c, String rotation, String from, String to) {
        prefs(c).edit().putString("pp_rotation", rotation).putString("pp_desde", from)
                .putString("pp_hasta", to).apply();
    }

    // ---------- Rules ----------

    /** True when the plate digit is restricted today (ignores the time of day). */
    static boolean appliesToday(Context c, Calendar day) {
        int digit = plateDigit(c);
        if (digit < 0 || rotationExpired(c, day)) return false;
        int idx = weekdayIndex(day);
        if (idx < 0 || isHoliday(day)) return false;
        int[][] rot = parseRotation(rotation(c));
        if (rot == null || idx >= rot.length) return false;
        for (int d : rot[idx]) if (d == digit) return true;
        return false;
    }

    /** True when today is outside the rotation's validity range. */
    static boolean rotationExpired(Context c, Calendar day) {
        String today = iso(day);
        return today.compareTo(validFrom(c)) < 0 || today.compareTo(validTo(c)) > 0;
    }

    /** Short Spanish status for the home card. */
    static String status(Context c, Calendar day) {
        if (plateDigit(c) < 0) return "Define tu placa en Configuración";
        if (rotationExpired(c, day)) return "Rotación vencida: verifica en Configuración";
        if (appliesToday(c, day)) {
            return "Hoy pico y placa " + AM_FROM + "–" + AM_TO + " y " + PM_FROM + "–" + PM_TO;
        }
        return "Sin pico y placa hoy";
    }

    /** 0 = Monday .. 4 = Friday, -1 on weekends. */
    private static int weekdayIndex(Calendar day) {
        int d = day.get(Calendar.DAY_OF_WEEK);
        return d >= Calendar.MONDAY && d <= Calendar.FRIDAY ? d - Calendar.MONDAY : -1;
    }

    /** "1,2;3,4;5,6;7,8;9,0" -> digits per weekday, or null when malformed (needs 5 groups). */
    static int[][] parseRotation(String s) {
        if (s == null) return null;
        String[] groups = s.trim().split(";");
        if (groups.length != 5) return null;
        int[][] out = new int[5][];
        for (int i = 0; i < 5; i++) {
            String[] parts = groups[i].trim().split(",");
            out[i] = new int[parts.length];
            for (int j = 0; j < parts.length; j++) {
                String p = parts[j].trim();
                if (p.length() != 1 || p.charAt(0) < '0' || p.charAt(0) > '9') return null;
                out[i][j] = p.charAt(0) - '0';
            }
        }
        return out;
    }

    // ---------- Dates ----------

    /** yyyy-MM-dd */
    static String iso(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Parses yyyy-MM-dd (strict); null when invalid. */
    static Calendar parseIso(String s) {
        if (s == null || !s.trim().matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        String[] p = s.trim().split("-");
        Calendar c = Calendar.getInstance();
        c.clear();
        c.setLenient(false);
        try {
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 12, 0, 0);
            c.getTimeInMillis(); // throws when the date does not exist
        } catch (Exception e) {
            return null;
        }
        return c;
    }

    // ---------- Colombian public holidays ----------

    static boolean isHoliday(Calendar day) {
        int y = day.get(Calendar.YEAR);
        String key = iso(day);
        for (String h : holidays(y)) if (h.equals(key)) return true;
        return false;
    }

    private static String[] holidays(int y) {
        String[] out = new String[18];
        int n = 0;
        // Fixed dates.
        out[n++] = fixed(y, 1, 1);    // Año Nuevo
        out[n++] = fixed(y, 5, 1);    // Día del Trabajo
        out[n++] = fixed(y, 7, 20);   // Independencia
        out[n++] = fixed(y, 8, 7);    // Batalla de Boyacá
        out[n++] = fixed(y, 12, 8);   // Inmaculada Concepción
        out[n++] = fixed(y, 12, 25);  // Navidad
        // Ley Emiliani: moved to the following Monday.
        out[n++] = monday(y, 1, 6); // Reyes Magos
        out[n++] = monday(y, 3, 19); // San José
        out[n++] = monday(y, 6, 29); // San Pedro y San Pablo
        out[n++] = monday(y, 8, 15); // Asunción
        out[n++] = monday(y, 10, 12); // Día de la Raza
        out[n++] = monday(y, 11, 1); // Todos los Santos
        out[n++] = monday(y, 11, 11); // Independencia de Cartagena
        // Easter based.
        Calendar easter = easter(y);
        out[n++] = offset(easter, -3); // Jueves Santo
        out[n++] = offset(easter, -2); // Viernes Santo
        out[n++] = offset(easter, 43); // Ascensión (Thursday, moved to Monday)
        out[n++] = offset(easter, 64); // Corpus Christi
        out[n++] = offset(easter, 71); // Sagrado Corazón
        return out;
    }

    private static String fixed(int y, int m, int d) {
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
    }

    /** The date, or the next Monday when it does not fall on one. */
    private static String monday(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, 12, 0, 0);
        return moveToMonday(c);
    }

    private static String moveToMonday(Calendar c) {
        int dow = c.get(Calendar.DAY_OF_WEEK);
        if (dow != Calendar.MONDAY) c.add(Calendar.DAY_OF_MONTH, (Calendar.MONDAY - dow + 7) % 7);
        return iso(c);
    }

    /** Easter-relative holiday; days offsets 43/64/71 land on Thursday/Thursday/Friday, then move. */
    private static String offset(Calendar easter, int days) {
        Calendar c = (Calendar) easter.clone();
        c.add(Calendar.DAY_OF_MONTH, days);
        // Holy Thursday/Friday (negative offsets) stay; the rest move to Monday.
        return days < 0 ? iso(c) : moveToMonday(c);
    }

    /** Gregorian Easter Sunday (Meeus/Jones/Butcher). */
    private static Calendar easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, month - 1, day, 12, 0, 0);
        return cal;
    }
}
