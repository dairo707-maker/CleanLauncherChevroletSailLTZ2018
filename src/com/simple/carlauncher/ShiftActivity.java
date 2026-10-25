package com.simple.carlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** Work shift screen: start/stop, live earnings, one-tap trip registration and weekly/monthly summary. */
public class ShiftActivity extends Activity {

    private final Handler handler = new Handler();
    private LinearLayout content, tripsList;
    private TextView tabShift, tabSummary, timer, kmTv, incomeTv, netHourTv, startBtn;
    private int tab;
    private long income;
    private double fuelPerKm;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            live();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackground(Ui.background(this));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Ui.text(this, "Turnos", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        tabShift = button("Turno", 0xFF4F7BFF, () -> showTab(0));
        tabSummary = button("Resumen", 0x334F7BFF, () -> showTab(1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.rightMargin = dp(8);
        bar.addView(tabShift, tlp);
        bar.addView(tabSummary, tlp);
        bar.addView(button("Exportar a USB", 0x334F7BFF, this::export), new LinearLayout.LayoutParams(-2, -2));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        content = new LinearLayout(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, 0, 1f);
        clp.topMargin = dp(10);
        root.addView(content, clp);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showTab(tab);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void showTab(int t) {
        tab = t;
        tabShift.getBackground().setTint(t == 0 ? 0xFF4F7BFF : 0x334F7BFF);
        tabSummary.getBackground().setTint(t == 1 ? 0xFF4F7BFF : 0x334F7BFF);
        content.removeAllViews();
        timer = null;
        if (t == 0) buildShift();
        else buildSummary();
    }

    // ---------- Shift tab ----------

    private void buildShift() {
        fuelPerKm = ShiftLog.fuelPerKm(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(16), dp(12), dp(16), dp(16));
        left.setBackground(Ui.card(this));
        timer = Ui.text(this, "", 48, Color.WHITE);
        timer.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(timer, new LinearLayout.LayoutParams(-1, dp(70)));
        kmTv = stat(left, "Km del turno");
        incomeTv = stat(left, "Ingresos");
        netHourTv = stat(left, "Neto por hora");
        TextView hint = Ui.text(this, "El valor es lo que recibes tú, ya descontada la comisión de la app. "
                + "Neto = ingresos menos combustible estimado.", 12, 0x99FFFFFF);
        hint.setSingleLine(false);
        hint.setGravity(Gravity.START);
        hint.setPadding(0, dp(4), 0, dp(4));
        left.addView(hint, new LinearLayout.LayoutParams(-1, 0, 1f));
        startBtn = big("", 0xFF2ECC71, this::toggleShift);
        left.addView(startBtn, new LinearLayout.LayoutParams(-1, dp(80)));
        content.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(platformRow("Uber", 0xFF3A3A46, "DiDi", 0xFFFF7A1A));
        right.addView(platformRow("InDrive", 0xFF2ECC71, "Particular", 0xFF4F7BFF));
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.card(this));
        tripsList = new LinearLayout(this);
        tripsList.setOrientation(LinearLayout.VERTICAL);
        tripsList.setPadding(dp(12), dp(10), dp(12), dp(10));
        sv.addView(tripsList);
        right.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1.3f);
        rlp.leftMargin = dp(12);
        content.addView(right, rlp);
        refreshTrips();
    }

    private View platformRow(String a, int ca, String b, int cb) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(84));
        lp.bottomMargin = dp(10);
        row.setLayoutParams(lp);
        row.addView(platformButton(a, ca), new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, -1, 1f);
        blp.leftMargin = dp(10);
        row.addView(platformButton(b, cb), blp);
        return row;
    }

    private TextView platformButton(String platform, int color) {
        // "Particular" is shown as "Otro": the generic quick-entry for any other trip.
        String label = "Particular".equals(platform) ? "Otro" : platform;
        return big(label, color, () -> askTrip(platform, label));
    }

    private TextView stat(LinearLayout parent, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = Ui.text(this, label, 15, 0xB3FFFFFF);
        l.setGravity(Gravity.START);
        TextView v = Ui.text(this, "", 24, Color.WHITE);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.END);
        box.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(v, new LinearLayout.LayoutParams(0, -2, 1.3f));
        parent.addView(box, new LinearLayout.LayoutParams(-1, dp(44)));
        return v;
    }

    /** Cheap per-second refresh: timer, km, income and net per hour. */
    private void live() {
        if (timer == null) return;
        boolean on = ShiftLog.isActive(this);
        double hours = ShiftLog.hours(this), km = ShiftLog.shiftKm(this);
        long s = on ? (System.currentTimeMillis() - ShiftLog.startMs(this)) / 1000 : 0;
        timer.setText(String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60));
        kmTv.setText(String.format(Locale.US, "%.1f km", km));
        long net = income - Math.round(km * fuelPerKm);
        incomeTv.setText(FuelLog.cop(income));
        netHourTv.setText(on && hours > 0.05 ? FuelLog.cop(Math.round(net / hours)) : "—");
        startBtn.setText(on ? "Terminar turno" : "Iniciar turno");
        ((GradientDrawable) startBtn.getBackground()).setColor(on ? 0xFFE74C3C : 0xFF2ECC71);
    }

    private void refreshTrips() {
        income = 0;
        for (ShiftLog.Trip t : ShiftLog.shiftTrips(this)) income += t.income();
        tripsList.removeAllViews();
        List<ShiftLog.Trip> today = ShiftLog.todayTrips(this);
        if (today.isEmpty()) {
            TextView empty = Ui.text(this, ShiftLog.isActive(this) ? "Aún no hay viajes hoy"
                    : "Inicia un turno para registrar viajes", 16, 0x99FFFFFF);
            empty.setPadding(0, dp(20), 0, 0);
            tripsList.addView(empty);
        }
        for (int i = today.size() - 1; i >= 0; i--) tripsList.addView(tripRow(today.get(i)));
        live();
    }

    private View tripRow(ShiftLog.Trip t) {
        TextView row = Ui.text(this, t.time + "  ·  " + t.platform + "  ·  " + FuelLog.cop(t.value)
                + (t.tip > 0 ? "  +" + FuelLog.cop(t.tip) : "") + "  ·  " + t.method, 17, Color.WHITE);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinimumHeight(dp(56));
        row.setBackground(rounded(0x1AFFFFFF, 12));
        Ui.pressable(row, 0.98f);
        row.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Viaje de las " + t.time)
                .setPositiveButton("Eliminar", (d, w) -> {
                    ShiftLog.removeTrip(this, t);
                    refreshTrips();
                })
                .setNegativeButton("Cancelar", null)
                .show());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private void toggleShift() {
        if (!ShiftLog.isActive(this)) {
            ShiftLog.start(this);
            Ui.startService(this, Odometer.class); // shift km come from the GPS odometer
            refreshTrips();
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("¿Terminar turno?")
                .setPositiveButton("Terminar", (d, w) -> {
                    ShiftLog.Summary s = ShiftLog.stop(this);
                    refreshTrips();
                    if (s != null) {
                        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                                .setTitle("Resumen del turno")
                                .setMessage(ShiftLog.describe(s))
                                .setPositiveButton("Listo", null)
                                .show();
                    }
                })
                .setNegativeButton("Seguir", null)
                .show();
    }

    private void askTrip(String platform, String label) {
        if (!ShiftLog.isActive(this)) {
            Toast.makeText(this, "Inicia un turno primero", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        TextView hint = Ui.text(this, "Lo que recibes tú, ya sin la comisión de la app.", 14, 0x99FFFFFF);
        hint.setSingleLine(false);
        hint.setGravity(Gravity.START);
        box.addView(hint);
        EditText value = field(box, "Valor cobrado (COP)", InputType.TYPE_CLASS_NUMBER);
        EditText tip = field(box, "Propina (opcional)", InputType.TYPE_CLASS_NUMBER);
        EditText km = field(box, "Km del viaje (opcional)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox cash = new CheckBox(this);
        cash.setText("Pago en efectivo");
        cash.setTextSize(20);
        cash.setChecked("efectivo".equals(ShiftLog.lastMethod(this)));
        box.addView(cash);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Viaje " + label)
                .setView(sv)
                .setPositiveButton("Guardar", (d, w) -> {
                    try {
                        long v = Long.parseLong(value.getText().toString().trim());
                        String ts = tip.getText().toString().trim(), ks = km.getText().toString().trim();
                        long p = ts.isEmpty() ? 0 : Long.parseLong(ts);
                        double k = ks.isEmpty() ? -1 : Double.parseDouble(ks.replace(',', '.'));
                        if (v <= 0 || p < 0) throw new NumberFormatException();
                        ShiftLog.addTrip(this, platform, v, p, cash.isChecked() ? "efectivo" : "app", k);
                        refreshTrips();
                    } catch (NumberFormatException ex) {
                        Toast.makeText(this, "Revisa el valor, la propina y los km", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------- Summary tab ----------

    private void buildSummary() {
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.card(this));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(14));
        sv.addView(box);
        period(box, "Esta semana", ShiftLog.weekStartIso());
        period(box, "Este mes", ShiftLog.monthStartIso());
        TextView note = Ui.text(this, "Neto por hora por plataforma es aproximado: reparte horas y combustible "
                + "según el número de viajes.", 12, 0x99FFFFFF);
        note.setSingleLine(false);
        note.setGravity(Gravity.START);
        box.addView(note);
        TextView fuel = button("Combustible estimado: " + FuelLog.cop(Math.round(ShiftLog.fuelPerKm(this)))
                + "/km" + (ShiftLog.fuelFromFills(this) ? " (tanqueadas)" : " (editar)"), 0x334F7BFF, this::askFuel);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, dp(56));
        flp.topMargin = dp(10);
        box.addView(fuel, flp);
        content.addView(sv, new LinearLayout.LayoutParams(-1, -1));
    }

    private void period(LinearLayout box, String title, String from) {
        TextView t = Ui.text(this, title, 20, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.START);
        t.setPadding(0, dp(8), 0, dp(4));
        box.addView(t);
        box.addView(statRow("Plataforma", "Ingresos", "Viajes", "Neto/hora", 0xB3FFFFFF, false));
        for (ShiftLog.Stat s : ShiftLog.periodStats(this, from)) {
            if (!"Total".equals(s.platform) && s.trips == 0) continue;
            box.addView(statRow(s.platform, FuelLog.cop(s.income), String.valueOf(s.trips),
                    s.netHour > 0 ? FuelLog.cop(s.netHour) : "—", Color.WHITE, "Total".equals(s.platform)));
        }
    }

    private View statRow(String a, String b, String c, String d, int color, boolean bold) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(40));
        String[] cells = {a, b, c, d};
        float[] w = {1.2f, 1.4f, 0.8f, 1.4f};
        for (int i = 0; i < 4; i++) {
            TextView tv = Ui.text(this, cells[i], 17, color);
            tv.setGravity(i == 0 ? Gravity.START : Gravity.END);
            if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(tv, new LinearLayout.LayoutParams(0, -2, w[i]));
        }
        return row;
    }

    private void askFuel() {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditText e = field(box, "COP por km (si no hay tanqueadas)", InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(ShiftLog.fallbackFuelPerKm(this)));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Costo de combustible por km")
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    try {
                        ShiftLog.setFallbackFuelPerKm(this, Integer.parseInt(e.getText().toString().trim()));
                        showTab(1);
                    } catch (NumberFormatException ex) {
                        Toast.makeText(this, "Número no válido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------- Helpers ----------

    private EditText field(LinearLayout parent, String hint, int type) {
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setHint(hint);
        e.setTextSize(22);
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private void export() {
        java.io.File out = FuelLog.exportToUsb(this);
        if (out != null) {
            Toast.makeText(this, "Exportado a " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No hay USB. Archivos en: " + FuelLog.dir(this).getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private TextView big(String label, int color, Runnable r) {
        TextView b = button(label, color, r);
        b.setTextSize(26);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(72));
        return b;
    }

    private TextView button(String label, int color, Runnable r) {
        TextView b = Ui.text(this, label, 18, Color.WHITE);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        b.setBackground(rounded(color, 14));
        Ui.pressable(b, 0.95f);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
