package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.InputStream;

/** First-run / settings screen for shared installs: name, car photo, voice, permissions. */
public class SetupActivity extends Activity {

    private static final int PICK_PHOTO = 1;

    private EditText name, plate, rotation, validTo;
    private ImageView carPreview;
    private Switch voice, picoPlaca, speedVol;
    private TextView homeBtn, locationBtn, notifBtn, overlayBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(14));
        root.setBackground(Ui.background(this));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "Configura tu inicio", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView done = button("Listo", 0xFF2ECC71, this::finishSetup);
        bar.addView(done, new LinearLayout.LayoutParams(-2, -2));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(10);
        root.addView(body, blp);
        body.addView(scroll(buildProfile()), new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
        rlp.leftMargin = dp(12);
        body.addView(scroll(buildPermissions()), rlp);

        setContentView(root);
    }

    private View scroll(View content) {
        ScrollView s = new ScrollView(this);
        s.setBackground(Ui.card(this));
        s.addView(content);
        return s;
    }

    private View buildProfile() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));

        card.addView(label("Tu nombre"));
        name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(Profile.name(this));
        name.setHint("Ej.: Ana");
        name.setTextColor(Color.WHITE);
        name.setHintTextColor(0x66FFFFFF);
        name.setTextSize(20);
        card.addView(name);

        card.addView(label("Foto de tu carro"));
        carPreview = new ImageView(this);
        carPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(carPreview, new LinearLayout.LayoutParams(-1, dp(110)));
        LinearLayout photoRow = new LinearLayout(this);
        photoRow.addView(button("Elegir foto", 0x334F7BFF, this::pickPhoto),
                new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(0, -2, 1f);
        qlp.leftMargin = dp(8);
        photoRow.addView(button("Quitar", 0x26FFFFFF, () -> {
            Profile.carPhotoFile(this).delete();
            showPhoto();
        }), qlp);
        card.addView(photoRow);
        TextView tip = Ui.text(this, "Consejo: una foto del carro en diagonal, de frente, con fondo despejado.", 12, 0x99FFFFFF);
        tip.setSingleLine(false);
        tip.setGravity(Gravity.START);
        tip.setPadding(0, dp(6), 0, dp(4));
        card.addView(tip);

        voice = toggle(card, "Saludo por voz al encender el carro", Profile.voiceEnabled(this));
        picoPlaca = toggle(card, "Recordar pico y placa (Cartagena)", Profile.picoPlacaEnabled(this));
        speedVol = toggle(card, "Subir el volumen según la velocidad (GPS)", SpeedVolume.enabled(this));
        card.addView(label("Último dígito de la placa"));
        plate = new EditText(this);
        plate.setInputType(InputType.TYPE_CLASS_NUMBER);
        plate.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1)});
        int digit = PicoPlaca.plateDigit(this);
        plate.setText(digit >= 0 ? String.valueOf(digit) : "");
        plate.setHint("Ej.: 7");
        plate.setTextColor(Color.WHITE);
        plate.setHintTextColor(0x66FFFFFF);
        plate.setTextSize(20);
        card.addView(plate);

        card.addView(label("Rotación (lunes a viernes)"));
        rotation = new EditText(this);
        rotation.setText(PicoPlaca.rotation(this));
        rotation.setTextColor(Color.WHITE);
        rotation.setTextSize(18);
        card.addView(rotation);
        card.addView(label("Válida hasta (aaaa-mm-dd)"));
        validTo = new EditText(this);
        validTo.setText(PicoPlaca.validTo(this));
        validTo.setTextColor(Color.WHITE);
        validTo.setTextSize(18);
        card.addView(validTo);
        TextView ppTip = Ui.text(this, "Formato: 1,2;3,4;5,6;7,8;9,0 (dígitos de lunes a viernes).", 12, 0x99FFFFFF);
        ppTip.setSingleLine(false);
        ppTip.setGravity(Gravity.START);
        card.addView(ppTip);
        showPhoto();
        return card;
    }

    private View buildPermissions() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.addView(label("Para que todo funcione"));

        homeBtn = button("", 0x334F7BFF, () -> openSettings(new Intent(Settings.ACTION_HOME_SETTINGS)));
        card.addView(spaced(homeBtn));
        locationBtn = button("", 0x334F7BFF,
                () -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1));
        card.addView(spaced(locationBtn));
        notifBtn = button("", 0x334F7BFF,
                () -> openSettings(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        card.addView(spaced(notifBtn));
        if (Profile.isNwd(this)) {
            overlayBtn = button("", 0x334F7BFF, () -> openSettings(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName()))));
            card.addView(spaced(overlayBtn));
        }

        card.addView(label("Herramientas del carro"));
        card.addView(spaced(button("Mantenimiento, combustible y costos", 0x334F7BFF,
                () -> startActivity(new Intent(this, MaintenanceActivity.class)))));
        card.addView(spaced(button("Escáner OBD2 (ELM327)", 0x334F7BFF,
                () -> startActivity(new Intent(this, ObdActivity.class)))));
        card.addView(spaced(button("Diagnóstico del radio", 0x334F7BFF,
                () -> startActivity(new Intent(this, DiagnosticsActivity.class)))));

        TextView info = Ui.text(this, Profile.isNwd(this)
                ? "Radio NWD detectada: radio FM, Bluetooth, ecualizador y aviso de volumen activados."
                : "En esta radio se usarán sus propias apps de radio, Bluetooth y ecualizador.", 13, 0xB3FFFFFF);
        info.setSingleLine(false);
        info.setGravity(Gravity.START);
        info.setPadding(0, dp(12), 0, 0);
        card.addView(info);
        return card;
    }

    // ---------- Helpers ----------

    private TextView label(String s) {
        TextView t = Ui.text(this, s, 15, 0xCCFFFFFF);
        t.setGravity(Gravity.START);
        t.setPadding(0, dp(10), 0, dp(6));
        return t;
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

    private View spaced(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        v.setLayoutParams(lp);
        return v;
    }

    private Switch toggle(LinearLayout parent, String text, boolean on) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextColor(Color.WHITE);
        s.setTextSize(15);
        s.setChecked(on);
        s.setPadding(0, dp(10), 0, dp(4));
        parent.addView(s);
        return s;
    }

    private void openSettings(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void showPhoto() {
        Bitmap b = Profile.carPhoto(this);
        if (b != null) {
            carPreview.clearColorFilter();
            carPreview.setImageBitmap(b);
        } else {
            carPreview.setImageResource(R.drawable.ic_car);
            carPreview.setColorFilter(0x55FFFFFF);
        }
    }

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, "Foto de tu carro"), PICK_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "No hay galería ni gestor de archivos", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request != PICK_PHOTO || result != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (b == null) throw new IllegalStateException();
            int w = Math.min(760, b.getWidth());
            Bitmap scaled = Bitmap.createScaledBitmap(b, w, Math.round(b.getHeight() * w / (float) b.getWidth()), true);
            try (FileOutputStream out = new FileOutputStream(Profile.carPhotoFile(this))) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            showPhoto();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo usar esa imagen", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean isHome = getPackageName().equals(homePackage());
        homeBtn.setText(check(isHome) + "Usar como pantalla de inicio");
        locationBtn.setText(check(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) + "Ubicación (para el clima)");
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        notifBtn.setText(check(listeners != null && listeners.contains(getPackageName()))
                + "Ver la música de otras apps");
        if (overlayBtn != null) {
            overlayBtn.setText(check(Settings.canDrawOverlays(this)) + "Aviso de volumen");
        }
    }

    private String homePackage() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo r = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return r == null || r.activityInfo == null ? "" : r.activityInfo.packageName;
    }

    private static String check(boolean ok) {
        return ok ? "✓  " : "→  ";
    }

    private void finishSetup() {
        String rot = rotation.getText().toString().trim();
        String to = validTo.getText().toString().trim();
        if (PicoPlaca.parseRotation(rot) == null || PicoPlaca.parseIso(to) == null) {
            Toast.makeText(this, "Revisa la rotación y la fecha del pico y placa", Toast.LENGTH_LONG).show();
            return;
        }
        String d = plate.getText().toString().trim();
        PicoPlaca.setPlateDigit(this, d.isEmpty() ? -1 : Integer.parseInt(d));
        // Keep the stored start date unless the new range would exclude today.
        String from = PicoPlaca.validFrom(this);
        if (from.compareTo(to) > 0) from = PicoPlaca.DEFAULT_FROM;
        PicoPlaca.setRotation(this, rot, from, to);
        Profile.prefs(this).edit()
                .putString("name", name.getText().toString().trim())
                .putBoolean("voice", voice.isChecked())
                .putBoolean("picoplaca", picoPlaca.isChecked())
                .putBoolean("speed_volume", speedVol.isChecked())
                .putBoolean("setup_done", true)
                .apply();
        SpeedVolume.sync(this);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
