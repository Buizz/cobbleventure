package dev.buizz.cobbleventure.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class DungeonNpcSlotReuseTest {
    @Test
    void anotherEncounterCanUseRemainingAuthoredSlotsInTheSameChamber() throws Exception {
        var used = List.of(slot(8, 8), slot(24, 8));
        var remaining = List.of(slot(8, 24), slot(24, 24));
        assertEquals(remaining, select(remaining, used, 2));
        assertEquals(List.of(), select(remaining, used, 3));
    }

    @Test
    void spacingStillRejectsCrowdedOrReusedSlots() throws Exception {
        assertEquals(List.of(), select(List.of(slot(8, 8), slot(9, 8)), List.of(slot(8, 8)), 1));
        assertEquals(List.of(), select(List.of(slot(8, 8), slot(9, 8)), List.of(), 2));
    }

    private static DungeonPieceLayout.ResolvedMarker slot(int x, int z) {
        return new DungeonPieceLayout.ResolvedMarker("npc_spawn", null, new BlockPos(x, 1, z), 0, null);
    }

    @SuppressWarnings("unchecked")
    private static List<DungeonPieceLayout.ResolvedMarker> select(
        List<DungeonPieceLayout.ResolvedMarker> available,
        List<DungeonPieceLayout.ResolvedMarker> used, int count
    ) throws Exception {
        var layout = new DungeonPieceLayout(new DungeonPiecePlan(1L, new BlockPos(32, 8, 32), List.of(), List.of()), List.of());
        var method = DungeonPieceLayout.class.getDeclaredMethod("selectNpcGroup", List.class, List.class,
            Map.class, Map.class, int.class, int.class, DungeonDefinition.NpcPlacement.class);
        method.setAccessible(true);
        return (List<DungeonPieceLayout.ResolvedMarker>) method.invoke(layout, new ArrayList<>(available), used,
            Map.of(0, used.size()), Map.of(), count, 0, new DungeonDefinition.NpcPlacement(true, "fixed", count, 4));
    }
}
