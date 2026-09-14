package dev.buizz.cobbleventure.battleai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class ForcedSwitchRecoveryTest {
    @Test
    void keepsValidForcedSwitchChoice() {
        String selected = ForcedSwitchRecovery.select(
                true, "rct-switch", value -> value.startsWith("rct"),
                () -> Stream.of("recovery-switch"));

        assertEquals("rct-switch", selected);
    }

    @Test
    void replacesInvalidPassWithFirstValidSwitch() {
        String selected = ForcedSwitchRecovery.select(
                true, "pass", value -> value.endsWith("switch"),
                () -> Stream.of("fainted", "recovery-switch", "other-switch"));

        assertEquals("recovery-switch", selected);
    }

    @Test
    void doesNotAlterOrdinaryTurnResponse() {
        String selected = ForcedSwitchRecovery.select(
                false, "pass", value -> false,
                () -> Stream.of("recovery-switch"));

        assertEquals("pass", selected);
    }

    @Test
    void preservesNullWhenNoValidRecoveryExists() {
        String selected = ForcedSwitchRecovery.select(
                true, null, value -> false,
                () -> Stream.of("fainted"));

        assertNull(selected);
    }
}
