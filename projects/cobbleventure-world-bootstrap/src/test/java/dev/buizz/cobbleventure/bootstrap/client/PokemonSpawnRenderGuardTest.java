package dev.buizz.cobbleventure.bootstrap.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PokemonSpawnRenderGuardTest {
    @Test void hidesPokemonUntilSpeciesIsSynchronized() {
        assertFalse(PokemonSpawnRenderGuard.hasSynchronizedSpecies(null));
        assertFalse(PokemonSpawnRenderGuard.hasSynchronizedSpecies(""));
        assertFalse(PokemonSpawnRenderGuard.hasSynchronizedSpecies("   "));
        assertTrue(PokemonSpawnRenderGuard.hasSynchronizedSpecies("cobblemon:pidgey"));
    }
}
