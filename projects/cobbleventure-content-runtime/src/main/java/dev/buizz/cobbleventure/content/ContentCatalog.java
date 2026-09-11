package dev.buizz.cobbleventure.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Required startup definitions. Invalid or absent content never falls back to engine defaults. */
public final class ContentCatalog {
    private ContentCatalog() {}

    public static JsonObject read(String path) {
        try (var stream = ContentFiles.open(path)) {
            if (stream == null) throw new IllegalStateException("Missing content catalog: " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (java.io.IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load content catalog: " + path, error);
        }
    }
}
