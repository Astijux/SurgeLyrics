package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.mc1_21_11.Runtime;
import net.minecraft.client.render.WeatherRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherRendering.class)
public class WeatherRenderingMixin {
    // VERIFY: Surge hooked intermediary method_62320; confirm its yarn name (renderPrecipitation?).
    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true, require = 0)
    private void surgelyrics$weather(CallbackInfo ci) { if (Runtime.SURGE.hideWeather()) ci.cancel(); }
}
