package dev.skyhub.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.skyhub.ui.SkyHubScreen;

/**
 * Optional ModMenu integration allowing users to launch SkyHub directly from ModMenu.
 */
public final class SkyHubModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SkyHubScreen::new;
    }
}
