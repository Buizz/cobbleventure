package dev.buizz.cobbleventure.adventure.research;

/** Pure validation/quotation shared by the server and its regression tests. */
public final class ResearchPolicy {
    private ResearchPolicy() {}
    private static String id(String value) { return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    public static boolean zCompatible(String type, String requiredMove, java.util.Collection<String> users,
                                      String pokemon, java.util.Collection<String> moves, java.util.Collection<String> types) {
        if (requiredMove.isBlank()) return users.isEmpty() && types.stream().anyMatch(t -> id(t).equals(id(type)));
        return users.stream().anyMatch(u -> id(u).equals(id(pokemon)))
            && moves.stream().anyMatch(m -> id(m).equals(id(requiredMove)));
    }
    public static long statCost(int[] current, int[] target, int limit, int totalLimit, long price) {
        if (current.length != 6 || target.length != 6 || price < 0) throw new IllegalArgumentException("Invalid stats");
        long cost = 0;
        int total = 0;
        for (int i = 0; i < 6; i++) {
            if (target[i] < 0 || target[i] > limit) throw new IllegalArgumentException("Stat outside range");
            total += target[i];
            cost = Math.addExact(cost, Math.multiplyExact(Math.max(0L, (long) target[i] - current[i]), price));
        }
        if (total > totalLimit) throw new IllegalArgumentException("Stat total exceeded");
        return cost;
    }
    public static int mushrooms(int before, int after, boolean oldFactor, boolean factor, boolean supported,
                                int perLevel, int factorCost) {
        if (after < 0 || after > 10 || (factor && !supported) || perLevel < 0 || factorCost < 0)
            throw new IllegalArgumentException("Invalid Dynamax target");
        return Math.addExact(Math.multiplyExact(Math.max(0, after - before), perLevel),
            factor && !oldFactor ? factorCost : 0);
    }
}
