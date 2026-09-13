package dev.buizz.cobbleventure.battleai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AIBattleTestCommandTest {
    @Test
    void playerIsLeftAndLoreleiRuntimeTrainerIsRight() {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID opponentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String runtimeId = AIBattleTestCommand.runtimeTrainerId(playerId, opponentId);

        assertEquals(
                "tbcs battle GEN_9_SINGLES " + playerId
                        + " vs " + opponentId + " as " + runtimeId,
                AIBattleTestCommand.battleCommand(playerId, opponentId, runtimeId)
        );
        assertTrue(runtimeId.startsWith("cobbleventure_battle_ai:test/lorelei/"));
    }
}
