package dev.buizz.cobbleventure.adventure;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Narrow, idempotent compatibility patch for Cobblemon's extracted Showdown cache. */
public final class ShowdownEmptySlotsPatch {
    private ShowdownEmptySlotsPatch() {}

    public static String replace(String source, String before, String after) {
        return replace(source, before, after, List.of());
    }

    public static String replace(String source, String before, String after, List<String> legacy) {
        if (source.contains(after)) return source;
        for (String previous : legacy) {
            if (source.contains(previous)) return source.replace(previous, after);
        }
        if (source.contains(before)) return source.replace(before, after);
        throw new IllegalStateException("Unsupported Showdown empty-slot patch target: " + before);
    }

    public static void apply() {
        Path root = Path.of("showdown").toAbsolutePath().normalize();
        try (var stream = ShowdownEmptySlotsPatch.class.getResourceAsStream("/showdown-empty-slots.json")) {
            if (stream == null) throw new IllegalStateException("Missing Showdown empty-slot patch rules");
            var rules = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            Map<Path, String> changed = new LinkedHashMap<>();
            for (var entry : rules.entrySet()) {
                Path path = root.resolve(entry.getKey()).normalize();
                if (!path.toRealPath().startsWith(root.toRealPath())) {
                    throw new IllegalStateException("Showdown patch path escaped its cache directory");
                }
                String original = Files.readString(path);
                String updated = original;
                for (var rule : entry.getValue().getAsJsonArray()) {
                    var value = rule.getAsJsonObject();
                    List<String> legacy = value.has("legacy")
                        ? value.getAsJsonArray("legacy").asList().stream()
                            .map(element -> element.getAsString()).toList()
                        : List.of();
                    updated = replace(updated, value.get("from").getAsString(),
                        value.get("to").getAsString(), legacy);
                }
                if (!updated.equals(original)) changed.put(path, updated);
            }
            // Validate every target before changing any file. Only the generated engine cache changes.
            for (var entry : changed.entrySet()) Files.writeString(entry.getKey(), entry.getValue());
            if (!changed.isEmpty()) com.mojang.logging.LogUtils.getLogger().info(
                "Applied empty active-slot compatibility to {} Showdown cache files", changed.size()
            );
        } catch (IOException error) {
            throw new IllegalStateException("Failed to apply Showdown empty-slot compatibility", error);
        }
    }
}
