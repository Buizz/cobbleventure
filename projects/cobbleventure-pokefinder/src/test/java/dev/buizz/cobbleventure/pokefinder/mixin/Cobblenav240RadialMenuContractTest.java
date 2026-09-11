package dev.buizz.cobbleventure.pokefinder.mixin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.metacontent.cobblenav.client.gui.widget.button.PokenavButton;
import com.metacontent.cobblenav.client.gui.widget.radialmenu.OpenedRadialMenu;
import com.metacontent.cobblenav.client.gui.widget.radialmenu.RadialPopupMenu;
import com.metacontent.cobblenav.os.PokenavOS;
import org.junit.jupiter.api.Test;

class Cobblenav240RadialMenuContractTest {
    @Test
    void contactsButtonCallbackTargetExists() {
        assertDoesNotThrow(() -> OpenedRadialMenu.class.getDeclaredMethod(
            "buttons$lambda$2",
            RadialPopupMenu.class,
            PokenavOS.class,
            PokenavButton.class
        ));
    }
}
