package dev.buizz.cobbleventure.bootstrap;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/** Resolves the direction a player should face after a building map transition. */
final class BuildingDestinationFacing {
    private BuildingDestinationFacing() {}

    static float yaw(String anchorType, Direction facing, Rotation rotation) {
        Direction arrivalFacing = rotation.rotate(facing);
        // A door's authored facing points through its front face. The landing point is
        // on the other side, so look away from the door and into the destination map.
        if ("door".equals(anchorType)) {
            arrivalFacing = arrivalFacing.getOpposite();
        }
        return arrivalFacing.toYRot();
    }
}
