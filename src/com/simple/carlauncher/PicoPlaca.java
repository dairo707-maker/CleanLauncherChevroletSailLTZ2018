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

    static final String DEFAULT_BANDS = "07:00-09:00,18:00-20:00";
    static final int MAX_BANDS = 4;

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

    /** Configured time bands as "HH:mm-HH:mm,..." (always valid). */
    static String bandsText(Context c) {
        String s = prefs(c).getString("pp_bands", DEFAULT_BANDS);
        return parseBands(s) == null ? DEFAULT_BANDS : s;
    }

    /** Special season (restriction all day), empty strings = off. */
    static String seasonFrom(Context c) {
        return prefs(c).getString("pp_temp_desde", "");
    }

    static String seasonTo(Context c) {
        return prefs(c).getString("pp_temp_hasta", "");
    }

    static void setBands(Context c, String bands, String seasonFrom, String seasonTo) {
        prefs(c).edit().putString("pp_bands", bands).putString("pp_temp_desde", seasonFrom)
                .putString("pp_temp_hasta", seasonTo).apply();
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

    /** True when today is inside the special 24 h season (both dates set and day within). */
    static boolean inSeason(Context c, Calendar day) {
        String f = seasonFrom(c), t = seasonTo(c);
        if (parseIso(f) == null || parseIso(t) == null) return false;
        String today = iso(day);
        return today.compareTo(f) >= 0 && today.compareTo(t) <= 0;
    }

    /** Bands in force today as {startMin, endMin}; the whole day during the special season. */
    static int[][] bandsFor(Context c, Calendar day) {
        if (inSeason(c, day)) return new int[][]{{0, 24 * 60}};
        int[][] b = parseBands(bandsText(c));
        return b == null ? parseBands(DEFAULT_BANDS) : b;
    }

    /** True when the plate is restricted today and the current time is inside a band. */
    static boolean inBandNow(Context c, Calendar now) {
        return bandEndNow(c, now) >= 0;
    }

    /** End (minutes of day) of the band we are in, or -1 when not in one. */
    static int bandEndNow(Context c, Calendar now) {
        if (!appliesToday(c, now)) return -1;
        int m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (int[] b : bandsFor(c, now)) if (m >= b[0] && m < b[1]) return b[1];
        return -1;
    }

    /** Start of the next band later today in ms since epoch, or -1 (none left / no restriction). */
    static long nextBandStart(Context c, Calendar now) {
        if (!appliesToday(c, now)) return -1;
        int m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        long best = -1;
        for (int[] b : bandsFor(c, now)) {
            if (b[0] > m && (best < 0 || b[0] < best)) best = b[0];
        }
        if (best < 0) return -1;
        Calendar t = (Calendar) now.clone();
        t.set(Calendar.HOUR_OF_DAY, (int) best / 60);
        t.set(Calendar.MINUTE, (int) best % 60);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        return t.getTimeInMillis();
    }

    /** Short Spanish status for the home card. */
    static String status(Context c, Calendar day) {
        if (plateDigit(c) < 0) return "Define tu placa en Configuración";
        if (rotationExpired(c, day)) return "Rotación vencida: verifica en Configuración";
        if (!appliesToday(c, day)) return "Sin pico y placa hoy";
        if (inSeason(c, day)) return "Pico y placa todo el día (temporada especial)";
        int end = bandEndNow(c, day);
        if (end >= 0) return "En pico y placa ahora (hasta " + hm(end) + ")";
        return "Hoy tienes pico y placa: " + bandsLabel(c, day);
    }

    /** "7:00–9:00 y 18:00–20:00". */
    static String bandsLabel(Context c, Calendar day) {
        int[][] b = bandsFor(c, day);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(i == b.length - 1 ? " y " : ", ");
            sb.append(hm(b[i][0])).append('–').append(hm(b[i][1]));
        }
        return sb.toString();
    }

    /** Same bands for speech: "de 7 a 9 y de 18 a 20". */
    static String bandsSpoken(Context c, Calendar day) {
        if (inSeason(c, day)) return "todo el día";
        int[][] b = bandsFor(c, day);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(i == b.length - 1 ? " y " : ", ");
            sb.append("de ").append(spokenHour(b[i][0])).append(" a ").append(spokenHour(b[i][1]));
        }
        return sb.toString();
    }

    /** "las 9" style hour for speech: whole hours without minutes. */
    static String spokenHour(int minutes) {
        return minutes % 60 == 0 ? String.valueOf(minutes / 60 % 24) : hm(minutes);
    }

    /** 420 -> "7:00" */
    static String hm(int minutes) {
        return String.format(Locale.US, "%d:%02d", minutes / 60 % 24, minutes % 60);
    }

    /**
     * "07:00-09:00,18:00-20:00" -> {{420,540},{1080,1200}}; null when malformed, empty, more than
     * MAX_BANDS, or a band does not end after it starts.
     */
    static int[][] parseBands(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String[] parts = s.trim().split(",");
        if (parts.length < 1 || parts.length > MAX_BANDS) return null;
        int[][] out = new int[parts.length][2];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (!p.matches("\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}")) return null;
            String[] ft = p.split("-");
            int a = minutes(ft[0]), z = minutes(ft[1]);
            if (a < 0 || z < 0 || z <= a) return null;
            out[i][0] = a;
            out[i][1] = z;
        }
        return out;
    }

    private static int minutes(String hhmm) {
        String[] p = hhmm.split(":");
        int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
        return h > 23 || m > 59 ? -1 : h * 60 + m;
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
