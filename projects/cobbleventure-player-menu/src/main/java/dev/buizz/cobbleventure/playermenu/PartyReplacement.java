package dev.buizz.cobbleventure.playermenu;

import java.util.ArrayList;
import java.util.List;

/** Preserves the exact original slots if moving a party into its PC fails. */
final class PartyReplacement {
    private PartyReplacement() {}

    interface Storage<T> {
        T get(int slot);
        int freePcSlots();
        void removeFromParty(T pokemon);
        boolean addToPc(T pokemon);
        void removeFromPc(T pokemon);
        void setParty(int slot, T pokemon);
    }

    static <T> void replace(Storage<T> storage, List<T> team) {
        if (team.size() != 6 || team.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("새 파티는 포켓몬 6마리여야 합니다.");
        }
        List<T> original = new ArrayList<>();
        for (int slot = 0; slot < 6; slot++) original.add(storage.get(slot));
        long occupied = original.stream().filter(java.util.Objects::nonNull).count();
        if (storage.freePcSlots() < occupied) {
            throw new IllegalStateException("기존 파티를 보관할 PC 공간이 부족합니다.");
        }
        try {
            for (T pokemon : original) {
                if (pokemon == null) continue;
                storage.removeFromParty(pokemon);
                if (!storage.addToPc(pokemon)) throw new IllegalStateException("기존 포켓몬을 PC로 옮기지 못했습니다.");
            }
            for (int slot = 0; slot < 6; slot++) storage.setParty(slot, team.get(slot));
        } catch (RuntimeException failure) {
            for (T pokemon : team) storage.removeFromParty(pokemon);
            for (int slot = 0; slot < 6; slot++) {
                T pokemon = original.get(slot);
                if (pokemon == null) continue;
                storage.removeFromPc(pokemon);
                storage.setParty(slot, pokemon);
            }
            throw failure;
        }
    }
}
