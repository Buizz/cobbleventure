package dev.buizz.cobbleventure.casino;

import java.math.BigInteger;

/** A bounded test balance below Integer.MAX_VALUE for both currency integrations. */
final class CheatMoneyAmounts {
    static final long CHIPS = 2_000_000_000L;
    static final BigInteger DOLLARS = BigInteger.valueOf(CHIPS);

    private CheatMoneyAmounts() {}
}
