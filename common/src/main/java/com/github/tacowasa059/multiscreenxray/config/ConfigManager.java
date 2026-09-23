package com.github.tacowasa059.multiscreenxray.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and atomically saves the shared client configuration. */
public final class ConfigManager {
    private static final System.Logger LOGGER = System.getLogger("MultiScreen X-ray");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static XrayConfig config = XrayConfig.defaults();
    private static Path file;
    private static long revision;

    private ConfigManager() {
    }

    public static synchronized XrayConfig load(Path configDirectory) {
        file = configDirectory.resolve("multiscreenxray.json");
        XrayConfig loaded = null;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                loaded = GSON.fromJson(reader, XrayConfig.class);
            } catch (RuntimeException | IOException exception) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Could not read MultiScreen X-ray config at " + file, exception);
            }
        }
        config = loaded == null ? XrayConfig.defaults() : loaded;
        config.normalize();
        revision++;
        if (loaded == null) save();
        return config;
    }

    public static synchronized XrayConfig get() {
        config.normalize();
        return config;
    }

    public static synchronized long revision() {
        return revision;
    }

    public static synchronized void changed() {
        config.normalize();
        revision++;
        save();
    }

    public static synchronized void save() {
        if (file == null) return;
        config.normalize();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Could not save MultiScreen X-ray config at " + file, exception);
        }
    }
}
