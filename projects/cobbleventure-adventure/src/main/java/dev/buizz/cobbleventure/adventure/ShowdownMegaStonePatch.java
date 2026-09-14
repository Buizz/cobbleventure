package dev.buizz.cobbleventure.adventure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Mega Showdown's map-based stones must also accept Showdown's native string-based stones. */
public final class ShowdownMegaStonePatch {
    static final String TARGET = "const megaEvolution = stone[species.name];";

    private ShowdownMegaStonePatch() {}

    public static String patch(String source) {
        try (var stream = ShowdownMegaStonePatch.class.getResourceAsStream("/showdown-mega-stone.js")) {
            if (stream == null) throw new IllegalStateException("Missing Mega Stone compatibility script");
            String replacement = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (source.contains(replacement)) return source;
            if (!source.contains(TARGET)) {
                throw new IllegalStateException("Unsupported Mega Showdown canMegaEvo implementation");
            }
            return source.replace(TARGET, replacement);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read Mega Stone compatibility script", error);
        }
    }

    public static void apply() {
        Path root = Path.of("showdown").toAbsolutePath().normalize();
        Path target = root.resolve("sim/battle-actions.js");
        try {
            if (!target.toRealPath().startsWith(root.toRealPath())) {
                throw new IllegalStateException("Mega Stone patch target escaped Showdown cache");
            }
            String original = Files.readString(target);
            String updated = patch(original);
            if (!updated.equals(original)) {
                Files.writeString(target, updated);
                com.mojang.logging.LogUtils.getLogger().info("Applied Mega Stone string/map compatibility to Showdown");
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot apply Mega Stone compatibility", error);
        }
    }
}
