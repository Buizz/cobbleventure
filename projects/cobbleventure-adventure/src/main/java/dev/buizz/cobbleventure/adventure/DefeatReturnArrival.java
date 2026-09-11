package dev.buizz.cobbleventure.adventure;

/** Confirms a teleport over several ticks before recovery releases NPC interaction. */
final class DefeatReturnArrival {
    enum Result { WAITING, CONFIRMED, DISPLACED }

    private final long requestedAt;
    private Result result = Result.WAITING;

    DefeatReturnArrival(long requestedAt) {
        this.requestedAt = requestedAt;
    }

    Result check(long gameTime, boolean sameDimension, double distanceSquared) {
        if (result != Result.WAITING) return result;
        if (!sameDimension || !Double.isFinite(distanceSquared) || distanceSquared > 16.0D) {
            result = Result.DISPLACED;
        } else if (gameTime - requestedAt >= 10L) {
            result = Result.CONFIRMED;
        }
        return result;
    }
}
