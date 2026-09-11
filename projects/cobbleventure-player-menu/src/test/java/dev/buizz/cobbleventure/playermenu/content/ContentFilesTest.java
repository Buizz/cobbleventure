package dev.buizz.cobbleventure.content;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentFilesTest {
    @TempDir Path directory;

    @Test void externalContentCanChangeWithoutClasspathChanges() throws Exception {
        String previous = System.getProperty("cobbleventure.contentDirectory");
        System.setProperty("cobbleventure.contentDirectory", directory.toString());
        try {
            Path file = directory.resolve("data/test/trainer.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "first team");
            assertTrue(ContentFiles.exists("/data/test/trainer.json"));
            try (var stream = ContentFiles.open("/data/test/trainer.json")) {
                assertEquals("first team", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            Files.writeString(file, "second team");
            try (var stream = ContentFiles.open("/data/test/trainer.json")) {
                assertEquals("second team", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertNull(ContentFiles.open("data/test/missing.json"));
            assertThrows(IllegalArgumentException.class, () -> ContentFiles.open("../outside.json"));
        } finally {
            if (previous == null) System.clearProperty("cobbleventure.contentDirectory");
            else System.setProperty("cobbleventure.contentDirectory", previous);
        }
    }
}
