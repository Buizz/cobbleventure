package dev.buizz.cobbleventure.playermenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TmAcquisitionPlannerTest {
    @Test
    void returnsOnlyFirstLockedRecipeInStableOrder() {
        assertEquals(
            List.of("protect", "surf"),
            TmAcquisitionPlanner.newlyDiscovered(
                List.of("protect", "default", "protect", "known", "surf"),
                Set.of("known"),
                Set.of("default"),
                false
            )
        );
    }

    @Test
    void unlockAllConfigurationSuppressesDiscovery() {
        assertEquals(
            List.of(),
            TmAcquisitionPlanner.newlyDiscovered(
                List.of("protect"), Set.of(), Set.of(), true
            )
        );
    }

    @Test
    void tradedPokemonDoesNotGrantRecipes() {
        assertFalse(TmAcquisitionPlanner.allowsPokemonGain(true));
        assertTrue(TmAcquisitionPlanner.allowsPokemonGain(false));
    }

    @Test
    void levelUpGrantsOnlyMovesCrossedByThatLevelChange() {
        assertFalse(TmAcquisitionPlanner.learnsAtLevel(20, 20, 21));
        assertTrue(TmAcquisitionPlanner.learnsAtLevel(21, 20, 21));
        assertTrue(TmAcquisitionPlanner.learnsAtLevel(23, 20, 25));
        assertFalse(TmAcquisitionPlanner.learnsAtLevel(26, 20, 25));
        assertFalse(TmAcquisitionPlanner.learnsAtLevel(20, 20, 20));
    }
}
