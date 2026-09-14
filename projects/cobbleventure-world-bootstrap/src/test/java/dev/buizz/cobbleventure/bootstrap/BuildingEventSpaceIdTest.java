package dev.buizz.cobbleventure.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildingEventSpaceIdTest {
    @Test
    void exposesOnlyResourceIdsToTheV5BoundarySnapshot() {
        assertTrue(BuildingEventSpaceIds.isPublic(
            "cobbleventure:building/pokemon_center"
        ));
        assertFalse(BuildingEventSpaceIds.isPublic(
            "__daycare_instance__|cobbleventure:generation_1|"
                + "cobbleventure:placeholder/daycare|1264,71,-314"
        ));
    }

    @Test
    void retainsWorldObjectInteriorsUnderPrivatePerInstanceKeys() {
        String instance = "cobbleventure:generation_1|"
            + "cobbleventure:league/indigo_plateau|-64,70,-128";
        String key = BuildingEventSpaceIds.registrationKey(null, instance, false);

        assertEquals("__building_instance__|" + instance, key);
        assertFalse(BuildingEventSpaceIds.isPublic(key));
        assertEquals(
            "cobbleventure:building/pokemon_center",
            BuildingEventSpaceIds.registrationKey(
                "cobbleventure:building/pokemon_center", instance, false
            )
        );
    }
}
