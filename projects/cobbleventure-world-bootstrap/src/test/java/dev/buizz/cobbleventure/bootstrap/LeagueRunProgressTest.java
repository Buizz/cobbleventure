package dev.buizz.cobbleventure.bootstrap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LeagueRunProgressTest {
    @Test
    void checkpointResumesAfterLastVictoryWhenInterrupted() {
        var progress = new LeagueRunProgress(0, 5, false).win(0).win(1).interrupt();
        assertEquals(2, progress.completed());
        assertTrue(progress.canBattle(2));
        assertFalse(progress.canBattle(1));
    }

    @Test
    void relayRequiresConsecutiveVictories() {
        var progress = new LeagueRunProgress(0, 5, true).win(0).win(1).interrupt();
        assertEquals(0, progress.completed());
        assertTrue(progress.canBattle(0));
        assertFalse(progress.canBattle(2));
    }

    @Test
    void duplicateOrOutOfOrderOutcomesCannotSkipStages() {
        var progress = new LeagueRunProgress(0, 5, false);
        assertSame(progress, progress.win(4));
        progress = progress.win(0);
        assertSame(progress, progress.win(0));
        assertSame(progress, progress.win(-1));
        assertEquals(1, progress.completed());
    }

    @Test
    void championVictoryRemainsClearedAfterLeavingOrDisconnecting() {
        for (boolean relay : new boolean[]{false, true}) {
            var progress = new LeagueRunProgress(4, 5, relay).win(4);
            assertEquals(5, progress.completed());
            assertFalse(progress.canBattle(5));
            assertTrue(progress.cleared());
            assertEquals(5, progress.interrupt().completed());
            assertEquals(5, progress.interrupt().interrupt().completed());
        }
    }

    @Test
    void clearedLobbyAdmissionRedirectsButReturnsFromRoomsDoNotBounceBack() {
        var cleared = new LeagueRunProgress(5, 5, true);
        assertTrue(cleared.redirectsLobbyToHall(false));
        assertFalse(cleared.redirectsLobbyToHall(true));
        assertFalse(new LeagueRunProgress(2, 5, false).redirectsLobbyToHall(false));
    }
}
