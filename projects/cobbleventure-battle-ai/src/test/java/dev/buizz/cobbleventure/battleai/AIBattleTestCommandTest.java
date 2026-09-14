package dev.buizz.cobbleventure.battleai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class AIBattleTestCommandTest {
    @Test
    void loreleiRuntimeTrainerUsesStableUniqueId() {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID opponentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String runtimeId = AIBattleTestCommand.runtimeTrainerId(playerId, opponentId);

        assertTrue(runtimeId.startsWith("cobbleventure_battle_ai:test/lorelei/"));
        assertEquals("expert_search", AIBattleTestCommand.TEST_DIFFICULTY);
    }

    @Test
    void packagedLeftEntryMatchesWebOfficialPreset() throws Exception {
        try (var stream = AIBattleTestCommandTest.class.getResourceAsStream(
                AIBattleTestPlayerPreset.TEAM_RESOURCE)) {
            assertNotNull(stream);
            var root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var team = root.getAsJsonArray("team");

            assertEquals("DBingsu 공식 엔트리", root.get("name").getAsString());
            assertEquals(6, team.size());
            assertEquals("porygon2", team.get(0).getAsJsonObject().get("species").getAsString());
            var urshifu = team.get(2).getAsJsonObject();
            assertEquals("urshifu", urshifu.get("species").getAsString());
            assertEquals("rapid_strike-style", urshifu.getAsJsonArray("aspects").get(0).getAsString());
            assertEquals("blaziken", team.get(5).getAsJsonObject().get("species").getAsString());
        }
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
