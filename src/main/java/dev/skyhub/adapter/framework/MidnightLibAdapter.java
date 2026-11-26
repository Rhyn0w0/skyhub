package dev.skyhub.adapter.framework;

import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.adapter.ModAdapter;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingAccess;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.model.SettingType;
import dev.skyhub.util.Reflect;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tier-1 Framework Adapter for MidnightLib (v1.9 / v1.10 on MC 26.2).
 * Covers PackCore, SkyBlock Enhancements, SkyRecipes, Enhanced Chat,
 * Enhanced Storage, Enhanced Sound Control, ScaleMe, and MarketGuard in one adapter.
 */
public final class MidnightLibAdapter implements ModAdapter {
    private static final String MIDNIGHT_CONFIG_CLASS = "eu.midnightdust.lib.config.MidnightConfig";

    @Override
    public String name() {
        return "MidnightLib";
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public void collect(AdapterContext ctx) {
        Class<?> mcClass = Reflect.findClass(MIDNIGHT_CONFIG_CLASS);
        if (mcClass == null) {
            return;
        }

        Object instancesObj = Reflect.getStaticField(mcClass, "configInstances");
        Object entriesObj = Reflect.getStaticField(mcClass, "entries");
        if (!(instancesObj instanceof Map<?, ?> configInstances) || !(entriesObj instanceof Map<?, ?> allEntries)) {
            return;
        }

        // Group EntryInfo by modid
        Map<String, List<Object>> entriesByMod = new HashMap<>();
        for (Object info : allEntries.values()) {
            if (info == null) continue;
            Object modIdObj = Reflect.getField(info, "modid");
            if (modIdObj instanceof String modId) {
                entriesByMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(info);
            }
        }

        for (Map.Entry<?, ?> modCfg : configInstances.entrySet()) {
            if (!(modCfg.getKey() instanceof String modId)) continue;
            Object configInstance = modCfg.getValue();
            if ("midnightlib".equals(modId)) continue;

            try {
                ModEntry mod = ctx.getOrCreateMod(modId);

                // Register MidnightConfig native screen opener
                ctx.registerNativeMenu(modId, parent -> {
                    Object s = Reflect.invokeStatic(mcClass, "getScreen", parent, modId);
                    return s instanceof Screen screen ? screen : null;
                });

                List<Object> modEntries = entriesByMod.getOrDefault(modId, List.of());
                int added = 0;

                for (Object info : modEntries) {
                    Object entryAnnotation = Reflect.getField(info, "entry");
                    if (entryAnnotation == null) continue; // skip comments/headers

                    Object fieldObj = Reflect.getField(info, "field");
                    if (!(fieldObj instanceof Field field)) continue;
                    field.setAccessible(true);

                    String fieldName = String.valueOf(Reflect.getField(info, "fieldName"));
                    String translationKey = String.valueOf(Reflect.getField(info, "translationKey"));
                    Object defaultVal = Reflect.getField(info, "defaultValue");

                    // Category from @Entry(category = "...")
                    Object catObj = Reflect.invoke(entryAnnotation, "category");
                    String category = (catObj instanceof String cs && !cs.isBlank())
                        ? resolveCategoryName(modId, cs)
                        : "General";

                    boolean isColor = Boolean.TRUE.equals(Reflect.invoke(entryAnnotation, "isColor"));
                    double min = toDouble(Reflect.invoke(entryAnnotation, "min"), 0.0);
                    double max = toDouble(Reflect.invoke(entryAnnotation, "max"), 100.0);
                    if (max <= min) {
                        min = 0.0;
                        max = 100.0;
                    }

                    Class<?> type = field.getType();
                    SettingType settingType = mapType(type, isColor);
                    if (settingType == null) continue;

                    List<String> enumLabels = List.of();
                    List<Object> enumValues = List.of();
                    if (settingType == SettingType.ENUM) {
                        Object[] constants = type.getEnumConstants();
                        if (constants == null || constants.length == 0) continue;
                        List<String> labels = new ArrayList<>(constants.length);
                        List<Object> values = new ArrayList<>(constants.length);
                        for (Object c : constants) {
                            values.add(c);
                            String enumKey = modId + ".midnightconfig.enum." + type.getSimpleName() + "." + c;
                            labels.add(Language.getInstance().has(enumKey) ? I18n.get(enumKey) : SettingEntry.formatIdentifier(c.toString()));
                        }
                        enumLabels = labels;
                        enumValues = values;
                    }

                    String displayName = (translationKey != null && Language.getInstance().has(translationKey))
                        ? Reflect.stripFormatting(I18n.get(translationKey))
                        : SettingEntry.formatIdentifier(fieldName);

                    String tooltipKey = translationKey + ".tooltip";
                    String description = Language.getInstance().has(tooltipKey)
                        ? Reflect.stripFormatting(I18n.get(tooltipKey))
                        : "";

                    boolean isStatic = Modifier.isStatic(field.getModifiers());
                    Object targetObj = isStatic ? null : configInstance;
                    double step = settingType == SettingType.INT ? 1.0 : Math.max(0.05, (max - min) / 100.0);

                    SettingAccess access = new SettingAccess() {
                        @Override
                        public Object get() {
                            try {
                                return field.get(targetObj);
                            } catch (Throwable ignored) {
                                return null;
                            }
                        }

                        @Override
                        public boolean set(Object value) {
                            try {
                                Object coerced = coerceValue(field.getType(), value);
                                field.set(targetObj, coerced);
                                Reflect.invoke(info, "setValue", coerced);
                                return true;
                            } catch (Throwable ignored) {
                                return false;
                            }
                        }

                        @Override
                        public Object defaultValue() {
                            return defaultVal;
                        }

                        @Override
                        public void save() {
                            Reflect.invokeStatic(mcClass, "write", modId);
                        }
                    };

                    ctx.addSetting(SettingEntry.builder()
                        .id(modId + ":ml:" + category + ":" + fieldName)
                        .modId(modId)
                        .modName(mod.displayName())
                        .modAccentColor(mod.accentColor())
                        .category(category)
                        .name(displayName)
                        .description(description)
                        .searchTags(List.of(fieldName, "midnightlib"))
                        .type(settingType)
                        .range(min, max, step)
                        .enumOptions(enumLabels, enumValues)
                        .access(access)
                        .build());
                    added++;
                }

                if (added > 0) {
                    ctx.markHealthy(modId, name(), added + " settings indexed via MidnightLib");
                }
            } catch (Throwable t) {
                ctx.reportFailure(modId, name(), t);
            }
        }
    }

    private String resolveCategoryName(String modId, String category) {
        String key = modId + ".midnightconfig.category." + category;
        if (Language.getInstance().has(key)) {
            return Reflect.stripFormatting(I18n.get(key));
        }
        return SettingEntry.formatIdentifier(category);
    }

    private SettingType mapType(Class<?> type, boolean isColor) {
        if (isColor) return SettingType.COLOR;
        if (type == boolean.class || type == Boolean.class) return SettingType.BOOLEAN;
        if (type == int.class || type == Integer.class || type == short.class || type == byte.class || type == long.class) {
            return SettingType.INT;
        }
        if (type == float.class || type == Float.class) return SettingType.FLOAT;
        if (type == double.class || type == Double.class) return SettingType.DOUBLE;
        if (type == String.class) return SettingType.STRING;
        if (type.isEnum()) return SettingType.ENUM;
        return null;
    }

    private Object coerceValue(Class<?> targetType, Object value) {
        if (value == null) return null;
        if (targetType == int.class || targetType == Integer.class) {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
        }
        if (targetType == float.class || targetType == Float.class) {
            return value instanceof Number n ? n.floatValue() : Float.parseFloat(value.toString());
        }
        if (targetType == double.class || targetType == Double.class) {
            return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
        }
        if (targetType == short.class || targetType == Short.class) {
            return value instanceof Number n ? n.shortValue() : Short.parseShort(value.toString());
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return value instanceof Number n ? n.byteValue() : Byte.parseByte(value.toString());
        }
        return value;
    }

    private double toDouble(Object obj, double fallback) {
        if (obj instanceof Number n) {
            double v = n.doubleValue();
            if (Double.isFinite(v) && Math.abs(v) < 1e9) {
                return v;
            }
        }
        return fallback;
    }
}
