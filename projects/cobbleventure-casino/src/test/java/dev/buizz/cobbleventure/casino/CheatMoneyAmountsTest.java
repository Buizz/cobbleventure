package dev.buizz.cobbleventure.casino;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheatMoneyAmountsTest {
    @Test void bothCurrenciesUseTheRequestedTwoBillionBalanceWithinSignedIntRange() {
        assertEquals(2_000_000_000L, CheatMoneyAmounts.CHIPS);
        assertEquals(new BigInteger("2000000000"), CheatMoneyAmounts.DOLLARS);
        assertEquals(2_000_000_000, Math.toIntExact(CheatMoneyAmounts.CHIPS));
        assertEquals(2_000_000_000, CheatMoneyAmounts.DOLLARS.intValueExact());
        assertTrue(CheatMoneyAmounts.CHIPS < Integer.MAX_VALUE);
    }
}
