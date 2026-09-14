package dev.buizz.cobbleventure.battleai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AIBattleTestPartyReplacementTest {
    @Test
    void preservesExistingPartyInPcAndInstallsSixTestMembers() {
        FakeStorage storage = new FakeStorage(List.of("old1", "old2", "old3"), 10);
        List<String> replacement = List.of("new1", "new2", "new3", "new4", "new5", "new6");

        AIBattleTestPartyReplacement.replace(storage, replacement);

        assertEquals(replacement, storage.party);
        assertEquals(List.of("old1", "old2", "old3"), storage.pc);
    }

    @Test
    void rejectsReplacementBeforeMovingAnythingWhenPcIsFull() {
        FakeStorage storage = new FakeStorage(List.of("old1", "old2"), 1);

        assertThrows(IllegalStateException.class, () -> AIBattleTestPartyReplacement.replace(
                storage, List.of("new1", "new2", "new3", "new4", "new5", "new6")));
        assertEquals(Arrays.asList("old1", "old2", null, null, null, null), storage.party);
        assertEquals(List.of(), storage.pc);
    }

    private static final class FakeStorage implements AIBattleTestPartyReplacement.Storage<String> {
        private final List<String> party = new ArrayList<>();
        private final List<String> pc = new ArrayList<>();
        private final int pcCapacity;

        private FakeStorage(List<String> initialParty, int pcCapacity) {
            party.addAll(initialParty);
            while (party.size() < 6) party.add(null);
            this.pcCapacity = pcCapacity;
        }

        @Override public String get(int slot) { return party.get(slot); }
        @Override public int freePcSlots() { return pcCapacity - pc.size(); }
        @Override public void removeFromParty(String value) { party.replaceAll(it -> value.equals(it) ? null : it); }
        @Override public boolean addToPc(String value) { return pc.size() < pcCapacity && pc.add(value); }
        @Override public void removeFromPc(String value) { pc.remove(value); }
        @Override public void setParty(int slot, String value) { party.set(slot, value); }
    }
}
