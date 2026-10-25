package com.simple.carlauncher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ELM327 over Bluetooth Classic (SPP) for engine data and trouble codes.
 *
 * One background thread connects to the adapter whose MAC is in Profile prefs "obd_mac",
 * polls the PIDs and publishes immutable Snapshots to listeners on the main thread. Unknown
 * values are NaN. With Profile prefs "obd_sim" it generates plausible values instead (no adapter).
 * The car has no MAF sensor (MAP only), so fuel flow is estimated with speed-density.
 */
final class ObdClient {

    private static final String TAG = "ObdClient";
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final long STALE_MS = 15000;

    // Speed-density fuel model (1.4 L gasoline).
    private static final float VE = 0.85f, DISPLACEMENT_L = 1.4f, AFR = 14.7f, GASOLINE_G_PER_L = 737f;

    /** One reading of everything. NaN = unknown. */
    static final class Snapshot {
        final float rpm, speed, coolant, map, iat, volts, load, lPer100km, lPerHour;
        final boolean milOn, connected, simulated;
        final List<String> dtcs;
        final long time;

        Snapshot(float rpm, float speed, float coolant, float map, float iat, float volts, float load,
                 float lPer100km, float lPerHour, boolean milOn, List<String> dtcs,
                 boolean connected, boolean simulated) {
            this.rpm = rpm; this.speed = speed; this.coolant = coolant; this.map = map;
            this.iat = iat; this.volts = volts; this.load = load;
            this.lPer100km = lPer100km; this.lPerHour = lPerHour;
            this.milOn = milOn; this.dtcs = dtcs; this.connected = connected; this.simulated = simulated;
            this.time = SystemClock.elapsedRealtime();
        }
    }

    interface Listener {
        /** Called on the main thread. */
        void onObd(Snapshot s);
    }

    private static ObdClient instance;
    private static volatile Snapshot last;
    /** Fuel tank level 0-100 % (PID 012F); NaN when the car does not report it. */
    static volatile float fuelPct = Float.NaN;

    /** Latest reading, or null if none yet. Check connected and age (see alert()). */
    static Snapshot lastSnapshot() {
        return last;
    }

    /** True when the last snapshot is recent and from a live (or simulated) source. */
    static boolean isFresh() {
        Snapshot s = last;
        return s != null && s.connected && SystemClock.elapsedRealtime() - s.time < STALE_MS;
    }

    /**
     * Short Spanish alert for the home screen, or null: check engine, hot engine (>105 C) or low
     * battery (<11.8 V engine off, <13.0 V running).
     */
    static String alert() {
        if (!isFresh()) return null;
        Snapshot s = last;
        if (s.milOn || !s.dtcs.isEmpty()) return "Revisar motor (check engine)";
        if (s.coolant > 105) return "Motor muy caliente: " + Math.round(s.coolant) + " °C";
        if (!Float.isNaN(s.volts)) {
            boolean running = s.rpm > 400;
            if (running ? s.volts < 13.0f : s.volts < 11.8f) {
                return "Batería baja: " + String.format(java.util.Locale.US, "%.1f", s.volts) + " V";
            }
        }
        return null;
    }

    static synchronized ObdClient get(Context c) {
        if (instance == null) instance = new ObdClient(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private Thread thread;
    private volatile boolean running;
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;

    // Worker state
    private float rpm = Float.NaN, speed = Float.NaN, coolant = Float.NaN, map = Float.NaN,
            iat = Float.NaN, volts = Float.NaN, load = Float.NaN, lph = Float.NaN, l100 = Float.NaN;
    private boolean milOn;
    private List<String> dtcs = Collections.emptyList();
    private int emptyStreak;
    private long lastCalc, lastSave;
    private float pendingL, pendingKm;

    private ObdClient(Context c) {
        ctx = c;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("obd", Context.MODE_PRIVATE);
    }

    static float totalLiters(Context c) { return prefs(c).getFloat("total_l", 0); }
    static float totalKm(Context c) { return prefs(c).getFloat("total_km", 0); }

    static void resetTotals(Context c) {
        prefs(c).edit().putFloat("total_l", 0).putFloat("total_km", 0).apply();
    }

    static String mac(Context c) {
        return Profile.prefs(c).getString("obd_mac", "");
    }

    static boolean simulation(Context c) {
        return Profile.prefs(c).getBoolean("obd_sim", false);
    }

    void addListener(Listener l) {
        synchronized (listeners) {
            if (!listeners.contains(l)) listeners.add(l);
        }
        Snapshot s = last;
        if (s != null) l.onObd(s);
    }

    void removeListener(Listener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    /** Starts the background thread (no-op if running or nothing is configured). */
    synchronized void start() {
        if (running || (mac(ctx).isEmpty() && !simulation(ctx))) return;
        running = true;
        thread = new Thread(this::run, "obd");
        thread.setDaemon(true);
        thread.start();
    }

    synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
        close();
    }

    /** Applies changed preferences (device or simulation). */
    synchronized void restart() {
        stop();
        start();
    }

    // ---------- Worker ----------

    private void run() {
        while (running) {
            try {
                if (simulation(ctx)) {
                    simTick();
                    Thread.sleep(500);
                    continue;
                }
                String mac = mac(ctx);
                BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
                if (mac.isEmpty() || ad == null || !ad.isEnabled() || !connect(ad, mac)) {
                    reset();
                    publish(false, false);
                    Thread.sleep(8000);
                    continue;
                }
                init();
                long lastDtc = 0;
                int cycle = 0;
                while (running) {
                    long now = SystemClock.elapsedRealtime();
                    if (lastDtc == 0 || now - lastDtc > 60000) {
                        readDtcs();
                        lastDtc = now;
                    }
                    pollOnce(cycle++);
                    publish(true, false);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.w(TAG, "link lost: " + e);
            } finally {
                close();
            }
            if (running) {
                reset();
                publish(false, false);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void reset() {
        rpm = speed = coolant = map = iat = volts = load = lph = l100 = Float.NaN;
        emptyStreak = 0;
        lastCalc = 0;
    }

    private boolean connect(BluetoothAdapter ad, String mac) {
        try {
            BluetoothDevice dev = ad.getRemoteDevice(mac);
            ad.cancelDiscovery();
            socket = dev.createRfcommSocketToServiceRecord(SPP);
            socket.connect();
            in = socket.getInputStream();
            out = socket.getOutputStream();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "connect: " + e);
            close();
            return false;
        }
    }

    private void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
        in = null;
        out = null;
    }

    private void init() throws IOException, InterruptedException {
        String[] cmds = {"ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0"};
        for (String c : cmds) {
            send(c, c.equals("ATZ") ? 4000 : 2000);
            Thread.sleep(100);
        }
        send("0100", 8000); // wakes the bus / protocol search
    }

    /** Sends a command and returns the raw reply up to the '>' prompt, or null on timeout. */
    private String send(String cmd, long timeoutMs) throws IOException {
        while (in.available() > 0) in.read(); // drop leftovers
        out.write((cmd + "\r").getBytes());
        out.flush();
        StringBuilder sb = new StringBuilder();
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < end) {
            if (in.available() > 0) {
                int b = in.read();
                if (b < 0) throw new IOException("closed");
                if (b == '>') {
                    emptyStreak = 0;
                    return sb.toString();
                }
                sb.append((char) b);
            } else {
                try {
                    Thread.sleep(8);
                } catch (InterruptedException e) {
                    throw new IOException("interrupted");
                }
            }
        }
        if (++emptyStreak >= 6) throw new IOException("adapter silent");
        return null;
    }

    /** Data bytes (after "41 PID") of a mode 01 reply, or null for NO DATA / ? / garbage. */
    private int[] query(String cmd) throws IOException {
        String r = send(cmd, 2500);
        if (r == null) return null;
        String prefix = "41" + cmd.substring(2);
        for (String line : r.split("[\r\n]+")) {
            String t = line.replace(" ", "").toUpperCase();
            if (t.startsWith(prefix)) return hex(t.substring(prefix.length()));
        }
        return null;
    }

    private static int[] hex(String s) {
        int n = s.length() / 2;
        int[] r = new int[n];
        try {
            for (int i = 0; i < n; i++) r[i] = Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        } catch (NumberFormatException e) {
            return null;
        }
        return r;
    }

    private static float val(int[] d, int need, float v) {
        return d == null || d.length < need ? Float.NaN : v;
    }

    private void pollOnce(int cycle) throws IOException {
        int[] d = query("010C");
        rpm = val(d, 2, d == null || d.length < 2 ? 0 : (d[0] * 256 + d[1]) / 4f);
        d = query("010D");
        speed = val(d, 1, d == null || d.length < 1 ? 0 : d[0]);
        // Slower values rotate, one per cycle.
        switch (cycle % 5) {
            case 0:
                d = query("0105");
                coolant = val(d, 1, d == null || d.length < 1 ? 0 : d[0] - 40);
                break;
            case 1:
                d = query("010B");
                map = val(d, 1, d == null || d.length < 1 ? 0 : d[0]);
                break;
            case 2:
                d = query("010F");
                iat = val(d, 1, d == null || d.length < 1 ? 0 : d[0] - 40);
                break;
            case 3:
                d = query("0142");
                volts = val(d, 2, d == null || d.length < 2 ? 0 : (d[0] * 256 + d[1]) / 1000f);
                if (Float.isNaN(volts)) volts = readAtrv();
                d = query("012F"); // not every car reports it; stays NaN then
                fuelPct = d == null || d.length < 1 ? Float.NaN : d[0] * 100f / 255f;
                break;
            default:
                d = query("0104");
                load = val(d, 1, d == null || d.length < 1 ? 0 : d[0] * 100f / 255f);
        }
        if (Float.isNaN(rpm) && Float.isNaN(speed)) { // ignition off: ECU does not answer
            coolant = map = iat = load = Float.NaN;
        }
        estimateFuel(false);
    }

    /** Adapter-measured battery voltage ("12.4V"), used when the ECU does not answer. */
    private float readAtrv() throws IOException {
        String r = send("ATRV", 1500);
        if (r == null) return Float.NaN;
        try {
            return Float.parseFloat(r.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private void readDtcs() throws IOException {
        String r = send("0101", 3000);
        if (r != null) {
            for (String line : r.split("[\r\n]+")) {
                String t = line.replace(" ", "").toUpperCase();
                if (t.startsWith("4101") && t.length() >= 8) {
                    int a = Integer.parseInt(t.substring(4, 6), 16);
                    milOn = (a & 0x80) != 0;
                }
            }
        }
        r = send("03", 4000);
        if (r == null) return;
        StringBuilder data = new StringBuilder();
        for (String line : r.split("[\r\n]+")) {
            String t = line.replace(" ", "").toUpperCase();
            if (t.matches("^[0-9A-F]:.*")) t = t.substring(2);       // CAN multi-frame index
            if (t.length() <= 3 || !t.matches("[0-9A-F]+")) continue; // SEARCHING, NODATA, length header
            data.append(t);
        }
        String all = data.toString();
        int p = all.indexOf("43");
        List<String> found = new ArrayList<>();
        if (p >= 0) {
            int[] b = hex(all.substring(p + 2));
            if (b != null) {
                int start = b.length % 2 == 1 ? 1 : 0; // CAN adds a count byte
                for (int i = start; i + 1 < b.length; i += 2) {
                    if (b[i] == 0 && b[i + 1] == 0) continue;
                    String code = dtc(b[i], b[i + 1]);
                    if (!found.contains(code)) found.add(code);
                }
            }
        }
        dtcs = found;
    }

    static String dtc(int a, int b) {
        char[] letters = {'P', 'C', 'B', 'U'};
        return "" + letters[(a >> 6) & 3] + ((a >> 4) & 3) + Integer.toHexString(a & 15).toUpperCase()
                + Integer.toHexString((b >> 4) & 15).toUpperCase() + Integer.toHexString(b & 15).toUpperCase();
    }

    /** Speed-density: air mass from RPM, MAP and IAT, then fuel from the stoichiometric ratio. */
    private void estimateFuel(boolean simulated) {
        long now = SystemClock.elapsedRealtime();
        if (Float.isNaN(rpm) || Float.isNaN(map) || Float.isNaN(iat) || rpm < 300) {
            lph = rpm < 300 ? 0 : Float.NaN;
            l100 = Float.NaN;
            lastCalc = now;
            return;
        }
        float iatK = iat + 273.15f;
        float mafGs = (rpm * map / iatK) / 120f * VE * DISPLACEMENT_L * 28.97f / 8.314f;
        float raw = mafGs / AFR / GASOLINE_G_PER_L * 3600f;
        lph = Float.isNaN(lph) ? raw : lph + (raw - lph) * 0.3f;
        l100 = !Float.isNaN(speed) && speed >= 3 ? lph / speed * 100f : Float.NaN;
        if (lastCalc > 0) {
            float dt = Math.min(2f, (now - lastCalc) / 1000f);
            pendingL += lph * dt / 3600f;
            if (!Float.isNaN(speed)) pendingKm += speed * dt / 3600f;
        }
        lastCalc = now;
        if (!simulated && now - lastSave > 30000) {
            lastSave = now;
            SharedPreferences p = prefs(ctx);
            p.edit().putFloat("total_l", p.getFloat("total_l", 0) + pendingL)
                    .putFloat("total_km", p.getFloat("total_km", 0) + pendingKm).apply();
            pendingL = 0;
            pendingKm = 0;
        }
    }

    // ---------- Simulation ----------

    private void simTick() {
        double t = SystemClock.elapsedRealtime() / 1000.0;
        speed = (float) Math.max(0, 55 + 45 * Math.sin(t / 15));
        rpm = (float) (800 + speed * 32 + 150 * Math.sin(t / 2));
        coolant = 88 + (float) (3 * Math.sin(t / 30));
        map = (float) (35 + 25 * Math.max(0, Math.sin(t / 15 + 0.6)) + 5 * Math.sin(t));
        iat = 32 + (float) (2 * Math.sin(t / 40));
        volts = 14.1f + (float) (0.2 * Math.sin(t / 7));
        load = (float) (30 + 25 * Math.sin(t / 15));
        fuelPct = (float) (55 + 40 * Math.sin(t / 120));
        milOn = false;
        dtcs = Collections.emptyList();
        estimateFuel(true);
        publish(true, true);
    }

    private void publish(boolean connected, boolean simulated) {
        final Snapshot s = new Snapshot(rpm, speed, coolant, map, iat, volts, load, l100, lph,
                milOn, dtcs, connected, simulated);
        last = s;
        main.post(() -> {
            List<Listener> copy;
            synchronized (listeners) {
                copy = new ArrayList<>(listeners);
            }
            for (Listener l : copy) l.onObd(s);
        });
    }
}
