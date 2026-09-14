package dev.buizz.cobbleventure.battleai;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.RCTApi;
import com.gitlab.srcmc.rctapi.api.errors.RCTErrors;
import com.gitlab.srcmc.rctapi.api.models.TrainerModel;
import com.gitlab.srcmc.rctapi.api.models.converter.PokemonModelConverter;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Creates the web battle lab's official player entry at a requested test level. */
final class AIBattleTestPlayerPreset {
    static final String TEAM_RESOURCE =
            "/data/cobbleventure_battle_ai/test-presets/dbingsu-server-party.json";

    private AIBattleTestPlayerPreset() {}

    static List<Pokemon> load(RCTApi api, ServerPlayer player, int level) {
        if (level < 1 || level > 100) {
            throw new IllegalArgumentException("레벨은 1~100이어야 합니다.");
        }
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
        List<Pokemon> team = model.getTeam().stream()
                .map(entry -> converter.toTarget(entry, errors))
                .toList();
        errors.check();
        for (Pokemon pokemon : team) {
            pokemon.setOriginalTrainer(player.getUUID());
            pokemon.setLevel(level);
            pokemon.heal();
        }
        return team;
    }
}
