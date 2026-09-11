package dev.buizz.cobbleventure.playermenu.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Session-persistent pending growth actions, independent from the short-lived toast queue. */
final class GrowthNotificationState {
    private final Map<UUID, PendingGrowth> pending = new HashMap<>();

    void addMoves(UUID pokemonId, int count) {
        if (count <= 0) return;
        pending.compute(pokemonId, (ignored, current) -> {
            PendingGrowth value = current == null ? PendingGrowth.EMPTY : current;
            return new PendingGrowth(value.moves() + count, value.evolutions());
        });
    }

    void addEvolutions(UUID pokemonId, int count) {
        if (count <= 0) return;
        pending.compute(pokemonId, (ignored, current) -> {
            PendingGrowth value = current == null ? PendingGrowth.EMPTY : current;
            return new PendingGrowth(value.moves(), value.evolutions() + count);
        });
    }

    PendingGrowth get(UUID pokemonId) {
        return pending.getOrDefault(pokemonId, PendingGrowth.EMPTY);
    }

    void acknowledgeMoves(UUID pokemonId) {
        update(pokemonId, new PendingGrowth(0, get(pokemonId).evolutions()));
    }

    void resolveEvolutions(UUID pokemonId) {
        update(pokemonId, new PendingGrowth(get(pokemonId).moves(), 0));
    }

    void retainPokemon(Set<UUID> pokemonIds) {
        pending.keySet().retainAll(pokemonIds);
    }

    void clear() {
        pending.clear();
    }

    private void update(UUID pokemonId, PendingGrowth value) {
        if (value.total() == 0) pending.remove(pokemonId);
        else pending.put(pokemonId, value);
    }

    record PendingGrowth(int moves, int evolutions) {
        private static final PendingGrowth EMPTY = new PendingGrowth(0, 0);

        int total() {
            return moves + evolutions;
        }
    }
}
