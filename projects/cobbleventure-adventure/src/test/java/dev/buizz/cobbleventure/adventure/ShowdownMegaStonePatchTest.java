package dev.buizz.cobbleventure.adventure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ShowdownMegaStonePatchTest {
    @Test
    void upgradesOnceAndRejectsUnknownEngineVersions() {
        String source = "if (!stone) return null;\n" + ShowdownMegaStonePatch.TARGET + "\nreturn megaEvolution;";
        String patched = ShowdownMegaStonePatch.patch(source);
        assertTrue(patched.contains("typeof stone === \"string\""));
        assertEquals(patched, ShowdownMegaStonePatch.patch(patched));
        assertTrue(patched.startsWith("if (!stone) return null;"));
        assertTrue(patched.endsWith("return megaEvolution;"));
        assertThrows(IllegalStateException.class, () -> ShowdownMegaStonePatch.patch("unknown engine"));
    }
}
