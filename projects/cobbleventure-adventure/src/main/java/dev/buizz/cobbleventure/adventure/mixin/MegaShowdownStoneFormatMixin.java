package dev.buizz.cobbleventure.adventure.mixin;

import dev.buizz.cobbleventure.adventure.ShowdownMegaStonePatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run after Mega Showdown writes its engine files, before Graal loads them. */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.utils.ShowdownPatcher", remap = false)
public abstract class MegaShowdownStoneFormatMixin {
    @Inject(method = "patch", at = @At("RETURN"), require = 1)
    private static void cobbleventure$acceptNativeMegaStones(CallbackInfo callback) {
        ShowdownMegaStonePatch.apply();
    }
}
