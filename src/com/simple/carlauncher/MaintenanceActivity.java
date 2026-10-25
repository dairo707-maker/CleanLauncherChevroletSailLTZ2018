package com.simple.carlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Maintenance schedule: every item with its status; tap to mark it done or edit its interval. */
public class MaintenanceActivity extends Activity {

    private LinearLayout list;

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
        TextView title = Ui.text(this, "Mantenimiento", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView costs = Ui.text(this, "Combustible y costos", 16, Color.WHITE);
        costs.setPadding(dp(18), dp(12), dp(18), dp(12));
        costs.setBackground(rounded(0x334F7BFF, 14));
        Ui.pressable(costs, 0.95f);
        costs.setOnClickListener(v -> startActivity(new Intent(this, CostsActivity.class)));
        bar.addView(costs, new LinearLayout.LayoutParams(-2, -2));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        slp.topMargin = dp(10);
        root.addView(scroll, slp);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        if (!Odometer.isConfigured(this)) {
            TextView hint = Ui.text(this, "Configura el kilometraje en Aceite para medir también por km.", 14, 0xB3FFFFFF);
            hint.setPadding(dp(4), dp(4), dp(4), dp(10));
            list.addView(hint);
        }
        List<Maintenance.Item> items = Maintenance.items(this);
        for (Maintenance.Item it : items) list.addView(row(it));
    }

    private View row(Maintenance.Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(8), dp(18), dp(8));
        row.setMinimumHeight(dp(76));
        row.setBackground(Ui.card(this));
        Ui.pressable(row, 0.98f);
        row.setOnClickListener(v -> showActions(it));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(this, it.name, 20, Color.WHITE);
        name.setGravity(Gravity.START);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(name);
        TextView detail = Ui.text(this, it.detail() + "  ·  cada " + OilActivity.fmt(it.intervalKm)
                + " km / " + it.intervalMonths + " m", 14, 0xB3FFFFFF);
        detail.setGravity(Gravity.START);
        texts.addView(detail);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        int color = it.status == Maintenance.OVERDUE ? 0xFFE53935
                : it.status == Maintenance.SOON ? 0xFFFFB300 : 0xFF2ECC71;
        String label = it.status == Maintenance.OVERDUE ? "VENCIDO"
                : it.status == Maintenance.SOON ? "PRONTO" : "OK";
        TextView pill = Ui.text(this, label, 15, it.status == Maintenance.SOON ? 0xFF1A1A1A : Color.WHITE);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setPadding(dp(16), dp(8), dp(16), dp(8));
        pill.setBackground(rounded(color, 20));
        row.addView(pill, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private void showActions(Maintenance.Item it) {
        boolean known = Odometer.isConfigured(this);
        String done = known ? "Marcar hecho hoy al km " + OilActivity.fmt(Odometer.currentKm(this))
                : "Marcar hecho hoy";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(it.name)
                .setMessage(it.detail())
                .setPositiveButton(done, (d, w) -> {
                    Maintenance.markDone(this, it.id);
                    Toast.makeText(this, "¡Listo! Registrado: " + it.name, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton("Editar intervalo", (d, w) -> editInterval(it))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void editInterval(Maintenance.Item it) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditText km = number("Cada cuántos km", it.intervalKm);
        EditText months = number("Cada cuántos meses", it.intervalMonths);
        box.addView(km);
        box.addView(months);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Intervalo: " + it.name)
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    try {
                        int k = Integer.parseInt(km.getText().toString().trim());
                        int m = Integer.parseInt(months.getText().toString().trim());
                        if (k <= 0 || m <= 0) return;
                        Maintenance.setInterval(this, it.id, k, m);
                        refresh();
                    } catch (NumberFormatException ignored) { }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private EditText number(String hint, int value) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint(hint);
        e.setText(String.valueOf(value));
        e.setSelectAllOnFocus(true);
        e.setTextSize(22);
        return e;
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
