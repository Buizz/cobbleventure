package dev.buizz.cobbleventure.bootstrap;

import java.util.Set;

/** Pure classification rules shared by rock-climb terrain generation and collision checks. */
final class RockClimbTerrainPolicy {
    private static final Set<String> NATURAL_COVERS = Set.of(
        "minecraft:grass_block",
        "minecraft:dirt",
        "minecraft:coarse_dirt",
        "minecraft:podzol",
        "minecraft:rooted_dirt",
        "minecraft:gravel"
    );

    private RockClimbTerrainPolicy() {}

    static boolean usesStoneFiller(String biome) {
        return biome.contains("peak") || biome.contains("mountain")
            || biome.contains("windswept");
    }

    static boolean isNaturalCover(String blockId) {
        return NATURAL_COVERS.contains(blockId);
    }
}
