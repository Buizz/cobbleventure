package dev.buizz.cobbleventure.adventure.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Mega Showdown 1.2.0's four-slot Rayquaza check safe for partially filled movesets. */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.gimmick.MegaGimmick", remap = false)
public abstract class MegaShowdownRayquazaMixin {
    @Inject(method = "canMega", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cobbleventure$rejectEmptyRayquazaMoveset(
        Pokemon pokemon, CallbackInfoReturnable<Boolean> callback
    ) {
        if (pokemon.getSpecies().getName().equals("Rayquaza")
            && pokemon.getMoveSet().getMoves().isEmpty()) {
            callback.setReturnValue(false);
        }
    }

    @Redirect(method = "canMega", at = @At(value = "INVOKE",
        target = "Ljava/util/List;get(I)Ljava/lang/Object;"), require = 1, allow = 1)
    private static Object cobbleventure$boundRayquazaMoveLookup(List<?> moves, int index) {
        return moves.get(MegaShowdownMoveIndex.bounded(moves.size(), index));
    }
}
