package dev.buizz.cobbleventure.battleai;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.gitlab.srcmc.rctapi.api.RCTApi;
import com.gitlab.srcmc.rctapi.api.battle.BattleState;
import com.gitlab.srcmc.rctapi.api.events.Events;
import com.gitlab.srcmc.rctapi.api.trainer.Trainer;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Starts the fixed Lorelei AI test against the executing player's current party. */
final class AIBattleTestCommand {
    static final String PRIMARY_COMMAND = "ai_battle_test";
    static final String ALIAS_COMMAND = "aibattletest";
    static final String LORELEI_TRAINER_ID = "rctmod:kanto_elite_1";
    static final String TEST_DIFFICULTY = "expert_search";
    private static final String TBCS_REGISTRY = "tbcs";
    private static final long PENDING_RETENTION_TICKS = 20L * 30L;
    private static final Map<String, PendingOpponent> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingOpponent> ACTIVE = new ConcurrentHashMap<>();
    private static boolean registered;
    private static boolean battleListenerRegistered;

    private AIBattleTestCommand() {}

    static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(AIBattleTestCommand::registerCommands);
        NeoForge.EVENT_BUS.addListener(AIBattleTestCommand::onServerTick);
        NeoForge.EVENT_BUS.addListener(AIBattleTestCommand::onServerStopped);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal(PRIMARY_COMMAND)
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> start(context.getSource()))
        );
        event.getDispatcher().register(
                Commands.literal(ALIAS_COMMAND)
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> start(context.getSource()))
        );
    }

    private static int start(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception error) {
            source.sendFailure(Component.literal("플레이어가 직접 실행해야 하는 명령어입니다."));
            return 0;
        }
        if (BattleRegistry.getBattleByParticipatingPlayerId(player.getUUID()) != null) {
            source.sendFailure(Component.literal("이미 다른 배틀에 참가 중입니다."));
            return 0;
        }
        if (PENDING.values().stream().anyMatch(entry -> entry.playerId().equals(player.getUUID()))
                || ACTIVE.values().stream().anyMatch(entry -> entry.playerId().equals(player.getUUID()))) {
            source.sendFailure(Component.literal("이미 AI 배틀 테스트를 시작하고 있습니다."));
            return 0;
        }
        if (Cobblemon.INSTANCE.getStorage().getParty(player).occupied() == 0) {
            source.sendFailure(Component.literal("왼쪽 엔트리로 사용할 현재 파티가 비어 있습니다."));
            return 0;
        }

        RCTApi api = RCTApi.getInstance(TBCS_REGISTRY);
        if (api == null) {
            source.sendFailure(Component.literal("TBCS 트레이너 레지스트리를 찾을 수 없습니다."));
            return 0;
        }
        TrainerRegistry registry = api.getTrainerRegistry();
        TrainerNPC authored = registry.getById(LORELEI_TRAINER_ID, TrainerNPC.class);
        if (authored == null) {
            source.sendFailure(Component.literal(
                    "AI 테스트용 칸나 트레이너가 없습니다. 프로젝트 콘텐츠를 다시 빌드해 주세요: "
                            + LORELEI_TRAINER_ID));
            return 0;
        }
        if (!(authored.getBattleAI() instanceof CobbleventureBattleAI authoredAI)) {
            source.sendFailure(Component.literal(
                    "칸나 트레이너에 Cobbleventure AI가 연결되어 있지 않습니다. 콘텐츠 빌드를 확인해 주세요."));
            return 0;
        }

        ArmorStand opponent = createOpponent(player);
        if (!player.serverLevel().addFreshEntity(opponent)) {
            source.sendFailure(Component.literal("오른쪽 테스트 엔트리를 생성하지 못했습니다."));
            return 0;
        }

        TrainerNPC runtimeTrainer = AIBattleTestTrainerFactory.create(
                authored, authoredAI, opponent, TEST_DIFFICULTY);
        String runtimeId = runtimeTrainerId(player.getUUID(), opponent.getUUID());
        ensureBattleListener(api);
        registry.registerNPC(runtimeId, runtimeTrainer);
        PendingOpponent pending = new PendingOpponent(
                runtimeId,
                player.getUUID(),
                runtimeTrainer,
                opponent,
                source.getServer().overworld().getGameTime() + PENDING_RETENTION_TICKS
        );
        PENDING.put(runtimeId, pending);

        String command = battleCommand(player.getUUID(), opponent.getUUID(), runtimeId);
        source.getServer().getCommands().performPrefixedCommand(
                source.getServer().createCommandSourceStack().withPermission(2), command);

        source.sendSuccess(() -> Component.literal(
                "AI 배틀 테스트를 시작합니다: 현재 파티(왼쪽) vs 칸나(오른쪽), AI="
                        + TEST_DIFFICULTY), false);
        return 1;
    }

    private static ArmorStand createOpponent(ServerPlayer player) {
        Vec3 position = opponentPosition(player.position(), player.getYRot());
        ArmorStand opponent = new ArmorStand(
                player.serverLevel(), position.x(), position.y(), position.z());
        opponent.setInvisible(true);
        byte flags = opponent.getEntityData().get(ArmorStand.DATA_CLIENT_FLAGS);
        opponent.getEntityData().set(
                ArmorStand.DATA_CLIENT_FLAGS,
                (byte) (flags | ArmorStand.CLIENT_FLAG_MARKER));
        opponent.setNoGravity(true);
        opponent.setInvulnerable(true);
        opponent.setSilent(true);
        opponent.setCustomName(Component.literal("AI Battle Test - Lorelei"));
        opponent.addTag("cobbleventure_ai_battle_test");
        return opponent;
    }

    static Vec3 opponentPosition(Vec3 playerPosition, float yawDegrees) {
        double yawRadians = Math.toRadians(yawDegrees);
        return playerPosition.add(
                -Math.sin(yawRadians) * 4.0D,
                0.0D,
                Math.cos(yawRadians) * 4.0D
        );
    }

    static String runtimeTrainerId(UUID playerId, UUID opponentId) {
        return CobbleventureBattleAIMod.MOD_ID + ":test/lorelei/"
                + playerId.toString().replace("-", "") + "/"
                + opponentId.toString().replace("-", "");
    }

    static String battleCommand(UUID playerId, UUID opponentId, String runtimeTrainerId) {
        return "tbcs battle GEN_9_SINGLES " + playerId
                + " vs " + opponentId + " as " + runtimeTrainerId;
    }

    private static void ensureBattleListener(RCTApi api) {
        if (battleListenerRegistered) return;
        battleListenerRegistered = true;
        api.getEventContext().register(Events.BATTLE_STARTED,
                event -> onBattleStarted(event.getValue()));
        api.getEventContext().register(Events.BATTLE_ENDED,
                event -> onBattleEnded(event.getValue()));
    }

    private static void onBattleStarted(BattleState state) {
        UUID battleId = state.getBattle().getBattleId();
        Stream<Trainer> participants = Stream.concat(
                state.getParticipants1().stream(), state.getParticipants2().stream());
        var startedTrainers = participants.toList();
        PENDING.entrySet().stream()
                .filter(entry -> startedTrainers.contains(entry.getValue().trainer()))
                .findFirst()
                .ifPresent(entry -> {
                    if (PENDING.remove(entry.getKey(), entry.getValue())) {
                        ACTIVE.put(battleId, entry.getValue());
                    }
                });
    }

    private static void onBattleEnded(BattleState state) {
        PendingOpponent opponent = ACTIVE.remove(state.getBattle().getBattleId());
        if (opponent != null) cleanup(opponent);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        PENDING.entrySet().stream()
                .filter(entry -> entry.getValue().expiresAt() < gameTime)
                .toList()
                .forEach(entry -> {
                    if (PENDING.remove(entry.getKey(), entry.getValue())) {
                        cleanup(entry.getValue());
                    }
                });
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        Stream.concat(PENDING.values().stream(), ACTIVE.values().stream())
                .distinct()
                .forEach(AIBattleTestCommand::cleanup);
        PENDING.clear();
        ACTIVE.clear();
    }

    private static void cleanup(PendingOpponent pending) {
        RCTApi api = RCTApi.getInstance(TBCS_REGISTRY);
        if (api != null) api.getTrainerRegistry().unregisterById(pending.runtimeId());
        pending.entity().discard();
    }

    private record PendingOpponent(
            String runtimeId,
            UUID playerId,
            TrainerNPC trainer,
            ArmorStand entity,
            long expiresAt
    ) {}
}
