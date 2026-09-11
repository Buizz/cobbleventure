package dev.buizz.cobbleventure.playermenu;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** A fully trained team using species implemented in the development Cobblemon release. */
final class CheatTeam {
    private CheatTeam() {}

    private record Member(String species, String nature, String ability, String item,
                          Stats primary, Stats secondary, Stats remainder, List<String> moves) {
        Pokemon create(ServerPlayer player) {
            if (PokemonSpecies.getByName(species) == null) {
                throw new IllegalStateException("포켓몬을 찾을 수 없습니다: " + species);
            }
            var properties = new PokemonProperties();
            properties.setSpecies(species);
            properties.setLevel(100);
            properties.setNature(nature);
            properties.setAbility(ability);
            properties.setHeldItem("cobblemon:" + item);
            properties.setMoves(moves);
            properties.setFriendship(255);
            Pokemon pokemon = properties.create(player);
            for (Stats stat : List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE,
                Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)) pokemon.setIV(stat, 31);
            pokemon.setEV(primary, 252);
            pokemon.setEV(secondary, 252);
            pokemon.setEV(remainder, 4);
            pokemon.heal();
            if (pokemon.getMoveSet().getMoveTemplates().size() != 4 || pokemon.heldItem().isEmpty()) {
                throw new IllegalStateException("기술 또는 지닌도구를 설정하지 못했습니다: " + species);
            }
            return pokemon;
        }
    }

    private static final List<Member> TEAM = List.of(
        new Member("mewtwo", "timid", "pressure", "life_orb", Stats.SPECIAL_ATTACK, Stats.SPEED, Stats.HP,
            List.of("psystrike", "aurasphere", "icebeam", "recover")),
        new Member("rayquaza", "adamant", "airlock", "life_orb", Stats.ATTACK, Stats.SPEED, Stats.HP,
            List.of("dragonascent", "outrage", "extremespeed", "dragondance")),
        new Member("xerneas", "modest", "fairyaura", "power_herb", Stats.SPECIAL_ATTACK, Stats.SPEED, Stats.HP,
            List.of("geomancy", "moonblast", "thunder", "focusblast")),
        new Member("lugia", "bold", "multiscale", "leftovers", Stats.HP, Stats.DEFENCE, Stats.SPECIAL_DEFENCE,
            List.of("aeroblast", "psychic", "icebeam", "roost")),
        new Member("hooh", "adamant", "regenerator", "heavy_duty_boots", Stats.ATTACK, Stats.HP, Stats.SPECIAL_DEFENCE,
            List.of("sacredfire", "bravebird", "earthquake", "recover")),
        new Member("garchomp", "jolly", "roughskin", "life_orb", Stats.ATTACK, Stats.SPEED, Stats.HP,
            List.of("earthquake", "dragonclaw", "stoneedge", "swordsdance"))
    );

    static int grant(CommandSourceStack source, Collection<ServerPlayer> players) {
        int changed = 0;
        for (ServerPlayer player : players) {
            if (PlayerExtensionsKt.isInBattle(player)) {
                source.sendFailure(Component.literal(player.getScoreboardName() + ": 전투 중에는 파티를 교체할 수 없습니다."));
                continue;
            }
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
            try {
                // Finish creating all six before touching any owned Pokemon.
                List<Pokemon> team = TEAM.stream().map(member -> member.create(player)).toList();
                PartyReplacement.replace(new PartyReplacement.Storage<Pokemon>() {
                    public Pokemon get(int slot) { return party.get(slot); }
                    public int freePcSlots() { return pc.getBoxes().stream().mapToInt(box -> box.getUnoccupiedSlots()).sum(); }
                    public void removeFromParty(Pokemon pokemon) { party.remove(pokemon); }
                    public boolean addToPc(Pokemon pokemon) { pokemon.recall(); return pc.add(pokemon); }
                    public void removeFromPc(Pokemon pokemon) { pc.remove(pokemon); }
                    public void setParty(int slot, Pokemon pokemon) { party.set(slot, pokemon); }
                }, team);
            } catch (RuntimeException error) {
                source.sendFailure(Component.literal(player.getScoreboardName() + ": " + error.getMessage()));
                continue;
            }
            ProgressionNetwork.maximizeLevelCap(player);
            player.sendSystemMessage(Component.literal(
                "[Cobbleventure] 100레벨·6V·노력치 훈련 완료 포켓몬 6마리 지급! 기존 파티는 PC로 이동했습니다."));
            changed++;
        }
        int count = changed;
        source.sendSuccess(() -> Component.literal("[Cobbleventure] 최강 파티 지급 완료 · " + count + "명"), true);
        return changed;
    }
}
