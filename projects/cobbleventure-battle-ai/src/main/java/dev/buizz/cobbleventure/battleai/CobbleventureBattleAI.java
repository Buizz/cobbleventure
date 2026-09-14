package dev.buizz.cobbleventure.battleai;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.RCTApi;
import com.gitlab.srcmc.rctapi.api.ai.RCTBattleAI;
import com.gitlab.srcmc.rctapi.api.ai.config.RCTBattleAIConfig;
import com.gitlab.srcmc.rctapi.api.models.Gimmicks;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * In-game adapter for Cobbleventure AI profiles.
 *
 * <p>RCT owns the battle response protocol while this adapter applies the authored difficulty,
 * strategy and mechanic policy. This keeps generated content on a real registered AI type and
 * gives the independent decision engine a stable Minecraft boundary for later scoring ports.</p>
 */
public final class CobbleventureBattleAI extends RCTBattleAI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final CobbleventureBattleAIConfig profile;
    private final boolean decisionLogging;
    private final Map<UUID, PendingBatonPass> pendingBatonPassTargets = new ConcurrentHashMap<>();
    private volatile String lastDecisionSource = "rct_fallback";
    private volatile String lastSearchFailure;
    private volatile CobblemonBattleSearch.SearchReplayTrace lastSearchReplayTrace;

    CobbleventureBattleAI(
            CobbleventureBattleAIConfig profile,
            RCTBattleAIConfig rctConfig
    ) {
        this(profile, rctConfig, false);
    }

    private CobbleventureBattleAI(
            CobbleventureBattleAIConfig profile,
            RCTBattleAIConfig rctConfig,
            boolean decisionLogging
    ) {
        super(rctConfig);
        this.profile = profile;
        this.decisionLogging = decisionLogging;
    }

    @Override
    public ShowdownActionResponse choose(
            ActiveBattlePokemon active,
            PokemonBattle battle,
            BattleSide side,
            ShowdownMoveset moveset,
            boolean forceSwitch
    ) {
        // Cobblemon omits the active moveset from forced-switch requests.
        // Mechanic flags only exist on ordinary move-choice requests.
        if (moveset != null) {
            applyMechanicPolicy(active, moveset);
        }
        UUID battleId = battle.getBattleId();
        BattleProjectionLogCapture.capture(battleId, battle.getBattleLog());
        lastSearchFailure = null;
        lastSearchReplayTrace = null;
        if (forceSwitch) {
            PendingBatonPass pending = pendingBatonPassTargets.remove(battleId);
            if (pending != null && ShowdownBattleLogObservation.hasMoveSince(
                    battle.getBattleLog(), pending.logCursor(), pending.position(), "batonpass")) {
                SwitchActionResponse response = new SwitchActionResponse(pending.target());
                if (response.isValid(active, moveset, true)) {
                    lastDecisionSource = "baton_pass_switch";
                    logDecision(active, battle, moveset, true, response, null);
                    return response;
                }
            }
        } else {
            pendingBatonPassTargets.remove(battleId);
        }
        if (usesIndependentDecisionEngine() && moveset != null) {
            try {
                CobblemonBattleSearch.PlannedResponse planned = CobblemonBattleSearch.plan(
                        active,
                        side,
                        moveset,
                        profile.difficulty(),
                        profile.strategy(),
                        forceSwitch
                );
                if (planned != null) {
                    lastSearchReplayTrace = planned.replayTrace();
                }
                if (planned != null && planned.response().isValid(active, moveset, forceSwitch)) {
                    if (planned.batonPassTarget() != null) {
                        pendingBatonPassTargets.put(battleId, new PendingBatonPass(
                                planned.batonPassTarget(), battle.getBattleLog().size(), active.getPNX()));
                    }
                    lastDecisionSource = "jvm_search";
                    logDecision(active, battle, moveset, forceSwitch,
                            planned.response(), planned.replayTrace());
                    return planned.response();
                }
                lastSearchFailure = planned == null
                        ? "search returned no plan"
                        : "search returned an invalid response: "
                                + responseText(active, moveset, planned.response());
            } catch (RuntimeException exception) {
                lastSearchFailure = exception.getClass().getName() + ": " + exception.getMessage();
                // 불완전한 타 모드 전투 상태에서는 RCT의 검증된 기본 선택기로 안전 복귀한다.
            }
        }
        if (!usesIndependentDecisionEngine() && !forceSwitch && moveset != null) {
            try {
                CobblemonBattleSearch.PlannedResponse screenPlan =
                        CobblemonBattleSearch.planScreenSupport(
                                active, side, moveset, profile.difficulty(), profile.strategy());
                if (screenPlan != null
                        && screenPlan.response().isValid(active, moveset, false)) {
                    lastDecisionSource = "shared_screen_policy";
                    logDecision(active, battle, moveset, false,
                            screenPlan.response(), screenPlan.replayTrace());
                    return screenPlan.response();
                }
                if (screenPlan != null) {
                    lastSearchFailure = "shared_screen_policy produced an invalid response: "
                            + screenPlan.response().toShowdownString(active, moveset);
                }
            } catch (RuntimeException exception) {
                lastSearchFailure = exception.getClass().getName() + ": " + exception.getMessage();
            }
        }
        lastDecisionSource = "rct_fallback";
        ShowdownActionResponse rctResponse;
        try {
            rctResponse = super.choose(active, battle, side, moveset, forceSwitch);
        } catch (RuntimeException exception) {
            if (!forceSwitch) throw exception;
            ShowdownActionResponse recovered = recoverForcedSwitch(active, moveset, true, null);
            if (recovered == null) throw exception;
            lastSearchFailure = "RCT forced-switch selection failed: "
                    + exception.getClass().getName() + ": " + exception.getMessage();
            lastDecisionSource = "forced_switch_recovery";
            logDecision(active, battle, moveset, true, recovered, null);
            return recovered;
        }

        ShowdownActionResponse recovered = recoverForcedSwitch(active, moveset, forceSwitch, rctResponse);
        if (recovered != rctResponse) {
            lastSearchFailure = "RCT produced an invalid forced-switch response: "
                    + (rctResponse == null ? "null" : rctResponse.getType().name());
            lastDecisionSource = "forced_switch_recovery";
        }
        logDecision(active, battle, moveset, forceSwitch, recovered, lastSearchReplayTrace);
        return recovered;
    }

    private static ShowdownActionResponse recoverForcedSwitch(
            ActiveBattlePokemon active,
            ShowdownMoveset moveset,
            boolean forceSwitch,
            ShowdownActionResponse proposed
    ) {
        return ForcedSwitchRecovery.select(
                forceSwitch,
                proposed,
                response -> response.isValid(active, moveset, forceSwitch),
                () -> active.getActor().getPokemonList().stream()
                        .map(pokemon -> (ShowdownActionResponse) new SwitchActionResponse(
                                pokemon.getUuid()))
        );
    }

    String lastDecisionSource() {
        return lastDecisionSource;
    }

    String lastSearchFailure() {
        return lastSearchFailure;
    }

    CobblemonBattleSearch.SearchReplayTrace lastSearchReplayTrace() {
        return lastSearchReplayTrace;
    }

    CobbleventureBattleAI withDifficulty(String difficulty) {
        CobbleventureBattleAIConfig overridden = new CobbleventureBattleAIConfig(
                difficulty,
                profile.strategy(),
                profile.cheatProbability(),
                profile.teraTarget(),
                profile.mechanics()
        );
        return new CobbleventureBattleAI(overridden, overridden.rctConfig(), true);
    }

    private boolean usesIndependentDecisionEngine() {
        return CobbleventureBattleAIConfig.usesIndependentDecisionEngine(profile.difficulty());
    }

    private void logDecision(
            ActiveBattlePokemon active,
            PokemonBattle battle,
            ShowdownMoveset moveset,
            boolean forceSwitch,
            ShowdownActionResponse response,
            CobblemonBattleSearch.SearchReplayTrace replayTrace
    ) {
        if (!decisionLogging) return;
        String species = active.hasPokemon()
                ? active.getBattlePokemon().getOriginalPokemon().getSpecies()
                        .getResourceIdentifier().toString()
                : "none";
        String algorithm = usesIndependentDecisionEngine()
                ? CobbleventureBattleAIConfig.decisionAlgorithm(profile.difficulty())
                : "rct";
        LOGGER.info(
                "[AI battle test decision] battle={}, turn={}, actor={}, species={}, "
                        + "difficulty={}, algorithm={}, source={}, forcedSwitch={}, response={}, "
                        + "failure={}, candidates={}",
                battle.getBattleId(),
                battle.getTurn(),
                active.getPNX(),
                species,
                profile.difficulty(),
                algorithm,
                lastDecisionSource,
                forceSwitch,
                responseText(active, moveset, response),
                lastSearchFailure == null ? "none" : lastSearchFailure,
                candidateSummary(replayTrace)
        );
    }

    private static String responseText(
            ActiveBattlePokemon active,
            ShowdownMoveset moveset,
            ShowdownActionResponse response
    ) {
        if (response == null) return "null";
        try {
            return response.toShowdownString(active, moveset);
        } catch (RuntimeException exception) {
            return response.getType().name() + "(serialization_failed="
                    + exception.getClass().getSimpleName() + ')';
        }
    }

    private static String candidateSummary(CobblemonBattleSearch.SearchReplayTrace replayTrace) {
        if (replayTrace == null || replayTrace.candidates().isEmpty()) return "none";
        return replayTrace.candidates().getFirst().actions().stream()
                .map(action -> String.format(Locale.ROOT, "%s[%s]=%.3f(p=%.3f)",
                        action.getId(), action.getKind(), action.getScore(),
                        action.getSuccessProbability()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private void applyMechanicPolicy(
            ActiveBattlePokemon active,
            ShowdownMoveset moveset
    ) {
        CobbleventureBattleAIConfig.Mechanics mechanics = profile.mechanics();
        if (!mechanics.allowsMegaEvolution()) {
            moveset.setCanMegaEvo(false);
            moveset.setCanUltraBurst(false);
        }
        if (!mechanics.allowsZMove()) {
            moveset.setCanZMove(null);
        }
        if (!mechanics.allowsDynamax()) {
            moveset.setCanDynamax(false);
            moveset.setMaxMoves(null);
        }
        if (!mechanics.allowsTerastallization()) {
            moveset.setCanTerastallize(null);
            return;
        }
        if (!isConfiguredTeraTarget(active)) {
            moveset.setCanTerastallize(null);
            return;
        }
        String automaticTeraType = resolveAutomaticTeraType(active);
        if (automaticTeraType != null) {
            // The Showdown request has already been created at this point. Updating only RCT's
            // trainer gimmick map is too late for the current decision, so update both views.
            moveset.setCanTerastallize(automaticTeraType);
        }
    }

    private boolean isConfiguredTeraTarget(ActiveBattlePokemon active) {
        if (profile.teraTarget() == null || !active.hasPokemon()) {
            return profile.teraTarget() == null;
        }
        String species = active.getBattlePokemon().getOriginalPokemon()
                .getSpecies().getResourceIdentifier().getPath();
        return profile.teraTarget().equalsIgnoreCase(species);
    }

    private static String resolveAutomaticTeraType(ActiveBattlePokemon active) {
        if (!active.hasPokemon()) {
            return null;
        }
        Pokemon pokemon = active.getBattlePokemon().getOriginalPokemon();
        return RCTApi.getInstances()
                .map(entry -> entry.getValue().getTrainerRegistry().getByOT(pokemon, TrainerNPC.class))
                .filter(trainer -> trainer != null)
                .findFirst()
                .map(trainer -> {
                    Gimmicks gimmicks = trainer.getGimmicks().of(pokemon);
                    if (gimmicks.tera() == null || !"auto".equalsIgnoreCase(gimmicks.tera())) {
                        return null;
                    }
                    String primaryType = pokemon.getPrimaryType().getName();
                    trainer.getGimmicks().to(
                            pokemon,
                            new Gimmicks(primaryType, gimmicks.dynamax(), gimmicks.gmax())
                    );
                    return primaryType;
                })
                .orElse(null);
    }

    private record PendingBatonPass(UUID target, int logCursor, String position) {}
}
