package com.simple.carlauncher;

import android.content.Context;

import java.util.List;

/**
 * Turns the greeting clip list into one sentence for text-to-speech (shared builds), so the
 * same reminders are spoken with the user's own name.
 */
final class Speech {

    private Speech() { }

    static String text(Context c, List<String> clips) {
        StringBuilder sb = new StringBuilder();
        for (String clip : clips) {
            String part = phrase(c, clip);
            if (part != null) sb.append(part).append(' ');
        }
        return sb.toString().trim();
    }

    /** True for clips whose sentence does not depend on live data (can be pre-rendered). */
    static boolean isFixed(String clip) {
        return !(clip.startsWith("v_cond_") || clip.startsWith("v_temp_") || clip.startsWith("v_maint:"));
    }

    /** Sentence for one clip name, or null when unknown. */
    static String phrase(Context c, String clip) {
        String name = Profile.name(c);
        String who = name.isEmpty() ? "" : ", " + name;
        switch (clip) {
            case "v_greet_morning": return "¡Buenos días" + who + "! Bienvenido.";
            case "v_greet_afternoon": return "¡Buenas tardes" + who + "! Bienvenido.";
            case "v_greet_night": return "¡Buenas noches" + who + "! Bienvenido.";
            case "v_greet_monday": return "¡Hola" + who + "! Arrancó la semana: hoy es lunes.";
            case "v_maintenance": return "Revisa el aceite, el agua del radiador y el líquido de frenos.";
            case "v_seatbelt": return "Ponte el cinturón de seguridad.";
            case "v_picoplaca": return "Hoy tienes pico y placa " + PicoPlaca.bandsSpoken(c, java.util.Calendar.getInstance()) + ".";
            case "v_careful": return "Maneja con cuidado. ¡Buen viaje!";
            case "v_oil_soon": return "Se acerca el cambio de aceite: quedan menos de quinientos kilómetros.";
            case "v_oil_due": return "Ya toca el cambio de aceite.";
            default: break;
        }
        if (clip.startsWith("v_maint:")) {
            String[] names = clip.substring(8).split(",");
            StringBuilder sb = new StringBuilder("Tienes mantenimiento vencido: ");
            for (int i = 0; i < names.length && i < 3; i++) sb.append(i > 0 ? ", " : "").append(names[i].toLowerCase());
            return sb.append(names.length > 3 ? " y otros." : ".").toString();
        }
        if (clip.startsWith("v_cond_")) return condition(clip.substring(7));
        if (clip.startsWith("v_temp_")) {
            String t = clip.substring(7).replace("m", "menos ");
            return "y hace " + t + " grados.";
        }
        return null;
    }

    private static String condition(String c) {
        switch (c) {
            case "clear": return "Afuera está despejado,";
            case "mostly": return "Afuera está casi despejado,";
            case "partly": return "Afuera está parcialmente nublado,";
            case "cloudy": return "Afuera está nublado,";
            case "fog": return "Afuera hay neblina,";
            case "drizzle": return "Afuera está lloviznando,";
            case "rain": return "Afuera está lloviendo,";
            case "snow": return "Afuera está nevando,";
            case "showers": return "Afuera hay aguaceros,";
            case "storm": return "Afuera hay tormenta,";
            default: return "";
        }
    }
}
