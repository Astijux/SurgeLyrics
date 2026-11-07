package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

/**
 * Liquid-glass settings window: one floating glass panel, a sliding pill tab bar, springy switches,
 * sliders and swatches, and a live preview of how the lyrics will look. Drawn immediate-mode on top of
 * the game's own background blur. Mouse is polled from GLFW so it doesn't depend on screen event APIs.
 */
public final class SettingsScreen extends Screen {
    private static final int W = 340, H = 246;
    private static final String[] TABS = {"Lyrics", "Look", "Surge", "Source"};
    private static final int[] COLOURS = {0xFF7DD3FC, 0xFFC084FC, 0xFFF9A8D4, 0xFFFDE68A, 0xFF86EFAC, 0xFFFDA4AF, 0xFF5EEAD4, 0xFFFFFFFF};
    private static final String[] MOTION = {"Calm", "Normal", "Lively"};
    private static final String[] SOURCES = {"Auto", "Windows", "Spotify"};
    private static final int TEXT = 0xFFF2F5FA, DIM = 0xFFA4ADBF, FAINT = 0xFF7C8598;
    private static int lastTab = 0;

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

        float o = open.step(dt, closing ? 0f : 1f);
        Glass.alpha = LyricsView.clamp01(o);
        if (closing && o < 0.04f) { Runtime.save(); client.setScreen(parent); Glass.alpha = 1f; return; }

        float fit = Math.min(1f, Math.min((height - 12f) / H, (width - 12f) / W));
        float s = fit * (0.94f + 0.06f * Math.min(1f, o));
        float ox = (width - W * s) / 2f, oy = (height - H * s) / 2f;

        boolean down = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        ui.begin(ctx, textRenderer, (mouseX - ox) / s, (mouseY - oy) / s, down, dt);

        Settings st = Runtime.SETTINGS;
        int accent = st.colour;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ox, oy);
        m.scale(s, s);

        Glass.shadow(ctx, 0, 0, W, H, 18);
        Glass.panel(ctx, 0, 0, W, H, 18, 0x70141A2A, 1f, 0.17f);

        // header
        ui.label("Surge Lyrics", 16, 12, TEXT);
        Glass.textRight(ctx, textRenderer, "K to reopen", W - 40, 12, FAINT);
        if (ui.button("close", W - 30, 8, 16, 16, "x", accent, false)) closing = true;

        // tabs (sliding glass indicator)
        lastTab = ui.segmented("tabs", 14, 30, W - 28, TABS, lastTab, accent);

        boolean surgeChanged = false, sourceChanged = false;
        idField.visible = false;
        final int lx = 16, cx = 134, cw = W - 14 - cx, rv = W - 16;
        int y = 60;
        final int rh = 22;

        switch (lastTab) {
            case 0 -> {
                ui.label("Lyrics", lx, y + 3, TEXT);
                st.lyricsEnabled = ui.toggle("lyr", W - 44, y, st.lyricsEnabled, accent); y += rh;
                ui.label("Now-playing bar", lx, y + 3, TEXT);
                st.showBar = ui.toggle("bar", W - 44, y, st.showBar, accent); y += rh;
                ui.label("Words per line", lx, y + 3, TEXT);
                st.wordsPerLine = Math.round(ui.slider("wpl", cx, y, cw - 44, st.wordsPerLine, 1, 8, 1, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(st.wordsPerLine), rv, y + 4, DIM); y += rh;
                ui.label("Stay time", lx, y + 3, TEXT);
                st.staySeconds = ui.slider("stay", cx, y, cw - 44, st.staySeconds, 1, 10, 0.5f, accent);
                Glass.textRight(ctx, textRenderer, st.staySeconds + "s", rv, y + 4, DIM); y += rh;
                ui.label("Size", lx, y + 3, TEXT);
                st.sizePercent = Math.round(ui.slider("size", cx, y, cw - 44, st.sizePercent, 50, 200, 5, accent));
                Glass.textRight(ctx, textRenderer, st.sizePercent + "%", rv, y + 4, DIM); y += rh;
                ui.label("Sync", lx, y + 3, TEXT);
                st.syncOffsetMs = Math.round(ui.slider("sync", cx, y, cw - 44, st.syncOffsetMs, -3000, 3000, 50, accent));
                Glass.textRight(ctx, textRenderer, (st.syncOffsetMs > 0 ? "+" : "") + st.syncOffsetMs + " ms", rv, y + 4, DIM);
            }
            case 1 -> {
                ui.label("Glow", lx, y + 3, TEXT);
                st.glow = ui.toggle("glow", W - 44, y, st.glow, accent); y += rh;
                ui.label("Gradient", lx, y + 3, TEXT);
                st.gradient = ui.toggle("grad", W - 44, y, st.gradient, accent); y += rh;
                ui.label("Motion", lx, y + 3, TEXT);
                st.intensity = ui.segmented("motion", cx, y, cw, MOTION, st.intensity, accent); y += rh;
                ui.label("Colour", lx, y + 3, TEXT);
                st.colour = ui.swatches("c1", cx, y + 1, COLOURS, st.colour); y += rh;
                ui.label("Colour 2", lx, y + 3, TEXT);
                st.colour2 = ui.swatches("c2", cx, y + 1, COLOURS, st.colour2);
            }
            case 2 -> {
                SurgeProfile[] ps = SurgeProfile.values();
                String[] names = new String[ps.length];
                int cur = 0;
                for (int i = 0; i < ps.length; i++) { names[i] = ps[i].label; if (ps[i].name().equals(st.surgeProfile)) cur = i; }
                ui.label("Profile", lx, y + 3, TEXT);
                int sel = ui.segmented("prof", cx, y, cw, names, cur, accent);
                if (sel != cur) { st.surgeProfile = ps[sel].name(); surgeChanged = true; }
                y += rh;
                ui.label("Adaptive easing", lx, y + 3, TEXT);
                boolean ad = ui.toggle("adapt", W - 44, y, st.surgeAdaptive, accent);
                if (ad != st.surgeAdaptive) { st.surgeAdaptive = ad; surgeChanged = true; }
                y += rh;
                ui.label("FPS overlay", lx, y + 3, TEXT);
                st.surgeOverlay = ui.toggle("ovl", W - 44, y, st.surgeOverlay, accent); y += rh;
                ui.label("Target FPS", lx, y + 3, TEXT);
                int tf = Math.round(ui.slider("tfps", cx, y, cw - 44, st.targetFps, 30, 240, 10, accent));
                if (tf != st.targetFps) { st.targetFps = tf; surgeChanged = true; }
                Glass.textRight(ctx, textRenderer, String.valueOf(st.targetFps), rv, y + 4, DIM); y += rh + 4;
                SurgeProfile p = Runtime.SURGE.profile();
                String d = p == SurgeProfile.VANILLA ? "Nothing is culled."
                        : "Entities past " + (int) (p.entityBlocks * Runtime.SURGE.scale()) + " blocks hidden, particles capped at "
                        + Runtime.SURGE.particleBudget() + ".";
                Glass.text(ctx, textRenderer, Glass.fit(textRenderer, d, W - 32), lx, y, DIM);
                Glass.text(ctx, textRenderer, "Live: " + LyricsHud.lastFps + " fps   ranges at " + Math.round(Runtime.SURGE.scale() * 100) + "%", lx, y + 12, FAINT);
            }
            default -> {
                ui.label("Source", lx, y + 3, TEXT);
                int src = ui.segmented("src", cx, y, cw, SOURCES, st.source, accent);
                if (src != st.source) { st.source = src; sourceChanged = true; }
                y += rh;
                Glass.text(ctx, textRenderer, Glass.fit(textRenderer, Runtime.NOW.describe(), W - 32), lx, y + 3, TEXT); y += 12;
                Glass.text(ctx, textRenderer, "Windows: " + Runtime.NOW.windowsStatus() + "   Spotify: " + Runtime.SPOTIFY.status(), lx, y + 3, FAINT);
                y += 18;
                ui.label("Spotify Client ID (only for the Spotify source)", lx, y, DIM); y += 12;
                Glass.panel(ctx, lx, y, W - 32, 18, 9, 0x34000000, 0.4f, 0f);
                idField.visible = true;
                int fx = Math.round(ox + (lx + 8) * s), fy = Math.round(oy + (y + 5) * s);
                idField.setX(fx); idField.setY(fy);
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

        // live preview (lyrics + look tabs)
        if (lastTab <= 1) {
            int py = H - 50;
            Glass.panel(ctx, 14, py, W - 28, 38, 12, 0x2A000000, 0.5f, 0.12f);
            String sample = "Glass lyrics look like this";
            float sc = Math.min(1.5f * st.sizePercent / 100f, (W - 56f) / Math.max(1, textRenderer.getWidth(sample)));
            LyricsHud.drawCentered(ctx, textRenderer, sample, W / 2f, py + 19 - 4.5f * sc, sc, Glass.alpha, st, true);
        }

        m.popMatrix();

        if (surgeChanged) Runtime.applySurge();
        if (sourceChanged) Runtime.NOW.sync();
        if (idField.visible) idField.render(ctx, mouseX, mouseY, delta);
        Glass.alpha = 1f;
    }

    @Override
    public void close() { closing = true; }

    @Override
    public boolean shouldPause() { return false; }
}
