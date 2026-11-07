package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.mc1_21_11.Runtime;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Unique private int surgelyrics$spawned;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void surgelyrics$reset(CallbackInfo ci) { surgelyrics$spawned = 0; }

    // Surge 1.0.0 hooked the same method (method_3058 = addParticle(Particle)).
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void surgelyrics$cap(Particle particle, CallbackInfo ci) {
        int budget = Runtime.SURGE.particleBudget();
        if (budget > 0 && ++surgelyrics$spawned > budget) ci.cancel();
    }
}
