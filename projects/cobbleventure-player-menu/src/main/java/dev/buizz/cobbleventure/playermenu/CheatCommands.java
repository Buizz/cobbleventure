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
                    "/cobbleventure_cheat <badges|hm|menus|destinations|tm|team|money|all> [대상] · 생략하면 자신"), false);
                return 1;
            });
        for (String action : List.of("badges", "hm", "menus", "destinations", "tm", "team", "all")) {
            root.then(Commands.literal(action)
                .executes(context -> apply(context.getSource(),
                    List.of(context.getSource().getPlayerOrException()), action))
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(context -> apply(context.getSource(),
                        EntityArgument.getPlayers(context, "players"), action))));
        }
        dispatcher.register(root);
    }

    private static int apply(CommandSourceStack source, Collection<ServerPlayer> players, String action) {
        if (action.equals("team")) return CheatTeam.grant(source, players);
        Set<String> badges = Set.of();
        if (action.equals("badges") || action.equals("all")) {
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
            if (action.equals("badges") || action.equals("all")) BadgeProgressNetwork.grantAll(player, badges);
            if (action.equals("hm") || action.equals("all")) PlayerOverviewNetwork.grantAll(player);
            if (action.equals("menus") || action.equals("all")) ProgressionNetwork.unlockAll(player);
            if (action.equals("destinations") || action.equals("all")) MapNetwork.visitAll(player);
            if (action.equals("tm") || action.equals("all")) {
                TmAcquisitionUnlock.discoverMoves(player, TechnicalMachines.INSTANCE.getMoveToTM().keySet());
            }
        }
        source.sendSuccess(() -> Component.literal(
            "[Cobbleventure] 테스트 설정 " + action + " 적용 완료 · 대상 " + players.size() + "명"
                + " (이미 획득한 항목은 유지)"), true);
        return players.size();
    }

}
