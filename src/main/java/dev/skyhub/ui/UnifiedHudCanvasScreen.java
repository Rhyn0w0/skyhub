package dev.skyhub.ui;

import dev.skyhub.config.SkyHubConfig;
import dev.skyhub.model.FeatureCategory;
import dev.skyhub.model.HudWidget;
import dev.skyhub.model.ModCatalog;
import dev.skyhub.registry.UnifiedRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase 2 Core UI: Unified Drag-and-Drop HUD Canvas across SkyHanni, Skyblocker,
 * Odin, SkyOcean, CustomScoreboard, and Stella.
 * Features Mod, Feature-Type, and Visibility dropdown filters, corner drag-resizing,
 * Shift-to-bypass alignment snapping, and a hoverable Help (?) controls popover.
 */
public final class UnifiedHudCanvasScreen extends Screen {
    private static final int SNAP_THRESHOLD_PX = 5;
    private static final int TOOLBAR_H = 30;
    private static final int DROPDOWN_ROW_H = 20;
    private static final int DROPDOWN_MAX_VISIBLE = 10;

    private enum OpenDropdown {
        NONE, MOD, TYPE, VISIBILITY
    }

    public enum VisibilityFilter {
        ALL("All HUDs", SkyHubTheme.ACCENT_CYAN),
        ACTIVE("Active", SkyHubTheme.ACCENT_GREEN),
        DISABLED("Disabled", SkyHubTheme.ACCENT_AMBER);

        private final String label;
        private final int accentColor;

        VisibilityFilter(String label, int accentColor) {
            this.label = label;
            this.accentColor = accentColor;
        }

        public String label() { return label; }
        public int accentColor() { return accentColor; }
    }

    private record DropdownItem(
        String modId,
        FeatureCategory category,
        VisibilityFilter visibility,
        String label,
        int count,
        int accentColor
    ) {}

    private static final String[][] HELP_ROWS = {
        { "Drag widget",   "Move position" },
        { "Drag corners",  "Resize scale" },
        { "Arrow keys",    "Nudge 1px (Shift: 5px)" },
        { "Hold Shift",    "Bypass snap guides" },
        { "Right-click",   "Toggle widget ON/OFF" },
        { "Tab",           "Back to Settings Hub" },
        { "Esc",           "Save & close" }
    };

    private final Screen returnScreen;

    private String filterModId = null; // null = All Mods
    private FeatureCategory filterType = null; // null = All Types
    private VisibilityFilter visibilityFilter = VisibilityFilter.ALL;
    private final boolean snapEnabled;

    private OpenDropdown openDropdown = OpenDropdown.NONE;
    private double dropdownScroll = 0;

    private HudWidget selectedWidget = null;
    private HudWidget draggingWidget = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private HudWidget resizingWidget = null;
    private HudSnapEngine.ResizeCorner resizeCorner = null;
    private int resizeAnchorX = 0;
    private int resizeAnchorY = 0;
    private List<HudSnapEngine.SnapGuide> activeGuides = List.of();

    private List<HudWidget> visibleWidgets = List.of();
    private List<DropdownItem> modDropdownItems = List.of();
    private List<DropdownItem> typeDropdownItems = List.of();
    private List<DropdownItem> visibilityDropdownItems = List.of();

    private String toast = "";
    private long toastUntil = 0L;

    // Toolbar button positions
    private int backBtnX, backBtnW;
    private int modDropX, modDropW;
    private int typeDropX, typeDropW;
    private int visDropX, visDropW;
    private int helpBtnX, helpBtnSize;
    private int doneBtnX, doneBtnW;

    public UnifiedHudCanvasScreen(Screen returnScreen) {
        super(Component.translatable("skyhub.hud.title"));
        this.returnScreen = returnScreen;
        this.snapEnabled = SkyHubConfig.get().hudSnappingDefault();
    }

    @Override
    protected void init() {
        UnifiedRegistry.getInstance().ensureInitialized();
        rebuildVisibleWidgets();
    }

    private boolean matchesVisibility(HudWidget w, VisibilityFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case ACTIVE -> w.isEnabled();
            case DISABLED -> !w.isEnabled();
        };
    }

    private void rebuildVisibleWidgets() {
        List<HudWidget> all = UnifiedRegistry.getInstance().hudWidgets();

        Map<String, String> modNames = new LinkedHashMap<>();
        Map<String, Integer> modCounts = new LinkedHashMap<>();
        int totalForModMenu = 0;

        Map<FeatureCategory, Integer> typeCounts = new EnumMap<>(FeatureCategory.class);
        int totalForTypeMenu = 0;

        int visAllCount = 0;
        int visActiveCount = 0;
        int visDisabledCount = 0;

        List<HudWidget> filtered = new ArrayList<>();
        for (HudWidget w : all) {
            FeatureCategory wType = FeatureCategory.classify(w);
            boolean modOk = filterModId == null || filterModId.equals(w.modId());
            boolean typeOk = filterType == null || wType == filterType;
            boolean visOk = matchesVisibility(w, visibilityFilter);

            if (typeOk && visOk) {
                modNames.putIfAbsent(w.modId(), w.modName());
                modCounts.merge(w.modId(), 1, Integer::sum);
                totalForModMenu++;
            }

            if (modOk && visOk) {
                typeCounts.merge(wType, 1, Integer::sum);
                totalForTypeMenu++;
            }

            if (modOk && typeOk) {
                visAllCount++;
                if (w.isEnabled()) visActiveCount++;
                else visDisabledCount++;
            }

            if (modOk && typeOk && visOk) {
                filtered.add(w);
            }
        }

        List<DropdownItem> modItems = new ArrayList<>();
        modItems.add(new DropdownItem(null, null, null, "All Mods", totalForModMenu, SkyHubTheme.ACCENT_CYAN));
        for (Map.Entry<String, String> e : modNames.entrySet()) {
            modItems.add(new DropdownItem(
                e.getKey(), null, null, e.getValue(),
                modCounts.getOrDefault(e.getKey(), 0),
                ModCatalog.accentColor(e.getKey())
            ));
        }
        modDropdownItems = modItems;

        List<DropdownItem> typeItems = new ArrayList<>();
        typeItems.add(new DropdownItem(null, null, null, "All Types", totalForTypeMenu, SkyHubTheme.ACCENT_CYAN));
        for (FeatureCategory cat : FeatureCategory.values()) {
            int c = typeCounts.getOrDefault(cat, 0);
            if (c > 0 || cat == filterType) {
                typeItems.add(new DropdownItem(null, cat, null, cat.displayName(), c, cat.accentColor()));
            }
        }
        typeDropdownItems = typeItems;

        visibilityDropdownItems = List.of(
            new DropdownItem(null, null, VisibilityFilter.ALL, VisibilityFilter.ALL.label(), visAllCount, VisibilityFilter.ALL.accentColor()),
            new DropdownItem(null, null, VisibilityFilter.ACTIVE, VisibilityFilter.ACTIVE.label(), visActiveCount, VisibilityFilter.ACTIVE.accentColor()),
            new DropdownItem(null, null, VisibilityFilter.DISABLED, VisibilityFilter.DISABLED.label(), visDisabledCount, VisibilityFilter.DISABLED.accentColor())
        );

        visibleWidgets = filtered;
        if (selectedWidget != null && !visibleWidgets.contains(selectedWidget)) {
            selectedWidget = null;
        }

        computeToolbarLayout();
    }

    private void computeToolbarLayout() {
        int x = 8;
        backBtnX = x;
        backBtnW = 56;
        x += backBtnW + 6;

        int right = width - 8;
        doneBtnW = 46;
        doneBtnX = right - doneBtnW;

        helpBtnSize = 20;
        helpBtnX = doneBtnX - 5 - helpBtnSize;

        int avail = Math.max(210, helpBtnX - x - 10);
        visDropW = Math.min(92, Math.max(72, avail / 4));
        int rem = Math.max(120, avail - visDropW - 12);
        int each = Math.min(134, Math.max(74, rem / 2));

        modDropX = x;
        modDropW = each;
        typeDropX = modDropX + modDropW + 6;
        typeDropW = each;
        visDropX = typeDropX + typeDropW + 6;
    }

    private void showToast(String msg) {
        this.toast = msg;
        this.toastUntil = System.currentTimeMillis() + 2400L;
    }

    private List<DropdownItem> activeDropdownItems() {
        return switch (openDropdown) {
            case MOD -> modDropdownItems;
            case TYPE -> typeDropdownItems;
            case VISIBILITY -> visibilityDropdownItems;
            case NONE -> List.of();
        };
    }

    private int activeDropdownX() {
        return switch (openDropdown) {
            case MOD -> modDropX;
            case TYPE -> typeDropX;
            case VISIBILITY -> visDropX;
            case NONE -> 0;
        };
    }

    private int activeDropdownW() {
        int base = switch (openDropdown) {
            case MOD -> modDropW;
            case TYPE -> typeDropW;
            case VISIBILITY -> visDropW;
            case NONE -> 120;
        };
        return Math.max(openDropdown == OpenDropdown.VISIBILITY ? 124 : 152, base);
    }

    private int activeDropdownHeight() {
        int rows = Math.min(DROPDOWN_MAX_VISIBLE, activeDropdownItems().size());
        return Math.max(DROPDOWN_ROW_H, rows * DROPDOWN_ROW_H + 6);
    }

    private void clampDropdownScroll() {
        int totalH = activeDropdownItems().size() * DROPDOWN_ROW_H;
        int viewH = DROPDOWN_MAX_VISIBLE * DROPDOWN_ROW_H;
        int max = Math.max(0, totalH - viewH);
        dropdownScroll = Math.max(0, Math.min(max, dropdownScroll));
    }

    private boolean isHelpHovered(int mouseX, int mouseY) {
        return mouseX >= helpBtnX && mouseX <= helpBtnX + helpBtnSize
            && mouseY >= 5 && mouseY <= 5 + helpBtnSize;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x7A070A10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        computeToolbarLayout();
        extractBackground(g, mouseX, mouseY, partialTick);

        // Subtle alignment centerlines
        g.verticalLine(width / 2, TOOLBAR_H, height, 0x22FFFFFF);
        g.horizontalLine(0, width, height / 2, 0x22FFFFFF);

        // Active snap guides when dragging
        for (HudSnapEngine.SnapGuide guide : activeGuides) {
            int color = guide.isCenterGuide() ? SkyHubTheme.GUIDE_CENTER : SkyHubTheme.GUIDE_EDGE;
            if (guide.vertical()) {
                g.verticalLine(guide.position(), TOOLBAR_H, height, color);
            } else {
                g.horizontalLine(0, width, guide.position(), color);
            }
        }

        // Render HUD widget boxes
        if (visibleWidgets.isEmpty()) {
            g.centeredText(font, "No HUD widgets matched the current filters.", width / 2, height / 2 - 8, SkyHubTheme.TEXT_PRIMARY);
            g.centeredText(font, "Try changing the Mod, Type, or Visibility dropdowns.", width / 2, height / 2 + 8, SkyHubTheme.TEXT_SECONDARY);
        } else {
            for (HudWidget w : visibleWidgets) {
                if (w == selectedWidget) continue;
                renderWidgetBox(g, w, false, openDropdown == OpenDropdown.NONE && isOverWidget(w, mouseX, mouseY), mouseX, mouseY);
            }
            if (selectedWidget != null) {
                renderWidgetBox(g, selectedWidget, true, openDropdown == OpenDropdown.NONE && isOverWidget(selectedWidget, mouseX, mouseY), mouseX, mouseY);
            }
        }

        // Top floating toolbar
        renderToolbar(g, mouseX, mouseY);

        // Bottom inspector pill ONLY when a widget is selected (bottom help bar moved to ? button)
        if (selectedWidget != null) {
            renderSelectedInspector(g, selectedWidget, mouseX, mouseY);
        }

        if (System.currentTimeMillis() < toastUntil && !toast.isBlank()) {
            int tw = font.width(toast) + 18;
            SkyHubTheme.drawBox(g, (width - tw) / 2, TOOLBAR_H + 8, tw, 18, 0xEE101824, SkyHubTheme.ACCENT_GREEN);
            g.centeredText(font, toast, width / 2, TOOLBAR_H + 13, SkyHubTheme.ACCENT_GREEN);
        }

        // Render open dropdown OR hoverable Help popover on top of everything
        if (openDropdown != OpenDropdown.NONE) {
            renderDropdownMenu(g, mouseX, mouseY);
        } else if (isHelpHovered(mouseX, mouseY)) {
            renderHelpPopover(g);
        }
    }

    private void renderWidgetBox(GuiGraphicsExtractor g, HudWidget w, boolean selected, boolean hovered, int mouseX, int mouseY) {
        int x = clampX(w.getX(), w.getScaledWidth());
        int y = clampY(w.getY(), w.getScaledHeight());
        int bw = w.getScaledWidth();
        int bh = w.getScaledHeight();

        boolean enabled = w.isEnabled();
        int accent = w.modAccentColor();
        int fill = enabled
            ? SkyHubTheme.withAlpha(accent, selected ? 0x48 : (hovered ? 0x36 : 0x22))
            : 0x4410141D;
        int border = selected ? 0xFFFFFFFF : (hovered ? accent : SkyHubTheme.withAlpha(accent, enabled ? 0xCC : 0x55));

        SkyHubTheme.drawBox(g, x, y, bw, bh, fill, border);
        if (selected && w.canScale()) {
            HudSnapEngine.ResizeCorner hoveredCorner = openDropdown == OpenDropdown.NONE
                ? hitTestCornerHandle(w, mouseX, mouseY)
                : null;
            drawCornerHandle(g, x, y, accent, hoveredCorner == HudSnapEngine.ResizeCorner.NW || resizeCorner == HudSnapEngine.ResizeCorner.NW);
            drawCornerHandle(g, x + bw, y, accent, hoveredCorner == HudSnapEngine.ResizeCorner.NE || resizeCorner == HudSnapEngine.ResizeCorner.NE);
            drawCornerHandle(g, x, y + bh, accent, hoveredCorner == HudSnapEngine.ResizeCorner.SW || resizeCorner == HudSnapEngine.ResizeCorner.SW);
            drawCornerHandle(g, x + bw, y + bh, accent, hoveredCorner == HudSnapEngine.ResizeCorner.SE || resizeCorner == HudSnapEngine.ResizeCorner.SE);
        }

        String tag = ModCatalog.shortLabel(w.modId(), w.modName());
        String label = SkyHubTheme.truncate(font, "[" + tag + "] " + w.name(), Math.max(24, bw - 8));
        g.text(font, label, x + 4, y + 4, enabled ? SkyHubTheme.TEXT_PRIMARY : SkyHubTheme.TEXT_MUTED, true);

        if (bh >= 26) {
            String coords = String.format(Locale.ROOT, "(%d, %d) %.2fx%s",
                w.getX(), w.getY(), w.getScale(), enabled ? "" : " OFF");
            g.text(font, SkyHubTheme.truncate(font, coords, Math.max(24, bw - 8)),
                x + 4, y + 15, SkyHubTheme.TEXT_SECONDARY, false);
        }
    }

    private void renderToolbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, width, TOOLBAR_H, 0xE80C1018);
        g.horizontalLine(0, width, TOOLBAR_H, SkyHubTheme.BORDER_SUBTLE);

        int py = 5;
        SkyHubTheme.drawPill(g, font, backBtnX, py, backBtnW, 20, "\u2190 Hub",
            SkyHubTheme.CARD_BG, SkyHubTheme.BORDER_SUBTLE, SkyHubTheme.TEXT_PRIMARY);

        // Mod Dropdown
        String modLabel = filterModId == null ? "Mod: All" : "Mod: " + resolveModName(filterModId);
        int modAccent = filterModId == null ? SkyHubTheme.ACCENT_CYAN : ModCatalog.accentColor(filterModId);
        boolean modActive = filterModId != null || openDropdown == OpenDropdown.MOD;
        renderDropdownButton(g, modDropX, py, modDropW, 20, modLabel, modAccent, modActive, openDropdown == OpenDropdown.MOD);

        // Type Dropdown
        String typeLabel = filterType == null ? "Type: All" : "Type: " + filterType.displayName();
        int typeAccent = filterType == null ? SkyHubTheme.ACCENT_CYAN : filterType.accentColor();
        boolean typeActive = filterType != null || openDropdown == OpenDropdown.TYPE;
        renderDropdownButton(g, typeDropX, py, typeDropW, 20, typeLabel, typeAccent, typeActive, openDropdown == OpenDropdown.TYPE);

        // Visibility Dropdown (All HUDs / Active / Hidden)
        boolean visActive = visibilityFilter != VisibilityFilter.ALL || openDropdown == OpenDropdown.VISIBILITY;
        renderDropdownButton(g, visDropX, py, visDropW, 20, visibilityFilter.label(),
            visibilityFilter.accentColor(), visActive, openDropdown == OpenDropdown.VISIBILITY);

        // Square Help (?) Button
        boolean helpHov = isHelpHovered(mouseX, mouseY);
        SkyHubTheme.drawPill(g, font, helpBtnX, py, helpBtnSize, helpBtnSize, "?",
            helpHov ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.CARD_BG,
            helpHov ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.BORDER_SUBTLE,
            helpHov ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.TEXT_SECONDARY);

        // Done Button
        SkyHubTheme.drawPill(g, font, doneBtnX, py, doneBtnW, 20, "Done",
            SkyHubTheme.ACCENT_GREEN_BG, SkyHubTheme.ACCENT_GREEN, SkyHubTheme.ACCENT_GREEN);
    }

    private void renderHelpPopover(GuiGraphicsExtractor g) {
        int keyColW = 120;
        int descColW = 128;
        int pad = 10;
        int popW = pad * 2 + keyColW + descColW;
        int headerH = 20;
        int rowH = 14;
        int popH = headerH + HELP_ROWS.length * rowH + 8;

        int px = Math.max(8, Math.min(width - popW - 8, helpBtnX + helpBtnSize - popW));
        int py = TOOLBAR_H + 4;

        SkyHubTheme.drawBox(g, px, py, popW, popH, 0xF80D121B, SkyHubTheme.ACCENT_CYAN);
        g.text(font, "HUD Canvas Controls", px + pad, py + 6, SkyHubTheme.TEXT_PRIMARY, true);
        g.horizontalLine(px + 6, px + popW - 6, py + headerH - 1, SkyHubTheme.BORDER_SUBTLE);

        int y = py + headerH + 4;
        for (String[] row : HELP_ROWS) {
            g.text(font, row[0], px + pad, y, SkyHubTheme.ACCENT_CYAN, false);
            g.text(font, row[1], px + pad + keyColW, y, SkyHubTheme.TEXT_SECONDARY, false);
            y += rowH;
        }
    }

    private void renderDropdownButton(
        GuiGraphicsExtractor g,
        int x,
        int y,
        int w,
        int h,
        String label,
        int accent,
        boolean active,
        boolean open
    ) {
        int bg = open ? SkyHubTheme.CARD_SELECTED
            : (active ? SkyHubTheme.withAlpha(accent, 0x33) : SkyHubTheme.CARD_BG);
        int border = (open || active) ? accent : SkyHubTheme.BORDER_SUBTLE;
        SkyHubTheme.drawBox(g, x, y, w, h, bg, border);

        String trimmed = SkyHubTheme.truncate(font, label, Math.max(24, w - 20));
        g.text(font, trimmed, x + 6, y + 6, active ? SkyHubTheme.TEXT_PRIMARY : SkyHubTheme.TEXT_SECONDARY, false);
        SkyHubTheme.drawChevron(g, x + w - 12, y + 7, open, active ? accent : SkyHubTheme.TEXT_MUTED);
    }

    private void renderDropdownMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<DropdownItem> items = activeDropdownItems();
        int dx = Math.min(activeDropdownX(), Math.max(8, width - activeDropdownW() - 8));
        int dy = TOOLBAR_H + 2;
        int dw = activeDropdownW();
        int dh = activeDropdownHeight();

        SkyHubTheme.drawBox(g, dx, dy, dw, dh, 0xF80D121B, SkyHubTheme.BORDER_ACTIVE);
        g.enableScissor(dx + 1, dy + 3, dx + dw - 1, dy + dh - 3);

        int y = dy + 3 - (int) dropdownScroll;
        for (DropdownItem item : items) {
            if (y + DROPDOWN_ROW_H >= dy && y <= dy + dh) {
                boolean selected = isDropdownItemSelected(item);
                boolean hovered = mouseX >= dx + 2 && mouseX <= dx + dw - 2
                    && mouseY >= y && mouseY < y + DROPDOWN_ROW_H
                    && mouseY >= dy + 2 && mouseY <= dy + dh - 2;

                if (selected || hovered) {
                    g.fill(dx + 2, y, dx + dw - 2, y + DROPDOWN_ROW_H,
                        selected ? SkyHubTheme.CARD_SELECTED : SkyHubTheme.CARD_HOVER);
                }

                g.fill(dx + 6, y + 5, dx + 9, y + DROPDOWN_ROW_H - 5, item.accentColor());

                String cnt = String.valueOf(item.count());
                int cntW = font.width(cnt);
                g.text(font, cnt, dx + dw - 8 - cntW, y + 6, SkyHubTheme.TEXT_MUTED, false);

                int maxLabelW = Math.max(30, dw - 24 - cntW);
                g.text(font, SkyHubTheme.truncate(font, item.label(), maxLabelW),
                    dx + 14, y + 6,
                    selected ? SkyHubTheme.TEXT_PRIMARY : SkyHubTheme.TEXT_SECONDARY, false);
            }
            y += DROPDOWN_ROW_H;
        }

        g.disableScissor();
    }

    private boolean isDropdownItemSelected(DropdownItem item) {
        return switch (openDropdown) {
            case MOD -> (item.modId() == null && filterModId == null)
                || (item.modId() != null && item.modId().equals(filterModId));
            case TYPE -> item.category() == filterType;
            case VISIBILITY -> item.visibility() == visibilityFilter;
            case NONE -> false;
        };
    }

    private String resolveModName(String modId) {
        for (DropdownItem item : modDropdownItems) {
            if (modId.equals(item.modId())) return item.label();
        }
        return ModCatalog.shortLabel(modId, modId);
    }

    private void renderSelectedInspector(GuiGraphicsExtractor g, HudWidget w, int mouseX, int mouseY) {
        int barW = Math.min(460, width - 20);
        int barH = 28;
        int bx = (width - barW) / 2;
        int by = height - barH - 8;

        SkyHubTheme.drawBox(g, bx, by, barW, barH, 0xEE10141D, w.modAccentColor());

        String info = String.format(Locale.ROOT, "%s \u00B7 %s  (%d, %d)  %.2fx",
            w.modName(), w.name(), w.getX(), w.getY(), w.getScale());
        int right = bx + barW - 6;

        if (w.canReset()) {
            int rw = 42;
            SkyHubTheme.drawPill(g, font, right - rw, by + 4, rw, 20, "Reset",
                SkyHubTheme.CARD_BG, SkyHubTheme.BORDER_SUBTLE, SkyHubTheme.ACCENT_AMBER);
            right -= (rw + 4);
        }
        if (w.canToggle()) {
            int tw = 44;
            boolean on = w.isEnabled();
            SkyHubTheme.drawPill(g, font, right - tw, by + 4, tw, 20, on ? "ON" : "OFF",
                on ? SkyHubTheme.ACCENT_GREEN_BG : SkyHubTheme.CARD_BG,
                on ? SkyHubTheme.ACCENT_GREEN : SkyHubTheme.BORDER_SUBTLE,
                on ? SkyHubTheme.ACCENT_GREEN : SkyHubTheme.TEXT_MUTED);
            right -= (tw + 4);
        }
        if (w.canScale()) {
            SkyHubTheme.drawPill(g, font, right - 18, by + 4, 18, 20, "+",
                SkyHubTheme.CARD_BG, SkyHubTheme.BORDER_SUBTLE, SkyHubTheme.TEXT_PRIMARY);
            right -= 21;
            SkyHubTheme.drawPill(g, font, right - 18, by + 4, 18, 20, "-",
                SkyHubTheme.CARD_BG, SkyHubTheme.BORDER_SUBTLE, SkyHubTheme.TEXT_PRIMARY);
            right -= 22;
        }

        g.text(font, SkyHubTheme.truncate(font, info, Math.max(60, right - bx - 12)),
            bx + 8, by + 10, SkyHubTheme.TEXT_PRIMARY, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        // 0. Open dropdown click / dismiss
        if (openDropdown != OpenDropdown.NONE) {
            int dx = Math.min(activeDropdownX(), Math.max(8, width - activeDropdownW() - 8));
            int dy = TOOLBAR_H + 2;
            int dw = activeDropdownW();
            int dh = activeDropdownHeight();
            if (mx >= dx && mx <= dx + dw && my >= dy && my <= dy + dh) {
                int y = dy + 3 - (int) dropdownScroll;
                for (DropdownItem item : activeDropdownItems()) {
                    if (my >= y && my < y + DROPDOWN_ROW_H) {
                        if (openDropdown == OpenDropdown.MOD) {
                            filterModId = item.modId();
                        } else if (openDropdown == OpenDropdown.TYPE) {
                            filterType = item.category();
                        } else if (openDropdown == OpenDropdown.VISIBILITY && item.visibility() != null) {
                            visibilityFilter = item.visibility();
                        }
                        openDropdown = OpenDropdown.NONE;
                        dropdownScroll = 0;
                        rebuildVisibleWidgets();
                        return true;
                    }
                    y += DROPDOWN_ROW_H;
                }
                return true;
            }
            if (my > TOOLBAR_H) {
                openDropdown = OpenDropdown.NONE;
                dropdownScroll = 0;
                return true;
            }
        }

        // 1. Toolbar clicks
        if (my <= TOOLBAR_H) {
            if (mx >= backBtnX && mx <= backBtnX + backBtnW) {
                openDropdown = OpenDropdown.NONE;
                saveAllDirty();
                minecraft.gui.setScreen(returnScreen != null ? returnScreen : new SkyHubScreen(null));
                return true;
            }
            if (mx >= modDropX && mx <= modDropX + modDropW) {
                openDropdown = (openDropdown == OpenDropdown.MOD) ? OpenDropdown.NONE : OpenDropdown.MOD;
                dropdownScroll = 0;
                return true;
            }
            if (mx >= typeDropX && mx <= typeDropX + typeDropW) {
                openDropdown = (openDropdown == OpenDropdown.TYPE) ? OpenDropdown.NONE : OpenDropdown.TYPE;
                dropdownScroll = 0;
                return true;
            }
            if (mx >= visDropX && mx <= visDropX + visDropW) {
                openDropdown = (openDropdown == OpenDropdown.VISIBILITY) ? OpenDropdown.NONE : OpenDropdown.VISIBILITY;
                dropdownScroll = 0;
                return true;
            }
            openDropdown = OpenDropdown.NONE;
            if (mx >= doneBtnX && mx <= doneBtnX + doneBtnW) {
                onClose();
                return true;
            }
            return true;
        }

        // 2. Bottom selected widget inspector buttons
        if (selectedWidget != null) {
            int barW = Math.min(460, width - 20);
            int barH = 28;
            int bx = (width - barW) / 2;
            int by = height - barH - 8;
            if (mx >= bx && mx <= bx + barW && my >= by && my <= by + barH) {
                int right = bx + barW - 6;
                if (selectedWidget.canReset()) {
                    int rw = 42;
                    if (mx >= right - rw && mx <= right) {
                        selectedWidget.reset();
                        showToast("Reset " + selectedWidget.name());
                        return true;
                    }
                    right -= (rw + 4);
                }
                if (selectedWidget.canToggle()) {
                    int tw = 44;
                    if (mx >= right - tw && mx <= right) {
                        selectedWidget.setEnabled(!selectedWidget.isEnabled());
                        rebuildVisibleWidgets();
                        return true;
                    }
                    right -= (tw + 4);
                }
                if (selectedWidget.canScale()) {
                    if (mx >= right - 18 && mx <= right) {
                        selectedWidget.setScale(selectedWidget.getScale() + 0.1);
                        return true;
                    }
                    right -= 21;
                    if (mx >= right - 18 && mx <= right) {
                        selectedWidget.setScale(selectedWidget.getScale() - 0.1);
                        return true;
                    }
                }
                return true;
            }
        }

        // 3. Corner resize handles FIRST (before body drag)
        if (button == 0 && selectedWidget != null && selectedWidget.canScale()) {
            HudSnapEngine.ResizeCorner corner = hitTestCornerHandle(selectedWidget, mx, my);
            if (corner != null) {
                int wx = clampX(selectedWidget.getX(), selectedWidget.getScaledWidth());
                int wy = clampY(selectedWidget.getY(), selectedWidget.getScaledHeight());
                int ww = selectedWidget.getScaledWidth();
                int wh = selectedWidget.getScaledHeight();

                resizingWidget = selectedWidget;
                resizeCorner = corner;
                resizeAnchorX = (corner == HudSnapEngine.ResizeCorner.SE || corner == HudSnapEngine.ResizeCorner.NE)
                    ? wx : (wx + ww);
                resizeAnchorY = (corner == HudSnapEngine.ResizeCorner.SE || corner == HudSnapEngine.ResizeCorner.SW)
                    ? wy : (wy + wh);
                return true;
            }
        }

        // 4. Hit-test widgets
        HudWidget hit = findWidgetAt(mx, my);
        if (hit != null) {
            selectedWidget = hit;
            if (button == 1 && hit.canToggle()) {
                hit.setEnabled(!hit.isEnabled());
                showToast(hit.name() + ": " + (hit.isEnabled() ? "ON" : "OFF"));
                rebuildVisibleWidgets();
                return true;
            }
            if (button == 0) {
                draggingWidget = hit;
                dragOffsetX = (int) mx - clampX(hit.getX(), hit.getScaledWidth());
                dragOffsetY = (int) my - clampY(hit.getY(), hit.getScaledHeight());
                return true;
            }
        } else {
            selectedWidget = null;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (resizingWidget != null && resizeCorner != null) {
            HudSnapEngine.ResizeResult res = HudSnapEngine.computeCornerResize(
                resizeCorner,
                resizeAnchorX,
                resizeAnchorY,
                event.x(),
                event.y(),
                resizingWidget.getWidth(),
                resizingWidget.getHeight(),
                TOOLBAR_H + 2,
                width,
                height
            );
            resizingWidget.setScale(res.scale());
            resizingWidget.setPosition(res.x(), res.y());
            return true;
        }
        if (draggingWidget == null) return false;

        int rawX = (int) Math.round(event.x()) - dragOffsetX;
        int rawY = (int) Math.round(event.y()) - dragOffsetY;

        boolean shiftHeld = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean doSnap = snapEnabled && !shiftHeld;

        List<HudSnapEngine.Box> others = new ArrayList<>(visibleWidgets.size());
        for (HudWidget w : visibleWidgets) {
            if (w == draggingWidget) continue;
            others.add(new HudSnapEngine.Box(
                w.id(),
                clampX(w.getX(), w.getScaledWidth()),
                clampY(w.getY(), w.getScaledHeight()),
                w.getScaledWidth(),
                w.getScaledHeight()
            ));
        }

        HudSnapEngine.Box moving = new HudSnapEngine.Box(
            draggingWidget.id(),
            rawX,
            rawY,
            draggingWidget.getScaledWidth(),
            draggingWidget.getScaledHeight()
        );
        HudSnapEngine.SnapResult snapped = HudSnapEngine.snap(moving, others, width, height, SNAP_THRESHOLD_PX, doSnap);
        draggingWidget.setPosition(snapped.x(), snapped.y());
        activeGuides = snapped.guides();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (resizingWidget != null) {
            resizingWidget = null;
            resizeCorner = null;
            return true;
        }
        if (draggingWidget != null) {
            draggingWidget = null;
            activeGuides = List.of();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (openDropdown != OpenDropdown.NONE) {
            int dx = Math.min(activeDropdownX(), Math.max(8, width - activeDropdownW() - 8));
            int dy = TOOLBAR_H + 2;
            int dw = activeDropdownW();
            int dh = activeDropdownHeight();
            if (mouseX >= dx && mouseX <= dx + dw && mouseY >= dy && mouseY <= dy + dh) {
                dropdownScroll -= scrollY * 20.0;
                clampDropdownScroll();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE && openDropdown != OpenDropdown.NONE) {
            openDropdown = OpenDropdown.NONE;
            dropdownScroll = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            openDropdown = OpenDropdown.NONE;
            saveAllDirty();
            minecraft.gui.setScreen(returnScreen != null ? returnScreen : new SkyHubScreen(null));
            return true;
        }
        if (selectedWidget != null) {
            int step = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;
            int dx = 0, dy = 0;
            if (key == GLFW.GLFW_KEY_LEFT) dx = -step;
            else if (key == GLFW.GLFW_KEY_RIGHT) dx = step;
            else if (key == GLFW.GLFW_KEY_UP) dy = -step;
            else if (key == GLFW.GLFW_KEY_DOWN) dy = step;

            if (dx != 0 || dy != 0) {
                selectedWidget.setPosition(
                    clampX(selectedWidget.getX() + dx, selectedWidget.getScaledWidth()),
                    clampY(selectedWidget.getY() + dy, selectedWidget.getScaledHeight())
                );
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        saveAllDirty();
        minecraft.gui.setScreen(null);
    }

    private void saveAllDirty() {
        Set<String> savedMods = new LinkedHashSet<>();
        for (HudWidget w : UnifiedRegistry.getInstance().hudWidgets()) {
            if (w.isDirty()) {
                w.save();
                savedMods.add(w.modName());
            }
        }
        if (!savedMods.isEmpty()) {
            showToast("Saved HUD layout to " + String.join(", ", savedMods));
        }
    }

    private HudWidget findWidgetAt(double mx, double my) {
        if (selectedWidget != null && isOverWidget(selectedWidget, mx, my)) {
            return selectedWidget;
        }
        for (int i = visibleWidgets.size() - 1; i >= 0; i--) {
            HudWidget w = visibleWidgets.get(i);
            if (isOverWidget(w, mx, my)) {
                return w;
            }
        }
        return null;
    }

    private void drawCornerHandle(GuiGraphicsExtractor g, int cx, int cy, int accent, boolean active) {
        int half = 3;
        int fill = active ? 0xFFFFFFFF : accent;
        int border = active ? accent : 0xFF0A0E15;
        SkyHubTheme.drawBox(g, cx - half, cy - half, half * 2, half * 2, fill, border);
    }

    private HudSnapEngine.ResizeCorner hitTestCornerHandle(HudWidget w, double mx, double my) {
        int x = clampX(w.getX(), w.getScaledWidth());
        int y = clampY(w.getY(), w.getScaledHeight());
        int bw = w.getScaledWidth();
        int bh = w.getScaledHeight();
        int r = 5;

        if (Math.abs(mx - x) <= r && Math.abs(my - y) <= r) return HudSnapEngine.ResizeCorner.NW;
        if (Math.abs(mx - (x + bw)) <= r && Math.abs(my - y) <= r) return HudSnapEngine.ResizeCorner.NE;
        if (Math.abs(mx - x) <= r && Math.abs(my - (y + bh)) <= r) return HudSnapEngine.ResizeCorner.SW;
        if (Math.abs(mx - (x + bw)) <= r && Math.abs(my - (y + bh)) <= r) return HudSnapEngine.ResizeCorner.SE;
        return null;
    }

    private boolean isOverWidget(HudWidget w, double mx, double my) {
        int x = clampX(w.getX(), w.getScaledWidth());
        int y = clampY(w.getY(), w.getScaledHeight());
        return mx >= x && mx <= x + w.getScaledWidth() && my >= y && my <= y + w.getScaledHeight();
    }

    private int clampX(int x, int w) {
        return Math.max(0, Math.min(Math.max(0, width - w), x));
    }

    private int clampY(int y, int h) {
        return Math.max(TOOLBAR_H + 2, Math.min(Math.max(TOOLBAR_H + 2, height - h), y));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
