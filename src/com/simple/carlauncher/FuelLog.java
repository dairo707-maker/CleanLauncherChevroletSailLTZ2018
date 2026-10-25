package com.simple.carlauncher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fuel fill-ups kept in a flat CSV (Power BI friendly: header row, ISO dates, '.' decimals) under
 * the app's external files dir, so no storage permission is needed. Efficiency is computed
 * between full tanks: km driven / gallons added since the previous full tank.
 */
final class FuelLog {

    static final String FUEL_FILE = "tanqueadas.csv";
    static final String MAINT_FILE = "mantenimiento.csv";
    private static final String FUEL_HEADER = "fecha,km,galones,valor_cop,lleno,km_por_galon,costo_km";

    static final class Entry {
        String date;      // yyyy-MM-dd
        int km;
        double gallons;
        long cost;        // COP
        boolean full;
        double kmPerGal = -1; // -1 = not computable
        double costPerKm = -1;
    }

    private FuelLog() { }

    // ---------- Files ----------

    /** .../Android/data/<pkg>/files/CleanLauncher (falls back to internal storage). */
    static File dir(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        File d = new File(base, "CleanLauncher");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static File fuelFile(Context c) {
        return new File(dir(c), FUEL_FILE);
    }

    static File maintFile(Context c) {
        return new File(dir(c), MAINT_FILE);
    }

    /** Appends one line to a CSV, writing the header first when the file is new. */
    static synchronized void append(File f, String header, String line) {
        try {
            boolean fresh = !f.exists() || f.length() == 0;
            try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, true), "UTF-8"))) {
                if (fresh) w.print(header + "\n");
                w.print(line + "\n");
            }
        } catch (IOException ignored) { }
    }

    // ---------- Entries ----------

    /** All fill-ups, oldest first, with efficiency columns computed. */
    static synchronized List<Entry> load(Context c) {
        List<Entry> list = new ArrayList<>();
        File f = fuelFile(c);
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line = r.readLine(); // header
                while ((line = r.readLine()) != null) {
                    String[] p = line.split(",");
                    if (p.length < 5) continue;
                    try {
                        Entry e = new Entry();
                        e.date = p[0].trim();
                        e.km = Integer.parseInt(p[1].trim());
                        e.gallons = Double.parseDouble(p[2].trim());
                        e.cost = Long.parseLong(p[3].trim());
                        e.full = "1".equals(p[4].trim());
                        list.add(e);
                    } catch (NumberFormatException ignored) { }
                }
            } catch (IOException ignored) { }
        }
        Collections.sort(list, (a, b) -> a.km != b.km ? Integer.compare(a.km, b.km) : a.date.compareTo(b.date));
        compute(list);
        return list;
    }

    /** km/gal and cost/km for each full tank, using everything added since the previous full one. */
    private static void compute(List<Entry> list) {
        int prevFull = -1;
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (!e.full) continue;
            if (prevFull >= 0) {
                int km = e.km - list.get(prevFull).km;
                double gal = 0;
                long cost = 0;
                for (int j = prevFull + 1; j <= i; j++) {
                    gal += list.get(j).gallons;
                    cost += list.get(j).cost;
                }
                if (km > 0 && gal > 0) {
                    e.kmPerGal = km / gal;
                    e.costPerKm = cost / (double) km;
                }
            }
            prevFull = i;
        }
    }

    static void add(Context c, Entry e) {
        List<Entry> list = load(c);
        list.add(e);
        save(c, list);
    }

    static void remove(Context c, Entry e) {
        List<Entry> list = load(c);
        for (int i = 0; i < list.size(); i++) {
            Entry x = list.get(i);
            if (x.km == e.km && x.date.equals(e.date) && x.cost == e.cost) {
                list.remove(i);
                break;
            }
        }
        save(c, list);
    }

    private static synchronized void save(Context c, List<Entry> list) {
        Collections.sort(list, (a, b) -> a.km != b.km ? Integer.compare(a.km, b.km) : a.date.compareTo(b.date));
        compute(list);
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fuelFile(c)), "UTF-8"))) {
            w.print(FUEL_HEADER + "\n");
            for (Entry e : list) {
                w.print(String.format(Locale.US, "%s,%d,%.3f,%d,%d,%s,%s\n", e.date, e.km, e.gallons, e.cost,
                        e.full ? 1 : 0,
                        e.kmPerGal > 0 ? String.format(Locale.US, "%.2f", e.kmPerGal) : "",
                        e.costPerKm > 0 ? String.format(Locale.US, "%.1f", e.costPerKm) : ""));
            }
        } catch (IOException ignored) { }
    }

    // ---------- Totals ----------

    /** Spending in the calendar month of {@code now}. */
    static long monthSpend(List<Entry> list, Calendar now) {
        String prefix = PicoPlaca.iso(now).substring(0, 7);
        long sum = 0;
        for (Entry e : list) if (e.date.startsWith(prefix)) sum += e.cost;
        return sum;
    }

    /** Spending from the same day one year ago until today. */
    static long last12Months(List<Entry> list, Calendar now) {
        Calendar from = (Calendar) now.clone();
        from.add(Calendar.YEAR, -1);
        String f = PicoPlaca.iso(from), t = PicoPlaca.iso(now);
        long sum = 0;
        for (Entry e : list) if (e.date.compareTo(f) > 0 && e.date.compareTo(t) <= 0) sum += e.cost;
        return sum;
    }

    /** Overall km/gal: total km between the first and last full tank over the gallons in between; -1 if unknown. */
    static double avgKmPerGal(List<Entry> list) {
        double km = 0, gal = 0;
        int prev = -1;
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (!e.full) continue;
            if (prev >= 0 && e.kmPerGal > 0) {
                km += e.km - list.get(prev).km;
                for (int j = prev + 1; j <= i; j++) gal += list.get(j).gallons;
            }
            prev = i;
        }
        return gal > 0 ? km / gal : -1;
    }

    /** Overall cost per km on the same basis as {@link #avgKmPerGal}; -1 if unknown. */
    static double avgCostPerKm(List<Entry> list) {
        double km = 0, cost = 0;
        int prev = -1;
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (!e.full) continue;
            if (prev >= 0 && e.kmPerGal > 0) {
                km += e.km - list.get(prev).km;
                for (int j = prev + 1; j <= i; j++) cost += list.get(j).cost;
            }
            prev = i;
        }
        return km > 0 ? cost / km : -1;
    }

    /** "$ 1.234.567" with dots as thousands separator. */
    static String cop(long v) {
        return "$ " + String.format(new Locale("es", "EC"), "%,d", v).replace(',', '.');
    }

    // ---------- USB export ----------

    /**
     * Copies the CSVs to the first mounted removable volume. Returns the destination folder, or
     * null when there is none (the caller then shows {@link #dir}).
     */
    static File exportToUsb(Context c) {
        File[] roots = c.getExternalFilesDirs(null);
        for (int i = 1; roots != null && i < roots.length; i++) {
            File r = roots[i];
            if (r == null) continue;
            try {
                if (!android.os.Environment.MEDIA_MOUNTED.equals(android.os.Environment.getExternalStorageState(r))) continue;
                if (!android.os.Environment.isExternalStorageRemovable(r)) continue;
                File out = new File(r, "CleanLauncher");
                if (!out.exists() && !out.mkdirs()) continue;
                copy(fuelFile(c), new File(out, FUEL_FILE));
                copy(maintFile(c), new File(out, MAINT_FILE));
                return out;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static void copy(File from, File to) throws IOException {
        if (!from.exists()) return;
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}
