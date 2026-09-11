package dev.buizz.cobbleventure.playermenu.mixin;

import com.cobblemon.mod.common.api.tms.TMMoveManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Cobblemon's broad accessible-move sync with Cobbleventure's explicit event policy. */
@Mixin(TMMoveManager.class)
abstract class TMMoveManagerPolicyMixin {
    @Inject(method = "syncTMsFromPokemon", at = @At("HEAD"), cancellable = true)
    private void cobbleventure$useExplicitRecipeDiscovery(Pokemon pokemon, CallbackInfo callback) {
        callback.cancel();
    }
}
