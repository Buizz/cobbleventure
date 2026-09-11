package dev.buizz.cobbleventure.playermenu;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure selection policy for TM recipes discovered from completed TM items. */
final class TmAcquisitionPlanner {
    private TmAcquisitionPlanner() {}

    static <T> List<T> newlyDiscovered(
        Collection<T> candidates,
        Set<T> learned,
        Set<T> inherentlyUnlocked,
        boolean unlockAll
    ) {
        if (unlockAll || candidates.isEmpty()) return List.of();
        LinkedHashSet<T> discovered = new LinkedHashSet<>();
        for (T candidate : candidates) {
            if (candidate != null
                && !learned.contains(candidate)
                && !inherentlyUnlocked.contains(candidate)) {
                discovered.add(candidate);
            }
        }
        return List.copyOf(discovered);
    }

    static boolean allowsPokemonGain(boolean activeTrade) {
        return !activeTrade;
    }

    static boolean learnsAtLevel(int moveLevel, int oldLevel, int newLevel) {
        return newLevel > oldLevel && moveLevel > oldLevel && moveLevel <= newLevel;
    }
}
