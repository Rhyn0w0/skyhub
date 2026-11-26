package dev.skyhub.adapter.framework;

import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingAccess;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.model.SettingType;
import dev.skyhub.util.Reflect;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Tier-1 Framework Adapter helper for Dandelion (net.azureaaron.dandelion)
 * which powers Skyblocker 6.x on Minecraft 26.2 (bridging YACL & MoulConfig).
 */
public final class DandelionConfigCollector {
    private DandelionConfigCollector() {}

    public static int collectCategory(
        AdapterContext ctx,
        ModEntry mod,
        Object configCategory,
        Runnable saveHook
    ) {
        if (configCategory == null) return 0;

        String catName = Reflect.extractText(Reflect.invoke(configCategory, "name"));
        if (catName.isBlank()) {
            catName = SettingEntry.formatIdentifier(String.valueOf(Reflect.invoke(configCategory, "id")));
        }

        int added = 0;

        // 1. Root group
        Object rootGroup = Reflect.invoke(configCategory, "rootGroup");
        if (rootGroup != null) {
            added += collectGroup(ctx, mod, catName, "", rootGroup, saveHook);
        }

        // 2. Named option groups
        Object groupsObj = Reflect.invoke(configCategory, "groups");
        if (groupsObj instanceof List<?> groups) {
            for (Object group : groups) {
                if (group == null) continue;
                String groupName = Reflect.extractText(Reflect.invoke(group, "name"));
                added += collectGroup(ctx, mod, catName, groupName, group, saveHook);
            }
        }

        return added;
    }

    private static int collectGroup(
        AdapterContext ctx,
        ModEntry mod,
        String categoryName,
        String groupName,
        Object group,
        Runnable saveHook
    ) {
        Object optionsObj = Reflect.invoke(group, "options");
        if (!(optionsObj instanceof List<?> options)) return 0;

        int added = 0;
        for (Object option : options) {
            if (option == null) continue;
            try {
                if (collectOption(ctx, mod, categoryName, groupName, option, saveHook)) {
                    added++;
                }
            } catch (Throwable ignored) {
            }
        }
        return added;
    }

    @SuppressWarnings("unchecked")
    private static boolean collectOption(
        AdapterContext ctx,
        ModEntry mod,
        String categoryName,
        String groupName,
        Object option,
        Runnable saveHook
    ) {
        Object modifiable = Reflect.invoke(option, "modifiable");
        if (Boolean.FALSE.equals(modifiable)) return false;

        Object binding = Reflect.invoke(option, "binding");
        if (binding == null) return false;

        Object typeObj = Reflect.invoke(option, "type");
        if (!(typeObj instanceof Class<?> valueClass)) return false;

        Object controller = Reflect.invoke(option, "controller");
        String ctrlName = controller != null ? controller.getClass().getSimpleName() : "";

        SettingType settingType = mapType(valueClass, ctrlName);
        if (settingType == null) return false;

        String name = Reflect.extractText(Reflect.invoke(option, "name"));
        String optionId = String.valueOf(Reflect.invoke(option, "id"));
        if (name.isBlank()) {
            name = SettingEntry.formatIdentifier(optionId);
        }

        StringBuilder desc = new StringBuilder();
        Object descObj = Reflect.invoke(option, "description");
        if (descObj instanceof List<?> lines) {
            for (Object line : lines) {
                String text = Reflect.extractText(line);
                if (!text.isBlank()) {
                    if (!desc.isEmpty()) desc.append(" ");
                    desc.append(text);
                }
            }
        }

        List<String> tags = new ArrayList<>();
        tags.add("dandelion");
        Object tagsObj = Reflect.invoke(option, "tags");
        if (tagsObj instanceof List<?> tagList) {
            for (Object t : tagList) {
                String text = Reflect.extractText(t);
                if (!text.isBlank()) tags.add(text);
            }
        }

        double min = 0.0;
        double max = 100.0;
        double step = settingType == SettingType.INT ? 1.0 : 0.1;
        if (controller != null && (settingType == SettingType.INT || settingType == SettingType.FLOAT || settingType == SettingType.DOUBLE)) {
            Object rMin = Reflect.invoke(controller, "min");
            Object rMax = Reflect.invoke(controller, "max");
            Object rStep = Reflect.invoke(controller, "step");
            if (rMin instanceof Number n) min = n.doubleValue();
            if (rMax instanceof Number n) max = n.doubleValue();
            if (rStep instanceof Number n && n.doubleValue() > 0) step = n.doubleValue();
            if (max <= min) max = min + 100.0;
        }

        List<String> enumLabels = List.of();
        List<Object> enumValues = List.of();
        if (settingType == SettingType.ENUM && valueClass.isEnum()) {
            Object[] constants = valueClass.getEnumConstants();
            if (constants == null || constants.length == 0) return false;
            Function<Object, Object> formatter = null;
            if (controller != null) {
                Object fmtObj = Reflect.invoke(controller, "formatter");
                if (fmtObj instanceof Function<?, ?> f) {
                    formatter = (Function<Object, Object>) f;
                }
            }
            List<String> labels = new ArrayList<>(constants.length);
            List<Object> values = new ArrayList<>(constants.length);
            for (Object c : constants) {
                values.add(c);
                String label = null;
                if (formatter != null) {
                    try {
                        label = Reflect.extractText(formatter.apply(c));
                    } catch (Throwable ignored) {
                    }
                }
                labels.add((label != null && !label.isBlank()) ? label : SettingEntry.formatIdentifier(c.toString()));
            }
            enumLabels = labels;
            enumValues = values;
        }

        SettingAccess access = new SettingAccess() {
            @Override
            public Object get() {
                Object val = Reflect.invoke(binding, "get");
                if (val instanceof Color c) {
                    return c.getRGB();
                }
                return val;
            }

            @Override
            public boolean set(Object value) {
                Object coerced = coerce(valueClass, value);
                Reflect.invoke(binding, "set", coerced);
                return true;
            }

            @Override
            public Object defaultValue() {
                Object def = Reflect.invoke(binding, "defaultValue");
                if (def instanceof Color c) {
                    return c.getRGB();
                }
                return def;
            }

            @Override
            public void save() {
                if (saveHook != null) {
                    saveHook.run();
                }
            }
        };

        String uniqueId = mod.modId() + ":dl:" + categoryName + ":" + groupName + ":" + optionId + ":" + name;
        ctx.addSetting(SettingEntry.builder()
            .id(uniqueId)
            .modId(mod.modId())
            .modName(mod.displayName())
            .modAccentColor(mod.accentColor())
            .category(categoryName)
            .subcategory(groupName)
            .name(name)
            .description(desc.toString())
            .searchTags(tags)
            .type(settingType)
            .range(min, max, step)
            .enumOptions(enumLabels, enumValues)
            .access(access)
            .build());
        return true;
    }

    private static SettingType mapType(Class<?> valueClass, String ctrlName) {
        if (valueClass == Color.class || ctrlName.contains("Colour") || ctrlName.contains("Color")) {
            return SettingType.COLOR;
        }
        if (valueClass == Boolean.class || valueClass == boolean.class) return SettingType.BOOLEAN;
        if (valueClass == Integer.class || valueClass == int.class || valueClass == Long.class || valueClass == long.class) {
            return SettingType.INT;
        }
        if (valueClass == Float.class || valueClass == float.class) return SettingType.FLOAT;
        if (valueClass == Double.class || valueClass == double.class) return SettingType.DOUBLE;
        if (valueClass == String.class) return SettingType.STRING;
        if (valueClass.isEnum()) return SettingType.ENUM;
        return null;
    }

    private static Object coerce(Class<?> target, Object value) {
        if (value == null) return null;
        if (target == Color.class && value instanceof Number n) return new Color(n.intValue(), true);
        if ((target == Integer.class || target == int.class) && value instanceof Number n) return n.intValue();
        if ((target == Long.class || target == long.class) && value instanceof Number n) return n.longValue();
        if ((target == Float.class || target == float.class) && value instanceof Number n) return n.floatValue();
        if ((target == Double.class || target == double.class) && value instanceof Number n) return n.doubleValue();
        return value;
    }
}
