package dev.buizz.cobbleventure.battleai;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline;
import com.cobblemon.mod.common.api.moves.animations.ActionEffects;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.ErroredBattleStart;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Headless integration tests which exercise the real Cobblemon battle protocol. */
@GameTestHolder(CobbleventureBattleAIMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CobbleventureBattleAIGameTests {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FAILURE_REPORT_TICK = 20_000;

    private CobbleventureBattleAIGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 21_000)
    public static void headlessExpertSearchBattleCompletes(GameTestHelper helper) {
        TracingBattleAI redAI = tracingAI("red", "aggressive");
        TracingBattleAI blueAI = tracingAI("blue", "defensive");
        TrainerBattleActor red = actor(
                "headless-red",
                redAI,
                pokemon("cobblemon:pikachu", 50,
                        "thunderbolt", "quickattack", "thunderwave", "growl")
        );
        TrainerBattleActor blue = actor(
                "headless-blue",
                blueAI,
                pokemon("cobblemon:blastoise", 50,
                        "surf", "icebeam", "protect", "tackle")
        );

        BattleStartResult result = BattleRegistry.startBattle(
                headlessSinglesFormat(),
                new BattleSide(red),
                new BattleSide(blue),
                true
        );
        if (result instanceof ErroredBattleStart error) {
            helper.fail("Headless battle creation failed: " + error.getErrors());
            return;
        }
        if (!(result instanceof SuccessfulBattleStart success)) {
            helper.fail("Unknown battle creation result: " + result.getClass().getName());
            return;
        }

        PokemonBattle battle = success.getBattle();
        battle.setMute(true);
        Map<ResourceLocation, ActionEffectTimeline> actionEffects =
                ActionEffects.INSTANCE.getActionEffects();
        Map<ResourceLocation, ActionEffectTimeline> savedActionEffects =
                new LinkedHashMap<>(actionEffects);
        actionEffects.clear();
        primeHeadlessActive(red);
        primeHeadlessActive(blue);
        AtomicInteger completed = new AtomicInteger();
        Map<TrainerBattleActor, Object> drivenRequests = new IdentityHashMap<>();
        helper.onEachTick(() -> {
            if (completed.get() != 0) {
                return;
            }
            if (hasHeadlessChoiceRequest(red) && hasHeadlessChoiceRequest(blue)) {
                driveHeadlessChoice(red, drivenRequests);
                driveHeadlessChoice(blue, drivenRequests);
            }
            if (battle.getEnded() || winnerName(battle, red, blue) != null) {
                completed.set(1);
                restoreActionEffects(actionEffects, savedActionEffects);
                writeReport(battle, red, blue, redAI, blueAI, "completed", null);
                helper.assertTrue(redAI.choiceCount() > 0, "Red AI made no choices");
                helper.assertTrue(blueAI.choiceCount() > 0, "Blue AI made no choices");
                helper.assertTrue(redAI.jvmSearchChoiceCount() > 0,
                        "Red AI never used the JVM search decision");
                helper.assertTrue(blueAI.jvmSearchChoiceCount() > 0,
                        "Blue AI never used the JVM search decision");
                helper.assertTrue(winnerName(battle, red, blue) != null,
                        "Battle ended without a recognized winner");
                helper.succeed();
                return;
            }
            if (helper.getTick() >= FAILURE_REPORT_TICK) {
                completed.set(1);
                restoreActionEffects(actionEffects, savedActionEffects);
                writeReport(battle, red, blue, redAI, blueAI, "timed_out",
                        "Battle did not end before tick " + FAILURE_REPORT_TICK);
                helper.fail("Headless battle timed out; report was written");
            }
        });
    }

    private static void primeHeadlessActive(TrainerBattleActor actor) {
        if (actor.getActivePokemon().size() != 1 || actor.getPokemonList().isEmpty()) {
            throw new IllegalStateException("Headless singles actor has an unexpected team layout");
        }
        ActiveBattlePokemon active = actor.getActivePokemon().getFirst();
        active.setBattlePokemon(actor.getPokemonList().getFirst());
    }

    private static void restoreActionEffects(
            Map<ResourceLocation, ActionEffectTimeline> actionEffects,
            Map<ResourceLocation, ActionEffectTimeline> savedActionEffects
    ) {
        actionEffects.clear();
        actionEffects.putAll(savedActionEffects);
    }

    private static void driveHeadlessChoice(
            TrainerBattleActor actor,
            Map<TrainerBattleActor, Object> drivenRequests
    ) {
        if (actor.getRequest() == null
                || actor.getRequest().getActive() == null) {
            return;
        }
        Object request = actor.getRequest();
        if (drivenRequests.put(actor, request) == request) {
            return;
        }
        if (actor.getActivePokemon().stream().noneMatch(ActiveBattlePokemon::hasPokemon)) {
            actor.getPokemonList().stream()
                    .filter(pokemon -> pokemon.getHealth() > 0)
                    .findFirst()
                    .ifPresent(pokemon -> actor.getActivePokemon().getFirst().setBattlePokemon(pokemon));
        }
        if (actor.getActivePokemon().stream().noneMatch(ActiveBattlePokemon::hasPokemon)) {
            return;
        }
        actor.onChoiceRequested();
    }

    private static boolean hasHeadlessChoiceRequest(TrainerBattleActor actor) {
        return actor.getRequest() != null && actor.getRequest().getActive() != null;
    }

    private static BattleFormat headlessSinglesFormat() {
        BattleFormat base = BattleFormat.Companion.getGEN_9_SINGLES();
        LinkedHashSet<String> rules = new LinkedHashSet<>(base.getRuleSet());
        rules.add("Team Preview");
        return base.copy(base.getMod(), base.getBattleType(), rules, base.getGen(), base.getAdjustLevel());
    }

    private static TracingBattleAI tracingAI(String side, String strategy) {
        BattleAI delegate = new CobbleventureBattleAIConfig(
                "expert_search",
                strategy,
                null,
                new CobbleventureBattleAIConfig.Mechanics()
        ).createBattleAI();
        return new TracingBattleAI(side, delegate);
    }

    private static TrainerBattleActor actor(
            String name,
            BattleAI ai,
            BattlePokemon... team
    ) {
        return new HeadlessTrainerBattleActor(name, UUID.randomUUID(), List.of(team), ai);
    }

    private static BattlePokemon pokemon(
            String species,
            int level,
            String... moves
    ) {
        PokemonProperties properties = new PokemonProperties();
        properties.setSpecies(species);
        properties.setLevel(level);
        properties.setMoves(List.of(moves));
        return BattlePokemon.Companion.safeCopyOf(properties.create());
    }

    private static void writeReport(
            PokemonBattle battle,
            TrainerBattleActor red,
            TrainerBattleActor blue,
            TracingBattleAI redAI,
            TracingBattleAI blueAI,
            String status,
            String error
    ) {
        String configuredPath = System.getProperty("cobbleventure.battleAiReport");
        if (configuredPath == null || configuredPath.isBlank()) {
            return;
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scenario", "headless_expert_search_1v1");
        report.put("status", status);
        report.put("battleId", battle.getBattleId().toString());
        report.put("turns", battle.getTurn());
        report.put("winner", winnerName(battle, red, blue));
        report.put("red", Map.of(
                "trainer", red.getTrainerName(),
                "decisions", redAI.decisions(),
                "actorState", actorState(red)));
        report.put("blue", Map.of(
                "trainer", blue.getTrainerName(),
                "decisions", blueAI.decisions(),
                "actorState", actorState(blue)));
        report.put("battleLog", new ArrayList<>(battle.getBattleLog()));
        if (error != null) {
            report.put("error", error);
        }

        Path output = Path.of(configuredPath);
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, GSON.toJson(report), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write headless battle report to " + output, exception);
        }
    }

    private static Map<String, Object> actorState(TrainerBattleActor actor) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("requestPresent", actor.getRequest() != null);
        state.put("requestActivePresent", actor.getRequest() != null
                && actor.getRequest().getActive() != null);
        state.put("mustChoose", actor.getMustChoose());
        state.put("responseTypes", actor.getResponses().stream()
                .map(response -> response.getType().name())
                .toList());
        state.put("activeSlots", actor.getActivePokemon().stream()
                .map(active -> Map.of("pnx", active.getPNX(), "hasPokemon", active.hasPokemon()))
                .toList());
        state.put("teamHealth", actor.getPokemonList().stream()
                .map(BattlePokemon::getHealth)
                .toList());
        return state;
    }

    private static String winnerName(
            PokemonBattle battle,
            TrainerBattleActor red,
            TrainerBattleActor blue
    ) {
        if (battle.getWinners().contains(red)) {
            return red.getTrainerName();
        }
        if (battle.getWinners().contains(blue)) {
            return blue.getTrainerName();
        }
        for (String message : battle.getBattleLog()) {
            for (String line : message.split("\\R")) {
                if (!line.startsWith("|win|")) {
                    continue;
                }
                String winnerId = line.substring("|win|".length());
                if (winnerId.equals(red.getUuid().toString())) {
                    return red.getTrainerName();
                }
                if (winnerId.equals(blue.getUuid().toString())) {
                    return blue.getTrainerName();
                }
            }
        }
        return null;
    }

    private static final class TracingBattleAI implements BattleAI {
        private final String side;
        private final BattleAI delegate;
        private final List<Map<String, Object>> decisions = new CopyOnWriteArrayList<>();

        private TracingBattleAI(String side, BattleAI delegate) {
            this.side = side;
            this.delegate = delegate;
        }

        @Override
        public ShowdownActionResponse choose(
                ActiveBattlePokemon active,
                PokemonBattle battle,
                BattleSide battleSide,
                ShowdownMoveset moveset,
                boolean forceSwitch
        ) {
            ShowdownActionResponse response = delegate.choose(
                    active, battle, battleSide, moveset, forceSwitch);
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("side", side);
            decision.put("turn", battle.getTurn());
            decision.put("active", active.getPNX());
            decision.put("forcedSwitch", forceSwitch);
            decision.put("type", response.getType().name());
            decision.put("showdown", response.toShowdownString(active, moveset));
            if (delegate instanceof CobbleventureBattleAI cobbleventureAI) {
                decision.put("source", cobbleventureAI.lastDecisionSource());
                if (cobbleventureAI.lastSearchFailure() != null) {
                    decision.put("searchFailure", cobbleventureAI.lastSearchFailure());
                }
                if (cobbleventureAI.lastSearchReplayTrace() != null) {
                    decision.put("searchReplay", cobbleventureAI.lastSearchReplayTrace());
                }
            }
            decisions.add(decision);
            return response;
        }

        private int choiceCount() {
            return decisions.size();
        }

        private long jvmSearchChoiceCount() {
            return decisions.stream()
                    .filter(decision -> "jvm_search".equals(decision.get("source")))
                    .count();
        }

        private List<Map<String, Object>> decisions() {
            return List.copyOf(decisions);
        }
    }

    private static final class HeadlessTrainerBattleActor extends TrainerBattleActor {
        private HeadlessTrainerBattleActor(
                String name,
                UUID gameId,
                List<? extends BattlePokemon> team,
                BattleAI battleAI
        ) {
            super(name, gameId, team, battleAI);
        }

        @Override
        public void sendUpdate(NetworkPacket<?> packet) {
            // There is no client to receive the choice prompt. The GameTest driver responds
            // after both actors have received their Showdown request for the current turn.
        }
    }
}
