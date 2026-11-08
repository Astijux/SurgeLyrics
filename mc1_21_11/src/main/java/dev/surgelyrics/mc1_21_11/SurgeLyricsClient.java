package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.SurgeProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SurgeLyricsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Runtime.applySurge();

        // VERIFY: 1.21.9+ key bindings take a KeyBinding.Category (Surge 1.0.0 used the 3-arg constructor).
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("surgelyrics", "main"));
        KeyBinding settingsKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.surgelyrics.settings", GLFW.GLFW_KEY_K, category));
        KeyBinding profileKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.surgelyrics.profile", GLFW.GLFW_KEY_UNKNOWN, category));

        // VERIFY: HudElementRegistry method name (addLast) in the Fabric API build you pick.
        HudElementRegistry.addLast(Identifier.of("surgelyrics", "hud"), new LyricsHud());

        // Custom hotbar: replaces the vanilla one only while enabled (and never in spectator mode).
        // VERIFY: HudElementRegistry.replaceElement / VanillaHudElements.HOTBAR in the Fabric API build you pick.
        CustomHotbar hotbar = new CustomHotbar();
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, original -> (ctx, tickCounter) -> {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (Runtime.SETTINGS.hotbar.on && mc.player != null && !mc.player.isSpectator()) {
                Glass.fast = true;
                try { hotbar.render(ctx, mc, mc.textRenderer, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight()); }
                finally { Glass.fast = false; }
            } else {
                original.render(ctx, tickCounter);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (settingsKey.wasPressed()) mc.setScreen(new SettingsScreen(mc.currentScreen));
            while (profileKey.wasPressed()) {
                SurgeProfile next = SurgeProfile.byName(Runtime.SETTINGS.surgeProfile).next();
                Runtime.SETTINGS.surgeProfile = next.name();
                Runtime.applySurge();
                Runtime.save();
                if (mc.player != null) mc.player.sendMessage(Text.literal("Surge: " + next.label), true);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> { Runtime.SPOTIFY.stop(); Runtime.WINDOWS.stop(); Runtime.AUDIO.stop(); Runtime.save(); });
        Runtime.SPOTIFY.start();   // cheap no-op polling until you connect
        Runtime.NOW.sync();        // starts Windows media reading when the Source setting allows it
        Runtime.syncAudio();       // starts the audio meter only if audio-reactive is on
    }
}
