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
        Track track = Runtime.SPOTIFY.track();
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
            fps = frames; frames = 0; fpsWindowStart = now;
            Runtime.SURGE.onSecond(fps);
        }
    }

    private void drawCentered(DrawContext ctx, TextRenderer font, String text, float cx, float cy,
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
        int bw = Math.min(200, w - 20), x = (w - bw) / 2, y = 6;
        ctx.fill(x, y, x + bw, y + 24, 0xA0101018);
        String title = fit(font, t.title() + "  -  " + t.artist(), bw - 10);
        ctx.drawTextWithShadow(font, title, x + 5, y + 4, 0xFFFFFFFF);
        String sub = lines == null ? "Loading lyrics..." : lines.isEmpty() ? "No synced lyrics"
                : LyricsView.clock(t.positionMs()) + " / " + LyricsView.clock(t.durationMs());
        ctx.drawTextWithShadow(font, sub, x + 5, y + 14, 0xFFA0A0B0);
        float p = t.durationMs() > 0 ? LyricsView.clamp01(t.positionMs() / (float) t.durationMs()) : 0;
        ctx.fill(x, y + 22, x + bw, y + 24, 0x40FFFFFF);
        ctx.fill(x, y + 22, x + Math.round(bw * p), y + 24, s.colour);
    }

    private static String fit(TextRenderer font, String text, int maxW) {
        if (font.getWidth(text) <= maxW) return text;
        int end = text.length();
        while (end > 1 && font.getWidth(text.substring(0, end) + "...") > maxW) end--;
        return text.substring(0, end) + "...";
    }
}
