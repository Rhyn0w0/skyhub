package dev.skyhub.ui;

import dev.skyhub.config.SkyHubConfig;
import dev.skyhub.model.AdapterHealth;
import dev.skyhub.model.FeatureCategory;
import dev.skyhub.model.ModCatalog;
import dev.skyhub.model.ModEntry;
import dev.skyhub.model.SettingEntry;
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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1 Core UI: iPad-Settings-style Unified Mod Hub with recursive collapsible sections,
 * Global Fuzzy Search, Direct Inline Controls, and By Type / By Mod sidebar switching.
 */
public final class SkyHubScreen extends Screen {
    private static final int MARGIN = 14;
    private static final int HEADER_H = 42;
    private static final int FOOTER_H = 22;
    private static final int SIDEBAR_W = 184;
    private static final int SIDEBAR_ROW_H = 24;
    private static final int DETAIL_BANNER_H = 34;

    private final Screen parent;
    private EditBox searchBox;

    // Inline string editor state
    private SettingEntry editingStringEntry;
    private EditBox inlineStringBox;

    // Selection state (onlySkyBlockMods & groupMode live in SkyHubConfig)
    private FeatureCategory selectedCategory = FeatureCategory.MOD_SETTINGS;
    private String selectedModId = null;

    // Collapsible section state
    private final Set<String> expandedSections = new HashSet<>();
    private List<SettingTreeBuilder.SectionNode> sectionRoots = List.of();
    private List<SettingTreeBuilder.VisibleRow> visibleRows = List.of();
    private int totalRowsHeight = 0;

    // Scroll state
    private double sidebarScroll = 0;
    private double listScroll = 0;

    // Cached sidebar & search data
    private List<ModEntry> visibleMods = List.of();
    private List<CategoryBucket> visibleCategories = List.of();
    private int scopedTotalSettings = 0;
    private List<SettingEntry> filteredSettings = List.of();

    // Slider dragging state
    private SettingEntry draggingSliderEntry = null;
    private int draggingSliderX = 0;
    private int draggingSliderW = 1;

    // Toast feedback
    private String toastMessage = "";
    private long toastExpiresAt = 0L;

    // Layout rects
    private int panelX, panelY, panelW, panelH;
    private int bodyY, bodyH;
    private int sidebarListY, sidebarListH;
    private int contentX, contentW;
    private int listY, listH;

    // Header button hitboxes
    private int btnRefreshX, btnRefreshSize;

    private record CategoryBucket(FeatureCategory category, int count) {}

    public SkyHubScreen(Screen parent) {
        super(Component.translatable("skyhub.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        UnifiedRegistry.getInstance().ensureInitialized();
        computeLayout();

        String prevQuery = searchBox != null ? searchBox.getValue() : "";
        int searchX = panelX + 68;
        int searchW = Math.max(140, btnRefreshX - searchX - 8);
        int searchY = panelY + 11;

        searchBox = new EditBox(font, searchX, searchY, searchW, 20, Component.translatable("skyhub.search.placeholder"));
        searchBox.setMaxLength(120);
        searchBox.setValue(prevQuery);
        searchBox.setHint(Component.translatable("skyhub.search.placeholder"));
        searchBox.setResponder(q -> {
            listScroll = 0;
            rebuildFilteredViews(false);
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        rebuildFilteredViews(true);
    }

    private void computeLayout() {
        panelX = MARGIN;
        panelY = MARGIN;
        panelW = Math.max(460, width - MARGIN * 2);
        panelH = Math.max(260, height - MARGIN * 2);

        bodyY = panelY + HEADER_H;
        bodyH = panelH - HEADER_H - FOOTER_H;
        sidebarListY = bodyY + 1;
        sidebarListH = Math.max(40, bodyH - 1);
        contentX = panelX + SIDEBAR_W;
        contentW = panelW - SIDEBAR_W;

        // Right-aligned header control: [20x20 Square Reload]
        int right = panelX + panelW - 10;
        btnRefreshSize = 20;
        btnRefreshX = right - btnRefreshSize;
    }

    private boolean isByType() {
        return SkyHubConfig.get().groupMode() == SkyHubConfig.GroupMode.BY_TYPE;
    }

    private boolean isSearching() {
        return searchBox != null && !searchBox.getValue().trim().isEmpty();
    }

    private void rebuildFilteredViews(boolean resetExpansion) {
        UnifiedRegistry reg = UnifiedRegistry.getInstance();
        boolean onlySkyBlock = SkyHubConfig.get().onlySkyBlockMods();

        // Build visible mods list
        List<ModEntry> modsOut = new ArrayList<>();
        for (ModEntry m : reg.mods()) {
            if (onlySkyBlock && !m.isSkyBlockMod()) continue;
            modsOut.add(m);
        }
        visibleMods = modsOut;

        if (selectedModId == null || visibleMods.stream().noneMatch(m -> m.modId().equals(selectedModId))) {
            selectedModId = visibleMods.isEmpty() ? null : visibleMods.getFirst().modId();
        }

        // Build category buckets (Mod Settings first, then gameplay types)
        Map<FeatureCategory, Integer> counts = new EnumMap<>(FeatureCategory.class);
        int totalScoped = 0;
        for (SettingEntry s : reg.settings()) {
            if (onlySkyBlock && !ModCatalog.isSkyBlockMod(s.modId())) continue;
            totalScoped++;
            counts.merge(FeatureCategory.classify(s), 1, Integer::sum);
        }
        scopedTotalSettings = totalScoped;

        List<CategoryBucket> catsOut = new ArrayList<>();
        for (FeatureCategory cat : FeatureCategory.values()) {
            int c = counts.getOrDefault(cat, 0);
            if (cat == FeatureCategory.MOD_SETTINGS || c > 0) {
                catsOut.add(new CategoryBucket(cat, c));
            }
        }
        visibleCategories = catsOut;

        if (selectedCategory == null || visibleCategories.stream().noneMatch(b -> b.category() == selectedCategory)) {
            selectedCategory = visibleCategories.isEmpty() ? FeatureCategory.MOD_SETTINGS : visibleCategories.getFirst().category();
        }

        String query = searchBox != null ? searchBox.getValue().trim() : "";
        boolean searching = !query.isEmpty();
        boolean byType = isByType();

        // Global search searches across all scoped mods/categories; empty search browses selected sidebar section
        filteredSettings = reg.search(new FuzzySearchIndex.FilterOptions(
            query,
            (!searching && !byType) ? selectedModId : null,
            (!searching && byType) ? selectedCategory : null,
            false,
            onlySkyBlock
        ));

        boolean includeModRoot = searching || byType;
        FeatureCategory skipPrefix = (!searching && byType) ? selectedCategory : null;
        sectionRoots = SettingTreeBuilder.buildTree(filteredSettings, includeModRoot, skipPrefix);

        if (resetExpansion) {
            expandedSections.clear();
            // iPad-style smart initial expansion: expand top-level when concise (<= 3), collapse when many
            if (sectionRoots.size() <= 3) {
                for (SettingTreeBuilder.SectionNode root : sectionRoots) {
                    expandedSections.add(root.key());
                }
            }
        }

        rebuildVisibleRows();
    }

    private void rebuildVisibleRows() {
        visibleRows = SettingTreeBuilder.flatten(sectionRoots, expandedSections, isSearching());
        int total = 10;
        for (SettingTreeBuilder.VisibleRow row : visibleRows) {
            total += row.height();
            if (row instanceof SettingTreeBuilder.SectionHeaderRow hdr && hdr.depth() == 0) {
                total += 4; // subtle iPad section breathing room
            } else if (row instanceof SettingTreeBuilder.SettingItemRow item && item.lastInGroup()) {
                total += 4;
            }
        }
        totalRowsHeight = total;
        clampScrolls();
    }

    private void expandAllSections() {
        for (SettingTreeBuilder.SectionNode root : sectionRoots) {
            collectAllKeys(root, expandedSections);
        }
        rebuildVisibleRows();
    }

    private void collapseAllSections() {
        expandedSections.clear();
        listScroll = 0;
        rebuildVisibleRows();
    }

    private void collectAllKeys(SettingTreeBuilder.SectionNode node, Set<String> out) {
        out.add(node.key());
        for (SettingTreeBuilder.SectionNode child : node.children().values()) {
            collectAllKeys(child, out);
        }
    }

    private void clampScrolls() {
        int itemCount = isByType() ? visibleCategories.size() : visibleMods.size();
        int maxSide = Math.max(0, itemCount * SIDEBAR_ROW_H - sidebarListH + 16);
        sidebarScroll = Math.max(0, Math.min(maxSide, sidebarScroll));

        int availListH = Math.max(40, bodyH - DETAIL_BANNER_H);
        int maxList = Math.max(0, totalRowsHeight - availListH + 12);
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

        // Clean centered brand title
        g.text(font, "SKYHUB", panelX + 14, panelY + 17, SkyHubTheme.ACCENT_CYAN, true);

        // Header control: [20x20 Square Reload]
        int pillY = panelY + 11;
        boolean refreshHovered = mouseX >= btnRefreshX && mouseX <= btnRefreshX + btnRefreshSize
            && mouseY >= pillY && mouseY <= pillY + btnRefreshSize;
        SkyHubTheme.drawBox(
            g, btnRefreshX, pillY, btnRefreshSize, btnRefreshSize,
            refreshHovered ? SkyHubTheme.CARD_HOVER : SkyHubTheme.CARD_BG,
            refreshHovered ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.BORDER_SUBTLE
        );
        SkyHubTheme.drawRefreshIcon(
            g,
            btnRefreshX + btnRefreshSize / 2,
            pillY + btnRefreshSize / 2,
            refreshHovered ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.TEXT_SECONDARY
        );

        // Sidebar
        renderSidebar(g, mouseX, mouseY);

        // Vertical divider
        g.verticalLine(contentX, bodyY, bodyY + bodyH, SkyHubTheme.BORDER_SUBTLE);

        // Main content area
        renderContentArea(g, mouseX, mouseY);

        // Footer bar
        renderFooter(g);

        // Tooltip for square reload button
        if (refreshHovered) {
            g.setTooltipForNextFrame(font, Component.literal("Reload mod registries"), mouseX, mouseY);
        }

        // Render vanilla EditBox widgets (searchBox & optional inlineStringBox)
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(panelX + 1, bodyY + 1, contentX, bodyY + bodyH, SkyHubTheme.SIDEBAR_BG);

        g.enableScissor(panelX + 1, sidebarListY, contentX, bodyY + bodyH);
        int y = sidebarListY + 5 - (int) sidebarScroll;

        if (isByType()) {
            renderCategorySidebarItems(g, y, mouseX, mouseY);
        } else {
            renderModSidebarItems(g, y, mouseX, mouseY);
        }

        g.disableScissor();
    }

    private void renderCategorySidebarItems(GuiGraphicsExtractor g, int startY, int mouseX, int mouseY) {
        int y = startY;
        boolean searching = isSearching();

        for (int i = 0; i < visibleCategories.size(); i++) {
            CategoryBucket bucket = visibleCategories.get(i);
            FeatureCategory cat = bucket.category();

            if (y + SIDEBAR_ROW_H >= sidebarListY && y <= bodyY + bodyH) {
                boolean selected = !searching && cat == selectedCategory;
                boolean hovered = isSidebarRowHovered(mouseX, mouseY, y);

                if (selected || hovered) {
                    SkyHubTheme.drawBox(
                        g, panelX + 5, y, SIDEBAR_W - 10, SIDEBAR_ROW_H - 2,
                        selected ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.CARD_HOVER,
                        selected ? cat.accentColor() : SkyHubTheme.BORDER_MUTED
                    );
                }
                if (selected) {
                    g.fill(panelX + 5, y + 2, panelX + 8, y + SIDEBAR_ROW_H - 4, cat.accentColor());
                } else {
                    g.fill(panelX + 8, y + 5, panelX + 11, y + SIDEBAR_ROW_H - 7, cat.accentColor());
                }

                String countText = String.valueOf(bucket.count());
                int countW = font.width(countText);
                g.text(font, countText, contentX - 10 - countW, y + 7, SkyHubTheme.TEXT_SECONDARY, false);

                int maxNameW = SIDEBAR_W - 30 - countW;
                String displayName = SkyHubTheme.truncate(font, cat.displayName(), maxNameW);
                g.text(font, displayName, panelX + 16, y + 7,
                    selected ? SkyHubTheme.TEXT_PRIMARY : SkyHubTheme.TEXT_SECONDARY, false);
            }

            y += SIDEBAR_ROW_H;
            // Subtle divider after Mod Settings (just like iPad Settings separating General/Meta from Features)
            if (cat == FeatureCategory.MOD_SETTINGS && i < visibleCategories.size() - 1) {
                g.horizontalLine(panelX + 10, contentX - 10, y + 1, SkyHubTheme.BORDER_MUTED);
                y += 5;
            }
        }
    }

    private void renderModSidebarItems(GuiGraphicsExtractor g, int startY, int mouseX, int mouseY) {
        int y = startY;
        boolean searching = isSearching();

        for (ModEntry mod : visibleMods) {
            if (y + SIDEBAR_ROW_H >= sidebarListY && y <= bodyY + bodyH) {
                boolean selected = !searching && mod.modId().equals(selectedModId);
                boolean hovered = isSidebarRowHovered(mouseX, mouseY, y);

                if (selected || hovered) {
                    SkyHubTheme.drawBox(
                        g, panelX + 5, y, SIDEBAR_W - 10, SIDEBAR_ROW_H - 2,
                        selected ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.CARD_HOVER,
                        selected ? mod.accentColor() : SkyHubTheme.BORDER_MUTED
                    );
                }

                if (selected) {
                    g.fill(panelX + 5, y + 2, panelX + 8, y + SIDEBAR_ROW_H - 4, mod.accentColor());
                } else {
                    g.fill(panelX + 8, y + 5, panelX + 11, y + SIDEBAR_ROW_H - 7, mod.accentColor());
                }

                String badgeText = mod.settingCount() > 0 ? String.valueOf(mod.settingCount())
                    : (mod.hudWidgetCount() > 0 ? "HUD" : (mod.hasNativeMenu() ? "\u2197" : "\u2022"));
                int badgeColor = mod.health() == AdapterHealth.HEALTHY ? SkyHubTheme.TEXT_SECONDARY
                    : (mod.hasNativeMenu() ? SkyHubTheme.ACCENT_AMBER : SkyHubTheme.TEXT_MUTED);
                int badgeW = font.width(badgeText);
                g.text(font, badgeText, contentX - 10 - badgeW, y + 7, badgeColor, false);

                int maxNameW = SIDEBAR_W - 30 - badgeW;
                String displayName = SkyHubTheme.truncate(font, mod.displayName(), maxNameW);
                g.text(font, displayName, panelX + 16, y + 7,
                    selected ? SkyHubTheme.TEXT_PRIMARY : SkyHubTheme.TEXT_SECONDARY, false);
            }
            y += SIDEBAR_ROW_H;
        }
    }

    private boolean isSidebarRowHovered(int mouseX, int mouseY, int y) {
        return mouseX >= panelX + 4 && mouseX < contentX - 4
            && mouseY >= y && mouseY < y + SIDEBAR_ROW_H - 2
            && mouseY >= sidebarListY && mouseY < bodyY + bodyH;
    }

    private void renderContentArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ModEntry selectedMod = (!isSearching() && !isByType() && selectedModId != null)
            ? UnifiedRegistry.getInstance().mod(selectedModId)
            : null;

        renderDetailBanner(g, selectedMod, mouseX, mouseY);

        listY = bodyY + DETAIL_BANNER_H;
        listH = bodyH - DETAIL_BANNER_H;

        if (visibleRows.isEmpty()) {
            renderEmptyOrFallbackState(g, selectedMod, mouseX, mouseY);
            return;
        }

        g.enableScissor(contentX + 1, listY, panelX + panelW - 1, listY + listH);

        SettingEntry hoveredForTooltip = null;
        int y = listY + 6 - (int) listScroll;
        int baseRowX = contentX + 10;
        int baseRowW = contentW - 24;

        for (SettingTreeBuilder.VisibleRow row : visibleRows) {
            if (row instanceof SettingTreeBuilder.SectionHeaderRow hdr && hdr.depth() == 0 && y > listY + 6 - (int) listScroll) {
                y += 4;
            }
            int rowH = row.height();

            if (y + rowH >= listY && y <= listY + listH) {
                int indent = Math.min(row.depth(), 4) * 14;
                int rx = baseRowX + indent;
                int rw = Math.max(160, baseRowW - indent);

                // Subtle vertical tree guide lines for nested iPad hierarchy
                for (int d = 1; d <= Math.min(row.depth(), 4); d++) {
                    int gx = baseRowX + (d - 1) * 14 + 6;
                    g.verticalLine(gx, y, y + rowH, SkyHubTheme.BORDER_MUTED);
                }

                if (row instanceof SettingTreeBuilder.SectionHeaderRow hdr) {
                    boolean hovered = mouseX >= rx && mouseX <= rx + rw
                        && mouseY >= y && mouseY < y + rowH - 2
                        && mouseY >= listY && mouseY < listY + listH;
                    renderSectionHeader(g, hdr, rx, y, rw, rowH - 2, hovered);
                } else if (row instanceof SettingTreeBuilder.SettingItemRow item) {
                    boolean hovered = mouseX >= rx && mouseX <= rx + rw
                        && mouseY >= y && mouseY < y + rowH
                        && mouseY >= listY && mouseY < listY + listH;
                    renderGroupedSettingRow(g, item, rx, y, rw, rowH, hovered, mouseX, mouseY);
                    if (hovered && !item.entry().description().isBlank()) {
                        hoveredForTooltip = item.entry();
                    }
                }
            }

            y += rowH;
            if (row instanceof SettingTreeBuilder.SettingItemRow item && item.lastInGroup()) {
                y += 4;
            }
        }

        g.disableScissor();

        // Scrollbar
        if (totalRowsHeight > listH) {
            int trackX = panelX + panelW - 7;
            int thumbH = Math.max(20, (int) ((double) listH * listH / totalRowsHeight));
            int maxScroll = Math.max(1, totalRowsHeight - listH);
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

    private void renderDetailBanner(GuiGraphicsExtractor g, ModEntry selectedMod, int mouseX, int mouseY) {
        int bx = contentX + 1;
        int by = bodyY + 1;
        int bw = contentW - 2;
        g.fill(bx, by, bx + bw, by + DETAIL_BANNER_H - 1, SkyHubTheme.CARD_BG);
        g.horizontalLine(bx, bx + bw, by + DETAIL_BANNER_H - 1, SkyHubTheme.BORDER_SUBTLE);

        int accent;
        String title;
        String subtitle;

        if (isSearching()) {
            accent = SkyHubTheme.ACCENT_CYAN;
            title = "Search: \"" + searchBox.getValue().trim() + "\"";
            subtitle = filteredSettings.size() + " matches across all categories";
        } else if (selectedMod != null) {
            accent = selectedMod.accentColor();
            title = selectedMod.displayName();
            subtitle = selectedMod.adapterName() + " \u00B7 " + selectedMod.settingCount() + " settings"
                + (selectedMod.hudWidgetCount() > 0 ? " \u00B7 " + selectedMod.hudWidgetCount() + " HUDs" : "");
        } else {
            FeatureCategory cat = selectedCategory != null ? selectedCategory : FeatureCategory.MOD_SETTINGS;
            accent = cat.accentColor();
            title = cat.displayName();
            subtitle = cat == FeatureCategory.MOD_SETTINGS
                ? "Update streams, mod config, backups, debug & pack settings"
                : filteredSettings.size() + " settings across " + sectionRoots.size() + " mods";
        }

        g.fill(bx + 8, by + 7, bx + 11, by + DETAIL_BANNER_H - 8, accent);
        g.text(font, SkyHubTheme.truncate(font, title, Math.max(80, bw - 220)), bx + 17, by + 6, SkyHubTheme.TEXT_PRIMARY, true);
        g.text(font, SkyHubTheme.truncate(font, subtitle, Math.max(80, bw - 220)), bx + 17, by + 19, SkyHubTheme.TEXT_MUTED, false);

        // Right-side action pills in banner
        int right = bx + bw - 8;

        if (!isSearching() && !sectionRoots.isEmpty()) {
            boolean allCollapsed = expandedSections.isEmpty();
            String label = allCollapsed ? "Expand All" : "Collapse";
            int w = 64;
            int x = right - w;
            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= by + 7 && mouseY <= by + 27;
            SkyHubTheme.drawPill(g, font, x, by + 7, w, 20, label,
                hov ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.PANEL_BG,
                hov ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.BORDER_SUBTLE,
                SkyHubTheme.TEXT_SECONDARY);
            right = x - 6;
        }

        if (selectedMod != null) {
            if (selectedMod.hasNativeMenu()) {
                int w = 88;
                int x = right - w;
                boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= by + 7 && mouseY <= by + 27;
                SkyHubTheme.drawPill(g, font, x, by + 7, w, 20, "Native Menu \u2197",
                    hov ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.PANEL_BG,
                    hov ? selectedMod.accentColor() : SkyHubTheme.BORDER_SUBTLE,
                    SkyHubTheme.TEXT_PRIMARY);
                right = x - 6;
            }
            if (selectedMod.hasNativeHud()) {
                int w = 80;
                int x = right - w;
                boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= by + 7 && mouseY <= by + 27;
                SkyHubTheme.drawPill(g, font, x, by + 7, w, 20, "Native HUD \u2197",
                    hov ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.PANEL_BG,
                    hov ? 0xFFA855F7 : SkyHubTheme.BORDER_SUBTLE,
                    SkyHubTheme.TEXT_SECONDARY);
            }
        }
    }

    private void renderSectionHeader(
        GuiGraphicsExtractor g,
        SettingTreeBuilder.SectionHeaderRow hdr,
        int x,
        int y,
        int w,
        int h,
        boolean hovered
    ) {
        SettingTreeBuilder.SectionNode node = hdr.node();
        boolean isRoot = hdr.depth() == 0;

        int bg = isRoot
            ? (hovered ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.CARD_HOVER)
            : (hovered ? SkyHubTheme.CARD_HOVER : SkyHubTheme.withAlpha(SkyHubTheme.CARD_BG, 0xCC));
        int border = hovered
            ? (node.modBadge() != null ? node.accentColor() : SkyHubTheme.ACCENT_CYAN)
            : (isRoot ? SkyHubTheme.BORDER_SUBTLE : SkyHubTheme.BORDER_MUTED);

        SkyHubTheme.drawBox(g, x, y, w, h, bg, border);
        if (isRoot && node.modBadge() != null) {
            g.fill(x + 1, y + 1, x + 4, y + h - 1, node.accentColor());
        }

        // Crisp pixel chevron
        int chevX = x + (isRoot ? 10 : 8);
        int chevY = y + (h - 6) / 2;
        SkyHubTheme.drawChevron(g, chevX, chevY, hdr.expanded(),
            hdr.expanded() ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.TEXT_SECONDARY);

        int textX = chevX + 11;
        if (node.modBadge() != null) {
            int bw = SkyHubTheme.drawBadge(g, font, textX, y + (h - 12) / 2, node.modBadge(), node.accentColor());
            textX += bw + 6;
        }

        String countLabel = String.valueOf(node.totalSettings());
        int countW = font.width(countLabel);
        int countPillW = countW + 10;
        int countPillX = x + w - countPillW - 6;
        SkyHubTheme.drawPill(g, font, countPillX, y + (h - 14) / 2, countPillW, 14, countLabel,
            SkyHubTheme.PANEL_BG, SkyHubTheme.BORDER_MUTED, SkyHubTheme.TEXT_MUTED);

        int maxTitleW = Math.max(40, countPillX - textX - 8);
        g.text(
            font,
            SkyHubTheme.truncate(font, node.title(), maxTitleW),
            textX,
            y + (h - font.lineHeight) / 2 + 1,
            isRoot ? SkyHubTheme.TEXT_PRIMARY : SkyHubTheme.TEXT_SECONDARY,
            isRoot
        );
    }

    private void renderGroupedSettingRow(
        GuiGraphicsExtractor g,
        SettingTreeBuilder.SettingItemRow item,
        int x,
        int y,
        int w,
        int h,
        boolean hovered,
        int mouseX,
        int mouseY
    ) {
        SettingEntry entry = item.entry();

        // iPad grouped inset card background
        g.fill(x, y, x + w, y + h, hovered ? SkyHubTheme.CARD_HOVER : SkyHubTheme.CARD_BG);
        g.verticalLine(x, y, y + h - 1, SkyHubTheme.BORDER_MUTED);
        g.verticalLine(x + w - 1, y, y + h - 1, SkyHubTheme.BORDER_MUTED);
        if (item.firstInGroup()) {
            g.horizontalLine(x, x + w - 1, y, SkyHubTheme.BORDER_MUTED);
        } else {
            // Subtle iPad inset separator line between rows in the same section
            g.horizontalLine(x + 10, x + w - 10, y, SkyHubTheme.BORDER_MUTED);
        }
        if (item.lastInGroup()) {
            g.horizontalLine(x, x + w - 1, y + h - 1, SkyHubTheme.BORDER_MUTED);
        }

        int curX = x + 10;
        int controlAreaW = 148;
        int leftTextMaxW = Math.max(80, w - (curX - x) - controlAreaW - 12);

        // Title line
        String title = SkyHubTheme.truncate(font, entry.name(), leftTextMaxW);
        int titleY = entry.description().isBlank() ? (y + (h - font.lineHeight) / 2 + 1) : (y + 6);
        g.text(font, title, curX, titleY, SkyHubTheme.TEXT_PRIMARY, false);

        // Description line (if present)
        if (!entry.description().isBlank()) {
            g.text(
                font,
                SkyHubTheme.truncate(font, entry.description(), leftTextMaxW),
                curX,
                y + 20,
                SkyHubTheme.TEXT_MUTED,
                false
            );
        }

        // Control area on right
        int ctrlRight = x + w - 8;
        int ctrlY = y + (h - 20) / 2;

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
            g.centeredText(font, "No settings matched.", cx, cy + 16, SkyHubTheme.TEXT_SECONDARY);
            g.centeredText(font, "Try another search query or switch sections.", cx, cy + 32, SkyHubTheme.TEXT_MUTED);
        }
    }

    private void renderFooter(GuiGraphicsExtractor g) {
        int fy = panelY + panelH - FOOTER_H;
        g.fill(panelX + 1, fy, panelX + panelW - 1, panelY + panelH - 1, SkyHubTheme.SIDEBAR_BG);
        g.horizontalLine(panelX, panelX + panelW - 1, fy, SkyHubTheme.BORDER_SUBTLE);

        UnifiedRegistry reg = UnifiedRegistry.getInstance();
        String left = filteredSettings.size() + " settings  \u00B7  " + scopedTotalSettings + " total  \u00B7  "
            + reg.hudWidgets().size() + " HUDs";
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

        // 1. Header reload button
        int pillY = panelY + 11;
        if (my >= pillY && my <= pillY + 20) {
            if (mx >= btnRefreshX && mx <= btnRefreshX + btnRefreshSize) {
                closeInlineStringEditor(true);
                UnifiedRegistry.getInstance().refresh();
                rebuildFilteredViews(false);
                showToast("Refreshed mod registries");
                return true;
            }
        }

        // 2. Sidebar list click
        if (mx >= panelX + 4 && mx < contentX - 4 && my >= sidebarListY && my < bodyY + bodyH) {
            closeInlineStringEditor(true);
            int y = sidebarListY + 5 - (int) sidebarScroll;
            if (isByType()) {
                for (int i = 0; i < visibleCategories.size(); i++) {
                    CategoryBucket bucket = visibleCategories.get(i);
                    if (my >= y && my < y + SIDEBAR_ROW_H - 2) {
                        selectedCategory = bucket.category();
                        if (isSearching()) {
                            searchBox.setValue("");
                        }
                        listScroll = 0;
                        rebuildFilteredViews(true);
                        return true;
                    }
                    y += SIDEBAR_ROW_H;
                    if (bucket.category() == FeatureCategory.MOD_SETTINGS && i < visibleCategories.size() - 1) {
                        y += 5;
                    }
                }
            } else {
                for (ModEntry mod : visibleMods) {
                    if (my >= y && my < y + SIDEBAR_ROW_H - 2) {
                        selectedModId = mod.modId();
                        if (isSearching()) {
                            searchBox.setValue("");
                        }
                        listScroll = 0;
                        rebuildFilteredViews(true);
                        return true;
                    }
                    y += SIDEBAR_ROW_H;
                }
            }
        }

        // 4. Detail banner buttons (Expand/Collapse + Native Menu/HUD)
        ModEntry selectedMod = (!isSearching() && !isByType() && selectedModId != null)
            ? UnifiedRegistry.getInstance().mod(selectedModId)
            : null;
        if (my >= bodyY + 7 && my <= bodyY + 27 && mx >= contentX) {
            int right = contentX + contentW - 9;
            if (!isSearching() && !sectionRoots.isEmpty()) {
                int w = 64;
                int x = right - w;
                if (mx >= x && mx <= x + w) {
                    if (expandedSections.isEmpty()) {
                        expandAllSections();
                    } else {
                        collapseAllSections();
                    }
                    return true;
                }
                right = x - 6;
            }
            if (selectedMod != null) {
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
        }

        // 5. Fallback big button when selected mod has 0 settings
        if (selectedMod != null && visibleRows.isEmpty() && selectedMod.hasNativeMenu()) {
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

        // 6. Collapsible section headers & setting rows click
        if (mx >= contentX && mx < panelX + panelW && my >= listY && my < listY + listH) {
            int baseRowX = contentX + 10;
            int baseRowW = contentW - 24;
            int y = listY + 6 - (int) listScroll;

            for (SettingTreeBuilder.VisibleRow row : visibleRows) {
                if (row instanceof SettingTreeBuilder.SectionHeaderRow hdr && hdr.depth() == 0 && y > listY + 6 - (int) listScroll) {
                    y += 4;
                }
                int rowH = row.height();
                int indent = Math.min(row.depth(), 4) * 14;
                int rx = baseRowX + indent;
                int rw = Math.max(160, baseRowW - indent);

                if (my >= y && my < y + rowH && mx >= rx && mx <= rx + rw) {
                    if (row instanceof SettingTreeBuilder.SectionHeaderRow hdr) {
                        closeInlineStringEditor(true);
                        if (!isSearching()) {
                            String key = hdr.node().key();
                            if (!expandedSections.remove(key)) {
                                expandedSections.add(key);
                            }
                            rebuildVisibleRows();
                        }
                        return true;
                    } else if (row instanceof SettingTreeBuilder.SettingItemRow item) {
                        return handleSettingRowClick(item.entry(), rx, y, rw, rowH, mx, my, button);
                    }
                }

                y += rowH;
                if (row instanceof SettingTreeBuilder.SettingItemRow item && item.lastInGroup()) {
                    y += 4;
                }
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

        if (entry.isModified()) {
            int resetX = ctrlRight - 18;
            if (mx >= resetX && mx <= resetX + 18 && my >= ctrlY && my <= ctrlY + 20) {
                closeInlineStringEditor(false);
                if (entry.reset()) {
                    showToast("Reset " + entry.name() + " (" + entry.modName() + ")");
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
        if (mouseX >= panelX && mouseX < contentX && mouseY >= sidebarListY && mouseY < bodyY + bodyH) {
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
