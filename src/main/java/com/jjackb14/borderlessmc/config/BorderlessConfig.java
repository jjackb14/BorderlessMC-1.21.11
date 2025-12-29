package com.jjackb14.borderlessmc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple JSON configuration for BorderlessMC.
 *
 * <p>Stored at {@code .minecraft/config/borderlessmc.json}.</p>
 *
 * @author jjackb14
 */
public final class BorderlessConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "borderlessmc.json";

    /**
     * Whether borderless fullscreen behavior is enabled.
     */
    public boolean enabled = true;

    private static BorderlessConfig INSTANCE;

    private BorderlessConfig() {}

    public static BorderlessConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static void save() {
        if (INSTANCE == null) return;
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
        }
        catch (IOException ignored) {}
    }

    private static BorderlessConfig load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            BorderlessConfig cfg = new BorderlessConfig();
            INSTANCE = cfg;
            save();
            return cfg;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            BorderlessConfig cfg = GSON.fromJson(json, BorderlessConfig.class);
            return (cfg != null) ? cfg: new BorderlessConfig();
        }
        catch (IOException ignored) {
            return new BorderlessConfig();
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

}
