package dev.buizz.cobbleventure.bootstrap;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Sparse road furniture templates; never paint the road or clear surrounding air. */
final class RoadArchPlacement {
    record Point(int x, int z) {}
    record Site(int x, int z, Rotation rotation) {}
    private RoadArchPlacement() {}

    static List<Site> sites(List<Point> points) {
        List<Point> straight = new ArrayList<>();
        for (Point point : points) {
            if (!straight.isEmpty() && straight.getLast().equals(point)) continue;
            while (straight.size() >= 2) {
                Point a = straight.get(straight.size()-2), b = straight.getLast();
                int dx=b.x-a.x, dz=b.z-a.z, nx=point.x-b.x, nz=point.z-b.z;
                if (!((dx==0 && nx==0 && Integer.signum(dz)==Integer.signum(nz))
                    || (dz==0 && nz==0 && Integer.signum(dx)==Integer.signum(nx)))) break;
                straight.removeLast();
            }
            straight.add(point);
        }
        points = straight;
        List<Site> result = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i - 1), b = points.get(i);
            int dx = b.x - a.x, dz = b.z - a.z;
            // NBT has quarter-turn rotations. Keep arches off diagonal bends,
            // where their narrow opening would obstruct the walking centerline.
            if ((dx != 0 && dz != 0) || (dx == 0 && dz == 0)) continue;
            int length = Math.abs(dx) + Math.abs(dz);
            Rotation rotation = dz > 0 ? Rotation.NONE : dz < 0 ? Rotation.CLOCKWISE_180
                : dx > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
            for (int distance = 12; distance <= length - 12; distance += 12) {
                Site site = new Site(a.x + Integer.signum(dx) * distance,
                    a.z + Integer.signum(dz) * distance, rotation);
                if (result.stream().noneMatch(old -> Math.hypot(old.x-site.x, old.z-site.z) < 12)) {
                    result.add(site);
                }
            }
        }
        return List.copyOf(result);
    }

    static void place(ServerLevel level, List<Point> centerline) {
        int variant = 0;
        for (Site site : sites(centerline)) {
            String suffix = new String[]{"", "_2", "_3"}[variant++ % 3];
            var template = level.getStructureManager().get(ResourceLocation.parse(
                "cobbleventure:road_decorations/league_arch" + suffix));
            if (template.isEmpty()) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.x, site.z) - 1;
            BlockPos anchor = StructureTemplate.transform(new BlockPos(9, 0, 2),
                Mirror.NONE, site.rotation, BlockPos.ZERO);
            BlockPos origin = new BlockPos(site.x, y, site.z).subtract(anchor);
            var size = template.get().getSize();
            boolean clear = true;
            // Require a nearly level, empty footprint: do not replace buildings,
            // trees or other road furniture, and do not leave floating pillars.
            for (int x = 0; x < size.getX() && clear; x++) {
                for (int z = 0; z < size.getZ() && clear; z++) {
                    BlockPos ground = origin.offset(StructureTemplate.transform(new BlockPos(x, 0, z),
                        Mirror.NONE, site.rotation, BlockPos.ZERO));
                    int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        ground.getX(), ground.getZ()) - 1;
                    if (Math.abs(height-y) > 1 || !level.getFluidState(ground).isEmpty()) { clear=false; break; }
                    for (int up = 1; up < size.getY(); up++) {
                        var state = level.getBlockState(ground.above(up));
                        if (!state.isAir() && !state.canBeReplaced()) { clear=false; break; }
                    }
                }
            }
            if (!clear) continue;
            template.get().placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setRotation(site.rotation).setIgnoreEntities(true),
                RandomSource.create(origin.asLong()), 2);
        }
    }
}
