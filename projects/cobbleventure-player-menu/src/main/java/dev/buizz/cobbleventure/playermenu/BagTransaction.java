package dev.buizz.cobbleventure.playermenu;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Applies machine costs and outputs against the inventory and extended bag as one operation.
 * Costs are removed from the extended bag first and outputs are always placed in that bag.
 */
public final class BagTransaction {
    public static final int MAX_ITEMS_PER_REQUEST = 1_000_000;
    private static final BagTransactionPlanner<ItemStack> PLANNER = new BagTransactionPlanner<>(
        new BagTransactionPlanner.StackOps<>() {
            @Override public boolean isEmpty(ItemStack stack) { return stack.isEmpty(); }
            @Override public boolean same(ItemStack first, ItemStack second) {
                return ItemStack.isSameItemSameComponents(first, second);
            }
            @Override public int count(ItemStack stack) { return stack.getCount(); }
            @Override public int maximumCount(ItemStack stack) { return stack.getMaxStackSize(); }
            @Override public ItemStack withCount(ItemStack stack, int count) {
                return count <= 0 ? ItemStack.EMPTY : stack.copyWithCount(count);
            }
            @Override public ItemStack copy(ItemStack stack) { return stack.copy(); }
            @Override public ItemStack empty() { return ItemStack.EMPTY; }
        },
        MAX_ITEMS_PER_REQUEST
    );

    private BagTransaction() {}

    public static Result execute(
        ServerPlayer player, List<ItemStack> costs, List<ItemStack> outputs
    ) {
        return execute(player, costs, outputs, SideEffect.NONE);
    }

    /**
     * Executes an item transaction together with an optional currency or machine-state mutation.
     * The side effect must be reversible until this method returns successfully.
     */
    public static Result execute(
        ServerPlayer player,
        List<ItemStack> costs,
        List<ItemStack> outputs,
        SideEffect sideEffect
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(sideEffect, "sideEffect");

        NonNullList<ItemStack> originalBag = copyOf(BagStorage.load(player));
        NonNullList<ItemStack> originalInventory = copyInventory(player);
        NonNullList<ItemStack> workingBag = copyOf(originalBag);
        NonNullList<ItemStack> workingInventory = copyOf(originalInventory);
        Result planned = plan(workingBag, workingInventory, costs, outputs);
        if (!planned.success()) return planned;
        if (!sideEffect.canApply()) return new Result(Status.SIDE_EFFECT_REJECTED);

        boolean sideEffectApplied = false;
        try {
            sideEffectApplied = true;
            sideEffect.apply();
            applyInventory(player, workingInventory);
            BagStorage.save(player, workingBag);
            BagNetwork.syncExternalMutation(player, workingBag);
            return new Result(Status.SUCCESS);
        } catch (RuntimeException error) {
            applyInventory(player, originalInventory);
            BagStorage.save(player, originalBag);
            BagNetwork.syncExternalMutation(player, originalBag);
            if (sideEffectApplied) {
                try {
                    sideEffect.rollback();
                } catch (RuntimeException ignored) {
                    // The item state is restored even if an external integration cannot roll itself back.
                }
            }
            return new Result(Status.COMMIT_FAILED);
        }
    }

    /** Mutates only the supplied working copies, allowing the complete operation to be validated first. */
    static Result plan(
        List<ItemStack> bag,
        List<ItemStack> inventory,
        List<ItemStack> costs,
        List<ItemStack> outputs
    ) {
        BagTransactionPlanner.Status status = PLANNER.plan(bag, inventory, costs, outputs);
        return new Result(Status.valueOf(status.name()));
    }

    private static NonNullList<ItemStack> copyInventory(ServerPlayer player) {
        NonNullList<ItemStack> result = NonNullList.withSize(36, ItemStack.EMPTY);
        for (int slot = 0; slot < result.size(); slot++) {
            result.set(slot, player.getInventory().getItem(slot).copy());
        }
        return result;
    }

    private static NonNullList<ItemStack> copyOf(List<ItemStack> source) {
        NonNullList<ItemStack> result = NonNullList.withSize(source.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < source.size(); slot++) {
            result.set(slot, source.get(slot).copy());
        }
        return result;
    }

    private static void applyInventory(ServerPlayer player, List<ItemStack> source) {
        for (int slot = 0; slot < Math.min(36, source.size()); slot++) {
            player.getInventory().setItem(slot, source.get(slot).copy());
        }
        player.getInventory().setChanged();
    }

    public enum Status {
        SUCCESS,
        INVALID_REQUEST,
        INSUFFICIENT_INPUT,
        OUTPUT_FULL,
        SIDE_EFFECT_REJECTED,
        COMMIT_FAILED
    }

    public record Result(Status status) {
        public boolean success() {
            return status == Status.SUCCESS;
        }
    }

    /** Reversible non-item state, such as a currency balance or a machine charge. */
    public interface SideEffect {
        SideEffect NONE = new SideEffect() {
            @Override public boolean canApply() { return true; }
            @Override public void apply() {}
            @Override public void rollback() {}
        };

        boolean canApply();
        void apply();
        void rollback();
    }
}
