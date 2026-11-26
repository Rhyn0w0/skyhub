package dev.skyhub.adapter.fallback;

import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.adapter.ModAdapter;
import dev.skyhub.model.AdapterHealth;
import dev.skyhub.model.ModCatalog;
import dev.skyhub.model.ModEntry;
import dev.skyhub.util.Reflect;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Tier-3 Universal Fallback Adapter.
 * Discovers all ModMenu config screen factories across the installed modpack
 * (using reflection so ModMenu itself stays optional) and guarantees every
 * SkyBlock Enhanced mod has a one-click native menu opener even if its deep
 * adapter breaks on a future update.
 */
public final class FallbackModMenuAdapter implements ModAdapter {
    private static final Set<String> IGNORED_IDS = Set.of(
        "minecraft", "java", "fabricloader", "skyhub", "mixinextras",
        "fabric-language-kotlin", "yet_another_config_lib_v3", "cloth-config",
        "cloth-basic-math", "owo", "placeholder-api", "uilib", "searchables"
    );

    @Override
    public String name() {
        return "ModMenu Fallback";
    }

    @Override
    public int priority() {
        return 95;
    }

    @Override
    public void collect(AdapterContext ctx) {
        Map<String, Function<Screen, Screen>> modMenuFactories = discoverModMenuFactories();

        // 1. Register every mod that has a ModMenu screen OR is in our SkyBlock Enhanced catalog
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            String modId = container.getMetadata().getId();
            if (shouldIgnore(modId)) continue;

            boolean hasModMenu = modMenuFactories.containsKey(modId);
            boolean isSkyBlock = ModCatalog.isSkyBlockMod(modId);
            boolean alreadyTracked = ctx.mods().containsKey(modId);

            if (!hasModMenu && !isSkyBlock && !alreadyTracked) {
                continue;
            }

            ModEntry mod = ctx.getOrCreateMod(modId);
            if (hasModMenu) {
                mod.setNativeMenuOpenerIfAbsent(modMenuFactories.get(modId));
            }
            if (mod.health() == AdapterHealth.FALLBACK_ONLY && mod.adapterName().equals("Fallback")) {
                String detail = mod.hasNativeMenu()
                    ? "One-click native config screen available"
                    : "Loaded in modpack (no config screen registered)";
                mod.setAdapterStatus("ModMenu / Native", AdapterHealth.FALLBACK_ONLY, detail);
            }
        }
    }

    private boolean shouldIgnore(String modId) {
        if (IGNORED_IDS.contains(modId)) return true;
        if (modId.startsWith("fabric-") || modId.startsWith("org_") || modId.startsWith("com_")
            || modId.startsWith("net_") || modId.startsWith("io_") || modId.startsWith("me_")) {
            return true;
        }
        return false;
    }

    private Map<String, Function<Screen, Screen>> discoverModMenuFactories() {
        Map<String, Function<Screen, Screen>> result = new HashMap<>();
        try {
            var containers = FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class);
            for (EntrypointContainer<Object> container : containers) {
                String ownerId = container.getProvider().getMetadata().getId();
                if ("skyhub".equals(ownerId)) continue;
                try {
                    Object api = container.getEntrypoint();
                    if (api == null) continue;

                    // Primary config screen factory for this mod
                    Object factory = Reflect.invoke(api, "getModConfigScreenFactory");
                    if (factory != null) {
                        result.putIfAbsent(ownerId, wrapConfigFactory(factory));
                    }

                    // Provided factories for other mods
                    Object providedObj = Reflect.invoke(api, "getProvidedConfigScreenFactories");
                    if (providedObj instanceof Map<?, ?> provided) {
                        for (Map.Entry<?, ?> pe : provided.entrySet()) {
                            if (pe.getKey() instanceof String targetId && pe.getValue() != null && !"skyhub".equals(targetId)) {
                                result.putIfAbsent(targetId, wrapConfigFactory(pe.getValue()));
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // Single broken ModMenu entrypoint never breaks others
                }
            }
        } catch (Throwable ignored) {
            // ModMenu not present or entrypoint error — cleanly ignored
        }
        return result;
    }

    private Function<Screen, Screen> wrapConfigFactory(Object configScreenFactory) {
        return parent -> {
            try {
                Object screen = Reflect.invoke(configScreenFactory, "create", parent);
                return screen instanceof Screen s ? s : null;
            } catch (Throwable ignored) {
                return null;
            }
        };
    }
}
