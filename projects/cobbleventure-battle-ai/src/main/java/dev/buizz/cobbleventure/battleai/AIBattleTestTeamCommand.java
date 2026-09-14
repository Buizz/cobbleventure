package dev.buizz.cobbleventure.battleai;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.RCTApi;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Loads the web battle lab's official test lineup into the executing player's party. */
final class AIBattleTestTeamCommand {
    static final String PRIMARY_COMMAND = "ai_test_team";
    static final String ALIAS_COMMAND = "aitestteam";
    private static final String TBCS_REGISTRY = "tbcs";

    private AIBattleTestTeamCommand() {}

    static void register(RegisterCommandsEvent event) {
        registerLiteral(event, PRIMARY_COMMAND);
        registerLiteral(event, ALIAS_COMMAND);
    }

    private static void registerLiteral(RegisterCommandsEvent event, String command) {
        event.getDispatcher().register(
                Commands.literal(command)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                                .executes(context -> load(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "level"))))
        );
    }

    private static int load(CommandSourceStack source, int level) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception error) {
            source.sendFailure(Component.literal("플레이어가 직접 실행해야 하는 명령어입니다."));
            return 0;
        }
        if (BattleRegistry.getBattleByParticipatingPlayerId(player.getUUID()) != null) {
            source.sendFailure(Component.literal("배틀 중에는 테스트 파티를 불러올 수 없습니다."));
            return 0;
        }
        RCTApi api = RCTApi.getInstance(TBCS_REGISTRY);
        if (api == null) {
            source.sendFailure(Component.literal("TBCS 트레이너 레지스트리를 찾을 수 없습니다."));
            return 0;
        }

        List<Pokemon> replacement;
        try {
            replacement = AIBattleTestPlayerPreset.load(api, player, level);
            replaceParty(player, replacement);
        } catch (RuntimeException error) {
            source.sendFailure(Component.literal("AI 테스트 파티를 불러오지 못했습니다: " + error.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "DBingsu 공식 테스트 엔트리를 Lv." + level
                        + "로 불러왔습니다. 기존 파티는 PC에 보관했습니다."), false);
        return 1;
    }

    private static void replaceParty(ServerPlayer player, List<Pokemon> replacement) {
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
        AIBattleTestPartyReplacement.replace(new AIBattleTestPartyReplacement.Storage<>() {
            @Override public Pokemon get(int slot) { return party.get(slot); }
            @Override public int freePcSlots() {
                return pc.getBoxes().stream().mapToInt(box -> box.getUnoccupiedSlots()).sum();
            }
            @Override public void removeFromParty(Pokemon value) { party.remove(value); }
            @Override public boolean addToPc(Pokemon value) {
                value.recall();
                return pc.add(value);
            }
            @Override public void removeFromPc(Pokemon value) { pc.remove(value); }
            @Override public void setParty(int slot, Pokemon value) { party.set(slot, value); }
        }, replacement);
    }
}
