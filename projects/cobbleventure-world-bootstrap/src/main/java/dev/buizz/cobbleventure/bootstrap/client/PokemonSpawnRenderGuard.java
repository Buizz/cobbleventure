package dev.buizz.cobbleventure.bootstrap.client;

/** Decides when a client-side Pokemon has enough synchronized data to render safely. */
public final class PokemonSpawnRenderGuard {
    private PokemonSpawnRenderGuard() {}

    public static boolean hasSynchronizedSpecies(String species) {
        return species != null && !species.isBlank();
    }
}
