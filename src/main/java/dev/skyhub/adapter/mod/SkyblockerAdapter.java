package dev.skyhub.adapter.mod;

import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.adapter.ModAdapter;
import dev.skyhub.adapter.framework.DandelionConfigCollector;
import dev.skyhub.model.HudWidget;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.util.Reflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Map;

/**
 * Tier-2 Mod-Specific Adapter for Skyblocker (v6.11.0+26.2).
 * Indexes all Dandelion config categories via DandelionConfigCollector and
 * all Skyblocker HUD widgets from WidgetManager.WIDGET_INSTANCES.
 */
public final class SkyblockerAdapter implements ModAdapter {
    private static final String MOD_ID = "skyblocker";
    private static final String CONFIG_MANAGER_CLASS = "de.hysky.skyblocker.config.SkyblockerConfigManager";
    private static final String WIDGET_MANAGER_CLASS = "de.hysky.skyblocker.skyblock.tabhud.screenbuilder.WidgetManager";
    private static final String WIDGET_CONFIG_SCREEN_CLASS = "de.hysky.skyblocker.skyblock.tabhud.config.WidgetsConfigurationScreen";

    private static final List<String> CATEGORY_CLASSES = List.of(
        "de.hysky.skyblocker.config.categories.GeneralCategory",
        "de.hysky.skyblocker.config.categories.UIAndVisualsCategory",
        "de.hysky.skyblocker.config.categories.DungeonsCategory",
        "de.hysky.skyblocker.config.categories.MiningCategory",
        "de.hysky.skyblocker.config.categories.FarmingCategory",
        "de.hysky.skyblocker.config.categories.ForagingCategory",
        "de.hysky.skyblocker.config.categories.SlayersCategory",
        "de.hysky.skyblocker.config.categories.CrimsonIsleCategory",
        "de.hysky.skyblocker.config.categories.HuntingCategory",
        "de.hysky.skyblocker.config.categories.OtherLocationsCategory",
        "de.hysky.skyblocker.config.categories.HelperCategory",
        "de.hysky.skyblocker.config.categories.ChatCategory",
        "de.hysky.skyblocker.config.categories.EventNotificationsCategory",
        "de.hysky.skyblocker.config.categories.QuickNavigationCategory",
        "de.hysky.skyblocker.config.categories.MiscCategory"
    );

    @Override
    public String name() {
        return "Skyblocker (Dandelion)";
    }

    @Override
    public int priority() {
        return 15;
    }

    @Override
    public void collect(AdapterContext ctx) {
        if (!ctx.isModLoaded(MOD_ID)) return;

        Class<?> mgrClass = Reflect.findClass(CONFIG_MANAGER_CLASS);
        if (mgrClass == null) return;

        try {
            ModEntry mod = ctx.getOrCreateMod(MOD_ID);

            // 1. Native Skyblocker config & HUD openers
            ctx.registerNativeMenu(MOD_ID, parent -> {
                Object s = Reflect.invokeStatic(mgrClass, "createGUI", parent);
                return s instanceof Screen screen ? screen : null;
            });
            Class<?> widgetScreenClass = Reflect.findClass(WIDGET_CONFIG_SCREEN_CLASS);
            if (widgetScreenClass != null) {
                ctx.registerNativeHud(MOD_ID, () -> {
                    try {
                        Object screen = widgetScreenClass.getDeclaredConstructor().newInstance();
                        if (screen instanceof Screen s) {
                            Minecraft.getInstance().gui.setScreen(s);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }

            // 2. Index all Dandelion categories against the live SkyblockerConfig
            Object dandelionMgr = Reflect.getStaticField(mgrClass, "CONFIG_MANAGER");
            Object defaults = dandelionMgr != null ? Reflect.invoke(dandelionMgr, "defaults") : null;
            Object liveConfig = Reflect.invokeStatic(mgrClass, "get");

            Runnable saveHook = () -> {
                if (dandelionMgr != null) {
                    Reflect.invoke(dandelionMgr, "save");
                    Reflect.invoke(dandelionMgr, "updatePatchedInstance");
                }
            };

            int settingsAdded = 0;
            if (defaults != null && liveConfig != null) {
                for (String catClassName : CATEGORY_CLASSES) {
                    Class<?> catClass = Reflect.findClass(catClassName);
                    if (catClass == null) continue;
                    try {
                        Object category = Reflect.invokeStatic(catClass, "create", defaults, liveConfig);
                        if (category != null) {
                            settingsAdded += DandelionConfigCollector.collectCategory(ctx, mod, category, saveHook);
                        }
                    } catch (Throwable ignored) {
                        // Single category change won't break the rest of Skyblocker
                    }
                }
            }

            // 3. Index Skyblocker HUD widgets from WidgetManager.WIDGET_INSTANCES
            int hudsAdded = collectHudWidgets(ctx, mod);

            if (settingsAdded > 0 || hudsAdded > 0) {
                ctx.markHealthy(MOD_ID, name(), settingsAdded + " settings, " + hudsAdded + " HUD widgets");
            }
        } catch (Throwable t) {
            ctx.reportFailure(MOD_ID, name(), t);
        }
    }

    private int collectHudWidgets(AdapterContext ctx, ModEntry mod) {
        Class<?> wmClass = Reflect.findClass(WIDGET_MANAGER_CLASS);
        if (wmClass == null) return 0;

        Object mapObj = Reflect.getStaticField(wmClass, "WIDGET_INSTANCES");
        if (!(mapObj instanceof Map<?, ?> widgets)) return 0;

        Runnable saveHud = () -> Reflect.invokeStatic(wmClass, "saveConfig");
        int count = 0;

        for (Map.Entry<?, ?> entry : widgets.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object widget = entry.getValue();
            if (widget == null) continue;

            try {
                String title = extractWidgetTitle(widget, key);
                ctx.addHudWidget(HudWidget.builder()
                    .id(MOD_ID + ":hud:" + key)
                    .modId(MOD_ID)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .name(title)
                    .category("HUD Widget")
                    .enabled(() -> Boolean.TRUE.equals(Reflect.invoke(widget, "shouldRender")), null)
                    .position(
                        () -> toInt(Reflect.invoke(widget, "getX"), 10),
                        () -> toInt(Reflect.invoke(widget, "getY"), 10),
                        x -> Reflect.invoke(widget, "setX", x),
                        y -> Reflect.invoke(widget, "setY", y)
                    )
                    .size(
                        () -> Math.max(64, toInt(Reflect.invoke(widget, "getWidth"), 110)),
                        () -> Math.max(20, toInt(Reflect.invoke(widget, "getHeight"), 36))
                    )
                    .scale(
                        () -> toDouble(Reflect.getField(widget, "scale"), 1.0),
                        s -> Reflect.setField(widget, "scale", (float) s)
                    )
                    .saveHook(saveHud)
                    .build());
                count++;
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    private String extractWidgetTitle(Object widget, String fallback) {
        Object info = Reflect.invoke(widget, "getInformation");
        if (info != null) {
            Object nameObj = Reflect.invoke(info, "name");
            String text = Reflect.extractText(nameObj);
            if (!text.isBlank()) return text;
        }
        return SettingEntry.formatIdentifier(fallback);
    }

    private int toInt(Object obj, int fallback) {
        return obj instanceof Number n ? n.intValue() : fallback;
    }

    private double toDouble(Object obj, double fallback) {
        return obj instanceof Number n ? n.doubleValue() : fallback;
    }
}
