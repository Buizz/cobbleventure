package dev.buizz.cobbleventure.playermenu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class MachinesTagTest {
    @Test
    void nativeAndLegacyTechnicalMachinesAreCategorized() throws Exception {
        try (var stream = dev.buizz.cobbleventure.content.ContentFiles.open(
            "/data/cobbleventure_player_menu/tags/item/machines.json"
        )) {
            String tag = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(tag.contains("\"id\": \"cobblemon:technical_machine\""));
            assertTrue(tag.contains("\"id\": \"#tmcraft:tm_moves\""));
        }
    }
}
