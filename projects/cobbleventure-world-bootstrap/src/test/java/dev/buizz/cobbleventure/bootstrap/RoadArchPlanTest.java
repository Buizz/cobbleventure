package dev.buizz.cobbleventure.bootstrap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadArchPlanTest {
    private static final List<RoadArchPlacement.Point> ROAD = List.of(
        new RoadArchPlacement.Point(0, -100), new RoadArchPlacement.Point(0, 100));
    private static final RoadArchPlacement.Site SITE = new RoadArchPlacement.Site(0, 0, Rotation.NONE, 0);
    private static final List<BlockPos> FEET = List.of(new BlockPos(1, 0, 2), new BlockPos(17, 0, 2));

    private static RoadArchPlan.Ground ground(ToIntBiFunction<Integer, Integer> height, BlockPos obstacle, RoadArchPlan.Cell kind) {
        return new RoadArchPlan.Ground() {
            public int surfaceY(int x, int z) { return height.applyAsInt(x, z); }
            public RoadArchPlan.Cell cell(BlockPos pos) {
                if (pos.equals(obstacle)) return kind;
                return pos.getY() <= surfaceY(pos.getX(), pos.getZ()) ? RoadArchPlan.Cell.TERRAIN : RoadArchPlan.Cell.EMPTY;
            }
        };
    }

    @Test void supportsSlopeWithoutFillingPassage() {
        var plan = RoadArchPlan.create(FEET, SITE, ROAD, ground((x,z) -> x < 0 ? 67 : 70, null, null));
        assertTrue(plan.allowed(), plan.failure().toString());
        assertEquals(List.of(new BlockPos(-8, 69, 0), new BlockPos(-8, 68, 0)), plan.foundations());
    }

    @Test void rejectsSteepGroundWithoutPartialFoundations() {
        var plan = RoadArchPlan.create(FEET, SITE, ROAD, ground((x,z) -> x < 0 ? 60 : 70, null, null));
        assertEquals(RoadArchPlan.Failure.STEEP, plan.failure());
        assertTrue(plan.foundations().isEmpty());
    }

    @Test void rejectsObstaclesAndWaterInsideFootingsWithoutPartialWrites() {
        for (var kind : List.of(RoadArchPlan.Cell.SOLID, RoadArchPlan.Cell.FLUID)) {
            var plan = RoadArchPlan.create(FEET, SITE, ROAD,
                ground((x,z) -> x < 0 ? 67 : 70, new BlockPos(-8, 68, 0), kind));
            assertEquals(kind == RoadArchPlan.Cell.FLUID ? RoadArchPlan.Failure.WATER : RoadArchPlan.Failure.OBSTACLE, plan.failure());
            assertTrue(plan.foundations().isEmpty());
        }
    }

    @Test void rejectsTemplateObstaclesAndHeadHeightBlocks() {
        var blocks = new ArrayList<>(FEET);
        blocks.add(new BlockPos(9, 2, 2));
        assertEquals(RoadArchPlan.Failure.BLOCKS_PASSAGE,
            RoadArchPlan.create(blocks, SITE, ROAD, ground((x,z) -> 70, null, null)).failure());
        assertEquals(RoadArchPlan.Failure.OBSTACLE,
            RoadArchPlan.create(FEET, SITE, ROAD, ground((x,z) -> 70, new BlockPos(-8,70,0), RoadArchPlan.Cell.SOLID)).failure());
    }

    @Test void allActualModulesWorkOnDiagonalAndReverseRoadsWithSlopedGround() throws Exception {
        for (String suffix : List.of("", "_2", "_3")) {
            var file = Path.of("../../content-projects/cobbleventure-main/content/structures/road_decorations/league_arch" + suffix + ".nbt");
            var tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getList("blocks", 10);
            List<BlockPos> blocks = new ArrayList<>();
            for (int i = 0; i < tag.size(); i++) {
                var p = tag.getCompound(i).getList("pos", 3);
                var local = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
                if (RoadArchPlacement.repeatedBlock(local)) blocks.add(local);
            }
            for (int direction : List.of(-1, 1)) {
                var road = List.of(new RoadArchPlacement.Point(0, 0), new RoadArchPlacement.Point(direction * 60, direction * 104));
                var occupied = new java.util.HashSet<BlockPos>();
                var terrain = ground((x,z) -> 70 + Math.floorDiv(z, 8), null, null);
                var sequentialGround = new RoadArchPlan.Ground() {
                    public int surfaceY(int x, int z) { return terrain.surfaceY(x, z); }
                    public RoadArchPlan.Cell cell(BlockPos pos) {
                        return occupied.contains(pos) ? RoadArchPlan.Cell.SOLID : terrain.cell(pos);
                    }
                };
                for (var site : RoadArchPlacement.sites(road)) {
                    var plan = RoadArchPlan.create(blocks, site, road, sequentialGround);
                    assertTrue(plan.allowed(), suffix + ": " + site + " " + plan.failure() + " at " + plan.rejectedAt());
                    var anchor = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
                        new BlockPos(9, 0, 2), net.minecraft.world.level.block.Mirror.NONE, site.rotation(), BlockPos.ZERO);
                    var center = plan.origin().offset(anchor);
                    for (var block : blocks) occupied.add(center.offset(RoadArchPlan.offset(block, site)));
                    occupied.addAll(plan.foundations());
                }
            }
        }
    }
}
