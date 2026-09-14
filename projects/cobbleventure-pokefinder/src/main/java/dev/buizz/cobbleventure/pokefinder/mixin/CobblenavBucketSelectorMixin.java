package dev.buizz.cobbleventure.pokefinder.mixin;

import com.cobblemon.mod.common.api.text.TextKt;
import com.metacontent.cobblenav.client.gui.widget.location.BucketSelectorWidget;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prevents CobbleNav 2.4.1 from assigning a component as its own hover contents. */
@Mixin(value = BucketSelectorWidget.class, remap = false)
abstract class CobblenavBucketSelectorMixin {
    @Redirect(
        method = "renderWidget",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/text/TextKt;onHover(Lnet/minecraft/network/chat/MutableComponent;Lnet/minecraft/network/chat/MutableComponent;)Lnet/minecraft/network/chat/MutableComponent;"
        ),
        require = 1
    )
    private MutableComponent cobbleventure$avoidSelfReferentialHover(
        MutableComponent component,
        MutableComponent hoverContents
    ) {
        return safeHover(component, hoverContents);
    }

    static MutableComponent safeHover(MutableComponent component, MutableComponent hoverContents) {
        if (component == hoverContents) {
            return component;
        }
        return TextKt.onHover(component, hoverContents);
    }
}
