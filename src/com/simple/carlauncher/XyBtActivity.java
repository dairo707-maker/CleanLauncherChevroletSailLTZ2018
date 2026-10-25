package com.simple.carlauncher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Bluetooth screen for the XY AUTO head unit: phone music (MediaSession) and a dialer. */
public class XyBtActivity extends Activity implements NowPlaying.Listener {

    private NowPlaying np;
    private TextView title, artist, source, device, number;
    private ImageView art, playBtn;
    private final StringBuilder digits = new StringBuilder();
    private BluetoothAdapter adapter;
    private BluetoothProfile a2dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        np = NowPlaying.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackground(Ui.background(this));
        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(12);
        root.addView(body, blp);
        body.addView(buildMusic(), new LinearLayout.LayoutParams(0, -1, 1.2f));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -1, 1f);
        dlp.leftMargin = dp(12);
        body.addView(buildDialer(), dlp);
        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView t = Ui.text(this, "Bluetooth", 26, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        t.setPadding(dp(16), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        device = Ui.text(this, "Sin conexión", 15, Color.WHITE);
        device.setPadding(dp(18), dp(9), dp(18), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x26FFFFFF);
        bg.setCornerRadius(dp(20));
        device.setBackground(bg);
        device.setMaxWidth(dp(260));
        bar.addView(device);
        return bar;
    }

    private View buildMusic() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(16), dp(20), dp(20));
        card.setBackground(Ui.card(this));

        TextView head = Ui.text(this, "Música del teléfono", 20, Color.WHITE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head);
        source = Ui.text(this, "", 13, 0x99FFFFFF);
        card.addView(source);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ph = new GradientDrawable(GradientDrawable.Orientation.TL_BR, Ui.pick(this,
                new int[]{0xFF2EB8FF, 0xFF8A4DFF}, new int[]{0xFF2E6BFF, 0xFF7B3FE4}));
        ph.setCornerRadius(dp(18));
        art.setBackground(ph);
        art.setClipToOutline(true);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(18));
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(130), dp(130));
        alp.topMargin = dp(10);
        card.addView(art, alp);

        title = Ui.text(this, "", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(12), 0, 0);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        artist = Ui.text(this, "", 16, 0xB3FFFFFF);
        card.addView(artist, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(12), 0, 0);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 56, false, () -> np.skip(false)),
                new LinearLayout.LayoutParams(dp(56), dp(56)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 68, true, np::playPause);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(68), dp(68));
        plp.setMargins(dp(20), 0, dp(20), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 56, false, () -> np.skip(true)),
                new LinearLayout.LayoutParams(dp(56), dp(56)));
        card.addView(controls);
        return card;
    }

    private View buildDialer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        card.setBackground(Ui.card(this));

        LinearLayout display = new LinearLayout(this);
        display.setGravity(Gravity.CENTER_VERTICAL);
        number = Ui.text(this, "", 30, Color.WHITE);
        number.setHint("Número");
        number.setHintTextColor(0x66FFFFFF);
        number.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        number.setPadding(dp(6), 0, 0, 0);
        display.addView(number, new LinearLayout.LayoutParams(0, -1, 1f));
        ImageView back = Ui.iconButton(this, R.drawable.ic_backspace, 44, false, () -> {
            if (digits.length() > 0) digits.deleteCharAt(digits.length() - 1);
            showNumber();
        });
        back.setOnLongClickListener(v -> {
            digits.setLength(0);
            showNumber();
            return true;
        });
        display.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        card.addView(display, new LinearLayout.LayoutParams(-1, dp(52)));

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 3; c++) {
                String k = keys[r * 3 + c];
                TextView key = Ui.text(this, k, 26, Color.WHITE);
                key.setTypeface(Typeface.DEFAULT_BOLD);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0x1FFFFFFF);
                bg.setCornerRadius(dp(14));
                key.setBackground(bg);
                Ui.pressable(key, 0.94f);
                key.setOnClickListener(v -> {
                    if (digits.length() < 20) digits.append(k);
                    showNumber();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(key, lp);
            }
            card.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        TextView call = action("Llamar", R.drawable.ic_call, new int[]{0xFF22C55E, 0xFF15803D});
        call.setOnClickListener(v -> XyAuto.dial(this, digits.toString()));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -1, 1.5f);
        clp.setMargins(dp(4), dp(4), dp(4), 0);
        bottom.addView(call, clp);
        TextView contacts = action("Contactos", R.drawable.ic_contacts,
                new int[]{0x33FFFFFF, 0x22FFFFFF});
        contacts.setOnClickListener(v -> Ui.launch(this, XyAuto.BLUETOOTH));
        LinearLayout.LayoutParams ctp = new LinearLayout.LayoutParams(0, -1, 1f);
        ctp.setMargins(dp(4), dp(4), dp(4), 0);
        bottom.addView(contacts, ctp);
        card.addView(bottom, new LinearLayout.LayoutParams(-1, dp(58)));
        return card;
    }

    private TextView action(String label, int icon, int[] colors) {
        TextView t = Ui.text(this, label, 17, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        t.setCompoundDrawablePadding(dp(8));
        t.setPadding(dp(10), 0, dp(10), 0);
        for (android.graphics.drawable.Drawable d : t.getCompoundDrawables()) {
            if (d != null) d.setTint(Color.WHITE);
        }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        bg.setCornerRadius(dp(14));
        t.setBackground(bg);
        Ui.pressable(t, 0.95f);
        return t;
    }

    private void showNumber() {
        number.setText(digits);
    }

    // ---------- Now playing ----------

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Track t = np.track(NowPlaying.Source.BT);
        NowPlaying.Source s = NowPlaying.Source.BT;
        if (t.title.isEmpty() && t.artist.isEmpty()) {
            NowPlaying.Source cur = np.current();
            if (cur != null) {
                s = cur;
                t = np.track(cur);
            }
        }
        title.setText(t.title.isEmpty() ? "Sin reproducción" : t.title);
        artist.setText(t.artist.isEmpty() ? "Reproduce música en tu teléfono" : t.artist);
        source.setText(t.updated > 0 ? np.label(s) : "");
        if (t.art != null) art.setImageBitmap(t.art);
        else art.setImageResource(R.drawable.ic_music);
        art.setColorFilter(t.art != null ? 0 : 0xCCFFFFFF);
        art.setScaleType(t.art != null ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
        playBtn.setImageResource(t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    // ---------- Connected device ----------

    private void findDevice() {
        try {
            adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return;
            adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    a2dp = proxy;
                    List<BluetoothDevice> l = proxy.getConnectedDevices();
                    if (!l.isEmpty() && l.get(0).getName() != null) device.setText(l.get(0).getName());
                }

                @Override
                public void onServiceDisconnected(int profile) { }
            }, BluetoothProfile.A2DP);
        } catch (Exception ignored) { }
    }

    @Override
    protected void onStart() {
        super.onStart();
        np.addListener(this);
        onNowPlayingChanged();
        findDevice();
    }

    @Override
    protected void onStop() {
        super.onStop();
        np.removeListener(this);
        try {
            if (adapter != null && a2dp != null) adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp);
        } catch (Exception ignored) { }
        a2dp = null;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
