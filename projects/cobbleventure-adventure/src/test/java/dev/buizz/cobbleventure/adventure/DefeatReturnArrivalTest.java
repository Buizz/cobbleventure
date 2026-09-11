package dev.buizz.cobbleventure.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static dev.buizz.cobbleventure.adventure.DefeatReturnArrival.Result.*;

import org.junit.jupiter.api.Test;

final class DefeatReturnArrivalTest {
    @Test
    void arrivalMustRemainNearDestinationBeforeItIsConfirmed() {
        var arrival = new DefeatReturnArrival(100);
        assertEquals(WAITING, arrival.check(101, true, 0));
        assertEquals(WAITING, arrival.check(109, true, 9));
        assertEquals(CONFIRMED, arrival.check(110, true, 16));
        // Once released, ordinary walking or dimension travel must not trigger a return.
        assertEquals(CONFIRMED, arrival.check(111, false, 10000));
    }

    @Test
    void delayedBattleTeleportRequiresANewArrivalWindow() {
        var arrival = new DefeatReturnArrival(100);
        assertEquals(WAITING, arrival.check(101, true, 0));
        assertEquals(DISPLACED, arrival.check(105, true, 10000));
        assertEquals(DISPLACED, arrival.check(110, true, 0));
        var retry = new DefeatReturnArrival(205);
        assertEquals(WAITING, retry.check(206, true, 0));
        assertEquals(CONFIRMED, retry.check(215, true, 0));
    }

    @Test
    void matchingCoordinatesInAnotherDimensionDoNotCountAsArrival() {
        assertEquals(DISPLACED, new DefeatReturnArrival(0).check(10, false, 0));
    }

    @Test
    void invalidPositionDoesNotCountAsArrival() {
        assertEquals(DISPLACED, new DefeatReturnArrival(0).check(10, true, Double.NaN));
    }
}
