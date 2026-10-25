package com.simple.carlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Fuel fill-ups and running costs: register fills, see km/gal, cost per km and spending. */
public class CostsActivity extends Activity {

    private LinearLayout stats, history;

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
        TextView title = Ui.text(this, "Combustible y costos", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(button("Exportar a USB", 0x334F7BFF, this::export), new LinearLayout.LayoutParams(-2, -2));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(10);
        root.addView(body, blp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(16), dp(16), dp(16), dp(16));
        left.setBackground(Ui.card(this));
        stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.VERTICAL);
        left.addView(stats, new LinearLayout.LayoutParams(-1, 0, 1f));
        left.addView(button("Registrar tanqueada", 0xFF2ECC71, () -> askFill()), new LinearLayout.LayoutParams(-1, dp(64)));
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.card(this));
        history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        history.setPadding(dp(12), dp(10), dp(12), dp(10));
        scroll.addView(history);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1.2f);
        rlp.leftMargin = dp(12);
        body.addView(scroll, rlp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<FuelLog.Entry> list = FuelLog.load(this);
        Calendar now = Calendar.getInstance();
        stats.removeAllViews();
        double kmpg = FuelLog.avgKmPerGal(list), ckm = FuelLog.avgCostPerKm(list);
        stats.addView(stat("Rendimiento promedio", kmpg > 0 ? String.format(Locale.US, "%.1f km/gal", kmpg) : "—"));
        stats.addView(stat("Costo por km", ckm > 0 ? FuelLog.cop(Math.round(ckm)) + "/km" : "—"));
        stats.addView(stat("Gasto de este mes", FuelLog.cop(FuelLog.monthSpend(list, now))));
        stats.addView(stat("Últimos 12 meses", FuelLog.cop(FuelLog.last12Months(list, now))));

        history.removeAllViews();
        if (list.isEmpty()) {
            TextView empty = Ui.text(this, "Aún no hay tanqueadas registradas", 16, 0x99FFFFFF);
            empty.setPadding(0, dp(20), 0, 0);
            history.addView(empty);
        }
        for (int i = list.size() - 1; i >= 0; i--) history.addView(row(list.get(i)));
    }

    private View stat(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(10));
        TextView l = Ui.text(this, label, 14, 0xB3FFFFFF);
        l.setGravity(Gravity.START);
        TextView v = Ui.text(this, value, 28, Color.WHITE);
        v.setGravity(Gravity.START);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(l);
        box.addView(v);
        return box;
    }

    private View row(FuelLog.Entry e) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinimumHeight(dp(72));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rounded(0x1AFFFFFF, 12));
        Ui.pressable(row, 0.98f);
        row.setOnClickListener(v -> confirmDelete(e));
        TextView top = Ui.text(this, e.date + "  ·  " + FuelLog.cop(e.cost) + "  ·  "
                + String.format(Locale.US, "%.2f gal", e.gallons) + (e.full ? "" : "  (parcial)"), 17, Color.WHITE);
        top.setGravity(Gravity.START);
        String sub = "km " + OilActivity.fmt(e.km);
        if (e.kmPerGal > 0) {
            sub += String.format(Locale.US, "  ·  %.1f km/gal  ·  ", e.kmPerGal) + FuelLog.cop(Math.round(e.costPerKm)) + "/km";
        }
        TextView bottom = Ui.text(this, sub, 14, 0xB3FFFFFF);
        bottom.setGravity(Gravity.START);
        row.addView(top);
        row.addView(bottom);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private void confirmDelete(FuelLog.Entry e) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Tanqueada del " + e.date)
                .setPositiveButton("Eliminar", (d, w) -> {
                    FuelLog.remove(this, e);
                    refresh();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void askFill() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditText date = field(box, "Fecha (aaaa-mm-dd)", PicoPlaca.iso(Calendar.getInstance()), InputType.TYPE_CLASS_DATETIME);
        EditText km = field(box, "Km del odómetro", Odometer.isConfigured(this) ? String.valueOf(Odometer.currentKm(this)) : "",
                InputType.TYPE_CLASS_NUMBER);
        EditText gal = field(box, "Galones", "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText cost = field(box, "Valor total (COP)", "", InputType.TYPE_CLASS_NUMBER);
        CheckBox full = new CheckBox(this);
        full.setText("Tanque lleno");
        full.setTextSize(18);
        full.setChecked(true);
        box.addView(full);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Registrar tanqueada")
                .setView(sv)
                .setPositiveButton("Guardar", (d, w) -> {
                    try {
                        FuelLog.Entry e = new FuelLog.Entry();
                        e.date = date.getText().toString().trim();
                        if (PicoPlaca.parseIso(e.date) == null) throw new NumberFormatException();
                        e.km = Integer.parseInt(km.getText().toString().trim());
                        e.gallons = Double.parseDouble(gal.getText().toString().trim().replace(',', '.'));
                        e.cost = Long.parseLong(cost.getText().toString().trim());
                        e.full = full.isChecked();
                        if (e.gallons <= 0 || e.cost <= 0) throw new NumberFormatException();
                        FuelLog.add(this, e);
                        refresh();
                    } catch (NumberFormatException ex) {
                        Toast.makeText(this, "Revisa los datos: fecha, km, galones y valor", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private EditText field(LinearLayout parent, String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(22);
        parent.addView(e);
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
