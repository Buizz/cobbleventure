package dev.buizz.cobbleventure.bootstrap;

/**
 * Small state policy for EasyNPC pair creation. EasyNPC preset imports may
 * become visible to the level after the command that requested them returns.
 */
final class RegionalNpcPairSpawnState {
    static final int RETRY_DELAY_TICKS = 40;
    static final int MAX_REQUESTS = 3;
    static final int MAX_AGE_TICKS = 240;

    private RegionalNpcPairSpawnState() {}

    enum Action {
        WAIT,
        REQUEST_PARTNER,
        COMPLETE,
        GIVE_UP
    }

    static Action next(
        boolean ownerPresent, boolean partnerPresent, int ageTicks,
        int partnerRequests, int ticksSincePartnerRequest
    ) {
        if (ownerPresent && partnerPresent) return Action.COMPLETE;
        if (ageTicks >= MAX_AGE_TICKS) return Action.GIVE_UP;
        if (!ownerPresent) return Action.WAIT;
        if (partnerRequests == 0
            || (partnerRequests < MAX_REQUESTS
                && ticksSincePartnerRequest >= RETRY_DELAY_TICKS)) {
            return Action.REQUEST_PARTNER;
        }
        return Action.WAIT;
    }
}
