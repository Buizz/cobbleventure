package dev.buizz.cobbleventure.bootstrap;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RoadArchPlacementTest {
    @Test void samplesDenseCenterlinesAtEightBlockIntervalsKeepingEntranceClear() {
        var points=IntStream.rangeClosed(0,60).mapToObj(z -> new RoadArchPlacement.Point(0,z)).toList();
        var sites=RoadArchPlacement.sites(points);
        assertEquals(List.of(18,26,34,42),sites.stream().map(RoadArchPlacement.Site::z).toList());
        assertTrue(sites.stream().allMatch(s -> s.rotation()==Rotation.NONE));
    }
    @Test void rotatesReverseAndHorizontalRoadsAndAvoidsShortSections() {
        assertEquals(Rotation.CLOCKWISE_180,RoadArchPlacement.sites(List.of(
            new RoadArchPlacement.Point(0,60),new RoadArchPlacement.Point(0,0))).getFirst().rotation());
        assertEquals(Rotation.COUNTERCLOCKWISE_90,RoadArchPlacement.sites(List.of(
            new RoadArchPlacement.Point(0,0),new RoadArchPlacement.Point(60,0))).getFirst().rotation());
        assertTrue(RoadArchPlacement.sites(List.of(new RoadArchPlacement.Point(0,0),
            new RoadArchPlacement.Point(20,0))).isEmpty());
    }
    @Test void diagonalHexRoadsReceiveArchesWhosePassageFollowsTheRoad() {
        var sites=RoadArchPlacement.sites(List.of(new RoadArchPlacement.Point(0,0),new RoadArchPlacement.Point(60,104)));
        assertFalse(sites.isEmpty());
        for (var site:sites) {
            assertEquals(Rotation.NONE,site.rotation());
            assertEquals(60.0/104,site.slope(),.001);
            assertEquals(0,RoadArchPlacement.shift(2,site).getX());
            assertEquals(5,RoadArchPlacement.shift(11,site).getX());
        }
    }
}
