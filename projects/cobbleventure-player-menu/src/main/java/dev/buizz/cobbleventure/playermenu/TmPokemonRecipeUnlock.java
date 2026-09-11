package dev.buizz.cobbleventure.playermenu;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.LevelUpEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonGainedEvent;
import com.cobblemon.mod.common.trade.TradeManager;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

/** Applies Cobbleventure's non-trade, actually-known-move TM discovery policy. */
public final class TmPokemonRecipeUnlock {
    private TmPokemonRecipeUnlock() {}

    public static void register() {
        CobblemonEvents.POKEMON_GAINED.subscribe(
            Priority.NORMAL,
            (Consumer<PokemonGainedEvent>) TmPokemonRecipeUnlock::onPokemonGained
        );
        CobblemonEvents.LEVEL_UP_EVENT.subscribe(
            Priority.NORMAL,
            (Consumer<LevelUpEvent>) TmPokemonRecipeUnlock::onLevelUp
        );
    }

    private static void onPokemonGained(PokemonGainedEvent event) {
        // The active trade still owns the transfer while PokemonGainedEvent is emitted.
        if (!TmAcquisitionPlanner.allowsPokemonGain(
            TradeManager.INSTANCE.getActiveTrade(event.getPlayerId()) != null
        )) return;
        ServerPlayer player = PlayerExtensionsKt.getPlayer(event.getPlayerId());
        if (player == null) return;
        TmAcquisitionUnlock.discoverMoves(player, event.getPokemon().getMoveSet().getMoveTemplates());
    }

    private static void onLevelUp(LevelUpEvent event) {
        ServerPlayer player = event.getPokemon().getOwnerPlayer();
        if (player == null || event.getNewLevel() <= event.getOldLevel()) return;
        var levelMoves = event.getPokemon().getForm().getMoves().getLevelUpMoves();
        var learnedNow = new ArrayList<com.cobblemon.mod.common.api.moves.MoveTemplate>();
        for (var entry : levelMoves.entrySet()) {
            if (TmAcquisitionPlanner.learnsAtLevel(
                entry.getKey(), event.getOldLevel(), event.getNewLevel()
            )) {
                learnedNow.addAll(entry.getValue());
            }
        }
        TmAcquisitionUnlock.discoverMoves(player, learnedNow);
    }
}
