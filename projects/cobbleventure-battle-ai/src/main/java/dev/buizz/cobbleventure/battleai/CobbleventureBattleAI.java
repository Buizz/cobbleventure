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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-game adapter for Cobbleventure AI profiles.
 *
 * <p>RCT owns the battle response protocol while this adapter applies the authored difficulty,
 * strategy and mechanic policy. This keeps generated content on a real registered AI type and
 * gives the independent decision engine a stable Minecraft boundary for later scoring ports.</p>
 */
public final class CobbleventureBattleAI extends RCTBattleAI {
    private final CobbleventureBattleAIConfig profile;
    private final Map<UUID, PendingBatonPass> pendingBatonPassTargets = new ConcurrentHashMap<>();
    private volatile String lastDecisionSource = "rct_fallback";
    private volatile String lastSearchFailure;
    private volatile CobblemonBattleSearch.SearchReplayTrace lastSearchReplayTrace;

    CobbleventureBattleAI(
            CobbleventureBattleAIConfig profile,
            RCTBattleAIConfig rctConfig
    ) {
        super(rctConfig);
        this.profile = profile;
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
        if (forceSwitch) {
            PendingBatonPass pending = pendingBatonPassTargets.remove(battleId);
            if (pending != null && ShowdownBattleLogObservation.hasMoveSince(
                    battle.getBattleLog(), pending.logCursor(), pending.position(), "batonpass")) {
                SwitchActionResponse response = new SwitchActionResponse(pending.target());
                if (response.isValid(active, moveset, true)) return response;
            }
        } else {
            pendingBatonPassTargets.remove(battleId);
        }
        if (usesJvmSearch() && moveset != null) {
            lastSearchFailure = null;
            lastSearchReplayTrace = null;
            try {
                CobblemonBattleSearch.PlannedResponse planned = CobblemonBattleSearch.plan(
                        active,
                        side,
                        moveset,
                        profile.difficulty(),
                        profile.strategy(),
                        forceSwitch
                );
                if (planned != null && planned.response().isValid(active, moveset, forceSwitch)) {
                    if (planned.batonPassTarget() != null) {
                        pendingBatonPassTargets.put(battleId, new PendingBatonPass(
                                planned.batonPassTarget(), battle.getBattleLog().size(), active.getPNX()));
                    }
                    lastSearchReplayTrace = planned.replayTrace();
                    lastDecisionSource = "jvm_search";
                    return planned.response();
                }
                lastSearchFailure = planned == null
                        ? "search returned no plan"
                        : "search returned an invalid response";
            } catch (RuntimeException exception) {
                lastSearchFailure = exception.getClass().getName() + ": " + exception.getMessage();
                // 불완전한 타 모드 전투 상태에서는 RCT의 검증된 기본 선택기로 안전 복귀한다.
            }
        }
        lastDecisionSource = "rct_fallback";
        return super.choose(active, battle, side, moveset, forceSwitch);
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

    private boolean usesJvmSearch() {
        return switch (profile.difficulty()) {
            case "expert_winrate", "expert_search", "cheater" -> true;
            default -> false;
        };
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
