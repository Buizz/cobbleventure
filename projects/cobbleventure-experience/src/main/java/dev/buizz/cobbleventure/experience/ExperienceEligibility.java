package dev.buizz.cobbleventure.experience;

/** Pure eligibility rules shared by battle and capture experience payouts. */
public final class ExperienceEligibility {
    private ExperienceEligibility() {}

    public static double multiplier(
        boolean alive, boolean participated, boolean hasExperienceShare, double shareMultiplier
    ) {
        if (!alive || (!participated && !hasExperienceShare)) return 0.0D;
        return participated ? 1.0D : Math.max(0.0D, shareMultiplier);
    }
}
