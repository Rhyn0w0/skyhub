package dev.skyhub.adapter;

import dev.skyhub.model.AdapterHealth;
import dev.skyhub.model.HudWidget;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Shared collector context passed to every ModAdapter during registry indexing.
 * Deduplicates entries by ID and tracks per-mod adapter health cleanly.
 */
public final class AdapterContext {
    private final Map<String, ModEntry> mods = new LinkedHashMap<>();
    private final Map<String, SettingEntry> settingsById = new LinkedHashMap<>();
    private final Map<String, HudWidget> widgetsById = new LinkedHashMap<>();
    private final Set<String> indexedSettingMods = new LinkedHashSet<>();
    private final Map<String, List<String>> adapterNotes = new HashMap<>();

    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public ModEntry getOrCreateMod(String modId) {
        return mods.computeIfAbsent(modId, id -> {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(id);
            if (container.isPresent()) {
                var meta = container.get().getMetadata();
                return new ModEntry(id, meta.getName(), meta.getVersion().getFriendlyString());
            }
            return new ModEntry(id, SettingEntry.formatIdentifier(id), "");
        });
    }

    public boolean hasIndexedSettingsFor(String modId) {
        return indexedSettingMods.contains(modId);
    }

    public void addSetting(SettingEntry entry) {
        if (entry == null) return;
        getOrCreateMod(entry.modId());
        settingsById.putIfAbsent(entry.id(), entry);
        indexedSettingMods.add(entry.modId());
    }

    public void addHudWidget(HudWidget widget) {
        if (widget == null) return;
        getOrCreateMod(widget.modId());
        widgetsById.putIfAbsent(widget.id(), widget);
    }

    public void markHealthy(String modId, String adapterName, String detail) {
        ModEntry mod = getOrCreateMod(modId);
        if (mod.health() != AdapterHealth.HEALTHY) {
            mod.setAdapterStatus(adapterName, AdapterHealth.HEALTHY, detail);
        } else {
            String prev = mod.adapterName();
            if (!prev.contains(adapterName)) {
                mod.setAdapterStatus(prev + " + " + adapterName, AdapterHealth.HEALTHY, detail);
            }
        }
    }

    public void reportFailure(String modId, String adapterName, Throwable error) {
        String reason = error.getClass().getSimpleName() +
            (error.getMessage() != null ? ": " + error.getMessage() : "");
        adapterNotes.computeIfAbsent(modId, k -> new ArrayList<>()).add(adapterName + " failed (" + reason + ")");
        ModEntry mod = getOrCreateMod(modId);
        if (mod.health() == AdapterHealth.FALLBACK_ONLY) {
            mod.setAdapterStatus(adapterName + " (fallback)", AdapterHealth.FALLBACK_ONLY,
                "Adapter disabled safely: " + reason);
        } else {
            mod.setAdapterStatus(mod.adapterName(), AdapterHealth.DEGRADED,
                "Partial fallback: " + reason);
        }
    }

    public void registerNativeMenu(String modId, Function<Screen, Screen> opener) {
        if (opener == null) return;
        getOrCreateMod(modId).setNativeMenuOpenerIfAbsent(opener);
    }

    public void registerNativeHud(String modId, Runnable opener) {
        if (opener == null) return;
        getOrCreateMod(modId).setNativeHudOpenerIfAbsent(opener);
    }

    public Map<String, ModEntry> mods() {
        return mods;
    }

    public List<SettingEntry> settings() {
        return new ArrayList<>(settingsById.values());
    }

    public List<HudWidget> hudWidgets() {
        return new ArrayList<>(widgetsById.values());
    }
}
