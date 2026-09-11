package dev.buizz.cobbleventure.bootstrap;

import com.cobblemon.mod.common.Cobblemon;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Excludes interiors before Cobblemon schedules a player's natural spawn attempts. */
final class InteriorSpawnPolicy {
    private InteriorSpawnPolicy() {}

    static void apply() {
        if (!configure(Cobblemon.config)) {
            LogUtils.getLogger().warn("Interior spawning blocklist requires Cobblemon 1.8 or later");
        }
    }

    static boolean configure(Object config) {
        // Keep the optional 1.7 build compatible; this native option was added in 1.8.
        try {
            var getter = config.getClass().getMethod("getWorldSpawningBlocklist");
            var setter = config.getClass().getMethod("setWorldSpawningBlocklist", Set.class);
            Set<?> existing = (Set<?>) getter.invoke(config);
            Set<Object> blocked = new LinkedHashSet<>(existing);
            blocked.add(ResourceLocation.parse("cobbleventure:building_interiors"));
            blocked.add(ResourceLocation.parse("cobbleventure:gym_interiors"));
            // Existing installations can retain an older main.json. Merge in memory
            // on every server start without replacing the user's other exclusions.
            setter.invoke(config, blocked);
            return true;
        } catch (NoSuchMethodException legacyVersion) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot configure interior natural spawning", exception);
        }
    }
}
