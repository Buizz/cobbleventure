package dev.buizz.cobbleventure.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class PokemonCenterDefeatReturnTest {
    @Test
    void recoveryStandsTwoBlocksInFrontOfNurseInEveryHorizontalDirection() {
        BlockPos nurse = new BlockPos(10, 64, -20);
        assertEquals(new BlockPos(10, 64, -22),
            PokemonCenterDefeatReturn.nurseRecoveryPosition(nurse, Direction.NORTH, p -> true));
        assertEquals(new BlockPos(10, 64, -18),
            PokemonCenterDefeatReturn.nurseRecoveryPosition(nurse, Direction.SOUTH, p -> true));
        assertEquals(new BlockPos(12, 64, -20),
            PokemonCenterDefeatReturn.nurseRecoveryPosition(nurse, Direction.EAST, p -> true));
        assertEquals(new BlockPos(8, 64, -20),
            PokemonCenterDefeatReturn.nurseRecoveryPosition(nurse, Direction.WEST, p -> true));
    }

    @Test
    void blockedCounterUsesThirdBlockAndNeverChoosesAnUnsafePosition() {
        BlockPos nurse = new BlockPos(-10, 80, 20);
        BlockPos third = new BlockPos(-10, 80, 23);
        assertEquals(third, PokemonCenterDefeatReturn.nurseRecoveryPosition(
            nurse, Direction.SOUTH, third::equals
        ));
        assertNull(PokemonCenterDefeatReturn.nurseRecoveryPosition(
            nurse, Direction.SOUTH, p -> false
        ));
    }

    @Test
    void trainerForfeitIsRecordedAsForcedDefeat() {
        assertTrue(PokemonCenterDefeatReturn.shouldRecordForfeit(false));
    }

    @Test
    void wildEscapeIsNotRecordedAsForcedDefeat() {
        assertFalse(PokemonCenterDefeatReturn.shouldRecordForfeit(true));
    }

    @Test
    void npcEventsStayBlockedUntilDefeatRecoveryHasTeleportedThePlayer() {
        assertTrue(PokemonCenterDefeatReturn.blocksNewNpcEvents(true, false, false));
        assertTrue(PokemonCenterDefeatReturn.blocksNewNpcEvents(false, true, false));
        assertFalse(PokemonCenterDefeatReturn.blocksNewNpcEvents(false, true, true));
        assertFalse(PokemonCenterDefeatReturn.blocksNewNpcEvents(false, false, false));
    }

    @Test
    void defeatRecoveryWaitsForRegisteredOrPendingTrainerBattle() {
        assertTrue(PokemonCenterDefeatReturn.shouldDeferRecovery(true, false));
        assertTrue(PokemonCenterDefeatReturn.shouldDeferRecovery(false, true));
        assertTrue(PokemonCenterDefeatReturn.shouldDeferRecovery(true, true));
        assertFalse(PokemonCenterDefeatReturn.shouldDeferRecovery(false, false));
    }
}
