package dev.buizz.cobbleventure.bootstrap;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Sparse road furniture templates; never paint the road or clear surrounding air. */
final class RoadArchPlacement {
    record Point(int x, int z) {}
    record Site(int x, int z, Rotation rotation, double slope) {}
    private RoadArchPlacement() {}
    static final int SPACING = 8;
    private static final int END_CLEARANCE = 18;
    // The original module includes trailing ornaments at z=11. Keep the arch and
    // its front statues (z=0..5) so neighboring eight-block repeats do not overlap.
    static boolean repeatedBlock(BlockPos local) { return local.getZ() < 6; }
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    static List<Site> sites(List<Point> points) {
        List<Site> result = new ArrayList<>();
        double length = 0;
        for (int i = 1; i < points.size(); i++) length += Math.hypot(points.get(i).x-points.get(i-1).x, points.get(i).z-points.get(i-1).z);
        for (double distance = END_CLEARANCE; distance <= length - END_CLEARANCE; distance += SPACING) {
            double[] center = sample(points, distance), before = sample(points, distance-6), after = sample(points, distance+6);
            double dx = after[0]-before[0], dz = after[1]-before[1];
            double ax = center[0]-before[0], az = center[1]-before[1], bx = after[0]-center[0], bz = after[1]-center[1];
            if (ax*bx+az*bz < .8*Math.hypot(ax,az)*Math.hypot(bx,bz)) continue;
            Rotation rotation = Math.abs(dz) >= Math.abs(dx)
                ? (dz >= 0 ? Rotation.NONE : Rotation.CLOCKWISE_180)
                : (dx >= 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90);
            BlockPos xAxis = StructureTemplate.transform(new BlockPos(1,0,0), Mirror.NONE, rotation, BlockPos.ZERO);
            BlockPos zAxis = StructureTemplate.transform(new BlockPos(0,0,1), Mirror.NONE, rotation, BlockPos.ZERO);
            double slope = (dx*xAxis.getX()+dz*xAxis.getZ())/(dx*zAxis.getX()+dz*zAxis.getZ());
            Site site = new Site((int)Math.round(center[0]), (int)Math.round(center[1]), rotation, slope);
            if (result.stream().noneMatch(old -> Math.hypot(old.x-site.x,old.z-site.z)<SPACING-1)) result.add(site);
        }
        return List.copyOf(result);
    }

    private static double[] sample(List<Point> points, double distance) {
        for (int i=1;i<points.size();i++) {
            Point a=points.get(i-1), b=points.get(i);
            double length=Math.hypot(b.x-a.x,b.z-a.z);
            if (length == 0) continue;
            if (distance <= length) return new double[]{a.x+(b.x-a.x)*distance/length,a.z+(b.z-a.z)*distance/length};
            distance-=length;
        }
        Point last=points.getLast(); return new double[]{last.x,last.z};
    }

    static BlockPos shift(int localZ, Site site) {
        return StructureTemplate.transform(new BlockPos((int)Math.round(site.slope*(localZ-2)),0,0), Mirror.NONE,site.rotation,BlockPos.ZERO);
    }

    private static final class FollowRoad extends net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor {
        private final Site site;
        FollowRoad(Site site) { super(List.of()); this.site=site; }
        @Override public StructureTemplate.StructureBlockInfo processBlock(net.minecraft.world.level.LevelReader level,
            BlockPos origin, BlockPos pivot, StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
            if (!repeatedBlock(original.pos())) return null;
            return new StructureTemplate.StructureBlockInfo(current.pos().offset(shift(original.pos().getZ(),site)), current.state(),current.nbt());
        }
    }

    private record Module(StructureTemplate template, List<BlockPos> blocks) {}

    static void place(ServerLevel level, WorldPlanModels.HexWorldPlan world, String routeId, List<Point> centerline) {
        List<Module> modules = new ArrayList<>();
        for (String suffix : List.of("", "_2", "_3")) {
            ResourceLocation id = ResourceLocation.parse("cobbleventure:road_decorations/league_arch" + suffix);
            StructureTemplate template = level.getStructureManager().get(id).orElse(null);
            if (template == null) {
                LOGGER.error("League arches unavailable: route={}, missingTemplate={}", routeId, id);
                return;
            }
            var encoded = template.save(new net.minecraft.nbt.CompoundTag()).getList("blocks", 10);
            List<BlockPos> blocks = new ArrayList<>();
            for (int i = 0; i < encoded.size(); i++) {
                var pos = encoded.getCompound(i).getList("pos", 3);
                BlockPos local = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                if (repeatedBlock(local)) blocks.add(local);
            }
            modules.add(new Module(template, List.copyOf(blocks)));
        }
        var ground = new RoadArchPlan.Ground() {
            @Override public int surfaceY(int x, int z) {
                return BuildingTerrainPlacement.groundY(world, x, z);
            }
            @Override public RoadArchPlan.Cell cell(BlockPos position) {
                if (!level.getFluidState(position).isEmpty()) return RoadArchPlan.Cell.FLUID;
                var state = level.getBlockState(position);
                if (state.isAir()) return RoadArchPlan.Cell.EMPTY;
                if (state.canBeReplaced()) return RoadArchPlan.Cell.REPLACEABLE;
                var terrain = CobbleventureBootstrap.nativeTerrainColumn(world, position.getX(), position.getZ());
                int preparedY = BuildingTerrainPlacement.groundY(world, position.getX(), position.getZ());
                if (preparedY != terrain.groundY() && position.getY() == preparedY
                    && state.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS)) return RoadArchPlan.Cell.TERRAIN;
                if (position.getY() == terrain.groundY() && state.equals(terrain.surface())) {
                    return RoadArchPlan.Cell.TERRAIN;
                }
                if (state.is(net.minecraft.tags.BlockTags.DIRT)
                    || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                    || state.is(net.minecraft.tags.BlockTags.SAND)
                    || state.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                    || state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)) return RoadArchPlan.Cell.TERRAIN;
                return RoadArchPlan.Cell.SOLID;
            }
        };
        List<Site> candidates = sites(centerline);
        var rejected = new java.util.EnumMap<RoadArchPlan.Failure, Integer>(RoadArchPlan.Failure.class);
        int placed = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Site site = candidates.get(i);
            Module module = modules.get(i % modules.size());
            RoadArchPlan.Plan plan = RoadArchPlan.create(module.blocks(), site, centerline, ground);
            if (!plan.allowed()) {
                rejected.merge(plan.failure(), 1, Integer::sum);
                LOGGER.debug("League arch rejected: route={}, reason={}, position={}", routeId, plan.failure(), plan.rejectedAt());
                continue;
            }
            BlockPos origin = plan.origin();
            if (!module.template().placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setRotation(site.rotation()).setIgnoreEntities(true).addProcessor(new FollowRoad(site)),
                RandomSource.create(origin.asLong()), 2)) {
                LOGGER.error("League arch placement failed: route={}, origin={}", routeId, origin);
                continue;
            }
            for (BlockPos support : plan.foundations()) {
                level.setBlock(support, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
            placed++;
        }
        LOGGER.info("League arches: route={}, candidates={}, placed={}, rejected={}", routeId, candidates.size(), placed, rejected);
        if (!candidates.isEmpty() && placed == 0) LOGGER.warn("No league arches placed: route={}, rejected={}", routeId, rejected);
    }
}
