package dev.buizz.cobbleventure.battleai;

import java.util.ArrayList;
import java.util.List;

/** Replaces a six-slot party while rolling back if PC storage fails. */
final class AIBattleTestPartyReplacement {
    private AIBattleTestPartyReplacement() {}

    interface Storage<T> {
        T get(int slot);
        int freePcSlots();
        void removeFromParty(T value);
        boolean addToPc(T value);
        void removeFromPc(T value);
        void setParty(int slot, T value);
    }

    static <T> void replace(Storage<T> storage, List<T> replacement) {
        if (replacement.size() != 6 || replacement.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("테스트 파티는 포켓몬 6마리여야 합니다.");
        }
        List<T> original = new ArrayList<>(6);
        for (int slot = 0; slot < 6; slot++) original.add(storage.get(slot));
        long occupied = original.stream().filter(java.util.Objects::nonNull).count();
        if (storage.freePcSlots() < occupied) {
            throw new IllegalStateException("기존 파티를 보관할 PC 공간이 부족합니다.");
        }

        try {
            for (T value : original) {
                if (value == null) continue;
                storage.removeFromParty(value);
                if (!storage.addToPc(value)) {
                    throw new IllegalStateException("기존 포켓몬을 PC로 옮기지 못했습니다.");
                }
            }
            for (int slot = 0; slot < 6; slot++) storage.setParty(slot, replacement.get(slot));
        } catch (RuntimeException failure) {
            for (T value : replacement) storage.removeFromParty(value);
            for (int slot = 0; slot < 6; slot++) {
                T value = original.get(slot);
                if (value == null) continue;
                storage.removeFromPc(value);
                storage.setParty(slot, value);
            }
            throw failure;
        }
    }
}
