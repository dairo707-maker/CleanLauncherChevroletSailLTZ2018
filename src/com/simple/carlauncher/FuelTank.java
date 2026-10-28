package com.simple.carlauncher;

import android.content.Context;

import java.util.List;

/**
 * Fuel left in the Sail's 42 L tank. From the scanner when the car reports PID 012F; otherwise
 * estimated from the last full fill-up: km driven since then (GPS odometer) / average km per gallon.
 */
final class FuelTank {

    private FuelTank() { }

    static final double CAPACITY_L = 42;
    static final double L_PER_GAL = 3.785;
    static final int LOW_KM = 60;

    /** {percent 0-100, km of range left, 1 if measured by OBD else 0}, or null when unknown. */
    static double[] state(Context c) {
        List<FuelLog.Entry> log = FuelLog.load(c); // parse the CSV once per refresh
        double kmPerGal = FuelLog.avgKmPerGal(log);
        if (ObdClient.isFresh() && !Float.isNaN(ObdClient.fuelPct)) {
            double pct = ObdClient.fuelPct;
            double range = kmPerGal > 0 ? CAPACITY_L * pct / 100 / L_PER_GAL * kmPerGal : -1;
            return new double[]{pct, range, 1};
        }
        if (kmPerGal <= 0 || !Odometer.isConfigured(c)) return null;
        FuelLog.Entry full = lastFull(log);
        if (full == null) return null;
        double usedGal = Math.max(0, Odometer.currentKm(c) - full.km) / kmPerGal;
        double leftL = Math.max(0, CAPACITY_L - usedGal * L_PER_GAL);
        return new double[]{leftL * 100 / CAPACITY_L, leftL / L_PER_GAL * kmPerGal, 0};
    }

    private static FuelLog.Entry lastFull(List<FuelLog.Entry> list) {
        FuelLog.Entry best = null;
        for (FuelLog.Entry e : list) if (e.full && (best == null || e.km > best.km)) best = e;
        return best;
    }

    /** Short text for the home chip, e.g. "⛽ 62 % · 230 km", or "" when unknown. */
    static String chip(Context c) {
        return chip(state(c));
    }

    static String chip(double[] s) {
        if (s == null) return "";
        return "⛽ " + Math.round(s[0]) + " %" + (s[1] >= 0 ? " · " + Math.round(s[1]) + " km" : "")
                + (s[2] == 0 ? " (est.)" : "");
    }

    static boolean low(Context c) {
        return low(state(c));
    }

    static boolean low(double[] s) {
        return s != null && ((s[1] >= 0 && s[1] < LOW_KM) || s[0] < 12);
    }
}
