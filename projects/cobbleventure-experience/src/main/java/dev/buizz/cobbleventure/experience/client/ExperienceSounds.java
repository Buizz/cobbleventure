package dev.buizz.cobbleventure.experience.client;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.client.CobblemonClient;
import dev.buizz.cobbleventure.experience.CobbleventureExperience;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** One shared audio channel for visible XP bars, including double battles. */
@EventBusSubscriber(modid = CobbleventureExperience.MOD_ID, value = Dist.CLIENT)
public final class ExperienceSounds {
    private static SimpleSoundInstance filling;
    private static SimpleSoundInstance levelUp;
    private static long visibleUntil;
    private static long lastLevelUp;

    private ExperienceSounds() {}

    public static void filling(long now) {
        if (Minecraft.getInstance().isPaused()) return;
        visibleUntil = now + 150_000_000L;
        if (filling != null || now - lastLevelUp < 650_000_000L) return;
        // The native fill asset is deliberately quiet; retain its original sound and pitch.
        filling = SimpleSoundInstance.forUI(CobblemonSounds.LEVELUP_START, 1.0F);
        Minecraft.getInstance().getSoundManager().play(filling);
    }

    public static void levelUp(long now) {
        stopFilling();
        // Simultaneous party level-ups should not stack the same jingle.
        if (now - lastLevelUp < 650_000_000L) return;
        lastLevelUp = now;
        var manager = Minecraft.getInstance().getSoundManager();
        if (levelUp != null) manager.stop(levelUp);
        levelUp = SimpleSoundInstance.forUI(CobblemonSounds.LEVELUP, 1.0F);
        manager.play(levelUp);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || CobblemonClient.INSTANCE.getBattle() == null) {
            clear();
        } else if (minecraft.isPaused() || System.nanoTime() > visibleUntil) {
            stopFilling();
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void stopFilling() {
        if (filling != null) Minecraft.getInstance().getSoundManager().stop(filling);
        filling = null;
    }

    private static void clear() {
        stopFilling();
        if (levelUp != null) Minecraft.getInstance().getSoundManager().stop(levelUp);
        levelUp = null;
        visibleUntil = 0;
        lastLevelUp = 0;
    }
}
