package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

/** HUD: now-playing glass bar, Surge overlay, widgets and the lyric block (modes, fonts, 3D, colour, transitions, audio reaction). */
public final class LyricsHud implements HudElement {
    static volatile int lastFps, lastLow;
    static boolean previewMode;                 // settings preview ignores the Surge lighten rule
    private final HudWidgets widgets = new HudWidgets();
    private int fps, low;

    // line transition state
    private int curKey = -2;
    private String curText = "", prevText = "";
    private long startNanos, lastShownNanos;
    private float lastHold = 1f, prevHold = 1f;
    private long lastFrameNanos = System.nanoTime();

    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        Glass.fast = true;                       // HUD uses the cheap panel path: a few big rectangles, not one per row
        try { draw(ctx); } finally { Glass.fast = false; }
    }

    private void draw(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        countFrames();
        long nowN = System.nanoTime();
        float dt = Math.min(0.1f, (nowN - lastFrameNanos) / 1e9f);
        lastFrameNanos = nowN;
        if (mc.options.hudHidden) return;
        Settings s = Runtime.SETTINGS;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        Glass.alpha = 1f;
        if (s.reactive) Runtime.REACT.update(Runtime.AUDIO.raw(), dt);

        if (s.surgeOverlay) {
            String t1 = "Surge " + Runtime.SURGE.profile().label + "   " + fps + " fps   low " + low
                    + (Runtime.SURGE.scale() < 1.0 ? "   eased " + Math.round(Runtime.SURGE.scale() * 100) + "%" : "");
            String t2 = Runtime.SURGE.active() ? Runtime.SURGE.statsLine() : "culling off";
            int ow = Math.max(tr.getWidth(t1), tr.getWidth(t2)) + 12;
            Glass.shadow(ctx, 2, 2, ow, 25, 7);
            Glass.panel(ctx, 2, 2, ow, 25, 7, 0x66141A2A, 0.7f, 0.12f);
            Glass.text(ctx, tr, t1, 8, 6, 0xFFF2F5FA);
            Glass.text(ctx, tr, t2, 8, 16, 0xFFA4ADBF);
        }

        widgets.render(ctx, mc, tr, sw, sh);

        if (!s.lyricsEnabled) return;
        Track track = Runtime.NOW.track();
        if (track == null) { curText = ""; return; }
        List<Lrc.Line> lines = Runtime.LYRICS.cached(track);
        if (lines == null) Runtime.LYRICS.request(track);          // async, de-duplicated, cached

        if (s.showBar) drawBar(ctx, tr, sw, sh, track, lines, s);
        if (lines == null || lines.isEmpty()) { curText = ""; return; }

        long pos = track.positionMs() + s.syncOffsetMs;
        LyricsView.View v = LyricsView.compute(lines, pos, s.displayMode, s.wordsPerLine, s.staySeconds);
        if (v.text().isEmpty() || v.hold() <= 0.01f) { curText = ""; return; }

        // -- transition bookkeeping: a new line/chunk starts a transition, the old text animates out
        if (v.key() != curKey) {
            boolean keepOld = !curText.isEmpty() && curKey >= 0 && lastHold > 0.3f && nowN - lastShownNanos < 1_500_000_000L;
            prevText = keepOld ? curText : "";
            prevHold = lastHold;
            startNanos = nowN;
            curKey = v.key();
        }
        curText = v.text();
        lastHold = v.hold();
        lastShownNanos = nowN;
        float p = LyricsView.clamp01((nowN - startNanos) / (s.transitionMs * 1_000_000f));

        float time = time();
        float base = (s.displayMode == 4 ? 1.3f : 1.6f) * s.sizePercent / 100f;
        float pulse = pulse(s);
        float maxW = sw * 0.92f;
        float[] m = measure(tr, v, s, base * (1f + (s.reactLyrics ? 0.10f * pulse : 0f)), maxW);   // {scale, width, height}
        int[] pl = Layout.place(s.anchor, s.offX, s.offY, sw, sh, Math.round(m[1]), Math.round(m[2]));
        float bob = s.intensity == 0 ? 0 : (float) Math.sin(System.nanoTime() / 1.2e9) * (1.2f * s.intensity);
        drawWithTransition(ctx, tr, v, s, prevText, prevHold, p, pl[0], pl[1] + bob, m[0], time, maxW, s.reactLyrics ? pulse : 0f);
    }

    /** 0..1 reaction strength right now (0 when audio-reactive is off). */
    static float pulse(Settings s) {
        if (!s.reactive) return 0f;
        float k = s.reactStrength / 100f;
        return LyricsView.clamp01(k * (0.9f * Runtime.REACT.beat() + 0.5f * Runtime.REACT.level()));
    }

    // ---------------------------------------------------------------- shared with the settings preview

    static float time() { return (System.currentTimeMillis() % 1_000_000L) / 1000f; }

    private static float quant(float t) { return (float) Math.floor(t * 20f) / 20f; }

    /** @return {scale after fitting to maxW, block width, block height} in GUI pixels */
    static float[] measure(TextRenderer tr, LyricsView.View v, Settings s, float scale, float maxW) {
        String fid = FontStyle.of(s.font).fontId();
        float tw = Math.max(1, tr.getWidth(FancyText.build(v.text(), fid, null)));
        if (tw * scale > maxW) scale = maxW / tw;
        float h = 9 * scale;
        if (s.displayMode == 4) h += 2 * (9 * scale * 0.6f + 4);
        return new float[]{scale, tw * scale, h};
    }

    /** Old line animates out while the new one animates in, using the chosen transition style. */
    static void drawWithTransition(DrawContext ctx, TextRenderer tr, LyricsView.View v, Settings s, String prevText, float prevHold,
                                   float p, float cx, float cy, float scale, float time, float maxW, float pulse) {
        LyricTransitions.Pose in = LyricTransitions.in(s.transition, p);
        if (p < 1f && prevText != null && !prevText.isEmpty()) {
            LyricTransitions.Pose out = LyricTransitions.out(s.transition, p);
            drawStyled(ctx, tr, prevText, cx, cy + out.dy(), scale * out.scale(), prevHold * out.alpha(), s, -1f, time, maxW, 1f, pulse);
        }
        drawBlock(ctx, tr, v, s, cx, cy, scale, v.hold(), time, maxW, in, pulse);
    }

    /** Current line (with the incoming pose) plus previous/next lines for the modes that show them. */
    static void drawBlock(DrawContext ctx, TextRenderer tr, LyricsView.View v, Settings s, float cx, float cy,
                          float scale, float alpha, float time, float maxW, LyricTransitions.Pose pose, float pulse) {
        float fill = s.displayMode == 3 ? v.fill() : -1f;
        drawStyled(ctx, tr, v.text(), cx, cy + pose.dy(), scale * pose.scale(), alpha * pose.alpha(), s, fill, time, maxW, pose.reveal(), pulse);
        float sc2 = scale * 0.6f, step = 9 * scale * 0.5f + 9 * sc2 * 0.5f + 4;
        int mode = s.displayMode;
        float nAlpha = alpha * LyricsView.clamp01(0.4f + 0.6f * pose.alpha());
        if (mode == 4) {
            if (!v.before().isEmpty()) drawPlain(ctx, tr, v.before(), cx, cy - step, sc2, nAlpha * 0.40f, s, maxW);
            if (!v.next().isEmpty()) drawPlain(ctx, tr, v.next(), cx, cy + step, sc2, nAlpha * 0.40f, s, maxW);
        } else if ((mode == 0 || mode == 1 || mode == 3) && !v.next().isEmpty()) {
            drawPlain(ctx, tr, v.next(), cx, cy + step, sc2, nAlpha * 0.42f, s, maxW);
        }
    }

    /** Dim secondary line: same font, single colour, no effects. */
    private static void drawPlain(DrawContext ctx, TextRenderer tr, String text, float cx, float cy, float scale,
                                  float alpha, Settings s, float maxW) {
        alpha *= s.opacity / 100f;
        int a = Math.round(255 * Math.max(0, Math.min(1, alpha)));
        if (a < 8) return;
        Text t = FancyText.build(text, FontStyle.of(s.font).fontId(), null);
        float tw = Math.max(1, tr.getWidth(t));
        if (tw * scale > maxW) scale = maxW / tw;
        var m = ctx.getMatrices();
        m.pushMatrix();                                   // VERIFY: Matrix3x2fStack API (1.21.6+)
        m.translate(cx, cy);
        m.scale(scale, scale);
        ctx.drawText(tr, t, -Math.round(tw / 2f), -4, (a << 24) | (s.colour & 0xFFFFFF), true);
        m.popMatrix();
    }

    /**
     * The main styled line. fill >= 0 turns on karaoke colouring. reveal < 1 shows only the first words,
     * left-aligned to the full line so nothing jitters. pulse (0..1) boosts the glow for audio reaction.
     */
    static void drawStyled(DrawContext ctx, TextRenderer tr, String text, float cx, float cy, float scale, float alpha,
                           Settings s, float fill, float time, float maxW, float reveal, float pulse) {
        alpha *= s.opacity / 100f;
        int a = Math.round(255 * Math.max(0, Math.min(1, alpha)));
        if (a < 8 || text.isEmpty() || reveal <= 0f) return;

        String fid = FontStyle.of(s.font).fontId();
        int n = text.codePointCount(0, text.length());
        boolean flowing = s.gradientMode >= 2;
        float t = flowing ? quant(time) : 0f;

        String shown = text;
        int shownCp = n;
        if (reveal < 1f) {                                // word-by-word reveal
            String[] words = text.split(" ");
            int k = Math.max(1, (int) Math.ceil(words.length * reveal));
            if (k < words.length) {
                shown = String.join(" ", Arrays.copyOf(words, k));
                shownCp = shown.codePointCount(0, shown.length());
            }
        }

        Text plainFull = FancyText.build(text, fid, null);
        Text plain = shown.equals(text) ? plainFull : FancyText.build(shown, fid, null);
        Text main;
        boolean perChar = s.gradientMode != 0 || fill >= 0;
        if (perChar) {
            final int fn = n, fcp = shownCp;
            final float ft = t, ff = fill;
            String variant = s.gradientMode + "," + s.colour + "," + s.colour2 + "," + s.gradientSpeed + ","
                    + (flowing ? (int) (t * 20) : 0) + "," + (fill >= 0 ? (int) (fill * n) : -1) + "," + shownCp;
            main = FancyText.buildCached(shown, fid, variant, () -> {
                int[] cols = new int[fcp];
                for (int i = 0; i < fcp; i++) {
                    int c = Colors.gradient(s.gradientMode, i, fn, ft, s.colour, s.colour2, s.gradientSpeed);
                    if (ff >= 0 && i >= ff * fn) c = LyricsView.mix(c, 0xFF6B7280, 0.72f);
                    cols[i] = c;
                }
                return cols;
            });
        } else {
            main = plain;
        }

        float twFull = Math.max(1, tr.getWidth(plainFull));
        if (twFull * scale > maxW) scale = maxW / twFull;
        int x = -Math.round(twFull / 2f), y = -4;

        var m = ctx.getMatrices();
        m.pushMatrix();                                   // VERIFY: Matrix3x2fStack API (1.21.6+)
        m.translate(cx, cy);
        m.scale(scale, scale);

        int c1 = s.colour & 0xFFFFFF;
        boolean light = !previewMode && Runtime.SURGE.lightenUi();   // game is struggling: skip eye-candy
        if (s.depthStyle == 1 && !light) {                // 3D: stacked, progressively darker copies = extruded letters
            double ang = Math.toRadians(s.lightAngle);
            float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
            for (int k = s.depthAmount; k >= 1; k--) {
                int col = Colors.darken(0xFF000000 | c1, 0.35f + 0.5f * k / s.depthAmount) & 0xFFFFFF;
                ctx.drawText(tr, plain, x + Math.round(dx * k * 0.9f), y + Math.round(dy * k * 0.9f),
                        (Math.round(a * 0.95f) << 24) | col, false);
            }
        } else if (s.depthStyle == 2) {                   // outline
            int col = Colors.darken(0xFF000000 | c1, 0.8f) & 0xFFFFFF;
            for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++)
                if (ox != 0 || oy != 0) ctx.drawText(tr, plain, x + ox, y + oy, (a << 24) | col, false);
        }
        int glow = s.glowStrength;
        if (pulse > 0.05f) glow = Math.max(glow, 35) + Math.round(pulse * 80f);       // audio reaction swells the glow
        if (glow > 0 && !light) {
            int ga = Math.round(a * 0.22f * glow / 50f);
            if (ga >= 4) for (int[] d : GLOW) ctx.drawText(tr, plain, x + d[0], y + d[1], (Math.min(255, ga) << 24) | c1, false);
        }
        ctx.drawText(tr, main, x, y, (a << 24) | (perChar ? 0xFFFFFF : c1), true);
        m.popMatrix();
    }

    private static final int[][] GLOW = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-2, 0}, {2, 0}, {0, -2}, {0, 2}};

    // ---------------------------------------------------------------- bar / fps

    private void countFrames() {
        FrameStats.Snapshot sn = Runtime.FRAMES.frame(System.nanoTime());
        if (sn == null) return;
        fps = sn.avg(); low = sn.low1(); lastFps = fps; lastLow = low;
        Runtime.SURGE.onSecond(fps, low);
    }

    private void drawBar(DrawContext ctx, TextRenderer font, int sw, int sh, Track t, List<Lrc.Line> lines, Settings s) {
        int bw = Math.min(210, sw - 20), bh = 28;
        int x = s.barBottom ? 8 : (sw - bw) / 2;
        int y = s.barBottom ? sh - bh - 8 : 6;
        boolean viz = s.reactive && s.reactBars;
        Glass.shadow(ctx, x, y, bw, bh, 10);
        Glass.panel(ctx, x, y, bw, bh, 10, 0x66141A2A, 0.9f, 0.16f);
        Glass.text(ctx, font, Glass.fit(font, t.title() + "  -  " + t.artist(), bw - 18 - (viz ? 32 : 0)), x + 9, y + 5, 0xFFF2F5FA);
        String sub = lines == null ? "Loading lyrics..." : lines.isEmpty() ? "No synced lyrics"
                : LyricsView.clock(t.positionMs()) + " / " + LyricsView.clock(t.durationMs());
        Glass.text(ctx, font, sub, x + 9, y + 15, 0xFFA4ADBF);
        float p = t.durationMs() > 0 ? LyricsView.clamp01(t.positionMs() / (float) t.durationMs()) : 0;
        int tx = x + 9, tw = bw - 18;
        Glass.rrect(ctx, tx, y + bh - 6, tw, 2, 1, 0x40FFFFFF);
        Glass.rrect(ctx, tx, y + bh - 6, Math.max(2, Math.round(tw * p)), 2, 1, s.colour);
        if (viz) {                                        // five bars that dance with the music
            float lv = Runtime.REACT.level() * (s.reactStrength / 50f), bt = Runtime.REACT.beat() * (s.reactStrength / 100f);
            float tm = time();
            for (int i = 0; i < 5; i++) {
                float wave = 0.55f + 0.45f * (float) Math.sin(tm * (6f + i * 1.7f) + i * 1.3f);
                float hgt = 2f + 13f * LyricsView.clamp01(lv * wave + bt * 0.3f);
                int bx = x + bw - 9 - 5 * 5 + i * 5, top = y + 20 - Math.round(hgt);
                Glass.rrect(ctx, bx, top, 3, Math.round(hgt), 1, LyricsView.mix(s.colour, s.colour2, i / 4f));
            }
        }
    }
}
