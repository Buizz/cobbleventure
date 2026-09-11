package dev.buizz.cobbleventure.playermenu;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.tms.TechnicalMachine;
import com.cobblemon.mod.common.api.tms.TechnicalMachines;
import com.cobblemon.mod.common.item.components.TMMoveComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Unlocks a locked TM recipe after the completed TM actually reaches a player's storage. */
public final class TmAcquisitionUnlock {
    private static final int INVENTORY_SCAN_INTERVAL = 20;

    private TmAcquisitionUnlock() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TmAcquisitionUnlock::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(TmAcquisitionUnlock::onPlayerTick);
    }

    /** Called by the shared bag mutation boundary after storage has been committed. */
    static void discoverInBag(ServerPlayer player, Iterable<ItemStack> storage) {
        discover(player, storage);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        List<ItemStack> held = new ArrayList<>(player.getInventory().items);
        held.addAll(BagStorage.load(player));
        discover(player, held);
    }

    /** Covers commands and third-party rewards that bypass the shared bag API. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || player.tickCount % INVENTORY_SCAN_INTERVAL != 0) return;
        discover(player, player.getInventory().items);
    }

    private static void discover(ServerPlayer player, Iterable<ItemStack> stacks) {
        var registry = TechnicalMachines.INSTANCE;
        List<ResourceLocation> candidates = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!BagTechnicalMachines.isTechnicalMachine(stack)) continue;
            var move = TMMoveComponent.Companion.getTMMove(stack);
            if (move == null) continue;
            TechnicalMachine tm = registry.getMoveToTM().get(move);
            if (tm != null) candidates.add(tm.getId());
        }
        learnIds(player, candidates);
    }

    static void discoverMoves(ServerPlayer player, Iterable<MoveTemplate> moves) {
        var registry = TechnicalMachines.INSTANCE;
        List<ResourceLocation> candidates = new ArrayList<>();
        for (MoveTemplate move : moves) {
            TechnicalMachine tm = registry.getMoveToTM().get(move);
            if (tm != null) candidates.add(tm.getId());
        }
        learnIds(player, candidates);
    }

    private static void learnIds(ServerPlayer player, List<ResourceLocation> candidates) {
        if (candidates.isEmpty()) return;
        var manager = Cobblemon.INSTANCE.getPlayerDataManager().getTMData(player);
        var registry = TechnicalMachines.INSTANCE;

        Set<ResourceLocation> inherent = new HashSet<>();
        for (ResourceLocation id : candidates) {
            TechnicalMachine tm = registry.getTmMap().get(id);
            if (tm != null && tm.isPassivelyObtained()) inherent.add(id);
        }
        List<ResourceLocation> discovered = TmAcquisitionPlanner.newlyDiscovered(
            candidates,
            manager.getLearnedTMs(),
            inherent,
            Cobblemon.INSTANCE.getConfig().getUnlockAllMoveDexMovesByDefault()
        );
        if (!discovered.isEmpty()) manager.learn(discovered);
    }
}
