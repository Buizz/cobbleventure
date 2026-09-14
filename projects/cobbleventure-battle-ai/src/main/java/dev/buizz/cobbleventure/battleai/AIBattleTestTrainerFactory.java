package dev.buizz.cobbleventure.battleai;

import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import net.minecraft.world.entity.LivingEntity;

/** Creates an isolated trainer copy whose AI difficulty is fixed for comparison tests. */
final class AIBattleTestTrainerFactory {
    private AIBattleTestTrainerFactory() {}

    static TrainerNPC create(
            TrainerNPC authored,
            CobbleventureBattleAI authoredAI,
            LivingEntity opponent,
            String difficulty
    ) {
        TrainerNPC copied = new TrainerNPC(authored);
        return new TrainerNPC(
                copied.getName(),
                copied.getTeam(),
                copied.getGimmicks(),
                copied.getBag(),
                copied.getBattleTheme(),
                authoredAI.withDifficulty(difficulty),
                opponent
        );
    }
}
