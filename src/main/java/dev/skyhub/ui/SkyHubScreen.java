package dev.skyhub.ui;

import dev.skyhub.model.AdapterHealth;
import dev.skyhub.model.ModCatalog;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingEntry;
import dev.skyhub.model.SettingType;
import dev.skyhub.registry.FuzzySearchIndex;
import dev.skyhub.registry.UnifiedRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 1 Core UI: Unified Mod Hub + Global Fuzzy Search + Direct Controls + Mod Switcher.
 * Writes directly to native mod configs with zero duplicate storage.
 */
public final class SkyHubScreen extends Screen {
    private static final int MARGIN = 14;
    private static final int HEADER_H = 42;
    private static final int FOOTER_H = 22;
    private static final int SIDEBAR_W = 178;
    private static final int MOD_ROW_H = 24;
    private static final int SETTING_ROW_H = 42;
    private static final int MOD_BANNER_H = 34;

    private final Screen parent;
    private EditBox searchBox;

    // Inline string editor state
    private SettingEntry editingStringEntry;
    private EditBox inlineStringBox;

    // Filter state
    private String selectedModId = null; // null = All Mods
    private boolean onlyCurrentlyOn = false;
    private boolean onlySkyBlockMods = true;

    // Scroll state
    private double sidebarScroll = 0;
    private double listScroll = 0;

    // Cached filtered views
    private List<ModEntry> visibleMods = List.of();
    private List<SettingEntry> filteredSettings = List.of();

    // Slider dragging state
    private SettingEntry draggingSliderEntry = null;
    private int draggingSliderX = 0;
    private int draggingSliderW = 1;

    // Toast feedback
    private String toastMessage = "";
    private long toastExpiresAt = 0L;

    // Layout rects (computed each frame / init)
    private int panelX, panelY, panelW, panelH;
    private int bodyY, bodyH;
    private int contentX, contentW;
    private int listY, listH;

    // Header button hitboxes
    private int btnOnX, btnOnW;
    private int btnScopeX, btnScopeW;
    private int btnHudX, btnHudW;
    private int btnRefreshX, btnRefreshW;

    public SkyHubScreen(Screen parent) {
        super(Component.translatable("skyhub.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        UnifiedRegistry.getInstance().ensureInitialized();
        computeLayout();

        String prevQuery = searchBox != null ? searchBox.getValue() : "";
        int searchX = panelX + 126;
        int searchW = Math.max(120, btnOnX - searchX - 8);
        int searchY = panelY + 11;

        searchBox = new EditBox(font, searchX, searchY, searchW, 20, Component.translatable("skyhub.search.placeholder"));
        searchBox.setMaxLength(120);
        searchBox.setValue(prevQuery);
        searchBox.setHint(Component.translatable("skyhub.search.placeholder"));
        searchBox.setResponder(q -> {
            listScroll = 0;
            rebuildFilteredViews();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        rebuildFilteredViews();
    }

    private void computeLayout() {
        panelX = MARGIN;
        panelY = MARGIN;
        panelW = Math.max(460, width - MARGIN * 2);
        panelH = Math.max(260, height - MARGIN * 2);

        bodyY = panelY + HEADER_H;
        bodyH = panelH - HEADER_H - FOOTER_H;
        contentX = panelX + SIDEBAR_W;
        contentW = panelW - SIDEBAR_W;

        // Right-aligned header pills
        int right = panelX + panelW - 10;
        btnRefreshW = 22;
        btnRefreshX = right - btnRefreshW;

        btnHudW = 78;
        btnHudX = btnRefreshX - 6 - btnHudW;

        btnScopeW = 64;
        btnScopeX = btnHudX - 6 - btnScopeW;

        btnOnW = 58;
        btnOnX = btnScopeX - 6 - btnOnW;
    }

    private void rebuildFilteredViews() {
        UnifiedRegistry reg = UnifiedRegistry.getInstance();

        List<ModEntry> allMods = reg.mods();
        List<ModEntry> modsOut = new ArrayList<>();
        for (ModEntry m : allMods) {
            if (onlySkyBlockMods && !m.isSkyBlockMod()) continue;
            modsOut.add(m);
        }
        visibleMods = modsOut;

        if (selectedModId != null) {
            boolean stillVisible = visibleMods.stream().anyMatch(m -> m.modId().equals(selectedModId));
            if (!stillVisible) {
                selectedModId = null;
            }
        }

        String query = searchBox != null ? searchBox.getValue() : "";
        filteredSettings = reg.search(new FuzzySearchIndex.FilterOptions(
            query,
            selectedModId,
            onlyCurrentlyOn,
            onlySkyBlockMods
        ));

        clampScrolls();
    }

    private void clampScrolls() {
        int maxSide = Math.max(0, (visibleMods.size() + 1) * MOD_ROW_H - bodyH + 12);
        sidebarScroll = Math.max(0, Math.min(maxSide, sidebarScroll));

        int bannerH = selectedModId != null ? MOD_BANNER_H : 0;
        int availListH = Math.max(40, bodyH - bannerH);
        int maxList = Math.max(0, filteredSettings.size() * SETTING_ROW_H - availListH + 12);
        listScroll = Math.max(0, Math.min(maxList, listScroll));
    }

    private void showToast(String message) {
        this.toastMessage = message;
        this.toastExpiresAt = System.currentTimeMillis() + 2500L;
    }

    private void closeInlineStringEditor(boolean commit) {
        if (editingStringEntry != null && inlineStringBox != null) {
            if (commit) {
                if (editingStringEntry.setString(inlineStringBox.getValue())) {
                    showToast("Saved " + editingStringEntry.name() + " (" + editingStringEntry.modName() + ")");
                }
            }
            removeWidget(inlineStringBox);
        }
        editingStringEntry = null;
        inlineStringBox = null;
    }

    private void openInlineStringEditor(SettingEntry entry, int x, int y, int w) {
        closeInlineStringEditor(true);
        editingStringEntry = entry;
        inlineStringBox = new EditBox(font, x, y, w, 18, Component.literal(entry.name()));
        inlineStringBox.setMaxLength(256);
        inlineStringBox.setValue(entry.getStringValue());
        addRenderableWidget(inlineStringBox);
        setFocused(inlineStringBox);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, SkyHubTheme.BACKDROP);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        computeLayout();
        extractBackground(g, mouseX, mouseY, partialTick);

        // Main outer frame
        SkyHubTheme.drawBox(g, panelX, panelY, panelW, panelH, SkyHubTheme.PANEL_BG, SkyHubTheme.BORDER_SUBTLE);

        // Header bar
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, bodyY, SkyHubTheme.SIDEBAR_BG);
        g.horizontalLine(panelX, panelX + panelW - 1, bodyY, SkyHubTheme.BORDER_SUBTLE);

        // Brand title & pack pill
        g.text(font, "SKYHUB", panelX + 12, panelY + 11, SkyHubTheme.ACCENT_CYAN, true);
        SkyHubTheme.drawBadge(g, font, panelX + 58, panelY + 10, "SBE 5.4.9", SkyHubTheme.ACCENT_AMBER);
        g.text(font, "MC 26.2", panelX + 12, panelY + 24, SkyHubTheme.TEXT_MUTED, false);

        // Header filter pills
        int pillY = panelY + 11;
        SkyHubTheme.drawPill(
            g, font, btnOnX, pillY, btnOnW, 20,
            "ON Only",
            onlyCurrentlyOn ? SkyHubTheme.ACCENT_GREEN_BG : SkyHubTheme.CARD_BG,
            onlyCurrentlyOn ? SkyHubTheme.ACCENT_GREEN : SkyHubTheme.BORDER_SUBTLE,
            onlyCurrentlyOn ? SkyHubTheme.ACCENT_GREEN : SkyHubTheme.TEXT_SECONDARY
        );
        SkyHubTheme.drawPill(
            g, font, btnScopeX, pillY, btnScopeW, 20,
            onlySkyBlockMods ? "SkyBlock" : "All Mods",
            onlySkyBlockMods ? SkyHubTheme.withAlpha(SkyHubTheme.ACCENT_CYAN, 0x33) : SkyHubTheme.CARD_BG,
            onlySkyBlockMods ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.BORDER_SUBTLE,
            onlySkyBlockMods ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.TEXT_SECONDARY
        );
        SkyHubTheme.drawPill(
            g, font, btnHudX, pillY, btnHudW, 20,
            "HUD Canvas",
            SkyHubTheme.withAlpha(0xFFA855F7, 0x38),
            0xFFA855F7,
            SkyHubTheme.TEXT_PRIMARY
        );
        SkyHubTheme.drawPill(
            g, font, btnRefreshX, pillY, btnRefreshW, 20,
            "\u21BB",
            SkyHubTheme.CARD_BG,
            SkyHubTheme.BORDER_SUBTLE,
            SkyHubTheme.TEXT_SECONDARY
        );

        // Sidebar
        renderSidebar(g, mouseX, mouseY);

        // Vertical divider
        g.verticalLine(contentX, bodyY, bodyY + bodyH, SkyHubTheme.BORDER_SUBTLE);

        // Main content area
        renderContentArea(g, mouseX, mouseY);

        // Footer bar
        renderFooter(g);

        // Render vanilla EditBox widgets (searchBox & optional inlineStringBox)
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(panelX + 1, bodyY + 1, contentX, bodyY + bodyH, SkyHubTheme.SIDEBAR_BG);
        g.enableScissor(panelX + 1, bodyY + 1, contentX, bodyY + bodyH);

        int y = bodyY + 6 - (int) sidebarScroll;

        // "All Mods" entry
        boolean allSelected = selectedModId == null;
        boolean allHovered = mouseX >= panelX + 4 && mouseX < contentX - 4 && mouseY >= y && mouseY < y + MOD_ROW_H - 2
            && mouseY >= bodyY && mouseY < bodyY + bodyH;
        int allBg = allSelected ? SkyHubTheme.CARD_SELECTED : (allHovered ? SkyHubTheme.CARD_HOVER : 0x00000000);
        if (allBg != 0) {
            SkyHubTheme.drawBox(g, panelX + 5, y, SIDEBAR_W - 10, MOD_ROW_H - 2, allBg,
                allSelected ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.BORDER_MUTED);
        }
        if (allSelected) {
            g.fill(panelX + 5, y + 2, panelX + 8, y + MOD_ROW_H - 4, SkyHubTheme.ACCENT_CYAN);
        }
        g.text(font, "All Mods", panelX + 14, y + 7, SkyHubTheme.TEXT_PRIMARY, false);
        String totalStr = String.valueOf(UnifiedRegistry.getInstance().settings().size());
        g.text(font, totalStr, contentX - 12 - font.width(totalStr), y + 7, SkyHubTheme.TEXT_MUTED, false);
        y += MOD_ROW_H + 2;

        g.horizontalLine(panelX + 10, contentX - 10, y - 2, SkyHubTheme.BORDER_MUTED);

        for (ModEntry mod : visibleMods) {
            if (y + MOD_ROW_H >= bodyY && y <= bodyY + bodyH) {
                boolean selected = mod.modId().equals(selectedModId);
                boolean hovered = mouseX >= panelX + 4 && mouseX < contentX - 4 && mouseY >= y && mouseY < y + MOD_ROW_H - 2
                    && mouseY >= bodyY && mouseY < bodyY + bodyH;

                if (selected || hovered) {
                    SkyHubTheme.drawBox(
                        g, panelX + 5, y, SIDEBAR_W - 10, MOD_ROW_H - 2,
                        selected ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.CARD_HOVER,
                        selected ? mod.accentColor() : SkyHubTheme.BORDER_MUTED
                    );
                }

                // Accent bar or dot
                g.fill(panelX + 8, y + 5, panelX + 11, y + MOD_ROW_H - 7, mod.accentColor());

                // Right count or fallback symbol
                String badgeText = mod.settingCount() > 0 ? String.valueOf(mod.settingCount())
                    : (mod.hudWidgetCount() > 0 ? "HUD" : (mod.hasNativeMenu() ? "\u2197" : "\u2022"));
                int badgeColor = mod.health() == AdapterHealth.HEALTHY ? SkyHubTheme.TEXT_SECONDARY
                    : (mod.hasNativeMenu() ? SkyHubTheme.ACCENT_AMBER : SkyHubTheme.TEXT_MUTED);
                int badgeW = font.width(badgeText);
                g.text(font, badgeText, contentX - 10 - badgeW, y + 7, badgeColor, false);

                // Mod name
                int maxNameW = SIDEBAR_W - 30 - badgeW;
                String displayName = SkyHubTheme.truncate(font, mod.displayName(), maxNameW);
                g.text(font, displayName, panelX + 16, y + 7,
                    selected ? SkyHubTheme.TEXT_PRIMARY : SkyHubTheme.TEXT_SECONDARY, false);
            }
            y += MOD_ROW_H;
        }

        g.disableScissor();
    }

    private void renderContentArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ModEntry selectedMod = selectedModId != null ? UnifiedRegistry.getInstance().mod(selectedModId) : null;
        int bannerH = selectedMod != null ? MOD_BANNER_H : 0;

        if (selectedMod != null) {
            renderSelectedModBanner(g, selectedMod, mouseX, mouseY);
        }

        listY = bodyY + bannerH;
        listH = bodyH - bannerH;

        if (filteredSettings.isEmpty()) {
            renderEmptyOrFallbackState(g, selectedMod, mouseX, mouseY);
            return;
        }

        g.enableScissor(contentX + 1, listY, panelX + panelW - 1, listY + listH);

        SettingEntry hoveredForTooltip = null;
        int y = listY + 6 - (int) listScroll;
        int rowX = contentX + 10;
        int rowW = contentW - 24;

        for (SettingEntry entry : filteredSettings) {
            if (y + SETTING_ROW_H >= listY && y <= listY + listH) {
                boolean rowHovered = mouseX >= rowX && mouseX <= rowX + rowW
                    && mouseY >= y && mouseY < y + SETTING_ROW_H - 4
                    && mouseY >= listY && mouseY < listY + listH;
                renderSettingRow(g, entry, rowX, y, rowW, SETTING_ROW_H - 4, rowHovered, mouseX, mouseY);
                if (rowHovered && !entry.description().isBlank()) {
                    hoveredForTooltip = entry;
                }
            }
            y += SETTING_ROW_H;
        }

        g.disableScissor();

        // Scrollbar
        int totalH = filteredSettings.size() * SETTING_ROW_H + 12;
        if (totalH > listH) {
            int trackX = panelX + panelW - 7;
            int thumbH = Math.max(20, (int) ((double) listH * listH / totalH));
            int maxScroll = totalH - listH;
            int thumbY = listY + 2 + (int) ((listScroll / maxScroll) * (listH - thumbH - 4));
            g.fill(trackX, listY + 2, trackX + 3, listY + listH - 2, SkyHubTheme.BORDER_MUTED);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, SkyHubTheme.BORDER_SUBTLE);
        }

        if (hoveredForTooltip != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hoveredForTooltip.name()));
            lines.add(Component.literal(hoveredForTooltip.modName() + " \u203A " + hoveredForTooltip.breadcrumb()));
            for (var seq : font.split(Component.literal(hoveredForTooltip.description()), 240)) {
                lines.add(Component.literal(extractSeq(seq)));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private void renderSelectedModBanner(GuiGraphicsExtractor g, ModEntry mod, int mouseX, int mouseY) {
        int bx = contentX + 1;
        int by = bodyY + 1;
        int bw = contentW - 2;
        g.fill(bx, by, bx + bw, by + MOD_BANNER_H - 1, SkyHubTheme.CARD_BG);
        g.horizontalLine(bx, bx + bw, by + MOD_BANNER_H - 1, SkyHubTheme.BORDER_SUBTLE);

        g.fill(bx + 8, by + 7, bx + 11, by + MOD_BANNER_H - 8, mod.accentColor());
        g.text(font, mod.displayName(), bx + 17, by + 6, SkyHubTheme.TEXT_PRIMARY, true);

        String meta = mod.adapterName() + " \u00B7 " + mod.settingCount() + " settings"
            + (mod.hudWidgetCount() > 0 ? " \u00B7 " + mod.hudWidgetCount() + " HUDs" : "");
        g.text(font, SkyHubTheme.truncate(font, meta, Math.max(80, bw - 210)), bx + 17, by + 19, SkyHubTheme.TEXT_MUTED, false);

        int right = bx + bw - 8;
        if (mod.hasNativeMenu()) {
            int w = 88;
            int x = right - w;
            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= by + 7 && mouseY <= by + 27;
            SkyHubTheme.drawPill(g, font, x, by + 7, w, 20, "Native Menu \u2197",
                hov ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.PANEL_BG,
                hov ? mod.accentColor() : SkyHubTheme.BORDER_SUBTLE,
                SkyHubTheme.TEXT_PRIMARY);
            right = x - 6;
        }
        if (mod.hasNativeHud()) {
            int w = 80;
            int x = right - w;
            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= by + 7 && mouseY <= by + 27;
            SkyHubTheme.drawPill(g, font, x, by + 7, w, 20, "Native HUD \u2197",
                hov ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.PANEL_BG,
                hov ? 0xFFA855F7 : SkyHubTheme.BORDER_SUBTLE,
                SkyHubTheme.TEXT_SECONDARY);
        }
    }

    private void renderEmptyOrFallbackState(GuiGraphicsExtractor g, ModEntry selectedMod, int mouseX, int mouseY) {
        int cx = contentX + contentW / 2;
        int cy = listY + listH / 2 - 26;

        if (selectedMod != null && selectedMod.settingCount() == 0) {
            int cardW = Math.min(340, contentW - 40);
            int cardH = 96;
            int cardX = cx - cardW / 2;
            SkyHubTheme.drawBox(g, cardX, cy, cardW, cardH, SkyHubTheme.CARD_BG, SkyHubTheme.BORDER_SUBTLE);
            g.centeredText(font, selectedMod.displayName() + " (Fallback Mode)", cx, cy + 14, SkyHubTheme.TEXT_PRIMARY);
            String detail = SkyHubTheme.truncate(font, selectedMod.statusDetail(), cardW - 24);
            g.centeredText(font, detail, cx, cy + 30, SkyHubTheme.TEXT_SECONDARY);

            if (selectedMod.hasNativeMenu()) {
                int bw = 160;
                int bx = cx - bw / 2;
                int by = cy + 54;
                boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 24;
                SkyHubTheme.drawPill(g, font, bx, by, bw, 24, "Open Native Menu \u2197",
                    hov ? SkyHubTheme.withAlpha(selectedMod.accentColor(), 0x55) : SkyHubTheme.withAlpha(selectedMod.accentColor(), 0x33),
                    selectedMod.accentColor(),
                    SkyHubTheme.TEXT_PRIMARY);
            }
        } else {
            g.centeredText(font, "No settings matched your current search/filters.", cx, cy + 16, SkyHubTheme.TEXT_SECONDARY);
            g.centeredText(font, "Try clearing the search query or disabling 'ON Only'.", cx, cy + 32, SkyHubTheme.TEXT_MUTED);
        }
    }

    private void renderSettingRow(
        GuiGraphicsExtractor g,
        SettingEntry entry,
        int x,
        int y,
        int w,
        int h,
        boolean hovered,
        int mouseX,
        int mouseY
    ) {
        SkyHubTheme.drawBox(
            g, x, y, w, h,
            hovered ? SkyHubTheme.CARD_HOVER : SkyHubTheme.CARD_BG,
            hovered ? SkyHubTheme.BORDER_SUBTLE : SkyHubTheme.BORDER_MUTED
        );

        // Left mod badge + category breadcrumb
        int curX = x + 8;
        if (selectedModId == null) {
            String shortTag = ModCatalog.shortLabel(entry.modId(), entry.modName());
            int badgeW = SkyHubTheme.drawBadge(g, font, curX, y + 5, shortTag, entry.modAccentColor());
            curX += badgeW + 6;
        }

        int controlAreaW = 148;
        int leftTextMaxW = Math.max(80, w - (curX - x) - controlAreaW - 14);

        // Top line: Setting Name + breadcrumb
        String title = SkyHubTheme.truncate(font, entry.name(), leftTextMaxW);
        g.text(font, title, curX, y + 6, SkyHubTheme.TEXT_PRIMARY, false);

        // Bottom line: Breadcrumb + short description
        String sub = entry.breadcrumb();
        if (!entry.description().isBlank()) {
            sub = sub + "  \u2022  " + entry.description();
        }
        g.text(font, SkyHubTheme.truncate(font, sub, w - 16 - controlAreaW), x + 8, y + 21, SkyHubTheme.TEXT_MUTED, false);

        // Control area on right
        int ctrlRight = x + w - 8;
        int ctrlY = y + (h - 20) / 2;

        // Optional Reset button if modified
        if (entry.isModified()) {
            int resetX = ctrlRight - 18;
            boolean rHov = mouseX >= resetX && mouseX <= resetX + 18 && mouseY >= ctrlY && mouseY <= ctrlY + 20;
            SkyHubTheme.drawPill(g, font, resetX, ctrlY, 18, 20, "\u21BA",
                rHov ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.PANEL_BG,
                rHov ? SkyHubTheme.ACCENT_AMBER : SkyHubTheme.BORDER_SUBTLE,
                rHov ? SkyHubTheme.ACCENT_AMBER : SkyHubTheme.TEXT_MUTED);
            ctrlRight = resetX - 5;
        }

        switch (entry.type()) {
            case BOOLEAN -> {
                boolean on = entry.getBooleanValue();
                int bw = 54;
                int bx = ctrlRight - bw;
                SkyHubTheme.drawPill(
                    g, font, bx, ctrlY, bw, 20,
                    on ? "ON" : "OFF",
                    on ? SkyHubTheme.ACCENT_GREEN_BG : SkyHubTheme.PANEL_BG,
                    on ? SkyHubTheme.ACCENT_GREEN : SkyHubTheme.BORDER_SUBTLE,
                    on ? SkyHubTheme.ACCENT_GREEN : SkyHubTheme.TEXT_MUTED
                );
            }
            case ENUM -> {
                int bw = 116;
                int bx = ctrlRight - bw;
                String val = SkyHubTheme.truncate(font, "\u25C2 " + entry.formattedValue() + " \u25B8", bw - 8);
                SkyHubTheme.drawPill(g, font, bx, ctrlY, bw, 20, val,
                    SkyHubTheme.PANEL_BG, SkyHubTheme.BORDER_SUBTLE, SkyHubTheme.ACCENT_CYAN);
            }
            case INT, FLOAT, DOUBLE -> {
                int minusW = 16;
                int plusW = 16;
                int sliderW = 84;
                int plusX = ctrlRight - plusW;
                int sliderX = plusX - 3 - sliderW;
                int minusX = sliderX - 3 - minusW;

                SkyHubTheme.drawPill(g, font, minusX, ctrlY, minusW, 20, "-",
                    SkyHubTheme.PANEL_BG, SkyHubTheme.BORDER_SUBTLE, SkyHubTheme.TEXT_SECONDARY);
                SkyHubTheme.drawPill(g, font, plusX, ctrlY, plusW, 20, "+",
                    SkyHubTheme.PANEL_BG, SkyHubTheme.BORDER_SUBTLE, SkyHubTheme.TEXT_SECONDARY);

                // Slider bar
                SkyHubTheme.drawBox(g, sliderX, ctrlY, sliderW, 20, SkyHubTheme.INPUT_BG, SkyHubTheme.BORDER_SUBTLE);
                double ratio = entry.max() > entry.min()
                    ? Math.max(0.0, Math.min(1.0, (entry.getDoubleValue() - entry.min()) / (entry.max() - entry.min())))
                    : 0.0;
                int fillW = (int) Math.round((sliderW - 2) * ratio);
                if (fillW > 0) {
                    g.fill(sliderX + 1, ctrlY + 1, sliderX + 1 + fillW, ctrlY + 19,
                        SkyHubTheme.withAlpha(entry.modAccentColor(), 0x66));
                }
                g.centeredText(font, entry.formattedValue(), sliderX + sliderW / 2, ctrlY + 6, SkyHubTheme.TEXT_PRIMARY);
            }
            case STRING -> {
                int bw = 120;
                int bx = ctrlRight - bw;
                if (editingStringEntry == entry && inlineStringBox != null) {
                    inlineStringBox.setX(bx);
                    inlineStringBox.setY(ctrlY + 1);
                    inlineStringBox.setWidth(bw);
                } else {
                    String preview = entry.getStringValue();
                    if (preview.isEmpty()) preview = "(empty)";
                    SkyHubTheme.drawPill(g, font, bx, ctrlY, bw, 20,
                        SkyHubTheme.truncate(font, preview, bw - 10),
                        SkyHubTheme.INPUT_BG, SkyHubTheme.BORDER_SUBTLE, SkyHubTheme.TEXT_SECONDARY);
                }
            }
            case COLOR -> {
                int bw = 78;
                int bx = ctrlRight - bw;
                int argb = entry.getColorValue();
                SkyHubTheme.drawBox(g, bx, ctrlY, bw, 20, SkyHubTheme.PANEL_BG, SkyHubTheme.BORDER_SUBTLE);
                g.fill(bx + 4, ctrlY + 4, bx + 16, ctrlY + 16, 0xFF000000 | (argb & 0xFFFFFF));
                g.outline(bx + 4, ctrlY + 4, 12, 12, SkyHubTheme.BORDER_SUBTLE);
                g.text(font, entry.formattedValue(), bx + 20, ctrlY + 6, SkyHubTheme.TEXT_SECONDARY, false);
            }
            case ACTION -> {
                int bw = 64;
                int bx = ctrlRight - bw;
                SkyHubTheme.drawPill(g, font, bx, ctrlY, bw, 20, "Run",
                    SkyHubTheme.PANEL_BG, SkyHubTheme.ACCENT_CYAN, SkyHubTheme.ACCENT_CYAN);
            }
        }
    }

    private void renderFooter(GuiGraphicsExtractor g) {
        int fy = panelY + panelH - FOOTER_H;
        g.fill(panelX + 1, fy, panelX + panelW - 1, panelY + panelH - 1, SkyHubTheme.SIDEBAR_BG);
        g.horizontalLine(panelX, panelX + panelW - 1, fy, SkyHubTheme.BORDER_SUBTLE);

        UnifiedRegistry reg = UnifiedRegistry.getInstance();
        String left = "Showing " + filteredSettings.size() + " / " + reg.settings().size()
            + " settings  \u00B7  " + reg.hudWidgets().size() + " HUDs";
        g.text(font, left, panelX + 10, fy + 7, SkyHubTheme.TEXT_MUTED, false);

        if (System.currentTimeMillis() < toastExpiresAt && !toastMessage.isBlank()) {
            g.centeredText(font, toastMessage, panelX + panelW / 2, fy + 7, SkyHubTheme.ACCENT_GREEN);
        }

        String hints = "Tab: HUD Canvas  \u00B7  Esc: Close";
        g.text(font, hints, panelX + panelW - 10 - font.width(hints), fy + 7, SkyHubTheme.TEXT_MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        // Check header pills first
        int pillY = panelY + 11;
        if (my >= pillY && my <= pillY + 20) {
            if (mx >= btnOnX && mx <= btnOnX + btnOnW) {
                onlyCurrentlyOn = !onlyCurrentlyOn;
                listScroll = 0;
                rebuildFilteredViews();
                return true;
            }
            if (mx >= btnScopeX && mx <= btnScopeX + btnScopeW) {
                onlySkyBlockMods = !onlySkyBlockMods;
                listScroll = 0;
                rebuildFilteredViews();
                return true;
            }
            if (mx >= btnHudX && mx <= btnHudX + btnHudW) {
                closeInlineStringEditor(true);
                minecraft.gui.setScreen(new UnifiedHudCanvasScreen(this));
                return true;
            }
            if (mx >= btnRefreshX && mx <= btnRefreshX + btnRefreshW) {
                closeInlineStringEditor(true);
                UnifiedRegistry.getInstance().refresh();
                rebuildFilteredViews();
                showToast("Refreshed mod registries");
                return true;
            }
        }

        // Sidebar click
        if (mx >= panelX + 4 && mx < contentX - 4 && my >= bodyY && my < bodyY + bodyH) {
            closeInlineStringEditor(true);
            int y = bodyY + 6 - (int) sidebarScroll;
            if (my >= y && my < y + MOD_ROW_H - 2) {
                selectedModId = null;
                listScroll = 0;
                rebuildFilteredViews();
                return true;
            }
            y += MOD_ROW_H + 2;
            for (ModEntry mod : visibleMods) {
                if (my >= y && my < y + MOD_ROW_H - 2) {
                    selectedModId = mod.modId();
                    listScroll = 0;
                    rebuildFilteredViews();
                    return true;
                }
                y += MOD_ROW_H;
            }
        }

        // Selected mod banner buttons
        ModEntry selectedMod = selectedModId != null ? UnifiedRegistry.getInstance().mod(selectedModId) : null;
        if (selectedMod != null && my >= bodyY + 7 && my <= bodyY + 27 && mx >= contentX) {
            int right = contentX + contentW - 9;
            if (selectedMod.hasNativeMenu()) {
                int w = 88;
                int x = right - w;
                if (mx >= x && mx <= x + w) {
                    closeInlineStringEditor(true);
                    if (!NativeMenuLauncher.openNativeConfig(selectedMod, this)) {
                        showToast("Could not open native menu for " + selectedMod.displayName());
                    }
                    return true;
                }
                right = x - 6;
            }
            if (selectedMod.hasNativeHud()) {
                int w = 80;
                int x = right - w;
                if (mx >= x && mx <= x + w) {
                    closeInlineStringEditor(true);
                    if (!NativeMenuLauncher.openNativeHud(selectedMod)) {
                        showToast("Native HUD requires being in-world");
                    }
                    return true;
                }
            }
        }

        // Fallback big button when selected mod has 0 settings
        if (selectedMod != null && filteredSettings.isEmpty() && selectedMod.hasNativeMenu()) {
            int cx = contentX + contentW / 2;
            int cy = listY + listH / 2 - 26;
            int bw = 160;
            int bx = cx - bw / 2;
            int by = cy + 54;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + 24) {
                if (!NativeMenuLauncher.openNativeConfig(selectedMod, this)) {
                    showToast("Native menu requires being in-world");
                }
                return true;
            }
        }

        // Let active EditBox handle clicks inside itself first
        if (super.mouseClicked(event, isDoubleClick)) {
            return true;
        }

        // Setting rows click
        if (mx >= contentX && mx < panelX + panelW && my >= listY && my < listY + listH) {
            int rowX = contentX + 10;
            int rowW = contentW - 24;
            int y = listY + 6 - (int) listScroll;

            for (SettingEntry entry : filteredSettings) {
                int rowH = SETTING_ROW_H - 4;
                if (my >= y && my < y + rowH && mx >= rowX && mx <= rowX + rowW) {
                    return handleSettingRowClick(entry, rowX, y, rowW, rowH, mx, my, button);
                }
                y += SETTING_ROW_H;
            }
        }

        closeInlineStringEditor(true);
        return false;
    }

    private boolean handleSettingRowClick(
        SettingEntry entry,
        int rowX,
        int rowY,
        int rowW,
        int rowH,
        double mx,
        double my,
        int button
    ) {
        int ctrlRight = rowX + rowW - 8;
        int ctrlY = rowY + (rowH - 20) / 2;

        // Reset button
        if (entry.isModified()) {
            int resetX = ctrlRight - 18;
            if (mx >= resetX && mx <= resetX + 18 && my >= ctrlY && my <= ctrlY + 20) {
                closeInlineStringEditor(false);
                if (entry.reset()) {
                    showToast("Reset " + entry.name() + " (" + entry.modName() + ")");
                    if (onlyCurrentlyOn) rebuildFilteredViews();
                }
                return true;
            }
            ctrlRight = resetX - 5;
        }

        switch (entry.type()) {
            case BOOLEAN -> {
                closeInlineStringEditor(true);
                if (entry.toggle()) {
                    showToast(entry.name() + ": " + entry.formattedValue() + " \u00B7 Saved to " + entry.modName());
                    if (onlyCurrentlyOn) rebuildFilteredViews();
                }
                return true;
            }
            case ENUM -> {
                closeInlineStringEditor(true);
                int delta = (button == 1) ? -1 : 1;
                if (entry.cycleEnum(delta)) {
                    showToast(entry.name() + ": " + entry.formattedValue() + " \u00B7 Saved to " + entry.modName());
                }
                return true;
            }
            case INT, FLOAT, DOUBLE -> {
                closeInlineStringEditor(true);
                int plusX = ctrlRight - 16;
                int sliderX = plusX - 3 - 84;
                int minusX = sliderX - 3 - 16;

                if (mx >= minusX && mx <= minusX + 16) {
                    if (entry.stepNumeric(-1)) {
                        showToast(entry.name() + ": " + entry.formattedValue() + " \u00B7 Saved to " + entry.modName());
                    }
                    return true;
                }
                if (mx >= plusX && mx <= plusX + 16) {
                    if (entry.stepNumeric(1)) {
                        showToast(entry.name() + ": " + entry.formattedValue() + " \u00B7 Saved to " + entry.modName());
                    }
                    return true;
                }
                if (mx >= sliderX && mx <= sliderX + 84) {
                    draggingSliderEntry = entry;
                    draggingSliderX = sliderX;
                    draggingSliderW = 84;
                    updateDraggedSlider(mx);
                    return true;
                }
                return false;
            }
            case STRING -> {
                int bw = 120;
                int bx = ctrlRight - bw;
                openInlineStringEditor(entry, bx, ctrlY + 1, bw);
                return true;
            }
            case ACTION -> {
                entry.triggerAction();
                showToast("Triggered " + entry.name());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void updateDraggedSlider(double mx) {
        if (draggingSliderEntry == null) return;
        double ratio = Math.max(0.0, Math.min(1.0, (mx - draggingSliderX) / draggingSliderW));
        double raw = draggingSliderEntry.min() + ratio * (draggingSliderEntry.max() - draggingSliderEntry.min());
        if (draggingSliderEntry.step() > 0) {
            raw = Math.round(raw / draggingSliderEntry.step()) * draggingSliderEntry.step();
        }
        if (draggingSliderEntry.setNumeric(raw)) {
            showToast(draggingSliderEntry.name() + ": " + draggingSliderEntry.formattedValue()
                + " \u00B7 Saved to " + draggingSliderEntry.modName());
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingSliderEntry != null) {
            updateDraggedSlider(event.x());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSliderEntry != null) {
            draggingSliderEntry = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX < contentX && mouseY >= bodyY && mouseY < bodyY + bodyH) {
            sidebarScroll -= scrollY * 22.0;
            clampScrolls();
            return true;
        }
        if (mouseX >= contentX && mouseX < panelX + panelW && mouseY >= listY && mouseY < listY + listH) {
            listScroll -= scrollY * 32.0;
            clampScrolls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (inlineStringBox != null && inlineStringBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                closeInlineStringEditor(true);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeInlineStringEditor(false);
                return true;
            }
            return super.keyPressed(event);
        }

        if (key == GLFW.GLFW_KEY_TAB) {
            minecraft.gui.setScreen(new UnifiedHudCanvasScreen(this));
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            setFocused(searchBox);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inlineStringBox != null && inlineStringBox.isFocused()) {
            return super.charTyped(event);
        }
        if (searchBox != null && !searchBox.isFocused()) {
            setFocused(searchBox);
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        closeInlineStringEditor(true);
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String extractSeq(net.minecraft.util.FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((idx, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}
