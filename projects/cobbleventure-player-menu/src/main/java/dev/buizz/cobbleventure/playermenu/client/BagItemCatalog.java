package dev.buizz.cobbleventure.playermenu.client;

import dev.buizz.cobbleventure.playermenu.BagTechnicalMachines;
import dev.buizz.cobbleventure.playermenu.CobbleventurePlayerMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Shared client-side categorization, search and quantity aggregation for bag views. */
final class BagItemCatalog {
    private static final TagKey<Item> KEY_ITEMS = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(CobbleventurePlayerMenu.MOD_ID, "key_items")
    );
    private static final TagKey<Item> MACHINES = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(CobbleventurePlayerMenu.MOD_ID, "machines")
    );

    private BagItemCatalog() {}

    static boolean matchesSearch(ItemStack stack, Player player, String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        StringBuilder searchable = new StringBuilder(stack.getHoverName().getString().toLowerCase(Locale.ROOT))
            .append(' ').append(itemId.toString().toLowerCase(Locale.ROOT));
        for (Component line : stack.getTooltipLines(Item.TooltipContext.EMPTY, player, TooltipFlag.NORMAL)) {
            searchable.append(' ').append(line.getString().toLowerCase(Locale.ROOT));
        }
        return searchable.toString().contains(normalized);
    }

    static List<Entry> aggregate(
        Player player, List<ItemStack> extendedSlots, Category category, String query
    ) {
        List<Entry> groups = new ArrayList<>();
        Map<Integer, List<Integer>> buckets = new HashMap<>();
        for (int slot = 9; slot < 36; slot++) {
            add(groups, buckets, player.getInventory().getItem(slot), false);
        }
        for (int slot = 0; slot < 9; slot++) {
            add(groups, buckets, player.getInventory().getItem(slot), false);
        }
        for (ItemStack stack : extendedSlots) add(groups, buckets, stack, true);

        groups.removeIf(entry -> !category.matches(entry.stack())
            || !matchesSearch(entry.stack(), player, query));
        groups.sort(Comparator
            .comparing((Entry entry) -> entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT))
            .thenComparing(entry -> BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString()));
        return List.copyOf(groups);
    }

    private static void add(
        List<Entry> groups,
        Map<Integer, List<Integer>> buckets,
        ItemStack stack,
        boolean extended
    ) {
        if (stack.isEmpty()) return;
        int hash = ItemStack.hashItemAndComponents(stack);
        List<Integer> candidates = buckets.computeIfAbsent(hash, ignored -> new ArrayList<>());
        for (int candidate : candidates) {
            Entry entry = groups.get(candidate);
            if (!ItemStack.isSameItemSameComponents(entry.stack(), stack)) continue;
            groups.set(candidate, new Entry(
                entry.stack(),
                saturatedAdd(entry.count(), stack.getCount()),
                saturatedAdd(entry.inventoryCount(), extended ? 0 : stack.getCount()),
                saturatedAdd(entry.extendedCount(), extended ? stack.getCount() : 0)
            ));
            return;
        }
        candidates.add(groups.size());
        groups.add(new Entry(
            stack.copyWithCount(1), stack.getCount(), extended ? 0 : stack.getCount(),
            extended ? stack.getCount() : 0
        ));
    }

    private static int saturatedAdd(int first, int second) {
        long sum = (long)first + second;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)sum;
    }

    enum Category {
        ALL("all"), RECOVERY("recovery"), BALLS("balls"), MACHINES("machines"), BATTLE("battle"),
        MATERIALS("materials"), KEY_ITEMS("key_items");

        private final String id;

        Category(String id) { this.id = id; }

        Component title() {
            return Component.translatable("screen.cobbleventure_player_menu.bag.category." + id);
        }

        boolean matches(ItemStack stack) {
            if (this == ALL) return true;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String namespace = itemId.getNamespace();
            String path = itemId.getPath();
            return switch (this) {
                case RECOVERY -> stack.has(DataComponents.FOOD)
                    || containsAny(path, "potion", "heal", "revive", "ether", "elixir", "berry", "candy", "rice_cake");
                case BALLS -> namespace.equals("cobblemon") && (path.endsWith("_ball") || path.contains("poke_ball"));
                case MACHINES -> isTechnicalMachine(stack, namespace, path);
                case BATTLE -> stack.isDamageableItem()
                    || containsAny(path, "sword", "bow", "shield", "vest", "band", "specs", "scarf", "gem");
                case KEY_ITEMS -> stack.is(BagItemCatalog.KEY_ITEMS)
                    || containsAny(path, "pokedex", "exp_share", "key", "badge", "map", "compass");
                case MATERIALS -> !RECOVERY.matches(stack) && !BALLS.matches(stack)
                    && !MACHINES.matches(stack) && !BATTLE.matches(stack) && !KEY_ITEMS.matches(stack);
                case ALL -> true;
            };
        }

        private static boolean isTechnicalMachine(
            ItemStack stack, String namespace, String path
        ) {
            return stack.is(BagItemCatalog.MACHINES)
                || BagTechnicalMachines.isTechnicalMachine(stack)
                || namespace.equals("tmcraft")
                || path.equals("technical_machine")
                || path.startsWith("tm_")
                || path.startsWith("tr_");
        }

        private static boolean containsAny(String value, String... candidates) {
            for (String candidate : candidates) if (value.contains(candidate)) return true;
            return false;
        }
    }

    record Entry(ItemStack stack, int count, int inventoryCount, int extendedCount) {}
}
