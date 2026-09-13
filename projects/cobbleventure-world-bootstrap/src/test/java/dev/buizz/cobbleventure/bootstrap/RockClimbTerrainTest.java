package dev.buizz.cobbleventure.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RockClimbTerrainTest {
    @Test
    void indigoPlateauPeakUsesStoneBelowItsSurface() {
        assertTrue(RockClimbTerrainPolicy.usesStoneFiller("minecraft:stony_peaks"));
    }

    @Test
    void naturalTerrainCoverDoesNotStopClimbingAtTheCliffTop() {
        assertTrue(RockClimbTerrainPolicy.isNaturalCover("minecraft:grass_block"));
        assertTrue(RockClimbTerrainPolicy.isNaturalCover("minecraft:dirt"));
        assertTrue(RockClimbTerrainPolicy.isNaturalCover("minecraft:gravel"));
        assertFalse(RockClimbTerrainPolicy.isNaturalCover("minecraft:stone_bricks"));
    }
}
