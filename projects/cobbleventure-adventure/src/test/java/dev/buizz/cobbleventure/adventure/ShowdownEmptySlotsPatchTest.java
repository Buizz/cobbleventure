package dev.buizz.cobbleventure.adventure;

import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ShowdownEmptySlotsPatchTest {
    @Test
    void everyRuleIsIdempotentAndUnknownEngineTextFailsExplicitly() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/showdown-empty-slots.json"))) {
            for (var file : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                for (var rule : file.getValue().getAsJsonArray()) {
                    String from = rule.getAsJsonObject().get("from").getAsString();
                    String to = rule.getAsJsonObject().get("to").getAsString();
                    String result = ShowdownEmptySlotsPatch.replace(from + "\n" + from, from, to);
                    assertEquals(to + "\n" + to, result);
                    assertEquals(result, ShowdownEmptySlotsPatch.replace(result, from, to));
                    var legacy = rule.getAsJsonObject().has("legacy")
                        ? rule.getAsJsonObject().getAsJsonArray("legacy").asList().stream()
                            .map(element -> element.getAsString()).toList()
                        : java.util.List.<String>of();
                    if (!legacy.isEmpty()) {
                        assertEquals(to, ShowdownEmptySlotsPatch.replace(legacy.getFirst(), from, to, legacy));
                    }
                    assertThrows(IllegalStateException.class, () ->
                        ShowdownEmptySlotsPatch.replace("different engine version", from, to, legacy));
                }
            }
        }
    }

    @Test
    void compatibilityIsScopedToAnActuallyMissingSlotInAOnePokemonDoublesTeam() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/showdown-empty-slots.json"))) {
            var rules = JsonParser.parseReader(reader).getAsJsonObject();
            for (var file : rules.entrySet()) {
                for (var rule : file.getValue().getAsJsonArray()) {
                    String replacement = rule.getAsJsonObject().get("to").getAsString();
                    assertTrue(replacement.contains("gameType === \"doubles\""), replacement);
                    assertTrue(replacement.contains("pokemon.length === 1"), replacement);
                }
            }

            String requestReplacement = rules.getAsJsonArray("sim/side.js").get(3)
                .getAsJsonObject().get("to").getAsString();
            assertTrue(requestReplacement.contains("!this.pokemon[index]"));
            assertTrue(requestReplacement.contains(": null"),
                "A transient empty slot in singles must remain null instead of becoming a fake moveset");
        }
    }

    @Test
    void everyRuleMatchesTheBundledCobblemonShowdownEngine() throws Exception {
        var sources = new HashMap<String, String>();
        try (var resource = getClass().getResourceAsStream("/data/cobblemon/showdown.zip")) {
            assertNotNull(resource, "Cobblemon's bundled Showdown engine must be on the test classpath");
            try (var zip = new ZipInputStream(resource)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (entry.isDirectory()) continue;
                    var bytes = new ByteArrayOutputStream();
                    zip.transferTo(bytes);
                    sources.put(entry.getName(), bytes.toString(StandardCharsets.UTF_8));
                }
            }
        }

        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/showdown-empty-slots.json"))) {
            for (var file : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                String source = sources.get(file.getKey());
                assertNotNull(source, file.getKey());
                for (var rule : file.getValue().getAsJsonArray()) {
                    var value = rule.getAsJsonObject();
                    source = ShowdownEmptySlotsPatch.replace(source,
                        value.get("from").getAsString(), value.get("to").getAsString());
                    assertTrue(source.contains(value.get("to").getAsString()));
                }
            }
        }
    }
}
