package dev.buizz.cobbleventure.playermenu;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheatBatchTest {
    @Test void allIncludesEveryIndividualCheatExactlyOnceWithoutRecursing() {
        List<String> executed = new ArrayList<>();
        assertTrue(CheatBatch.failures(name -> { executed.add(name); return 1; }).isEmpty());
        assertEquals(List.of("badges", "hm", "menus", "destinations", "tm",
            "team", "money", "key_items", "items"), executed);
    }

    @Test void failedTeamAndMissingCasinoDoNotPreventItemGrants() {
        List<String> executed = new ArrayList<>();
        var failures = CheatBatch.failures(name -> {
            executed.add(name);
            if (name.equals("team")) return 0;
            if (name.equals("money")) throw new IllegalStateException("command unavailable");
            return 1;
        });
        assertEquals(List.of("team", "money (command unavailable)"), failures);
        assertEquals(CheatBatch.ACTIONS, executed);
    }

    @Test void eachTargetHasIndependentFailureResults() {
        assertEquals(9, CheatBatch.failures(name -> 0).size());
        assertTrue(CheatBatch.failures(name -> 1).isEmpty());
    }
}
