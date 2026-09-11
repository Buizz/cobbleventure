package dev.buizz.cobbleventure.playermenu.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.keybind.keybinds.SummaryBinding;
import com.cobblemon.mod.common.item.PokedexItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.buizz.cobbleventure.playermenu.BagNetwork;
import dev.buizz.cobbleventure.playermenu.mixin.SummaryScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import dev.buizz.cobbleventure.playermenu.ProgressionNetwork;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

final class CobblemonMenuIntegration {
    private CobblemonMenuIntegration() {}

    static boolean openPartySummary() {
        int selection = CobblemonClient.INSTANCE.getStorage().getSelectedSlot();
        Pokemon selectedPokemon = CobblemonClient.INSTANCE.getStorage().getParty().get(selection);
        if (selectedPokemon == null) {
            selectedPokemon = firstPartyPokemon();
        }
        if (selectedPokemon == null) {
            return false;
        }

        CobblemonClient.INSTANCE.getStorage().switchToPokemon(selectedPokemon.getUuid());
        SummaryBinding.INSTANCE.onPress();
        return true;
    }

    static boolean openGrowthAction(UUID pokemonId, GrowthNotificationOverlay.Kind kind) {
        List<Pokemon> party = partyPokemon();
        int selection = -1;
        for (int index = 0; index < party.size(); index++) {
            if (party.get(index).getUuid().equals(pokemonId)) {
                selection = index;
                break;
            }
        }
        if (selection < 0) return false;

        Summary.Companion.open(party, true, selection);
        if (!(Minecraft.getInstance().screen instanceof Summary summary)) return false;
        if (kind == GrowthNotificationOverlay.Kind.MOVES) {
            ((SummaryScreenAccessor)(Object)summary).cobbleventure$displayMainScreen(
                SummaryScreenAccessor.cobbleventure$movesTab()
            );
        } else {
            summary.displaySideScreen(Summary.EVOLVE, null);
        }
        return true;
    }

    static boolean requestRemotePc() {
        if (Minecraft.getInstance().getConnection() == null) return false;
        Minecraft.getInstance().setScreen(null);
        ProgressionNetwork.requestPc();
        return true;
    }

    static boolean openOwnedPokedex() {
        ItemStack pokedexStack = ownedPokedex();
        if (!(pokedexStack.getItem() instanceof PokedexItem pokedex)) {
            return false;
        }

        CobblemonClient.INSTANCE
            .getPokedexUsageContext()
            .openPokedexGUI(pokedex.getType(), null);
        return true;
    }

    static boolean hasOwnedPokedex() {
        return !ownedPokedex().isEmpty();
    }

    private static ItemStack ownedPokedex() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack pokedexStack = findPokedex(player.getInventory());
        if (pokedexStack.isEmpty()) {
            pokedexStack = findPokedex(BagNetwork.clientSnapshot().slots());
        }
        return pokedexStack;
    }

    static int partySize() {
        int size = 0;
        for (int slot = 0; slot < 6; slot++) {
            if (CobblemonClient.INSTANCE.getStorage().getParty().get(slot) != null) {
                size++;
            }
        }
        return size;
    }

    static List<Pokemon> partyPokemon() {
        List<Pokemon> result = new ArrayList<>(6);
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = CobblemonClient.INSTANCE.getStorage().getParty().get(slot);
            if (pokemon != null) result.add(pokemon);
        }
        return List.copyOf(result);
    }

    private static Pokemon firstPartyPokemon() {
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = CobblemonClient.INSTANCE.getStorage().getParty().get(slot);
            if (pokemon != null) {
                return pokemon;
            }
        }
        return null;
    }

    private static ItemStack findPokedex(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof PokedexItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findPokedex(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof PokedexItem) return stack;
        }
        return ItemStack.EMPTY;
    }
}
