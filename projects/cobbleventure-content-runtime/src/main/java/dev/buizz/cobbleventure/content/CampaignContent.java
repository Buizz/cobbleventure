package dev.buizz.cobbleventure.content;

import com.google.gson.JsonObject;

/** Campaign identifiers and progression conventions authored by the content project. */
public final class CampaignContent {
    private static final JsonObject VALUES = ContentCatalog.read(
        "data/cobbleventure/catalogs/campaign.json").getAsJsonObject("values");

    private CampaignContent() {}

    public static String text(String key) {
        var value = VALUES.get(key);
        if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            throw new IllegalStateException("Missing campaign setting: " + key);
        }
        return value.getAsString();
    }
}
