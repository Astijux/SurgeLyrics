package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

/** Clean centered lyrics + a slim now-playing bar + optional Surge overlay. Everything is drawn in one pass. */
public final class LyricsHud implements HudElement {
    private static final int[][] GLOW = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    static volatile int lastFps;
    private long fpsWindowStart = System.nanoTime();
    private int frames, fps;

    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        countFrames();
        if (mc.options.hudHidden) return;
        Settings s = Runtime.SETTINGS;
        TextRenderer font = mc.textRenderer;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();

        if (s.surgeOverlay) {
            String t = "Surge " + Runtime.SURGE.profile().label + "  " + fps + " fps"
                    + (Runtime.SURGE.scale() < 1.0 ? "  (eased " + Math.round(Runtime.SURGE.scale() * 100) + "%)" : "");
            ctx.fill(2, 2, 6 + font.getWidth(t), 14, 0x80000000);
            ctx.drawTextWithShadow(font, t, 4, 4, 0xFFFFFFFF);
        }

        if (!s.lyricsEnabled) return;
        Glass.alpha = 1f;
        Track track = Runtime.NOW.track();
        if (track == null) return;

        List<Lrc.Line> lines = Runtime.LYRICS.cached(track);
        if (lines == null) Runtime.LYRICS.request(track);          // async, de-duplicated, cached

        if (s.showBar) drawBar(ctx, font, w, track, lines, s);
        if (lines == null || lines.isEmpty()) return;

        long pos = track.positionMs() + s.syncOffsetMs;
        LyricsView.View v = LyricsView.compute(lines, pos, s.wordsPerLine, s.staySeconds);
        if (v.alpha() <= 0.01f || v.text().isEmpty()) return;

        float scale = 1.6f * s.sizePercent / 100f;
        float bob = s.intensity == 0 ? 0 : (float) Math.sin(System.nanoTime() / 1.2e9) * (1.2f * s.intensity);
        float cx = w / 2f, cy = h * 0.58f + bob;

        drawCentered(ctx, font, v.text(), cx, cy, scale, v.alpha(), s, true);
        if (!v.next().isEmpty())
            drawCentered(ctx, font, fit(font, v.next(), (int) (w * 0.8f / (scale * 0.6f))), cx,
                    cy + 14 * scale + 6, scale * 0.6f, v.alpha() * 0.45f, s, false);
    }

    private void countFrames() {
        frames++;
        long now = System.nanoTime();
        if (now - fpsWindowStart >= 1_000_000_000L) {
            fps = frames; lastFps = fps; frames = 0; fpsWindowStart = now;
            Runtime.SURGE.onSecond(fps);
        }
    }

    static void drawCentered(DrawContext ctx, TextRenderer font, String text, float cx, float cy,
                              float scale, float alpha, Settings s, boolean glowAllowed) {
        int tw = font.getWidth(text);
        var m = ctx.getMatrices();
        m.pushMatrix();                                   // VERIFY: Matrix3x2fStack API (1.21.6+)
        m.translate(cx, cy);
        m.scale(scale, scale);
        float x = -tw / 2f;
        if (s.glow && glowAllowed) {
            int g = LyricsView.withAlpha(s.colour, alpha * 0.22f);
            for (int[] d : GLOW)
                ctx.drawText(font, text, Math.round(x + d[0]), d[1], g, false);
        }
        if (s.gradient) {
            // colour per character, left to right: cheap, no per-frame allocations beyond substrings
            float cursor = x;
            int n = text.length();
            for (int i = 0; i < n; i++) {
                String ch = text.substring(i, i + 1);
                int col = LyricsView.withAlpha(LyricsView.mix(s.colour, s.colour2, n <= 1 ? 0 : i / (float) (n - 1)), alpha);
                ctx.drawText(font, ch, Math.round(cursor), 0, col, true);
                cursor += font.getWidth(ch);
            }
        } else {
            ctx.drawText(font, text, Math.round(x), 0, LyricsView.withAlpha(s.colour, alpha), true);
        }
        m.popMatrix();
    }

    private void drawBar(DrawContext ctx, TextRenderer font, int w, Track t, List<Lrc.Line> lines, Settings s) {
        int bw = Math.min(210, w - 20), x = (w - bw) / 2, y = 6, bh = 28;
        Glass.shadow(ctx, x, y, bw, bh, 10);
        Glass.panel(ctx, x, y, bw, bh, 10, 0x66141A2A, 0.9f, 0.16f);
        Glass.text(ctx, font, fit(font, t.title() + "  -  " + t.artist(), bw - 18), x + 9, y + 5, 0xFFF2F5FA);
        String sub = lines == null ? "Loading lyrics..." : lines.isEmpty() ? "No synced lyrics"
                : LyricsView.clock(t.positionMs()) + " / " + LyricsView.clock(t.durationMs());
        Glass.text(ctx, font, sub, x + 9, y + 15, 0xFFA4ADBF);
        float p = t.durationMs() > 0 ? LyricsView.clamp01(t.positionMs() / (float) t.durationMs()) : 0;
        int tx = x + 9, tw = bw - 18;
        Glass.rrect(ctx, tx, y + bh - 6, tw, 2, 1, 0x40FFFFFF);
        Glass.rrect(ctx, tx, y + bh - 6, Math.max(2, Math.round(tw * p)), 2, 1, s.colour);
    }

    private static String fit(TextRenderer font, String text, int maxW) {
        if (font.getWidth(text) <= maxW) return text;
        int end = text.length();
        while (end > 1 && font.getWidth(text.substring(0, end) + "...") > maxW) end--;
        return text.substring(0, end) + "...";
    }
}
