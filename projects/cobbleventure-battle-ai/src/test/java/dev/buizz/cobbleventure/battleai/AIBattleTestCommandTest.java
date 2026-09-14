package dev.buizz.cobbleventure.battleai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;
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

    @Test
    void opponentStartsFourBlocksInFrontInsteadOfOverlappingPlayer() {
        Vec3 playerPosition = new Vec3(100.5D, 64.0D, -20.5D);

        Vec3 south = AIBattleTestCommand.opponentPosition(playerPosition, 0.0F);
        Vec3 west = AIBattleTestCommand.opponentPosition(playerPosition, 90.0F);

        assertEquals(4.0D, playerPosition.distanceTo(south), 0.000_001D);
        assertEquals(4.0D, playerPosition.distanceTo(west), 0.000_001D);
        assertEquals(new Vec3(100.5D, 64.0D, -16.5D), south);
        assertEquals(new Vec3(96.5D, 64.0D, -20.5D), west);
    }
}
