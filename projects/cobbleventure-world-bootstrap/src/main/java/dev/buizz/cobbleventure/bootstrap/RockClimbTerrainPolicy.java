package dev.buizz.cobbleventure.bootstrap;

/** Pure classification rules for rock-climb terrain generation. */
final class RockClimbTerrainPolicy {
    private RockClimbTerrainPolicy() {}

    static boolean usesStoneFiller(String biome) {
        return biome.contains("peak") || biome.contains("mountain")
            || biome.contains("windswept");
    }
}
