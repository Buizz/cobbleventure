package dev.buizz.cobbleventure.bootstrap;

import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.buizz.cobbleventure.bootstrap.CobbleventureBootstrap.Point;

class CenterStructureRoadsTest {
    @Test void roadApproachesEverySideWithoutCrossingBuilding() {
        var box = new CenterStructureRoads.Rect(-20, -20, 20, 20);
        for (Direction facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            Point entry = new Point(facing.getStepX() * 10, facing.getStepZ() * 10);
            var line = CenterStructureRoads.endAtEntrance(List.of(new Point(-100, 0), new Point(0, 0)), entry, facing, box);
            assertEquals(new Point(-100, 0), line.getFirst());
            assertEquals(entry, line.getLast());
            for (int i = 1; i < line.size() - 1; i++) assertFalse(CenterStructureRoads.crosses(line.get(i - 1), line.get(i), box));
            Point approach = line.get(line.size() - 2);
            assertEquals(facing.getStepX() * 10, approach.x() - entry.x());
            assertEquals(facing.getStepZ() * 10, approach.z() - entry.z());
        }
    }

    @Test void entrancePrefersDoorsAndSupportsDungeonTransitions() {
        var metadata = JsonParser.parseString("""
            {"anchors":[{"id":"npc","type":"npc_position"},
            {"id":"dungeon_entry","type":"interaction_point"},
            {"id":"exit","type":"transition"},{"id":"door","type":"door"}]}
            """).getAsJsonObject();
        assertEquals("door", CenterStructureRoads.entrance(metadata).get("id").getAsString());
        metadata.getAsJsonArray("anchors").remove(3);
        assertEquals("exit", CenterStructureRoads.entrance(metadata).get("id").getAsString());
        metadata.getAsJsonArray("anchors").remove(2);
        assertEquals("dungeon_entry", CenterStructureRoads.entrance(metadata).get("id").getAsString());
    }
}
