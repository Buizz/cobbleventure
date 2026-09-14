package dev.buizz.cobbleventure.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class PokemonCenterDefeatReturnTest {
    @Test
    void leagueAndNurseOverwriteTheSamePersistentCheckpoint() {
        CompoundTag data = new CompoundTag();
        BlockPos nurse = new BlockPos(10, 64, 20);
        BlockPos lobby = new BlockPos(90, 72, 120);
        PokemonCenterDefeatReturn.saveCheckpoint(data, "cobbleventure:generation_1",
            nurse, nurse, true, false);
        PokemonCenterDefeatReturn.saveCheckpoint(data, "cobbleventure:building_interiors",
            lobby, lobby, false, true);
        CompoundTag restored = data.copy();
        assertTrue(PokemonCenterDefeatReturn.hasSavedCheckpoint(restored));
        assertFalse(restored.getBoolean("cobbleventurePokemonCenterVisited"));
        assertEquals("cobbleventure:building_interiors",
            restored.getString("cobbleventurePokemonCenterDimension"));
        assertEquals(90, restored.getInt("cobbleventurePokemonCenterX"));
        assertEquals(120, restored.getInt("cobbleventurePokemonCenterExitZ"));

        PokemonCenterDefeatReturn.saveCheckpoint(restored, "cobbleventure:generation_1",
            nurse, nurse, true, false);
        assertTrue(PokemonCenterDefeatReturn.hasSavedCheckpoint(restored));
        assertFalse(restored.getBoolean("cobbleventureLeagueCheckpoint"));
        assertTrue(restored.getBoolean("cobbleventurePokemonCenterVisited"));
        assertEquals(10, restored.getInt("cobbleventurePokemonCenterX"));
    }

    @Test
    void legacyNurseCheckpointSurvivesButGenericSpawnIsNotASavePoint() {
        CompoundTag legacy = new CompoundTag();
        legacy.putBoolean("cobbleventurePokemonCenterVisited", true);
        assertTrue(PokemonCenterDefeatReturn.hasSavedCheckpoint(legacy));
        PokemonCenterDefeatReturn.saveCheckpoint(legacy, "minecraft:overworld",
            BlockPos.ZERO, BlockPos.ZERO, false, false);
        assertFalse(PokemonCenterDefeatReturn.hasSavedCheckpoint(legacy));
    }

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
