package dev.buizz.cobbleventure.adventure.mixin;

import dev.buizz.cobbleventure.playermenu.BattleRingAccess;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends only equipment possession; Showdown still enforces configuration and battle rules. */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.utils.AccessoriesUtils", remap = false)
public abstract class MegaShowdownStoredRingsMixin {
    @Inject(method = "checkTagInAccessories", at = @At("RETURN"), cancellable = true, require = 1)
    private static void cobbleventure$allowStoredRings(
        LivingEntity entity, TagKey<Item> tag, CallbackInfoReturnable<Boolean> callback
    ) {
        if (!callback.getReturnValueZ() && BattleRingAccess.hasStoredRing(entity, tag)) {
            callback.setReturnValue(true);
        }
    }
}
