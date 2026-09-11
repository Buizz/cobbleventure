package dev.buizz.cobbleventure.playermenu;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.item.components.TMMoveComponent;
import com.cobblemon.mod.common.item.interactive.TechnicalMachineItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Native TM learning rules applied to stored party Pokémon, without spawning an entity. */
public final class BagTechnicalMachines {
    private BagTechnicalMachines() {}

    public enum Availability {
        LEARNABLE, ALREADY_KNOWN, INCOMPATIBLE, INVALID, IN_BATTLE;

        public Component label() {
            return Component.translatable("screen.cobbleventure_player_menu.bag.tm."
                + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public static boolean isTechnicalMachine(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TechnicalMachineItem;
    }

    public static Availability availability(ItemStack stack, Pokemon pokemon, boolean inBattle) {
        if (!isTechnicalMachine(stack) || pokemon == null) return Availability.INVALID;
        var move = TMMoveComponent.Companion.getTMMove(stack);
        if (move == null) return Availability.INVALID;
        if (inBattle) return Availability.IN_BATTLE;
        if (!pokemon.getForm().getMoves().tmLearnableMoves().contains(move)) return Availability.INCOMPATIBLE;
        if (pokemon.getMoveSet().getMoveTemplates().contains(move)
            || pokemon.getAllAccessibleMoves().contains(move)) return Availability.ALREADY_KNOWN;
        return Availability.LEARNABLE;
    }

    public static boolean teach(ServerPlayer player, ItemStack stack, Pokemon pokemon, boolean inBattle) {
        Availability status = availability(stack, pokemon, inBattle);
        if (status != Availability.LEARNABLE) {
            BagNetwork.notifyTmResult(player, status.label());
            return false;
        }
        var move = TMMoveComponent.Companion.getTMMove(stack);
        boolean benched = !pokemon.getMoveSet().hasSpace();
        boolean learned = benched
            ? pokemon.getBenchedMoves().add(new BenchedMove(move, 0))
            : pokemon.getMoveSet().add(move.create());
        if (!learned) {
            BagNetwork.notifyTmResult(player, Availability.ALREADY_KNOWN.label());
            return false;
        }
        if (!player.isCreative() && !Cobblemon.INSTANCE.getConfig().getInfiniteTmUses()) stack.shrink(1);
        BagNetwork.notifyTmResult(player, Component.translatable(
            "screen.cobbleventure_player_menu.bag.tm." + (benched ? "learned_benched" : "learned"),
            pokemon.getDisplayName(false), move.getDisplayName()));
        player.playNotifySound(CobblemonSounds.MOVE_LEARN, SoundSource.MASTER, 1, 1);
        return true;
    }
}
