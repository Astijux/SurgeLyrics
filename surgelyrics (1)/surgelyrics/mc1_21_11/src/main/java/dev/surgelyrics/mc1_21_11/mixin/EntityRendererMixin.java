package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.mc1_21_11.Runtime;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity> {
    // VERIFY: Surge hooked renderLabelIfPresent; hasLabel is the simpler gate if it still exists in 1.21.11.
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true, require = 0)
    private void surgelyrics$label(T entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        if (Runtime.SURGE.cullNametag(squaredDistanceToCamera)) cir.setReturnValue(false);
    }
}
