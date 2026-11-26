package dev.skyhub.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.skyhub.ui.SkyHubSettingsScreen;

/**
 * Optional ModMenu integration opening SkyHub's Skyblocker-style vanilla settings screen.
 */
public final class SkyHubModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SkyHubSettingsScreen::new;
    }
}
