package dev.buizz.cobbleventure.bootstrap;

/** Arrival points never create a reverse trigger when used as route destinations. */
final class BuildingConnectionTypes {
    private BuildingConnectionTypes() {}

    static boolean isTrigger(String type) {
        return "door".equals(type) || "transition".equals(type);
    }

    static boolean isDestination(String type) {
        return isTrigger(type) || "arrival".equals(type)
            || "interior_spawn".equals(type) || "exterior_spawn".equals(type);
    }
}
