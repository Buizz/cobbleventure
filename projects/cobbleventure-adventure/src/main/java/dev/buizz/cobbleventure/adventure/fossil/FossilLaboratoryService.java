package dev.buizz.cobbleventure.adventure.fossil;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.fossil.Fossil;
import com.cobblemon.mod.common.api.fossil.Fossils;
import com.cobblemon.mod.common.block.entity.FossilMultiblockEntity;
import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure;
import dev.buizz.cobbleventure.adventure.mixin.FossilMachineAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Uses the installed version's actual machine, recipes, rendering and collection logic. */
public final class FossilLaboratoryService {
    public static final String JOB = "cobbleventure_fossil_job";
    public static final int SPEED = 24; // 14,400 native ticks / 24 = 30 seconds at 20 TPS.
    private FossilLaboratoryService() {}

    public static Component text(String key, Object... args) {
        return Component.translatable("fossil.cobbleventure_adventure." + key, args);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FossilLaboratoryService::onUse);
        NeoForge.EVENT_BUS.addListener(FossilLaboratoryService::onBreak);
        NeoForge.EVENT_BUS.addListener(FossilLaboratoryService::onExplosion);
    }

    public static FossilMultiblockStructure machineAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FossilMultiblockEntity entity
            && entity.getMultiblockStructure() instanceof FossilMultiblockStructure machine) return machine;
        return null;
    }

    public static FossilMultiblockStructure findMachine(Entity npc) {
        FossilMultiblockStructure closest = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(npc.blockPosition().offset(-12, -3, -12),
                                                  npc.blockPosition().offset(12, 5, 12))) {
            if (!npc.level().hasChunkAt(pos)) continue;
            var machine = machineAt(npc.level(), pos);
            if (machine == null) continue;
            double candidate = machine.getTankBasePos().distSqr(npc.blockPosition());
            if (candidate < distance) { closest = machine; distance = candidate; }
        }
        return closest;
    }

    public static CompoundTag job(FossilMultiblockStructure machine, Level level) {
        BlockEntity tank = level.getBlockEntity(machine.getTankBasePos());
        return tank == null ? new CompoundTag() : tank.getPersistentData().getCompound(JOB);
    }

    public static int[] ingredients(ServerPlayer player, Fossil fossil) {
        var inventory = player.getInventory().items;
        return FossilIngredientSelection.select(fossil.getFossils().size(),
            inventory.stream().map(ItemStack::getCount).toList(),
            (ingredient, slot) -> fossil.getFossils().get(ingredient).test(inventory.get(slot)));
    }

    public static List<Fossil> available(ServerPlayer player) {
        return Fossils.all().stream().filter(f -> !f.getFossils().isEmpty())
            .filter(f -> ingredients(player, f) != null)
            .sorted(Comparator.comparing(f -> f.getIdentifier().toString())).toList();
    }

    public static Component start(ServerPlayer player, Entity npc, Fossil fossil) {
        var machine = findMachine(npc);
        if (machine == null) return text("no_machine");
        if (!job(machine, player.level()).isEmpty() || machine.isRunning() || machine.getHasCreatedPokemon()
            || !machine.getFossilInventory().isEmpty() || machine.getOrganicMaterialInside() > 0) return text("busy");
        int[] slots = ingredients(player, fossil);
        if (slots == null || slots.length == 0) return text("missing");
        List<ItemStack> input = new ArrayList<>();
        for (int slot : slots) input.add(player.getInventory().items.get(slot).copyWithCount(1));
        // The native machine must resolve exactly the recipe the menu displayed.
        if (Fossils.getFossilByItemStacks(input) != fossil) return text("missing");
        var tank = player.level().getBlockEntity(machine.getTankBasePos());
        if (tank == null) return text("no_machine");
        var record = new CompoundTag();
        record.putUUID("owner", player.getUUID());
        record.putUUID("npc", npc.getUUID());
        record.putString("recipe", fossil.getIdentifier().toString());
        tank.getPersistentData().put(JOB, record);
        machine.setFossilInventory(input);
        machine.updateFossilType(player.level());
        FossilMachineAccess access = (FossilMachineAccess)(Object)machine;
        access.cobbleventure$setOrganicMaterial(FossilMultiblockStructure.MATERIAL_TO_START);
        access.cobbleventure$setOwner(player.getUUID());
        for (int slot : slots) player.getInventory().items.get(slot).shrink(1);
        player.getInventory().setChanged();
        machine.startMachine(player.level());
        tank.setChanged();
        return text("started");
    }

    public static Component collect(ServerPlayer player, Entity npc) {
        var machine = findMachine(npc);
        if (machine == null) return text("no_machine");
        CompoundTag record = job(machine, player.level());
        if (!record.hasUUID("owner") || !record.getUUID("owner").equals(player.getUUID())) return text("not_owner");
        if (!machine.getHasCreatedPokemon()) return text("working");
        var storage = Cobblemon.INSTANCE.getStorage();
        if (storage.getParty(player).occupied() >= 6
            && storage.getPC(player).getBoxes().stream().noneMatch(box -> box.getUnoccupiedSlots() > 0)) return text("storage_full");
        // Delegate to Cobblemon so 1.8 alpha/shiny rolls, revived events and advancement remain intact.
        // The lab supplies a ball; preserve the player's held stack even if an extension throws.
        ItemStack held = player.getMainHandItem();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CobblemonItems.POKE_BALL));
            BlockPos pos = machine.getTankBasePos();
            machine.useWithoutItem(player.level().getBlockState(pos), player.level(), pos, player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
        if (machine.getHasCreatedPokemon()) return text("working");
        var tank = player.level().getBlockEntity(machine.getTankBasePos());
        tank.getPersistentData().remove(JOB);
        tank.setChanged();
        return text("collected");
    }

    public static void beforeTick(FossilMultiblockStructure machine, Level level) {
        if (level.isClientSide || job(machine, level).isEmpty()) return;
        var access = (FossilMachineAccess)(Object)machine;
        access.cobbleventure$setProtection(machine.getHasCreatedPokemon() ? 6000 : -1);
        if (machine.getTimeRemaining() > 0)
            access.cobbleventure$setTimeRemaining(Math.max(1, machine.getTimeRemaining() - SPEED + 1));
    }

    public static void afterTick(FossilMultiblockStructure machine, Level level) {
        if (level.isClientSide || job(machine, level).isEmpty()) return;
        if (level.getGameTime() % 10 == 0) {
            machine.updateProgress(level);
            machine.syncToClient(level);
            machine.markDirty(level);
        }
        var record = job(machine, level);
        if (machine.getHasCreatedPokemon() && !record.getBoolean("notified") && record.hasUUID("owner")) {
            var player = level.getServer().getPlayerList().getPlayer(record.getUUID("owner"));
            if (player != null) { player.sendSystemMessage(text("ready")); record.putBoolean("notified", true); }
        }
    }

    private static boolean protectedAt(Level level, BlockPos pos) {
        var machine = machineAt(level, pos);
        if (machine == null) machine = machineAt(level, pos.below());
        return machine != null && !job(machine, level).isEmpty();
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (protectedAt(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && protectedAt(level, event.getPos())) event.setCanceled(true);
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> protectedAt(event.getLevel(), pos));
    }
}
