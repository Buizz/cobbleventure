package dev.buizz.cobbleventure.battleai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CobbleventureBattleAIConfigTest {
    @ParameterizedTest
    @ValueSource(strings = {"expert_winrate", "expert_search", "cheater"})
    void fallbackSelectionMarginRemainsPositive(String difficulty) {
        assertTrue(CobbleventureBattleAIConfig.selectMargin(difficulty) > 0.0);
    }

    @Test
    void independentEngineRoutesExpertDifficultiesToTheirExpectedAlgorithms() {
        assertTrue(CobbleventureBattleAIConfig.usesIndependentDecisionEngine("expert"));
        assertTrue(CobbleventureBattleAIConfig.usesIndependentDecisionEngine("expert_winrate"));
        assertTrue(CobbleventureBattleAIConfig.usesIndependentDecisionEngine("expert_search"));
        assertFalse(CobbleventureBattleAIConfig.usesIndependentDecisionEngine("standard"));

        assertEquals("heuristic", CobbleventureBattleAIConfig.decisionAlgorithm("expert"));
        assertEquals("win_rate", CobbleventureBattleAIConfig.decisionAlgorithm("expert_winrate"));
        assertEquals("two_turn", CobbleventureBattleAIConfig.decisionAlgorithm("expert_search"));
    }
}
