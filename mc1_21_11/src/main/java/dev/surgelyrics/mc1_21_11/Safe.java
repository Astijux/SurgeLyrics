package dev.surgelyrics.mc1_21_11;

import net.minecraft.client.gui.DrawContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crash shield. Every feature that runs inside the game's render or event code goes through here: if it throws,
 * that one feature switches itself off for the rest of the session, the reason is logged, and the game carries on.
 */
public final class Safe {
    private Safe() {}

    private static final Logger LOG = LoggerFactory.getLogger("surgelyrics");
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();

    public static boolean isOff(String feature) { return DISABLED.contains(feature); }

    /** Names of features that were switched off after an error, for the settings screen. */
    public static String summary() { return DISABLED.isEmpty() ? "" : String.join(", ", new TreeSet<>(DISABLED)); }

    /** Run non-drawing code (mixin bodies, event handlers). */
    public static void run(String feature, Runnable r) {
        if (DISABLED.contains(feature)) return;
        try { r.run(); } catch (Throwable t) { fail(feature, t); }
    }

    /** Run drawing code. On failure the matrix stack is unwound so later HUD elements still draw correctly. */
    public static void gui(String feature, DrawContext ctx, Runnable r) {
        if (DISABLED.contains(feature)) return;
        try { r.run(); } catch (Throwable t) { fail(feature, t); unwind(ctx); }
    }

    public static void unwind(DrawContext ctx) {
        try {
            var m = ctx.getMatrices();
            for (int i = 0; i < 64; i++) m.popMatrix();           // stops itself (throws) when the stack is back at the bottom
        } catch (Throwable ignored) { /* already at the bottom */ }
    }

    /** Writes a plain message to the log file and the game log (for things that are not exceptions). */
    public static void note(String message) {
        LOG.warn("[surgelyrics] {}", message);
        try {
            Files.writeString(Runtime.SETTINGS_FILE.resolveSibling("surgelyrics-errors.log"),
                    "\n=== " + Instant.now() + " ===\n" + message + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) { /* logging must never throw */ }
    }

    public static void fail(String feature, Throwable t) {
        if (!DISABLED.add(feature)) return;                       // log each feature once
        LOG.error("[surgelyrics] '{}' crashed and was switched off for this session", feature, t);
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String[] lines = sw.toString().split("\\R");
            StringBuilder sb = new StringBuilder("\n=== ").append(Instant.now()).append("  feature: ").append(feature).append(" ===\n");
            for (int i = 0; i < Math.min(lines.length, 40); i++) sb.append(lines[i]).append('\n');
            Files.writeString(Runtime.SETTINGS_FILE.resolveSibling("surgelyrics-errors.log"), sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) { /* logging must never throw */ }
    }
}
