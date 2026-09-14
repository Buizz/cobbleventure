package dev.buizz.cobbleventure.bootstrap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import static dev.buizz.cobbleventure.bootstrap.WorldPlanModels.*;
import dev.buizz.cobbleventure.bootstrap.CobbleventureBootstrap.Point;

/** Connect road endpoints to centered buildings without moving or rotating the building. */
final class CenterStructureRoads {
    private CenterStructureRoads() {}

    static HexWorldPlan connect(ServerLevel level, HexWorldPlan world) {
        return connect(world);
    }

    static HexWorldPlan connect(HexWorldPlan world) {
        List<ConnectionPath> paths = new ArrayList<>(world.paths());
        for (var object : world.worldStructures()) {
            if (!object.placementAnchor().equals("center")) continue;
            if (paths.stream().noneMatch(path -> object.id().equals(path.from()) || object.id().equals(path.to()))) continue;
            ResourceLocation id = ResourceLocation.parse(object.structure());
            JsonObject metadata = new JsonObject();
            net.minecraft.nbt.CompoundTag template;
            String base = "/data/" + id.getNamespace() + "/";
            try (var stream = dev.buizz.cobbleventure.content.ContentFiles.open(base + "structure/" + id.getPath() + ".nbt")) {
                if (stream == null) throw new IllegalStateException("Missing centered structure: " + id);
                template = net.minecraft.nbt.NbtIo.readCompressed(stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read road structure: " + id, error); }
            try (var stream = dev.buizz.cobbleventure.content.ContentFiles.open(base + "structure_metadata/" + id.getPath() + ".structure.json")) {
                if (stream != null) try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                    metadata = JsonParser.parseReader(reader).getAsJsonObject();
                }
            } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read road entrance: " + id, error); }
            JsonObject anchor = roadAnchor(template);
            if (anchor == null) anchor = entrance(metadata);
            if (anchor == null) continue;
            Rotation rotation = Rotation.values()[Math.floorMod(object.rotation(), 4)];
            var sizes = template.getList("size", 3);
            int width = sizes.getInt(0), depth = sizes.getInt(2);
            boolean sideways = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
            var size = new net.minecraft.core.Vec3i(sideways ? depth : width, sizes.getInt(1), sideways ? width : depth);
            Point center = world.grid().worldCenter(object.anchor());
            int minX = center.x() - size.getX() / 2, minZ = center.z() - size.getZ() / 2;
            BlockPos origin = WorldStructureSystem.rotatedTemplateOrigin(minX, 0, minZ,
                width, depth, rotation);
            var coordinates = anchor.has("safe_spawn") ? anchor.getAsJsonArray("safe_spawn") : anchor.getAsJsonArray("position");
            BlockPos local = new BlockPos(coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(), coordinates.get(2).getAsInt());
            BlockPos transformed = StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
            Point entry = new Point(transformed.getX(), transformed.getZ());
            String facing = anchor.has("safe_side") ? anchor.get("safe_side").getAsString()
                : anchor.has("facing") ? anchor.get("facing").getAsString() : "north";
            Direction outside = Direction.byName(facing);
            if (outside == null || !outside.getAxis().isHorizontal()) outside = Direction.NORTH;
            outside = rotation.rotate(outside);
            for (int i = 0; i < paths.size(); i++) {
                ConnectionPath path = paths.get(i);
                boolean from = object.id().equals(path.from()), to = object.id().equals(path.to());
                if ((!from && !to) || path.corridorWidthBlocks() <= 0 || path.surfaceStyle().equals("water")) continue;
                int margin = (int) Math.ceil(path.corridorWidthBlocks() * (1 + Math.min(.42, Math.abs(path.edgeNoise()) * 1.5)) / 2) + 2;
                Rect bounds = new Rect(minX - margin, minZ - margin, minX + size.getX() - 1 + margin, minZ + size.getZ() - 1 + margin);
                List<Point> line = new ArrayList<>(path.centerline());
                if (from) Collections.reverse(line);
                line = endAtEntrance(line, entry, outside, bounds);
                if (from) Collections.reverse(line);
                RouteBounds routeBounds = new RouteBounds(line.stream().mapToInt(Point::x).min().orElseThrow(),
                    line.stream().mapToInt(Point::z).min().orElseThrow(), line.stream().mapToInt(Point::x).max().orElseThrow(),
                    line.stream().mapToInt(Point::z).max().orElseThrow());
                paths.set(i, new ConnectionPath(path.id(), path.displayName(), path.from(), path.to(), path.biome(), path.boundaryProfile(),
                    path.corridorWidthBlocks(), path.edgeNoise(), path.terrainProfile(), path.surfaceStyle(), path.accessRequirement(),
                    path.cells(), path.encounterCells(), List.copyOf(line), routeBounds, path.fromTownRoad(), path.toTownRoad(),
                    path.pokemonSpawns(), path.npcPlacements(), path.trainerPopulation(), List.copyOf(line), routeBounds));
            }
        }
        return new HexWorldPlan(world.grid(), world.seed(), world.cells(), List.copyOf(paths), world.settlements(),
            world.boundaryProfiles(), world.defaultEmptyTerrain(), world.emptyTerrainTiles(), world.environmentOverrides(),
            world.levelOverrides(), world.caveEntrances(), world.tilePokemonHabitats(), world.gates(), world.worldStructures());
    }

    static JsonObject entrance(JsonObject metadata) {
        if (!metadata.has("anchors")) return null;
        // Prefer a physical door, then a transition, then a dungeon interaction entrance.
        for (String type : List.of("door", "transition", "interaction_point")) {
            for (var value : metadata.getAsJsonArray("anchors")) {
                JsonObject anchor = value.getAsJsonObject();
                if (anchor.has("type") && type.equals(anchor.get("type").getAsString())) return anchor;
            }
        }
        return null;
    }

    static JsonObject roadAnchor(net.minecraft.nbt.CompoundTag template) {
        var palette = template.getList("palette", 10);
        var blocks = template.getList("blocks", 10);
        JsonObject result = null;
        for (int i=0; i<blocks.size(); i++) {
            var block = blocks.getCompound(i);
            var state = palette.getCompound(block.getInt("state"));
            if (!state.getString("Name").equals("minecraft:jigsaw")
                || !block.getCompound("nbt").getString("name").equals("cobbleventure:road_anchor")) continue;
            if (result != null) throw new IllegalStateException("Centered structure requires exactly one road_anchor");
            result = new JsonObject();
            result.addProperty("type", "road_anchor");
            result.addProperty("safe_side", state.getCompound("Properties").getString("orientation").split("_")[0]);
            var coordinates = new com.google.gson.JsonArray();
            var pos = block.getList("pos",3);
            for (int axis=0; axis<3; axis++) coordinates.add(pos.getInt(axis));
            result.add("position", coordinates);
        }
        return result;
    }

    record Rect(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(Point p) { return p.x() > minX && p.x() < maxX && p.z() > minZ && p.z() < maxZ; }
    }

    static List<Point> endAtEntrance(List<Point> source, Point entry, Direction outside, Rect box) {
        List<Point> result = new ArrayList<>(source);
        while (result.size() > 1 && box.contains(result.getLast())) result.removeLast();
        Point approach = switch (outside) {
            case NORTH -> new Point(entry.x(), box.minZ());
            case SOUTH -> new Point(entry.x(), box.maxZ());
            case EAST -> new Point(box.maxX(), entry.z());
            default -> new Point(box.minX(), entry.z());
        };
        Point start = result.getLast();
        List<Point> nodes = List.of(start, approach, new Point(box.minX(), box.minZ()),
            new Point(box.maxX(), box.minZ()), new Point(box.maxX(), box.maxZ()), new Point(box.minX(), box.maxZ()));
        double[] distances = new double[6]; Arrays.fill(distances, Double.POSITIVE_INFINITY); distances[0] = 0;
        int[] previous = new int[6]; Arrays.fill(previous, -1); boolean[] visited = new boolean[6];
        for (int step = 0; step < 6; step++) {
            int at = -1;
            for (int i = 0; i < 6; i++) if (!visited[i] && (at < 0 || distances[i] < distances[at])) at = i;
            visited[at] = true;
            for (int next = 0; next < 6; next++) {
                if (crosses(nodes.get(at), nodes.get(next), box)) continue;
                double distance = distances[at] + Math.hypot(nodes.get(at).x() - nodes.get(next).x(), nodes.get(at).z() - nodes.get(next).z());
                if (distance < distances[next]) { distances[next] = distance; previous[next] = at; }
            }
        }
        if (!Double.isFinite(distances[1])) throw new IllegalStateException("Road starts inside centered structure footprint");
        List<Point> tail = new ArrayList<>();
        for (int at = 1; at != 0; at = previous[at]) tail.add(nodes.get(at));
        Collections.reverse(tail); result.addAll(tail); result.add(entry);
        return result;
    }

    static boolean crosses(Point a, Point b, Rect box) {
        double low = 0, high = 1;
        double[] starts = {a.x(), a.z()}, deltas = {b.x() - a.x(), b.z() - a.z()};
        double[] mins = {box.minX() + .001, box.minZ() + .001}, maxs = {box.maxX() - .001, box.maxZ() - .001};
        for (int axis = 0; axis < 2; axis++) {
            if (deltas[axis] == 0) { if (starts[axis] < mins[axis] || starts[axis] > maxs[axis]) return false; }
            else {
                double t1 = (mins[axis] - starts[axis]) / deltas[axis], t2 = (maxs[axis] - starts[axis]) / deltas[axis];
                low = Math.max(low, Math.min(t1, t2)); high = Math.min(high, Math.max(t1, t2));
                if (low > high) return false;
            }
        }
        return true;
    }
}
