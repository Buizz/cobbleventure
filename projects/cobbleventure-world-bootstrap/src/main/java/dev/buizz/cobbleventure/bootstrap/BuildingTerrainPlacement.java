package dev.buizz.cobbleventure.bootstrap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.buizz.cobbleventure.content.ContentFiles;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import static dev.buizz.cobbleventure.bootstrap.WorldPlanModels.*;

/** Shared entrance datum and optional earthworks for exterior world objects. */
final class BuildingTerrainPlacement {
    record Entrance(BlockPos offset, int floorOffset) {}
    record Plot(int minX, int minZ, int maxX, int maxZ, int floorY) {
        int distance(int x, int z) {
            return Math.max(Math.max(minX - x, x - maxX), Math.max(minZ - z, z - maxZ));
        }
        int height(int x, int z, int naturalY) {
            double t = Math.clamp(distance(x, z) / 8.0, 0, 1);
            return (int) Math.round(floorY + (naturalY - floorY) * t);
        }
    }
    private static final Map<HexWorldPlan, List<Plot>> PLOTS = new WeakHashMap<>();
    private BuildingTerrainPlacement() {}

    static Entrance entrance(String structure, StructureTemplate template, Rotation rotation) {
        JsonObject anchor = CenterStructureRoads.roadAnchor(template.save(new net.minecraft.nbt.CompoundTag()));
        if (anchor == null) {
            String[] id = structure.split(":", 2);
            try (var stream = ContentFiles.open("/data/" + id[0] + "/structure_metadata/" + id[1] + ".structure.json")) {
                if (stream != null) anchor = CenterStructureRoads.entrance(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read entrance: " + structure, e); }
        }
        if (anchor == null) return null;
        var position = anchor.getAsJsonArray(anchor.has("safe_spawn") ? "safe_spawn" : "position");
        var local = new BlockPos(position.get(0).getAsInt(), position.get(1).getAsInt(), position.get(2).getAsInt());
        boolean groundMarker = "road_anchor".equals(anchor.get("type").getAsString());
        return new Entrance(StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO),
            local.getY() - (groundMarker ? 0 : 1));
    }

    static CobbleventureBootstrap.Point roadReference(HexWorldPlan world, String objectId, BlockPos entrance) {
        var attached = world.paths().stream().filter(p -> objectId.equals(p.from()) || objectId.equals(p.to()))
            .filter(p -> p.corridorWidthBlocks() > 0 && !p.surfaceStyle().equals("water")).toList();
        var paths = attached.isEmpty() ? world.paths().stream().filter(p -> p.corridorWidthBlocks() > 0
            && !p.surfaceStyle().equals("water")).toList() : attached;
        CobbleventureBootstrap.Point best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var path : paths) for (int i = 1; i < path.centerline().size(); i++) {
            var a = path.centerline().get(i - 1); var b = path.centerline().get(i);
            double dx = b.x() - a.x(), dz = b.z() - a.z();
            double length = dx * dx + dz * dz;
            double t = length == 0 ? 0 : Math.clamp(((entrance.getX() - a.x()) * dx + (entrance.getZ() - a.z()) * dz) / length, 0, 1);
            double x = a.x() + t * dx, z = a.z() + t * dz;
            double d = Math.hypot(x - entrance.getX(), z - entrance.getZ());
            if (d < distance && (!attached.isEmpty() || d <= world.grid().radius())) {
                distance = d; best = new CobbleventureBootstrap.Point((int)Math.round(x), (int)Math.round(z));
            }
        }
        return best;
    }

    static int groundY(HexWorldPlan world, int x, int z) {
        int natural = CobbleventureBootstrap.nativeTerrainColumn(world, x, z).groundY();
        for (Plot plot : PLOTS.getOrDefault(world, List.of())) if (plot.distance(x, z) < 8) return plot.height(x, z, natural);
        return natural;
    }

    static void prepare(ServerLevel level, HexWorldPlan world, String structure, StructureTemplate template,
                        BlockPos origin, Rotation rotation, int floorY, boolean generate) {
        if (!BuildingRuntimeSystem.reserveBuildingPlot(structure)) return;
        var size = template.getSize();
        BlockPos opposite = origin.offset(StructureTemplate.transform(new BlockPos(size.getX()-1, 0, size.getZ()-1), Mirror.NONE, rotation, BlockPos.ZERO));
        Plot plot = new Plot(Math.min(origin.getX(), opposite.getX()) - 2, Math.min(origin.getZ(), opposite.getZ()) - 2,
            Math.max(origin.getX(), opposite.getX()) + 2, Math.max(origin.getZ(), opposite.getZ()) + 2, floorY);
        var plots = PLOTS.computeIfAbsent(world, ignored -> new ArrayList<>());
        if (!plots.contains(plot)) plots.add(plot);
        if (!generate) return;
        // Run before the NBT is placed. Only natural ground/foliage is cut;
        // existing masonry and block entities are never erased by earthworks.
        for (int x = plot.minX()-7; x <= plot.maxX()+7; x++) for (int z = plot.minZ()-7; z <= plot.maxZ()+7; z++) {
            var column = CobbleventureBootstrap.nativeTerrainColumn(world, x, z);
            int naturalY = column.groundY();
            int targetY = plot.height(x, z, naturalY);
            for (int y = Math.min(naturalY, targetY); y <= Math.max(naturalY, targetY) + 24; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState current = level.getBlockState(pos);
                if (level.getBlockEntity(pos) != null || !level.getFluidState(pos).isEmpty()) continue;
                boolean natural = current.is(BlockTags.DIRT) || current.is(BlockTags.BASE_STONE_OVERWORLD)
                    || current.is(BlockTags.SAND) || current.is(Blocks.GRAVEL) || current.is(BlockTags.LOGS)
                    || current.is(BlockTags.LEAVES) || current.is(Blocks.DIRT_PATH);
                boolean road = column.sample() != null && column.sample().kind().equals("route");
                natural |= road && y == naturalY && current.equals(column.surface());
                if (y <= targetY && (current.isAir() || current.canBeReplaced() || natural)) {
                    level.setBlock(pos, y == targetY
                        ? (road ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState())
                        : Blocks.DIRT.defaultBlockState(), 2);
                } else if (y > targetY && (natural || current.canBeReplaced()) && !current.isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }
}
