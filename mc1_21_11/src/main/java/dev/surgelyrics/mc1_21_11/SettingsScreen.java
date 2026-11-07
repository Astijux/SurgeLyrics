package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.function.Supplier;

/** Plain, two-column settings screen built from vanilla widgets (stays correct across GUI scales). */
public final class SettingsScreen extends Screen {
    private static final int[] COLOURS = {0xFF7DD3FC, 0xFFC084FC, 0xFFF9A8D4, 0xFFFDE68A, 0xFF86EFAC, 0xFFFFFFFF};
    private static final String[] COLOUR_NAMES = {"Sky", "Violet", "Pink", "Amber", "Mint", "White"};
    private static final String[] INTENSITY = {"Calm", "Normal", "Lively"};
    private static final int[] FPS = {30, 60, 90, 120, 144, 165, 240};

    private final Screen parent;
    private TextFieldWidget idField;
    private ButtonWidget connectButton;

    public SettingsScreen(Screen parent) { super(Text.literal("Surge Lyrics")); this.parent = parent; }

    @Override
    protected void init() {
        Settings s = Runtime.SETTINGS;
        int colW = 150, gap = 10, left = width / 2 - colW - gap / 2, right = width / 2 + gap / 2, y0 = 40, row = 24;
        int y = y0;

        toggle(left, y, colW, "Lyrics", () -> s.lyricsEnabled, v -> s.lyricsEnabled = v); y += row;
        toggle(left, y, colW, "Now-playing bar", () -> s.showBar, v -> s.showBar = v); y += row;
        toggle(left, y, colW, "Glow", () -> s.glow, v -> s.glow = v); y += row;
        toggle(left, y, colW, "Gradient", () -> s.gradient, v -> s.gradient = v); y += row;
        cycle(left, y, colW, "Motion", () -> INTENSITY[s.intensity], () -> s.intensity = (s.intensity + 1) % 3); y += row;
        cycle(left, y, colW, "Words per line", () -> String.valueOf(s.wordsPerLine), () -> s.wordsPerLine = s.wordsPerLine % 8 + 1); y += row;
        cycle(left, y, colW, "Stay", () -> s.staySeconds + "s", () -> s.staySeconds = s.staySeconds >= 10 ? 1 : s.staySeconds + 0.5f); y += row;
        cycle(left, y, colW, "Size", () -> s.sizePercent + "%", () -> s.sizePercent = s.sizePercent >= 200 ? 50 : s.sizePercent + 25); y += row;
        cycle(left, y, colW, "Sync", () -> (s.syncOffsetMs > 0 ? "+" : "") + s.syncOffsetMs + " ms",
                () -> s.syncOffsetMs = s.syncOffsetMs >= 3000 ? -3000 : s.syncOffsetMs + 100); y += row;
        cycle(left, y, colW, "Colour", () -> colourName(s.colour), () -> s.colour = next(s.colour)); y += row;
        cycle(left, y, colW, "Colour 2", () -> colourName(s.colour2), () -> s.colour2 = next(s.colour2));

        int ry = y0;
        cycle(right, ry, colW, "Surge", () -> Runtime.SURGE.profile().label, () -> {
            s.surgeProfile = SurgeProfile.byName(s.surgeProfile).next().name(); Runtime.applySurge(); }); ry += row;
        toggle(right, ry, colW, "Adaptive easing", () -> s.surgeAdaptive, v -> { s.surgeAdaptive = v; Runtime.applySurge(); }); ry += row;
        toggle(right, ry, colW, "FPS overlay", () -> s.surgeOverlay, v -> s.surgeOverlay = v); ry += row;
        cycle(right, ry, colW, "Target FPS", () -> String.valueOf(s.targetFps), () -> {
            int i = 0; while (i < FPS.length - 1 && FPS[i] <= s.targetFps) i++;
            s.targetFps = FPS[s.targetFps >= FPS[FPS.length - 1] ? 0 : i]; Runtime.applySurge(); }); ry += row + 8;

        idField = new TextFieldWidget(textRenderer, right, ry, colW, 20, Text.literal("Spotify Client ID"));
        idField.setMaxLength(64);
        idField.setText(s.spotifyClientId);
        idField.setPlaceholder(Text.literal("Spotify Client ID"));
        idField.setChangedListener(t -> s.spotifyClientId = t.strip());
        addDrawableChild(idField); ry += row;

        connectButton = addDrawableChild(ButtonWidget.builder(connectLabel(), b -> {
            if (Runtime.SPOTIFY.connected()) Runtime.SPOTIFY.logout();
            else Runtime.SPOTIFY.startLogin(url -> Util.getOperatingSystem().open(URI.create(url)));
            b.setMessage(connectLabel());
        }).dimensions(right, ry, colW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
    }

    private Text connectLabel() { return Text.literal(Runtime.SPOTIFY.connected() ? "Disconnect Spotify" : "Connect Spotify"); }

    private void toggle(int x, int y, int w, String label, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        Supplier<Text> text = () -> Text.literal(label + ": " + (get.getAsBoolean() ? "On" : "Off"));
        addDrawableChild(ButtonWidget.builder(text.get(), b -> { set.accept(!get.getAsBoolean()); b.setMessage(text.get()); })
                .dimensions(x, y, w, 20).build());
    }

    private void cycle(int x, int y, int w, String label, Supplier<String> value, Runnable advance) {
        Supplier<Text> text = () -> Text.literal(label + ": " + value.get());
        addDrawableChild(ButtonWidget.builder(text.get(), b -> { advance.run(); b.setMessage(text.get()); })
                .dimensions(x, y, w, 20).build());
    }

    private static int next(int c) {
        for (int i = 0; i < COLOURS.length; i++) if (COLOURS[i] == c) return COLOURS[(i + 1) % COLOURS.length];
        return COLOURS[0];
    }
    private static String colourName(int c) {
        for (int i = 0; i < COLOURS.length; i++) if (COLOURS[i] == c) return COLOUR_NAMES[i];
        return "Custom";
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "Surge Lyrics", width / 2, 12, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Lyrics", width / 2 - 160, 28, 0xFFA0A0B0);
        ctx.drawTextWithShadow(textRenderer, "Performance and Spotify", width / 2 + 5, 28, 0xFFA0A0B0);
        if (connectButton != null) connectButton.setMessage(connectLabel());
        String st = Runtime.SPOTIFY.status();
        ctx.drawTextWithShadow(textRenderer, st, width / 2 + 5, height - 52, 0xFFA0A0B0);
        ctx.drawTextWithShadow(textRenderer, "Redirect URI: " + Runtime.SPOTIFY.redirectUri(), width / 2 + 5, height - 42, 0xFF707080);
    }

    @Override public void close() { Runtime.save(); client.setScreen(parent); }
}
