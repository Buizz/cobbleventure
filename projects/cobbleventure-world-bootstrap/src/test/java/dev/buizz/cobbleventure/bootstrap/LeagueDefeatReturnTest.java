package dev.buizz.cobbleventure.bootstrap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LeagueDefeatReturnTest {
    @Test
    void defeatCancelsLobbyReturnInsteadOfDelayingItUntilAfterHealing() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Map<UUID, String> returns = new HashMap<>();
        returns.put(player, "league-lobby");
        returns.put(other, "other-lobby");

        assertTrue(LeagueRuntimeSystem.suppressLobbyReturnForRecovery(player, true, returns));
        assertFalse(returns.containsKey(player));
        assertEquals("other-lobby", returns.get(other));
        // Arrival and nurse dialogue still suppress the inactive-room ejection path.
        assertTrue(LeagueRuntimeSystem.suppressLobbyReturnForRecovery(player, true, returns));
        // Healing completion must not release an old lobby teleport.
        assertFalse(LeagueRuntimeSystem.suppressLobbyReturnForRecovery(player, false, returns));
        assertFalse(returns.containsKey(player));
    }

    @Test
    void interruptionWithoutDefeatKeepsItsLobbyReturn() {
        UUID player = UUID.randomUUID();
        Map<UUID, String> returns = new HashMap<>();
        returns.put(player, "league-lobby");
        assertFalse(LeagueRuntimeSystem.suppressLobbyReturnForRecovery(player, false, returns));
        assertEquals("league-lobby", returns.get(player));
    }
}
