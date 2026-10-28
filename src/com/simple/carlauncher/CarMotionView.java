package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Transparent overlay over a FIT_CENTER car image: ground streaks + spinning wheel blur. */
public class CarMotionView extends View {
    private static final float MIN_KMH = 3f;
    private static final long FRAME_MS = 33;
    private static final int MAXS = 10;

    // wheels in image-relative coords (x,y of width/height fractions), radii as fraction of image width
    private static final float[][] WHEELS = {
        {0.556f, 0.786f, 0.070f, 0.095f},  // front: cx, cy, rx, ry
        {0.912f, 0.682f, 0.048f, 0.076f}   // rear
    };

    // ground streak seeds: x, y (fractions of image w / h), length (fraction of w), phase offset
    private final float[] sx = new float[MAXS];
    private final float[] sy = new float[MAXS];
    private final float[] sl = new float[MAXS];
    private final float[] sp = new float[MAXS];

    private int imgW = 589, imgH = 384;
    private float kmh;
    private boolean paused;
    private boolean active;
    private long lastT;
    private float streakPhase;
    private float wheelAngle;

    private final Paint streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final Runnable tick = new Runnable() {
        @Override public void run() { invalidate(); }
    };

    public CarMotionView(Context c) {
        super(c);
        streakPaint.setStyle(Paint.Style.STROKE);
        streakPaint.setStrokeCap(Paint.Cap.ROUND);
        wheelPaint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < MAXS; i++) {
            // deterministic pseudo-random spread over the ground band
            float a = (i * 0.6180339f) % 1f;
            float b = (i * 0.7548777f + 0.3f) % 1f;
            sx[i] = -0.1f + a * 1.1f;
            sy[i] = 0.84f + b * 0.15f;
            sl[i] = 0.05f + ((i * 0.41f) % 1f) * 0.07f;
            sp[i] = (i * 0.37f) % 1f;
        }
        setWillNotDraw(false);
    }

    public void setImageSize(int w, int h) {
        if (w > 0 && h > 0 && (w != imgW || h != imgH)) { imgW = w; imgH = h; invalidate(); }
    }

    public void setSpeed(float v) {
        if (v < 0 || v != v) v = 0;
        if (v > 160) v = 160;
        kmh = v;
        update();
    }

    public void pause() { paused = true; removeCallbacks(tick); active = false; }

    public void resume() { paused = false; update(); }

    private void update() {
        boolean want = kmh >= MIN_KMH && !paused && getVisibility() == VISIBLE && isShown();
        if (want && !active) { active = true; lastT = SystemClock.uptimeMillis(); invalidate(); }
        else if (!want && active) { active = false; removeCallbacks(tick); invalidate(); }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); update(); }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow(); removeCallbacks(tick); active = false;
    }

    @Override protected void onVisibilityChanged(View v, int vis) {
        super.onVisibilityChanged(v, vis);
        if (vis == VISIBLE) update(); else { removeCallbacks(tick); active = false; }
    }

    @Override protected void onDraw(Canvas c) {
        if (kmh < MIN_KMH || paused) return;
        int vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = (now - lastT) / 1000f;
        lastT = now;
        if (dt > 0.1f) dt = 0.1f;
        if (dt < 0) dt = 0;

        // FIT_CENTER rect
        float sc = Math.min(vw / (float) imgW, vh / (float) imgH);
        float dw = imgW * sc, dh = imgH * sc;
        float ox = (vw - dw) / 2f, oy = (vh - dh) / 2f;

        int band = kmh < 30 ? 0 : kmh < 60 ? 1 : kmh < 90 ? 2 : 3;
        int n = band == 0 ? 3 : band == 1 ? 5 : band == 2 ? 8 : 10;
        float lenMul = 0.7f + band * 0.5f;
        streakPhase = (streakPhase + dt * (0.25f + kmh / 45f)) % 1f;

        // ground streaks, moving to bottom-right
        float dx = 0.8f, dy = 0.25f;
        float travel = 0.35f * dw;
        streakPaint.setStrokeWidth(Math.max(1.5f, dh * 0.007f));
        for (int i = 0; i < n; i++) {
            float t = (streakPhase + sp[i]) % 1f;
            float fade = (float) Math.sin(Math.PI * t);
            float len = sl[i] * dw * lenMul;
            float x = ox + sx[i] * dw + dx * travel * (t - 0.5f);
            float y = oy + sy[i] * dh + dy * travel * (t - 0.5f);
            if (y > oy + dh) continue;
            streakPaint.setColor(((int) (fade * 70) << 24) | 0xFFFFFF);
            c.drawLine(x, y, x + dx * len, y + dy * len, streakPaint);
        }

        // wheels
        wheelAngle = (wheelAngle + dt * (200f + kmh * 4f)) % 360f;
        float sweep = 18f + Math.min(kmh, 90f) * 0.4f;
        for (int i = 0; i < WHEELS.length; i++) {
            float[] w = WHEELS[i];
            float cx = ox + w[0] * dw, cy = oy + w[1] * dh;
            float r = w[3] * dw;               // vertical radius in px
            float sxs = w[2] / w[3];           // horizontal squash
            c.save();
            c.translate(cx, cy);
            c.scale(sxs, 1f);
            c.rotate(-wheelAngle);
            float rr = r * 0.62f;
            arc.set(-rr, -rr, rr, rr);
            wheelPaint.setStrokeWidth(r * 0.40f);
            for (int k = 0; k < 4; k++) {
                wheelPaint.setColor((k & 1) == 0 ? 0x55D8DCE0 : 0x45303438);
                c.drawArc(arc, k * 90f, sweep, false, wheelPaint);
            }
            c.restore();
        }

        postDelayed(tick, FRAME_MS);
    }
}
