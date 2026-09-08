package dev.buizz.cobbleventure.playermenu;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.pokemon.EvGainedEvent;
import com.cobblemon.mod.common.api.pokemon.stats.BattleEvSource;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

/** Applies the player's progression cap to battle-only Pokemon copies. */
public final class BattleLevelCap {
    private static final System.Logger LOGGER = System.getLogger(BattleLevelCap.class.getName());
    private static final int UNRESTRICTED_LEVEL_CAP = 100;
    private static final Set<PokemonBattle> FAINT_AWARDED_BATTLES =
        Collections.newSetFromMap(new WeakHashMap<>());

    private BattleLevelCap() {}

    public static void register() {
        CobblemonEvents.BATTLE_FAINTED.subscribe(
            Priority.LOWEST,
            BattleLevelCap::awardEvsOnFaint
        );
        CobblemonEvents.EV_GAINED_EVENT_PRE.subscribe(
            Priority.HIGHEST,
            BattleLevelCap::suppressDeferredBattleEvGain
        );
        CobblemonEvents.EV_GAINED_EVENT_POST.subscribe(
            Priority.LOWEST,
            BattleLevelCap::persistBattleCloneEvGain
        );
    }

    /**
     * Cobbleventure Experience pays battle rewards when a Pokemon faints and
     * suppresses Cobblemon's deferred experience payout. Pay EVs at the same
     * reliable point so battle-only level-cap copies retain their earned EVs.
     */
    private static void awardEvsOnFaint(BattleFaintedEvent event) {
        BattlePokemon killed = event.getKilled();
        BattleActor killedActor = null;
        for (BattleActor actor : event.getBattle().getActors()) {
            if (actor.getPokemonList().contains(killed)) {
                killedActor = actor;
                break;
            }
        }
        if (killedActor == null) return;

        FAINT_AWARDED_BATTLES.add(event.getBattle());
        int awarded = 0;
        for (BattleActor actor : killedActor.getSide().getOppositeSide().getActors()) {
            if (!(actor instanceof PlayerBattleActor)) continue;
            for (BattlePokemon recipient : actor.getPokemonList()) {
                if (recipient.getHealth() <= 0) continue;
                var yields = Cobblemon.INSTANCE.getEvYieldCalculator().calculate(recipient, killed);
                Pokemon affected = recipient.getEffectedPokemon();
                for (var yield : yields.entrySet()) {
                    int actual = affected.getEvs().add(yield.getKey(), yield.getValue());
                    awarded += actual;
                    Pokemon original = recipient.getOriginalPokemon();
                    if (actual > 0 && original != affected) {
                        original.getEvs().add(yield.getKey(), actual);
                    }
                }
            }
        }
        if (awarded > 0) {
            LOGGER.log(
                System.Logger.Level.INFO,
                "Battle EV payout completed: battle={0}, defeated={1}, awarded={2}",
                event.getBattle().getBattleId(),
                killed.getEffectedPokemon().getSpecies().getResourceIdentifier(),
                awarded
            );
        } else {
            LOGGER.log(
                System.Logger.Level.WARNING,
                "Battle EV payout produced no EVs: battle={0}, defeated={1}",
                event.getBattle().getBattleId(),
                killed.getEffectedPokemon().getSpecies().getResourceIdentifier()
            );
        }
    }

    /** Prevent Cobblemon's battle-end pass from paying the same KO a second time. */
    private static void suppressDeferredBattleEvGain(EvGainedEvent.Pre event) {
        if (event.getSource() instanceof BattleEvSource source
            && FAINT_AWARDED_BATTLES.contains(source.getBattle())) {
            event.cancel();
        }
    }

    public static List<BattlePokemon> adjustPlayerTeam(
        UUID playerId, List<? extends BattlePokemon> team
    ) {
        ServerPlayer player = PlayerExtensionsKt.getPlayer(playerId);
        if (player == null) {
            return new ArrayList<>(team);
        }

        int levelCap = ProgressionNetwork.levelCap(player);
        if (levelCap >= UNRESTRICTED_LEVEL_CAP) {
            return new ArrayList<>(team);
        }

        List<BattlePokemon> adjusted = new ArrayList<>(team.size());
        for (BattlePokemon battlePokemon : team) {
            adjusted.add(adjustPokemon(playerId, battlePokemon, levelCap));
        }
        return adjusted;
    }

    private static BattlePokemon adjustPokemon(
        UUID playerId, BattlePokemon source, int levelCap
    ) {
        Pokemon original = source.getOriginalPokemon();
        ServerPlayer owner = original.getOwnerPlayer();
        if (owner == null || !owner.getUUID().equals(playerId)
            || source.getEffectedPokemon().getLevel() <= levelCap) {
            return source;
        }

        Pokemon currentBattleCopy = source.getEffectedPokemon();
        if (currentBattleCopy != original) {
            lowerLevelAndScaleHealth(currentBattleCopy, levelCap);
            source.getPostBattlePokemonOperations().add(ignored -> {
                copyPersistentBattleState(original, currentBattleCopy);
                return kotlin.Unit.INSTANCE;
            });
            return source;
        }

        BattlePokemon scaled = BattlePokemon.Companion.safeCopyOf(original);
        Pokemon battleCopy = scaled.getEffectedPokemon();
        lowerLevelAndScaleHealth(battleCopy, levelCap);

        scaled.getPostBattlePokemonOperations().addAll(source.getPostBattlePokemonOperations());
        scaled.getPostBattlePokemonOperations().add(ignored -> {
            copyPersistentBattleState(original, battleCopy);
            return kotlin.Unit.INSTANCE;
        });
        scaled.getPostBattleEntityOperations().addAll(source.getPostBattleEntityOperations());
        return scaled;
    }

    /**
     * Cobblemon awards battle EVs to the affected battle Pokemon. A level-capped
     * Pokemon fights as a clone, so mirror the actual awarded amount into the
     * stored Pokemon immediately instead of relying only on battle-end cleanup.
     */
    private static void persistBattleCloneEvGain(EvGainedEvent.Post event) {
        if (event.getAmount() <= 0 || !(event.getSource() instanceof BattleEvSource source)) {
            return;
        }

        Pokemon affected = event.getPokemon();
        for (var actor : source.getBattle().getActors()) {
            if (!(actor instanceof PlayerBattleActor)) {
                continue;
            }
            for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                if (battlePokemon.getEffectedPokemon() != affected) {
                    continue;
                }
                Pokemon original = battlePokemon.getOriginalPokemon();
                if (original != affected) {
                    original.getEvs().add(event.getStat(), event.getAmount());
                }
                return;
            }
        }
    }

    private static void lowerLevelAndScaleHealth(Pokemon pokemon, int levelCap) {
        int previousHealth = pokemon.getCurrentHealth();
        int previousMaximum = pokemon.getMaxHealth();
        pokemon.setLevel(levelCap);
        pokemon.setCurrentHealth(scaledHealth(
            previousHealth, previousMaximum, pokemon.getMaxHealth()
        ));
    }

    private static void copyPersistentBattleState(Pokemon original, Pokemon battleCopy) {
        original.getMoveSet().copyFrom(battleCopy.getMoveSet());
        original.setStatus(battleCopy.getStatus());
        original.setHeldItem$common(battleCopy.getHeldItem$common().copy());
        copyEvsInto(original.getEvs(), battleCopy.getEvs());
        int resultingHealth = scaledHealth(
            battleCopy.getCurrentHealth(), battleCopy.getMaxHealth(), original.getMaxHealth()
        );
        original.setCurrentHealth(resultingHealth);
    }

    static void copyEvsInto(EVs target, EVs source) {
        for (var entry : source) target.set(entry.getKey(), entry.getValue());
    }

    static int scaledHealth(int health, int sourceMaximum, int targetMaximum) {
        if (health <= 0 || targetMaximum <= 0) return 0;
        if (sourceMaximum <= 0) return Math.min(health, targetMaximum);
        long numerator = (long) health * targetMaximum;
        int scaled = (int) Math.ceil((double) numerator / sourceMaximum);
        return Math.max(1, Math.min(targetMaximum, scaled));
    }
}
