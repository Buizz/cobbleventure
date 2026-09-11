package dev.buizz.cobbleventure.content;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Startup content shared by world generation and client catalogs. Never reads engine JARs. */
public final class ContentFiles {
    private ContentFiles() {}

    public static Path root() {
        return Path.of(System.getProperty("cobbleventure.contentDirectory",
            "config/cobbleventure/content")).toAbsolutePath().normalize();
    }

    private static Path resolve(String resource) {
        String relative = resource.startsWith("/") ? resource.substring(1) : resource;
        Path root = root();
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid content path: " + resource);
        return path;
    }

    public static boolean exists(String resource) {
        return Files.isRegularFile(resolve(resource));
    }

    public static InputStream open(String resource) {
        Path path = resolve(resource);
        if (!Files.isRegularFile(path)) return null;
        try {
            return Files.newInputStream(path);
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot read content: " + path, error);
        }
    }
}
