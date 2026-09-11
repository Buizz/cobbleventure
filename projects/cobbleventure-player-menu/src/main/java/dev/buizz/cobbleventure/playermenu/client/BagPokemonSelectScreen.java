package dev.buizz.cobbleventure.playermenu.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.buizz.cobbleventure.playermenu.BagNetwork;
import dev.buizz.cobbleventure.playermenu.BagTechnicalMachines;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Bag actions over the shared party picker; TM rules remain owned by the bag service. */
final class BagPokemonSelectScreen extends PokemonSelectScreen {
    enum Action { USE, GIVE }

    BagPokemonSelectScreen(BagScreen parent, boolean extended, int sourceSlot, ItemStack stack, Action action) {
        super(parent,
            Component.translatable("screen.cobbleventure_player_menu.bag.pokemon_select."
                + (action == Action.USE ? "use_title" : "give_title")),
            Component.translatable("screen.cobbleventure_player_menu.bag.pokemon_select."
                + (action == Action.USE ? "use_hint" : "give_hint"), stack.getHoverName()),
            stack, PokemonSelectScreen::currentParty,
            pokemon -> eligibility(stack, action, pokemon),
            (pokemon, slot) -> select(parent, extended, sourceSlot, stack, action, pokemon, slot));
    }

    private static Eligibility eligibility(ItemStack stack, Action action, Pokemon pokemon) {
        if (action != Action.USE || !BagTechnicalMachines.isTechnicalMachine(stack)) return Eligibility.available();
        var status = BagTechnicalMachines.availability(stack, pokemon, CobblemonClient.INSTANCE.getBattle() != null);
        boolean active = status == BagTechnicalMachines.Availability.LEARNABLE;
        return new Eligibility(active, status.label(), active && !pokemon.getMoveSet().hasSpace()
            ? Component.translatable("screen.cobbleventure_player_menu.bag.tm.bench_hint") : Component.empty());
    }

    private static void select(BagScreen parent, boolean extended, int sourceSlot, ItemStack stack, Action action, Pokemon pokemon, int partySlot) {
        if (action == Action.USE && BagTechnicalMachines.isTechnicalMachine(stack)) {
            if (BagTechnicalMachines.availability(stack, pokemon, CobblemonClient.INSTANCE.getBattle() != null)
                != BagTechnicalMachines.Availability.LEARNABLE) return;
            BagNetwork.requestTeachTm(extended, sourceSlot, pokemon.getUuid(), stack);
            parent.tmUseRequested();
        } else if (action == Action.USE) {
            PlayerMenuClient.useBagItemOnPokemon(extended, sourceSlot, partySlot);
            parent.pokemonUseRequested();
        } else {
            PlayerMenuClient.giveBagItemToPokemon(extended, sourceSlot, partySlot);
            parent.pokemonGiveRequested(pokemon.getDisplayName(false));
        }
        Minecraft.getInstance().setScreen(parent);
    }

}
