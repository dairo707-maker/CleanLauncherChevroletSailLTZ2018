package com.simple.carlauncher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Full-screen speedometer: arc gauge 0-160 km/h, limit marker and trip stats. */
public class SpeedActivity extends Activity {

    private static final float MAX = 160f;
    private final Handler handler = new Handler();
    private Dial dial;
    private TextView kmTrip, driving, maxV, avgV;
    private int startKm;
    private int maxSeen;
    private long movingMs, lastTick;
    private double movingKmh;   // sum of speed * seconds while moving
    private int limit;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startKm = Odometer.currentKm(this);
        limit = DriverAssist.limit(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0E14);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 12));
        root.setOnClickListener(v -> finish()); // tap anywhere to go back

        dial = new Dial(this, limit);
        root.addView(dial, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        kmTrip = stat(row, "km de este viaje");
        driving = stat(row, "tiempo manejando");
        maxV = stat(row, "velocidad máxima");
        avgV = stat(row, "promedio en movimiento");
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private TextView stat(LinearLayout row, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextView v = Ui.text(this, "--", 24, Color.WHITE);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        TextView l = Ui.text(this, label, 12, 0x99FFFFFF);
        col.addView(v);
        col.addView(l);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return v;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = android.os.SystemClock.elapsedRealtime();
            int v = DriverAssist.bestKmh();
            if (v > maxSeen) maxSeen = v;
            if (v > 3 && lastTick != 0) {
                long dt = Math.min(now - lastTick, 2500);
                movingMs += dt;
                movingKmh += v * (dt / 1000.0);
            }
            lastTick = now;
            dial.set(v, DriverAssist.speedFromObd() ? "OBD" : "GPS");
            int km = Math.max(0, Odometer.currentKm(SpeedActivity.this) - startKm);
            kmTrip.setText(km + " km");
            int min = DriverAssist.drivingMinutes();
            driving.setText(min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min");
            maxV.setText(maxSeen + " km/h");
            avgV.setText(movingMs < 3000 ? "--" : String.format(Locale.US, "%d km/h",
                    Math.round(movingKmh / (movingMs / 1000.0))));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        lastTick = 0;
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    /** Arc gauge with limit tick, big number and source label. */
    private static final class Dial extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final int limit;
        private int value = -1;
        private String src = "GPS";

        Dial(Context c, int limit) {
            super(c);
            this.limit = limit;
            float d = c.getResources().getDisplayMetrics().density;
            for (Paint p : new Paint[]{track, arc, tick}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(22 * d);
            }
            arc.setStrokeCap(Paint.Cap.ROUND);
            track.setStrokeCap(Paint.Cap.ROUND);
            track.setColor(0x26FFFFFF);
            tick.setColor(0xFFFFB300);
            tick.setStrokeWidth(5 * d);
            big.setColor(Color.WHITE);
            big.setTextSize(110 * d);
            big.setTypeface(Typeface.DEFAULT_BOLD);
            big.setTextAlign(Paint.Align.CENTER);
            small.setColor(0x99FFFFFF);
            small.setTextSize(20 * d);
            small.setTextAlign(Paint.Align.CENTER);
        }

        void set(int v, String source) {
            value = v;
            src = source;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h * 1.1f), pad = track.getStrokeWidth();
            float cx = w / 2, cy = h / 2;
            box.set(cx - s / 2 + pad, cy - s / 2 + pad, cx + s / 2 - pad, cy + s / 2 - pad);
            c.drawArc(box, 135, 270, false, track);
            if (value >= 0) {
                arc.setColor(value > limit ? 0xFFFF5252 : 0xFF4FC3F7);
                float f = Math.min(1f, value / MAX);
                if (f > 0.005f) c.drawArc(box, 135, 270 * f, false, arc);
            }
            // limit marker: short radial tick across the arc
            double a = Math.toRadians(135 + 270 * Math.min(1f, limit / MAX));
            float r = box.width() / 2, half = pad * 0.8f;
            c.drawLine(cx + (float) Math.cos(a) * (r - half), cy + (float) Math.sin(a) * (r - half),
                    cx + (float) Math.cos(a) * (r + half), cy + (float) Math.sin(a) * (r + half), tick);
            big.setColor(value > limit ? 0xFFFF5252 : Color.WHITE);
            c.drawText(value < 0 ? "--" : String.valueOf(value), cx, cy + big.getTextSize() * 0.3f, big);
            c.drawText("km/h", cx, cy + big.getTextSize() * 0.3f + small.getTextSize() * 1.6f, small);
            c.drawText("Fuente: " + src + "  ·  Límite " + limit, cx,
                    cy + big.getTextSize() * 0.3f + small.getTextSize() * 3.2f, small);
        }
    }
}
