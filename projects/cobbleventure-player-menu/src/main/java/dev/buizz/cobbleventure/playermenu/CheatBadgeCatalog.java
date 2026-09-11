package dev.buizz.cobbleventure.playermenu;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.Set;

/** Validates the entire badge batch before any player progression is changed. */
final class CheatBadgeCatalog {
    private CheatBadgeCatalog() {}

    static Set<String> readIds(Reader reader) {
        Set<String> ids = new LinkedHashSet<>();
        for (var element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("badges")) {
            String id = element.getAsJsonObject().get("id").getAsString();
            if (!id.matches("cobbleventure:badge/[a-z0-9/._-]+") || id.length() > 128) {
                throw new IllegalArgumentException("잘못된 배지 ID: " + id);
            }
            ids.add(id);
        }
        if (ids.isEmpty() || ids.size() > 128) throw new IllegalArgumentException("배지 수는 1~128개여야 합니다.");
        return ids;
    }
}
