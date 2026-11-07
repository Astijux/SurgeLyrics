package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.mc1_21_11.Runtime;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public class CloudRendererMixin {
    // VERIFY: Surge hooked intermediary method_62168; confirm its yarn name (renderClouds?) and that it isn't overloaded.
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void surgelyrics$clouds(CallbackInfo ci) { if (Runtime.SURGE.hideClouds()) ci.cancel(); }
}
