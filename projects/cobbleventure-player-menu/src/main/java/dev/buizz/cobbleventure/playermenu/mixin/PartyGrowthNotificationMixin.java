package dev.buizz.cobbleventure.playermenu.mixin;

import com.cobblemon.mod.common.client.gui.PartyOverlayDataControl;
import dev.buizz.cobbleventure.playermenu.client.GrowthNotificationOverlay;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Promotes Cobblemon's transient party popups into visible, actionable growth notices. */
@Mixin(PartyOverlayDataControl.class)
abstract class PartyGrowthNotificationMixin {
    @Inject(method = "pokemonGainedExp", at = @At("HEAD"))
    private void cobbleventure$showLevelToast(
        UUID pokemonId,
        Integer oldLevel,
        int experienceGained,
        int movesLearned,
        int evolutionsUnlocked,
        CallbackInfo callback
    ) {
        GrowthNotificationOverlay.experienceGained(
            pokemonId, movesLearned, evolutionsUnlocked
        );
    }

    @Inject(method = "pokemonGainedMoves", at = @At("HEAD"))
    private void cobbleventure$showMoveToast(UUID pokemonId, int count, CallbackInfo callback) {
        GrowthNotificationOverlay.movesGained(pokemonId, count);
    }

    @Inject(method = "pokemonGainedEvo", at = @At("HEAD"))
    private void cobbleventure$showEvolutionToast(UUID pokemonId, int count, CallbackInfo callback) {
        GrowthNotificationOverlay.evolutionsGained(pokemonId, count);
    }
}
