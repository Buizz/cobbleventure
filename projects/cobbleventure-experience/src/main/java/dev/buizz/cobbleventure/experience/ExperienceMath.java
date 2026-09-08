package dev.buizz.cobbleventure.experience;

/** Small deterministic helpers kept separate from rendering for regression tests. */
public final class ExperienceMath {
    private ExperienceMath() {}

    public static float progress(int experience, int levelStart, int nextLevel) {
        if (nextLevel <= levelStart) return 1.0F;
        return Math.clamp(
            (float)(experience - levelStart) / (float)(nextLevel - levelStart), 0.0F, 1.0F
        );
    }

    public static int captureExperience(
        int baseYield, int defeatedLevel, int recipientLevel, double participationMultiplier,
        double tradedMultiplier, double luckyEggMultiplier, double evolutionMultiplier,
        double friendshipMultiplier, double globalMultiplier
    ) {
        if (baseYield <= 0 || defeatedLevel <= 0 || participationMultiplier <= 0.0D) return 0;
        double base = (double)baseYield * defeatedLevel / 5.0D;
        double scaling = Math.pow(
            (2.0D * defeatedLevel + 10.0D)
                / (defeatedLevel + Math.max(1, recipientLevel) + 10.0D),
            2.5D
        );
        return Math.max(0, (int)Math.round(
            (base * participationMultiplier * scaling + 1.0D)
                * tradedMultiplier * luckyEggMultiplier * evolutionMultiplier
                * friendshipMultiplier * globalMultiplier
        ));
    }
}
