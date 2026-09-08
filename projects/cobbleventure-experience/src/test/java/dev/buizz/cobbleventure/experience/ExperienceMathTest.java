package dev.buizz.cobbleventure.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ExperienceMathTest {
    @Test
    void progressIsClampedToCurrentLevel() {
        assertEquals(0.0F, ExperienceMath.progress(80, 100, 200));
        assertEquals(0.5F, ExperienceMath.progress(150, 100, 200));
        assertEquals(1.0F, ExperienceMath.progress(250, 100, 200));
    }

    @Test
    void captureFormulaMatchesCobblemonRoundingOrder() {
        assertEquals(129, ExperienceMath.captureExperience(
            64, 10, 10, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D
        ));
    }
}
