package dev.buizz.cobbleventure.bootstrap;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BuildingDestinationYawTest {
    @Test
    void facesAwayFromDoorAfterEnteringEitherSide() {
        assertEquals(
            Direction.SOUTH.toYRot(),
            BuildingDestinationFacing.yaw("door", Direction.NORTH, Rotation.NONE)
        );
        assertEquals(
            Direction.NORTH.toYRot(),
            BuildingDestinationFacing.yaw("door", Direction.SOUTH, Rotation.NONE)
        );
    }

    @Test
    void appliesStructureRotationBeforeFacingAwayFromDoor() {
        assertEquals(
            Direction.WEST.toYRot(),
            BuildingDestinationFacing.yaw(
                "door", Direction.NORTH, Rotation.CLOCKWISE_90
            )
        );
    }

    @Test
    void keepsAuthoredFacingForNonDoorArrivalAnchors() {
        assertEquals(
            Direction.EAST.toYRot(),
            BuildingDestinationFacing.yaw(
                "arrival", Direction.NORTH, Rotation.CLOCKWISE_90
            )
        );
        assertEquals(
            Direction.SOUTH.toYRot(),
            BuildingDestinationFacing.yaw(
                "transition", Direction.EAST, Rotation.CLOCKWISE_90
            )
        );
    }
}
