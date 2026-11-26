package dev.skyhub.adapter.mod;

import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.adapter.ModAdapter;
import dev.skyhub.model.HudWidget;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingAccess;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.model.SettingType;
import dev.skyhub.util.Reflect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tier-2 Mod-Specific Adapter for Odin (v0.3.6 on MC 26.2).
 * Indexes every Odin Module toggle, every child Setting (Boolean/Number/Selector/String),
 * and every HUDSetting / HudElement from ModuleManager.INSTANCE.
 */
public final class OdinAdapter implements ModAdapter {
    private static final String MOD_ID = "odin";
    private static final String MODULE_MANAGER_CLASS = "com.odtheking.odin.features.ModuleManager";

    @Override
    public String name() {
        return "Odin";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public void collect(AdapterContext ctx) {
        if (!ctx.isModLoaded(MOD_ID)) return;

        Class<?> mmClass = Reflect.findClass(MODULE_MANAGER_CLASS);
        if (mmClass == null) return;

        try {
            Object mm = Reflect.getStaticField(mmClass, "INSTANCE");
            if (mm == null) return;

            ModEntry mod = ctx.getOrCreateMod(MOD_ID);
            Runnable saveHook = () -> Reflect.invoke(mm, "saveConfigurations");

            int settingsAdded = 0;

            // 1. Walk all Odin modules + their child settings
            Object modulesObj = Reflect.invoke(mm, "getModules");
            if (modulesObj instanceof Map<?, ?> modules) {
                for (Object module : modules.values()) {
                    if (module == null) continue;
                    String modName = String.valueOf(Reflect.invoke(module, "getName"));
                    String modDesc = String.valueOf(Reflect.invoke(module, "getDescription"));
                    Object catObj = Reflect.invoke(module, "getCategory");
                    String catName = catObj != null ? SettingEntry.formatIdentifier(catObj.toString()) : "Modules";
                    boolean alwaysActive = Boolean.TRUE.equals(Reflect.invoke(module, "getAlwaysActive"));

                    if (!alwaysActive) {
                        ctx.addSetting(SettingEntry.builder()
                            .id(MOD_ID + ":mod:" + modName)
                            .modId(MOD_ID)
                            .modName(mod.displayName())
                            .modAccentColor(mod.accentColor())
                            .category(catName)
                            .subcategory(modName)
                            .name(modName + " (Module)")
                            .description(modDesc == null ? "" : modDesc)
                            .searchTags(List.of("odin", "module", catName, modName))
                            .type(SettingType.BOOLEAN)
                            .access(new SettingAccess() {
                                @Override
                                public Object get() {
                                    return Boolean.TRUE.equals(Reflect.invoke(module, "getEnabled"));
                                }

                                @Override
                                public boolean set(Object value) {
                                    boolean want = Boolean.TRUE.equals(value);
                                    boolean current = Boolean.TRUE.equals(Reflect.invoke(module, "getEnabled"));
                                    if (want != current) {
                                        Reflect.invoke(module, "toggle");
                                    }
                                    return true;
                                }

                                @Override
                                public void save() {
                                    saveHook.run();
                                }
                            })
                            .build());
                        settingsAdded++;
                    }

                    // Child settings
                    Object settingsMapObj = Reflect.invoke(module, "getSettings");
                    if (settingsMapObj instanceof Map<?, ?> settingsMap) {
                        for (Object setting : settingsMap.values()) {
                            if (setting == null) continue;
                            if (collectSetting(ctx, mod, catName, modName, setting, saveHook)) {
                                settingsAdded++;
                            }
                        }
                    }
                }
            }

            // 2. Walk Odin HUDSettings from getHudSettingsCache()
            int hudsAdded = 0;
            Object hudCacheObj = Reflect.invoke(mm, "getHudSettingsCache");
            if (hudCacheObj instanceof List<?> hudSettings) {
                for (int i = 0; i < hudSettings.size(); i++) {
                    Object hudSetting = hudSettings.get(i);
                    if (hudSetting == null) continue;
                    if (collectHudSetting(ctx, mod, hudSetting, i, saveHook)) {
                        hudsAdded++;
                    }
                }
            }

            if (settingsAdded > 0 || hudsAdded > 0) {
                ctx.markHealthy(MOD_ID, name(), settingsAdded + " settings, " + hudsAdded + " HUDs");
            }
        } catch (Throwable t) {
            ctx.reportFailure(MOD_ID, name(), t);
        }
    }

    private boolean collectSetting(
        AdapterContext ctx,
        ModEntry mod,
        String category,
        String moduleName,
        Object setting,
        Runnable saveHook
    ) {
        String simpleName = setting.getClass().getSimpleName();
        String settingName = Reflect.stripFormatting(String.valueOf(Reflect.invoke(setting, "getName")));
        Object descObj = Reflect.invoke(setting, "getDesc");
        String desc = descObj instanceof String s ? Reflect.stripFormatting(s) : "";

        return switch (simpleName) {
            case "BooleanSetting" -> {
                ctx.addSetting(SettingEntry.builder()
                    .id(MOD_ID + ":set:" + moduleName + ":" + settingName)
                    .modId(MOD_ID)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .category(category)
                    .subcategory(moduleName)
                    .name(settingName)
                    .description(desc)
                    .searchTags(List.of("odin", moduleName))
                    .type(SettingType.BOOLEAN)
                    .access(new SettingAccess() {
                        @Override public Object get() { return Reflect.invoke(setting, "getEnabled"); }
                        @Override public boolean set(Object v) {
                            Reflect.invoke(setting, "setEnabled", Boolean.TRUE.equals(v));
                            return true;
                        }
                        @Override public Object defaultValue() { return Reflect.invoke(setting, "getDefault"); }
                        @Override public void save() { saveHook.run(); }
                    })
                    .build());
                yield true;
            }
            case "NumberSetting" -> {
                Object val = Reflect.invoke(setting, "getValue");
                boolean isInt = val instanceof Integer || val instanceof Long;
                double min = toDouble(Reflect.getField(setting, "min"), 0.0);
                double max = toDouble(Reflect.getField(setting, "max"), 100.0);
                double inc = toDouble(Reflect.getField(setting, "increment"), isInt ? 1.0 : 0.1);
                if (max <= min) max = min + 100.0;

                ctx.addSetting(SettingEntry.builder()
                    .id(MOD_ID + ":set:" + moduleName + ":" + settingName)
                    .modId(MOD_ID)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .category(category)
                    .subcategory(moduleName)
                    .name(settingName)
                    .description(desc)
                    .searchTags(List.of("odin", moduleName))
                    .type(isInt ? SettingType.INT : SettingType.DOUBLE)
                    .range(min, max, inc > 0 ? inc : (isInt ? 1.0 : 0.1))
                    .access(new SettingAccess() {
                        @Override public Object get() { return Reflect.invoke(setting, "getValue"); }
                        @Override public boolean set(Object v) {
                            if (!(v instanceof Number n)) return false;
                            Object current = Reflect.invoke(setting, "getValue");
                            Number coerced = current instanceof Integer ? n.intValue()
                                : current instanceof Float ? n.floatValue()
                                : current instanceof Long ? n.longValue()
                                : n.doubleValue();
                            Reflect.invoke(setting, "setValue", coerced);
                            return true;
                        }
                        @Override public Object defaultValue() { return Reflect.invoke(setting, "getDefault"); }
                        @Override public void save() { saveHook.run(); }
                    })
                    .build());
                yield true;
            }
            case "SelectorSetting" -> {
                Object optsObj = Reflect.invoke(setting, "getOptions");
                if (!(optsObj instanceof List<?> opts) || opts.isEmpty()) yield false;
                List<String> labels = new ArrayList<>(opts.size());
                List<Object> values = new ArrayList<>(opts.size());
                for (Object o : opts) {
                    values.add(o);
                    labels.add(SettingEntry.formatIdentifier(o.toString()));
                }
                ctx.addSetting(SettingEntry.builder()
                    .id(MOD_ID + ":set:" + moduleName + ":" + settingName)
                    .modId(MOD_ID)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .category(category)
                    .subcategory(moduleName)
                    .name(settingName)
                    .description(desc)
                    .searchTags(List.of("odin", moduleName))
                    .type(SettingType.ENUM)
                    .enumOptions(labels, values)
                    .access(new SettingAccess() {
                        @Override public Object get() { return Reflect.invoke(setting, "getValue"); }
                        @Override public boolean set(Object v) {
                            Reflect.invoke(setting, "setValue", v);
                            return true;
                        }
                        @Override public Object defaultValue() { return Reflect.invoke(setting, "getDefault"); }
                        @Override public void save() { saveHook.run(); }
                    })
                    .build());
                yield true;
            }
            case "StringSetting" -> {
                ctx.addSetting(SettingEntry.builder()
                    .id(MOD_ID + ":set:" + moduleName + ":" + settingName)
                    .modId(MOD_ID)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .category(category)
                    .subcategory(moduleName)
                    .name(settingName)
                    .description(desc)
                    .searchTags(List.of("odin", moduleName))
                    .type(SettingType.STRING)
                    .access(new SettingAccess() {
                        @Override public Object get() { return Reflect.invoke(setting, "getValue"); }
                        @Override public boolean set(Object v) {
                            Reflect.invoke(setting, "setValue", String.valueOf(v));
                            return true;
                        }
                        @Override public Object defaultValue() { return Reflect.invoke(setting, "getDefault"); }
                        @Override public void save() { saveHook.run(); }
                    })
                    .build());
                yield true;
            }
            default -> false;
        };
    }

    private boolean collectHudSetting(
        AdapterContext ctx,
        ModEntry mod,
        Object hudSetting,
        int index,
        Runnable saveHook
    ) {
        Object hud = Reflect.invoke(hudSetting, "getHud");
        if (hud == null) return false;

        Object module = Reflect.invoke(hudSetting, "getModule");
        String moduleName = module != null ? String.valueOf(Reflect.invoke(module, "getName")) : "Odin";
        String hudName = Reflect.stripFormatting(String.valueOf(Reflect.invoke(hudSetting, "getName")));
        String label = hudName.equalsIgnoreCase(moduleName) ? hudName : moduleName + " — " + hudName;

        ctx.addHudWidget(HudWidget.builder()
            .id(MOD_ID + ":hud:" + index + ":" + label)
            .modId(MOD_ID)
            .modName(mod.displayName())
            .modAccentColor(mod.accentColor())
            .name(label)
            .category(moduleName)
            .enabled(
                () -> Boolean.TRUE.equals(Reflect.invoke(hud, "getEnabled")),
                v -> Reflect.invoke(hud, "setEnabled", v)
            )
            .position(
                () -> toInt(Reflect.invoke(hud, "getX"), 10),
                () -> toInt(Reflect.invoke(hud, "getY"), 10),
                x -> Reflect.invoke(hud, "setX", x),
                y -> Reflect.invoke(hud, "setY", y)
            )
            .size(
                () -> Math.max(60, toInt(Reflect.invoke(hud, "getWidth"), 100)),
                () -> Math.max(18, toInt(Reflect.invoke(hud, "getHeight"), 24))
            )
            .scale(
                () -> toDouble(Reflect.invoke(hud, "getScale"), 1.0),
                s -> Reflect.invoke(hud, "setScale", (float) s)
            )
            .saveHook(saveHook)
            .build());
        return true;
    }

    private int toInt(Object obj, int fallback) {
        return obj instanceof Number n ? n.intValue() : fallback;
    }

    private double toDouble(Object obj, double fallback) {
        return obj instanceof Number n ? n.doubleValue() : fallback;
    }
}
