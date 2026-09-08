package dev.buizz.cobbleventure.experience;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.OriginalTrainerType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;

/** Owns all experience timing and capture rewards previously supplied by sidemods. */
public final class ExperienceRewards {
    private static final SidemodExperienceSource CAPTURE_SOURCE =
        new SidemodExperienceSource(CobbleventureExperience.MOD_ID);

    private ExperienceRewards() {}

    public static void register() {
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, ExperienceRewards::onFainted);
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, ExperienceRewards::onCaptured);
    }

    private static void onFainted(BattleFaintedEvent event) {
        BattleActor defeatedActor = actorContaining(event.getBattle(), event.getKilled());
        if (defeatedActor == null) return;
        awardBattleExperience(defeatedActor, event.getKilled());
    }

    private static void onCaptured(PokemonCapturedEvent event) {
        PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(event.getPlayer());
        BattlePokemon captured = battle == null ? null : findPokemon(battle, event.getPokemon());
        if (captured != null) {
            BattleActor capturedActor = actorContaining(battle, captured);
            if (capturedActor != null) awardBattleExperience(capturedActor, captured);
            return;
        }
        awardOutOfBattleCapture(event.getPlayer(), event.getPokemon());
    }

    private static void awardBattleExperience(BattleActor defeatedActor, BattlePokemon defeated) {
        double shareMultiplier = Cobblemon.INSTANCE.getConfig().getExperienceShareMultiplier();
        PokemonBattle battle = defeatedActor.getSide().getBattle();
        if (battle.isPvP() && !Cobblemon.INSTANCE.getConfig().getAllowExperienceFromPvP()) return;
        for (BattleActor actor : defeatedActor.getSide().getOppositeSide().getActors()) {
            if (!(actor instanceof PlayerBattleActor playerActor)) continue;
            for (BattlePokemon recipient : actor.getPokemonList()) {
                boolean participated = recipient.getFacedOpponents().contains(defeated);
                boolean hasShare = recipient.getEffectedPokemon().heldItemNoCopy$common()
                    .is(CobblemonItemTags.EXPERIENCE_SHARE);
                double multiplier = ExperienceEligibility.multiplier(
                    recipient.getHealth() > 0, participated, hasShare, shareMultiplier
                );
                if (multiplier <= 0.0D) continue;
                int experience = Cobblemon.INSTANCE.getExperienceCalculator().calculate(
                    recipient, defeated, multiplier
                );
                if (experience > 0) awardStoredPokemon(playerActor, recipient, experience);
            }
        }
    }

    /**
     * Level-capped battles use an affected copy. Cobblemon deliberately ignores
     * experience for such copies, so apply the calculated reward to the stored
     * original while retaining the battle participant list in the source.
     */
    private static void awardStoredPokemon(
        PlayerBattleActor actor, BattlePokemon recipient, int experience
    ) {
        BattleExperienceSource source = new BattleExperienceSource(
            actor.getBattle(), new ArrayList<>(recipient.getFacedOpponents())
        );
        ServerPlayer player = PlayerExtensionsKt.getPlayer(actor.getUuid());
        Pokemon stored = recipient.getOriginalPokemon();
        if (player == null) stored.addExperience(source, experience);
        else stored.addExperienceWithPlayer(player, source, experience);
        if (recipient.getEffectedPokemon() == stored) recipient.sendUpdate();
    }

    private static void awardOutOfBattleCapture(ServerPlayer player, Pokemon captured) {
        double shareMultiplier = Cobblemon.INSTANCE.getConfig().getExperienceShareMultiplier();
        boolean leadFound = false;
        for (Pokemon recipient : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (recipient.getUuid().equals(captured.getUuid()) || recipient.getCurrentHealth() <= 0) continue;
            boolean participated = !leadFound;
            if (participated) leadFound = true;
            boolean hasShare = recipient.heldItemNoCopy$common().is(CobblemonItemTags.EXPERIENCE_SHARE);
            double multiplier = ExperienceEligibility.multiplier(
                true, participated, hasShare, shareMultiplier
            );
            if (multiplier <= 0.0D) continue;
            int experience = captureExperience(player, recipient, captured, multiplier);
            if (experience > 0) recipient.addExperienceWithPlayer(player, CAPTURE_SOURCE, experience);
        }
    }

    private static int captureExperience(
        ServerPlayer player, Pokemon recipient, Pokemon captured, double multiplier
    ) {
        double traded = recipient.getOriginalTrainerType() == OriginalTrainerType.PLAYER
            && recipient.getOriginalTrainer().equals(player.getUUID().toString()) ? 1.0D : 1.5D;
        double luckyEgg = recipient.heldItemNoCopy$common().is(CobblemonItemTags.LUCKY_EGG)
            ? Cobblemon.INSTANCE.getConfig().getLuckyEggMultiplier() : 1.0D;
        double evolution = hasReadyLevelEvolution(recipient) ? 1.2D : 1.0D;
        double friendship = recipient.getFriendship() >= 220 ? 1.2D : 1.0D;
        return ExperienceMath.captureExperience(
            captured.getForm().getBaseExperienceYield(), captured.getLevel(), recipient.getLevel(),
            multiplier, traded, luckyEgg, evolution, friendship,
            Cobblemon.INSTANCE.getConfig().getExperienceMultiplier()
        );
    }

    private static boolean hasReadyLevelEvolution(Pokemon pokemon) {
        for (var candidate : pokemon.getEvolutionProxy().server()) {
            if (!(candidate instanceof Evolution evolution)) continue;
            boolean hasLevelRequirement = false;
            boolean ready = true;
            for (var requirement : evolution.getRequirements()) {
                if (requirement instanceof LevelRequirement) hasLevelRequirement = true;
                if (!requirement.check(pokemon)) ready = false;
            }
            if (hasLevelRequirement && ready) return true;
        }
        return false;
    }

    private static BattleActor actorContaining(PokemonBattle battle, BattlePokemon pokemon) {
        for (BattleActor actor : battle.getActors()) {
            if (actor.getPokemonList().contains(pokemon)) return actor;
        }
        return null;
    }

    private static BattlePokemon findPokemon(PokemonBattle battle, Pokemon pokemon) {
        for (BattleActor actor : battle.getActors()) {
            for (BattlePokemon candidate : actor.getPokemonList()) {
                if (candidate.getOriginalPokemon().getUuid().equals(pokemon.getUuid())) return candidate;
            }
        }
        return null;
    }
}
