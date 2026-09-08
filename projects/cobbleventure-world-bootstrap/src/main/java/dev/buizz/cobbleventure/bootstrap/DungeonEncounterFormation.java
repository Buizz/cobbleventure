package dev.buizz.cobbleventure.bootstrap;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Defines a symmetric two-trainer formation around an authored encounter marker. */
final class DungeonEncounterFormation {
    static final int PLAYER_DISTANCE = 5;
    static final int ANCHOR_SEARCH_RADIUS = PLAYER_DISTANCE + 1;

    private DungeonEncounterFormation() {}

    static Formation create(BlockPos center, float opponentYaw, int actorCount) {
        Direction facing = Direction.fromYRot(opponentYaw);
        if (actorCount <= 1) {
            return new Formation(
                List.of(center), List.of(center.relative(facing, PLAYER_DISTANCE)),
                facing
            );
        }
        if (actorCount != 2) {
            throw new IllegalStateException(
                "Dungeon battle formation supports one or two actors: " + actorCount
            );
        }
        Direction right = facing.getClockWise();
        BlockPos firstOpponent = center.relative(right.getOpposite());
        BlockPos secondOpponent = center.relative(right);
        return new Formation(
            List.of(firstOpponent, secondOpponent),
            List.of(
                firstOpponent.relative(facing, PLAYER_DISTANCE),
                secondOpponent.relative(facing, PLAYER_DISTANCE)
            ),
            facing
        );
    }

    static BlockPos resolveSafePlayerAnchor(
        BlockPos requestedCenter,
        float opponentYaw,
        int actorCount,
        Predicate<BlockPos> safePosition
    ) {
        if (playersAreSafe(
            create(requestedCenter, opponentYaw, actorCount), safePosition
        )) return requestedCenter;
        for (int distance = 1; distance <= ANCHOR_SEARCH_RADIUS; distance++) {
            for (int xOffset = -distance; xOffset <= distance; xOffset++) {
                int zDistance = distance - Math.abs(xOffset);
                BlockPos first = requestedCenter.offset(xOffset, 0, -zDistance);
                if (playersAreSafe(
                    create(first, opponentYaw, actorCount), safePosition
                )) return first;
                if (zDistance == 0) continue;
                BlockPos second = requestedCenter.offset(xOffset, 0, zDistance);
                if (playersAreSafe(
                    create(second, opponentYaw, actorCount), safePosition
                )) return second;
            }
        }
        return null;
    }

    private static boolean playersAreSafe(
        Formation formation, Predicate<BlockPos> safePosition
    ) {
        return formation.players().stream().allMatch(safePosition);
    }

    record Formation(
        List<BlockPos> opponents,
        List<BlockPos> players,
        Direction opponentFacing
    ) {
        float playerYaw() {
            return opponentFacing.getOpposite().toYRot();
        }
    }
}
