package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.core.Settings;
import dev.surgelyrics.core.Zoom;
import dev.surgelyrics.mc1_21_11.Runtime;
import dev.surgelyrics.mc1_21_11.Safe;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    // Hold-to-zoom. Only the world FOV is touched (changingFov), never the hand. VERIFY: getFov(Camera, float, boolean) in yarn 1.21.11.
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void surgelyrics$zoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (!changingFov) return;
        Safe.run("zoom", () -> {
            Settings s = Runtime.SETTINGS;
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - Runtime.zoomLastNanos) / 1e9f);
            Runtime.zoomLastNanos = now;
            float amount = Runtime.ZOOM.step(dt, (s.zoom && Runtime.zoomHeld) ? 1f : 0f);
            if (amount > 0.001f) cir.setReturnValue(Zoom.fov(cir.getReturnValue(), amount, s.zoomFactor));
        });
    }
}
