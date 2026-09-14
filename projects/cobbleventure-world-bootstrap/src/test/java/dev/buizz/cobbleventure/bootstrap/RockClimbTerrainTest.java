package dev.buizz.cobbleventure.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RockClimbTerrainTest {
    @Test
    void indigoPlateauPeakUsesStoneBelowItsSurface() {
        assertTrue(RockClimbTerrainPolicy.usesStoneFiller("minecraft:stony_peaks"));
    }
}
