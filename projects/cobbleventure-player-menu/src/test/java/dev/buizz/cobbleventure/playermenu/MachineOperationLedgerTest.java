package dev.buizz.cobbleventure.playermenu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MachineOperationLedgerTest {
    @Test
    void duplicateOperationCannotExecuteWhilePendingOrAfterCompletion() {
        MachineOperationLedger ledger = new MachineOperationLedger();
        UUID operationId = UUID.randomUUID();

        assertEquals(MachineOperationLedger.BeginResult.ACCEPTED, ledger.begin(operationId, "craft:protect:1"));
        assertEquals(MachineOperationLedger.BeginResult.IN_PROGRESS, ledger.begin(operationId, "craft:protect:1"));
        ledger.complete(operationId, "success");
        assertEquals(MachineOperationLedger.BeginResult.REPLAYED, ledger.begin(operationId, "craft:protect:1"));
        assertEquals("success", ledger.result(operationId));
    }

    @Test
    void reusedOperationIdWithDifferentRequestIsAConflict() {
        MachineOperationLedger ledger = new MachineOperationLedger();
        UUID operationId = UUID.randomUUID();

        ledger.begin(operationId, "craft:protect:1");

        assertEquals(MachineOperationLedger.BeginResult.CONFLICT, ledger.begin(operationId, "craft:surf:1"));
    }

    @Test
    void ledgerIsBounded() {
        MachineOperationLedger ledger = new MachineOperationLedger();
        for (int index = 0; index < 64; index++) {
            UUID operationId = UUID.randomUUID();
            ledger.begin(operationId, "craft:" + index);
            ledger.complete(operationId);
        }
        assertEquals(MachineOperationLedger.BeginResult.ACCEPTED,
            ledger.begin(UUID.randomUUID(), "craft:new"));
        assertEquals(64, ledger.size());
    }

    @Test
    void pendingOperationsAreNeverEvicted() {
        MachineOperationLedger ledger = new MachineOperationLedger();
        for (int index = 0; index < 64; index++) {
            ledger.begin(UUID.randomUUID(), "craft:" + index);
        }

        assertEquals(MachineOperationLedger.BeginResult.FULL,
            ledger.begin(UUID.randomUUID(), "craft:overflow"));
        assertEquals(64, ledger.size());
    }
}
