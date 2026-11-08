package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.mc1_21_11.Runtime;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    // Background FPS cap: when the window isn't focused, render far fewer frames.
    // VERIFY: getFramerateLimit exists in yarn 1.21.11 (private int getFramerateLimit()).
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void surgelyrics$background(CallbackInfoReturnable<Integer> cir) {
        int cap = Runtime.SETTINGS.backgroundFps;
        if (cap > 0 && !MinecraftClient.getInstance().isWindowFocused()) cir.setReturnValue(Math.min(cir.getReturnValue(), cap));
    }
}
