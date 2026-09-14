package dev.buizz.cobbleventure.battleai;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.RCTApi;
import com.gitlab.srcmc.rctapi.api.errors.RCTErrors;
import com.gitlab.srcmc.rctapi.api.models.TrainerModel;
import com.gitlab.srcmc.rctapi.api.models.converter.PokemonModelConverter;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerPlayer;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.level.ServerPlayer;

/** Supplies the web battle lab's official left entry without changing the owned party. */
final class AIBattleTestPlayerPreset extends TrainerPlayer {
    static final String TEAM_RESOURCE =
            "/data/cobbleventure_battle_ai/test-presets/dbingsu-server-party.json";

    private final Pokemon[] team;

    private AIBattleTestPlayerPreset(ServerPlayer player, Pokemon[] team) {
        super(player);
        this.team = team;
    }

    static AIBattleTestPlayerPreset load(RCTApi api, ServerPlayer player) {
        TrainerModel model;
        try (var stream = AIBattleTestPlayerPreset.class.getResourceAsStream(TEAM_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("웹 기준 왼쪽 엔트리 리소스를 찾을 수 없습니다.");
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                model = api.configureGsonBuilder(new GsonBuilder()).create()
                        .fromJson(reader, TrainerModel.class);
            }
        } catch (IOException error) {
            throw new IllegalStateException("웹 기준 왼쪽 엔트리를 읽지 못했습니다.", error);
        }

        if (model == null || model.getTeam() == null || model.getTeam().size() != 6) {
            throw new IllegalStateException("웹 기준 왼쪽 엔트리는 포켓몬 6마리여야 합니다.");
        }

        var errors = RCTErrors.create();
        var converter = new PokemonModelConverter();
        Pokemon[] team = model.getTeam().stream()
                .map(entry -> converter.toTarget(entry, errors))
                .toArray(Pokemon[]::new);
        errors.check();
        for (Pokemon pokemon : team) {
            pokemon.setOriginalTrainer(player.getUUID());
            pokemon.heal();
        }
        return new AIBattleTestPlayerPreset(player, team);
    }

    @Override
    public Pokemon[] getTeam() {
        return team;
    }
}
