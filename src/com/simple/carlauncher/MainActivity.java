package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;

public class MainActivity extends Activity implements NowPlaying.Listener {


    private static final String MAPS_PKG = "com.google.android.apps.maps";
    private static final String MAPS = "com.google.android.apps.maps/com.google.android.maps.MapsActivity";
    private static final String EQ = "com.nwd.audioset/.home_horizontalActivity";
    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    private final Handler handler = new Handler();
    private final RadioClient radio = new RadioClient();
    private NowPlaying nowPlaying;

    private TextView greeting, weatherTemp, weatherDesc, weatherCity, weatherExtra;
    private ImageView weatherIcon;
    private GradientDrawable weatherBg;
    private ImageView car, carLights;
    private TextView carAlerts, acInfo, fuelInfo;
    private GradientDrawable fuelBg;
    private long fuelWarnedAt;
    private ImageView shuffleBtn, repeatBtn;
    private GradientDrawable acBg, shiftBg;
    private TextView shiftInfo, speedNum;
    private View speedChip;
    // 1 s speed refresh, runs only while resumed
    private final Runnable speedTick = new Runnable() {
        @Override public void run() {
            if (speedNum != null && speedChip.getVisibility() == View.VISIBLE) {
                int v = DriverAssist.bestKmh();
                speedNum.setText(v < 0 ? "--" : String.valueOf(v));
                speedNum.setTextColor(v > DriverAssist.limit(MainActivity.this) ? 0xFFFF5252 : Color.WHITE);
            }
            if (carMotion != null) carMotion.setSpeed(DriverAssist.bestKmh());
            handler.postDelayed(this, 1000);
        }
    };
    private CarMotionView carMotion; // wheels and road streaks while driving
    private boolean hasLightsImage;
    private boolean receiversOn; // onResume can bail out to recreate() before registering
    private TextView oilInfo;
    private boolean createdDark;
    private String createdProfile;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified();
    }
    private GradientDrawable oilBg;
    private long lastWeatherFetch;

    private TextView fmBand, fmFreq, fmUnit;
    private ImageView art, playBtn;
    private TextView songSource, songTitle, songArtist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nowPlaying = NowPlaying.get(this); // the music card reads it while being built

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(4));
        root.setBackground(Ui.background(this));

        LinearLayout cards = new LinearLayout(this);
        cards.addView(buildWeatherCard(), weight(1.2f, 0));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.addView(buildClockCard(), vweight(0.62f, 0));
        middle.addView(Profile.isXyAuto(this) ? buildMapCard() : buildFmCard(), vweight(1.38f, dp(12)));
        cards.addView(middle, weight(1f, dp(12)));

        cards.addView(buildMusicCard(), weight(1.05f, dp(12)));
        root.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildDock(), new LinearLayout.LayoutParams(-1, dp(84)));
        setContentView(root);

        radio.bind(this);
        createdDark = Ui.isDark(this);
        createdProfile = profileStamp();
        // Shared installs: ask for name, car photo and permissions on first start.
        if (!Profile.isSetupDone(this) && Profile.bundledCar(this, false) == 0) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (Profile.isNwd(this)) startService(new Intent(this, VolumeOverlay.class));
        Ui.startService(this, GreeterService.class);
        Ui.startService(this, Odometer.class);
        SpeedVolume.sync(this);
        DriverAssist.sync(this);
        if (Profile.isXyAuto(this)) new Thread(() -> XyEq.apply(getApplicationContext())).start();
        ObdClient.get(this).start(); // no-op until an adapter is paired or simulation is on
    }

    // ---------- Greeting + weather ----------

    private View buildWeatherCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(26), dp(24), dp(26), dp(22));
        weatherBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.pick(this, new int[]{0x993FA9FF, 0x667B4DFF}, new int[]{0xFF1E1E28, 0xFF121218}));
        weatherBg.setCornerRadius(dp(18));
        weatherBg.setStroke(dp(1), 0x447FD8FF);
        card.setBackground(weatherBg);

        TextView hello = left(Ui.text(this, Profile.hello(this), 34, Color.WHITE));
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(hello);

        greeting = left(Ui.text(this, "", 17, 0xCCFFFFFF));
        greeting.setPadding(0, dp(4), 0, 0);
        card.addView(greeting);

        // The user's own car (photo with background removed); headlights glow when the car's lights are on.
        // Own photo (shared builds) > bundled car with lights-on variant (owner's build) > icon.
        FrameLayout carBox = new FrameLayout(this);
        car = new ImageView(this);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights = new ImageView(this);
        carLights.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights.setAlpha(0f);
        android.graphics.Bitmap own = Profile.carPhoto(this);
        int bundled = Profile.bundledCar(this, false), bundledLights = Profile.bundledCar(this, true);
        if (own != null) {
            car.setImageBitmap(own);
        } else if (bundled != 0) {
            car.setImageResource(bundled);
            if (bundledLights != 0) {
                carLights.setImageResource(bundledLights);
                hasLightsImage = true;
            }
        } else {
            car.setImageResource(R.drawable.ic_car);
            car.setColorFilter(0x40FFFFFF);
        }
        carBox.addView(car, new FrameLayout.LayoutParams(-1, -1));
        carBox.addView(carLights, new FrameLayout.LayoutParams(-1, -1));
        if (own == null && bundled != 0) { // only the bundled Sail has known wheel positions
            android.graphics.drawable.Drawable d = getDrawable(bundled);
            carMotion = new CarMotionView(this);
            carMotion.setImageSize(d.getIntrinsicWidth(), d.getIntrinsicHeight());
            carBox.addView(carMotion, new FrameLayout.LayoutParams(-1, -1));
        }
        // Warnings drawn over the car: engine (scanner/maintenance), tires and brakes.
        carAlerts = Ui.text(this, "", 13, Color.WHITE);
        carAlerts.setPadding(dp(12), dp(4), dp(12), dp(4));
        carAlerts.setVisibility(View.GONE);
        carAlerts.setOnClickListener(v -> startActivity(new Intent(this,
                ObdClient.alert() != null ? ObdActivity.class : MaintenanceActivity.class)));
        carBox.addView(carAlerts, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        // Fuel left (scanner, or estimated from the last full tank); opens fuel and costs.
        fuelInfo = Ui.text(this, "", 13, Color.WHITE);
        fuelInfo.setPadding(dp(12), dp(4), dp(12), dp(4));
        fuelBg = new GradientDrawable();
        fuelBg.setCornerRadius(dp(12));
        fuelInfo.setBackground(fuelBg);
        fuelInfo.setOnClickListener(v -> startActivity(new Intent(this, CostsActivity.class)));
        carBox.addView(fuelInfo, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));

        // Work shift (Uber, DiDi, InDrive): time and income so far; opens the shift screen.
        shiftInfo = Ui.text(this, "", 13, Color.WHITE);
        shiftInfo.setPadding(dp(14), dp(6), dp(14), dp(6));
        shiftBg = new GradientDrawable();
        shiftBg.setCornerRadius(dp(14));
        shiftInfo.setBackground(shiftBg);
        Ui.pressable(shiftInfo, 0.92f);
        shiftInfo.setOnClickListener(v -> startActivity(new Intent(this, ShiftActivity.class)));
        // Over the car (top-right): the clock card has no height left at 1024x600.
        carBox.addView(shiftInfo, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));

        // Speedometer chip (top-left): OBD/GPS speed, red above the limit; opens the full speedometer.
        LinearLayout speedChip = new LinearLayout(this);
        speedChip.setOrientation(LinearLayout.HORIZONTAL);
        speedChip.setGravity(Gravity.BOTTOM);
        speedChip.setPadding(dp(12), dp(4), dp(12), dp(4));
        GradientDrawable speedBg = new GradientDrawable();
        speedBg.setCornerRadius(dp(16));
        speedBg.setColor(0x80000000);
        speedChip.setBackground(speedBg);
        speedNum = Ui.text(this, "--", 34, Color.WHITE);
        speedNum.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView speedUnit = Ui.text(this, " km/h", 12, 0xCCFFFFFF);
        speedUnit.setPadding(0, 0, 0, dp(6));
        speedChip.addView(speedNum);
        speedChip.addView(speedUnit);
        Ui.pressable(speedChip, 0.92f);
        speedChip.setOnClickListener(v -> startActivity(new Intent(this, SpeedActivity.class)));
        carBox.addView(speedChip, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        speedChip.setVisibility(DriverAssist.speedometerEnabled(this) ? View.VISIBLE : View.GONE);
        this.speedChip = speedChip;

        carBox.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, 0, 1f);
        clp.setMargins(dp(30), dp(8), dp(30), dp(8));
        card.addView(carBox, clp);

        // Compact weather row: icon + temperature + description, leaving room for the car.
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(0xFFFFE07A);
        now.addView(weatherIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        weatherTemp = left(Ui.text(this, "--°", 50, Color.WHITE));
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setPadding(dp(10), 0, dp(12), 0);
        now.addView(weatherTemp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        weatherDesc = left(Ui.text(this, "Cargando el clima…", 16, Color.WHITE));
        weatherDesc.setSingleLine(false);
        weatherDesc.setMaxLines(2);
        texts.addView(weatherDesc);
        weatherCity = left(Ui.text(this, "", 13, 0xB3FFFFFF));
        weatherCity.setPadding(0, dp(2), 0, 0);
        texts.addView(weatherCity);
        now.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(now);

        weatherExtra = left(Ui.text(this, "", 14, 0xE6FFFFFF));
        weatherExtra.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x26FFFFFF);
        chip.setCornerRadius(dp(20));
        weatherExtra.setBackground(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        card.addView(weatherExtra, lp);

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> fetchWeather(true));
        return card;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String day = h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
        greeting.setText(Profile.picoPlacaEnabled(this)
                ? day + " · " + PicoPlaca.status(this, Calendar.getInstance()) : day + " · Bienvenido");
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        weatherIcon.setImageResource(w.icon());
        weatherIcon.setColorFilter(w.icon() == R.drawable.ic_w_sun ? 0xFFFFD25A
                : w.icon() == R.drawable.ic_w_moon ? 0xFFDDE4FF : Color.WHITE);
        weatherTemp.setText(w.temp + "°");
        weatherDesc.setText(w.description());
        weatherCity.setText(w.city.isEmpty() ? "Sensación " + w.feelsLike + "°"
                : w.city + " · Sensación " + w.feelsLike + "°");
        weatherExtra.setText("↑ " + w.max + "°   ↓ " + w.min + "°   💧 " + w.humidity + "%   " + w.wind + " km/h");
        weatherBg.setColors(Ui.isDark(this) ? new int[]{0xFF1E1E28, 0xFF121218}
                : w.isDay ? new int[]{0x993FA9FF, 0x667B4DFF} : new int[]{0x8C2A3A9E, 0x80261A6E});
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    private final Runnable periodic = new Runnable() {
        public void run() {
            if (Ui.isDark(MainActivity.this) != createdDark) recreate();
            updateGreeting();
            if (!Profile.isNwd(MainActivity.this)) showCarLights(carLightsOn()); // no ILL broadcast: follow the clock
            updateOil(); // maintenance and scanner alerts change while driving
            fetchWeather(false);
            handler.postDelayed(this, 60 * 1000);
        }
    };

    // ---------- Car lights (ILL wire) ----------

    private final android.content.BroadcastReceiver illReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            android.util.Log.d("CarLights", "ILL broadcast extras=" + i.getExtras());
            showCarLights(i.getByteExtra("extra_ill_state", (byte) 0) != 0);
            // Auto theme follows the car's lights; give the kernel a moment to store the state.
            handler.postDelayed(() -> { if (Ui.isDark(MainActivity.this) != createdDark) recreate(); }, 1500);
        }
    };

    /** XY AUTO headlights: GreeterService stores the state first, then the car picture follows it. */
    private final android.content.BroadcastReceiver lampReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            handler.postDelayed(() -> showCarLights(carLightsOn()), 600);
        }
    };

    /**
     * NWD: the kernel keeps the light state as bit 2 of mcu_no_source_device_state. Other radios
     * (XY AUTO) until their ILL signal is known: lights on from dusk to dawn (Cartagena, ~18:00-06:00).
     */
    private boolean carLightsOn() {
        if (Profile.isNwd(this)) {
            int state = android.provider.Settings.System.getInt(getContentResolver(), "mcu_no_source_device_state", 0);
            return (state & 2) != 0;
        }
        if (XyAuto.lampKnown(this)) return XyAuto.lampOn(this);
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 18 || h < 6;
    }

    private void showCarLights(boolean on) {
        // Cross-fade between the "lights off" and "lights on" photos.
        if (!hasLightsImage) return;
        carLights.animate().alpha(on ? 1f : 0f).setDuration(600).start();
        car.animate().alpha(on ? 0f : 1f).setDuration(600).start();
    }

    /** Chip over the car listing the zones that need attention, red when anything is overdue. */
    private void updateCarAlerts() {
        StringBuilder sb = new StringBuilder();
        int worst = 0;
        String obd = ObdClient.alert();
        int engine = Math.max(obd != null ? 2 : 0, Maintenance.worstFor(this, Maintenance.ENGINE));
        int[] levels = {engine, Maintenance.worstFor(this, Maintenance.TIRES), Maintenance.worstFor(this, Maintenance.BRAKES)};
        String[] names = {"Motor", "Llantas", "Frenos"};
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == 0) continue;
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(names[i]);
            worst = Math.max(worst, levels[i]);
        }
        if (sb.length() == 0) {
            carAlerts.setVisibility(View.GONE);
            return;
        }
        carAlerts.setText("⚠ " + sb);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(worst == 2 ? 0xDDE53935 : 0xDDFF9800);
        carAlerts.setBackground(bg);
        carAlerts.setVisibility(View.VISIBLE);
    }

    private void updateShift() {
        String chip = ShiftLog.chip(this);
        shiftInfo.setText(chip.isEmpty() ? "▶ Turno" : chip);
        shiftBg.setColor(chip.isEmpty() ? 0x26FFFFFF : 0xCC2E7D32);
    }

    private void updateFuel() {
        String chip = FuelTank.chip(this);
        fuelInfo.setVisibility(chip.isEmpty() ? View.GONE : View.VISIBLE);
        if (chip.isEmpty()) return;
        boolean low = FuelTank.low(this);
        fuelInfo.setText(chip);
        fuelBg.setColor(low ? 0xDDE53935 : 0x80000000);
        long now = System.currentTimeMillis();
        if (low && now - fuelWarnedAt > 30 * 60_000L) {
            fuelWarnedAt = now;
            Tts.say(this, "Combustible bajo. Busca dónde tanquear.");
        }
    }

    private void updateAc() {
        boolean on = AcLog.isOn(this);
        acInfo.setText(on ? "❄ " + AcLog.minutesOn(this) + " min" : "❄ A/C");
        acBg.setColor(on ? 0xCC1E88E5 : 0x26FFFFFF);
    }

    private void updateOil() {
        updateCarAlerts();
        if (acInfo != null) updateAc();
        if (fuelInfo != null) updateFuel();
        if (shiftInfo != null) updateShift();
        // Scanner alerts (check engine, temperature, battery) and overdue maintenance outrank the oil countdown.
        String obd = ObdClient.alert();
        if (obd != null) {
            oilInfo.setText(obd);
            oilBg.setColor(0xCCE53935);
            return;
        }
        int worst = Maintenance.worst(this);
        if (worst > 0 && (!Odometer.isConfigured(this) || Odometer.remainingKm(this) > Odometer.WARN_KM)) {
            oilInfo.setText(Maintenance.summary(this));
            oilBg.setColor(worst == 2 ? 0xCCE53935 : 0xCCFF9800);
            return;
        }
        if (!Odometer.isConfigured(this)) {
            oilInfo.setText("Aceite: configurar");
            oilBg.setColor(0x26FFFFFF);
            return;
        }
        int left = Odometer.remainingKm(this);
        oilInfo.setText(left < 0 ? "Aceite: ¡ya toca el cambio!" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        oilBg.setColor(left < 0 ? 0xCCE53935 : left <= Odometer.WARN_KM ? 0xCCFF9800 : 0x26FFFFFF);
    }

    // ---------- Clock ----------

    private View buildClockCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(40);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.CENTER);
        card.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(14);
        date.setTextColor(0xCCFFFFFF);
        date.setPadding(0, dp(6), 0, 0);
        date.setGravity(Gravity.CENTER);
        card.addView(date);

        // Oil-change status pill; opens the oil screen.
        oilInfo = Ui.text(this, "", 13, Color.WHITE);
        oilInfo.setPadding(dp(14), dp(6), dp(14), dp(6));
        oilBg = new GradientDrawable();
        oilBg.setCornerRadius(dp(14));
        oilInfo.setBackground(oilBg);
        Ui.pressable(oilInfo, 0.92f);
        oilInfo.setOnClickListener(v -> startActivity(new Intent(this, ObdClient.alert() != null ? ObdActivity.class
                : !Odometer.isConfigured(this) ? OilActivity.class : MaintenanceActivity.class)));
        LinearLayout pills = new LinearLayout(this);
        pills.setGravity(Gravity.CENTER);
        pills.addView(oilInfo);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.topMargin = dp(8);
        card.addView(pills, olp);

        // A/C: the climate panel is mechanical, so the driver marks it here (logged to aire.csv).
        acInfo = Ui.text(this, "", 13, Color.WHITE);
        acInfo.setPadding(dp(14), dp(6), dp(14), dp(6));
        acBg = new GradientDrawable();
        acBg.setCornerRadius(dp(14));
        acInfo.setBackground(acBg);
        Ui.pressable(acInfo, 0.92f);
        acInfo.setOnClickListener(v -> {
            AcLog.toggle(this);
            updateAc();
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.leftMargin = dp(6);
        pills.addView(acInfo, alp);

        updateAc();

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> openCalendar());
        return card;
    }

    /** Calendar app if there is one; otherwise a month view that marks holidays and pico y placa. */
    private void openCalendar() {
        Intent cal = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR);
        if (safeStart(cal)) return;
        android.widget.CalendarView view = new android.widget.CalendarView(this);
        TextView info = Ui.text(this, dayInfo(Calendar.getInstance()), 16, Color.WHITE);
        info.setPadding(dp(16), dp(8), dp(16), dp(12));
        view.setOnDateChangeListener((v, y, m, d) -> {
            Calendar day = Calendar.getInstance();
            day.set(y, m, d, 8, 0, 0);
            info.setText(dayInfo(day));
        });
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(view, new LinearLayout.LayoutParams(dp(420), dp(330)));
        box.addView(info);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setView(box)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private String dayInfo(Calendar day) {
        String s = new java.text.SimpleDateFormat("EEEE d 'de' MMMM", new java.util.Locale("es")).format(day.getTime());
        if (PicoPlaca.isHoliday(day)) return s + " · Festivo";
        return s + (PicoPlaca.appliesToday(this, day) ? " · Pico y placa " + PicoPlaca.bandsLabel(this, day) : " · Sin pico y placa");
    }

    // ---------- FM ----------

    private View buildFmCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER);
        ImageView radioIcon = new ImageView(this);
        radioIcon.setImageResource(R.drawable.ic_radio);
        radioIcon.setColorFilter(0xFFFF9F5A);
        head.addView(radioIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        fmBand = Ui.text(this, "RADIO FM", 15, Color.WHITE);
        fmBand.setPadding(dp(8), 0, 0, 0);
        head.addView(fmBand);
        card.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(Ui.iconButton(this, R.drawable.ic_prev, 52, false, () -> stationStep(false)),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        fmFreq = Ui.text(this, "--.-", 50, Color.WHITE);
        fmFreq.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(fmFreq, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.iconButton(this, R.drawable.ic_next, 52, false, () -> stationStep(true)),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        row.setPadding(dp(12), dp(10), dp(12), 0);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        fmUnit = Ui.text(this, "MHz", 15, 0x99FFFFFF);
        card.addView(fmUnit);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> openRadio());
        return card;
    }

    // ---------- Mini map (XY AUTO) ----------

    private MiniMapView miniMap;
    private LinearLayout mapNav;
    private ImageView mapNavIcon;
    private TextView mapNavInstr, mapNavSub;

    private View buildMapCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.card(this));
        card.setClipToOutline(true);
        miniMap = new MiniMapView(this);
        card.addView(miniMap, new FrameLayout.LayoutParams(-1, -1));
        card.setOnClickListener(v -> openNavApp(NavInfo.WAZE, MAPS_PKG));
        card.setOnLongClickListener(v -> { openNavApp(MAPS_PKG, NavInfo.WAZE); return true; });

        // Next instruction, only while NavInfo is active.
        mapNav = new LinearLayout(this);
        mapNav.setGravity(Gravity.CENTER_VERTICAL);
        mapNav.setPadding(dp(8), dp(5), dp(10), dp(5));
        GradientDrawable nb = new GradientDrawable();
        nb.setColor(0xE6102040);
        nb.setCornerRadius(dp(12));
        nb.setStroke(dp(1), 0x6633CCFF);
        mapNav.setBackground(nb);
        mapNav.setVisibility(View.GONE);
        mapNavIcon = new ImageView(this);
        mapNavIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mapNav.addView(mapNavIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout nt = new LinearLayout(this);
        nt.setOrientation(LinearLayout.VERTICAL);
        mapNavInstr = Ui.text(this, "", 15, Color.WHITE);
        mapNavInstr.setTypeface(Typeface.DEFAULT_BOLD);
        mapNavInstr.setSingleLine(true);
        mapNavInstr.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mapNavInstr.setGravity(Gravity.START);
        nt.addView(mapNavInstr);
        mapNavSub = Ui.text(this, "", 12, 0xCCFFFFFF);
        mapNavSub.setSingleLine(true);
        mapNavSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mapNavSub.setGravity(Gravity.START);
        nt.addView(mapNavSub);
        LinearLayout.LayoutParams ntp = new LinearLayout.LayoutParams(0, -2, 1f);
        ntp.leftMargin = dp(8);
        mapNav.addView(nt, ntp);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        np.setMargins(dp(8), dp(8), dp(8), 0);
        card.addView(mapNav, np);

        // App shortcuts, bottom-left (attribution sits bottom-right).
        LinearLayout chips = new LinearLayout(this);
        chips.addView(mapChip("Waze", () -> openNavApp(NavInfo.WAZE, MAPS_PKG)));
        chips.addView(mapChip("Maps", () -> openNavApp(MAPS_PKG, NavInfo.WAZE)));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        cp.setMargins(dp(8), 0, 0, dp(8));
        card.addView(chips, cp);
        return card;
    }

    private View mapChip(String label, Runnable r) {
        TextView t = Ui.text(this, label, 12, Color.WHITE);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC000000);
        g.setCornerRadius(dp(12));
        t.setBackground(g);
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    /** Launches the preferred navigation app, or the fallback if it is not installed. */
    private void openNavApp(String first, String second) {
        for (String pkg : new String[]{first, second}) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null && safeStart(i)) return;
        }
        toast("Waze / Google Maps no están instalados");
    }

    /** Lit when on; repeat-one is shown brighter than repeat-all. Hidden for sources without a session. */
    private void updateModes() {
        if (nowPlaying == null || shuffleBtn == null || repeatBtn == null) return;
        int vis = nowPlaying.hasModes() ? View.VISIBLE : View.INVISIBLE;
        shuffleBtn.setVisibility(vis);
        repeatBtn.setVisibility(vis);
        shuffleBtn.setAlpha(nowPlaying.shuffleOn ? 1f : 0.4f);
        repeatBtn.setAlpha(nowPlaying.repeatMode == 0 ? 0.4f : nowPlaying.repeatMode == 1 ? 1f : 0.8f);
        repeatBtn.setColorFilter(nowPlaying.repeatMode == 1 ? 0xFF7FD8FF : Color.WHITE);
    }

    private void stationStep(boolean next) {
        if (Profile.isXyAuto(this)) {
            XyAuto.radioStep(this, next);
            return;
        }
        if (!Profile.isNwd(this)) {
            openRadio();
            return;
        }
        if (!Ui.radioIsSource(this)) {
            Ui.selectRadio(this, radio); // first tap from Bluetooth: bring the radio back
            handler.postDelayed(this::updateRadio, 2500);
            return;
        }
        radio.preset(next);
        handler.postDelayed(this::updateRadio, 300);
    }

    private void updateRadio() {
        RadioClient.Station st = radio.current();
        if (st == null) return;
        fmBand.setText(st.psName.isEmpty() ? "RADIO " + st.bandLabel() : st.bandLabel() + " · " + st.psName);
        fmFreq.setText(st.frequencyLabel());
        fmUnit.setText(st.isAm() ? "kHz" : "MHz");
    }

    private final Runnable radioPoll = new Runnable() {
        public void run() {
            updateRadio();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Music (any source) + navigation strip ----------

    private ImageView artBlur, navIcon;
    private View progressBar, shade;
    private float progressFrac;
    private LinearLayout navStrip;
    private TextView navInstr, navSub;
    private android.graphics.Bitmap blurredFor;
    private String sourceIconPkg = "";

    private final NavInfo.Listener navListener = this::updateNav;

    private View buildMusicCard() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.pick(this, new int[]{0xFF6A3BFF, 0xFFC04DE8, 0xFF2B1A7A},
                        new int[]{0xFF2E1A55, 0xFF1A1030, 0xFF0A0A0E}));
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setClipToOutline(true);

        // Blurred, darkened copy of the album art as background.
        artBlur = new ImageView(this);
        artBlur.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artBlur.setVisibility(View.GONE);
        card.addView(artBlur, new FrameLayout.LayoutParams(-1, -1));
        shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x55000000, 0xDD100A30}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(12), dp(16), dp(14));

        songSource = Ui.text(this, "MÚSICA", 12, Color.WHITE);
        songSource.setLetterSpacing(0.1f);
        songSource.setPadding(dp(10), dp(4), dp(12), dp(4));
        songSource.setCompoundDrawablePadding(dp(6));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x40000000);
        chip.setCornerRadius(dp(14));
        songSource.setBackground(chip);
        col.addView(songSource, new LinearLayout.LayoutParams(-2, -2));

        // Waze strip: only visible while NavInfo is active; replaces the source chip.
        navStrip = new LinearLayout(this);
        navStrip.setGravity(Gravity.CENTER_VERTICAL);
        navStrip.setPadding(dp(10), dp(8), dp(12), dp(8));
        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(0xE6102040);
        navBg.setCornerRadius(dp(14));
        navBg.setStroke(dp(1), 0x6633CCFF);
        navStrip.setBackground(navBg);
        navStrip.setVisibility(View.GONE);
        navIcon = new ImageView(this);
        navIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        navStrip.addView(navIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout navText = new LinearLayout(this);
        navText.setOrientation(LinearLayout.VERTICAL);
        navInstr = Ui.text(this, "", 18, Color.WHITE);
        navInstr.setTypeface(Typeface.DEFAULT_BOLD);
        navInstr.setSingleLine(true);
        navInstr.setEllipsize(android.text.TextUtils.TruncateAt.END);
        navInstr.setGravity(Gravity.START);
        navText.addView(navInstr);
        navSub = Ui.text(this, "", 13, 0xCCFFFFFF);
        navSub.setSingleLine(true);
        navSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        navSub.setGravity(Gravity.START);
        navText.addView(navSub);
        LinearLayout.LayoutParams ntp = new LinearLayout.LayoutParams(0, -2, 1f);
        ntp.leftMargin = dp(10);
        navStrip.addView(navText, ntp);
        Ui.pressable(navStrip, 0.97f);
        navStrip.setOnClickListener(v -> openWaze());
        col.addView(navStrip, new LinearLayout.LayoutParams(-1, -2));

        // Square, rounded cover that takes whatever height is left.
        FrameLayout artBox = new FrameLayout(this);
        art = new ImageView(this) {
            @Override
            protected void onMeasure(int w, int h) {
                int s = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
                setMeasuredDimension(s, s);
            }
        };
        art.setClipToOutline(true);
        art.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12));
            }
        });
        artBox.addView(art, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        LinearLayout.LayoutParams abp = new LinearLayout.LayoutParams(-1, 0, 1f);
        abp.topMargin = dp(8);
        abp.bottomMargin = dp(8);
        col.addView(artBox, abp);

        songTitle = Ui.text(this, "", 20, Color.WHITE);
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        songTitle.setSingleLine(true);
        songTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(songTitle);
        songArtist = Ui.text(this, "", 15, 0xCCFFFFFF);
        songArtist.setSingleLine(true);
        songArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        songArtist.setPadding(0, dp(2), 0, dp(8));
        col.addView(songArtist);

        progressBar = new View(this) {
            private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(android.graphics.Canvas c) {
                float r = getHeight() / 2f;
                p.setColor(0x44FFFFFF);
                c.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, p);
                p.setColor(Color.WHITE);
                c.drawRoundRect(0, 0, getWidth() * progressFrac, getHeight(), r, r, p);
            }
        };
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(4));
        pp.bottomMargin = dp(8);
        col.addView(progressBar, pp);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        shuffleBtn = Ui.iconButton(this, R.drawable.ic_shuffle, 34, false, () -> {
            nowPlaying.toggleShuffle();
            updateModes();
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(34), dp(34));
        mlp.rightMargin = dp(6);
        controls.addView(shuffleBtn, mlp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 48, false, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 58, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(58), dp(58));
        plp.setMargins(dp(10), 0, dp(10), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 48, false, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        repeatBtn = Ui.iconButton(this, R.drawable.ic_repeat, 34, false, () -> {
            nowPlaying.cycleRepeat();
            updateModes();
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(34), dp(34));
        rlp.leftMargin = dp(6);
        controls.addView(repeatBtn, rlp);
        updateModes();
        col.addView(controls);
        card.addView(col, new FrameLayout.LayoutParams(-1, -1));

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> nowPlaying.open(this));
        return card;
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        songSource.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        setSourceIcon(s);
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(empty ? "Toca para abrir la música" : t.artist);
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (t != null && t.art != null) {
            art.setPadding(0, 0, 0, 0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.clearColorFilter();
            art.setBackground(null);
            art.setImageBitmap(t.art);
            if (blurredFor != t.art) {
                blurredFor = t.art;
                artBlur.setImageBitmap(blur(t.art));
            }
            artBlur.setVisibility(View.VISIBLE);
        } else {
            blurredFor = null;
            artBlur.setVisibility(View.GONE);
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(0x26FFFFFF);
            ph.setCornerRadius(dp(12));
            art.setBackground(ph);
            art.setPadding(dp(28), dp(28), dp(28), dp(28));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
            art.setColorFilter(0x99FFFFFF);
        }
        updateProgress();
    }

    /** Cheap blur: shrink to a few pixels in two steps; the ImageView scales it back up with filtering. */
    private android.graphics.Bitmap blur(android.graphics.Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= 0 || h <= 0) return src;
        android.graphics.Bitmap mid = android.graphics.Bitmap.createScaledBitmap(src, 96, Math.max(8, 96 * h / w), true);
        return android.graphics.Bitmap.createScaledBitmap(mid, 24, Math.max(6, 24 * h / w), true);
    }

    private void setSourceIcon(NowPlaying.Source s) {
        android.graphics.drawable.Drawable d = null;
        String key = "";
        if (s == NowPlaying.Source.BT) {
            key = "bt";
            if (!key.equals(sourceIconPkg)) {
                d = getDrawable(R.drawable.ic_bluetooth).mutate();
                d.setTint(Color.WHITE);
            }
        } else if (s != null) {
            d = nowPlaying.icon(s);
            key = d == null ? "" : nowPlaying.track(s).pkg;
            if (key.equals(sourceIconPkg)) d = null;
        }
        if (key.equals(sourceIconPkg) && d == null) return; // unchanged
        sourceIconPkg = key;
        if (d != null) d.setBounds(0, 0, dp(16), dp(16));
        songSource.setCompoundDrawables(d, null, null, null);
    }

    /** Position bar, refreshed once a second while the activity is visible. */
    private void updateProgress() {
        long dur = nowPlaying.duration(), pos = nowPlaying.position();
        if (dur <= 0 || pos < 0) {
            progressBar.setVisibility(View.GONE);
            return;
        }
        progressFrac = (float) pos / dur;
        progressBar.setVisibility(View.VISIBLE);
        progressBar.invalidate();
    }

    private final Runnable progressTick = new Runnable() {
        public void run() {
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Waze strip ----------

    private void updateNav() {
        boolean on = NavInfo.active;
        boolean xy = Profile.isXyAuto(this);
        navStrip.setVisibility(on && !xy ? View.VISIBLE : View.GONE);
        songSource.setVisibility(on && !xy ? View.GONE : View.VISIBLE);
        if (mapNav != null) mapNav.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) return;
        if (mapNav != null) {
            mapNavInstr.setText(NavInfo.instruction);
            String ms = NavInfo.distance;
            if (!NavInfo.eta.isEmpty()) ms += (ms.isEmpty() ? "" : "  ·  ") + NavInfo.eta;
            mapNavSub.setText(ms);
            if (NavInfo.icon != null) { mapNavIcon.setImageBitmap(NavInfo.icon); mapNavIcon.clearColorFilter(); }
            else { mapNavIcon.setImageResource(R.drawable.ic_compass); mapNavIcon.setColorFilter(Color.WHITE); }
        }
        navInstr.setText(NavInfo.instruction);
        String sub = NavInfo.distance;
        if (!NavInfo.eta.isEmpty()) sub += (sub.isEmpty() ? "" : "  ·  ") + NavInfo.eta;
        navSub.setText(sub);
        if (NavInfo.icon != null) {
            navIcon.setImageBitmap(NavInfo.icon);
        } else {
            navIcon.setImageResource(R.drawable.ic_compass);
            navIcon.setColorFilter(Color.WHITE);
        }
        if (NavInfo.icon != null) navIcon.clearColorFilter();
    }

    private void openWaze() {
        Intent i = getPackageManager().getLaunchIntentForPackage(NavInfo.WAZE);
        if (i == null || !safeStart(i)) toast("Waze no está instalado");
    }

    // ---------- Dock ----------

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(dockIcon(R.drawable.ic_compass, this::openMaps), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_bluetooth, this::openBluetooth), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_radio, this::openRadio), weight(1f, 0));

        FrameLayout center = new FrameLayout(this);
        RingView ring = new RingView(this);
        center.addView(ring, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        Ui.pressable(ring, 0.88f);
        ring.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        dock.addView(center, weight(1f, 0));

        dock.addView(dockIcon(R.drawable.ic_w_moon,
                () -> startActivity(new Intent(this, ScreenOffActivity.class))), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_settings, this::openEqualizer), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_gear, this::openSettings), weight(1f, 0));
        return dock;
    }

    // ---------- Shortcuts: NWD screens, or the head unit's own apps elsewhere ----------

    private void openRadio() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, RadioActivity.class));
        else if (Profile.isXyAuto(this)) Ui.launch(this, XyAuto.RADIO); // the factory app: direct control proved unreliable
        else if (!launchByKeyword("radio", "fm", "tuner")) toast("No se encontró una app de radio");
    }

    private void openBluetooth() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, BtActivity.class));
        else if (Profile.isXyAuto(this)) startActivity(new Intent(this, XyBtActivity.class));
        else if (!launchByKeyword("bluetooth", "bt music", "btmusic", "phone link"))
            safeStart(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void openEqualizer() {
        if (Profile.isNwd(this)) {
            startActivity(new Intent(this, EqActivity.class));
            return;
        }
        if (Profile.isXyAuto(this)) {
            startActivity(new Intent(this, XyEqActivity.class));
            return;
        }
        Intent eq = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (!safeStart(eq) && !launchByKeyword("ecualiz", "equaliz", "audio", "sonido", "dsp"))
            toast("No se encontró un ecualizador");
    }

    private void openSettings() {
        if (Profile.isNwd(this)) Ui.launch(this, SETTINGS);
        else if (Profile.isXyAuto(this)) Ui.launch(this, XyAuto.SETTINGS);
        else safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    private void openMaps() {
        Intent maps = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
        if (maps == null || !safeStart(maps)) {
            safeStart(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=")));
        }
    }

    /** Opens the first installed app whose name or package matches one of the keywords. */
    private boolean launchByKeyword(String... keywords) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            String label = (ri.loadLabel(pm) + " " + ri.activityInfo.packageName).toLowerCase();
            for (String k : keywords) {
                if (label.contains(k)) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    return safeStart(i);
                }
            }
        }
        return false;
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private View dockIcon(int icon, Runnable action) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(0xE6FFFFFF);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        f.addView(iv, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        return f;
    }

    // ---------- Lifecycle & helpers ----------

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.isDark(this) != createdDark || !profileStamp().equals(createdProfile)) {
            // theme (setting, night or car lights) or profile (name, car photo) changed
            recreate();
            return;
        }
        updateGreeting();
        updateOil();
        showWeather(Weather.cached(this));
        registerReceiver(illReceiver, new android.content.IntentFilter("com.nwd.ACTION_ILL_STATE_CHANGE"));
        registerReceiver(lampReceiver, new android.content.IntentFilter(XyAuto.LAMP));
        receiversOn = true;
        if (miniMap != null) miniMap.onResume();
        boolean lights = hasLightsImage && carLightsOn();
        android.util.Log.d("CarLights", "onResume lights=" + lights);
        carLights.setAlpha(lights ? 1f : 0f);
        car.setAlpha(lights ? 0f : 1f);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        handler.post(periodic);
        handler.post(speedTick);
        if (carMotion != null) carMotion.resume();
        CarEvents.startAll(this); // services may have been killed while the car was off (quick boot)
        if (Profile.isNwd(this)) handler.post(radioPoll);
        nowPlaying.addListener(this);
        NavInfo.addListener(navListener);
        updateNav();
        onNowPlayingChanged();
        handler.postDelayed(progressTick, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiversOn) {
            unregisterReceiver(illReceiver);
            unregisterReceiver(lampReceiver);
            receiversOn = false;
        }
        if (miniMap != null) miniMap.onPause();
        handler.removeCallbacks(periodic);
        handler.removeCallbacks(speedTick);
        if (carMotion != null) carMotion.pause();
        handler.removeCallbacks(radioPoll);
        nowPlaying.removeListener(this);
        NavInfo.removeListener(navListener);
        handler.removeCallbacks(progressTick);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) fetchWeather(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        radio.unbind(this);
    }

    private TextView left(TextView tv) {
        tv.setGravity(Gravity.START);
        return tv;
    }

    private LinearLayout.LayoutParams weight(float w, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        lp.leftMargin = left;
        return lp;
    }

    private LinearLayout.LayoutParams vweight(float w, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, w);
        lp.topMargin = top;
        return lp;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.94f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }
}
