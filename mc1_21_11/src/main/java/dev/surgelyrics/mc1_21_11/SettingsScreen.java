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
import java.util.function.IntConsumer;

/**
 * Liquid-glass settings window. Nine tabs: lyrics, a paged font picker (each font previewed in itself), placement,
 * colour with hex input and gradient tracks, 3D effects, HUD widgets, and Surge. The window can float in 3D and lean
 * with the mouse. Drawn immediate-mode over the game's background blur; the mouse is polled from GLFW.
 */
public final class SettingsScreen extends Screen {
    private static final int W = 540, H = 262;
    private static final String[] TABS = {"Lyrics", "Style", "Layout", "Colour", "FX", "Flair", "HUD", "PvP", "Audio", "Packs", "Surge", "Cull", "Source"};
    private static final String[] MOTION = {"Calm", "Normal", "Lively"};
    private static final String[] SOURCES = {"Auto", "Windows", "Spotify"};
    private static final String[] DEPTH = {"Flat", "3D", "Outline"};
    private static final String[] BAR = {"Top", "Bottom left"};
    private static final String[] EDIT = {"Primary", "Secondary"};
    private static final String[] WIDGETS = {"Armor", "Keys", "Ping", "Timer", "Reach", "Combo", "Hotbar"};
    private static final String[] ARMOR_LAYOUT = {"Vertical", "Horizontal"};
    private static final String[] DURABILITY = {"None", "Bar", "Number", "Percent"};
    private static final String[] PVP_CHIPS = {"Target", "Effects", "Items", "Coords", "CPS", "Health", "Speed", "Stance", "Alerts", "Zoom"};
    private static final String[] PACK_PAGES = {"Packs", "Window"};
    private static final String[] PACK_KINDS = {"Looks", "PvP loadouts"};
    private static final String[] TARGET_SRC = {"Last hit + aim", "Last hit", "Crosshair"};
    private static final String[] BACKDROP = {"None", "Glass", "Dark", "Bar"};
    private static final String[] HB_STYLE = {"Glass", "Dark", "Minimal"};
    private static final String[] TIMER_MODE = {"Game launch", "World join"};
    private static final int[] QUICK = {0xFF7DD3FC, 0xFFC084FC, 0xFFF9A8D4, 0xFFFDE68A, 0xFF86EFAC, 0xFFFDA4AF, 0xFF5EEAD4, 0xFFFFFFFF};
    private static final int[] HUE_STOPS = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
    private static final int TEXT = 0xFFF2F5FA, DIM = 0xFFA4ADBF, FAINT = 0xFF7C8598;
    private static int lastTab = 0, fontPage = -1, hudSel = 0, pvpSel = 0, packPage = 0, packKind = 0, lastPack = -1;
    private static boolean editSecond = false;

    private final Screen parent;
    private final GlassUi ui = new GlassUi();
    private final Motion.Spring open = new Motion.Spring(0f, 200f, 22f);
    private TextFieldWidget idField, hexField;
    private long lastNanos = System.nanoTime();
    private boolean closing, hexSetting;
    private String hexKey = "";
    private IntConsumer hexTarget;
    private float curOx, curOy, curS = 1f;
    private int pvIdx;
    private String pvPrev = "";
    private long pvStart = System.nanoTime();

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

        hexField = new TextFieldWidget(textRenderer, 0, 0, 70, 12, Text.literal("Hex colour"));
        hexField.setDrawsBackground(false);
        hexField.setMaxLength(8);
        hexField.setPlaceholder(Text.literal("#RRGGBB"));
        hexField.setChangedListener(t -> {
            if (hexSetting || hexTarget == null) return;
            Integer c = Colors.parseHex(t);
            if (c != null) hexTarget.accept(c);          // only valid codes apply, so typing never breaks anything
        });
        hexField.visible = false;
        addSelectableChild(hexField);

        if (fontPage < 0) fontPage = Runtime.SETTINGS.font / FontStyle.PER_PAGE;
    }

    /** Point the shared hex box at a colour. Doesn't overwrite what you're typing while it has focus. */
    private void hexBind(String key, int colour, IntConsumer target) {
        hexTarget = target;
        hexField.visible = true;
        if (!key.equals(hexKey) || !hexField.isFocused()) {
            String want = Colors.toHex(colour);
            if (!want.equalsIgnoreCase(hexField.getText())) { hexSetting = true; hexField.setText(want); hexSetting = false; }
        }
        hexKey = key;
    }

    private void hexPill(DrawContext ctx, int x, int y, int w, int colour) {
        boolean valid = Colors.parseHex(hexField.getText()) != null;
        Glass.panel(ctx, x, y, w, 16, 8, valid ? 0x34000000 : 0x66F87171, 0.5f, 0f);
        Glass.panel(ctx, x + 4, y + 3, 10, 10, 5, colour, 0.9f, 0.3f);
        hexField.setX(Math.round(curOx + (x + 20) * curS));
        hexField.setY(Math.round(curOy + (y + 4) * curS));
        hexField.setWidth(Math.round((w - 26) * curS));
        hexField.setHeight(Math.max(10, Math.round(9 * curS)));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        try {
            renderBody(ctx, mouseX, mouseY, delta);
        } catch (Throwable t) {                           // a broken settings screen must never crash the game: close it
            Safe.fail("settings-screen", t);
            Safe.unwind(ctx);
            Glass.alpha = 1f;
            Glass.fast = false;
            Runtime.save();
            client.setScreen(parent);
        }
    }

    private void renderBody(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);         // the game's own background blur
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Settings st = Runtime.SETTINGS;
        Glass.fast = false;

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
        curOx = ox; curOy = oy; curS = s;

        boolean down = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        ui.begin(ctx, textRenderer, (mouseX - ox) / s, (mouseY - oy) / s, down, dt);

        int accent = st.colour;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ox, oy);
        m.scale(s, s);

        if (st.windowGlow) {                               // soft colour clouds behind the glass, drifting slowly
            float tt = LyricsHud.time();
            blob(ctx, W * 0.14f + 22 * (float) Math.sin(tt * 0.31), H * 0.18f + 14 * (float) Math.cos(tt * 0.27), 72, st.colour);
            blob(ctx, W * 0.86f + 18 * (float) Math.cos(tt * 0.23), H * 0.82f + 16 * (float) Math.sin(tt * 0.29), 78, st.colour2);
            blob(ctx, W * 0.52f + 40 * (float) Math.sin(tt * 0.17), H + 6, 60, st.colour);
        }
        Glass.shadow(ctx, Math.round(pdx), Math.round(pdy), W, H, 18);
        if (st.window3d) {                                 // slab thickness, shifts with the lean
            for (int i = 4; i >= 1; i--)
                Glass.rrect(ctx, Math.round(pdx * i * 0.35f), Math.round(pdy * i * 0.35f + i * 0.7f), W, H, 18, 0x26101828);
        }
        Glass.rimTint = accent & 0xFFFFFF;                 // rim light picks up your accent colour
        Glass.panel(ctx, 0, 0, W, H, 18, Glass.hudFill(0x70), 1f, 0.17f);
        Glass.rimTint = 0;
        if (st.windowSweep) Glass.sweep(ctx, 0, 0, W, H, 18, (LyricsHud.time() % 8f) / 8f);

        // header: brand mark, title, dividers
        Glass.panel(ctx, 16, 10, 14, 14, 7, accent, 0.9f, 0.3f);
        Glass.rrect(ctx, 22, 16, 6, 6, 3, st.colour2);
        ui.label("Surge Lyrics", 38, 13, TEXT);
        Glass.textRight(ctx, textRenderer, "K to reopen", W - 40, 13, FAINT);
        if (ui.button("close", W - 30, 8, 16, 16, "x", accent, false)) closing = true;
        lastTab = ui.segmented("tabs", 14, 30, W - 28, TABS, lastTab, accent);
        Glass.rrect(ctx, 16, 52, W - 32, 1, 0, (0x40 << 24) | (accent & 0xFFFFFF));

        boolean surgeChanged = false, sourceChanged = false, audioChanged = false;
        idField.visible = false;
        hexField.visible = false;
        final int lx = 16, cx = 134, cw = W - 14 - cx, rv = W - 16, rh = 22, tg = W - 44;
        int y = 60;

        switch (lastTab) {
            case 0 -> {
                tipRow(y, "Show or hide the lyrics on your HUD.");
                ui.label("Lyrics", lx, y + 3, TEXT);
                st.lyricsEnabled = ui.toggle("lyr", tg, y, st.lyricsEnabled, accent); y += rh;
                ui.label("Now-playing bar", lx, y + 3, TEXT);
                st.showBar = ui.toggle("bar", tg, y, st.showBar, accent); y += rh;
                tipRow(y, "Chunk: a few words at a time. Line: the whole line. Type: types it out. Sing: karaoke fill. Scroll: previous / current / next.");
                ui.label("Display", lx, y + 3, TEXT);
                st.displayMode = ui.segmented("mode", cx, y, cw, LyricsView.MODES, st.displayMode, accent); y += rh;
                tipRow(y, "How many words each chunk shows (Chunk display only).");
                ui.label("Words per line", lx, y + 3, st.displayMode == 0 ? TEXT : FAINT);
                st.wordsPerLine = Math.round(ui.slider("wpl", cx, y, cw - 44, st.wordsPerLine, 1, 8, 1, accent));
                Glass.textRight(ctx, textRenderer, st.displayMode == 0 ? String.valueOf(st.wordsPerLine) : "chunk mode", rv, y + 4, DIM); y += rh;
                tipRow(y, "How long a line stays before fading out when there is a long gap.");
                ui.label("Stay time", lx, y + 3, TEXT);
                st.staySeconds = ui.slider("stay", cx, y, cw - 44, st.staySeconds, 1, 10, 0.5f, accent);
                Glass.textRight(ctx, textRenderer, st.staySeconds + "s", rv, y + 4, DIM); y += rh;
                tipRow(y, "Shift the lyrics earlier (negative) or later (positive) if they feel off.");
                ui.label("Sync", lx, y + 3, TEXT);
                st.syncOffsetMs = Math.round(ui.slider("sync", cx, y, cw - 44, st.syncOffsetMs, -3000, 3000, 50, accent));
                Glass.textRight(ctx, textRenderer, (st.syncOffsetMs > 0 ? "+" : "") + st.syncOffsetMs + " ms", rv, y + 4, DIM);
            }
            case 1 -> {
                FontStyle[] fs = FontStyle.values();
                int chipW = (W - 32 - 18) / 4, start = fontPage * FontStyle.PER_PAGE;
                for (int j = 0; j < FontStyle.PER_PAGE; j++) {
                    int i = start + j;
                    if (i >= fs.length) break;
                    int cxp = lx + (j % 4) * (chipW + 6), cyp = y + (j / 4) * 22;
                    boolean have = Fonts.available(fs[i]);
                    if (ui.chip("font" + i, cxp, cyp, chipW, 18, have ? fs[i].label : fs[i].label + " (missing)", Fonts.idOf(fs[i]), st.font == i, accent)) st.font = i;
                }
                y += 2 * rh + 2;
                int pages = FontStyle.pages();
                if (ui.button("fp-", lx, y, 24, 16, "<", accent, false)) fontPage = (fontPage + pages - 1) % pages;
                Glass.textCentered(ctx, textRenderer, "Fonts  " + (fontPage + 1) + " / " + pages + "   (" + fs.length + " total)", W / 2, y + 4, DIM);
                if (ui.button("fp+", W - 16 - 24, y, 24, 16, ">", accent, false)) fontPage = (fontPage + 1) % pages;
                y += 24;
                ui.label("Size", lx, y + 3, TEXT);
                st.sizePercent = Math.round(ui.slider("size", cx, y, cw - 44, st.sizePercent, 50, 200, 5, accent));
                Glass.textRight(ctx, textRenderer, st.sizePercent + "%", rv, y + 4, DIM); y += rh;
                ui.label("Opacity", lx, y + 3, TEXT);
                st.opacity = Math.round(ui.slider("opac", cx, y, cw - 44, st.opacity, 25, 100, 5, accent));
                Glass.textRight(ctx, textRenderer, st.opacity + "%", rv, y + 4, DIM); y += rh;
                if (!Fonts.available(FontStyle.of(st.font)))
                    Glass.text(ctx, textRenderer, "This font's files are missing from the build, so the default font is used.", lx, y, 0xFFF87171);
                else if (Fonts.missing() > 0)
                    Glass.text(ctx, textRenderer, Fonts.missing() + " font(s) missing from this build (shown as 'missing'). See config/surgelyrics-errors.log.", lx, y, 0xFFFBBF24);
            }
            case 2 -> {
                st.anchor = anchorGrid(ctx, "anc", lx, y, st.anchor, st.offX, st.offY, 200, 22, accent);
                int rx = 150, sx = 226, sww = W - 16 - 56 - sx;
                ui.label("Horizontal", rx, y + 3, TEXT);
                st.offX = Math.round(ui.slider("ox", sx, y, sww, st.offX, -300, 300, 5, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(st.offX), rv, y + 4, DIM);
                ui.label("Vertical", rx, y + rh + 3, TEXT);
                st.offY = Math.round(ui.slider("oy", sx, y + rh, sww, st.offY, -300, 300, 5, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(st.offY), rv, y + rh + 4, DIM);
                if (ui.button("reset", rx, y + 2 * rh, 110, 18, "Reset position", accent, false)) { st.anchor = 4; st.offX = 0; st.offY = 30; }
                Glass.text(ctx, textRenderer, "Anchor: " + Layout.ANCHORS[st.anchor], rx, y + 2 * rh + 26, DIM);
                int by = y + 68 + 14;
                ui.label("Now-playing bar", lx, by + 3, TEXT);
                st.barBottom = ui.segmented("barpos", cx + 20, by, 190, BAR, st.barBottom ? 1 : 0, accent) == 1;
                Glass.text(ctx, textRenderer, "Click a cell to move the lyrics, then nudge with the sliders.", lx, by + 22, FAINT);
            }
            case 3 -> {
                List<Colors.Palette> ps = Colors.PALETTES;
                ui.label("Presets", lx, y + 4, TEXT);
                int pick = ui.palettes("pal", cx, y + 2, ps, st.colour, st.colour2);
                if (pick >= 0) { st.colour = ps.get(pick).c1(); st.colour2 = ps.get(pick).c2(); }
                y += rh;
                ui.label("Edit", lx, y + 3, TEXT);
                editSecond = ui.segmented("edit", cx, y, 150, EDIT, editSecond ? 1 : 0, accent) == 1;
                final boolean second = editSecond;
                int cur = second ? st.colour2 : st.colour;
                hexBind("lyr" + second, cur, c -> { if (second) st.colour2 = c; else st.colour = c; });
                hexPill(ctx, cx + 160, y, cw - 160, cur);
                y += rh;
                float[] hsb = Colors.toHsb(cur);
                int h0 = Math.round(hsb[0] * 360), s0 = Math.round(hsb[1] * 100), b0 = Math.round(hsb[2] * 100);
                ui.label("Hue", lx, y + 3, TEXT);
                ui.trackStops = HUE_STOPS;
                int h1 = Math.round(ui.slider("hue", cx, y, cw - 44, h0, 0, 360, 1, cur));
                Glass.textRight(ctx, textRenderer, h1 + "\u00b0", rv, y + 4, DIM); y += rh;
                ui.label("Saturation", lx, y + 3, TEXT);
                ui.trackStops = new int[]{Colors.fromHsb(h1 / 360f, 0f, 1f), Colors.fromHsb(h1 / 360f, 1f, 1f)};
                int s1 = Math.round(ui.slider("sat", cx, y, cw - 44, s0, 0, 100, 1, cur));
                Glass.textRight(ctx, textRenderer, s1 + "%", rv, y + 4, DIM); y += rh;
                ui.label("Brightness", lx, y + 3, TEXT);
                ui.trackStops = new int[]{0xFF000000, Colors.fromHsb(h1 / 360f, s1 / 100f, 1f)};
                int b1 = Math.round(ui.slider("bri", cx, y, cw - 44, b0, 0, 100, 1, cur));
                Glass.textRight(ctx, textRenderer, b1 + "%", rv, y + 4, DIM); y += rh;
                if (h1 != h0 || s1 != s0 || b1 != b0) {            // only write back when you actually moved a slider
                    int nc = Colors.fromHsb(h1 / 360f, s1 / 100f, b1 / 100f);
                    if (second) st.colour2 = nc; else st.colour = nc;
                }
                ui.label("Gradient", lx, y + 3, TEXT);
                st.gradientMode = ui.segmented("grad", cx, y, cw, Colors.GRADIENT_MODES, st.gradientMode, accent); y += rh;
                ui.label("Flow speed", lx, y + 3, st.gradientMode >= 2 ? TEXT : FAINT);
                st.gradientSpeed = Math.round(ui.slider("gspd", cx, y, cw - 44, st.gradientSpeed, 1, 10, 1, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(st.gradientSpeed), rv, y + 4, DIM);
            }
            case 4 -> {
                tipRow(y, "Flat, 3D (stacked layers) or Outline.");
                ui.label("Text depth", lx, y + 3, TEXT);
                st.depthStyle = ui.segmented("depth", cx, y, cw, DEPTH, st.depthStyle, accent); y += rh;
                boolean d3 = st.depthStyle == 1;
                ui.label("3D thickness", lx, y + 3, d3 ? TEXT : FAINT);
                st.depthAmount = Math.round(ui.slider("damt", cx, y, cw - 44, st.depthAmount, 1, 8, 1, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(st.depthAmount), rv, y + 4, DIM); y += rh;
                ui.label("Light angle", lx, y + 3, d3 ? TEXT : FAINT);
                st.lightAngle = Math.round(ui.slider("ang", cx, y, cw - 44, st.lightAngle, 0, 355, 5, accent));
                Glass.textRight(ctx, textRenderer, st.lightAngle + "\u00b0", rv, y + 4, DIM); y += rh;
                tipRow(y, "Soft glow behind the letters. Skipped automatically while Surge is easing.");
                ui.label("Glow", lx, y + 3, TEXT);
                st.glowStrength = Math.round(ui.slider("glow", cx, y, cw - 44, st.glowStrength, 0, 100, 5, accent));
                Glass.textRight(ctx, textRenderer, st.glowStrength == 0 ? "off" : st.glowStrength + "%", rv, y + 4, DIM); y += rh;
                tipRow(y, "How one line replaces the next.");
                ui.label("Transition", lx, y + 3, TEXT);
                st.transition = ui.segmented("trans", cx, y, cw, LyricTransitions.STYLES, st.transition, accent); y += rh;
                tipRow(y, "How long the transition takes.");
                ui.label("Duration", lx, y + 3, TEXT);
                st.transitionMs = Math.round(ui.slider("tms", cx, y, cw - 44, st.transitionMs, 150, 800, 10, accent));
                Glass.textRight(ctx, textRenderer, st.transitionMs + " ms", rv, y + 4, DIM);
            }
            case 5 -> {
                tipRow(y, "A card behind the lyrics: glass, dark, or a bar with an accent edge.");
                ui.label("Backdrop", lx, y + 3, TEXT);
                st.backdrop = ui.segmented("bkd", cx, y, cw, BACKDROP, st.backdrop, accent); y += rh;
                tipRow(y, "A dim upside-down copy of the line underneath.");
                ui.label("Reflection", lx, y + 3, TEXT);
                st.reflection = Math.round(ui.slider("refl", cx, y, cw - 44, st.reflection, 0, 60, 5, accent));
                Glass.textRight(ctx, textRenderer, st.reflection == 0 ? "off" : st.reflection + "%", rv, y + 4, DIM); y += rh;
                tipRow(y, "Each letter rides a wave. Letters are drawn one by one, so 3D layers and glow are off in this mode.");
                ui.label("Wave letters", lx, y + 3, TEXT);
                st.waveAmp = Math.round(ui.slider("wave", cx, y, cw - 44, st.waveAmp, 0, 6, 1, accent));
                Glass.textRight(ctx, textRenderer, st.waveAmp == 0 ? "off" : st.waveAmp + " px", rv, y + 4, DIM); y += rh;
                tipRow(y, "Twinkling glints around the lyrics.");
                ui.label("Sparkles", lx, y + 3, TEXT);
                st.sparkles = ui.toggle("spark", tg, y, st.sparkles, accent); y += rh;
                tipRow(y, "A thin line under the lyrics that fills as the line plays.");
                ui.label("Progress underline", lx, y + 3, TEXT);
                st.underline = ui.toggle("undl", tg, y, st.underline, accent); y += rh;
                ui.label("Screen-edge glow on beats", lx, y + 3, st.reactive ? TEXT : FAINT);
                st.edgeGlow = ui.toggle("edge", tg, y, st.edgeGlow, accent); y += rh;
                Glass.text(ctx, textRenderer, "Wave draws letters one by one, so it replaces 3D layers and glow. Edge glow needs Audio reactive.", lx, y, FAINT);
            }
            case 6 -> {
                HudWidget[] ws = st.widgets();
                int chipW = (W - 32 - 6 * 5) / 7;
                for (int i = 0; i < ws.length; i++)
                    if (ui.chip("hw" + i, lx + i * (chipW + 5), y, chipW, 18, WIDGETS[i], null, hudSel == i, accent)) hudSel = i;
                final HudWidget cfg = ws[hudSel];
                y += 26;
                int[] vs = switch (hudSel) { case 0 -> new int[]{60, 80}; case 1 -> new int[]{68, 70}; case 6 -> new int[]{190, 28}; default -> new int[]{74, 18}; };
                cfg.anchor = anchorGrid(ctx, "ha", lx, y, cfg.anchor, cfg.offX, cfg.offY, vs[0], vs[1], accent);
                int rx = 150, sx = 226, sww = W - 16 - 56 - sx;
                ui.label("Horizontal", rx, y + 3, TEXT);
                cfg.offX = Math.round(ui.slider("hox" + hudSel, sx, y, sww, cfg.offX, -300, 300, 2, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(cfg.offX), rv, y + 4, DIM);
                ui.label("Vertical", rx, y + rh + 3, TEXT);
                cfg.offY = Math.round(ui.slider("hoy" + hudSel, sx, y + rh, sww, cfg.offY, -300, 300, 2, accent));
                Glass.textRight(ctx, textRenderer, String.valueOf(cfg.offY), rv, y + rh + 4, DIM);
                ui.label("Scale", rx, y + 2 * rh + 3, TEXT);
                cfg.scale = Math.round(ui.slider("hsc" + hudSel, sx, y + 2 * rh, sww, cfg.scale, 50, 200, 5, accent));
                Glass.textRight(ctx, textRenderer, cfg.scale + "%", rv, y + 2 * rh + 4, DIM);
                y += 68 + 8;
                ui.label("Enabled", lx, y + 3, TEXT);
                cfg.on = ui.toggle("hon" + hudSel, lx + 56, y, cfg.on, accent);
                ui.label("Glass background", 150, y + 3, TEXT);
                cfg.bg = ui.toggle("hbg" + hudSel, tg, y, cfg.bg, accent);
                y += rh;
                ui.label("Colour", lx, y + 3, TEXT);
                hexBind("hud" + hudSel, cfg.color, c -> cfg.color = c);
                hexPill(ctx, lx + 56, y, 108, cfg.color);
                cfg.color = ui.swatches("hq" + hudSel, 178, y + 1, QUICK, cfg.color);
                y += rh;
                switch (hudSel) {
                    case 0 -> {
                        ui.label("Layout", lx, y + 3, TEXT);
                        st.armorVertical = ui.segmented("alay", cx, y, 130, ARMOR_LAYOUT, st.armorVertical ? 0 : 1, accent) == 0;
                        ui.label("Held items", 280, y + 3, TEXT);
                        st.armorHands = ui.toggle("ahand", tg, y, st.armorHands, accent); y += rh;
                        ui.label("Durability", lx, y + 3, TEXT);
                        st.armorDur = ui.segmented("adur", cx, y, cw, DURABILITY, st.armorDur, accent);
                    }
                    case 1 -> {
                        ui.label("Mouse", lx, y + 3, TEXT);
                        st.keysMouse = ui.toggle("kmouse", lx + 44, y, st.keysMouse, accent);
                        ui.label("Space bar", 110, y + 3, TEXT);
                        st.keysSpace = ui.toggle("kspace", 110 + 58, y, st.keysSpace, accent);
                        ui.label("CPS", 214, y + 3, TEXT);
                        st.keysCps = ui.toggle("kcps", 214 + 28, y, st.keysCps, accent);
                    }
                    case 2 -> {
                        ui.label("Colour by quality", lx, y + 3, TEXT);
                        st.pingColorize = ui.toggle("pcol", lx + 110, y, st.pingColorize, accent);
                    }
                    case 3 -> {
                        ui.label("Counts from", lx, y + 3, TEXT);
                        st.timerMode = ui.segmented("tmode", cx, y, 200, TIMER_MODE, st.timerMode, accent);
                    }
                    case 4 -> {
                        ui.label("Decimals", lx, y + 3, TEXT);
                        st.reachDecimals = Math.round(ui.slider("rdec", cx, y, cw - 44, st.reachDecimals, 1, 3, 1, accent));
                        Glass.textRight(ctx, textRenderer, String.valueOf(st.reachDecimals), rv, y + 4, DIM);
                    }
                    case 6 -> {
                        ui.label("Style", lx, y + 3, TEXT);
                        st.hotbarStyle = ui.segmented("hbs", cx, y, 200, HB_STYLE, st.hotbarStyle, accent);
                        ui.label("Numbers", 346, y + 3, TEXT);
                        st.hotbarNumbers = ui.toggle("hbn", tg, y, st.hotbarNumbers, accent); y += rh;
                        ui.label("Offhand", lx, y + 3, TEXT);
                        st.hotbarOffhand = ui.toggle("hbo", lx + 52, y, st.hotbarOffhand, accent);
                        ui.label("Glide", 130, y + 3, TEXT);
                        st.hotbarAnimate = ui.toggle("hba", 130 + 34, y, st.hotbarAnimate, accent);
                        Glass.text(ctx, textRenderer, "Enable replaces the vanilla hotbar. Colour = highlight.", 220, y + 5, FAINT);
                    }
                    default -> {
                        ui.label("Resets after", lx, y + 3, TEXT);
                        st.comboSeconds = Math.round(ui.slider("cmbs", cx, y, cw - 44, st.comboSeconds, 1, 10, 1, accent));
                        Glass.textRight(ctx, textRenderer, st.comboSeconds + "s idle", rv, y + 4, DIM);
                    }
                }
            }
            case 7 -> {
                int chipW = (W - 32 - (PVP_CHIPS.length - 1) * 4) / PVP_CHIPS.length;
                for (int i = 0; i < PVP_CHIPS.length; i++)
                    if (ui.chip("pv" + i, lx + i * (chipW + 4), y, chipW, 18, PVP_CHIPS[i], null, pvpSel == i, accent)) pvpSel = i;
                y += 26;
                if (pvpSel < 8) {
                    final HudWidget cfg = st.pvpWidgets()[pvpSel];
                    int[] vs = switch (pvpSel) { case 0 -> new int[]{120, 34}; case 1 -> new int[]{96, 60}; case 2 -> new int[]{80, 18}; case 3 -> new int[]{130, 24}; default -> new int[]{80, 18}; };
                    cfg.anchor = anchorGrid(ctx, "pa", lx, y, cfg.anchor, cfg.offX, cfg.offY, vs[0], vs[1], accent);
                    int rx = 150, sx = 226, sww = W - 16 - 56 - sx;
                    ui.label("Horizontal", rx, y + 3, TEXT);
                    cfg.offX = Math.round(ui.slider("pox" + pvpSel, sx, y, sww, cfg.offX, -300, 300, 2, accent));
                    Glass.textRight(ctx, textRenderer, String.valueOf(cfg.offX), rv, y + 4, DIM);
                    ui.label("Vertical", rx, y + rh + 3, TEXT);
                    cfg.offY = Math.round(ui.slider("poy" + pvpSel, sx, y + rh, sww, cfg.offY, -300, 300, 2, accent));
                    Glass.textRight(ctx, textRenderer, String.valueOf(cfg.offY), rv, y + rh + 4, DIM);
                    ui.label("Scale", rx, y + 2 * rh + 3, TEXT);
                    cfg.scale = Math.round(ui.slider("psc" + pvpSel, sx, y + 2 * rh, sww, cfg.scale, 50, 200, 5, accent));
                    Glass.textRight(ctx, textRenderer, cfg.scale + "%", rv, y + 2 * rh + 4, DIM);
                    y += 68 + 8;
                    ui.label("Enabled", lx, y + 3, TEXT);
                    cfg.on = ui.toggle("pon" + pvpSel, lx + 56, y, cfg.on, accent);
                    ui.label("Glass background", 150, y + 3, TEXT);
                    cfg.bg = ui.toggle("pbg" + pvpSel, tg, y, cfg.bg, accent);
                    y += rh;
                    ui.label("Colour", lx, y + 3, TEXT);
                    hexBind("pvp" + pvpSel, cfg.color, c -> cfg.color = c);
                    hexPill(ctx, lx + 56, y, 108, cfg.color);
                    cfg.color = ui.swatches("pq" + pvpSel, 178, y + 1, QUICK, cfg.color);
                    y += rh;
                    switch (pvpSel) {
                        case 0 -> {
                            ui.label("Show", lx, y + 3, TEXT);
                            st.targetSource = ui.segmented("tsrc", cx, y, 260, TARGET_SRC, st.targetSource, accent); y += rh;
                            ui.label("Armor", lx, y + 3, TEXT);
                            st.targetArmor = ui.toggle("tarm", lx + 44, y, st.targetArmor, accent);
                            ui.label("Distance", 110, y + 3, TEXT);
                            st.targetDistance = ui.toggle("tdist", 110 + 54, y, st.targetDistance, accent);
                            ui.label("Keep last hit", 230, y + 3, TEXT);
                            st.targetHoldSec = Math.round(ui.slider("thold", 310, y, 130, st.targetHoldSec, 2, 10, 1, accent));
                            Glass.textRight(ctx, textRenderer, st.targetHoldSec + "s", rv, y + 4, DIM);
                        }
                        case 1 -> {
                            ui.label("Show level", lx, y + 3, TEXT);
                            st.effectsLevel = ui.toggle("elvl", lx + 70, y, st.effectsLevel, accent);
                            ui.label("Blink under", 170, y + 3, TEXT);
                            st.effectsWarnSec = Math.round(ui.slider("ewarn", 244, y, 170, st.effectsWarnSec, 0, 30, 1, accent));
                            Glass.textRight(ctx, textRenderer, st.effectsWarnSec == 0 ? "off" : st.effectsWarnSec + "s", rv, y + 4, DIM);
                        }
                        case 2 -> {
                            ui.label("Totems", lx, y + 3, TEXT);
                            st.itemTotem = ui.toggle("itot", lx + 44, y, st.itemTotem, accent);
                            ui.label("Gapples", 110, y + 3, TEXT);
                            st.itemGapple = ui.toggle("igap", 110 + 46, y, st.itemGapple, accent);
                            ui.label("Pearls", 210, y + 3, TEXT);
                            st.itemPearl = ui.toggle("iprl", 210 + 38, y, st.itemPearl, accent);
                            ui.label("Crystals", 300, y + 3, TEXT);
                            st.itemCrystal = ui.toggle("icry", 300 + 48, y, st.itemCrystal, accent); y += rh;
                            ui.label("XP bottles", lx, y + 3, TEXT);
                            st.itemXp = ui.toggle("ixp", lx + 62, y, st.itemXp, accent);
                            ui.label("Arrows", 130, y + 3, TEXT);
                            st.itemArrow = ui.toggle("iarr", 130 + 40, y, st.itemArrow, accent);
                            ui.label("Hide zero", 226, y + 3, TEXT);
                            st.itemsHideZero = ui.toggle("izero", 226 + 54, y, st.itemsHideZero, accent);
                            ui.label("Vertical", 330, y + 3, TEXT);
                            st.itemsVertical = ui.toggle("ivert", 330 + 44, y, st.itemsVertical, accent);
                        }
                        case 3 -> {
                            ui.label("Decimals", lx, y + 3, TEXT);
                            st.coordsDecimals = Math.round(ui.slider("cdec", cx, y, 120, st.coordsDecimals, 0, 2, 1, accent));
                            Glass.text(ctx, textRenderer, String.valueOf(st.coordsDecimals), cx + 130, y + 4, DIM); y += rh;
                            ui.label("Facing", lx, y + 3, TEXT);
                            st.coordsFacing = ui.toggle("cface", lx + 40, y, st.coordsFacing, accent);
                            ui.label("Nether / Overworld line", 110, y + 3, TEXT);
                            st.coordsNether = ui.toggle("cneth", 110 + 118, y, st.coordsNether, accent);
                        }
                        case 4 -> {
                            ui.label("Show right click", lx, y + 3, TEXT);
                            st.cpsRight = ui.toggle("cpsr", lx + 90, y, st.cpsRight, accent);
                        }
                        case 5 -> {
                            ui.label("Show absorption", lx, y + 3, TEXT);
                            st.healthAbsorb = ui.toggle("habs", lx + 88, y, st.healthAbsorb, accent);
                            ui.label("Colour by health", 190, y + 3, TEXT);
                            st.healthColorize = ui.toggle("hcol", 190 + 90, y, st.healthColorize, accent);
                        }
                        case 6 -> {
                            ui.label("Decimals", lx, y + 3, TEXT);
                            st.speedDecimals = Math.round(ui.slider("sdec", cx, y, 120, st.speedDecimals, 0, 2, 1, accent));
                            Glass.text(ctx, textRenderer, String.valueOf(st.speedDecimals), cx + 130, y + 4, DIM);
                        }
                        default -> {
                            ui.label("Always show", lx, y + 3, TEXT);
                            st.stanceAlways = ui.toggle("stal", lx + 66, y, st.stanceAlways, accent);
                            Glass.text(ctx, textRenderer, "Off: only shows while sprinting or sneaking.", 150, y + 4, FAINT);
                        }
                    }
                } else if (pvpSel == 8) {
                    tipRow(y, "Red screen edges that pulse faster as your health drops.");
                    ui.label("Low health warning", lx, y + 3, TEXT);
                    st.lowHealthWarn = ui.toggle("lhw", tg, y, st.lowHealthWarn, accent); y += rh;
                    ui.label("Warn below", lx, y + 3, st.lowHealthWarn ? TEXT : FAINT);
                    st.lowHealthHp = Math.round(ui.slider("lhhp", cx, y, cw - 80, st.lowHealthHp, 2, 20, 1, accent));
                    Glass.textRight(ctx, textRenderer, Fmt.fixed(st.lowHealthHp / 2.0, 1) + " hearts", rv, y + 4, DIM); y += rh + 4;
                    tipRow(y, "Armor widget blinks red/white when a piece is nearly broken.");
                    ui.label("Low armor flash", lx, y + 3, TEXT);
                    st.lowArmorFlash = ui.toggle("law", tg, y, st.lowArmorFlash, accent); y += rh;
                    ui.label("Flash below", lx, y + 3, st.lowArmorFlash ? TEXT : FAINT);
                    st.armorWarnPct = Math.round(ui.slider("awp", cx, y, cw - 80, st.armorWarnPct, 5, 50, 5, accent));
                    Glass.textRight(ctx, textRenderer, st.armorWarnPct + "% durability", rv, y + 4, DIM); y += rh + 4;
                    tipRow(y, "Pulsing NO TOTEM under the crosshair while you are carrying none.");
                    ui.label("No-totem alert", lx, y + 3, TEXT);
                    st.lowTotemWarn = ui.toggle("ltw", tg, y, st.lowTotemWarn, accent);
                } else {
                    tipRow(y, "Hold the zoom key to narrow your field of view. It does not touch combat or aim.");
                    ui.label("Hold-to-zoom", lx, y + 3, TEXT);
                    st.zoom = ui.toggle("zoom", tg, y, st.zoom, accent); y += rh;
                    ui.label("Zoom strength", lx, y + 3, st.zoom ? TEXT : FAINT);
                    st.zoomFactor = Math.round(ui.slider("zf", cx, y, cw - 80, st.zoomFactor, 2, 10, 1, accent));
                    Glass.textRight(ctx, textRenderer, "x" + st.zoomFactor, rv, y + 4, DIM); y += rh + 8;
                    Glass.text(ctx, textRenderer, Glass.fit(textRenderer, "Zoom key: hold C (change it under Controls).", W - 32), lx, y, FAINT);
                    Glass.text(ctx, textRenderer, Glass.fit(textRenderer, "Everything in this tab only shows information you already have: no hitbox, reach or aim changes.", W - 32), lx, y + 12, FAINT);
                }
            }
            case 8 -> {
                tipRow(y, "Makes the HUD react to your system's output volume. Windows only.");
                ui.label("Audio reactive", lx, y + 3, TEXT);
                boolean rc = ui.toggle("react", tg, y, st.reactive, accent);
                if (rc != st.reactive) { st.reactive = rc; audioChanged = true; }
                y += rh;
                ui.label("Strength", lx, y + 3, st.reactive ? TEXT : FAINT);
                st.reactStrength = Math.round(ui.slider("rstr", cx, y, cw - 44, st.reactStrength, 0, 100, 5, accent));
                Glass.textRight(ctx, textRenderer, st.reactStrength + "%", rv, y + 4, DIM); y += rh;
                ui.label("Lyrics pulse and glow", lx, y + 3, st.reactive ? TEXT : FAINT);
                st.reactLyrics = ui.toggle("rly", tg, y, st.reactLyrics, accent); y += rh;
                ui.label("Visualizer bars (now-playing bar)", lx, y + 3, st.reactive ? TEXT : FAINT);
                st.reactBars = ui.toggle("rbar", tg, y, st.reactBars, accent); y += rh;
                ui.label("Widget glass glow", lx, y + 3, st.reactive ? TEXT : FAINT);
                st.reactWidgets = ui.toggle("rwid", tg, y, st.reactWidgets, accent); y += rh + 6;
                ui.label("Live level", lx, y + 1, TEXT);
                float lv = Runtime.REACT.level(), bt = Runtime.REACT.beat();
                Glass.panel(ctx, cx, y, cw - 30, 10, 5, 0x34000000, 0.4f, 0f);
                int fw = Math.max(0, Math.round((cw - 34) * LyricsView.clamp01(lv)));
                if (fw > 1) Glass.rrect(ctx, cx + 2, y + 2, fw, 6, 3, LyricsView.mix(st.colour, st.colour2, lv));
                Glass.panel(ctx, cx + cw - 20, y, 10, 10, 5, LyricsView.mix(0x30FFFFFF, (0xFF << 24) | (st.colour & 0xFFFFFF), bt), 0.7f, 0.2f);
                y += rh;
                Glass.text(ctx, textRenderer, "Meter: " + Runtime.AUDIO.status(), lx, y, FAINT);
                Glass.text(ctx, textRenderer, "Reacts to your system's output volume, so music from any app drives it. Windows only.", lx, y + 12, FAINT);
            }
            case 9 -> {
                packPage = ui.segmented("ppage", lx, y, 150, PACK_PAGES, packPage, accent);
                if (packPage == 0) {
                    Glass.text(ctx, textRenderer, "Pick a pack to set font, colours, effects and window theme in one click.", lx + 162, y + 4, FAINT);
                    y += 24;
                    packKind = ui.segmented("pkind", lx, y, 190, PACK_KINDS, packKind, accent);
                    int[] list = packKind == 0 ? Presets.looks() : Presets.pvps();
                    Glass.text(ctx, textRenderer, packKind == 0 ? "Look packs never touch your PvP widgets." : "Loadouts switch PvP widgets and alerts, and keep lyrics small.", lx + 202, y + 4, FAINT);
                    y += 24;
                    int cols = 4, gw = (W - 32 - (cols - 1) * 6) / cols;
                    for (int j = 0; j < list.length; j++) {
                        int pi = list[j], gx = lx + (j % cols) * (gw + 6), gy = y + (j / cols) * 22;
                        ui.tip(gx, gy, gw, 18, Presets.NAMES[pi] + ": " + Presets.DESC[pi]);
                        if (ui.chip("pack" + pi, gx, gy, gw, 18, "    " + Presets.NAMES[pi], null, lastPack == pi, accent)) { Presets.apply(st, pi); lastPack = pi; surgeChanged = true; }
                        int[] sw2 = Presets.swatch(pi);
                        Glass.rrect(ctx, gx + 8, gy + 5, 8, 8, 4, sw2[0]);
                        Glass.rrect(ctx, gx + 13, gy + 8, 5, 5, 2, sw2[1]);
                    }
                } else {
                    int chipW = (W - 32 - 5 * 5) / 6;
                    y += 24;
                    for (int i = 0; i < Themes.ALL.length; i++) {
                        int tx = lx + (i % 6) * (chipW + 5), ty = y + (i / 6) * 22;
                        if (ui.chip("th" + i, tx, ty, chipW, 18, "   " + Themes.ALL[i].name(), null, st.uiTheme == i, accent)) st.uiTheme = i;
                        Glass.rrect(ctx, tx + 7, ty + 5, 8, 8, 4, 0xFF000000 | Themes.ALL[i].body());
                        Glass.rrect(ctx, tx + 8, ty + 6, 6, 6, 3, 0x40FFFFFF);
                    }
                    y += 2 * 22 + 4;
                    tipRow(y, "How see-through the window, HUD panels, widgets and hotbar are. Theme and opacity apply to all of them.");
                    ui.label("Glass opacity", lx, y + 3, TEXT);
                    st.glassOpacity = Math.round(ui.slider("gop", cx, y, cw - 44, st.glassOpacity, 30, 100, 5, accent));
                    Glass.textRight(ctx, textRenderer, st.glassOpacity + "%", rv, y + 4, DIM); y += rh;
                    tipRow(y, "Soft colour clouds drifting behind this window.");
                    ui.label("Window glow", lx, y + 3, TEXT);
                    st.windowGlow = ui.toggle("wglow", tg, y, st.windowGlow, accent); y += rh;
                    ui.label("Light sweep", lx, y + 3, TEXT);
                    st.windowSweep = ui.toggle("wsweep", tg, y, st.windowSweep, accent); y += rh;
                    ui.label("3D settings window", lx, y + 3, TEXT);
                    st.window3d = ui.toggle("w3d", tg, y, st.window3d, accent); y += rh;
                    ui.label("Lyric bob motion", lx, y + 3, TEXT);
                    st.intensity = ui.segmented("motion", cx, y, cw, MOTION, st.intensity, accent);
                }
            }
            case 10 -> {
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
                y += 2 * rh + 2;
                final int rs = 20;
                tipRow(y, "Tightens culling when FPS or the 1% low is poor, relaxes it when there is headroom.");
                ui.label("Adaptive easing", lx, y + 3, TEXT);
                boolean ad = ui.toggle("adapt", tg, y, st.surgeAdaptive, accent);
                if (ad != st.surgeAdaptive) { st.surgeAdaptive = ad; surgeChanged = true; }
                y += rs;
                tipRow(y, "The frame rate adaptive easing aims for.");
                ui.label("Target FPS", lx, y + 3, st.surgeAdaptive ? TEXT : FAINT);
                int tf = Math.round(ui.slider("tfps", cx, y, cw - 44, st.targetFps, 30, 240, 10, accent));
                if (tf != st.targetFps) { st.targetFps = tf; surgeChanged = true; }
                Glass.textRight(ctx, textRenderer, String.valueOf(st.targetFps), rv, y + 4, DIM); y += rs;
                tipRow(y, "Caps FPS while the game window is not focused. 0 = off.");
                ui.label("Background FPS", lx, y + 3, TEXT);
                st.backgroundFps = Math.round(ui.slider("bgfps", cx, y, cw - 44, st.backgroundFps, 0, 60, 5, accent));
                Glass.textRight(ctx, textRenderer, st.backgroundFps == 0 ? "off" : String.valueOf(st.backgroundFps), rv, y + 4, DIM); y += rs;
                tipRow(y, "Skips glow, 3D layers and effects while Surge is easing.");
                ui.label("Lighten lyrics when slow", lx, y + 3, TEXT);
                boolean lt = ui.toggle("lite", tg, y, st.lightenLyrics, accent);
                if (lt != st.lightenLyrics) { st.lightenLyrics = lt; surgeChanged = true; }
                y += rs;
                ui.label("FPS overlay", lx, y + 3, TEXT);
                st.surgeOverlay = ui.toggle("ovl", tg, y, st.surgeOverlay, accent); y += rs;
                tipRow(y, "Skips drawing mobs and chests that are completely hidden by solid blocks.");
                ui.label("Hide behind walls", lx, y + 3, TEXT);
                ui.label("Mobs", 150, y + 3, DIM);
                st.occludeMobs = ui.toggle("occm", 150 + 32, y, st.occludeMobs, accent);
                ui.label("Chests and signs", 250, y + 3, DIM);
                st.occludeBlocks = ui.toggle("occb", tg, y, st.occludeBlocks, accent);
                y += rs + 2;
                SurgeLimits lim = Runtime.SURGE.limits();
                String d = !Runtime.SURGE.active() ? "Nothing is culled."
                        : "Mobs past " + (int) (lim.mob() * Runtime.SURGE.scale()) + " blocks, items past " + (int) (lim.item() * Runtime.SURGE.scale())
                        + ", particles capped at " + Runtime.SURGE.particleBudget() + ".";
                Glass.text(ctx, textRenderer, Glass.fit(textRenderer, d, W - 32), lx, y, DIM);
                Glass.text(ctx, textRenderer, "Live: " + LyricsHud.lastFps + " fps, 1% low " + LyricsHud.lastLow
                        + "   ranges at " + Math.round(Runtime.SURGE.scale() * 100) + "%", lx, y + 12, FAINT);
            }
            case 11 -> {
                final int r2 = 20;
                SurgeProfile curP = SurgeProfile.byName(st.surgeProfile);
                tipRow(y, "Distances in blocks. 0 means never hide. Moving a slider switches to Custom.");
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
                ui.label("Hide weather", 230, y + 3, TEXT);
                boolean cw2 = ui.toggle("cwx", tg, y, st.cWeather, accent);
                if (cc != st.cClouds || cw2 != st.cWeather) { st.cClouds = cc; st.cWeather = cw2; moved = true; }
                if (moved) { st.surgeProfile = SurgeProfile.CUSTOM.name(); surgeChanged = true; }
                y += r2 + 6;
                Glass.text(ctx, textRenderer, "Active: " + Runtime.SURGE.profile().label + ".  0 = never hide.  Moving a slider switches to Custom.", lx, y, FAINT);
                Glass.text(ctx, textRenderer, "Never hidden: you, other players, your pets, your mount, bosses, glowing mobs.", lx, y + 11, FAINT);
            }
            default -> {
                tipRow(y, "Where now-playing comes from: Windows media, the Spotify API, or whichever is playing.");
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

        // live preview, in the real renderer, for every tab that changes how lyrics look; the two sample lines
        // swap every few seconds so the chosen transition is always visible
        if (lastTab <= 1 || lastTab == 3 || lastTab == 4 || lastTab == 5 || (lastTab == 9 && packPage == 0)) {
            int py = H - 46;
            Glass.rrect(ctx, 16, py - 6, W - 32, 1, 0, 0x1EFFFFFF);
            Glass.panel(ctx, 14, py, W - 28, 34, 12, 0x2A000000, 0.5f, 0.12f);
            String[] samples = {"Glass lyrics look like this", "and they move like this"};
            float t = LyricsHud.time();
            int idx = (int) (t / 2.8f) % 2;
            if (idx != pvIdx) { pvPrev = samples[pvIdx]; pvIdx = idx; pvStart = System.nanoTime(); }
            float tp = LyricsView.clamp01((System.nanoTime() - pvStart) / (st.transitionMs * 1_000_000f));
            float fillAnim = (t % 2.8f) / 2.8f;
            LyricsView.View pv = new LyricsView.View(samples[pvIdx], 1f, "and the next line fades in", 0f, "the line before", fillAnim, 1f, pvIdx);
            float sc = Math.min(1.2f * st.sizePercent / 100f, 1.35f);
            float cyp = py + (st.displayMode == 4 ? 17 : 14);
            LyricsHud.previewMode = true;
            LyricsHud.drawWithTransition(ctx, textRenderer, pv, st, pvPrev, 1f, tp, W / 2f, cyp,
                    st.displayMode == 4 ? Math.min(sc, 0.9f) : sc, t, W - 56f, 0f);
            LyricsHud.previewMode = false;
        }

        String dis = Safe.summary();
        if (ui.tip != null) Glass.text(ctx, textRenderer, Glass.fit(textRenderer, ui.tip, W - 32), 16, H - 11, 0xFFB6C0D6);
        else if (!dis.isEmpty())
            Glass.text(ctx, textRenderer, Glass.fit(textRenderer, "Switched off after an error: " + dis + "  (see config/surgelyrics-errors.log)", W - 32), 16, H - 11, 0xFFF87171);

        m.popMatrix();

        if (surgeChanged) Runtime.applySurge();
        if (sourceChanged) Runtime.NOW.sync();
        if (audioChanged) Runtime.syncAudio();
        if (idField.visible) idField.render(ctx, mouseX, mouseY, delta);
        if (hexField.visible) hexField.render(ctx, mouseX, mouseY, delta);
        Glass.alpha = 1f;
    }

    /** Registers the whole row at y as hover area for a help line. */
    private void tipRow(int y, String text) { ui.tip(16, y, W - 32, 20, text); }

    /** Soft glow cloud: three stacked translucent discs. */
    private void blob(DrawContext ctx, float cx, float cy, int r, int colour) {
        for (int i = 0; i < 3; i++) {
            int rr = r - i * 22;
            if (rr < 8) break;
            Glass.rrect(ctx, Math.round(cx - rr), Math.round(cy - rr), rr * 2, rr * 2, rr, (0x0E << 24) | (colour & 0xFFFFFF));
        }
    }

    /** Mini screen that doubles as a 3x3 anchor picker; shows where the box lands with the current offsets. */
    private int anchorGrid(DrawContext ctx, String id, int x, int y, int anchor, int offX, int offY, int vw, int vh, int accent) {
        final int mw = 120, mh = 68, cw3 = mw / 3, ch3 = mh / 3;
        Glass.panel(ctx, x, y, mw, mh, 8, 0x30000000, 0.6f, 0.1f);
        for (int a = 0; a < 9; a++) {
            int gx = x + (a % 3) * cw3, gy = y + (a / 3) * ch3;
            boolean hov = ui.hover(gx, gy, cw3, ch3), sel = anchor == a;
            float sp = ui.anim(id + a, sel ? 1 : 0, sel ? 1 : 0);
            if (sp > 0.02f || hov)
                Glass.panel(ctx, gx + 2, gy + 2, cw3 - 4, ch3 - 4, 5,
                        LyricsView.mix(hov ? 0x26FFFFFF : 0x00FFFFFF, (0x70 << 24) | (Runtime.SETTINGS.colour & 0xFFFFFF), LyricsView.clamp01(sp)),
                        sel ? 0.9f : 0.4f, 0.2f);
            if (hov && ui.pressed) anchor = a;
        }
        int[] pp = Layout.place(anchor, offX, offY, 427, 240, vw, vh);
        int pw = Math.max(4, Math.round(vw * mw / 427f)), ph = Math.max(3, Math.round(vh * mh / 240f));
        int px = x + Math.round(pp[0] * mw / 427f) - pw / 2, py = y + Math.round(pp[1] * mh / 240f) - ph / 2;
        Glass.rrect(ctx, px, py, pw, ph, Math.min(2, ph / 2), 0xFFFFFFFF);
        if (pw > 2 && ph > 2) Glass.rrect(ctx, px + 1, py + 1, pw - 2, ph - 2, Math.min(1, ph / 2), Runtime.SETTINGS.colour);
        return anchor;
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
