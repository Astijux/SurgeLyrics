package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.core.SurgeGovernor;
import dev.surgelyrics.mc1_21_11.Runtime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Unique private int surgelyrics$spawned;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void surgelyrics$reset(CallbackInfo ci) { surgelyrics$spawned = 0; }

    // Distance cull: skip particles that would spawn far from you. VERIFY the method name/descriptor in yarn 1.21.11.
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void surgelyrics$far(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz,
                                 CallbackInfoReturnable<Particle> cir) {
        SurgeGovernor g = Runtime.SURGE;
        if (!g.active()) return;
        var me = MinecraftClient.getInstance().player;
        if (me != null && g.cullParticle(me.squaredDistanceTo(x, y, z))) cir.setReturnValue(null);
    }

    // Per-tick cap (Surge 1.0.0 hooked the same method, method_3058 = addParticle(Particle)).
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void surgelyrics$cap(Particle particle, CallbackInfo ci) {
        int budget = Runtime.SURGE.particleBudget();
        if (budget > 0 && ++surgelyrics$spawned > budget) { Runtime.SURGE.countParticleCapped(); ci.cancel(); }
    }
}
