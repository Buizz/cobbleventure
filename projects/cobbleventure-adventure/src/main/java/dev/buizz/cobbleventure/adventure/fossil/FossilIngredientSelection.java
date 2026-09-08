package dev.buizz.cobbleventure.adventure.fossil;

import java.util.List;
import java.util.function.BiPredicate;

/** Assigns ingredients to inventory slots without reusing items, including overlapping predicates. */
public final class FossilIngredientSelection {
    private FossilIngredientSelection() {}

    public static int[] select(int ingredients, List<Integer> counts, BiPredicate<Integer, Integer> matches) {
        int[] used = new int[counts.size()];
        int[] slots = new int[ingredients];
        return assign(0, slots, used, counts, matches) ? slots : null;
    }

    private static boolean assign(int index, int[] slots, int[] used, List<Integer> counts,
                                  BiPredicate<Integer, Integer> matches) {
        if (index == slots.length) return true;
        for (int slot = 0; slot < used.length; slot++) {
            if (used[slot] >= counts.get(slot) || !matches.test(index, slot)) continue;
            used[slot]++;
            slots[index] = slot;
            if (assign(index + 1, slots, used, counts, matches)) return true;
            used[slot]--;
        }
        return false;
    }
}
