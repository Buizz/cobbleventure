package dev.buizz.cobbleventure.playermenu;

import java.util.List;

/** Game-independent planner used before a bag transaction mutates persisted player state. */
final class BagTransactionPlanner<T> {
    private final StackOps<T> stacks;
    private final int maximumItems;

    BagTransactionPlanner(StackOps<T> stacks, int maximumItems) {
        this.stacks = stacks;
        this.maximumItems = maximumItems;
    }

    Status plan(List<T> bag, List<T> inventory, List<T> costs, List<T> outputs) {
        if (!validRequest(costs) || !validRequest(outputs)) return Status.INVALID_REQUEST;
        for (T cost : costs) {
            if (cost == null || stacks.isEmpty(cost)) continue;
            int remaining = removeFrom(bag, cost, stacks.count(cost));
            remaining = removeFrom(inventory, cost, remaining);
            if (remaining > 0) return Status.INSUFFICIENT_INPUT;
        }
        for (T output : outputs) {
            if (output == null || stacks.isEmpty(output)) continue;
            if (!add(bag, stacks.copy(output))) return Status.OUTPUT_FULL;
        }
        return Status.SUCCESS;
    }

    private boolean validRequest(List<T> requested) {
        if (requested == null) return false;
        long total = 0L;
        for (T stack : requested) {
            if (stack == null || stacks.isEmpty(stack)) continue;
            total += stacks.count(stack);
            if (total > maximumItems) return false;
        }
        return true;
    }

    private int removeFrom(List<T> slots, T prototype, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
            T stored = slots.get(slot);
            if (stacks.isEmpty(stored) || !stacks.same(stored, prototype)) continue;
            int removed = Math.min(remaining, stacks.count(stored));
            int newCount = stacks.count(stored) - removed;
            slots.set(slot, newCount == 0 ? stacks.empty() : stacks.withCount(stored, newCount));
            remaining -= removed;
        }
        return remaining;
    }

    private boolean add(List<T> slots, T incoming) {
        for (int slot = 0; slot < slots.size() && !stacks.isEmpty(incoming); slot++) {
            T stored = slots.get(slot);
            if (stacks.isEmpty(stored) || !stacks.same(stored, incoming)) continue;
            int moved = Math.min(stacks.count(incoming), stacks.maximumCount(stored) - stacks.count(stored));
            if (moved <= 0) continue;
            slots.set(slot, stacks.withCount(stored, stacks.count(stored) + moved));
            incoming = stacks.withCount(incoming, stacks.count(incoming) - moved);
        }
        for (int slot = 0; slot < slots.size() && !stacks.isEmpty(incoming); slot++) {
            if (!stacks.isEmpty(slots.get(slot))) continue;
            int moved = Math.min(stacks.count(incoming), stacks.maximumCount(incoming));
            slots.set(slot, stacks.withCount(incoming, moved));
            incoming = stacks.withCount(incoming, stacks.count(incoming) - moved);
        }
        return stacks.isEmpty(incoming);
    }

    enum Status { SUCCESS, INVALID_REQUEST, INSUFFICIENT_INPUT, OUTPUT_FULL }

    interface StackOps<T> {
        boolean isEmpty(T stack);
        boolean same(T first, T second);
        int count(T stack);
        int maximumCount(T stack);
        T withCount(T stack, int count);
        T copy(T stack);
        T empty();
    }
}
