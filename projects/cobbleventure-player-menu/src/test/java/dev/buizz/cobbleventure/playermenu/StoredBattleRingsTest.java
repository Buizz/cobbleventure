package dev.buizz.cobbleventure.playermenu;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class StoredBattleRingsTest {
    @Test
    void ownershipTracksRemovalAndRequiresMatchingRing() {
        var bag = new ArrayList<>(List.of("mega", "potion"));
        assertTrue(StoredBattleRings.contains("mega_showdown:mega_bracelet", bag, "mega"::equals));
        assertFalse(StoredBattleRings.contains("mega_showdown:z_ring", bag, "z"::equals));
        bag.remove("mega");
        assertFalse(StoredBattleRings.contains("mega_showdown:mega_bracelet", bag, "mega"::equals));
        bag.add("z");
        assertTrue(StoredBattleRings.contains("mega_showdown:z_ring", bag, "z"::equals));
    }

    @Test
    void otherEquipmentDoesNotGainPossessionBasedAccess() {
        for (String tag : List.of("omni_ring", "dynamax_band", "tera_orb")) {
            assertFalse(StoredBattleRings.contains("mega_showdown:" + tag, List.of("ring"), value -> true));
        }
    }
}
