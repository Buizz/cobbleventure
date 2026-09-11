package dev.buizz.cobbleventure.bootstrap;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AuthoredNpcIdentityTest {
    @Test void recognizesNewAndAlreadyPlacedLeagueNpcs() {
        for (String stage : new String[]{"elite_1", "elite_2", "elite_3", "elite_4", "champion"}) {
            String identity = "cobbleventure_npc/cobbleventure/npc/league/kanto_" + stage;
            assertTrue(AuthoredNpcIdentity.matches(Set.of(identity), identity));
            assertTrue(AuthoredNpcIdentity.matches(Set.of(
                "cobbleventure_regional_npc", "cves_binding/cobbleventure/league/kanto_" + stage
            ), identity));
        }
    }

    @Test void doesNotMatchOtherTrainersOrUnrelatedCustomScripts() {
        Set<String> tags = Set.of("cves_binding/cobbleventure/league/kanto_elite_1");
        assertFalse(AuthoredNpcIdentity.matches(tags,
            "cobbleventure_npc/cobbleventure/npc/league/kanto_elite_2"));
        assertFalse(AuthoredNpcIdentity.matches(tags,
            "cobbleventure_npc/cobbleventure/npc/custom/kanto_elite_1"));
        assertFalse(AuthoredNpcIdentity.matches(tags, null));
        assertFalse(AuthoredNpcIdentity.matches(Set.of("cobbleventure_regional_npc"),
            "cobbleventure_npc/cobbleventure/npc/league/kanto_elite_1"));
    }
}
