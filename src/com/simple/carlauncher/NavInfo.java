package com.simple.carlauncher;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Navigation state taken from Waze's ongoing notification (a launcher cannot embed Waze).
 * Parsing is best effort: the raw extras are logged under tag "NavInfo" to refine it from logcat.
 */
final class NavInfo {

    static final String WAZE = "com.waze";

    interface Listener {
        void onNavChanged();
    }

    private static final Pattern DIST = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?\\s?(?:km|m|mi|ft|yd))\\b", Pattern.CASE_INSENSITIVE);

    private static final List<Listener> listeners = new ArrayList<>();
    static String instruction = "", distance = "", eta = "";
    static Bitmap icon;
    static long updated;
    static boolean active;

    private NavInfo() { }

    static void addListener(Listener l) { listeners.add(l); }

    static void removeListener(Listener l) { listeners.remove(l); }

    /** Reads a notification posted by Waze; ignores the ones that are not the ongoing guidance. */
    static void onPosted(Context c, StatusBarNotification sbn) {
        try {
            Notification n = sbn.getNotification();
            Bundle e = n.extras;
            if (e == null) return;
            CharSequence title = e.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = e.getCharSequence(Notification.EXTRA_TEXT);
            CharSequence sub = e.getCharSequence(Notification.EXTRA_SUB_TEXT);
            CharSequence big = e.getCharSequence(Notification.EXTRA_BIG_TEXT);
            Log.d("NavInfo", "flags=" + n.flags + " title=" + title + " | text=" + text + " | sub=" + sub
                    + " | big=" + big + " | ticker=" + n.tickerText + " | keys=" + e.keySet());
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) == 0) return;
            String t = title == null ? "" : title.toString().trim();
            String x = text == null ? "" : text.toString().trim();
            if (t.isEmpty() && x.isEmpty()) return;

            // Usually "300 m · Turn right" in the title and the street/ETA in the text.
            String dist = "", instr = t;
            Matcher m = DIST.matcher(t);
            if (m.find()) {
                dist = m.group(1);
                instr = t.replace(m.group(), "").replaceAll("^[\\s·•\\-–:,]+|[\\s·•\\-–:,]+$", "");
            }
            if (instr.isEmpty()) instr = x;
            String etaText = sub == null ? "" : sub.toString().trim();
            if (etaText.isEmpty() && !x.equals(instr)) etaText = x;
            if (dist.isEmpty()) {
                Matcher m2 = DIST.matcher(x);
                if (m2.find()) dist = m2.group(1);
            }
            Bitmap ic = largeIcon(c, n);
            boolean changed = !instr.equals(instruction) || !dist.equals(distance)
                    || !etaText.equals(eta) || !active;
            instruction = instr;
            distance = dist;
            eta = etaText;
            if (ic != null) icon = ic;
            updated = SystemClock.elapsedRealtime();
            active = true;
            if (changed) fire();
        } catch (RuntimeException ex) {
            Log.w("NavInfo", "Could not parse Waze notification", ex);
        }
    }

    static void clear() {
        if (!active) return;
        active = false;
        instruction = distance = eta = "";
        icon = null;
        fire();
    }

    private static Bitmap largeIcon(Context c, Notification n) {
        try {
            Icon i = n.getLargeIcon();
            if (i == null) return null;
            Drawable d = i.loadDrawable(c);
            if (d == null) return null;
            if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();
            Bitmap b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 64, 64);
            d.draw(new Canvas(b));
            return b;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void fire() {
        for (Listener l : new ArrayList<>(listeners)) l.onNavChanged();
    }
}
