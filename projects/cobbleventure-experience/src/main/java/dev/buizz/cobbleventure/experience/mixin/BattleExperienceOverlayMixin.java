package dev.buizz.cobbleventure.experience.mixin;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import dev.buizz.cobbleventure.experience.client.ExperienceOverlay;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(BattleOverlay.class)
public abstract class BattleExperienceOverlayMixin {
    @Unique private float cobbleventure$tileX;
    @Unique private float cobbleventure$tileY;
    @Unique private float cobbleventure$tileOpacity;
    @Unique private boolean cobbleventure$tileDrawn;
    @Unique private float cobbleventure$levelUpPulse;

    @Inject(method = "drawTile", at = @At("HEAD"))
    private void cobbleventure$resetTile(CallbackInfo ci) {
        cobbleventure$tileDrawn = false;
    }

    @ModifyArgs(method = "drawTile", at = @At(value = "INVOKE", target =
        "Lcom/cobblemon/mod/common/client/gui/battle/BattleOverlay;drawBattleTile(Lnet/minecraft/client/gui/GuiGraphics;FFFZLcom/cobblemon/mod/common/pokemon/Species;ILnet/minecraft/network/chat/MutableComponent;Lcom/cobblemon/mod/common/pokemon/Gender;Lcom/cobblemon/mod/common/pokemon/status/PersistentStatus;Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;Lkotlin/Triple;FLcom/cobblemon/mod/common/client/battle/ClientBallDisplay;IFZZZLnet/minecraft/network/chat/MutableComponent;ZLcom/cobblemon/mod/common/api/pokedex/PokedexEntryProgress;)V"))
    private void cobbleventure$drawExperience(
        Args args, GuiGraphics graphics, float tickDelta, ActiveClientBattlePokemon active,
        boolean left, int rank, PokedexEntryProgress dexState,
        boolean hasCommand, boolean hovered, boolean compact
    ) {
        cobbleventure$tileX = args.get(1);
        cobbleventure$tileY = args.get(2);
        cobbleventure$tileOpacity = args.get(12);
        cobbleventure$tileDrawn = true;
        cobbleventure$levelUpPulse = ExperienceOverlay.beginTile(
            graphics, active, cobbleventure$tileX, cobbleventure$tileY, compact);
    }

    @Inject(method = "drawTile", at = @At("RETURN"))
    private void cobbleventure$drawInsideFrame(
        GuiGraphics graphics, float tickDelta, ActiveClientBattlePokemon active,
        boolean left, int rank, PokedexEntryProgress dexState,
        boolean hasCommand, boolean hovered, boolean compact, CallbackInfo ci
    ) {
        if (cobbleventure$tileDrawn) {
            try {
                ExperienceOverlay.renderTile(graphics, active,
                    cobbleventure$tileX, cobbleventure$tileY, compact, cobbleventure$tileOpacity);
            } finally {
                ExperienceOverlay.endTile(graphics, cobbleventure$tileX, cobbleventure$tileY,
                    compact, cobbleventure$tileOpacity, cobbleventure$levelUpPulse);
            }
        }
    }
}
