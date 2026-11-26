package dev.skyhub.adapter.framework;

import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.adapter.ModAdapter;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingAccess;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.model.SettingType;
import dev.skyhub.util.Reflect;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Tier-1 Framework Adapter for ResourcefulConfig (v5.x on MC 26.2).
 * Covers SkyOcean, CustomScoreboard, SkyBlockPv, Feesh, and any other ResourcefulConfig mod.
 */
public final class ResourcefulConfigAdapter implements ModAdapter {
    private static final String CONFIGURATIONS_CLASS =
        "com.teamresourceful.resourcefulconfig.common.config.Configurations";
    private static final String SCREEN_CLASS =
        "com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigScreen";
    private static final String OPTION_CLASS =
        "com.teamresourceful.resourcefulconfig.api.types.options.Option";

    @Override
    public String name() {
        return "ResourcefulConfig";
    }

    @Override
    public int priority() {
        return 55;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void collect(AdapterContext ctx) {
        Class<?> configsClass = Reflect.findClass(CONFIGURATIONS_CLASS);
        if (configsClass == null) {
            return;
        }
        Object instance = Reflect.getStaticField(configsClass, "INSTANCE");
        if (instance == null) {
            return;
        }

        Class<?> optionClass = Reflect.findClass(OPTION_CLASS);
        Object colorOptionKey = optionClass != null ? Reflect.getStaticField(optionClass, "COLOR") : null;
        Object rangeOptionKey = optionClass != null ? Reflect.getStaticField(optionClass, "RANGE") : null;

        Object modToConfigsObj = Reflect.invoke(instance, "modToConfigs");
        if (!(modToConfigsObj instanceof Map<?, ?> modToConfigs)) {
            return;
        }

        for (Map.Entry<?, ?> entry : modToConfigs.entrySet()) {
            if (!(entry.getKey() instanceof String modId)) continue;
            if (!(entry.getValue() instanceof Collection<?> configIds)) continue;

            try {
                ModEntry mod = ctx.getOrCreateMod(modId);
                int before = ctx.settings().size();

                // Register ResourcefulConfig native screen opener
                registerNativeScreen(ctx, modId);

                for (Object cidObj : configIds) {
                    if (!(cidObj instanceof String configId)) continue;
                    Object config = Reflect.invoke(instance, "getConfig", configId);
                    if (config == null) continue;
                    traverseConfig(ctx, mod, config, config, "", "", colorOptionKey, rangeOptionKey, 0);
                }

                int added = ctx.settings().size() - before;
                if (added > 0) {
                    ctx.markHealthy(modId, name(), added + " settings indexed via ResourcefulConfig");
                }
            } catch (Throwable t) {
                ctx.reportFailure(modId, name(), t);
            }
        }
    }

    private void registerNativeScreen(AdapterContext ctx, String modId) {
        Class<?> screenClass = Reflect.findClass(SCREEN_CLASS);
        if (screenClass == null) return;
        ctx.registerNativeMenu(modId, parent -> {
            Object factory = Reflect.invokeStatic(screenClass, "getFactory", modId);
            if (factory instanceof Function<?, ?> fn) {
                @SuppressWarnings("unchecked")
                Function<Screen, Screen> typed = (Function<Screen, Screen>) fn;
                return typed.apply(parent);
            }
            Object direct = Reflect.invokeStatic(screenClass, "make", parent, modId);
            if (direct instanceof Screen s) return s;
            return null;
        });
    }

    private void traverseConfig(
        AdapterContext ctx,
        ModEntry mod,
        Object rootConfig,
        Object currentConfig,
        String categoryPath,
        String subcategoryPath,
        Object colorOptionKey,
        Object rangeOptionKey,
        int depth
    ) {
        if (currentConfig == null || depth > 8) return;

        Object elementsObj = Reflect.invoke(currentConfig, "elements");
        if (elementsObj instanceof List<?> elements) {
            for (Object element : elements) {
                processElement(ctx, mod, rootConfig, element, categoryPath, subcategoryPath, colorOptionKey, rangeOptionKey, depth);
            }
        }

        Object categoriesObj = Reflect.invoke(currentConfig, "categories");
        if (categoriesObj instanceof Map<?, ?> categories) {
            for (Map.Entry<?, ?> catEntry : categories.entrySet()) {
                String catKey = String.valueOf(catEntry.getKey());
                Object subConfig = catEntry.getValue();
                if (subConfig == null) continue;

                String catTitle = extractConfigTitle(subConfig, catKey);
                String nextCat = categoryPath.isEmpty() ? catTitle : categoryPath;
                String nextSub = categoryPath.isEmpty() ? "" : (subcategoryPath.isEmpty() ? catTitle : subcategoryPath + " / " + catTitle);

                traverseConfig(ctx, mod, rootConfig, subConfig, nextCat, nextSub, colorOptionKey, rangeOptionKey, depth + 1);
            }
        }
    }

    private String extractConfigTitle(Object config, String fallbackKey) {
        Object info = Reflect.invoke(config, "info");
        if (info != null) {
            Object titleObj = Reflect.invoke(info, "title");
            String text = Reflect.extractText(titleObj);
            if (!text.isBlank()) {
                return text;
            }
        }
        return SettingEntry.formatIdentifier(fallbackKey);
    }

    private void processElement(
        AdapterContext ctx,
        ModEntry mod,
        Object rootConfig,
        Object element,
        String categoryPath,
        String subcategoryPath,
        Object colorOptionKey,
        Object rangeOptionKey,
        int depth
    ) {
        if (element == null || depth > 10) return;
        Object hidden = Reflect.invoke(element, "isHidden");
        if (Boolean.TRUE.equals(hidden)) return;

        Object entry = Reflect.invoke(element, "entry");
        if (entry == null) return;

        String elementId = String.valueOf(Reflect.invoke(element, "id"));
        Object entryTypeObj = Reflect.invoke(entry, "type");
        if (entryTypeObj == null) return;
        String entryType = entryTypeObj.toString();

        // Nested object entry -> recurse
        if ("OBJECT".equals(entryType)) {
            Object subElements = Reflect.invoke(entry, "elements");
            if (subElements instanceof List<?> list) {
                String subName = extractEntryTitle(entry, elementId);
                String nextCat = categoryPath.isEmpty() ? subName : categoryPath;
                String nextSub = categoryPath.isEmpty() ? "" : (subcategoryPath.isEmpty() ? subName : subcategoryPath + " / " + subName);
                for (Object child : list) {
                    processElement(ctx, mod, rootConfig, child, nextCat, nextSub, colorOptionKey, rangeOptionKey, depth + 1);
                }
            }
            return;
        }

        // Skip arrays for inline scalar controls
        Object isArray = Reflect.invoke(entry, "isArray");
        if (Boolean.TRUE.equals(isArray)) {
            return;
        }

        Object entryData = Reflect.invoke(entry, "options");
        String title = extractEntryTitle(entry, elementId);
        String comment = "";
        if (entryData != null) {
            comment = Reflect.extractText(Reflect.invoke(entryData, "comment"));
        }

        boolean isColor = false;
        if (entryData != null && colorOptionKey != null) {
            Object hasColor = Reflect.invoke(entryData, "hasOption", colorOptionKey);
            isColor = Boolean.TRUE.equals(hasColor);
        }

        double min = 0.0;
        double max = 100.0;
        double step = 1.0;
        if (entryData != null && rangeOptionKey != null) {
            Object rangeObj = Reflect.invoke(entryData, "getOption", rangeOptionKey);
            if (rangeObj != null) {
                Object rMin = Reflect.invoke(rangeObj, "min");
                Object rMax = Reflect.invoke(rangeObj, "max");
                if (rMin instanceof Number n) min = n.doubleValue();
                if (rMax instanceof Number n) max = n.doubleValue();
                step = ("FLOAT".equals(entryType) || "DOUBLE".equals(entryType)) ? Math.max(0.05, (max - min) / 100.0) : 1.0;
            }
        }

        SettingType mappedType = mapEntryType(entryType, isColor);
        if (mappedType == null) return;

        List<String> enumLabels = List.of();
        List<Object> enumValues = List.of();
        if (mappedType == SettingType.ENUM) {
            Object objType = Reflect.invoke(entry, "objectType");
            if (objType instanceof Class<?> cls && cls.isEnum()) {
                Object[] constants = cls.getEnumConstants();
                if (constants == null || constants.length == 0) return;
                List<String> labels = new ArrayList<>(constants.length);
                List<Object> values = new ArrayList<>(constants.length);
                for (Object c : constants) {
                    values.add(c);
                    labels.add(SettingEntry.formatIdentifier(c.toString()));
                }
                enumLabels = labels;
                enumValues = values;
            } else {
                return;
            }
        }

        SettingAccess access = createAccess(entry, rootConfig, mappedType);
        String fullCat = categoryPath.isEmpty() ? "General" : categoryPath;
        String uniqueId = mod.modId() + ":rc:" + fullCat + ":" + subcategoryPath + ":" + elementId;

        ctx.addSetting(SettingEntry.builder()
            .id(uniqueId)
            .modId(mod.modId())
            .modName(mod.displayName())
            .modAccentColor(mod.accentColor())
            .category(fullCat)
            .subcategory(subcategoryPath)
            .name(title)
            .description(comment)
            .searchTags(List.of(elementId, "resourcefulconfig"))
            .type(mappedType)
            .range(min, max, step)
            .enumOptions(enumLabels, enumValues)
            .access(access)
            .build());
    }

    private String extractEntryTitle(Object entry, String fallbackId) {
        Object entryData = Reflect.invoke(entry, "options");
        if (entryData != null) {
            String title = Reflect.extractText(Reflect.invoke(entryData, "title"));
            if (!title.isBlank()) {
                return title;
            }
        }
        return SettingEntry.formatIdentifier(fallbackId);
    }

    private SettingType mapEntryType(String entryType, boolean isColor) {
        if (isColor) return SettingType.COLOR;
        return switch (entryType) {
            case "BOOLEAN" -> SettingType.BOOLEAN;
            case "BYTE", "SHORT", "INTEGER", "LONG" -> SettingType.INT;
            case "FLOAT" -> SettingType.FLOAT;
            case "DOUBLE" -> SettingType.DOUBLE;
            case "STRING" -> SettingType.STRING;
            case "ENUM" -> SettingType.ENUM;
            default -> null;
        };
    }

    private SettingAccess createAccess(Object entry, Object rootConfig, SettingType type) {
        return new SettingAccess() {
            @Override
            public Object get() {
                return switch (type) {
                    case BOOLEAN -> Reflect.invoke(entry, "getBoolean");
                    case INT, COLOR -> Reflect.invoke(entry, "getInt");
                    case FLOAT -> Reflect.invoke(entry, "getFloat");
                    case DOUBLE -> Reflect.invoke(entry, "getDouble");
                    case STRING -> Reflect.invoke(entry, "getString");
                    case ENUM -> Reflect.invoke(entry, "getEnum");
                    case ACTION -> null;
                };
            }

            @Override
            public boolean set(Object value) {
                Object res = switch (type) {
                    case BOOLEAN -> value instanceof Boolean b ? Reflect.invoke(entry, "setBoolean", b) : Boolean.FALSE;
                    case INT, COLOR -> value instanceof Number n ? Reflect.invoke(entry, "setInt", n.intValue()) : Boolean.FALSE;
                    case FLOAT -> value instanceof Number n ? Reflect.invoke(entry, "setFloat", n.floatValue()) : Boolean.FALSE;
                    case DOUBLE -> value instanceof Number n ? Reflect.invoke(entry, "setDouble", n.doubleValue()) : Boolean.FALSE;
                    case STRING -> value instanceof String s ? Reflect.invoke(entry, "setString", s) : Boolean.FALSE;
                    case ENUM -> value instanceof Enum<?> e ? Reflect.invoke(entry, "setEnum", e) : Boolean.FALSE;
                    case ACTION -> Boolean.FALSE;
                };
                return Boolean.TRUE.equals(res);
            }

            @Override
            public Object defaultValue() {
                return Reflect.invoke(entry, "defaultValue");
            }

            @Override
            public void save() {
                Reflect.invoke(rootConfig, "save");
            }
        };
    }

    /**
     * Helper used by MeowddingOverlayAdapter to save a mod's ResourcefulConfig after HUD edits.
     */
    public static void saveConfigsForMod(String modId) {
        Class<?> configsClass = Reflect.findClass(CONFIGURATIONS_CLASS);
        if (configsClass == null) return;
        Object instance = Reflect.getStaticField(configsClass, "INSTANCE");
        if (instance == null) return;
        Object ids = Reflect.invoke(instance, "getConfigsForMod", modId);
        if (ids instanceof Set<?> set) {
            for (Object idObj : set) {
                if (idObj instanceof String configId) {
                    Object cfg = Reflect.invoke(instance, "getConfig", configId);
                    if (cfg != null) {
                        Reflect.invoke(cfg, "save");
                    }
                }
            }
        }
    }
}
