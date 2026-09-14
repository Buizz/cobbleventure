package dev.buizz.cobbleventure.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RegionalNpcPairSpawnStateTest {
    @Test
    void waitsForAnAsynchronouslyImportedOwnerBeforeRequestingThePartner() {
        assertEquals(
            RegionalNpcPairSpawnState.Action.WAIT,
            RegionalNpcPairSpawnState.next(false, false, 1, 0, 0)
        );
        assertEquals(
            RegionalNpcPairSpawnState.Action.REQUEST_PARTNER,
            RegionalNpcPairSpawnState.next(true, false, 2, 0, 0)
        );
        assertEquals(
            RegionalNpcPairSpawnState.Action.WAIT,
            RegionalNpcPairSpawnState.next(true, false, 3, 1, 1)
        );
        assertEquals(
            RegionalNpcPairSpawnState.Action.COMPLETE,
            RegionalNpcPairSpawnState.next(true, true, 4, 1, 2)
        );
    }

    @Test
    void retriesARequestedPartnerOnlyAfterTheVisibilityGracePeriod() {
        assertEquals(
            RegionalNpcPairSpawnState.Action.WAIT,
            RegionalNpcPairSpawnState.next(true, false, 20, 1, 20)
        );
        assertEquals(
            RegionalNpcPairSpawnState.Action.REQUEST_PARTNER,
            RegionalNpcPairSpawnState.next(
                true, false, 41, 1, RegionalNpcPairSpawnState.RETRY_DELAY_TICKS
            )
        );
    }

    @Test
    void givesUpAfterTheBoundedRepairWindow() {
        assertEquals(
            RegionalNpcPairSpawnState.Action.GIVE_UP,
            RegionalNpcPairSpawnState.next(
                true, false, RegionalNpcPairSpawnState.MAX_AGE_TICKS,
                RegionalNpcPairSpawnState.MAX_REQUESTS,
                RegionalNpcPairSpawnState.RETRY_DELAY_TICKS
            )
        );
    }
}
