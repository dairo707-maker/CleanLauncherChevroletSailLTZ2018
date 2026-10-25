package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Work shifts for ride-hailing (Uber, DiDi, InDrive) and private trips. Same CSV conventions as
 * {@link FuelLog} (header row, ISO dates, '.' decimals) so Power BI can load them directly.
 *
 * Trip "valor" is what the driver receives: the apps already deduct their commission.
 * Net = income - estimated fuel (shift km x cost per km). Shift id in the trips file is the
 * shift start in epoch milliseconds; turnos.csv matches it by fecha + inicio.
 */
final class ShiftLog {

    static final String SHIFT_FILE = "turnos.csv";
    static final String TRIPS_FILE = "viajes_plataforma.csv";
    static final String[] PLATFORMS = {"Uber", "DiDi", "InDrive", "Particular"};
    static final int DEFAULT_FUEL_COP_KM = 350;
    private static final String SHIFT_HEADER =
            "fecha,inicio,fin,horas,km,ingresos_cop,viajes,combustible_estimado_cop,neto_cop,neto_hora_cop,neto_km_cop";
    private static final String TRIPS_HEADER = "fecha,hora,plataforma,valor_cop,propina_cop,metodo,km,turno_id";

    static final class Trip {
        String date, time, platform, method; // method: efectivo | app
        long value, tip;                      // COP
        double km = -1;                       // -1 = not given
        long shiftId;

        long income() {
            return value + tip;
        }
    }

    /** Result of one shift. */
    static final class Summary {
        long startMs, endMs;
        double hours, km;
        long income, fuel, net, netHour, netKm;
        int trips;
    }

    /** Totals for a period; platform "Total" is the whole period. */
    static final class Stat {
        String platform;
        long income, net, netHour;
        int trips;
    }

    private ShiftLog() { }

    // ---------- State ----------

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("shift", Context.MODE_PRIVATE);
    }

    static boolean isActive(Context c) {
        return prefs(c).getLong("start_ms", 0) > 0;
    }

    static long startMs(Context c) {
        return prefs(c).getLong("start_ms", 0);
    }

    static double hours(Context c) {
        return isActive(c) ? (System.currentTimeMillis() - startMs(c)) / 3600000.0 : 0;
    }

    private static float odoKm(Context c) {
        SharedPreferences p = Odometer.prefs(c);
        return p.getInt("base_km", 0) + p.getFloat("gps_km", 0);
    }

    /** GPS km driven since the shift started (needs the Odometer service running). */
    static double shiftKm(Context c) {
        return isActive(c) ? Math.max(0, odoKm(c) - prefs(c).getFloat("start_km", 0)) : 0;
    }

    static void start(Context c) {
        if (isActive(c)) return;
        prefs(c).edit().putLong("start_ms", System.currentTimeMillis()).putFloat("start_km", odoKm(c)).apply();
    }

    /** Ends the shift, appends its row to turnos.csv and returns the summary. */
    static Summary stop(Context c) {
        if (!isActive(c)) return null;
        Summary s = new Summary();
        s.startMs = startMs(c);
        s.endMs = System.currentTimeMillis();
        s.hours = (s.endMs - s.startMs) / 3600000.0;
        s.km = shiftKm(c);
        for (Trip t : shiftTrips(c)) {
            s.income += t.income();
            s.trips++;
        }
        s.fuel = Math.round(s.km * fuelPerKm(c));
        s.net = s.income - s.fuel;
        s.netHour = s.hours > 0.01 ? Math.round(s.net / s.hours) : 0;
        s.netKm = s.km > 0.1 ? Math.round(s.net / s.km) : 0;
        FuelLog.append(new File(FuelLog.dir(c), SHIFT_FILE), SHIFT_HEADER, String.format(Locale.US,
                "%s,%s,%s,%.2f,%.1f,%d,%d,%d,%d,%d,%s", date(s.startMs), time(s.startMs), time(s.endMs),
                s.hours, s.km, s.income, s.trips, s.fuel, s.net, s.netHour,
                s.km > 0.1 ? String.valueOf(s.netKm) : ""));
        prefs(c).edit().remove("start_ms").remove("start_km").apply();
        return s;
    }

    // ---------- Fuel cost ----------

    /** COP per km: from the fill-ups when known, else the configurable fallback (default 350). */
    static double fuelPerKm(Context c) {
        double v = FuelLog.avgCostPerKm(FuelLog.load(c));
        return v > 0 ? v : fallbackFuelPerKm(c);
    }

    static boolean fuelFromFills(Context c) {
        return FuelLog.avgCostPerKm(FuelLog.load(c)) > 0;
    }

    static int fallbackFuelPerKm(Context c) {
        return prefs(c).getInt("fuel_cop_km", DEFAULT_FUEL_COP_KM);
    }

    static void setFallbackFuelPerKm(Context c, int v) {
        prefs(c).edit().putInt("fuel_cop_km", Math.max(1, v)).apply();
    }

    // ---------- Trips ----------

    static String lastMethod(Context c) {
        return prefs(c).getString("last_method", "efectivo");
    }

    static void addTrip(Context c, String platform, long value, long tip, String method, double km) {
        if (!isActive(c)) return;
        long now = System.currentTimeMillis();
        prefs(c).edit().putString("last_method", method).apply();
        FuelLog.append(new File(FuelLog.dir(c), TRIPS_FILE), TRIPS_HEADER, String.format(Locale.US,
                "%s,%s,%s,%d,%d,%s,%s,%d", date(now), time(now), platform, value, tip, method,
                km > 0 ? String.format(Locale.US, "%.1f", km) : "", startMs(c)));
    }

    static synchronized List<Trip> loadTrips(Context c) {
        List<Trip> list = new ArrayList<>();
        File f = new File(FuelLog.dir(c), TRIPS_FILE);
        if (!f.exists()) return list;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line = r.readLine(); // header
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length < 8) continue;
                try {
                    Trip t = new Trip();
                    t.date = p[0];
                    t.time = p[1];
                    t.platform = p[2];
                    t.value = Long.parseLong(p[3]);
                    t.tip = Long.parseLong(p[4]);
                    t.method = p[5];
                    t.km = p[6].isEmpty() ? -1 : Double.parseDouble(p[6]);
                    t.shiftId = Long.parseLong(p[7]);
                    list.add(t);
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException ignored) { }
        return list;
    }

    static synchronized void removeTrip(Context c, Trip x) {
        List<Trip> list = loadTrips(c);
        for (int i = 0; i < list.size(); i++) {
            Trip t = list.get(i);
            if (t.shiftId == x.shiftId && t.date.equals(x.date) && t.time.equals(x.time)
                    && t.platform.equals(x.platform) && t.value == x.value) {
                list.remove(i);
                break;
            }
        }
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(new File(FuelLog.dir(c), TRIPS_FILE)), "UTF-8"))) {
            w.print(TRIPS_HEADER + "\n");
            for (Trip t : list) {
                w.print(String.format(Locale.US, "%s,%s,%s,%d,%d,%s,%s,%d\n", t.date, t.time, t.platform, t.value,
                        t.tip, t.method, t.km > 0 ? String.format(Locale.US, "%.1f", t.km) : "", t.shiftId));
            }
        } catch (IOException ignored) { }
    }

    /** Trips of the active shift, oldest first. */
    static List<Trip> shiftTrips(Context c) {
        List<Trip> out = new ArrayList<>();
        long id = startMs(c);
        for (Trip t : loadTrips(c)) if (t.shiftId == id) out.add(t);
        return out;
    }

    static List<Trip> todayTrips(Context c) {
        List<Trip> out = new ArrayList<>();
        String today = PicoPlaca.iso(Calendar.getInstance());
        for (Trip t : loadTrips(c)) if (t.date.equals(today)) out.add(t);
        return out;
    }

    // ---------- Period summaries ----------

    static String weekStartIso() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7)); // back to Monday
        return PicoPlaca.iso(cal);
    }

    static String monthStartIso() {
        return PicoPlaca.iso(Calendar.getInstance()).substring(0, 7) + "-01";
    }

    /**
     * Stats per platform since {@code fromIso} plus a final "Total" entry. Hours and fuel come from
     * finished shifts (plus the active one) and are split by trip share, so per-platform net/hour
     * is approximate.
     */
    static List<Stat> periodStats(Context c, String fromIso) {
        double hours = 0;
        long fuel = 0;
        File f = new File(FuelLog.dir(c), SHIFT_FILE);
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line = r.readLine();
                while ((line = r.readLine()) != null) {
                    String[] p = line.split(",", -1);
                    if (p.length < 8 || p[0].compareTo(fromIso) < 0) continue;
                    try {
                        hours += Double.parseDouble(p[3]);
                        fuel += Long.parseLong(p[7]);
                    } catch (NumberFormatException ignored) { }
                }
            } catch (IOException ignored) { }
        }
        if (isActive(c) && date(startMs(c)).compareTo(fromIso) >= 0) {
            hours += hours(c);
            fuel += Math.round(shiftKm(c) * fuelPerKm(c));
        }
        List<Trip> trips = new ArrayList<>();
        for (Trip t : loadTrips(c)) if (t.date.compareTo(fromIso) >= 0) trips.add(t);
        List<Stat> out = new ArrayList<>();
        for (String name : PLATFORMS) {
            Stat s = new Stat();
            s.platform = name;
            for (Trip t : trips) {
                if (t.platform.equals(name)) {
                    s.income += t.income();
                    s.trips++;
                }
            }
            out.add(s);
        }
        Stat total = new Stat();
        total.platform = "Total";
        for (Stat s : out) {
            total.income += s.income;
            total.trips += s.trips;
        }
        out.add(total);
        for (Stat s : out) {
            double share = total.trips > 0 ? s.trips / (double) total.trips : 0;
            s.net = Math.round(s.income - fuel * share);
            double h = hours * share;
            s.netHour = h > 0.05 ? Math.round(s.net / h) : 0;
        }
        return out;
    }

    // ---------- Text helpers ----------

    /** Short text for a home chip, e.g. "Turno 2:15 · $ 85.000"; empty when no shift is active. */
    static String chip(Context c) {
        if (!isActive(c)) return "";
        long income = 0;
        for (Trip t : shiftTrips(c)) income += t.income();
        return "Turno " + hm(hours(c)) + " · " + FuelLog.cop(income);
    }

    static String describe(Summary s) {
        return String.format(Locale.US, "Duración: %s\nKm: %.1f\nViajes: %d\n", hm(s.hours), s.km, s.trips)
                + "Ingresos: " + FuelLog.cop(s.income) + "\nCombustible (est.): " + FuelLog.cop(s.fuel)
                + "\nNeto: " + FuelLog.cop(s.net) + "\nNeto por hora: " + FuelLog.cop(s.netHour)
                + (s.km > 0.1 ? "\nNeto por km: " + FuelLog.cop(s.netKm) : "");
    }

    /** 2.25 h -> "2:15". */
    static String hm(double hours) {
        long m = Math.round(hours * 60);
        return String.format(Locale.US, "%d:%02d", m / 60, m % 60);
    }

    private static String date(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ms));
    }

    private static String time(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ms));
    }
}
