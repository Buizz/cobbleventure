package dev.buizz.cobbleventure.adventure.mixin;

/** Move-index compatibility logic kept outside the mixin target class. */
final class MegaShowdownMoveIndex {
    private MegaShowdownMoveIndex() {}

    static int bounded(int size, int requested) {
        if (size <= 0) throw new IllegalArgumentException("Rayquaza moveset must not be empty");
        return Math.min(requested, size - 1);
    }
}
