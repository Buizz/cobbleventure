package dev.buizz.cobbleventure.playermenu.mixin;

import com.cobblemon.mod.common.client.gui.summary.Summary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Routes a growth badge directly to the matching native Cobblemon summary control. */
@Mixin(Summary.class)
public interface SummaryScreenAccessor {
    @Accessor("MOVES")
    static int cobbleventure$movesTab() {
        throw new AssertionError();
    }

    @Invoker("displayMainScreen")
    void cobbleventure$displayMainScreen(int index);
}
