package dev.buizz.cobbleventure.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class InteriorSpawnPolicyTest {
    @Test
    void mergesNativeBlocklistWithoutChangingExistingExclusionsOrOutdoorSpawning() {
        Config config = new Config();
        ResourceLocation custom = ResourceLocation.parse("example:private_world");
        config.setWorldSpawningBlocklist(Set.of(custom));
        assertTrue(InteriorSpawnPolicy.configure(config));
        assertTrue(InteriorSpawnPolicy.configure(config));
        assertEquals(Set.of(custom,
            ResourceLocation.parse("cobbleventure:building_interiors"),
            ResourceLocation.parse("cobbleventure:gym_interiors")), config.getWorldSpawningBlocklist());
        assertFalse(config.getWorldSpawningBlocklist().contains(ResourceLocation.parse("cobbleventure:generation_1")));
    }

    @Test
    void legacyConfigIsNotMistakenForAnAppliedBlocklist() {
        assertFalse(InteriorSpawnPolicy.configure(new Object()));
    }

    public static final class Config {
        private Set<ResourceLocation> blocklist;
        public Set<ResourceLocation> getWorldSpawningBlocklist() { return blocklist; }
        public void setWorldSpawningBlocklist(Set<ResourceLocation> value) { blocklist = value; }
    }
}
