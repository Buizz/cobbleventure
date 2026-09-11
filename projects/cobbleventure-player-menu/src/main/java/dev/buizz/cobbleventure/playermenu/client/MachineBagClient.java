package dev.buizz.cobbleventure.playermenu.client;

import dev.buizz.cobbleventure.playermenu.BagNetwork;
import dev.buizz.cobbleventure.playermenu.MachineBagNetwork;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-only routing for bag-integrated machine screens. */
public final class MachineBagClient {
    private MachineBagClient() {}

    public static void open(MachineBagNetwork.OpenPayload payload) {
        BagNetwork.requestSnapshot();
        Minecraft.getInstance().setScreen(new TmWorkshopScreen(
            payload.token(), payload.machineId(), payload.pos(), payload.entries()
        ));
    }

    public static void update(MachineBagNetwork.UpdatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TmWorkshopScreen screen && screen.matches(payload.token())) {
            screen.update(payload);
        }
    }

    public static void expire(UUID token, String reasonKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof TmWorkshopScreen screen) || !screen.matches(token)) return;
        screen.expire();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(reasonKey), true);
        }
    }
}
