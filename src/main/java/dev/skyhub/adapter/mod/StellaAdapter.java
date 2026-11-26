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
 * Tier-2 Mod-Specific Adapter for Stella (v1.0.6+26.2).
 * Indexes Stella's DSL config tree and HUDManager elements directly.
 */
public final class StellaAdapter implements ModAdapter {
    private static final String MOD_ID = "stella";
    private static final String CONFIG_KT_CLASS = "co.stellarskys.stella.utils.ConfigKt";
    private static final String HUD_MANAGER_CLASS = "co.stellarskys.stella.hud.HUDManager";

    @Override
    public String name() {
        return "Stella";
    }

    @Override
    public int priority() {
        return 25;
    }

    @Override
    public void collect(AdapterContext ctx) {
        if (!ctx.isModLoaded(MOD_ID)) return;

        try {
            ModEntry mod = ctx.getOrCreateMod(MOD_ID);
            int settingsAdded = collectConfig(ctx, mod);
            int hudsAdded = collectHuds(ctx, mod);

            if (settingsAdded > 0 || hudsAdded > 0) {
                ctx.markHealthy(MOD_ID, name(), settingsAdded + " settings, " + hudsAdded + " HUDs");
            }
        } catch (Throwable t) {
            ctx.reportFailure(MOD_ID, name(), t);
        }
    }

    private int collectConfig(AdapterContext ctx, ModEntry mod) {
        Class<?> cfgKtClass = Reflect.findClass(CONFIG_KT_CLASS);
        if (cfgKtClass == null) return 0;

        Object config = Reflect.invokeStatic(cfgKtClass, "getConfig");
        if (config == null) return 0;

        Reflect.invoke(config, "ensureLoaded");
        Runnable saveHook = () -> Reflect.invoke(config, "save");

        Object catsObj = Reflect.getField(config, "categories");
        if (!(catsObj instanceof Map<?, ?> categories)) return 0;

        int added = 0;
        for (Object cat : categories.values()) {
            if (cat == null) continue;
            String catName = Reflect.stripFormatting(String.valueOf(Reflect.invoke(cat, "getName")));
            Object subsObj = Reflect.invoke(cat, "getSubcategories");
            if (!(subsObj instanceof Map<?, ?> subs)) continue;

            for (Object sub : subs.values()) {
                if (sub == null) continue;
                String subName = Reflect.stripFormatting(String.valueOf(Reflect.getField(sub, "subName")));
                Object elemsObj = Reflect.getField(sub, "elements");
                if (!(elemsObj instanceof Map<?, ?> elements)) continue;

                for (Object elem : elements.values()) {
                    if (elem == null) continue;
                    if (collectElement(ctx, mod, catName, subName, elem, saveHook)) {
                        added++;
                    }
                }
            }
        }
        return added;
    }

    private boolean collectElement(
        AdapterContext ctx,
        ModEntry mod,
        String category,
        String subcategory,
        Object elem,
        Runnable saveHook
    ) {
        String simple = elem.getClass().getSimpleName();
        String key = String.valueOf(Reflect.invoke(elem, "getConfigName"));
        String name = Reflect.stripFormatting(String.valueOf(Reflect.invoke(elem, "getName")));
        String desc = Reflect.stripFormatting(String.valueOf(Reflect.invoke(elem, "getDescription")));
        if (name.isBlank()) name = SettingEntry.formatIdentifier(key);

        return switch (simple) {
            case "Toggle" -> {
                ctx.addSetting(SettingEntry.builder()
                    .id(MOD_ID + ":cfg:" + key)
                    .modId(MOD_ID)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .category(category)
                    .subcategory(subcategory)
                    .name(name)
                    .description(desc)
                    .searchTags(List.of("stella", key))
                    .type(SettingType.BOOLEAN)
                    .access(new SettingAccess() {
                        @Override public Object get() { return Reflect.invoke(elem, "getValue"); }
                        @Override public boolean set(Object v) {
                            Reflect.invoke(elem, "setValue", Boolean.TRUE.equals(v));
                            return true;
                        }
                        @Override public void save() { saveHook.run(); }
                    })
                    .build());
                yield true;
            }
            case "Slider", "StepSlider" -> {
                boolean isStep = "StepSlider".equals(simple);
                double min = toDouble(Reflect.getField(elem, "min"), 0.0);
                double max = toDouble(Reflect.getField(elem, "max"), 100.0);
                double step = toDouble(Reflect.getField(elem, "step"), isStep ? 1.0 : 0.1);
                if (max <= min) max = min + 100.0;

                ctx.addSetting(SettingEntry.builder()
                    .id(MOD_ID + ":cfg:" + key)
                    .modId(MOD_ID)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .category(category)
                    .subcategory(subcategory)
                    .name(name)
                    .description(desc)
                    .searchTags(List.of("stella", key))
                    .type(isStep ? SettingType.INT : SettingType.FLOAT)
                    .range(min, max, step > 0 ? step : 1.0)
                    .access(new SettingAccess() {
                        @Override public Object get() { return Reflect.invoke(elem, "getValue"); }
                        @Override public boolean set(Object v) {
                            if (!(v instanceof Number n)) return false;
                            Reflect.invoke(elem, "setValue", isStep ? n.intValue() : n.floatValue());
                            return true;
                        }
                        @Override public void save() { saveHook.run(); }
                    })
                    .build());
                yield true;
            }
            case "Dropdown" -> {
                Object optsObj = Reflect.getField(elem, "options");
                if (!(optsObj instanceof List<?> opts) || opts.isEmpty()) yield false;
                List<String> labels = new ArrayList<>(opts.size());
                List<Object> values = new ArrayList<>(opts.size());
                for (int i = 0; i < opts.size(); i++) {
                    labels.add(Reflect.stripFormatting(String.valueOf(opts.get(i))));
                    values.add(i);
                }
                ctx.addSetting(SettingEntry.builder()
                    .id(MOD_ID + ":cfg:" + key)
                    .modId(MOD_ID)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .category(category)
                    .subcategory(subcategory)
                    .name(name)
                    .description(desc)
                    .searchTags(List.of("stella", key))
                    .type(SettingType.ENUM)
                    .enumOptions(labels, values)
                    .access(new SettingAccess() {
                        @Override public Object get() { return Reflect.invoke(elem, "getValue"); }
                        @Override public boolean set(Object v) {
                            if (v instanceof Number n) {
                                Reflect.invoke(elem, "setValue", n.intValue());
                                return true;
                            }
                            return false;
                        }
                        @Override public void save() { saveHook.run(); }
                    })
                    .build());
                yield true;
            }
            case "TextInput" -> {
                ctx.addSetting(SettingEntry.builder()
                    .id(MOD_ID + ":cfg:" + key)
                    .modId(MOD_ID)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .category(category)
                    .subcategory(subcategory)
                    .name(name)
                    .description(desc)
                    .searchTags(List.of("stella", key))
                    .type(SettingType.STRING)
                    .access(new SettingAccess() {
                        @Override public Object get() { return Reflect.invoke(elem, "getValue"); }
                        @Override public boolean set(Object v) {
                            Reflect.invoke(elem, "setValue", String.valueOf(v));
                            return true;
                        }
                        @Override public void save() { saveHook.run(); }
                    })
                    .build());
                yield true;
            }
            default -> false;
        };
    }

    private int collectHuds(AdapterContext ctx, ModEntry mod) {
        Class<?> hmClass = Reflect.findClass(HUD_MANAGER_CLASS);
        if (hmClass == null) return 0;

        Object hm = Reflect.getStaticField(hmClass, "INSTANCE");
        if (hm == null) return 0;

        Object elemsObj = Reflect.invoke(hm, "getElements");
        if (!(elemsObj instanceof Map<?, ?> elements)) return 0;

        Runnable saveHud = () -> Reflect.invoke(hm, "saveAllLayouts");
        int count = 0;

        for (Map.Entry<?, ?> entry : elements.entrySet()) {
            String id = String.valueOf(entry.getKey());
            Object elem = entry.getValue();
            if (elem == null) continue;

            ctx.addHudWidget(HudWidget.builder()
                .id(MOD_ID + ":hud:" + id)
                .modId(MOD_ID)
                .modName(mod.displayName())
                .modAccentColor(mod.accentColor())
                .name(SettingEntry.formatIdentifier(id))
                .category("HUD")
                .enabled(() -> Boolean.TRUE.equals(Reflect.invoke(elem, "isEnabled")), null)
                .position(
                    () -> (int) Math.round(toDouble(Reflect.invoke(elem, "getX"), 10.0)),
                    () -> (int) Math.round(toDouble(Reflect.invoke(elem, "getY"), 10.0)),
                    x -> Reflect.invoke(elem, "setX", (float) x),
                    y -> Reflect.invoke(elem, "setY", (float) y)
                )
                .size(
                    () -> Math.max(50, toInt(Reflect.invoke(elem, "getWidth"), 90)),
                    () -> Math.max(16, toInt(Reflect.invoke(elem, "getHeight"), 20))
                )
                .scale(
                    () -> toDouble(Reflect.invoke(elem, "getScale"), 1.0),
                    s -> Reflect.invoke(elem, "setScale", (float) s)
                )
                .saveHook(saveHud)
                .build());
            count++;
        }
        return count;
    }

    private int toInt(Object obj, int fallback) {
        return obj instanceof Number n ? n.intValue() : fallback;
    }

    private double toDouble(Object obj, double fallback) {
        return obj instanceof Number n ? n.doubleValue() : fallback;
    }
}
