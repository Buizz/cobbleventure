package dev.buizz.cobbleventure.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLeagueApproachTest {
    @Test void packagedNativePlanEndsAtMarkedDoorAndProducesArchSites() throws Exception {
        Path content = Path.of("../../pack/overrides/development-placeholder/config/cobbleventure/content").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(content));
        String previous = System.getProperty("cobbleventure.contentDirectory");
        try {
            System.setProperty("cobbleventure.contentDirectory", content.toString());
            var document = com.google.gson.JsonParser.parseString(Files.readString(content.resolve("data/cobbleventure/hex_worlds/generation_1.json"))).getAsJsonObject();
            var gridData=document.getAsJsonObject("grid"); var origin=gridData.getAsJsonObject("origin");
            var grid=new WorldPlanModels.HexGrid(gridData.get("tile_radius_blocks").getAsInt(), new CobbleventureBootstrap.BlockPoint(origin.get("x").getAsInt(),origin.get("y").getAsInt(),origin.get("z").getAsInt()));
            var objects=WorldStructureSystem.parse(document.getAsJsonArray("objects")).stream().filter(o->o.id().equals("indigo_plateau")).toList();
            var authored=document.getAsJsonArray("connections").asList().stream().map(v->v.getAsJsonObject()).filter(v->v.get("id").getAsString().equals("route_custom_23")).findFirst().orElseThrow();
            var cells=authored.getAsJsonArray("cells").asList().stream().map(v->v.getAsJsonObject()).map(v->new WorldPlanModels.HexCoord(v.get("q").getAsInt(),v.get("r").getAsInt())).toList();
            var line=cells.stream().map(grid::worldCenter).toList();
            var pathInput=new WorldPlanModels.ConnectionPath("route_custom_23","arch",authored.get("from").getAsString(),null,"minecraft:plains",null,
                12,0,null,authored.get("surface_style").getAsString(),null,cells,java.util.List.of(),line,new WorldPlanModels.RouteBounds(-1000,-1000,1000,1000),null,null,null,java.util.List.of(),null);
            var input=new WorldPlanModels.HexWorldPlan(grid,1,java.util.Map.of(),java.util.List.of(pathInput),java.util.Map.of(),java.util.Map.of(),"plains",java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),java.util.List.of(),java.util.Map.of(),java.util.List.of(),objects);
            var world = CenterStructureRoads.connect(input);
            var path = world.paths().stream().filter(p -> p.id().equals("route_custom_23")).findFirst().orElseThrow();
            var object = world.worldStructures().stream().filter(o -> o.id().equals("indigo_plateau")).findFirst().orElseThrow();
            var center = world.grid().worldCenter(object.anchor());
            assertEquals("league_arch", path.surfaceStyle());
            assertEquals(new CobbleventureBootstrap.Point(center.x()-30+29, center.z()-22+43), path.centerline().getFirst());
            assertNotEquals(center, path.centerline().getFirst());
            var entrancePoint = path.centerline().getFirst();
            assertEquals(entrancePoint, BuildingTerrainPlacement.roadReference(world, object.id(),
                new net.minecraft.core.BlockPos(entrancePoint.x(), 0, entrancePoint.z())));
            var sites = RoadArchPlacement.sites(path.centerline().stream().map(p -> new RoadArchPlacement.Point(p.x(), p.z())).toList());
            assertFalse(sites.isEmpty());
            System.out.println("Native league approach: entrance="+path.centerline().getFirst()+", archSites="+sites.size());
        } finally {
            if (previous == null) System.clearProperty("cobbleventure.contentDirectory");
            else System.setProperty("cobbleventure.contentDirectory", previous);
        }
    }
}
