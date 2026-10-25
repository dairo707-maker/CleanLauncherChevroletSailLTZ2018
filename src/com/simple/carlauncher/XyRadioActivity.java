package com.simple.carlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** FM screen for the XY AUTO head unit: reads the factory app's station text, tunes when enabled. */
public class XyRadioActivity extends Activity {

    private static final int SLOTS = 6;
    private static final int MIN = 8750, MAX = 10800, STEP = 10; // 10 kHz units

    private final Handler handler = new Handler();
    private TextView band, station, freq;
    private DialView dial;
    private final TextView[] presetFreq = new TextView[SLOTS];
    private final LinearLayout[] presetTile = new LinearLayout[SLOTS];
    private int current = -1; // 10 kHz units, -1 unknown

    private final ContentObserver observer = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackground(Ui.background(this));

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(12);
        root.addView(body, blp);
        body.addView(buildTuner(), new LinearLayout.LayoutParams(0, -1, 1.45f));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -1, 1f);
        plp.leftMargin = dp(12);
        body.addView(buildPresets(), plp);
        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Ui.text(this, "Radio", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView classic = chip("Vista clásica", 0x26FFFFFF);
        classic.setOnClickListener(v -> Ui.launch(this, XyAuto.RADIO));
        bar.addView(classic);

        band = chip("FM", 0xFF4F7BFF);
        band.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(10);
        bar.addView(band, lp);
        return bar;
    }

    private TextView chip(String s, int color) {
        TextView c = Ui.text(this, s, 16, Color.WHITE);
        c.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(22));
        c.setBackground(bg);
        Ui.pressable(c, 0.92f);
        return c;
    }

    private View buildTuner() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(16), dp(20), dp(20));
        card.setBackground(Ui.card(this));

        station = Ui.text(this, "", 18, 0xCCFFFFFF);
        card.addView(station);

        LinearLayout big = new LinearLayout(this);
        big.setGravity(Gravity.CENTER);
        freq = Ui.text(this, "--.-", 104, Color.WHITE);
        freq.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        freq.setIncludeFontPadding(false);
        big.addView(freq);
        TextView unit = Ui.text(this, "MHz", 22, 0x99FFFFFF);
        unit.setPadding(dp(10), dp(30), 0, 0);
        big.addView(unit);
        card.addView(big);

        dial = new DialView(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(80));
        dlp.topMargin = dp(4);
        card.addView(dial, dlp);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(14), 0, 0);
        controls.addView(control(R.drawable.ic_rewind, "Buscar", false, () -> nudge(-STEP)));
        controls.addView(control(R.drawable.ic_prev, "Anterior", true, () -> XyAuto.radioStep(this, false)));
        controls.addView(control(R.drawable.ic_next, "Siguiente", true, () -> XyAuto.radioStep(this, true)));
        controls.addView(control(R.drawable.ic_forward, "Buscar", false, () -> nudge(STEP)));
        card.addView(controls);
        return card;
    }

    private View control(int icon, String label, boolean primary, Runnable action) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        int size = primary ? 64 : 56;
        c.addView(Ui.iconButton(this, icon, size, primary, action),
                new LinearLayout.LayoutParams(dp(size), dp(size)));
        TextView t = Ui.text(this, label, 12, 0x99FFFFFF);
        t.setPadding(0, dp(6), 0, 0);
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(12), 0, dp(12), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private View buildPresets() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Ui.card(this));

        TextView title = Ui.text(this, "Emisoras guardadas", 17, Color.WHITE);
        title.setGravity(Gravity.START);
        title.setPadding(dp(4), 0, 0, dp(2));
        card.addView(title);
        TextView hint = Ui.text(this, "Mantén pulsado para guardar", 12, 0x80FFFFFF);
        hint.setGravity(Gravity.START);
        hint.setPadding(dp(4), 0, 0, dp(8));
        card.addView(hint);

        for (int r = 0; r < SLOTS / 2; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                int index = r * 2 + col;
                LinearLayout tile = new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                TextView f = Ui.text(this, "--", 26, Color.WHITE);
                f.setTypeface(Typeface.DEFAULT_BOLD);
                tile.addView(f);
                tile.addView(Ui.text(this, "P" + (index + 1), 12, 0x99FFFFFF));
                Ui.pressable(tile, 0.94f);
                tile.setOnClickListener(v -> onPreset(index));
                tile.setOnLongClickListener(v -> {
                    savePreset(index);
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                lp.setMargins(dp(5), dp(5), dp(5), dp(5));
                row.addView(tile, lp);
                presetFreq[index] = f;
                presetTile[index] = tile;
            }
            card.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        return card;
    }

    // ---------- Actions ----------

    private SharedPreferences prefs() {
        return getSharedPreferences("xyradio", Context.MODE_PRIVATE);
    }

    private int preset(int i) {
        return prefs().getInt("p" + i, 0);
    }

    private void onPreset(int i) {
        int f = preset(i);
        if (f == 0) {
            Toast.makeText(this, "Vacío: mantén pulsado para guardar", Toast.LENGTH_SHORT).show();
            return;
        }
        tuneOrOpen(f);
    }

    private void savePreset(int i) {
        if (current < 0) {
            Toast.makeText(this, "Sin emisora que guardar", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs().edit().putInt("p" + i, current).apply();
        Toast.makeText(this, "P" + (i + 1) + " = " + label(current), Toast.LENGTH_SHORT).show();
        refresh();
    }

    /** Manual step (no hardware seek available): move one step and wrap around the band. */
    private void nudge(int delta) {
        int base = current < 0 ? MIN : current + delta;
        if (base > MAX) base = MIN;
        if (base < MIN) base = MAX;
        tuneOrOpen(base);
    }

    private void tuneOrOpen(int f) {
        if (XyAuto.tune(this, f)) {
            current = f; // optimistic; the observer corrects it
            showFrequency();
        } else {
            Toast.makeText(this, "Ajuste directo pendiente: abriendo la radio", Toast.LENGTH_SHORT).show();
            Ui.launch(this, XyAuto.RADIO);
        }
    }

    // ---------- State ----------

    private static String label(int f) {
        return String.format(java.util.Locale.US, "%d.%d", f / 100, (f % 100) / 10);
    }

    private void refresh() {
        String[] st = XyAuto.fmStation(this);
        if (st == null) {
            current = -1;
            band.setText("FM");
            station.setText("Abre la radio de fábrica para empezar");
            freq.setText("--.-");
            updatePresets();
            return;
        }
        band.setText(st[0]);
        station.setText("Radio " + st[0]);
        try {
            current = Math.round(Float.parseFloat(st[1]) * 100f);
        } catch (NumberFormatException e) {
            current = -1;
        }
        showFrequency();
    }

    private void showFrequency() {
        if (current < 0) return;
        freq.setText(label(current));
        dial.setFrequency(current);
        updatePresets();
    }

    private void updatePresets() {
        for (int i = 0; i < SLOTS; i++) {
            int p = preset(i);
            presetFreq[i].setText(p == 0 ? "--" : label(p));
            boolean active = p != 0 && p == current;
            GradientDrawable bg = active
                    ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, Ui.pick(this,
                            new int[]{0xFF2EB8FF, 0xFF8A4DFF}, new int[]{0xFF2E6BFF, 0xFF7B3FE4}))
                    : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x26FFFFFF, 0x14FFFFFF});
            bg.setCornerRadius(dp(14));
            presetTile[i].setBackground(bg);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(Settings.System.getUriFor(XyAuto.FM_TEXT), false, observer);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(observer);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
