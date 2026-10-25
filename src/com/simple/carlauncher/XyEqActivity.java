package com.simple.carlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Equalizer for the XY AUTO unit: Android audio effects on the global mix (session 0). */
public class XyEqActivity extends Activity {

    private LevelSlider[] bands;
    private TextView[] bandValues;
    private final TextView[] presetChips = new TextView[XyEq.PRESET_NAMES.length];
    private TextView loudChip;
    private LevelSlider bassSlider, virtSlider;
    private static final int STEPS = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(14));
        root.setBackground(Ui.background(this));
        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(56)));

        if (!XyEq.ensure()) {
            root.addView(buildUnsupported(), new LinearLayout.LayoutParams(-1, 0, 1f));
            setContentView(root);
            return;
        }

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(48));
        plp.topMargin = dp(8);
        root.addView(buildPresets(), plp);

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(10);
        root.addView(body, blp);
        body.addView(buildBands(), new LinearLayout.LayoutParams(0, -1, 1.6f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
        rlp.leftMargin = dp(10);
        body.addView(buildTones(), rlp);
        setContentView(root);
        loadAll();
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Ui.text(this, "Ecualizador", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        if (XyEq.hasLoudness()) {
            loudChip = chip("Loudness");
            loudChip.setOnClickListener(v -> {
                XyEq.setLoudness(this, !XyEq.loudOn(this));
                paint(loudChip, XyEq.loudOn(this));
            });
            bar.addView(loudChip);
        }
        return bar;
    }

    private View buildUnsupported() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));
        card.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView msg = Ui.text(this, "Este equipo no permite ecualizar desde la app", 20, Color.WHITE);
        msg.setSingleLine(false);
        card.addView(msg);
        TextView sub = Ui.text(this, "Usa los ajustes de sonido de fábrica", 15, 0x99FFFFFF);
        sub.setPadding(0, dp(6), 0, dp(18));
        card.addView(sub);
        TextView open = chip("Abrir ajustes de fábrica");
        paint(open, true);
        open.setOnClickListener(v -> Ui.launch(this, XyAuto.SETTINGS));
        card.addView(open);
        return card;
    }

    private TextView chip(String s) {
        TextView c = Ui.text(this, s, 15, Color.WHITE);
        c.setPadding(dp(16), dp(9), dp(16), dp(9));
        Ui.pressable(c, 0.92f);
        paint(c, false);
        return c;
    }

    private View buildPresets() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < XyEq.PRESET_NAMES.length; i++) {
            int idx = i;
            TextView c = chip(XyEq.PRESET_NAMES[i]);
            c.setOnClickListener(v -> {
                XyEq.setPreset(this, idx);
                loadAll();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            row.addView(c, lp);
            presetChips[i] = c;
        }
        scroll.addView(row);
        return scroll;
    }

    private View buildBands() {
        int n = XyEq.bandCount();
        bands = new LevelSlider[n];
        bandValues = new TextView[n];
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(8), dp(12), dp(8), dp(10));
        card.setBackground(Ui.card(this));
        for (int b = 0; b < n; b++) {
            int band = b;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView val = Ui.text(this, "0", 13, 0xCCFFFFFF);
            col.addView(val);
            LevelSlider s = new LevelSlider(this, true, STEPS);
            s.setListener((sl, v, done) -> {
                bandValues[band].setText(db(mbOf(v)));
                if (done) {
                    XyEq.setCustomLevel(this, band, mbOf(v));
                    markPreset(XyEq.CUSTOM);
                }
            });
            col.addView(s, new LinearLayout.LayoutParams(-1, 0, 1f));
            TextView lab = Ui.text(this, XyEq.bandLabel(b), 13, 0x99FFFFFF);
            col.addView(lab);
            card.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
            bands[b] = s;
            bandValues[b] = val;
        }
        return card;
    }

    private View buildTones() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(Ui.card(this));

        if (XyEq.hasBass()) {
            card.addView(label("Graves"));
            bassSlider = new LevelSlider(this, false, 10);
            bassSlider.setListener((s, v, done) -> {
                if (done) XyEq.setBass(this, v * 100);
            });
            card.addView(bassSlider, new LinearLayout.LayoutParams(-1, dp(40)));
        }
        if (XyEq.hasVirtualizer()) {
            card.addView(label("Envolvente"));
            virtSlider = new LevelSlider(this, false, 10);
            virtSlider.setListener((s, v, done) -> {
                if (done) XyEq.setVirtualizer(this, v * 100);
            });
            card.addView(virtSlider, new LinearLayout.LayoutParams(-1, dp(40)));
        }
        View spacer = new View(this);
        card.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView note = Ui.text(this, "Balance y fader: requiere control del radio", 12, 0x80FFFFFF);
        note.setSingleLine(false);
        card.addView(note);
        return card;
    }

    private TextView label(String s) {
        TextView t = Ui.text(this, s, 16, Color.WHITE);
        t.setGravity(Gravity.START);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    // ---------- State ----------

    private int mbOf(int step) {
        int min = XyEq.minMb(), max = XyEq.maxMb();
        return min + Math.round(step * (max - min) / (float) STEPS);
    }

    private int stepOf(int mb) {
        int min = XyEq.minMb(), max = XyEq.maxMb();
        return Math.round((mb - min) * STEPS / (float) Math.max(1, max - min));
    }

    private static String db(int mb) {
        int d = Math.round(mb / 100f);
        return d > 0 ? "+" + d : String.valueOf(d);
    }

    private void loadAll() {
        int p = XyEq.preset(this);
        for (int b = 0; b < bands.length; b++) {
            int mb = XyEq.presetLevel(this, p, b);
            bands[b].setValue(stepOf(mb));
            bandValues[b].setText(db(mb));
        }
        if (bassSlider != null) bassSlider.setValue(XyEq.bassStrength(this) / 100);
        if (virtSlider != null) virtSlider.setValue(XyEq.virtStrength(this) / 100);
        boolean configured = getSharedPreferences("xyeq", MODE_PRIVATE).getBoolean("configured", false);
        markPreset(configured ? p : -1);
        if (loudChip != null) paint(loudChip, XyEq.loudOn(this));
    }

    private void markPreset(int idx) {
        for (int i = 0; i < presetChips.length; i++) paint(presetChips[i], i == idx);
    }

    private void paint(TextView chip, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        if (on) {
            bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            bg.setColors(Ui.pick(this, new int[]{0xFF2EB8FF, 0xFF8A4DFF}, new int[]{0xFF2E6BFF, 0xFF7B3FE4}));
        } else {
            bg.setColor(0x26FFFFFF);
        }
        chip.setBackground(bg);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
