package dev.buizz.cobbleventure.playermenu;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Repeatable recovery and training supplies, committed as a complete bag transaction. */
final class CheatItems {
    private static final int DEFAULT_COUNT = 64;
    private static final int MAX_COUNT = 4096;
    private static final List<String> ITEMS = List.of(
        "full_restore", "max_revive", "max_elixir", "rare_candy",
        "exp_candy_xs", "exp_candy_s", "exp_candy_m", "exp_candy_l", "exp_candy_xl"
    );

    private CheatItems() {}

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("items")
            .requires(source -> source.hasPermission(2))
            .executes(context -> grant(context.getSource(),
                List.of(context.getSource().getPlayerOrException()), DEFAULT_COUNT))
            .then(Commands.argument("players", EntityArgument.players())
                .executes(context -> grant(context.getSource(),
                    EntityArgument.getPlayers(context, "players"), DEFAULT_COUNT))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                    .executes(context -> grant(context.getSource(),
                        EntityArgument.getPlayers(context, "players"), IntegerArgumentType.getInteger(context, "count")))));
    }

    private static int grant(CommandSourceStack source, Collection<ServerPlayer> players, int count) {
        List<ItemStack> outputs = new ArrayList<>();
        // Resolve every item before granting anything; do not silently issue an incomplete kit.
        for (String path : ITEMS) {
            var id = ResourceLocation.fromNamespaceAndPath("cobblemon", path);
            var item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isEmpty()) {
                source.sendFailure(Component.literal("[Cobbleventure] 지급할 아이템을 찾을 수 없습니다: " + id));
                return 0;
            }
            outputs.add(new ItemStack(item.get(), count));
        }
        int completed = 0;
        for (ServerPlayer player : players) {
            var result = BagTransaction.execute(player, List.of(), outputs);
            if (!result.success()) {
                source.sendFailure(Component.literal(player.getScoreboardName() + ": 아이템 지급 실패 · "
                    + (result.status() == BagTransaction.Status.OUTPUT_FULL
                        ? "가방 공간이 부족합니다." : result.status().name())));
                continue;
            }
            player.sendSystemMessage(Component.literal(
                "[Cobbleventure] 회복·성장 아이템 " + ITEMS.size() + "종을 각각 " + count + "개씩 가방에 지급했습니다."));
            completed++;
        }
        int targets = completed;
        source.sendSuccess(() -> Component.literal(
            "[Cobbleventure] 아이템 치트 적용 완료 · 대상 " + targets + "명 · 각 " + count + "개"), true);
        return completed;
    }
}
