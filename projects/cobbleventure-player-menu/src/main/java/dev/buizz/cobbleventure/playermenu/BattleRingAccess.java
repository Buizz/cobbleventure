package dev.buizz.cobbleventure.playermenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Reads current server-owned inventory and bag contents, never a permanent ability flag. */
public final class BattleRingAccess {
    private BattleRingAccess() {}

    public static boolean hasStoredRing(LivingEntity entity, TagKey<Item> tag) {
        if (!(entity instanceof ServerPlayer player) || !StoredBattleRings.supports(tag.location().toString())) {
            return false;
        }
        var matches = (java.util.function.Predicate<ItemStack>) stack -> !stack.isEmpty() && stack.is(tag);
        String id = tag.location().toString();
        return StoredBattleRings.contains(id, player.getInventory().items, matches)
            || StoredBattleRings.contains(id, player.getInventory().offhand, matches)
            || matches.test(player.containerMenu.getCarried())
            || StoredBattleRings.contains(id, BagStorage.load(player), matches);
    }
}
