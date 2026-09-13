package dev.buizz.cobbleventure.bootstrap;

import com.google.gson.JsonObject;
import dev.buizz.cobbleventure.adventure.PokemonCenterDefeatReturn;
import dev.buizz.cobbleventure.adventure.event.EventBattleBridge;
import dev.buizz.cobbleventure.adventure.event.NpcBattleResolvedEvent;
import dev.buizz.cobbleventure.playermenu.BattlePositioningEvent;
import dev.buizz.cobbleventure.playermenu.PlayerConditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owns ordered room admission and per-player challenge progress in authored leagues. */
final class LeagueRuntimeSystem {
    private static final String DATA = "cobbleventureLeagueRuns";
    private static final Map<String, Instance> INSTANCES = new LinkedHashMap<>();
    private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();
    private static final Map<UUID, Instance> RETURNS = new HashMap<>();

    private LeagueRuntimeSystem() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LeagueRuntimeSystem::beforeBattle);
        NeoForge.EVENT_BUS.addListener(LeagueRuntimeSystem::resolved);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LeagueRuntimeSystem::login);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LeagueRuntimeSystem::logout);
        NeoForge.EVENT_BUS.addListener(LeagueRuntimeSystem::clonePlayer);
        NeoForge.EVENT_BUS.addListener(LeagueRuntimeSystem::tick);
    }

    static void clear() { INSTANCES.clear(); ATTEMPTS.clear(); RETURNS.clear(); }

    static void add(String key, JsonObject config, List<Room> rooms) {
        if (rooms.size() != config.getAsJsonArray("rooms").size()) {
            throw new IllegalStateException("League rooms are unavailable: " + key);
        }
        List<PlayerConditions.Condition> conditions = new ArrayList<>();
        config.getAsJsonArray("conditions").forEach(value -> conditions.add(PlayerConditions.parse(value.getAsJsonObject())));
        List<String> locked = new ArrayList<>();
        config.getAsJsonArray("locked_dialogue").forEach(value -> locked.add(value.getAsString()));
        // Stable authored IDs/ordering/NPCs/room choices invalidate incompatible in-progress runs.
        String signature = config.get("mode") + "|" + config.get("rooms");
        INSTANCES.put(key, new Instance(key, config.get("id").getAsString(),
            config.get("mode").getAsString().equals("relay"), signature,
            config.get("condition_mode").getAsString(), List.copyOf(conditions),
            List.copyOf(locked), List.copyOf(rooms),
            config.has("next_generation") ? config.get("next_generation").getAsInt() : 0,
            config.has("generation_travel_mode") && config.get("generation_travel_mode").getAsString().equals("travel_test")));
    }

    record Room(ServerLevel level, BlockPos origin, Vec3i size, BlockPos entry,
                float yaw, int stage, String npcTag, BlockPos npc) {
        boolean contains(ServerPlayer player) {
            return player.serverLevel() == level
                && BuildingEventSpaceBounds.contains(origin, size, player.blockPosition());
        }
    }

    private record Instance(String key, String id, boolean relay, String signature,
                            String mode, List<PlayerConditions.Condition> conditions,
                            List<String> locked, List<Room> rooms, int nextGeneration, boolean travelTest) {
        Room lobby() { return rooms.getFirst(); }
        int stages() { return rooms.size() - 2; }
        Room stage(int stage) { return rooms.get(stage + 1); }
        Room containing(ServerPlayer player) {
            return rooms.stream().filter(room -> room.contains(player)).findFirst().orElse(null);
        }
        boolean allows(ServerPlayer player) {
            return conditions.isEmpty() || PlayerConditions.matches(player, mode, conditions);
        }
    }

    private record Attempt(Instance instance, int stage, UUID npc) {}
    record Route(BuildingRuntimeSystem.DoorTarget target, Runnable arrived) {}

    static boolean travelToNextGeneration(ServerPlayer player, int generation) {
        for (Instance instance : INSTANCES.values()) {
            Room source = instance.containing(player);
            if (source == null || source.stage != instance.stages()) continue;
            LeagueRunProgress progress = progress(state(player, instance), instance);
            if (!instance.travelTest || instance.nextGeneration != generation || !progress.cleared()) {
                message(player, "리그 클리어 후 설정된 다음 세대로 이동할 수 있습니다.");
                return false;
            }
            if (!StarterSpawnSystem.movePlayerToGenerationStart(player, generation)) {
                message(player, "다음 세대의 시작 위치를 준비하지 못했습니다. 서버 설정을 확인하세요.");
                return false;
            }
            write(player, instance, progress, false);
            ATTEMPTS.remove(player.getUUID());
            RETURNS.remove(player.getUUID());
            message(player, generation + "세대 시작 위치로 이동했습니다. 이동 테스트이므로 아이템과 포켓몬은 유지됩니다.");
            return true;
        }
        return false;
    }

    static boolean travelToNextGeneration(ServerPlayer player) {
        for (Instance instance : INSTANCES.values()) {
            Room room = instance.containing(player);
            if (room != null && room.stage == instance.stages()) {
                return travelToNextGeneration(player, instance.nextGeneration);
            }
        }
        message(player, "명예의 전당에서 안내원에게 말을 걸어 주세요.");
        return false;
    }

    static Route route(ServerPlayer player, BuildingRuntimeSystem.DoorTarget target) {
        for (Instance instance : INSTANCES.values()) {
            Room destination = instance.rooms.stream().filter(room ->
                room.level.dimension().equals(target.dimension()) && room.entry.equals(target.position())
            ).findFirst().orElse(null);
            if (destination == null) continue;
            Room source = instance.containing(player);
            CompoundTag state = state(player, instance);
            LeagueRunProgress progress = progress(state, instance);
            if (destination.stage == -1) {
                boolean leavingChallenge = source != null && source.stage >= 0 && source.stage < instance.stages();
                if (progress.redirectsLobbyToHall(source != null && source.stage >= 0)) {
                    return new Route(target(target, instance.stage(instance.stages())), () -> {
                        write(player, instance, progress, false);
                        ATTEMPTS.remove(player.getUUID());
                    });
                }
                if (!progress.cleared() && source == null && RETURNS.get(player.getUUID()) != instance && !instance.allows(player)) {
                    instance.locked.forEach(line -> message(player, line));
                    return null;
                }
                return new Route(target, () -> {
                    write(player, instance, progress.interrupt(), false);
                    ATTEMPTS.remove(player.getUUID());
                    RETURNS.remove(player.getUUID());
                    if (leavingChallenge) message(player, progress.cleared()
                        ? "리그 로비로 돌아왔습니다. 클리어 기록은 유지됩니다."
                        : instance.relay ? "리그 도전을 포기했습니다. 다음 도전은 첫 방부터 시작합니다."
                        : "리그 도전을 포기했습니다. 다음 도전은 마지막으로 승리한 방 다음부터 이어집니다.");
                });
            }
            if (!progress.cleared() && (source == null || source.stage == -1) && !instance.allows(player)) {
                instance.locked.forEach(line -> message(player, line));
                return null;
            }
            boolean fromLobby = source != null && source.stage == -1;
            if (fromLobby && destination.stage == 0) {
                // Lobby always links to stage one in authoring; admission chooses the saved stage.
                Room resume = instance.stage(progress.completed());
                return new Route(target(target, resume), () -> write(player, instance, progress, true));
            }
            if (source == null || !state.getBoolean("active")
                || !state.getString("instance").equals(instance.key)
                || destination.stage != progress.completed() || source.stage + 1 != destination.stage) {
                message(player, "이 방의 트레이너에게 승리한 뒤 다음 방으로 이동할 수 있습니다.");
                return null;
            }
            return new Route(target, () -> {});
        }
        // Every authored route leaving the facility interrupts the run.
        return new Route(target, () -> interruptAll(player));
    }

    private static BuildingRuntimeSystem.DoorTarget target(BuildingRuntimeSystem.DoorTarget original, Room room) {
        return new BuildingRuntimeSystem.DoorTarget(room.level.dimension(), room.entry,
            original.conditions(), original.conditionMode(), original.lockedDialogue(),
            original.enterDialogue(), true, original.musicTrack(), room.yaw);
    }

    private static CompoundTag state(ServerPlayer player, Instance instance) {
        CompoundTag root = player.getPersistentData().getCompound(DATA);
        CompoundTag state = root.getCompound(instance.id);
        // Migrate completed challenges written before the hall became a persistent hub.
        boolean cleared = state.getBoolean("cleared") || (
            state.getString("signature").equals(instance.signature)
                && state.getInt("completed") == instance.stages());
        if (!state.getString("signature").equals(instance.signature)) {
            state = new CompoundTag();
            state.putString("signature", instance.signature);
            state.putBoolean("relay", instance.relay);
            root.put(instance.id, state);
            player.getPersistentData().put(DATA, root);
        }
        if (cleared) {
            state.putBoolean("cleared", true);
            state.putInt("completed", instance.stages());
        }
        return state;
    }

    private static LeagueRunProgress progress(CompoundTag state, Instance instance) {
        return new LeagueRunProgress(Math.clamp(state.getInt("completed"), 0, instance.stages()),
            instance.stages(), instance.relay);
    }

    private static void write(ServerPlayer player, Instance instance, LeagueRunProgress progress, boolean active) {
        CompoundTag state = state(player, instance);
        state.putInt("completed", progress.completed());
        state.putBoolean("cleared", progress.cleared());
        state.putBoolean("active", active);
        state.putString("instance", instance.key);
    }

    private static void beforeBattle(BattlePositioningEvent event) {
        if (event.isCanceled()) return;
        ServerPlayer player = event.player();
        for (Instance instance : INSTANCES.values()) {
            for (Room room : instance.rooms) {
                if (room.npcTag == null || event.opponent().level() != room.level
                    || !AuthoredNpcIdentity.matches(event.opponent().getTags(), room.npcTag)
                    || event.opponent().distanceToSqr(Vec3.atBottomCenterOf(room.npc)) > 16) continue;
                CompoundTag state = state(player, instance);
                if (!room.contains(player) || !state.getBoolean("active")
                    || !state.getString("instance").equals(instance.key)
                    || !progress(state, instance).canBattle(room.stage)) {
                    event.setCanceled(true);
                    message(player, "로비에서 도전을 시작하고 정해진 순서대로 진행하세요. 승리한 방에서는 출구로 이동하세요.");
                    return;
                }
                ATTEMPTS.put(player.getUUID(), new Attempt(instance, room.stage, event.opponent().getUUID()));
                return;
            }
        }
        boolean retiredLeagueNpc = INSTANCES.values().stream().flatMap(instance -> instance.rooms.stream())
            .anyMatch(room -> AuthoredNpcIdentity.matches(event.opponent().getTags(), room.npcTag));
        if (retiredLeagueNpc) {
            event.setCanceled(true);
            message(player, "이전 리그 배치입니다. 현재 리그 로비를 통해 입장하세요.");
        }
    }

    private static void resolved(NpcBattleResolvedEvent event) {
        Attempt attempt = ATTEMPTS.get(event.player().getUUID());
        if (attempt == null || !attempt.npc.equals(event.npc())) return;
        ATTEMPTS.remove(event.player().getUUID());
        ServerPlayer player = event.player();
        CompoundTag state = state(player, attempt.instance);
        if (!state.getBoolean("active") || !state.getString("instance").equals(attempt.instance.key)) return;
        LeagueRunProgress progress = progress(state, attempt.instance);
        if (!progress.canBattle(attempt.stage)) return;
        Room room = attempt.instance.containing(player);
        if (event.outcome().equals("win") && (room == null || room.stage != attempt.stage)) {
            write(player, attempt.instance, progress.interrupt(), false);
            RETURNS.put(player.getUUID(), attempt.instance);
            return;
        }
        if (event.outcome().equals("win")) {
            write(player, attempt.instance, progress.win(attempt.stage), true);
        } else {
            write(player, attempt.instance, progress.interrupt(), false);
            RETURNS.put(player.getUUID(), attempt.instance);
            message(player, attempt.instance.relay ? "도전이 종료되었습니다. 로비에서 처음부터 다시 도전하세요."
                : "도전이 중단되었습니다. 로비에서 마지막 승리 다음 방부터 이어갈 수 있습니다.");
        }
    }

    private static void interruptAll(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(DATA);
        for (String id : root.getAllKeys()) {
            CompoundTag value = root.getCompound(id);
            if (!value.getBoolean("active")) continue;
            if (value.getBoolean("relay") && !value.getBoolean("cleared")) value.putInt("completed", 0);
            value.putBoolean("active", false);
        }
        ATTEMPTS.remove(player.getUUID());
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag root = player.getPersistentData().getCompound(DATA);
        root.getAllKeys().forEach(id -> {
            CompoundTag value = root.getCompound(id);
            if (value.getBoolean("active")) value.putBoolean("interrupted", true);
        });
        interruptAll(player);
        RETURNS.remove(player.getUUID());
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag root = player.getPersistentData().getCompound(DATA);
        List<String> interruptedInstances = root.getAllKeys().stream().filter(id ->
            root.getCompound(id).getBoolean("active") || root.getCompound(id).getBoolean("interrupted"))
            .map(id -> root.getCompound(id).getString("instance")).toList();
        // Migrate before interruption, but capture returns before a signature update replaces state.
        INSTANCES.values().forEach(instance -> state(player, instance));
        interruptAll(player); // Also handles a process restart without a logout callback.
        if (!interruptedInstances.isEmpty()) EventBattleBridge.forfeitInterruptedBattle(player);
        if (!interruptedInstances.isEmpty()) {
            for (String key : interruptedInstances) {
                Instance instance = INSTANCES.get(key);
                if (instance != null) {
                    RETURNS.put(player.getUUID(), instance);
                    break;
                }
            }
        }
        root.getAllKeys().forEach(id -> root.getCompound(id).remove("interrupted"));
    }

    private static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getPersistentData().put(DATA, event.getOriginal().getPersistentData().getCompound(DATA).copy());
        interruptAll(player);
    }

    private static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            // Both battle-result handlers have run by this tick. Defeat recovery owns the
            // destination; also discard queued lobby returns so they cannot fire after healing.
            if (suppressLobbyReturnForRecovery(player.getUUID(),
                PokemonCenterDefeatReturn.hasDefeatRecovery(player),
                RETURNS)) continue;
            if (AuthoredBattlePositions.inBattle(player)) continue;
            Instance pending = RETURNS.get(player.getUUID());
            if (pending != null && returnToLobby(player, pending)) RETURNS.remove(player.getUUID());
            for (Instance instance : INSTANCES.values()) {
                Room room = instance.containing(player);
                CompoundTag state = state(player, instance);
                boolean activeHere = state.getBoolean("active") && state.getString("instance").equals(instance.key);
                if (activeHere && (room == null || room.stage == -1)) {
                    write(player, instance, progress(state, instance).interrupt(), false);
                    ATTEMPTS.remove(player.getUUID());
                } else if (room != null && room.stage >= 0 && !activeHere
                    && !(room.stage == instance.stages() && progress(state, instance).cleared())) {
                    returnToLobby(player, instance);
                }
            }
        }
    }

    static boolean suppressLobbyReturnForRecovery(
        UUID playerId, boolean recovering, Map<UUID, ?> pendingReturns
    ) {
        if (!recovering) return false;
        pendingReturns.remove(playerId);
        return true;
    }

    private static boolean returnToLobby(ServerPlayer player, Instance instance) {
        Room lobby = instance.lobby();
        return BuildingRuntimeSystem.activateTarget(player, new BuildingRuntimeSystem.DoorTarget(
            lobby.level.dimension(), lobby.entry, List.of(), "all", List.of(), List.of(), true, null, lobby.yaw), 10L);
    }

    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }
}
