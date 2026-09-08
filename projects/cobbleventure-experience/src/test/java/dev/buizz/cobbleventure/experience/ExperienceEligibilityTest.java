package dev.buizz.cobbleventure.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ExperienceEligibilityTest {
    @Test
    void participantReceivesFullExperience() {
        assertEquals(1.0D, ExperienceEligibility.multiplier(true, true, false, 0.5D));
    }

    @Test
    void experienceShareUsesConfiguredMultiplier() {
        assertEquals(0.5D, ExperienceEligibility.multiplier(true, false, true, 0.5D));
    }

    @Test
    void faintedOrIneligiblePokemonReceiveNothing() {
        assertEquals(0.0D, ExperienceEligibility.multiplier(false, true, true, 0.5D));
        assertEquals(0.0D, ExperienceEligibility.multiplier(true, false, false, 0.5D));
    }
}
