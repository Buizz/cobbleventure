package dev.buizz.cobbleventure.casino;

import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.narrnouille.cobblemoncasino.data.PlayerCasinoBalanceData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Extends the shared cheat root from the module that owns both currency dependencies. */
final class CasinoMoneyCheat {
    private CasinoMoneyCheat() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(CasinoMoneyCheat::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cobbleventure_cheat")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("money")
                .requires(source -> source.hasPermission(2))
                .executes(context -> refill(context.getSource(),
                    List.of(context.getSource().getPlayerOrException())))
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(context -> refill(context.getSource(), EntityArgument.getPlayers(context, "players"))))));
    }

    private static int refill(CommandSourceStack source, Collection<ServerPlayer> players) {
        var balances = PlayerCasinoBalanceData.get(source.getServer());
        for (ServerPlayer player : players) {
            PlayerExtensionKt.setCobbleDollars(player, CheatMoneyAmounts.DOLLARS);
            // Assign instead of adding: repeated use must never overflow the chip balance.
            balances.setBalance(player.getUUID(), CheatMoneyAmounts.CHIPS);
            CasinoHudNetwork.syncNow(player);
            player.sendSystemMessage(Component.literal(
                "[Cobbleventure] 코블달러와 카지노칩을 각각 20억(2,000,000,000)으로 설정했습니다."));
        }
        source.sendSuccess(() -> Component.literal(
            "[Cobbleventure] 돈 치트 적용 완료 · 대상 " + players.size() + "명"), true);
        return players.size();
    }
}
