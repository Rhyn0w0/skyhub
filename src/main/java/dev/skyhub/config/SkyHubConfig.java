package dev.skyhub.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.skyhub.SkyHubClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight persisted preferences for SkyHub itself (stored in config/skyhub.json).
 * Never duplicates other mods' settings — only stores SkyHub's own UI preferences.
 */
public final class SkyHubConfig {
    public enum GroupMode {
        BY_TYPE("Feature Type"),
        BY_MOD("Mod");

        private final String label;

        GroupMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public GroupMode next() {
            return this == BY_TYPE ? BY_MOD : BY_TYPE;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SkyHubConfig instance = new SkyHubConfig();

    private boolean onlySkyBlockMods = true;
    private GroupMode groupMode = GroupMode.BY_TYPE;
    private boolean hudSnappingDefault = true;

    public static SkyHubConfig get() {
        return instance;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyhub.json");
    }

    public static void load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            instance = new SkyHubConfig();
            return;
        }
        try {
            String json = Files.readString(path);
            SkyHubConfig loaded = GSON.fromJson(json, SkyHubConfig.class);
            if (loaded != null) {
                if (loaded.groupMode == null) {
                    loaded.groupMode = GroupMode.BY_TYPE;
                }
                instance = loaded;
            }
        } catch (Throwable t) {
            SkyHubClient.LOGGER.warn("[SkyHub] Could not read skyhub.json, using defaults: {}", t.toString());
            instance = new SkyHubConfig();
        }
    }

    public static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance));
        } catch (Throwable t) {
            SkyHubClient.LOGGER.warn("[SkyHub] Could not save skyhub.json: {}", t.toString());
        }
    }

    public boolean onlySkyBlockMods() {
        return onlySkyBlockMods;
    }

    public void setOnlySkyBlockMods(boolean onlySkyBlockMods) {
        this.onlySkyBlockMods = onlySkyBlockMods;
    }

    public GroupMode groupMode() {
        return groupMode == null ? GroupMode.BY_TYPE : groupMode;
    }

    public void setGroupMode(GroupMode groupMode) {
        this.groupMode = groupMode != null ? groupMode : GroupMode.BY_TYPE;
    }

    public boolean hudSnappingDefault() {
        return hudSnappingDefault;
    }

    public void setHudSnappingDefault(boolean hudSnappingDefault) {
        this.hudSnappingDefault = hudSnappingDefault;
    }
}
