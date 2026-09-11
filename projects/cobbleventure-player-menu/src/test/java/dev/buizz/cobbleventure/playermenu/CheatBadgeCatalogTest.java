package dev.buizz.cobbleventure.playermenu;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheatBadgeCatalogTest {
    @Test void includesEveryGenerationAndDeduplicates() {
        assertEquals(Set.of("cobbleventure:badge/kanto/boulder", "cobbleventure:badge/johto/zephyr"),
            CheatBadgeCatalog.readIds(new StringReader("""
                {"badges":[{"id":"cobbleventure:badge/kanto/boulder"},
                {"id":"cobbleventure:badge/johto/zephyr"},{"id":"cobbleventure:badge/kanto/boulder"}]}
                """)));
    }

    @Test void rejectsInvalidOrEmptyBatchBeforeGrantingAnything() {
        assertThrows(IllegalArgumentException.class, () -> CheatBadgeCatalog.readIds(new StringReader("{\"badges\":[]}")));
        assertThrows(IllegalArgumentException.class, () -> CheatBadgeCatalog.readIds(new StringReader("""
            {"badges":[{"id":"cobbleventure:badge/kanto/boulder"},{"id":"minecraft:stone"}]}
            """)));
    }

    @Test void acceptsCurrentAuthoredCatalog() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("../../content-projects/cobbleventure-main/content/catalogs/badges.json"))) {
            var ids = CheatBadgeCatalog.readIds(reader);
            assertTrue(ids.contains("cobbleventure:badge/kanto/boulder"));
            assertTrue(ids.size() > 8);
        }
    }
}
