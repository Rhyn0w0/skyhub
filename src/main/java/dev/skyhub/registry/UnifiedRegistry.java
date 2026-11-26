package dev.skyhub.registry;

import dev.skyhub.SkyHubClient;
import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.adapter.ModAdapter;
import dev.skyhub.model.HudWidget;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry that runs all framework, mod-specific, and fallback adapters
 * inside strict crash-proof isolation boundaries and serves normalized entries
 * to the Unified Search Hub and Unified HUD Canvas.
 */
public final class UnifiedRegistry {
    private static final UnifiedRegistry INSTANCE = new UnifiedRegistry();

    private final List<ModAdapter> adapters = new ArrayList<>();
    private List<ModEntry> mods = List.of();
    private Map<String, ModEntry> modsById = Map.of();
    private List<SettingEntry> settings = List.of();
    private List<HudWidget> hudWidgets = List.of();
    private boolean initialized = false;

    private UnifiedRegistry() {}

    public static UnifiedRegistry getInstance() {
        return INSTANCE;
    }

    public void registerAdapter(ModAdapter adapter) {
        adapters.add(adapter);
        adapters.sort(Comparator.comparingInt(ModAdapter::priority));
    }

    public void clearAdapters() {
        adapters.clear();
    }

    public void ensureInitialized() {
        if (!initialized) {
            refresh();
        }
    }

    public void refresh() {
        long start = System.currentTimeMillis();
        AdapterContext ctx = new AdapterContext();

        for (ModAdapter adapter : adapters) {
            try {
                adapter.collect(ctx);
            } catch (Throwable t) {
                SkyHubClient.LOGGER.warn("[SkyHub] Adapter '{}' threw unexpectedly and was isolated: {}",
                    adapter.name(), t.toString());
            }
        }

        // Compute per-mod counts
        Map<String, Integer> settingCounts = new HashMap<>();
        for (SettingEntry s : ctx.settings()) {
            settingCounts.merge(s.modId(), 1, Integer::sum);
        }
        Map<String, Integer> hudCounts = new HashMap<>();
        for (HudWidget w : ctx.hudWidgets()) {
            hudCounts.merge(w.modId(), 1, Integer::sum);
        }

        List<ModEntry> modList = new ArrayList<>(ctx.mods().values());
        for (ModEntry m : modList) {
            m.setSettingCount(settingCounts.getOrDefault(m.modId(), 0));
            m.setHudWidgetCount(hudCounts.getOrDefault(m.modId(), 0));
        }

        // Sort: SkyBlock mods first (by curated priority), then mods with indexed settings, then alphabetical
        modList.sort(Comparator
            .comparing((ModEntry m) -> !m.isSkyBlockMod())
            .thenComparingInt(ModEntry::sortPriority)
            .thenComparing((ModEntry m) -> m.settingCount() == 0 && m.hudWidgetCount() == 0)
            .thenComparing(ModEntry::displayName, String.CASE_INSENSITIVE_ORDER));

        Map<String, ModEntry> byId = new HashMap<>();
        for (ModEntry m : modList) {
            byId.put(m.modId(), m);
        }

        this.mods = List.copyOf(modList);
        this.modsById = Map.copyOf(byId);
        this.settings = List.copyOf(ctx.settings());
        this.hudWidgets = List.copyOf(ctx.hudWidgets());
        this.initialized = true;

        long elapsed = System.currentTimeMillis() - start;
        SkyHubClient.LOGGER.info(
            "[SkyHub] Indexed {} settings and {} HUD widgets across {} mods in {} ms",
            this.settings.size(), this.hudWidgets.size(), this.mods.size(), elapsed
        );
    }

    public List<ModEntry> mods() {
        ensureInitialized();
        return mods;
    }

    public ModEntry mod(String modId) {
        ensureInitialized();
        return modsById.get(modId);
    }

    public List<SettingEntry> settings() {
        ensureInitialized();
        return settings;
    }

    public List<HudWidget> hudWidgets() {
        ensureInitialized();
        return hudWidgets;
    }

    public List<SettingEntry> search(FuzzySearchIndex.FilterOptions options) {
        ensureInitialized();
        return FuzzySearchIndex.filter(settings, options);
    }
}
