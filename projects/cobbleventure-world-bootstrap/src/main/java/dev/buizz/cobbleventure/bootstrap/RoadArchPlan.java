package dev.buizz.cobbleventure.bootstrap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Read-only preflight. No world writes are allowed until the complete plan succeeds. */
final class RoadArchPlan {
    static final int MAX_FOUNDATION_HEIGHT = 8;
    enum Cell { EMPTY, REPLACEABLE, TERRAIN, SOLID, FLUID }
    enum Failure { NONE, EMPTY_TEMPLATE, STEEP, OBSTACLE, WATER, BLOCKS_PASSAGE }
    interface Ground {
        int surfaceY(int x, int z);
        Cell cell(BlockPos position);
    }
    record Plan(BlockPos origin, List<BlockPos> foundations, Failure failure, BlockPos rejectedAt) {
        boolean allowed() { return failure == Failure.NONE; }
    }

    private RoadArchPlan() {}

    static BlockPos offset(BlockPos local, RoadArchPlacement.Site site) {
        return StructureTemplate.transform(local.offset(-9, 0, -2), Mirror.NONE, site.rotation(), BlockPos.ZERO)
            .offset(RoadArchPlacement.shift(local.getZ(), site));
    }

    static Plan create(List<BlockPos> blocks, RoadArchPlacement.Site site,
                       List<RoadArchPlacement.Point> road, Ground ground) {
        if (blocks.isEmpty()) return failed(Failure.EMPTY_TEMPLATE, BlockPos.ZERO);
        int highest = ground.surfaceY(site.x(), site.z()), lowest = highest;
        boolean hasFooting = false;
        for (BlockPos local : blocks) {
            BlockPos offset = offset(local, site);
            int y = ground.surfaceY(site.x() + offset.getX(), site.z() + offset.getZ());
            highest = Math.max(highest, y);
            if (local.getY() == 0) {
                hasFooting = true;
                lowest = Math.min(lowest, y);
            }
        }
        BlockPos center = new BlockPos(site.x(), highest, site.z());
        if (!hasFooting) return failed(Failure.EMPTY_TEMPLATE, center);
        if (highest - lowest > MAX_FOUNDATION_HEIGHT) return failed(Failure.STEEP, center);
        Set<BlockPos> foundations = new LinkedHashSet<>();
        for (BlockPos local : blocks) {
            BlockPos target = center.offset(offset(local, site));
            Cell cell = ground.cell(target);
            if (cell == Cell.FLUID) return failed(Failure.WATER, target);
            if (cell == Cell.SOLID || (local.getY() > 0 && cell == Cell.TERRAIN)) {
                return failed(Failure.OBSTACLE, target);
            }
            int surfaceY = ground.surfaceY(target.getX(), target.getZ());
            if (target.getY() > surfaceY && target.getY() <= surfaceY + 3
                && passageDistance(target, road) < 2) return failed(Failure.BLOCKS_PASSAGE, target);
            if (local.getY() != 0) continue;
            for (int y = highest - 1; y >= surfaceY; y--) {
                BlockPos support = new BlockPos(target.getX(), y, target.getZ());
                Cell below = ground.cell(support);
                if (below == Cell.FLUID) return failed(Failure.WATER, support);
                if (below == Cell.SOLID) return failed(Failure.OBSTACLE, support);
                if (below == Cell.EMPTY || below == Cell.REPLACEABLE) {
                    if (passageDistance(support, road) < 2) return failed(Failure.BLOCKS_PASSAGE, support);
                    foundations.add(support);
                }
            }
        }
        BlockPos anchor = StructureTemplate.transform(new BlockPos(9, 0, 2), Mirror.NONE, site.rotation(), BlockPos.ZERO);
        return new Plan(center.subtract(anchor), List.copyOf(foundations), Failure.NONE, null);
    }

    private static Plan failed(Failure failure, BlockPos position) {
        return new Plan(BlockPos.ZERO, List.of(), failure, position);
    }

    private static double passageDistance(BlockPos p, List<RoadArchPlacement.Point> road) {
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 1; i < road.size(); i++) {
            var a = road.get(i - 1); var b = road.get(i);
            double dx = b.x() - a.x(), dz = b.z() - a.z();
            double length = dx * dx + dz * dz;
            double t = length == 0 ? 0 : Math.clamp(((p.getX() - a.x()) * dx + (p.getZ() - a.z()) * dz) / length, 0, 1);
            distance = Math.min(distance, Math.hypot(p.getX() - a.x() - t * dx, p.getZ() - a.z() - t * dz));
        }
        return distance;
    }
}
