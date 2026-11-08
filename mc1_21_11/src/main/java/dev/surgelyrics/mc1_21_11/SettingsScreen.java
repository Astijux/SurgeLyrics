package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.List;

/**
 * Liquid-glass settings window. Eight tabs, a font picker that previews each font in itself, a placement
 * grid with a mini-screen, full colour control, 3D text options, and an optional floating 3D window that
 * leans with the mouse. Drawn immediate-mode over the game's background blur; the mouse is polled from GLFW.
 */
public final class SettingsScreen extends Screen {
    private static final int W = 380, H = 262;
    private static final String[] TABS = {"Lyrics", "Style", "Layout", "Colour", "Effects", "Surge", "Culling", "Source"};
    private static final String[] MOTION = {"Calm", "Normal", "Lively"};
    private static final String[] SOURCES = {"Auto", "Windows", "Spotify"};
    private static final String[] DEPTH = {"Flat", "3D", "Outline"};
    private static final String[] BAR = {"Top", "Bottom left"};
    private static final String[] EDIT = {"Primary", "Secondary"};
    private static final int TEXT = 0xFFF2F5FA, DIM = 0xFFA4ADBF, FAINT = 0xFF7C8598;
    private static int lastTab = 0;
    private static boolean editSecond = false;

    private final Screen parent;
    private final GlassUi ui = new GlassUi();
    private final Motion.Spring open = new Motion.Spring(0f, 200f, 22f);
    private TextFieldWidget idField;
    private long lastNanos = System.nanoTime();
    private boolean closing;

    public SettingsScreen(Screen parent) { super(Text.literal("Surge Lyrics")); this.parent = parent; }

    @Override
    protected void init() {
        idField = new TextFieldWidget(textRenderer, 0, 0, 100, 14, Text.literal("Spotify Client ID"));
        idField.setDrawsBackground(false);
        idField.setMaxLength(64);
        idField.setText(Runtime.SETTINGS.spotifyClientId);
        idField.setPlaceholder(Text.literal("paste Client ID"));
        idField.setChangedListener(t -> Runtime.SETTINGS.spotifyClientId = t.strip());
        idField.visible = false;
        addSelectableChild(idField);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);         // the game's own background blur
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Settings st = Runtime.SETTINGS;

        float o = open.step(dt, closing ? 0f : 1f);
        Glass.alpha = LyricsView.clamp01(o);
        if (closing && o < 0.04f) { Runtime.save(); client.setScreen(parent); Glass.alpha = 1f; return; }

        // floating 3D window: leans away from the mouse
        float par = st.window3d ? 1f : 0f;
        float nx = Math.max(-1f, Math.min(1f, (mouseX - width / 2f) / (width / 2f)));
        float ny = Math.max(-1f, Math.min(1f, (mouseY - height / 2f) / (height / 2f)));
        float pdx = nx * par * 3f, pdy = ny * par * 2.5f;

        float fit = Math.min(1f, Math.min((height - 12f) / H, (width - 12f) / W));
        float s = fit * (0.94f + 0.06f * Math.min(1f, o));
        float ox = (width - W * s) / 2f - pdx * 0.5f, oy = (height - H * s) / 2f - pdy * 0.5f;

        boolean down = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        ui.begin(ctx, textRenderer, (mouseX - ox) / s, (mouseY - oy) / s, down, dt);

        int accent = st.colour;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ox, oy);
        m.scale(s, s);

        Glass.shadow(ctx, Math.round(pdx), Math.round(pdy), W, H, 18);
        if (st.window3d) {                                 // slab thickness, shifts with the lean
            for (int i = 4; i >= 1; i--)
                Glass.rrect(ctx, Math.round(pdx * i * 0.35f), Math.round(pdy * i * 0.35f + i * 0.7f), W, H, 18, 0x26101828);
        }
        Glass.panel(ctx, 0, 0, W, H, 18, 0x70141A2A, 1f, 0.17f);

        ui.label("Surge Lyrics", 16, 12, TEXT);
        Glass.textRight(ctx, textRenderer, "K to reopen", W - 40, 12, FAINT);
        if (ui.button("close", W - 30, 8, 16, 16, "x", accent, false)) closing = true;

        lastTab = ui.segmented("tabs", 14, 30, W - 28, TABS, lastTab, accent);

        boolean surgeChanged = false, sourceChanged = false;
        idField.visible = false;
        final int lx = 16, cx = 134, cw = W - 14 - cx, rv = W - 16, rh = 22, tg = W - 44;
        int y = 60;

        switch (lastTab) {
            case 0 -> {
                ui.label("Lyrics", lx, y + 3, TEXT);
                st.lyricsEnabled = ui.toggle("lyr", tg, y, st.lyricsEnabled, accent); y += rh;
                ui.label("Now-playing bar", lx, y + 3, TEXT);
                st.showBar = ui.toggle("bar", tg, y, st.showBar, accent); y += rh;
                ui.label("Display", lx, y + 3, TEXT);
                st.displayMode = ui.segmented("mode", cx, y, cw, LyricsView.MODES, st.displayMode, accent); y += rh;
                ui.label("Words per line", lx, y + 3, st.displayMode == 0 ? TEXT : FAINT);
                st.wordsPerLine = Math.round(ui.slider("wpl", cx, y, cw - 44, st.wordsPerLine, 1, 8, 1, accent));
                Glass.textRight(ctx, textRenderer, st.displayMode == 0 ? String.valueOf(st.wordsPerLine) : "chunk mode", rv, y + 4, DIM); y += rh;
                ui.label("Stay time", lx, y + 3, TEXT);
                st.staySeconds = ui.slider("stay", cx, y, cw - 44, st.staySeconds, 1, 10, 0.5f, accent);
                Glass.textRight(ctx, textRenderer, st.staySeconds + "s", rv, y + 4, DIM); y += rh;
                ui.label("Sync", lx, y + 3, TEXT);
                st.syncOffsetMs = Math.round(ui.slider("sync", cx, y, cw - 44, st.syncOffsetMs, -3000, 3000, 50, accent));
                Glass.textRight(ctx, textRenderer, (st.syncOffsetMs > 0 ? "+" : "") + st.syncOffsetMs + " ms", rv, y + 4, DIM);
            }
            case 1 -> {
                FontStyle[] fs = FontStyle.values();
                int chipW = (W - 32 - 18) / 4;
                for (int i = 0; i < fs.length; i++) {
                    int cxp = lx + (i % 4) * (chipW + 6), cyp = y + (i / 4) * 22;
                    if (ui.chip("font" + i, cxp, cyp, chipW, 18, fs[i].label, fs[i].fontId(), st.font == i, accent)) st.font = i;
                }
                y += 2 * rh + 6;
                ui.label("Size", lx, y + 3, TEXT);
                st.sizePercent = Math.round(ui.slider("size", cx, y, cw - 44, st.sizePercent, 50, 200, 5, accent));
                Glass.textRight(ctx, textRenderer, st.sizePercent + "%", rv, y + 4, DIM); y += rh;
                ui.label("Opacity", lx, y + 3, TEXT);
                st.opacity = Math.round(ui.slider("opac", cx, y, cw - 44, st.opacity, 25, 100, 5, accent));
                Glass.textRight(ctx, textRenderer, st.opacity + "%", rv, y + 4, DIM);
            }
            case 2 -> {
                // mini screen doubling as the anchor picker
                int mx0 = lx, my0 = y, mw = 120, mh = 68, cw3 = mw / 3, ch3 = mh / 3;
                Glass.panel(ctx, mx0, my0, mw, mh, 8, 0x30000000, 0.6f, 0.1f);
                for (int a = 0; a < 9; a++) {
                    int gx = mx0 + (a % 3) * cw3, gy = my0 + (a / 3) * ch3;
                    boolean hov = ui.hover(gx, gy, cw3, ch3), sel = st.anchor == a;
                    float sp = ui.anim("anc" + a, sel ? 1 : 0, sel ? 1 : 0);
                    if (sp > 0.02f || hov)
                        Glass.panel(ctx, gx + 2, gy + 2, cw3 - 4, ch3 - 4, 5,
                                LyricsView.mix(hov ? 0x26FFFFFF : 0x00FFFFFF, (0x70 << 24) | (accent & 0xFFFFFF), LyricsView.clamp01(sp)),
                                sel ? 0.9f : 0.4f, 0.2f);
                    if (hov && ui.pressed) st.anchor = a;
                }
                int[] pp = Layout.place(st.anchor, st.offX, st.offY, 427, 240, 200, 22);
                int pw = Math.round(200 * mw / 427f), px = mx0 + Math.round(pp[0] * mw / 427f) - pw / 2, py = my0 + Math.round(pp[1] * mh / 240f) - 2;
                Glass.rrect(ctx, px, py, pw, 5, 2, 0xFFFFFFFF);
                Glass.rrect(ctx, px + 1, py + 1, Math.max(1, pw - 2), 3, 1, accent);

                int rx = 150, sx = 226, sww = 84;
                ui.label("Horizontal", rx, y + 3, TEXT);
                st.offX = Math.round(ui.slider("ox", sx, y, sww, st.offX, -300, 300, 5, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(st.offX), rv, y + 4, DIM);
                ui.label("Vertical", rx, y + rh + 3, TEXT);
                st.offY = Math.round(ui.slider("oy", sx, y + rh, sww, st.offY, -300, 300, 5, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(st.offY), rv, y + rh + 4, DIM);
                if (ui.button("reset", rx, y + 2 * rh, 110, 18, "Reset position", accent, false)) { st.anchor = 4; st.offX = 0; st.offY = 30; }
                Glass.text(ctx, textRenderer, "Anchor: " + Layout.ANCHORS[st.anchor], rx, y + 2 * rh + 26, DIM);

                int by = my0 + mh + 14;
                ui.label("Now-playing bar", lx, by + 3, TEXT);
                int bsel = ui.segmented("barpos", cx + 20, by, 190, BAR, st.barBottom ? 1 : 0, accent);
                st.barBottom = bsel == 1;
                Glass.text(ctx, textRenderer, "Click a cell to move the lyrics, then nudge with the sliders.", lx, by + 28, FAINT);
            }
            case 3 -> {
                List<Colors.Palette> ps = Colors.PALETTES;
                ui.label("Presets", lx, y + 4, TEXT);
                int pick = ui.palettes("pal", cx, y + 2, ps, st.colour, st.colour2);
                if (pick >= 0) { st.colour = ps.get(pick).c1(); st.colour2 = ps.get(pick).c2(); }
                y += rh;
                ui.label("Edit", lx, y + 3, TEXT);
                editSecond = ui.segmented("edit", cx, y, 150, EDIT, editSecond ? 1 : 0, accent) == 1; y += rh;
                int cur = editSecond ? st.colour2 : st.colour;
                float[] hsb = Colors.toHsb(cur);
                int h0 = Math.round(hsb[0] * 360), s0 = Math.round(hsb[1] * 100), b0 = Math.round(hsb[2] * 100);
                ui.label("Hue", lx, y + 3, TEXT);
                int h1 = Math.round(ui.slider("hue", cx, y, cw - 44, h0, 0, 360, 1, cur));
                Glass.textRight(ctx, textRenderer, h1 + "\u00b0", rv, y + 4, DIM); y += rh;
                ui.label("Saturation", lx, y + 3, TEXT);
                int s1 = Math.round(ui.slider("sat", cx, y, cw - 44, s0, 0, 100, 1, cur));
                Glass.textRight(ctx, textRenderer, s1 + "%", rv, y + 4, DIM); y += rh;
                ui.label("Brightness", lx, y + 3, TEXT);
                int b1 = Math.round(ui.slider("bri", cx, y, cw - 44, b0, 0, 100, 1, cur));
                Glass.textRight(ctx, textRenderer, b1 + "%", rv, y + 4, DIM); y += rh;
                if (h1 != h0 || s1 != s0 || b1 != b0) {            // only write back when you actually moved a slider
                    int nc = Colors.fromHsb(h1 / 360f, s1 / 100f, b1 / 100f);
                    if (editSecond) st.colour2 = nc; else st.colour = nc;
                }
                ui.label("Gradient", lx, y + 3, TEXT);
                st.gradientMode = ui.segmented("grad", cx, y, cw, Colors.GRADIENT_MODES, st.gradientMode, accent); y += rh;
                ui.label("Flow speed", lx, y + 3, st.gradientMode >= 2 ? TEXT : FAINT);
                st.gradientSpeed = Math.round(ui.slider("gspd", cx, y, cw - 44, st.gradientSpeed, 1, 10, 1, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(st.gradientSpeed), rv, y + 4, DIM);
            }
            case 4 -> {
                ui.label("Text depth", lx, y + 3, TEXT);
                st.depthStyle = ui.segmented("depth", cx, y, cw, DEPTH, st.depthStyle, accent); y += rh;
                boolean d3 = st.depthStyle == 1;
                ui.label("3D thickness", lx, y + 3, d3 ? TEXT : FAINT);
                st.depthAmount = Math.round(ui.slider("damt", cx, y, cw - 44, st.depthAmount, 1, 8, 1, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(st.depthAmount), rv, y + 4, DIM); y += rh;
                ui.label("Light angle", lx, y + 3, d3 ? TEXT : FAINT);
                st.lightAngle = Math.round(ui.slider("ang", cx, y, cw - 44, st.lightAngle, 0, 355, 5, accent));
                Glass.textRight(ctx, textRenderer, st.lightAngle + "\u00b0", rv, y + 4, DIM); y += rh;
                ui.label("Glow", lx, y + 3, TEXT);
                st.glowStrength = Math.round(ui.slider("glow", cx, y, cw - 44, st.glowStrength, 0, 100, 5, accent));
                Glass.textRight(ctx, textRenderer, st.glowStrength == 0 ? "off" : st.glowStrength + "%", rv, y + 4, DIM); y += rh;
                ui.label("Motion", lx, y + 3, TEXT);
                st.intensity = ui.segmented("motion", cx, y, cw, MOTION, st.intensity, accent); y += rh;
                ui.label("3D settings window", lx, y + 3, TEXT);
                st.window3d = ui.toggle("w3d", tg, y, st.window3d, accent);
            }
            case 5 -> {
                SurgeProfile[] ps = SurgeProfile.values();
                SurgeProfile curP = SurgeProfile.byName(st.surgeProfile);
                int chipW = (W - 32 - 18) / 4;
                for (int i = 0; i < ps.length; i++) {
                    int cxp = lx + (i % 4) * (chipW + 6), cyp = y + (i / 4) * 22;
                    if (ui.chip("sp" + i, cxp, cyp, chipW, 18, ps[i].label, null, ps[i] == curP, accent)) {
                        if (ps[i] == SurgeProfile.CUSTOM && curP != SurgeProfile.CUSTOM && curP != null)
                            SurgeLimits.copyToCustom(curP, st);          // Custom starts from what you were using
                        st.surgeProfile = ps[i].name();
                        surgeChanged = true;
                    }
                }
                y += 2 * rh + 6;
                ui.label("Adaptive easing", lx, y + 3, TEXT);
                boolean ad = ui.toggle("adapt", tg, y, st.surgeAdaptive, accent);
                if (ad != st.surgeAdaptive) { st.surgeAdaptive = ad; surgeChanged = true; }
                y += rh;
                ui.label("Target FPS", lx, y + 3, st.surgeAdaptive ? TEXT : FAINT);
                int tf = Math.round(ui.slider("tfps", cx, y, cw - 44, st.targetFps, 30, 240, 10, accent));
                if (tf != st.targetFps) { st.targetFps = tf; surgeChanged = true; }
                Glass.textRight(ctx, textRenderer, String.valueOf(st.targetFps), rv, y + 4, DIM); y += rh;
                ui.label("Background FPS", lx, y + 3, TEXT);
                st.backgroundFps = Math.round(ui.slider("bgfps", cx, y, cw - 44, st.backgroundFps, 0, 60, 5, accent));
                Glass.textRight(ctx, textRenderer, st.backgroundFps == 0 ? "off" : String.valueOf(st.backgroundFps), rv, y + 4, DIM); y += rh;
                ui.label("Lighten lyrics when slow", lx, y + 3, TEXT);
                boolean lt = ui.toggle("lite", tg, y, st.lightenLyrics, accent);
                if (lt != st.lightenLyrics) { st.lightenLyrics = lt; surgeChanged = true; }
                y += rh;
                ui.label("FPS overlay", lx, y + 3, TEXT);
                st.surgeOverlay = ui.toggle("ovl", tg, y, st.surgeOverlay, accent); y += rh + 2;
                SurgeLimits lim = Runtime.SURGE.limits();
                String d = !Runtime.SURGE.active() ? "Nothing is culled."
                        : "Mobs past " + (int) (lim.mob() * Runtime.SURGE.scale()) + " blocks, items past " + (int) (lim.item() * Runtime.SURGE.scale())
                        + ", particles capped at " + Runtime.SURGE.particleBudget() + ".";
                Glass.text(ctx, textRenderer, Glass.fit(textRenderer, d, W - 32), lx, y, DIM);
                Glass.text(ctx, textRenderer, "Live: " + LyricsHud.lastFps + " fps, 1% low " + LyricsHud.lastLow
                        + "   ranges at " + Math.round(Runtime.SURGE.scale() * 100) + "%", lx, y + 12, FAINT);
            }
            case 6 -> {
                final int r2 = 20;
                SurgeProfile curP = SurgeProfile.byName(st.surgeProfile);
                ui.label("Custom limits", lx, y + 3, TEXT);
                if (ui.button("copyp", W - 154, y, 140, 16, "Copy current profile", accent, false)) {
                    SurgeLimits.copyToCustom(curP == null ? SurgeProfile.BALANCED : curP, st);
                    st.surgeProfile = SurgeProfile.CUSTOM.name(); surgeChanged = true;
                }
                y += r2;
                int[] before = {st.cMob, st.cItem, st.cDeco, st.cBlockEntity, st.cNametag, st.cParticleDist, st.cParticleCap};
                st.cMob = distRow(ctx, "cmob", "Mobs", st.cMob, 0, 192, 4, y, accent, " blocks"); y += r2;
                st.cItem = distRow(ctx, "citem", "Dropped items", st.cItem, 0, 192, 4, y, accent, " blocks"); y += r2;
                st.cDeco = distRow(ctx, "cdeco", "Frames and stands", st.cDeco, 0, 192, 4, y, accent, " blocks"); y += r2;
                st.cBlockEntity = distRow(ctx, "cblk", "Chests and signs", st.cBlockEntity, 0, 192, 4, y, accent, " blocks"); y += r2;
                st.cNametag = distRow(ctx, "ctag", "Name tags", st.cNametag, 0, 192, 4, y, accent, " blocks"); y += r2;
                st.cParticleDist = distRow(ctx, "cpd", "Particle distance", st.cParticleDist, 0, 192, 4, y, accent, " blocks"); y += r2;
                st.cParticleCap = distRow(ctx, "cpc", "Particles per tick", st.cParticleCap, 0, 2000, 20, y, accent, ""); y += r2;
                boolean moved = before[0] != st.cMob || before[1] != st.cItem || before[2] != st.cDeco || before[3] != st.cBlockEntity
                        || before[4] != st.cNametag || before[5] != st.cParticleDist || before[6] != st.cParticleCap;
                ui.label("Hide clouds", lx, y + 3, TEXT);
                boolean cc = ui.toggle("ccl", lx + 76, y, st.cClouds, accent);
                ui.label("Hide weather", 196, y + 3, TEXT);
                boolean cw2 = ui.toggle("cwx", tg, y, st.cWeather, accent);
                if (cc != st.cClouds || cw2 != st.cWeather) { st.cClouds = cc; st.cWeather = cw2; moved = true; }
                if (moved) { st.surgeProfile = SurgeProfile.CUSTOM.name(); surgeChanged = true; }
                y += r2 + 6;
                Glass.text(ctx, textRenderer, "Active: " + Runtime.SURGE.profile().label + ".  0 = never hide.  Moving a slider switches to Custom.", lx, y, FAINT);
                Glass.text(ctx, textRenderer, "Never hidden: you, other players, your pets, your mount, bosses, glowing mobs.", lx, y + 11, FAINT);
            }
            default -> {
                ui.label("Source", lx, y + 3, TEXT);
                int src = ui.segmented("src", cx, y, cw, SOURCES, st.source, accent);
                if (src != st.source) { st.source = src; sourceChanged = true; }
                y += rh;
                Glass.text(ctx, textRenderer, Glass.fit(textRenderer, Runtime.NOW.describe(), W - 32), lx, y + 3, TEXT); y += 12;
                Glass.text(ctx, textRenderer, Glass.fit(textRenderer, "Windows: " + Runtime.NOW.windowsStatus() + "   Spotify: " + Runtime.SPOTIFY.status(), W - 32), lx, y + 3, FAINT);
                y += 18;
                ui.label("Spotify Client ID (only for the Spotify source)", lx, y, DIM); y += 12;
                Glass.panel(ctx, lx, y, W - 32, 18, 9, 0x34000000, 0.4f, 0f);
                idField.visible = true;
                idField.setX(Math.round(ox + (lx + 8) * s)); idField.setY(Math.round(oy + (y + 5) * s));
                idField.setWidth(Math.round((W - 48) * s)); idField.setHeight(Math.max(10, Math.round(9 * s)));
                y += 26;
                boolean conn = Runtime.SPOTIFY.connected();
                if (ui.button("conn", lx, y, 130, 18, conn ? "Disconnect Spotify" : "Connect Spotify", accent, !conn)) {
                    if (conn) Runtime.SPOTIFY.logout();
                    else Runtime.SPOTIFY.startLogin(url -> Util.getOperatingSystem().open(URI.create(url)));
                }
                Glass.text(ctx, textRenderer, "Redirect URI: " + Runtime.SPOTIFY.redirectUri(), lx + 138, y + 5, FAINT);
            }
        }

        // live preview, in the real renderer, for every tab that changes how lyrics look
        if (lastTab <= 1 || lastTab == 3 || lastTab == 4) {
            int py = H - 46;
            Glass.panel(ctx, 14, py, W - 28, 34, 12, 0x2A000000, 0.5f, 0.12f);
            String sample = "Glass lyrics look like this";
            float t = LyricsHud.time();
            float fillAnim = (t % 3f) / 3f;
            LyricsView.View pv = new LyricsView.View(sample, 1f, "and the next line fades in", 0f, "the line before", fillAnim);
            float sc = Math.min(1.2f * st.sizePercent / 100f, 1.35f);
            float cyp = py + (st.displayMode == 4 ? 17 : 14);
            LyricsHud.previewMode = true;
            LyricsHud.drawBlock(ctx, textRenderer, pv, st, W / 2f, cyp, st.displayMode == 4 ? Math.min(sc, 0.9f) : sc, Glass.alpha, t, W - 56f);
            LyricsHud.previewMode = false;
        }

        m.popMatrix();

        if (surgeChanged) Runtime.applySurge();
        if (sourceChanged) Runtime.NOW.sync();
        if (idField.visible) idField.render(ctx, mouseX, mouseY, delta);
        Glass.alpha = 1f;
    }

    /** One labelled slider row for the Culling tab. 0 shows as "off" for distances. */
    private int distRow(DrawContext ctx, String id, String label, int val, int min, int max, int step, int y, int accent, String unit) {
        ui.label(label, 16, y + 3, TEXT);
        int v = Math.round(ui.slider(id, 134, y, W - 14 - 134 - 70, val, min, max, step, accent));
        Glass.textRight(ctx, textRenderer, v == 0 ? (unit.isEmpty() ? "unlimited" : "off") : v + unit, W - 16, y + 4, DIM);
        return v;
    }

    @Override
    public void close() { closing = true; }

    @Override
    public boolean shouldPause() { return false; }
}
