package dev.buizz.cobbleventure.content;

import java.util.Optional;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Installs the same external bundle for server data and client assets. */
public final class ContentPacks {
    private ContentPacks() {}

    public static void register(AddPackFindersEvent event) {
        // Never advertise editable local content as a known/trusted vanilla pack.
        if (event.isTrusted()) return;
        event.addRepositorySource(consumer -> {
            try (var reader = Files.newBufferedReader(ContentFiles.root().resolve("content-manifest.json"))) {
                var manifest = JsonParser.parseReader(reader).getAsJsonObject();
                if (manifest.get("schema_version").getAsInt() != 1
                    || manifest.get("engine_contract").getAsInt() != 2) {
                    throw new IllegalStateException("Unsupported Cobbleventure content contract");
                }
            } catch (java.io.IOException | RuntimeException error) {
                throw new IllegalStateException("Missing or incompatible content bundle: " + ContentFiles.root(), error);
            }
            Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo("cobbleventure/content", Component.literal("Cobbleventure Content"),
                    PackSource.DEFAULT, Optional.empty()),
                new PathPackResources.PathResourcesSupplier(ContentFiles.root()),
                event.getPackType(), new PackSelectionConfig(true, Pack.Position.TOP, false)
            );
            if (pack == null) {
                throw new IllegalStateException("Cobbleventure content is missing or invalid: "
                    + ContentFiles.root() + ". Install the content bundle before starting Minecraft.");
            }
            consumer.accept(pack);
        });
    }
}
