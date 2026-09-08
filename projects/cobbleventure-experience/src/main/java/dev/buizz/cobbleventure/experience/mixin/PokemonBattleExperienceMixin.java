package dev.buizz.cobbleventure.experience.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Experience is paid per KO, so Cobblemon's battle-end pass must not pay it again. */
@Mixin(PokemonBattle.class)
public abstract class PokemonBattleExperienceMixin {
    @Redirect(
        method = "end",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/battles/model/actor/BattleActor;awardExperience(Lcom/cobblemon/mod/common/battles/pokemon/BattlePokemon;I)V"
        )
    )
    private void cobbleventure$suppressDeferredExperience(
        BattleActor actor, BattlePokemon pokemon, int experience
    ) {
        // Deliberately empty: ExperienceRewards pays the same reward on BATTLE_FAINTED.
    }
}
