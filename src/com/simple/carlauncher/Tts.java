package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One shared Spanish text-to-speech engine for greetings, driver alerts and messages read aloud.
 * Phrases asked for before the engine is ready are queued; spoken as navigation guidance so the
 * head unit mixes them over the music.
 */
final class Tts {

    private Tts() { }

    private static TextToSpeech tts;
    private static boolean ready;
    private static final List<String> pending = new ArrayList<>();

    // Pre-rendered phrases: voice/<key>.wav, valid while the stored hash matches the text.
    private static final List<String[]> renderQueue = new ArrayList<>(); // {key, text}
    private static String rendering; // key being synthesized, or null
    private static Context appCtx;

    /** Starts the engine ahead of time so the first phrase is not delayed. */
    static synchronized void warm(Context c) {
        if (tts != null) return;
        say(c, " ");
        pending.clear();
    }

    /** Speaks after whatever is already being said. */
    static synchronized void say(Context c, String text) {
        if (text == null) return;
        if (ready) {
            if (text.trim().isEmpty()) return;
            tts.speak(text, TextToSpeech.QUEUE_ADD, new Bundle(), "cl" + System.nanoTime());
            return;
        }
        pending.add(text);
        if (tts != null) return;
        tts = new TextToSpeech(c.getApplicationContext(), status -> {
            synchronized (Tts.class) {
                if (status != TextToSpeech.SUCCESS) {
                    tts = null;
                    pending.clear();
                    renderQueue.clear();
                    return;
                }
                Locale es = new Locale("es", "US");
                if (tts.isLanguageAvailable(es) < 0) es = new Locale("es");
                tts.setLanguage(es);
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                ready = true;
                for (String t : pending) tts.speak(t, TextToSpeech.QUEUE_ADD, new Bundle(), "cl" + System.nanoTime());
                pending.clear();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { renderDone(id, true); }
                    @Override public void onError(String id) { renderDone(id, false); }
                });
                renderNext();
            }
        });
    }

    // ---------- Pre-rendered audio ----------

    private static File file(Context c, String key) {
        File dir = new File(c.getApplicationContext().getFilesDir(), "voice");
        if (!dir.isDirectory()) dir.mkdirs();
        return new File(dir, key + ".wav");
    }

    private static SharedPreferences cache(Context c) {
        return c.getApplicationContext().getSharedPreferences("voice_cache", Context.MODE_PRIVATE);
    }

    private static String hash(String text) {
        return Integer.toHexString(text.hashCode()) + "-" + text.length();
    }

    /** The rendered wav for this exact text, or null when missing or stale. */
    static File cached(Context c, String key, String text) {
        File f = file(c, key);
        if (!f.isFile() || f.length() < 100) return null;
        return hash(text).equals(cache(c).getString(key, "")) ? f : null;
    }

    /** Renders the phrase to voice/<key>.wav in the background (no-op when already current). */
    static synchronized void prerender(Context c, String key, String text) {
        if (text == null || text.trim().isEmpty() || cached(c, key, text) != null) return;
        appCtx = c.getApplicationContext();
        for (String[] q : renderQueue) if (q[0].equals(key)) { q[1] = text; return; }
        if (key.equals(rendering)) return;
        renderQueue.add(new String[]{key, text});
        if (ready) renderNext();
        else warm(c); // the engine start calls renderNext()
    }

    private static void renderNext() {
        if (!ready || rendering != null || renderQueue.isEmpty()) return;
        String[] job = renderQueue.remove(0);
        rendering = job[0];
        File tmp = new File(file(appCtx, job[0]).getPath() + ".tmp");
        Bundle params = new Bundle();
        int r = tts.synthesizeToFile(job[1], params, tmp, "pr_" + job[0] + "|" + hash(job[1]));
        if (r != TextToSpeech.SUCCESS) {
            rendering = null;
            renderNext();
        }
    }

    private static synchronized void renderDone(String id, boolean ok) {
        if (id == null || !id.startsWith("pr_")) return; // an ordinary spoken phrase
        String[] p = id.substring(3).split("\\|");
        File tmp = new File(file(appCtx, p[0]).getPath() + ".tmp");
        File f = file(appCtx, p[0]);
        if (ok && tmp.length() > 100) {
            f.delete();
            if (tmp.renameTo(f)) cache(appCtx).edit().putString(p[0], p[1]).apply();
        } else {
            tmp.delete();
        }
        rendering = null;
        renderNext();
    }
}
