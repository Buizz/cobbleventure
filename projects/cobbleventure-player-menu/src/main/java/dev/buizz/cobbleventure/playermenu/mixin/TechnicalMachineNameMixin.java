package dev.buizz.cobbleventure.playermenu.mixin;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.item.interactive.TechnicalMachineItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exposes the native TM variant in every client list that displays a stack name. */
@Mixin(ItemStack.class)
abstract class TechnicalMachineNameMixin {
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void cobbleventure$includeMachineMove(CallbackInfoReturnable<Component> callback) {
        ItemStack stack = (ItemStack)(Object)this;
        if (!(stack.getItem() instanceof TechnicalMachineItem)
            || stack.has(DataComponents.CUSTOM_NAME) || stack.has(DataComponents.ITEM_NAME)) return;
        var tm = stack.get(CobblemonItemComponents.TM_MOVE);
        if (tm == null || tm.getMoveName().isBlank()) return;
        // Keep the component translatable: language changes must also update existing stacks.
        Component move = Component.translatableWithFallback(
            "cobblemon.move." + tm.getMoveName(), tm.getMoveName());
        callback.setReturnValue(callback.getReturnValue().copy().append(" · ").append(move));
    }
}
