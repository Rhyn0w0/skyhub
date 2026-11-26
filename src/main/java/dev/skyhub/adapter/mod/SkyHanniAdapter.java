package dev.skyhub.adapter.mod;

import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.adapter.ModAdapter;
import dev.skyhub.adapter.framework.MoulConfigAdapter;
import dev.skyhub.model.HudWidget;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.util.Reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Tier-2 Mod-Specific Adapter for SkyHanni (v9.5.0 on MC 26.2).
 * Delegates config indexing to the Tier-1 MoulConfigAdapter and extracts
 * SkyHanni Position HUD elements directly from SkyHanniMod.feature.
 */
public final class SkyHanniAdapter implements ModAdapter {
    private static final String MOD_ID = "skyhanni";
    private static final String SKYHANNI_MOD_CLASS = "at.hannibal2.skyhanni.SkyHanniMod";
    private static final String POSITION_CLASS = "at.hannibal2.skyhanni.config.core.config.Position";
    private static final String GUI_EDIT_MANAGER_CLASS = "at.hannibal2.skyhanni.data.GuiEditManager";
    private static final String CONFIG_FILE_TYPE_CLASS = "at.hannibal2.skyhanni.config.ConfigFileType";

    @Override
    public String name() {
        return "SkyHanni (MoulConfig)";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void collect(AdapterContext ctx) {
        if (!ctx.isModLoaded(MOD_ID)) return;

        Class<?> modClass = Reflect.findClass(SKYHANNI_MOD_CLASS);
        if (modClass == null) return;

        try {
            ModEntry mod = ctx.getOrCreateMod(MOD_ID);
            Object configManager = Reflect.getStaticField(modClass, "configManager");
            Object feature = Reflect.getStaticField(modClass, "feature");

            Runnable saveHook = createSaveHook(configManager);

            // 1. Register SkyHanni native HUD editor fallback
            Class<?> guiEditClass = Reflect.findClass(GUI_EDIT_MANAGER_CLASS);
            if (guiEditClass != null) {
                ctx.registerNativeHud(MOD_ID, () -> Reflect.invokeStatic(guiEditClass, "openGuiPositionEditor", true));
            }

            // 2. Index settings via Tier-1 MoulConfigAdapter
            int settingsAdded = 0;
            if (configManager != null) {
                Object processor = Reflect.invoke(configManager, "getProcessor");
                if (processor != null) {
                    settingsAdded = MoulConfigAdapter.collectProcessor(ctx, mod, processor, saveHook);
                }
            }

            // 3. Extract Position HUD widgets from SkyHanniMod.feature
            int hudsAdded = 0;
            Class<?> positionClass = Reflect.findClass(POSITION_CLASS);
            if (feature != null && positionClass != null) {
                Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                hudsAdded = scanPositions(ctx, mod, feature, positionClass, "General", "", visited, saveHook, 0);
            }

            if (settingsAdded > 0 || hudsAdded > 0) {
                ctx.markHealthy(MOD_ID, name(), settingsAdded + " settings, " + hudsAdded + " HUDs");
            }
        } catch (Throwable t) {
            ctx.reportFailure(MOD_ID, name(), t);
        }
    }

    private Runnable createSaveHook(Object configManager) {
        if (configManager == null) return () -> {};
        Class<?> fileTypeClass = Reflect.findClass(CONFIG_FILE_TYPE_CLASS);
        Object featuresEnum = fileTypeClass != null ? Reflect.getStaticField(fileTypeClass, "FEATURES") : null;
        return () -> {
            if (featuresEnum != null) {
                Reflect.invoke(configManager, "saveConfig", featuresEnum, "SkyHub edit", false);
            } else {
                Reflect.invoke(configManager, "savePendingConfigs");
            }
        };
    }

    private int scanPositions(
        AdapterContext ctx,
        ModEntry mod,
        Object node,
        Class<?> positionClass,
        String category,
        String pathPrefix,
        Set<Object> visited,
        Runnable saveHook,
        int depth
    ) {
        if (node == null || depth > 5 || !visited.add(node)) return 0;

        Class<?> cls = node.getClass();
        String clsName = cls.getName();
        if (!clsName.startsWith("at.hannibal2.skyhanni.config")) return 0;

        int found = 0;
        for (Field f : cls.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
            try {
                f.setAccessible(true);
                Object val = f.get(node);
                if (val == null) continue;

                String fieldLabel = SettingEntry.formatIdentifier(f.getName());
                String nextCat = depth == 0 ? fieldLabel : category;
                String nextPath = pathPrefix.isEmpty() ? f.getName() : pathPrefix + "." + f.getName();

                if (positionClass.isInstance(val)) {
                    Object internalObj = Reflect.invoke(val, "getInternalName");
                    String displayName = (internalObj instanceof String s && !s.isBlank())
                        ? Reflect.stripFormatting(s)
                        : (depth >= 2 ? SettingEntry.formatIdentifier(cls.getSimpleName().replace("Config", "")) + " — " + fieldLabel : fieldLabel);

                    final Object pos = val;
                    ctx.addHudWidget(HudWidget.builder()
                        .id(MOD_ID + ":pos:" + nextPath)
                        .modId(MOD_ID)
                        .modName(mod.displayName())
                        .modAccentColor(mod.accentColor())
                        .name(displayName)
                        .category(nextCat)
                        .position(
                            () -> toInt(Reflect.invoke(pos, "getAbsX0", 110), toInt(Reflect.invoke(pos, "getX"), 10)),
                            () -> toInt(Reflect.invoke(pos, "getAbsY0", 22), toInt(Reflect.invoke(pos, "getY"), 10)),
                            x -> {
                                int curY = toInt(Reflect.invoke(pos, "getAbsY0", 22), toInt(Reflect.invoke(pos, "getY"), 10));
                                Reflect.invoke(pos, "moveTo", x, curY);
                            },
                            y -> {
                                int curX = toInt(Reflect.invoke(pos, "getAbsX0", 110), toInt(Reflect.invoke(pos, "getX"), 10));
                                Reflect.invoke(pos, "moveTo", curX, y);
                            }
                        )
                        .size(() -> 115, () -> 22)
                        .scale(
                            () -> toDouble(Reflect.invoke(pos, "getEffectiveScale"), 1.0),
                            s -> Reflect.invoke(pos, "setScale", (float) s)
                        )
                        .saveHook(saveHook)
                        .build());
                    found++;
                } else if (val.getClass().getName().startsWith("at.hannibal2.skyhanni.config")) {
                    found += scanPositions(ctx, mod, val, positionClass, nextCat, nextPath, visited, saveHook, depth + 1);
                }
            } catch (Throwable ignored) {
            }
        }
        return found;
    }

    private int toInt(Object obj, int fallback) {
        return obj instanceof Number n ? n.intValue() : fallback;
    }

    private double toDouble(Object obj, double fallback) {
        return obj instanceof Number n ? n.doubleValue() : fallback;
    }
}
