package com.simple.carlauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Service schedule for a Chevrolet Sail 1.4: every item is due by km OR months, whichever comes
 * first. Last-done km/date and custom intervals live in the "maintenance" prefs; the oil item
 * shares its values with {@link Odometer} / OilActivity so nothing is duplicated.
 */
final class Maintenance {

    static final int OK = 0, SOON = 1, OVERDUE = 2;
    static final int ENGINE = 0, TIRES = 1, BRAKES = 2, BODY = 3;

    private static final String CHANNEL = "maintenance";
    private static final int NOTIF_ID = 1201;

    /** id, name, category, default km, default months. */
    private static final Object[][] DEFS = {
            {"aceite", "Aceite y filtro", ENGINE, 5000, 6},
            {"llantas", "Rotación de llantas", TIRES, 10000, 12},
            {"alineacion", "Alineación y balanceo", TIRES, 10000, 12},
            {"frenos", "Revisión de frenos", BRAKES, 10000, 12},
            {"filtro_aire", "Filtro de aire", ENGINE, 10000, 12},
            {"filtro_cabina", "Filtro de cabina", BODY, 15000, 12},
            {"bujias", "Bujías", ENGINE, 40000, 36},
            {"refrigerante", "Refrigerante", ENGINE, 40000, 24},
            {"liquido_frenos", "Líquido de frenos", BRAKES, 40000, 24},
            {"correa", "Correa de distribución", ENGINE, 60000, 48},
            {"suspension", "Amortiguadores y rodamientos", BODY, 20000, 24},
    };

    static final class Item {
        String id, name;
        int category, intervalKm, intervalMonths;
        int lastKm = -1;      // -1 = unknown
        long lastMs;          // 0 = unknown
        int status = OK;
        int kmLeft = Integer.MAX_VALUE;   // MAX_VALUE = unknown
        int daysLeft = Integer.MAX_VALUE; // MAX_VALUE = unknown

        /** One line for the list, e.g. "Faltan 1.200 km · 45 días". */
        String detail() {
            StringBuilder sb = new StringBuilder();
            if (kmLeft != Integer.MAX_VALUE) {
                sb.append(kmLeft >= 0 ? "Faltan " + OilActivity.fmt(kmLeft) + " km"
                        : "Pasado por " + OilActivity.fmt(-kmLeft) + " km");
            }
            if (daysLeft != Integer.MAX_VALUE) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(daysLeft >= 0 ? daysLeft + " días" : "vencido hace " + (-daysLeft) + " días");
            }
            return sb.length() == 0 ? "Sin registro" : sb.toString();
        }
    }

    private Maintenance() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("maintenance", Context.MODE_PRIVATE);
    }

    // ---------- Items ----------

    /** All items with their current status (also starts the clock for items never registered). */
    static List<Item> items(Context c) {
        SharedPreferences p = prefs(c);
        boolean configured = Odometer.isConfigured(c);
        int cur = Odometer.currentKm(c);
        long now = System.currentTimeMillis();
        List<Item> out = new ArrayList<>();
        for (Object[] d : DEFS) {
            Item it = new Item();
            it.id = (String) d[0];
            it.name = (String) d[1];
            it.category = (Integer) d[2];
            boolean oil = "aceite".equals(it.id);
            if (oil) {
                it.intervalKm = Odometer.intervalKm(c);
                it.lastKm = Odometer.prefs(c).contains("last_change_km") ? Odometer.lastChangeKm(c) : -1;
                it.lastMs = Odometer.lastChangeMs(c);
            } else {
                it.intervalKm = p.getInt("ikm_" + it.id, (Integer) d[3]);
                it.lastKm = p.getInt("km_" + it.id, -1);
                it.lastMs = p.getLong("ms_" + it.id, 0);
            }
            it.intervalMonths = p.getInt("imo_" + it.id, (Integer) d[4]);
            // Never registered: start counting from today so a new install is not all "vencido".
            if (it.lastMs == 0) {
                it.lastMs = now;
                p.edit().putLong(oil ? "ms_aceite" : "ms_" + it.id, now).apply();
                if (oil) Odometer.prefs(c).edit().putLong("last_change_ms", now).apply();
            }
            if (it.lastKm < 0 && configured) {
                it.lastKm = cur;
                if (oil) Odometer.setLastChangeKm(c, cur);
                else p.edit().putInt("km_" + it.id, cur).apply();
            }
            evaluate(it, configured ? cur : -1, now);
            out.add(it);
        }
        return out;
    }

    private static void evaluate(Item it, int curKm, long now) {
        int status = OK;
        if (curKm >= 0 && it.lastKm >= 0 && it.intervalKm > 0) {
            it.kmLeft = it.lastKm + it.intervalKm - curKm;
            if (it.kmLeft < 0) status = OVERDUE;
            else if (it.kmLeft <= it.intervalKm / 10) status = SOON;
        }
        if (it.lastMs > 0 && it.intervalMonths > 0) {
            Calendar due = Calendar.getInstance();
            due.setTimeInMillis(it.lastMs);
            due.add(Calendar.MONTH, it.intervalMonths);
            long diff = due.getTimeInMillis() - now;
            it.daysLeft = (int) Math.floor(diff / 86_400_000.0);
            if (it.daysLeft < 0) status = OVERDUE;
            else if (it.daysLeft <= 30) status = Math.max(status, SOON);
        }
        it.status = status;
    }

    static int worst(Context c) {
        int w = OK;
        for (Item it : items(c)) w = Math.max(w, it.status);
        return w;
    }

    /** Worst status among the items of one category (ENGINE, TIRES, BRAKES, BODY). */
    static int worstFor(Context c, int category) {
        int w = OK;
        for (Item it : items(c)) if (it.category == category) w = Math.max(w, it.status);
        return w;
    }

    /** Short text for the home card: "Al día", "2 vencidos · 1 pronto". */
    static String summary(Context c) {
        int over = 0, soon = 0;
        for (Item it : items(c)) {
            if (it.status == OVERDUE) over++;
            else if (it.status == SOON) soon++;
        }
        if (over == 0 && soon == 0) return "Mantenimiento al día";
        StringBuilder sb = new StringBuilder();
        if (over > 0) sb.append(over).append(over == 1 ? " vencido" : " vencidos");
        if (soon > 0) sb.append(over > 0 ? " · " : "").append(soon).append(" pronto");
        return sb.toString();
    }

    /** Names of overdue items, optionally skipping oil (which has its own spoken reminder). */
    static List<String> overdueNames(Context c, boolean includeOil) {
        List<String> out = new ArrayList<>();
        for (Item it : items(c)) {
            if (it.status != OVERDUE || (!includeOil && "aceite".equals(it.id))) continue;
            out.add(it.name);
        }
        return out;
    }

    // ---------- Editing ----------

    /** Registers the item as done today at the current odometer reading and logs it to the CSV. */
    static void markDone(Context c, String id) {
        markDone(c, id, Calendar.getInstance(), Odometer.isConfigured(c) ? Odometer.currentKm(c) : -1);
    }

    /** Done on a past date (e.g. registered late) at that odometer reading; km -1 = unknown. */
    static void markDone(Context c, String id, Calendar when, int km) {
        long ms = when.getTimeInMillis();
        if ("aceite".equals(id)) {
            SharedPreferences.Editor e = Odometer.prefs(c).edit().putLong("last_change_ms", ms);
            if (km >= 0) e.putInt("last_change_km", km);
            e.apply();
        } else {
            SharedPreferences.Editor e = prefs(c).edit().putLong("ms_" + id, ms);
            if (km >= 0) e.putInt("km_" + id, km);
            e.apply();
        }
        logDone(c, id, when, km);
    }

    static void setInterval(Context c, String id, int km, int months) {
        if ("aceite".equals(id)) Odometer.setIntervalKm(c, km);
        else prefs(c).edit().putInt("ikm_" + id, km).apply();
        prefs(c).edit().putInt("imo_" + id, months).apply();
    }

    /** Appends a row to mantenimiento.csv: fecha,km,item,categoria,intervalo_km,intervalo_meses. */
    static void logDone(Context c, String id) {
        logDone(c, id, Calendar.getInstance(), Odometer.isConfigured(c) ? Odometer.currentKm(c) : -1);
    }

    static void logDone(Context c, String id, Calendar when, int km) {
        for (Item it : items(c)) {
            if (!it.id.equals(id)) continue;
            FuelLog.append(FuelLog.maintFile(c), "fecha,km,item,categoria,intervalo_km,intervalo_meses",
                    String.format(Locale.US, "%s,%s,%s,%s,%d,%d", PicoPlaca.iso(when),
                            km >= 0 ? String.valueOf(km) : "", it.id, categoryName(it.category),
                            it.intervalKm, it.intervalMonths));
            return;
        }
    }

    static String categoryName(int cat) {
        switch (cat) {
            case TIRES: return "llantas";
            case BRAKES: return "frenos";
            case BODY: return "carroceria";
            default: return "motor";
        }
    }

    // ---------- Notification ----------

    /** Posts one notification listing due/overdue items, at most once per day. */
    static void notifyIfDue(Context c) {
        try {
            SharedPreferences p = prefs(c);
            String today = PicoPlaca.iso(Calendar.getInstance());
            if (today.equals(p.getString("notif_day", ""))) return;
            StringBuilder sb = new StringBuilder();
            boolean overdue = false;
            for (Item it : items(c)) {
                if (it.status == OK) continue;
                overdue |= it.status == OVERDUE;
                if (sb.length() > 0) sb.append(", ");
                sb.append(it.name).append(it.status == OVERDUE ? " (vencido)" : " (pronto)");
            }
            if (sb.length() == 0) return;
            p.edit().putString("notif_day", today).putLong("notif_ms", System.currentTimeMillis()).apply();

            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Mantenimiento",
                        NotificationManager.IMPORTANCE_DEFAULT));
                b = new Notification.Builder(c, CHANNEL);
            } else {
                b = new Notification.Builder(c);
            }
            PendingIntent pi = PendingIntent.getActivity(c, 0,
                    new Intent(c, MaintenanceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            b.setSmallIcon(R.drawable.ic_car)
                    .setContentTitle(overdue ? "Mantenimiento vencido" : "Mantenimiento próximo")
                    .setContentText(sb.toString())
                    .setStyle(new Notification.BigTextStyle().bigText(sb.toString()))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(NOTIF_ID, b.build());
        } catch (Exception ignored) { }
    }
}
