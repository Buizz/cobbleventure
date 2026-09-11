package dev.buizz.cobbleventure.playermenu;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Bounded per-session ledger that prevents machine requests from executing twice. */
final class MachineOperationLedger {
    private static final int MAX_ENTRIES = 64;
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();

    BeginResult begin(UUID operationId, String fingerprint) {
        if (operationId == null || fingerprint == null || fingerprint.isBlank()) {
            return BeginResult.INVALID;
        }
        Entry existing = entries.get(operationId);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) return BeginResult.CONFLICT;
            return existing.completed() ? BeginResult.REPLAYED : BeginResult.IN_PROGRESS;
        }
        if (entries.size() >= MAX_ENTRIES) {
            UUID oldestCompleted = null;
            for (var candidate : entries.entrySet()) {
                if (candidate.getValue().completed()) {
                    oldestCompleted = candidate.getKey();
                    break;
                }
            }
            if (oldestCompleted == null) return BeginResult.FULL;
            entries.remove(oldestCompleted);
        }
        entries.put(operationId, new Entry(fingerprint, false, ""));
        return BeginResult.ACCEPTED;
    }

    void complete(UUID operationId) {
        complete(operationId, "");
    }

    void complete(UUID operationId, String result) {
        Entry entry = entries.get(operationId);
        if (entry != null) entries.put(operationId, new Entry(entry.fingerprint(), true, result));
    }

    String result(UUID operationId) {
        Entry entry = entries.get(operationId);
        return entry == null ? "" : entry.result();
    }

    int size() {
        return entries.size();
    }

    enum BeginResult { ACCEPTED, IN_PROGRESS, REPLAYED, CONFLICT, INVALID, FULL }
    record Entry(String fingerprint, boolean completed, String result) {}
}
