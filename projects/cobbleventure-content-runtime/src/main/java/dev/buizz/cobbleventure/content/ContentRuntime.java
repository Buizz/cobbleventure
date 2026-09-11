package dev.buizz.cobbleventure.content;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("cobbleventure_content_runtime")
public final class ContentRuntime {
    public ContentRuntime(IEventBus bus) {
        bus.addListener(ContentPacks::register);
    }
}
