package dev.buizz.cobbleventure.bootstrap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingTerrainPlacementTest {
    @Test void plotUsesRoadDatumAcrossFootprintAndBlendsBothRaisedAndLowerGround() {
        var plot = new BuildingTerrainPlacement.Plot(-10, -20, 10, 20, 73);
        assertEquals(73, plot.height(0, 0, 85));
        assertEquals(73, plot.height(-10, 20, 60));
        assertEquals(77, plot.height(14, 0, 81));
        assertEquals(69, plot.height(-14, 0, 65));
        assertEquals(81, plot.height(18, 0, 81));
        assertEquals(65, plot.height(-18, 0, 65));
    }
}
