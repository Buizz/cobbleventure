package dev.buizz.cobbleventure.battleai;

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Keeps a valid AI choice, but replaces an invalid mandatory switch with a valid candidate. */
final class ForcedSwitchRecovery {
    private ForcedSwitchRecovery() {}

    static <T> T select(
            boolean forceSwitch,
            T proposed,
            Predicate<T> isValid,
            Supplier<Stream<T>> recoveryCandidates
    ) {
        if (!forceSwitch || (proposed != null && isValid.test(proposed))) {
            return proposed;
        }
        return recoveryCandidates.get()
                .filter(isValid)
                .findFirst()
                .orElse(proposed);
    }
}
