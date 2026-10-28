package com.simple.carlauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny north-up OpenStreetMap view that follows the car. Tiles: memory LRU + disk cache,
 * fetched by two background threads. No animation loop: it only redraws on a new fix or tile.
 */
public class MiniMapView extends View implements LocationListener {
    private static final int TILE = 256;
    private static final long FRESH_MS = 30L * 24 * 3600 * 1000;   // never refetch below this age
    private static final long DISK_CAP = 60L * 1024 * 1024;
    private static final long RETRY_MS = 20000;
    private static final long ZOOM_DWELL_MS = 8000;
    private static final String UA = "CleanLauncher-Sail/1.0 (personal car head unit)";

    // Shared by every instance: one pool, one memory cache.
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    private final Set<String> inFlight = new HashSet<>();
    private final HashMap<String, Long> failedAt = new HashMap<>();
    private final File dir;
    private final boolean dark;
    private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final RectF rect = new RectF();
    private final float dp;

    private LocationManager lm;
    private Location loc;
    private float bearing;
    private int zoom = 17;
    private long zoomAt;
    private boolean hostResumed, listening, offline;
    private volatile boolean cleaned;

    public MiniMapView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        dark = Ui.isDark(c);
        dir = new File(c.getCacheDir(), "tiles");
        dir.mkdirs();
        if (dark) {
            // invert, dim and tint blue: bright OSM becomes a dark navy map
            float k = 0.78f;
            ColorMatrix cm = new ColorMatrix(new float[]{
                    -k * 0.80f, 0, 0, 0, 255 * k * 0.80f,
                    0, -k * 0.90f, 0, 0, 255 * k * 0.90f,
                    0, 0, -k * 1.05f, 0, 255 * k * 1.05f,
                    0, 0, 0, 1, 0});
            tilePaint.setColorFilter(new ColorMatrixColorFilter(cm));
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        setBackgroundColor(dark ? 0xFF14161C : 0xFFE4E6EA);
    }

    // ---------- lifecycle ----------

    public void onResume() { hostResumed = true; updateListening(); }

    public void onPause() { hostResumed = false; updateListening(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateListening();
        if (!cleaned) { cleaned = true; POOL.execute(this::trimDisk); }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateListening();
    }

    @Override protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        updateListening();
    }

    @Override protected void onVisibilityChanged(View c, int v) {
        super.onVisibilityChanged(c, v);
        updateListening();
    }

    private void updateListening() {
        boolean want = hostResumed && isAttachedToWindow() && getVisibility() == VISIBLE
                && getWindowVisibility() == VISIBLE;
        if (want == listening || lm == null) return;
        if (want) {
            if (getContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return; // retried on next onResume
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, this);
                listening = true;
                if (loc == null) {
                    Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (last != null) { loc = last; invalidate(); }
                }
            } catch (SecurityException | IllegalArgumentException e) {
                listening = false;
            }
        } else {
            try { lm.removeUpdates(this); } catch (Exception ignored) { }
            listening = false;
        }
    }

    // ---------- location ----------

    @Override public void onLocationChanged(Location l) {
        loc = l;
        if (l.hasBearing() && l.hasSpeed() && l.getSpeed() > 2f) bearing = l.getBearing();
        updateZoom(l.hasSpeed() ? l.getSpeed() * 3.6f : 0f);
        invalidate();
    }

    @Override public void onStatusChanged(String p, int s, Bundle e) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }

    /** Hysteresis: 17 below ~30 km/h, 16 up to ~70, 15 above; at most one change per dwell. */
    private void updateZoom(float kmh) {
        long now = System.currentTimeMillis();
        if (now - zoomAt < ZOOM_DWELL_MS) return;
        int z = zoom;
        if (zoom == 17 && kmh >= 34) z = kmh >= 78 ? 15 : 16;
        else if (zoom == 16) { if (kmh < 26) z = 17; else if (kmh >= 74) z = 15; }
        else if (zoom == 15 && kmh < 66) z = kmh < 26 ? 17 : 16;
        if (z != zoom) { zoom = z; zoomAt = now; }
    }

    // ---------- drawing ----------

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (loc == null) {
            drawMessage(c, w, h, "Buscando GPS…");
            return;
        }
        double n = (double) (1 << zoom) * TILE;
        double lat = Math.max(-85.0, Math.min(85.0, loc.getLatitude()));
        double px = (loc.getLongitude() + 180.0) / 360.0 * n;
        double sin = Math.sin(Math.toRadians(lat));
        double py = (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * n;
        float cx = w / 2f, cy = h * 0.6f;
        double left = px - cx, top = py - cy;
        int maxT = 1 << zoom;
        int tx0 = (int) Math.floor(left / TILE), tx1 = (int) Math.floor((left + w) / TILE);
        int ty0 = (int) Math.floor(top / TILE), ty1 = (int) Math.floor((top + h) / TILE);

        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(dark ? 0xFF22252E : 0xFFCDD0D6);
        stroke.setStrokeWidth(1);
        boolean missing = false;
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                float dx = (float) (tx * TILE - left), dy = (float) (ty * TILE - top);
                if (ty < 0 || ty >= maxT) continue;
                int wx = ((tx % maxT) + maxT) % maxT;
                Bitmap b = tile(zoom, wx, ty);
                if (b != null) {
                    c.drawBitmap(b, dx, dy, tilePaint);
                } else {
                    missing = true;
                    c.drawRect(dx, dy, dx + TILE, dy + TILE, stroke);
                }
            }
        }
        drawMarker(c, cx, cy);

        text.setTextSize(10 * dp);
        text.setColor(dark ? 0xAAFFFFFF : 0xAA202020);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText("© OpenStreetMap", w - 6 * dp, h - 5 * dp, text);
        if (offline && missing) drawChip(c, "Sin conexión", w / 2f, h - 22 * dp);
    }

    private void drawMarker(Canvas c, float cx, float cy) {
        boolean moving = loc.hasSpeed() && loc.getSpeed() > 2f;
        fill.setColor(0x4D000000);
        c.drawCircle(cx, cy + 2 * dp, 13 * dp, fill);
        if (moving) {
            float r = 14 * dp;
            c.save();
            c.rotate(bearing, cx, cy);
            arrow.reset();
            arrow.moveTo(cx, cy - r);
            arrow.lineTo(cx + r * 0.75f, cy + r * 0.8f);
            arrow.lineTo(cx, cy + r * 0.35f);
            arrow.lineTo(cx - r * 0.75f, cy + r * 0.8f);
            arrow.close();
            fill.setColor(0xFF2E8BFF);
            c.drawPath(arrow, fill);
            stroke.setColor(Ui.isDark(getContext()) ? 0xFF0A1830 : 0xFFFFFFFF);
            stroke.setStrokeWidth(2 * dp);
            c.drawPath(arrow, stroke);
            c.restore();
        } else {
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, 10 * dp, fill);
            fill.setColor(0xFF2E8BFF);
            c.drawCircle(cx, cy, 7 * dp, fill);
        }
    }

    private void drawMessage(Canvas c, int w, int h, String msg) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(16 * dp);
        text.setColor(dark ? 0xCCFFFFFF : 0xCC202020);
        c.drawText(msg, w / 2f, h / 2f, text);
    }

    private void drawChip(Canvas c, String s, float cx, float y) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(12 * dp);
        float tw = text.measureText(s);
        rect.set(cx - tw / 2 - 10 * dp, y - 14 * dp, cx + tw / 2 + 10 * dp, y + 6 * dp);
        fill.setColor(0xCC000000);
        c.drawRoundRect(rect, 10 * dp, 10 * dp, fill);
        text.setColor(0xFFFFFFFF);
        c.drawText(s, cx, y, text);
    }

    // ---------- tiles ----------

    private Bitmap tile(int z, int x, int y) {
        String key = z + "_" + x + "_" + y;
        Bitmap b = MEM.get(key);
        if (b != null) return b;
        synchronized (inFlight) {
            if (inFlight.contains(key)) return null;
            Long f = failedAt.get(key);
            if (f != null && System.currentTimeMillis() - f < RETRY_MS) return null;
            inFlight.add(key);
        }
        POOL.execute(() -> load(key, z, x, y));
        return null;
    }

    private void load(String key, int z, int x, int y) {
        boolean ok = false;
        try {
            File f = new File(dir, key + ".png");
            long age = f.exists() ? System.currentTimeMillis() - f.lastModified() : Long.MAX_VALUE;
            Bitmap b = f.exists() ? decode(f) : null;
            if (b != null) {
                MEM.put(key, b);
                ok = true;
                postInvalidate();
            }
            if (b == null || age >= FRESH_MS) {
                boolean fetched = download(f, z, x, y);
                if (fetched) {
                    offline = false;
                    Bitmap nb = decode(f);
                    if (nb != null) { MEM.put(key, nb); ok = true; postInvalidate(); }
                } else if (b == null) {
                    offline = true;
                    postInvalidate();
                }
            }
        } catch (Throwable ignored) {
            // OOM or IO: leave the grid placeholder
        } finally {
            synchronized (inFlight) {
                inFlight.remove(key);
                if (ok) failedAt.remove(key); else failedAt.put(key, System.currentTimeMillis());
            }
        }
    }

    private static Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (b == null) f.delete(); // corrupt
        return b;
    }

    private boolean download(File dest, int z, int x, int y) {
        File tmp = new File(dest.getPath() + ".tmp");
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL("https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png")
                    .openConnection();
            con.setRequestProperty("User-Agent", UA);
            con.setConnectTimeout(6000);
            con.setReadTimeout(8000);
            if (con.getResponseCode() != 200) return false;
            try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
            dest.delete();
            return tmp.renameTo(dest);
        } catch (Exception e) {
            tmp.delete();
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** Keeps the disk cache under the cap by deleting the oldest tiles first. */
    private void trimDisk() {
        try {
            File[] fs = dir.listFiles();
            if (fs == null) return;
            long total = 0;
            for (File f : fs) total += f.length();
            if (total <= DISK_CAP) return;
            Arrays.sort(fs, new Comparator<File>() {
                @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
            });
            for (File f : fs) {
                if (total <= DISK_CAP * 0.85) break;
                total -= f.length();
                f.delete();
            }
        } catch (Throwable ignored) { }
    }
}
