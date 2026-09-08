package dev.buizz.cobbleventure.experience;

import dev.buizz.cobbleventure.experience.client.ExperienceOverlay;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(CobbleventureExperience.MOD_ID)
public final class CobbleventureExperience {
    public static final String MOD_ID = "cobbleventure_experience";

    public CobbleventureExperience(IEventBus modBus) {
        ExperienceNetwork.register(modBus);
        ExperienceRewards.register();
        if (FMLEnvironment.dist.isClient()) ExperienceOverlay.initialize();
    }
}
