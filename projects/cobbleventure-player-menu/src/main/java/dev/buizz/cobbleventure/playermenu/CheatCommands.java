package dev.buizz.cobbleventure.playermenu;

import com.cobblemon.mod.common.api.tms.TechnicalMachines;
import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only, additive test setup commands using the normal progression stores. */
final class CheatCommands {
    private CheatCommands() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(CheatCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("cobbleventure_cheat")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal(
                    "/cobbleventure_cheat <badges|hm|menus|destinations|tm|team|money|key_items|mega_ring|z_ring|items|all> [대상] · items는 [대상] [수량] 지원"), false);
                return 1;
            });
        for (String action : List.of("badges", "hm", "menus", "destinations", "tm", "team", "key_items", "mega_ring", "z_ring", "all")) {
            root.then(Commands.literal(action)
                .executes(context -> apply(context.getSource(),
                    List.of(context.getSource().getPlayerOrException()), action))
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(context -> apply(context.getSource(),
                        EntityArgument.getPlayers(context, "players"), action))));
        }
        root.then(CheatItems.command());
        dispatcher.register(root);
    }

    private static int apply(CommandSourceStack source, Collection<ServerPlayer> players, String action) {
        if (action.equals("all")) return applyAll(source, players);
        if (action.equals("team")) return CheatTeam.grant(source, players);
        if (action.equals("key_items") || action.equals("mega_ring") || action.equals("z_ring"))
            return grantImportantItems(source, players, action);
        Set<String> badges = Set.of();
        if (action.equals("badges")) {
            var resource = source.getServer().getResourceManager().getResource(
                ResourceLocation.parse("cobbleventure_player_menu:league/badges.json"));
            if (resource.isEmpty()) {
                source.sendFailure(Component.literal("배지 카탈로그를 찾을 수 없습니다."));
                return 0;
            }
            try (Reader reader = resource.get().openAsReader()) {
                badges = CheatBadgeCatalog.readIds(reader);
            } catch (IOException | RuntimeException error) {
                source.sendFailure(Component.literal("배지 카탈로그를 읽을 수 없습니다: " + error.getMessage()));
                return 0;
            }
        }
        for (ServerPlayer player : players) {
            if (action.equals("badges")) BadgeProgressNetwork.grantAll(player, badges);
            if (action.equals("hm")) PlayerOverviewNetwork.grantAll(player);
            if (action.equals("menus")) ProgressionNetwork.unlockAll(player);
            if (action.equals("destinations")) MapNetwork.visitAll(player);
            if (action.equals("tm")) {
                TmAcquisitionUnlock.discoverMoves(player, TechnicalMachines.INSTANCE.getMoveToTM().keySet());
            }
        }
        source.sendSuccess(() -> Component.literal(
            "[Cobbleventure] 테스트 설정 " + action + " 적용 완료 · 대상 " + players.size() + "명"
                + " (이미 획득한 항목은 유지)"), true);
        return players.size();
    }

    private static int applyAll(CommandSourceStack source, Collection<ServerPlayer> players) {
        int completed = 0;
        for (ServerPlayer player : players) {
            // Player-only arguments reject bare UUIDs as potentially including non-player entities.
            // Use the resolved player's name while retaining the original command source and permissions.
            var failed = CheatBatch.failures(action -> source.getServer().getCommands().getDispatcher().execute(
                CheatBatch.command(action, player.getGameProfile().getName()), source));
            if (failed.isEmpty()) {
                completed++;
            } else {
                source.sendFailure(Component.literal("[Cobbleventure] " + player.getScoreboardName()
                    + " · all 일부 미적용: " + String.join(", ", failed)
                    + (failed.size() == CheatBatch.ACTIONS.size()
                        ? " · 완료된 항목이 없습니다." : " · 나머지 성공 항목은 적용되었습니다.")));
            }
        }
        int count = completed;
        source.sendSuccess(() -> Component.literal("[Cobbleventure] all 치트 9종 적용 결과 · 전체 완료 "
            + count + "/" + players.size() + "명"), true);
        return completed;
    }

    private static int grantImportantItems(CommandSourceStack source, Collection<ServerPlayer> players, String action) {
        int completed = 0;
        for (ServerPlayer player : players) {
            try {
                var result = action.equals("key_items") ? ImportantItemProtection.grantAll(player)
                    : ImportantItemProtection.grantRing(player, action);
                if (!result.success()) {
                    source.sendFailure(Component.literal(player.getScoreboardName() + ": 중요도구 지급 실패 · "
                        + (result.status() == BagTransaction.Status.OUTPUT_FULL
                            ? "가방 공간이 부족합니다." : result.status().name())));
                    continue;
                }
                player.sendSystemMessage(Component.literal(
                    "[Cobbleventure] " + action + " 획득 완료! 이미 보유한 도구는 유지하고 부족한 도구만 지급했습니다."));
                completed++;
            } catch (IllegalStateException error) {
                source.sendFailure(Component.literal(player.getScoreboardName() + ": " + error.getMessage()));
            }
        }
        int count = completed;
        source.sendSuccess(() -> Component.literal("[Cobbleventure] 중요도구 치트 적용 완료 · 대상 " + count + "명"), true);
        return completed;
    }

}
