package dev.skyhub.ui;

import dev.skyhub.SkyHubClient;
import dev.skyhub.model.ModEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Safely opens a mod's native config menu or native HUD editor,
 * falling back to its client command if no direct Screen factory exists.
 */
public final class NativeMenuLauncher {
    private NativeMenuLauncher() {}

    public static boolean openNativeConfig(ModEntry mod, Screen parent) {
        if (mod == null) return false;
        Minecraft mc = Minecraft.getInstance();

        if (mod.nativeMenuOpener() != null) {
            try {
                Screen screen = mod.nativeMenuOpener().apply(parent);
                if (screen != null) {
                    mc.gui.setScreen(screen);
                    return true;
                }
            } catch (Throwable t) {
                SkyHubClient.LOGGER.warn("[SkyHub] Native menu opener failed for {}: {}", mod.modId(), t.toString());
            }
        }

        return runClientCommand(mod.nativeConfigCommand());
    }

    public static boolean openNativeHud(ModEntry mod) {
        if (mod == null) return false;

        if (mod.nativeHudOpener() != null) {
            try {
                mod.nativeHudOpener().run();
                return true;
            } catch (Throwable t) {
                SkyHubClient.LOGGER.warn("[SkyHub] Native HUD opener failed for {}: {}", mod.modId(), t.toString());
            }
        }

        return runClientCommand(mod.nativeHudCommand());
    }

    private static boolean runClientCommand(String command) {
        if (command == null || command.isBlank()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return false;
        }
        String clean = command.startsWith("/") ? command.substring(1) : command;
        mc.gui.setScreen(null);
        mc.getConnection().sendCommand(clean);
        return true;
    }
}
