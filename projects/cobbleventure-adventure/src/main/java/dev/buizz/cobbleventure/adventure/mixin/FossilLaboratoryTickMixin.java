package dev.buizz.cobbleventure.adventure.mixin;

import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure;
import dev.buizz.cobbleventure.adventure.fossil.FossilLaboratoryService;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FossilMultiblockStructure.class, remap = false)
abstract class FossilLaboratoryTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void cobbleventure$accelerate(Level level, CallbackInfo ci) {
        FossilLaboratoryService.beforeTick((FossilMultiblockStructure)(Object)this, level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cobbleventure$syncProgress(Level level, CallbackInfo ci) {
        FossilLaboratoryService.afterTick((FossilMultiblockStructure)(Object)this, level);
    }
}
