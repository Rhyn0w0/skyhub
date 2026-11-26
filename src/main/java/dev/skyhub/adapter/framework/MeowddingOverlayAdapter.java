package dev.skyhub.adapter.framework;

import dev.skyhub.adapter.AdapterContext;
import dev.skyhub.adapter.ModAdapter;
import dev.skyhub.model.HudWidget;
import dev.skyhub.model.ModEntry;
import dev.skyhub.util.Reflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tier-1 Framework HUD Adapter for Meowdding-Lib Overlays.
 * Covers SkyOcean and CustomScoreboard HUD widgets in one shot, writing directly
 * to each Overlay's live Position and saving through ResourcefulConfig.
 */
public final class MeowddingOverlayAdapter implements ModAdapter {
    private static final String OVERLAYS_CLASS = "me.owdding.lib.overlays.Overlays";
    private static final String EDIT_SCREEN_CLASS = "me.owdding.lib.overlays.EditOverlaysScreen";

    @Override
    public String name() {
        return "Meowdding Overlays";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public void collect(AdapterContext ctx) {
        Class<?> overlaysClass = Reflect.findClass(OVERLAYS_CLASS);
        if (overlaysClass == null) return;

        Object overlaysListObj = Reflect.getStaticField(overlaysClass, "overlays");
        if (!(overlaysListObj instanceof List<?> overlays)) return;

        Set<String> seenMods = new HashSet<>();

        for (int i = 0; i < overlays.size(); i++) {
            Object overlay = overlays.get(i);
            if (overlay == null) continue;

            try {
                Object modIdObj = Reflect.invoke(overlay, "getModId");
                String modId = modIdObj instanceof String s && !s.isBlank() ? s : "skyocean";
                ModEntry mod = ctx.getOrCreateMod(modId);

                if (seenMods.add(modId)) {
                    registerNativeHudScreen(ctx, modId);
                }

                String overlayName = Reflect.extractText(Reflect.invoke(overlay, "getName"));
                if (overlayName.isBlank()) {
                    overlayName = overlay.getClass().getSimpleName();
                }

                Object position = Reflect.invoke(overlay, "getPosition");
                if (position == null) continue;

                final String finalModId = modId;
                ctx.addHudWidget(HudWidget.builder()
                    .id(modId + ":meow:" + i + ":" + overlayName)
                    .modId(modId)
                    .modName(mod.displayName())
                    .modAccentColor(mod.accentColor())
                    .name(overlayName)
                    .category("Overlay")
                    .enabled(() -> Boolean.TRUE.equals(Reflect.invoke(overlay, "getEnabled")), null)
                    .position(
                        () -> toInt(Reflect.invoke(position, "getX"), 10),
                        () -> toInt(Reflect.invoke(position, "getY"), 10),
                        x -> Reflect.invoke(overlay, "setX", x),
                        y -> Reflect.invoke(overlay, "setY", y)
                    )
                    .size(
                        () -> extractBound(overlay, true),
                        () -> extractBound(overlay, false)
                    )
                    .scale(
                        () -> toDouble(Reflect.invoke(position, "getScale"), 1.0),
                        s -> Reflect.invoke(overlay, "setScale", (float) s)
                    )
                    .resetHook(() -> Reflect.invoke(position, "resetPosition"))
                    .saveHook(() -> ResourcefulConfigAdapter.saveConfigsForMod(finalModId))
                    .build());
            } catch (Throwable ignored) {
            }
        }
    }

    private void registerNativeHudScreen(AdapterContext ctx, String modId) {
        Class<?> editClass = Reflect.findClass(EDIT_SCREEN_CLASS);
        if (editClass == null) return;
        ctx.registerNativeHud(modId, () -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                Constructor<?> ctor = editClass.getConstructor(String.class, Screen.class);
                Object screen = ctor.newInstance(modId, mc.gui.screen());
                if (screen instanceof Screen s) {
                    mc.gui.setScreen(s);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private int extractBound(Object overlay, boolean width) {
        Object bounds = Reflect.invoke(overlay, "getBounds");
        if (bounds != null) {
            Object val = Reflect.invoke(bounds, width ? "getFirst" : "getSecond");
            int n = toInt(val, width ? 110 : 24);
            return Math.max(width ? 44 : 18, n);
        }
        return width ? 110 : 24;
    }

    private int toInt(Object obj, int fallback) {
        return obj instanceof Number n ? n.intValue() : fallback;
    }

    private double toDouble(Object obj, double fallback) {
        return obj instanceof Number n ? n.doubleValue() : fallback;
    }
}
