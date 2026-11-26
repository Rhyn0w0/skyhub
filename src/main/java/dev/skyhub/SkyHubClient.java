package dev.skyhub;

import dev.skyhub.adapter.fallback.FallbackModMenuAdapter;
import dev.skyhub.adapter.framework.MeowddingOverlayAdapter;
import dev.skyhub.adapter.framework.MidnightLibAdapter;
import dev.skyhub.adapter.framework.ResourcefulConfigAdapter;
import dev.skyhub.adapter.mod.OdinAdapter;
import dev.skyhub.adapter.mod.SkyHanniAdapter;
import dev.skyhub.adapter.mod.SkyblockerAdapter;
import dev.skyhub.adapter.mod.StellaAdapter;
import dev.skyhub.config.SkyHubConfig;
import dev.skyhub.registry.UnifiedRegistry;
import dev.skyhub.ui.SkyHubScreen;
import dev.skyhub.ui.SkyHubSettingsScreen;
import dev.skyhub.ui.UnifiedHudCanvasScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SkyHub client entrypoint for SkyBlock Enhanced 5.4.9 (Minecraft 26.2 / Fabric).
 */
public final class SkyHubClient implements ClientModInitializer {
    public static final String MOD_ID = "skyhub";
    public static final Logger LOGGER = LoggerFactory.getLogger("SkyHub");

    private static KeyMapping openHubKey;
    private static KeyMapping openHudKey;

    @Override
    public void onInitializeClient() {
        SkyHubConfig.load();
        registerAdapters();
        registerKeyMappings();
        registerCommands();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.gui.screen() != null) return;
            while (openHubKey.consumeClick()) {
                client.gui.setScreen(new SkyHubScreen(null));
            }
            while (openHudKey.consumeClick()) {
                client.gui.setScreen(new UnifiedHudCanvasScreen(null));
            }
        });

        LOGGER.info("[SkyHub] Initialized for SkyBlock Enhanced 5.4.9 (MC 26.2). Press '\\' or run /skyhub to open.");
    }

    private void registerAdapters() {
        UnifiedRegistry registry = UnifiedRegistry.getInstance();
        registry.clearAdapters();

        // Tier-2: Mod-specific adapters (custom registries & HUD positions)
        registry.registerAdapter(new SkyHanniAdapter());
        registry.registerAdapter(new SkyblockerAdapter());
        registry.registerAdapter(new OdinAdapter());
        registry.registerAdapter(new StellaAdapter());

        // Tier-1: Framework adapters (cover multiple mods in one pass)
        registry.registerAdapter(new MeowddingOverlayAdapter());
        registry.registerAdapter(new ResourcefulConfigAdapter());
        registry.registerAdapter(new MidnightLibAdapter());

        // Tier-3: Universal fallback (ModMenu & native command openers)
        registry.registerAdapter(new FallbackModMenuAdapter());
    }

    private void registerKeyMappings() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openHubKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.skyhub.open_hub",
            GLFW.GLFW_KEY_BACKSLASH,
            category
        ));
        openHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.skyhub.open_hud",
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            category
        ));
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommands.literal("skyhub")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new SkyHubScreen(null)));
                    return 1;
                })
                .then(ClientCommands.literal("config").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new SkyHubScreen(null)));
                    return 1;
                }))
                .then(ClientCommands.literal("hud").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new UnifiedHudCanvasScreen(null)));
                    return 1;
                }))
                .then(ClientCommands.literal("settings").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new SkyHubSettingsScreen(null)));
                    return 1;
                }))
                .then(ClientCommands.literal("reload").executes(ctx -> {
                    UnifiedRegistry.getInstance().refresh();
                    int settings = UnifiedRegistry.getInstance().settings().size();
                    int huds = UnifiedRegistry.getInstance().hudWidgets().size();
                    int mods = UnifiedRegistry.getInstance().mods().size();
                    ctx.getSource().sendFeedback(Component.literal(
                        "\u00A7b[SkyHub]\u00A7r Reloaded " + settings + " settings & " + huds + " HUDs across " + mods + " mods."
                    ));
                    return 1;
                }));

            var node = dispatcher.register(root);
            dispatcher.register(ClientCommands.literal("shhub").redirect(node));
        });
    }
}
