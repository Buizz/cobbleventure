package dev.buizz.cobbleventure.adventure.research;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchPolicyTest {
    @Test void zResearchChecksCurrentMovesAndExactSpeciesForm() {
        assertTrue(ResearchPolicy.zCompatible("fire", "", java.util.List.of(), "pikachu", java.util.List.of("flamethrower"), java.util.List.of("Fire")));
        assertFalse(ResearchPolicy.zCompatible("fire", "", java.util.List.of(), "charizard", java.util.List.of("tackle"), java.util.List.of("normal")));
        assertTrue(ResearchPolicy.zCompatible("electric", "Volt Tackle", java.util.List.of("Pikachu"), "pikachu", java.util.List.of("volttackle"), java.util.List.of("electric")));
        assertFalse(ResearchPolicy.zCompatible("electric", "Volt Tackle", java.util.List.of("Pikachu"), "pikachu", java.util.List.of("thunderbolt"), java.util.List.of("electric")));
        assertFalse(ResearchPolicy.zCompatible("electric", "Thunderbolt", java.util.List.of("Pikachu-Original"), "pikachu", java.util.List.of("thunderbolt"), java.util.List.of("electric")));
    }
    @Test void decreasesDoNotDiscountIncreases() {
        assertEquals(2520, ResearchPolicy.statCost(new int[]{0,252,0,0,0,0}, new int[]{0,0,0,0,0,252},252,510,10));
        assertEquals(0, ResearchPolicy.statCost(new int[]{31,31,31,31,31,31}, new int[6],31,186,500));
        assertEquals(93000, ResearchPolicy.statCost(new int[6], new int[]{31,31,31,31,31,31},31,186,500));
    }
    @Test void validatesIndividualAndTotalLimitsBeforeQuoting() {
        assertThrows(IllegalArgumentException.class, () -> ResearchPolicy.statCost(new int[6],new int[]{0,252,252,0,0,7},252,510,10));
        assertThrows(IllegalArgumentException.class, () -> ResearchPolicy.statCost(new int[6],new int[]{32,0,0,0,0,0},31,186,500));
        assertThrows(IllegalArgumentException.class, () -> ResearchPolicy.statCost(new int[6],new int[]{-1,0,0,0,0,0},31,186,500));
        assertThrows(IllegalArgumentException.class, () -> ResearchPolicy.statCost(new int[6],new int[5],31,186,500));
        assertEquals(5100, ResearchPolicy.statCost(new int[6],new int[]{6,252,0,0,0,252},252,510,10));
    }
    @Test void dynamaxUsesMushroomsOnlyForNewProgress() {
        assertEquals(13, ResearchPolicy.mushrooms(0,10,false,true,true,1,3));
        assertEquals(0, ResearchPolicy.mushrooms(10,0,true,false,true,1,3));
        assertEquals(0, ResearchPolicy.mushrooms(10,10,true,true,true,1,3));
        assertThrows(IllegalArgumentException.class, () -> ResearchPolicy.mushrooms(0,11,false,false,true,1,3));
        assertThrows(IllegalArgumentException.class, () -> ResearchPolicy.mushrooms(0,0,false,true,false,1,3));
    }
}
