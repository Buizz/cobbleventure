package dev.buizz.cobbleventure.playermenu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BagTransactionPlannerTest {
    private static final FakeStack EMPTY = new FakeStack("", 0, 64);
    private final BagTransactionPlanner<FakeStack> planner = new BagTransactionPlanner<>(new FakeOps(), 100);

    @Test
    void removesFromExtendedBagBeforeInventoryAndAddsOutput() {
        List<FakeStack> bag = slots(stack("jam", 3), EMPTY, EMPTY);
        List<FakeStack> inventory = slots(stack("jam", 4), EMPTY);

        BagTransactionPlanner.Status result = planner.plan(
            bag, inventory, List.of(stack("jam", 5)), List.of(stack("tm", 1))
        );

        assertEquals(BagTransactionPlanner.Status.SUCCESS, result);
        assertEquals(0, count(bag, "jam"));
        assertEquals(2, count(inventory, "jam"));
        assertEquals(1, count(bag, "tm"));
    }

    @Test
    void outputCanUseSpaceFreedByIngredients() {
        List<FakeStack> bag = slots(stack("blank_disc", 1));

        BagTransactionPlanner.Status result = planner.plan(
            bag, slots(EMPTY), List.of(stack("blank_disc", 1)), List.of(stack("tm", 1))
        );

        assertEquals(BagTransactionPlanner.Status.SUCCESS, result);
        assertEquals(1, count(bag, "tm"));
    }

    @Test
    void insufficientInputsAreRejected() {
        BagTransactionPlanner.Status result = planner.plan(
            slots(stack("jam", 2)), slots(EMPTY), List.of(stack("jam", 3)), List.of()
        );

        assertEquals(BagTransactionPlanner.Status.INSUFFICIENT_INPUT, result);
    }

    @Test
    void fullOutputIsRejected() {
        BagTransactionPlanner.Status result = planner.plan(
            slots(stack("jam", 64)), slots(EMPTY), List.of(), List.of(stack("tm", 1))
        );

        assertEquals(BagTransactionPlanner.Status.OUTPUT_FULL, result);
    }

    @Test
    void repeatedCostsCannotOverdrawTheSameStack() {
        BagTransactionPlanner.Status result = planner.plan(
            slots(stack("jam", 5)),
            slots(EMPTY),
            List.of(stack("jam", 3), stack("jam", 3)),
            List.of()
        );

        assertEquals(BagTransactionPlanner.Status.INSUFFICIENT_INPUT, result);
    }

    @Test
    void oversizedRequestsAreRejected() {
        BagTransactionPlanner.Status result = planner.plan(
            slots(EMPTY), slots(EMPTY), List.of(stack("jam", 101)), List.of()
        );

        assertEquals(BagTransactionPlanner.Status.INVALID_REQUEST, result);
    }

    private static FakeStack stack(String kind, int count) {
        return new FakeStack(kind, count, 64);
    }

    @Test
    void consumableGrantMergesWithOwnedItemsAndSplitsOversizedStacks() {
        List<FakeStack> bag = slots(stack("rare_candy", 32), EMPTY, EMPTY);
        assertEquals(BagTransactionPlanner.Status.SUCCESS,
            planner.plan(bag, slots(EMPTY), List.of(), List.of(stack("rare_candy", 96))));
        assertEquals(128, count(bag, "rare_candy"));
        assertEquals(64, bag.get(0).count());
        assertEquals(64, bag.get(1).count());
        assertEquals(EMPTY, bag.get(2));
    }

    @Test
    void grantsMultipleUnstackableToolsWithoutCosts() {
        List<FakeStack> bag = slots(stack("existing", 64), EMPTY, EMPTY);
        List<FakeStack> tools = List.of(new FakeStack("flute", 1, 1), new FakeStack("pokedex", 1, 1));
        assertEquals(BagTransactionPlanner.Status.SUCCESS,
            planner.plan(bag, slots(EMPTY), List.of(), tools));
        assertEquals(64, count(bag, "existing"));
        assertEquals(1, count(bag, "flute"));
        assertEquals(1, count(bag, "pokedex"));
    }

    @Test
    void refusesToolBatchThatOnlyPartiallyFits() {
        assertEquals(BagTransactionPlanner.Status.OUTPUT_FULL,
            planner.plan(slots(EMPTY), slots(EMPTY), List.of(),
                List.of(new FakeStack("flute", 1, 1), new FakeStack("pokedex", 1, 1))));
    }

    @Test
    void alreadyOwnedToolsRequireNoFreeSpaceForFlagRepair() {
        assertEquals(BagTransactionPlanner.Status.SUCCESS,
            planner.plan(slots(stack("existing", 64)), slots(EMPTY), List.of(), List.of()));
    }

    private static List<FakeStack> slots(FakeStack... stacks) {
        return new ArrayList<>(List.of(stacks));
    }

    private static int count(List<FakeStack> stacks, String kind) {
        return stacks.stream().filter(stack -> stack.kind().equals(kind)).mapToInt(FakeStack::count).sum();
    }

    private record FakeStack(String kind, int count, int maximumCount) {}

    private static final class FakeOps implements BagTransactionPlanner.StackOps<FakeStack> {
        @Override public boolean isEmpty(FakeStack stack) { return stack.count() <= 0; }
        @Override public boolean same(FakeStack first, FakeStack second) {
            return first.kind().equals(second.kind());
        }
        @Override public int count(FakeStack stack) { return stack.count(); }
        @Override public int maximumCount(FakeStack stack) { return stack.maximumCount(); }
        @Override public FakeStack withCount(FakeStack stack, int count) {
            return count <= 0 ? EMPTY : new FakeStack(stack.kind(), count, stack.maximumCount());
        }
        @Override public FakeStack copy(FakeStack stack) { return stack; }
        @Override public FakeStack empty() { return EMPTY; }
    }
}
