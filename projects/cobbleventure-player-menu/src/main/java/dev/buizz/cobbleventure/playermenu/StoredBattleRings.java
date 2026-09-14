package dev.buizz.cobbleventure.playermenu;

import java.util.function.Predicate;

/** Limits possession-based equipment checks to Mega Evolution and Z-Moves. */
final class StoredBattleRings {
    private StoredBattleRings() {}

    static boolean supports(String tag) {
        return tag.equals("mega_showdown:mega_bracelet") || tag.equals("mega_showdown:z_ring");
    }

    static <T> boolean contains(String tag, Iterable<T> stacks, Predicate<T> matches) {
        if (!supports(tag)) return false;
        for (T stack : stacks) if (matches.test(stack)) return true;
        return false;
    }
}
