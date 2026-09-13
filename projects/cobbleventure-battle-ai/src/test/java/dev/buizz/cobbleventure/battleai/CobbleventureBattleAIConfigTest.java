package dev.buizz.cobbleventure.battleai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CobbleventureBattleAIConfigTest {
    @ParameterizedTest
    @ValueSource(strings = {"expert_winrate", "expert_search", "cheater"})
    void fallbackSelectionMarginRemainsPositive(String difficulty) {
        assertTrue(CobbleventureBattleAIConfig.selectMargin(difficulty) > 0.0);
    }
}
