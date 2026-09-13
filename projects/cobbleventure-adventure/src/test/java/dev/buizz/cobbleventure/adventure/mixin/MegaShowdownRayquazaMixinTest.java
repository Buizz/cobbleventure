package dev.buizz.cobbleventure.adventure.mixin;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MegaShowdownRayquazaMixinTest {
    @Test
    void repeatsOnlyTheLastRealMoveWhenMegaShowdownChecksFourSlots() {
        assertEquals(0, MegaShowdownMoveIndex.bounded(1, 3));
        assertEquals(1, MegaShowdownMoveIndex.bounded(2, 2));
        assertEquals(2, MegaShowdownMoveIndex.bounded(3, 3));
        assertEquals(3, MegaShowdownMoveIndex.bounded(4, 3));
        assertThrows(IllegalArgumentException.class,
            () -> MegaShowdownMoveIndex.bounded(0, 0));
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

    @Test
    void mixinDoesNotDeclareNonPrivateStaticHelperMethods() {
        assertEquals(List.of(), Arrays.stream(MegaShowdownRayquazaMixin.class.getDeclaredMethods())
            .filter(method -> Modifier.isStatic(method.getModifiers()))
            .filter(method -> !Modifier.isPrivate(method.getModifiers()))
            .map(method -> method.getName())
            .toList());
    }
}
