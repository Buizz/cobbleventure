package dev.buizz.cobbleventure.bootstrap;

import net.minecraft.resources.ResourceLocation;

/** Separates authored V5 building IDs from internal per-instance registry keys. */
final class BuildingEventSpaceIds {
    private BuildingEventSpaceIds() {}

    static String registrationKey(
        String eventSpaceId, String instanceKey, boolean daycare
    ) {
        if (eventSpaceId != null && !eventSpaceId.isBlank()) {
            return eventSpaceId;
        }
        return (daycare ? "__daycare_instance__|" : "__building_instance__|")
            + instanceKey;
    }

    static boolean isPublic(String value) {
        return ResourceLocation.tryParse(value) != null;
    }
}
