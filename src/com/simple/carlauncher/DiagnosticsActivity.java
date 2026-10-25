package com.simple.carlauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * "Diagnóstico del radio": dumps what this head unit is (hardware, apps, components, cameras,
 * sensors, audio) into a text file, and can sniff broadcasts for 60 s (ACC, reverse, lights...).
 * Meant for when ADB over Wi-Fi is not available.
 */
public class DiagnosticsActivity extends Activity {

    private static final String FILE = "diagnostico_radio.txt";
    private static final String NAME_FILTER =
            "xyauto|autochips|atc|mediatek|mtk|car|radio|bt|bluetooth|can|mcu|camera|launcher|setting|media|music|video|avm|dvr|tpms";

    /** Head-unit actions seen on NWD, SYU, Microntek and generic Chinese Android units (guesses). */
    private static final String[] ACTIONS = {
            "android.intent.action.ACC_ON", "android.intent.action.ACC_OFF",
            "com.android.action.ACC_ON", "com.android.action.ACC_OFF",
            "android.intent.action.REVERSE", "android.intent.action.REVERSE_ON", "android.intent.action.REVERSE_OFF",
            "android.intent.action.BACKCAR", "android.intent.action.BACK_CAR",
            "com.car.action.BACK_CAR", "com.car.action.BACK_CAR_ON", "com.car.action.BACK_CAR_OFF",
            "android.intent.action.ILL_ON", "android.intent.action.ILL_OFF",
            "android.intent.action.SLEEP", "android.intent.action.WAKEUP",
            "android.intent.action.SCREEN_ON", "android.intent.action.SCREEN_OFF",
            "android.intent.action.ACTION_SHUTDOWN", "android.intent.action.QUICKBOOT_POWEROFF",
            "android.intent.action.BOOT_COMPLETED", "android.intent.action.USER_PRESENT",
            "android.intent.action.HEADSET_PLUG", "android.intent.action.MEDIA_MOUNTED",
            "android.intent.action.MEDIA_UNMOUNTED", "android.intent.action.MEDIA_EJECT",
            "android.hardware.usb.action.USB_STATE", "android.hardware.usb.action.USB_DEVICE_ATTACHED",
            "android.net.conn.CONNECTIVITY_CHANGE", "android.bluetooth.adapter.action.STATE_CHANGED",
            "android.bluetooth.device.action.ACL_CONNECTED", "android.bluetooth.device.action.ACL_DISCONNECTED",
            "android.media.VOLUME_CHANGED_ACTION", "android.media.RINGER_MODE_CHANGED",
            "android.intent.action.BATTERY_CHANGED", "android.intent.action.TIME_TICK",
            "android.intent.action.CONFIGURATION_CHANGED", "android.intent.action.DATE_CHANGED",
            "com.nwd.action.ACTION_APP_IN_OUT", "com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE",
            "com.nwd.action.ACTION_ACC_ON", "com.nwd.action.ACTION_ACC_OFF",
            "com.nwd.action.ACTION_BACK_CAR", "com.nwd.action.ACTION_ILL_ON", "com.nwd.action.ACTION_ILL_OFF",
            "com.nwd.action.ACTION_MCU_STATE", "com.nwd.action.ACTION_SOURCE_CHANGED",
            "com.nwd.action.ACTION_BRAKE", "com.nwd.action.ACTION_CAN_DATA",
            "com.nwd.android.action.ACC_ON", "com.nwd.android.action.ACC_OFF",
            "com.syu.ms.acc", "com.syu.ms.acc_on", "com.syu.ms.acc_off", "com.syu.ms.reverse",
            "com.syu.ms.back", "com.syu.ms.canbus", "com.syu.ms.toolkit", "com.syu.ms.sleep", "com.syu.ms.wakeup",
            "com.microntek.irkeyDown", "com.microntek.bootcheck", "com.microntek.canbusdisplay",
            "com.microntek.acc", "com.microntek.acc_on", "com.microntek.acc_off", "com.microntek.reverse",
            "com.microntek.android.action.ACC", "com.microntek.musicclientreceiver",
            "com.xyauto.action.ACC_ON", "com.xyauto.action.ACC_OFF", "com.xyauto.action.BACK_CAR",
            "com.xyauto.action.REVERSE", "com.xyauto.action.ILL", "com.xyauto.action.CAN",
            "com.xyauto.action.MCU", "com.xyauto.action.SLEEP", "com.xyauto.action.WAKEUP",
            "com.xyauto.action.BRAKE", "com.xyauto.action.KEY", "com.xyauto.intent.action.ACC",
            "com.xy.action.ACC_ON", "com.xy.action.ACC_OFF", "com.xy.action.BACK_CAR",
            "com.carsetting.action.ACC", "com.carsetting.action.BACKCAR",
            "com.autochips.action.ACC", "com.autochips.backcar",
            "android.intent.action.MEDIA_BUTTON", "com.android.music.playstatechanged",
    };

    private TextView progress, result, capBtn;
    private final Handler ui = new Handler();
    private final StringBuilder bcLog = new StringBuilder();
    private String report = "";
    private BroadcastReceiver receiver;
    private int bcCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(14));
        root.setBackground(Ui.background(this));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "Diagnóstico del radio", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(margin(button("Generar informe", 0x334F7BFF, this::generate)));
        capBtn = button("Capturar broadcasts 60 s", 0x334F7BFF, this::capture);
        bar.addView(margin(capBtn));
        bar.addView(margin(button("Volver", 0xFF2ECC71, this::finish)));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        progress = Ui.text(this, "Toca «Generar informe». Se guarda como " + FILE, 15, 0xCCFFFFFF);
        progress.setGravity(Gravity.START);
        progress.setPadding(0, dp(8), 0, dp(8));
        root.addView(progress);

        result = Ui.text(this, "", 13, Color.WHITE);
        result.setSingleLine(false);
        result.setGravity(Gravity.START);
        result.setPadding(dp(14), dp(10), dp(14), dp(10));
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.card(this));
        sv.addView(result);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCapture();
    }

    // ---------- Report ----------

    private void generate() {
        progress("Generando informe…");
        new Thread(() -> {
            try {
                String r = buildReport();
                report = r;
                String paths = save(r);
                ui.post(() -> {
                    progress.setText("Informe listo");
                    result.setText(paths);
                });
            } catch (Throwable t) {
                ui.post(() -> progress.setText("Error: " + t));
            }
        }, "diag").start();
    }

    private void progress(String s) {
        ui.post(() -> progress.setText(s));
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("DIAGNÓSTICO DEL RADIO  ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');

        progress("Datos del equipo…");
        sec(sb, "Build");
        sb.append("BRAND=").append(Build.BRAND).append("\nMANUFACTURER=").append(Build.MANUFACTURER)
                .append("\nMODEL=").append(Build.MODEL).append("\nDEVICE=").append(Build.DEVICE)
                .append("\nPRODUCT=").append(Build.PRODUCT).append("\nBOARD=").append(Build.BOARD)
                .append("\nHARDWARE=").append(Build.HARDWARE).append("\nBOOTLOADER=").append(Build.BOOTLOADER)
                .append("\nDISPLAY=").append(Build.DISPLAY).append("\nFINGERPRINT=").append(Build.FINGERPRINT)
                .append("\nTAGS=").append(Build.TAGS).append("\nTYPE=").append(Build.TYPE)
                .append("\nSERIAL(radio)=").append(Build.SERIAL)
                .append("\nRELEASE=").append(Build.VERSION.RELEASE).append("\nSDK_INT=").append(Build.VERSION.SDK_INT)
                .append("\nSECURITY_PATCH=").append(Build.VERSION.SECURITY_PATCH)
                .append("\nABIS=");
        for (String a : Build.SUPPORTED_ABIS) sb.append(a).append(' ');
        sb.append('\n');

        sec(sb, "Propiedades del sistema");
        String[] props = {"ro.board.platform", "ro.hardware", "ro.product.model", "ro.product.brand",
                "ro.product.name", "ro.product.device", "ro.product.board", "ro.product.manufacturer",
                "ro.product.cpu.abi", "ro.product.cpu.abilist", "ro.product.locale",
                "ro.build.version.release", "ro.build.version.sdk", "ro.build.version.incremental",
                "ro.build.version.security_patch", "ro.build.display.id", "ro.build.description",
                "ro.build.date", "ro.sf.lcd_density", "persist.sys.timezone", "ro.serialno"};
        for (String p : props) sb.append(p).append('=').append(prop(p)).append('\n');

        sec(sb, "Memoria y almacenamiento");
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        sb.append("RAM total: ").append(mi.totalMem / 1048576).append(" MB, libre: ")
                .append(mi.availMem / 1048576).append(" MB, memoryClass=").append(am.getMemoryClass())
                .append(" MB\n");
        sb.append("Datos: ").append(stat(Environment.getDataDirectory())).append('\n');
        sb.append("Almacenamiento externo: ").append(stat(Environment.getExternalStorageDirectory())).append('\n');
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        sb.append("Pantalla: ").append(dm.widthPixels).append('x').append(dm.heightPixels)
                .append(" densidad ").append(dm.density).append(" (").append(dm.densityDpi).append(" dpi)\n");

        progress("Funciones del sistema…");
        sec(sb, "Features");
        FeatureInfo[] fi = getPackageManager().getSystemAvailableFeatures();
        List<String> feats = new ArrayList<>();
        if (fi != null) for (FeatureInfo f : fi) if (f.name != null) feats.add(f.name);
        Collections.sort(feats);
        for (String f : feats) sb.append(f).append('\n');

        progress("Listando paquetes…");
        sec(sb, "Paquetes instalados (E=habilitado, S=sistema)");
        PackageManager pm = getPackageManager();
        List<PackageInfo> pkgs = pm.getInstalledPackages(0);
        Collections.sort(pkgs, (a, b) -> a.packageName.compareTo(b.packageName));
        List<String> interesting = new ArrayList<>();
        for (PackageInfo p : pkgs) {
            ApplicationInfo ai = p.applicationInfo;
            boolean sys = ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean en = ai == null || ai.enabled;
            sb.append(en ? 'E' : '-').append(sys ? 'S' : '-').append(' ').append(p.packageName)
                    .append("  v").append(p.versionName).append('\n');
            if (matches(p.packageName)) interesting.add(p.packageName);
        }

        sec(sb, "Componentes de paquetes de interés (" + interesting.size() + ")");
        int n = 0;
        for (String pkg : interesting) {
            progress("Componentes " + (++n) + "/" + interesting.size() + ": " + pkg);
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES
                        | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS);
                sb.append("\n# ").append(pkg).append('\n');
                if (pi.activities != null) for (ActivityInfo a : pi.activities) {
                    sb.append("  activity ").append(a.name).append(flags(a.exported, a.enabled, a.permission)).append('\n');
                }
                if (pi.services != null) for (ServiceInfo s : pi.services) {
                    sb.append("  service  ").append(s.name).append(flags(s.exported, s.enabled, s.permission)).append('\n');
                }
                if (pi.receivers != null) for (ActivityInfo r : pi.receivers) {
                    sb.append("  receiver ").append(r.name).append(flags(r.exported, r.enabled, r.permission)).append('\n');
                }
            } catch (Throwable t) { // huge packages can overflow the binder
                sb.append("# ").append(pkg).append(": ").append(t).append('\n');
            }
        }

        progress("Cámaras y video…");
        sec(sb, "/dev/video*");
        File[] devs = new File("/dev").listFiles();
        List<String> vids = new ArrayList<>();
        if (devs != null) for (File f : devs) if (f.getName().startsWith("video")) {
            vids.add(f.getName() + " r=" + f.canRead() + " w=" + f.canWrite());
        }
        Collections.sort(vids);
        for (String v : vids) sb.append(v).append('\n');
        if (vids.isEmpty()) sb.append("(ninguno visible)\n");

        sec(sb, "Camera API");
        try {
            int cams = Camera.getNumberOfCameras();
            sb.append("Cámaras: ").append(cams).append('\n');
            for (int i = 0; i < cams; i++) {
                Camera.CameraInfo ci = new Camera.CameraInfo();
                Camera.getCameraInfo(i, ci);
                sb.append("  #").append(i).append(" facing=")
                        .append(ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK ? "back" : "front")
                        .append(" orientation=").append(ci.orientation).append('\n');
            }
        } catch (Throwable t) {
            sb.append("Error: ").append(t).append('\n');
        }

        progress("Sensores y audio…");
        sec(sb, "Sensores");
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {
            sb.append(s.getName()).append(" | type=").append(s.getType()).append(" | ")
                    .append(s.getVendor()).append('\n');
        }

        sec(sb, "Dispositivos de audio");
        try {
            AudioManager au = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            for (AudioDeviceInfo d : au.getDevices(AudioManager.GET_DEVICES_ALL)) {
                sb.append(d.isSink() ? "salida " : "entrada ").append("type=").append(d.getType())
                        .append(" id=").append(d.getId()).append(' ').append(d.getProductName()).append('\n');
            }
            for (int st = 0; st <= 5; st++) {
                sb.append("stream ").append(st).append(": ").append(au.getStreamVolume(st))
                        .append('/').append(au.getStreamMaxVolume(st)).append('\n');
            }
        } catch (Throwable t) {
            sb.append("Error: ").append(t).append('\n');
        }
        return sb.toString();
    }

    private static boolean matches(String pkg) {
        String p = pkg.toLowerCase(Locale.US);
        for (String k : NAME_FILTER.split("\\|")) if (p.contains(k)) return true;
        return false;
    }

    private static String flags(boolean exported, boolean enabled, String perm) {
        return (exported ? " [exported]" : "") + (enabled ? "" : " [disabled]")
                + (perm != null ? " perm=" + perm : "");
    }

    private static void sec(StringBuilder sb, String t) {
        sb.append("\n==== ").append(t).append(" ====\n");
    }

    private static String stat(File f) {
        try {
            StatFs s = new StatFs(f.getPath());
            return f.getPath() + " total " + s.getTotalBytes() / 1048576 + " MB, libre "
                    + s.getAvailableBytes() / 1048576 + " MB";
        } catch (Throwable t) {
            return f.getPath() + " (" + t + ")";
        }
    }

    private static String prop(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            return (String) c.getMethod("get", String.class, String.class).invoke(null, key, "");
        } catch (Throwable t) {
            return "(error)";
        }
    }

    // ---------- Saving ----------

    /** Writes the report to the app's external dir and to each removable volume; returns a summary. */
    private String save(String text) {
        StringBuilder out = new StringBuilder("Informe guardado en:\n");
        List<File> targets = new ArrayList<>();
        File primary = getExternalFilesDir(null);
        if (primary != null) targets.add(new File(primary, FILE));
        File[] dirs = getExternalFilesDirs(null);
        if (dirs != null) for (File d : dirs) {
            if (d == null || d.equals(primary)) continue;
            targets.add(new File(d, FILE));
            String p = d.getAbsolutePath();
            int i = p.indexOf("/Android/");
            if (i > 0) targets.add(new File(p.substring(0, i), FILE)); // volume root (e.g. USB stick)
        }
        boolean any = false;
        for (File f : targets) {
            try (FileOutputStream o = new FileOutputStream(f)) {
                o.write(text.getBytes("UTF-8"));
                out.append("  OK  ").append(f.getAbsolutePath()).append('\n');
                any = true;
            } catch (Throwable t) {
                out.append("  --  ").append(f.getAbsolutePath()).append("  (").append(t.getMessage()).append(")\n");
            }
        }
        if (!any) out.append("No se pudo escribir ningún archivo.\n");
        return out.toString();
    }

    // ---------- Broadcast capture ----------

    private void capture() {
        if (receiver != null) return;
        bcLog.setLength(0);
        bcCount = 0;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                bcCount++;
                bcLog.append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()))
                        .append(' ').append(i.getAction());
                Bundle b = i.getExtras();
                if (b != null) {
                    try {
                        for (String k : b.keySet()) bcLog.append("\n    ").append(k).append('=').append(b.get(k));
                    } catch (Throwable t) {
                        bcLog.append("\n    (extras ilegibles)");
                    }
                }
                bcLog.append('\n');
                progress("Capturando… " + bcCount + " broadcasts");
            }
        };
        int ok = 0;
        for (String a : ACTIONS) {
            try {
                registerReceiver(receiver, new IntentFilter(a));
                ok++;
            } catch (Throwable ignored) {
            }
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progress("Capturando 60 s (" + ok + " acciones). Prueba: reversa, luces, apagar/encender…");
        ui.postDelayed(this::finishCapture, 60000);
    }

    private void stopCapture() {
        ui.removeCallbacksAndMessages(null);
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Throwable ignored) {
            }
            receiver = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void finishCapture() {
        if (receiver == null) return;
        stopCapture();
        final String log = bcLog.length() == 0 ? "(no llegó ningún broadcast)\n" : bcLog.toString();
        final String full = report + "\n==== Broadcasts capturados (60 s) ====\n" + log;
        progress("Captura terminada: " + bcCount + " broadcasts. Guardando…");
        new Thread(() -> {
            String paths = save(full);
            report = full;
            ui.post(() -> {
                progress.setText("Captura guardada (" + bcCount + " broadcasts)");
                result.setText(paths + "\n" + log);
            });
        }, "diag-save").start();
    }

    // ---------- Views ----------

    private android.view.View margin(android.view.View v) {
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
}
