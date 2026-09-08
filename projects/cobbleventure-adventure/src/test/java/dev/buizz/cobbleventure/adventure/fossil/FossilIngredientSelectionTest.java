package dev.buizz.cobbleventure.adventure.fossil;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FossilIngredientSelectionTest {
    @Test void oneItemCannotPayForTwoIngredients() {
        assertNull(FossilIngredientSelection.select(2, List.of(1), (ingredient, slot) -> true));
    }
    @Test void twoItemsInOneStackCanPayForTwoIngredients() {
        assertArrayEquals(new int[]{0, 0}, FossilIngredientSelection.select(2, List.of(2), (i, s) -> true));
    }
    @Test void overlappingPredicatesBacktrackToPreserveSpecificIngredient() {
        assertArrayEquals(new int[]{1, 0}, FossilIngredientSelection.select(2, List.of(1, 1),
            (ingredient, slot) -> ingredient == 0 || slot == 0));
    }
    @Test void missingHalfOfAPairIsUnavailable() {
        assertNull(FossilIngredientSelection.select(2, List.of(3, 0), (ingredient, slot) -> ingredient == slot));
    }
}
