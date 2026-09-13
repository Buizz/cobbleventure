package dev.buizz.cobbleventure.adventure.mixin;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MegaShowdownRayquazaMixinTest {
    @Test
    void repeatsOnlyTheLastRealMoveWhenMegaShowdownChecksFourSlots() {
        assertEquals(0, MegaShowdownRayquazaMixin.boundedMoveIndex(1, 3));
        assertEquals(1, MegaShowdownRayquazaMixin.boundedMoveIndex(2, 2));
        assertEquals(2, MegaShowdownRayquazaMixin.boundedMoveIndex(3, 3));
        assertEquals(3, MegaShowdownRayquazaMixin.boundedMoveIndex(4, 3));
        assertThrows(IllegalArgumentException.class,
            () -> MegaShowdownRayquazaMixin.boundedMoveIndex(0, 0));
    }

    @Test
    void optionalCompatibilityMixinIsRegistered() throws Exception {
        try (var stream = getClass().getResourceAsStream("/cobbleventure_adventure.mixins.json")) {
            assertNotNull(stream);
            var root = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
            assertTrue(root.getAsJsonArray("mixins").asList().stream().anyMatch(value ->
                value.getAsString().equals("MegaShowdownRayquazaMixin")));
        }
    }
}
