package com.simple.carlauncher;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads incoming WhatsApp and driver-app notifications aloud (pref "read_messages", off by default). */
final class MessageReader {

    private MessageReader() { }

    private static final long DUP_MS = 10_000;
    private static final int MAX_CHARS = 160;
    private static final Pattern SUMMARY = Pattern.compile(
            "(?i)^\\d+\\s+(mensajes?|messages?|chats?|conversaciones).*|.*mensajes? nuevos?.*|.*new messages?.*"
                    + "|.*revisando.*|.*checking for new.*");
    private static final Map<String, Long> recent = new HashMap<>();

    static void onPosted(Context c, StatusBarNotification sbn) {
        try {
            if (!Profile.prefs(c).getBoolean("read_messages", false)) return;
            String pkg = sbn.getPackageName();
            String app = appName(pkg);
            if (app == null) return;
            Notification n = sbn.getNotification();
            if ((n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_GROUP_SUMMARY)) != 0) return;
            Bundle e = n.extras;
            if (e == null) return;
            String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
            if (title.isEmpty() && text.isEmpty()) return;
            boolean chat = app.equals("WhatsApp");
            if (chat && (title.equals("WhatsApp") || SUMMARY.matcher(text).matches()
                    || SUMMARY.matcher(title).matches())) return;
            if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
            if (isDuplicate(pkg + "|" + title + "|" + text)) return;
            Tts.say(c, chat ? "Mensaje de " + title + ": " + text
                    : app + ": " + title + (text.isEmpty() ? "" : ". " + text));
        } catch (RuntimeException ex) {
            android.util.Log.w("MessageReader", "Ignoring notification", ex);
        }
    }

    /** Display name for the packages we read, null for the rest. */
    private static String appName(String pkg) {
        if (pkg == null) return null;
        if (pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b")) return "WhatsApp";
        if (pkg.equals("com.ubercab.driver")) return "Uber";
        if (pkg.equals("sinet.startup.indriver")) return "inDriver";
        if (pkg.contains("didi") && pkg.contains("driver")) return "DiDi";
        return null;
    }

    private static boolean isDuplicate(String key) {
        long now = SystemClock.elapsedRealtime();
        for (Iterator<Long> it = recent.values().iterator(); it.hasNext(); ) {
            if (now - it.next() > DUP_MS) it.remove();
        }
        if (recent.containsKey(key)) return true;
        recent.put(key, now);
        return false;
    }

    private static String str(CharSequence s) {
        return s == null ? "" : s.toString().trim();
    }
}
