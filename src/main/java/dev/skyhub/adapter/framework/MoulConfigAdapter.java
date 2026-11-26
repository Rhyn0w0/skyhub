package dev.skyhub.adapter.framework;

import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingAccess;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.model.SettingType;
import dev.skyhub.util.Reflect;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tier-1 Framework Adapter helper for MoulConfig (supports both standard
 * io.github.notenoughupdates.moulconfig and relocated packages such as
 * SkyHanni's at.hannibal2.skyhanni.deps.moulconfig).
 */
public final class MoulConfigAdapter {
    private MoulConfigAdapter() {}

    public static int collectProcessor(
        AdapterContext ctx,
        ModEntry mod,
        Object processor,
        Runnable saveHook
    ) {
        if (processor == null) return 0;

        Object categoriesObj = Reflect.invoke(processor, "getAllCategories");
        if (!(categoriesObj instanceof Map<?, ?> categories)) {
            return 0;
        }

        int added = 0;
        for (Map.Entry<?, ?> catEntry : categories.entrySet()) {
            Object category = catEntry.getValue();
            if (category == null) continue;

            String catName = Reflect.extractText(Reflect.invoke(category, "getDisplayName"));
            if (catName.isBlank()) {
                catName = SettingEntry.formatIdentifier(String.valueOf(catEntry.getKey()));
            }

            // Build accordionId -> accordion title map for clean subcategory breadcrumbs
            Map<?, ?> anchors = Reflect.invoke(category, "getAccordionAnchors") instanceof Map<?, ?> m ? m : Map.of();

            Object optionsObj = Reflect.invoke(category, "getOptions");
            if (!(optionsObj instanceof List<?> options)) continue;

            for (Object option : options) {
                if (option == null) continue;
                try {
                    if (collectOption(ctx, mod, catName, anchors, option, saveHook)) {
                        added++;
                    }
                } catch (Throwable ignored) {
                    // Isolate single malformed option so the rest of the category still indexes
                }
            }
        }
        return added;
    }

    private static boolean collectOption(
        AdapterContext ctx,
        ModEntry mod,
        String categoryName,
        Map<?, ?> accordionAnchors,
        Object option,
        Runnable saveHook
    ) {
        Object rawValue = Reflect.invoke(option, "get");
        Type declaredType = Reflect.invoke(option, "getType") instanceof Type t ? t : null;

        // Unwrap MoulConfig Property<T> if present
        boolean isProperty = isPropertyObject(rawValue);
        Object unwrapped = isProperty ? Reflect.invoke(rawValue, "get") : rawValue;
        Class<?> valueClass = resolveValueClass(unwrapped, declaredType);
        if (valueClass == null) {
            return false;
        }

        Object editor = Reflect.invoke(option, "getEditor");
        String editorSimpleName = editor != null ? editor.getClass().getSimpleName() : "";

        // Skip accordion headers & purely decorative/info editors
        if (editorSimpleName.contains("Accordion") || editorSimpleName.contains("InfoText")
            || editorSimpleName.contains("DraggableList") || editorSimpleName.contains("Keybind")) {
            return false;
        }

        SettingType settingType = mapType(valueClass, editorSimpleName);
        if (settingType == null) {
            return false;
        }

        String name = Reflect.extractText(Reflect.invoke(option, "getName"));
        String path = String.valueOf(Reflect.invoke(option, "getPath"));
        if (name.isBlank()) {
            name = SettingEntry.formatIdentifier(path);
        }
        String description = Reflect.extractText(Reflect.invoke(option, "getDescription"));

        // Subcategory from accordion anchor if nested
        String subcategory = "";
        Object accId = Reflect.invoke(option, "getAccordionId");
        if (accId instanceof Integer id && id >= 0 && accordionAnchors.containsKey(id)) {
            Object anchorOpt = accordionAnchors.get(id);
            subcategory = Reflect.extractText(Reflect.invoke(anchorOpt, "getName"));
        }

        // Extract @SearchTag[]
        List<String> tags = new ArrayList<>();
        tags.add("moulconfig");
        if (path != null && !path.isBlank()) tags.add(path);
        Object searchTagsObj = Reflect.invoke(option, "getSearchTags");
        if (searchTagsObj instanceof Object[] arr) {
            for (Object tagAnn : arr) {
                Object val = Reflect.invoke(tagAnn, "value");
                if (val instanceof String s && !s.isBlank()) {
                    tags.add(s);
                }
            }
        }

        // Slider range extraction
        double min = 0.0;
        double max = 100.0;
        double step = settingType == SettingType.INT ? 1.0 : 0.1;
        if (editor != null && editorSimpleName.contains("Slider")) {
            Object minF = Reflect.getField(editor, "minValue");
            Object maxF = Reflect.getField(editor, "maxValue");
            Object stepF = Reflect.getField(editor, "minStep");
            if (minF instanceof Number n) min = n.doubleValue();
            if (maxF instanceof Number n) max = n.doubleValue();
            if (stepF instanceof Number n && n.doubleValue() > 0) step = n.doubleValue();
            if (max <= min) max = min + 100.0;
        }

        // Enum options
        List<String> enumLabels = List.of();
        List<Object> enumValues = List.of();
        if (settingType == SettingType.ENUM && valueClass.isEnum()) {
            Object[] constants = valueClass.getEnumConstants();
            if (constants == null || constants.length == 0) return false;
            List<String> labels = new ArrayList<>(constants.length);
            List<Object> values = new ArrayList<>(constants.length);
            for (Object c : constants) {
                values.add(c);
                String label = Reflect.stripFormatting(c.toString());
                labels.add(label.isBlank() ? SettingEntry.formatIdentifier(((Enum<?>) c).name()) : label);
            }
            enumLabels = labels;
            enumValues = values;
        }

        SettingAccess access = new SettingAccess() {
            @Override
            public Object get() {
                Object cur = Reflect.invoke(option, "get");
                if (isPropertyObject(cur)) {
                    cur = Reflect.invoke(cur, "get");
                }
                if (settingType == SettingType.COLOR && cur != null && !(cur instanceof Number) && !(cur instanceof String)) {
                    // MoulConfig ChromaColour -> getEffectiveColourRGB()
                    Object rgb = Reflect.invoke(cur, "getEffectiveColourRGB");
                    if (rgb instanceof Number) return rgb;
                }
                return cur;
            }

            @Override
            public boolean set(Object value) {
                Object coerced = coerce(valueClass, value);
                Object cur = Reflect.invoke(option, "get");
                if (isPropertyObject(cur)) {
                    Reflect.invoke(cur, "set", coerced);
                    Reflect.invoke(option, "explicitNotifyChange");
                    return true;
                }
                Object ok = Reflect.invoke(option, "set", coerced);
                if (Boolean.TRUE.equals(ok)) {
                    Reflect.invoke(option, "explicitNotifyChange");
                    return true;
                }
                return false;
            }

            @Override
            public void save() {
                if (saveHook != null) {
                    saveHook.run();
                }
            }
        };

        ctx.addSetting(SettingEntry.builder()
            .id(mod.modId() + ":mc:" + path)
            .modId(mod.modId())
            .modName(mod.displayName())
            .modAccentColor(mod.accentColor())
            .category(categoryName)
            .subcategory(subcategory)
            .name(name)
            .description(description)
            .searchTags(tags)
            .type(settingType)
            .range(min, max, step)
            .enumOptions(enumLabels, enumValues)
            .access(access)
            .build());
        return true;
    }

    private static boolean isPropertyObject(Object obj) {
        if (obj == null) return false;
        String cls = obj.getClass().getName();
        return cls.endsWith(".Property") || cls.contains("moulconfig.observer.Property");
    }

    private static Class<?> resolveValueClass(Object value, Type declaredType) {
        if (value != null) {
            return value.getClass();
        }
        if (declaredType instanceof Class<?> c) {
            return c;
        }
        if (declaredType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1 && args[0] instanceof Class<?> inner) {
                return inner;
            }
        }
        return null;
    }

    private static SettingType mapType(Class<?> valueClass, String editorSimpleName) {
        if (editorSimpleName.contains("Colour") || editorSimpleName.contains("Color")
            || valueClass.getSimpleName().contains("ChromaColour")) {
            return SettingType.COLOR;
        }
        if (valueClass == Boolean.class || valueClass == boolean.class) return SettingType.BOOLEAN;
        if (valueClass == Integer.class || valueClass == int.class
            || valueClass == Long.class || valueClass == long.class) return SettingType.INT;
        if (valueClass == Float.class || valueClass == float.class) return SettingType.FLOAT;
        if (valueClass == Double.class || valueClass == double.class) return SettingType.DOUBLE;
        if (valueClass == String.class) return SettingType.STRING;
        if (valueClass.isEnum()) return SettingType.ENUM;
        return null;
    }

    private static Object coerce(Class<?> target, Object value) {
        if (value == null) return null;
        if ((target == Integer.class || target == int.class) && value instanceof Number n) return n.intValue();
        if ((target == Long.class || target == long.class) && value instanceof Number n) return n.longValue();
        if ((target == Float.class || target == float.class) && value instanceof Number n) return n.floatValue();
        if ((target == Double.class || target == double.class) && value instanceof Number n) return n.doubleValue();
        return value;
    }
}
