package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.core.SurgeGovernor;
import dev.surgelyrics.mc1_21_11.Runtime;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderManagerMixin {
    // VERIFY: method name ("get") against yarn for 1.21.11. Returning null = "no renderer", which vanilla already handles.
    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void surgelyrics$cull(BlockEntity be, CallbackInfoReturnable cir) {
        SurgeGovernor g = Runtime.SURGE;
        if (!g.active() || be instanceof BeaconBlockEntity || be instanceof EndGatewayBlockEntity) return;
        var me = MinecraftClient.getInstance().player;
        if (me == null) return;
        BlockPos p = be.getPos();
        if (g.cullBlockEntity(me.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)))
            cir.setReturnValue(null);
    }
}
