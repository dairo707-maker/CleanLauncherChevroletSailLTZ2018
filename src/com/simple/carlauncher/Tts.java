package com.simple.carlauncher;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

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
            }
        });
    }
}
