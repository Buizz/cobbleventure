package dev.buizz.cobbleventure.playermenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyReplacementTest {
    private static final List<String> TEAM = List.of("new0", "new1", "new2", "new3", "new4", "new5");

    @Test void fillsEmptyPartyEvenWithFullPc() {
        var storage = new Storage(0);
        PartyReplacement.replace(storage, TEAM);
        assertEquals(TEAM, storage.party);
        assertTrue(storage.pc.isEmpty());
    }

    @Test void archivesFullPartyAndPreservesExistingPc() {
        var storage = new Storage(7, "old0", "old1", "old2", "old3", "old4", "old5");
        storage.pc.add("already-in-pc");
        PartyReplacement.replace(storage, TEAM);
        assertEquals(TEAM, storage.party);
        assertEquals(List.of("already-in-pc", "old0", "old1", "old2", "old3", "old4", "old5"), storage.pc);
    }

    @Test void refusesInsufficientSpaceWithoutMutation() {
        var storage = new Storage(1, "old0", null, "old2");
        var before = new ArrayList<>(storage.party);
        assertThrows(IllegalStateException.class, () -> PartyReplacement.replace(storage, TEAM));
        assertEquals(before, storage.party);
        assertTrue(storage.pc.isEmpty());
    }

    @Test void restoresGapsAfterPcRejectsSecondPokemon() {
        var storage = new Storage(6, null, "old1", null, "old3");
        storage.rejectPc = "old3";
        var before = new ArrayList<>(storage.party);
        assertThrows(IllegalStateException.class, () -> PartyReplacement.replace(storage, TEAM));
        assertEquals(before, storage.party);
        assertTrue(storage.pc.isEmpty());
    }

    @Test void restoresOriginalsAfterPartialTeamInsertion() {
        var storage = new Storage(6, "old0", null, "old2");
        storage.rejectParty = "new3";
        var before = new ArrayList<>(storage.party);
        assertThrows(IllegalStateException.class, () -> PartyReplacement.replace(storage, TEAM));
        assertEquals(before, storage.party);
        assertTrue(storage.pc.isEmpty());
    }

    @Test void repeatedCommandArchivesPreviousTeamInsteadOfDeletingIt() {
        var storage = new Storage(6);
        PartyReplacement.replace(storage, TEAM);
        var next = List.of("next0", "next1", "next2", "next3", "next4", "next5");
        PartyReplacement.replace(storage, next);
        assertEquals(next, storage.party);
        assertEquals(TEAM, storage.pc);
    }

    private static final class Storage implements PartyReplacement.Storage<String> {
        final List<String> party = new ArrayList<>(Arrays.asList(new String[6]));
        final List<String> pc = new ArrayList<>();
        final int capacity;
        String rejectPc;
        String rejectParty;
        Storage(int capacity, String... original) {
            this.capacity = capacity;
            for (int i = 0; i < original.length; i++) party.set(i, original[i]);
        }
        public String get(int slot) { return party.get(slot); }
        public int freePcSlots() { return capacity - pc.size(); }
        public void removeFromParty(String pokemon) {
            int slot = party.indexOf(pokemon);
            if (slot >= 0) party.set(slot, null);
        }
        public boolean addToPc(String pokemon) {
            if (pokemon.equals(rejectPc) || freePcSlots() == 0) return false;
            return pc.add(pokemon);
        }
        public void removeFromPc(String pokemon) { pc.remove(pokemon); }
        public void setParty(int slot, String pokemon) {
            if (pokemon.equals(rejectParty)) throw new IllegalStateException("insertion failed");
            party.set(slot, pokemon);
        }
    }
}
