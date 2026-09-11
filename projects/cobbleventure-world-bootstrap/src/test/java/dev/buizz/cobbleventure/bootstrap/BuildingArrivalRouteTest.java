package dev.buizz.cobbleventure.bootstrap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BuildingArrivalRouteTest {
    @Test
    void arrivalRoutesCannotCreateAnUnconditionalReverseTrigger() {
        for (String type : new String[]{"arrival", "interior_spawn", "exterior_spawn"}) {
            assertTrue(BuildingConnectionTypes.isDestination(type));
            assertFalse(BuildingConnectionTypes.isTrigger(type));
        }
    }

    @Test
    void existingDoorsRemainBidirectionalAndNpcPositionsAreNotDoorDestinations() {
        for (String type : new String[]{"door", "transition"}) {
            assertTrue(BuildingConnectionTypes.isDestination(type));
            assertTrue(BuildingConnectionTypes.isTrigger(type));
        }
        assertFalse(BuildingConnectionTypes.isDestination("npc_position"));
    }
}
