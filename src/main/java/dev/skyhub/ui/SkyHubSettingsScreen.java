package dev.skyhub.ui;

import dev.skyhub.SkyHubClient;
import dev.skyhub.config.SkyHubConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Skyblocker-style vanilla settings screen for SkyHub itself.
 * Accessible ONLY via ModMenu or /skyhub settings.
 * Uses native vanilla Button widgets and Screen background so resource/texture packs
 * skin every button and frame identically to Skyblocker and vanilla Minecraft menus.
 */
public final class SkyHubSettingsScreen extends Screen {
    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(SkyHubClient.MOD_ID, "icon.png");
    private static final String SOURCE_URL = "https://github.com/Rhyn0w0/skyhub";
    private static final String ISSUES_URL = "https://github.com/Rhyn0w0/skyhub/issues";
    private static final String MODRINTH_URL = "https://modrinth.com/mod/skyhub";

    private static final int FULL_WIDTH = 308;
    private static final int HALF_WIDTH = 150;
    private static final int GAP = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_STEP = 24;

    private final Screen parent;
    private final String modVersion;

    private Button groupModeButton;
    private Button modScopeButton;
    private Button hudSnapButton;

    public SkyHubSettingsScreen(Screen parent) {
        super(Component.literal("SkyHub"));
        this.parent = parent;
        this.modVersion = FabricLoader.getInstance()
            .getModContainer(SkyHubClient.MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("0.1.0");
    }

    @Override
    protected void init() {
        int left = (width - FULL_WIDTH) / 2;
        int right = left + HALF_WIDTH + GAP;
        int totalRowsHeight = 6 * ROW_STEP;
        int startY = Math.max(54, (height - totalRowsHeight) / 2 + 6);

        SkyHubConfig cfg = SkyHubConfig.get();

        // Row 0: Open Settings Hub... (full width, matching Skyblocker's "Open Config...")
        addRenderableWidget(Button.builder(
            Component.translatable("skyhub.settings.open_hub"),
            btn -> minecraft.gui.setScreen(new SkyHubScreen(this))
        ).bounds(left, startY, FULL_WIDTH, BUTTON_HEIGHT).build());

        // Row 1: Group By (Mod / Feature Type) | Mod Scope (SkyBlock / All Mods)
        int y1 = startY + ROW_STEP;
        groupModeButton = addRenderableWidget(Button.builder(
            groupModeLabel(cfg),
            btn -> {
                cfg.setGroupMode(cfg.groupMode().next());
                SkyHubConfig.save();
                btn.setMessage(groupModeLabel(cfg));
            }
        ).bounds(left, y1, HALF_WIDTH, BUTTON_HEIGHT).build());

        modScopeButton = addRenderableWidget(Button.builder(
            modScopeLabel(cfg),
            btn -> {
                cfg.setOnlySkyBlockMods(!cfg.onlySkyBlockMods());
                SkyHubConfig.save();
                btn.setMessage(modScopeLabel(cfg));
            }
        ).bounds(right, y1, HALF_WIDTH, BUTTON_HEIGHT).build());

        // Row 2: HUD Snapping | Open HUD Canvas...
        int y2 = startY + ROW_STEP * 2;
        hudSnapButton = addRenderableWidget(Button.builder(
            hudSnapLabel(cfg),
            btn -> {
                cfg.setHudSnappingDefault(!cfg.hudSnappingDefault());
                SkyHubConfig.save();
                btn.setMessage(hudSnapLabel(cfg));
            }
        ).bounds(left, y2, HALF_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.translatable("skyhub.settings.open_hud"),
            btn -> minecraft.gui.setScreen(new UnifiedHudCanvasScreen(this))
        ).bounds(right, y2, HALF_WIDTH, BUTTON_HEIGHT).build());

        // Row 3: Open Config Folder | Report Bugs
        int y3 = startY + ROW_STEP * 3;
        addRenderableWidget(Button.builder(
            Component.translatable("skyhub.settings.open_config_folder"),
            btn -> Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir())
        ).bounds(left, y3, HALF_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.translatable("skyhub.settings.report_bugs"),
            btn -> ConfirmLinkScreen.confirmLinkNow(this, ISSUES_URL, true)
        ).bounds(right, y3, HALF_WIDTH, BUTTON_HEIGHT).build());

        // Row 4: Source | Modrinth
        int y4 = startY + ROW_STEP * 4;
        addRenderableWidget(Button.builder(
            Component.translatable("skyhub.settings.source"),
            btn -> ConfirmLinkScreen.confirmLinkNow(this, SOURCE_URL, true)
        ).bounds(left, y4, HALF_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.translatable("skyhub.settings.modrinth"),
            btn -> ConfirmLinkScreen.confirmLinkNow(this, MODRINTH_URL, true)
        ).bounds(right, y4, HALF_WIDTH, BUTTON_HEIGHT).build());

        // Row 5: Done (full width)
        int y5 = startY + ROW_STEP * 5 + 4;
        addRenderableWidget(Button.builder(
            CommonComponents.GUI_DONE,
            btn -> onClose()
        ).bounds(left, y5, FULL_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // Skyblocker-style centered icon + "SkyHub <version>" header
        String headerText = "SkyHub " + modVersion;
        int iconSize = 24;
        int spacing = 8;
        int textW = font.width(headerText);
        int totalW = iconSize + spacing + textW;

        int totalRowsHeight = 6 * ROW_STEP;
        int startY = Math.max(54, (height - totalRowsHeight) / 2 + 6);
        int headerY = startY - 36;
        int headerX = (width - totalW) / 2;

        g.blit(RenderPipelines.GUI_TEXTURED, ICON_TEXTURE, headerX, headerY, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize);
        g.text(font, headerText, headerX + iconSize + spacing, headerY + (iconSize - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
    }

    private static Component groupModeLabel(SkyHubConfig cfg) {
        return Component.literal("Group By: " + cfg.groupMode().label());
    }

    private static Component modScopeLabel(SkyHubConfig cfg) {
        return Component.literal("Mods: " + (cfg.onlySkyBlockMods() ? "SkyBlock" : "All"));
    }

    private static Component hudSnapLabel(SkyHubConfig cfg) {
        return Component.literal("HUD Snap: " + (cfg.hudSnappingDefault() ? "ON" : "OFF"));
    }

    @Override
    public void onClose() {
        SkyHubConfig.save();
        minecraft.gui.setScreen(parent);
    }
}
