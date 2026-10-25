package com.simple.carlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Live engine data (rpm, speed, coolant, fuel use), trouble codes and OBD adapter setup. */
public class ObdActivity extends Activity implements ObdClient.Listener {

    private Gauge rpm, speed, coolant, fuel;
    private TextView status, extra, codes, simBtn;
    private ObdClient obd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        obd = ObdClient.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(14));
        root.setBackground(Ui.background(this));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "Diagnóstico del motor", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(margin(button("Adaptador", 0x334F7BFF, this::pickDevice)));
        simBtn = button("", 0x334F7BFF, this::toggleSim);
        bar.addView(margin(simBtn));
        bar.addView(margin(button("Volver", 0xFF2ECC71, this::finish)));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(10);
        root.addView(body, blp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        rpm = new Gauge(this, "RPM", 0, 7000);
        speed = new Gauge(this, "km/h", 0, 180);
        coolant = new Gauge(this, "Temp. °C", 40, 130);
        fuel = new Gauge(this, "L/100 km", 0, 20);
        left.addView(row(rpm, speed), new LinearLayout.LayoutParams(-1, 0, 1f));
        left.addView(row(coolant, fuel), new LinearLayout.LayoutParams(-1, 0, 1f));
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1.25f));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(Ui.card(this));
        status = Ui.text(this, "", 16, Color.WHITE);
        status.setSingleLine(false);
        status.setGravity(Gravity.START);
        card.addView(status);
        extra = Ui.text(this, "", 14, 0xCCFFFFFF);
        extra.setSingleLine(false);
        extra.setGravity(Gravity.START);
        extra.setPadding(0, dp(8), 0, dp(8));
        card.addView(extra);
        TextView h = Ui.text(this, "Códigos de falla", 15, 0xCCFFFFFF);
        h.setGravity(Gravity.START);
        card.addView(h);
        codes = Ui.text(this, "", 14, Color.WHITE);
        codes.setSingleLine(false);
        codes.setGravity(Gravity.START);
        codes.setPadding(0, dp(4), 0, 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(codes);
        card.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -1, 1f);
        clp.leftMargin = dp(12);
        body.addView(card, clp);

        setContentView(root);
        update(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        obd.addListener(this);
        obd.start();
        refreshSim();
    }

    @Override
    protected void onPause() {
        super.onPause();
        obd.removeListener(this);
    }

    @Override
    public void onObd(ObdClient.Snapshot s) {
        update(s);
    }

    private void update(ObdClient.Snapshot s) {
        boolean live = s != null && s.connected;
        if (ObdClient.mac(this).isEmpty() && !ObdClient.simulation(this)) {
            status.setText("Sin adaptador. Toca «Adaptador» para elegir el OBD2 Bluetooth ya emparejado.");
        } else if (!live) {
            status.setText("Conectando con el adaptador…");
        } else {
            String a = ObdClient.alert();
            status.setText((s.simulated ? "SIMULACIÓN · " : "Conectado · ")
                    + (a != null ? a : "Todo normal"));
            status.setTextColor(a != null ? 0xFFFF6B5A : Color.WHITE);
        }
        if (!live) {
            rpm.set(Float.NaN, "--", 0xFF2EE6FF);
            speed.set(Float.NaN, "--", 0xFF2EE6FF);
            coolant.set(Float.NaN, "--", 0xFF2EE6FF);
            fuel.set(Float.NaN, "--", 0xFF2EE6FF);
            extra.setText("");
            codes.setText("");
            return;
        }
        rpm.set(s.rpm, fmt(s.rpm, 0), 0xFF2EE6FF);
        speed.set(s.speed, fmt(s.speed, 0), 0xFF4F7BFF);
        coolant.set(s.coolant, fmt(s.coolant, 0),
                s.coolant > 105 ? 0xFFFF4D4D : s.coolant > 98 ? 0xFFFFA63D : 0xFFB45CFF);
        fuel.set(s.lPer100km, fmt(s.lPer100km, 1), 0xFF2ECC71);
        extra.setText("Consumo: " + fmt(s.lPerHour, 1) + " L/h\n"
                + "Presión admisión: " + fmt(s.map, 0) + " kPa · Aire: " + fmt(s.iat, 0) + " °C\n"
                + "Batería: " + fmt(s.volts, 1) + " V · Carga: " + fmt(s.load, 0) + " %\n"
                + "Acumulado: " + String.format(Locale.US, "%.1f L en %.0f km",
                ObdClient.totalLiters(this), ObdClient.totalKm(this)));
        if (s.dtcs.isEmpty()) {
            codes.setText(s.milOn ? "Luz de motor encendida (sin códigos legibles)" : "Sin códigos de falla");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String c : s.dtcs) sb.append(c).append(": ").append(explain(c)).append('\n');
            codes.setText(sb.toString().trim());
        }
    }

    private static String fmt(float v, int dec) {
        return Float.isNaN(v) ? "--" : String.format(Locale.US, "%." + dec + "f", v);
    }

    /** Short Spanish meaning of a trouble code (a few specific ones, else by family). */
    static String explain(String c) {
        switch (c) {
            case "P0300": return "Falla de encendido aleatoria";
            case "P0301": case "P0302": case "P0303": case "P0304":
                return "Falla de encendido en cilindro " + c.charAt(4);
            case "P0171": return "Mezcla muy pobre (poco combustible o aire de más)";
            case "P0172": return "Mezcla muy rica";
            case "P0420": return "Catalizador con baja eficiencia";
            case "P0335": return "Sensor del cigüeñal";
            case "P0117": case "P0118": return "Sensor de temperatura del motor";
            case "P0113": case "P0112": return "Sensor de temperatura del aire";
            case "P0107": case "P0108": return "Sensor de presión del múltiple (MAP)";
            default:
        }
        if (c.length() < 3) return "Código desconocido";
        switch (c.substring(0, 3)) {
            case "P00": return "Control de aire y combustible";
            case "P01": return "Sistema de aire / combustible";
            case "P02": return "Inyectores";
            case "P03": return "Encendido / falla de chispa";
            case "P04": return "Control de emisiones";
            case "P05": return "Velocidad / ralentí";
            case "P06": return "Computador del motor";
            case "P07": case "P08": case "P09": return "Transmisión";
            default:
        }
        switch (c.charAt(0)) {
            case 'B': return "Carrocería";
            case 'C': return "Chasis (frenos, dirección)";
            case 'U': return "Comunicación entre módulos";
            default: return "Tren motriz";
        }
    }

    // ---------- Adapter / simulation ----------

    private void pickDevice() {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null || !ad.isEnabled()) {
            Toast.makeText(this, "Bluetooth apagado", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<BluetoothDevice> bonded = ad.getBondedDevices();
        final List<BluetoothDevice> devs = new ArrayList<>(bonded);
        if (devs.isEmpty()) {
            Toast.makeText(this, "Empareja primero el adaptador OBD2 (clave 1234 o 0000)", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[devs.size()];
        for (int i = 0; i < names.length; i++) {
            BluetoothDevice d = devs.get(i);
            names[i] = (d.getName() == null ? "?" : d.getName()) + "  " + d.getAddress();
        }
        new AlertDialog.Builder(this).setTitle("Adaptador OBD2")
                .setItems(names, (dlg, which) -> {
                    Profile.prefs(this).edit().putString("obd_mac", devs.get(which).getAddress()).apply();
                    obd.restart();
                }).show();
    }

    private void toggleSim() {
        Profile.prefs(this).edit().putBoolean("obd_sim", !ObdClient.simulation(this)).apply();
        obd.restart();
        refreshSim();
    }

    private void refreshSim() {
        simBtn.setText(ObdClient.simulation(this) ? "Simulación: sí" : "Simulación: no");
    }

    // ---------- Views ----------

    private View row(View a, View b) {
        LinearLayout r = new LinearLayout(this);
        r.addView(a, new LinearLayout.LayoutParams(0, -1, 1f));
        r.addView(b, new LinearLayout.LayoutParams(0, -1, 1f));
        return r;
    }

    private View margin(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView button(String s, int color, Runnable r) {
        TextView b = Ui.text(this, s, 16, Color.WHITE);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        Ui.pressable(b, 0.95f);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    /** 270-degree arc gauge with the value in the middle. */
    private static final class Gauge extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final String unit;
        private final float min, max;
        private float value = Float.NaN;
        private String text = "--";

        Gauge(Context c, String unit, float min, float max) {
            super(c);
            this.unit = unit;
            this.min = min;
            this.max = max;
            float d = c.getResources().getDisplayMetrics().density;
            for (Paint p : new Paint[]{track, arc}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(12 * d);
            }
            track.setColor(0x26FFFFFF);
            big.setColor(Color.WHITE);
            big.setTextSize(34 * d);
            big.setTypeface(Typeface.DEFAULT_BOLD);
            big.setTextAlign(Paint.Align.CENTER);
            small.setColor(0x99FFFFFF);
            small.setTextSize(14 * d);
            small.setTextAlign(Paint.Align.CENTER);
        }

        void set(float v, String t, int color) {
            value = v;
            text = t;
            arc.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h), pad = track.getStrokeWidth();
            box.set(w / 2 - s / 2 + pad, h / 2 - s / 2 + pad, w / 2 + s / 2 - pad, h / 2 + s / 2 - pad);
            c.drawArc(box, 135, 270, false, track);
            if (!Float.isNaN(value)) {
                float f = Math.max(0, Math.min(1, (value - min) / (max - min)));
                c.drawArc(box, 135, 270 * f, false, arc);
            }
            c.drawText(text, w / 2, h / 2 + big.getTextSize() * 0.2f, big);
            c.drawText(unit, w / 2, h / 2 + big.getTextSize() * 0.2f + small.getTextSize() * 1.6f, small);
        }
    }
}
