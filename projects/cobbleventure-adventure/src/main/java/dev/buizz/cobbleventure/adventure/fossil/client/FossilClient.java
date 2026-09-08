package dev.buizz.cobbleventure.adventure.fossil.client;

import dev.buizz.cobbleventure.adventure.fossil.FossilNetwork;
import net.minecraft.client.Minecraft;

public final class FossilClient {
    private FossilClient() {}
    public static void open(FossilNetwork.View view) {
        var minecraft = Minecraft.getInstance();
        if (view.close()) {
            if (minecraft.screen instanceof FossilScreen) minecraft.setScreen(null);
            if (minecraft.player != null) minecraft.player.displayClientMessage(view.feedback(), false);
        } else if (minecraft.screen instanceof FossilScreen screen && screen.npc().equals(view.npc())) {
            screen.apply(view);
        } else minecraft.setScreen(new FossilScreen(view));
    }
}
