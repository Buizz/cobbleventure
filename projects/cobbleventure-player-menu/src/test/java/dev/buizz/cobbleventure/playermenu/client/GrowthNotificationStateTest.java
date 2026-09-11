package dev.buizz.cobbleventure.playermenu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GrowthNotificationStateTest {
    @Test
    void keepsMoveAndEvolutionActionsUntilEachIsResolved() {
        GrowthNotificationState state = new GrowthNotificationState();
        UUID pokemon = UUID.randomUUID();

        state.addMoves(pokemon, 2);
        state.addEvolutions(pokemon, 1);
        assertEquals(new GrowthNotificationState.PendingGrowth(2, 1), state.get(pokemon));

        state.acknowledgeMoves(pokemon);
        assertEquals(new GrowthNotificationState.PendingGrowth(0, 1), state.get(pokemon));

        state.resolveEvolutions(pokemon);
        assertEquals(new GrowthNotificationState.PendingGrowth(0, 0), state.get(pokemon));
    }

    @Test
    void dropsActionsForPokemonThatLeftTheParty() {
        GrowthNotificationState state = new GrowthNotificationState();
        UUID retained = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        state.addMoves(retained, 1);
        state.addEvolutions(removed, 1);

        state.retainPokemon(Set.of(retained));

        assertEquals(1, state.get(retained).total());
        assertEquals(0, state.get(removed).total());
    }
}
