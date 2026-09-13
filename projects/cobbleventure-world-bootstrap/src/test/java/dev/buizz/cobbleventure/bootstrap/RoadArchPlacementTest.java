package dev.buizz.cobbleventure.bootstrap;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RoadArchPlacementTest {
    @Test void samplesDenseCenterlinesAtTwelveBlockIntervals() {
        var points=IntStream.rangeClosed(0,60).mapToObj(z -> new RoadArchPlacement.Point(0,z)).toList();
        var sites=RoadArchPlacement.sites(points);
        assertEquals(List.of(12,24,36,48),sites.stream().map(RoadArchPlacement.Site::z).toList());
        assertTrue(sites.stream().allMatch(s -> s.rotation()==Rotation.NONE));
    }
    @Test void rotatesReverseAndHorizontalRoadsAndAvoidsShortOrDiagonalSections() {
        assertEquals(Rotation.CLOCKWISE_180,RoadArchPlacement.sites(List.of(
            new RoadArchPlacement.Point(0,60),new RoadArchPlacement.Point(0,0))).getFirst().rotation());
        assertEquals(Rotation.COUNTERCLOCKWISE_90,RoadArchPlacement.sites(List.of(
            new RoadArchPlacement.Point(0,0),new RoadArchPlacement.Point(60,0))).getFirst().rotation());
        assertTrue(RoadArchPlacement.sites(List.of(new RoadArchPlacement.Point(0,0),
            new RoadArchPlacement.Point(20,0),new RoadArchPlacement.Point(40,20))).isEmpty());
    }
}
