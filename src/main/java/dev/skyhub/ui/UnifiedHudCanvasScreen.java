package dev.skyhub.ui;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase 2 Core UI: Unified Drag-and-Drop HUD Canvas across SkyHanni, Skyblocker,
 * Odin, SkyOcean, CustomScoreboard, and Stella.
 * Edits live HUD coordinates/scale directly and invokes each dirty mod's native save hook.
 */
public final class UnifiedHudCanvasScreen extends Screen {
    private static final int SNAP_THRESHOLD_PX = 5;
    private static final int TOOLBAR_H = 30;

    private final Screen returnScreen;

    private String filterModId = null; // null = All HUD mods
    private boolean showHidden = true;
    private boolean snapEnabled = true;

    private HudWidget selectedWidget = null;
    private HudWidget draggingWidget = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private List<HudSnapEngine.SnapGuide> activeGuides = List.of();

    private List<HudWidget> visibleWidgets = List.of();
    private List<ModFilterPill> modFilterPills = List.of();

    private String toast = "";
    private long toastUntil = 0L;

    // Toolbar button positions
    private int backBtnX, backBtnW;
    private int hiddenBtnX, hiddenBtnW;
    private int snapBtnX, snapBtnW;
    private int saveBtnX, saveBtnW;

    private record ModFilterPill(String modId, String label, int accentColor, int x, int w) {}

    public UnifiedHudCanvasScreen(Screen returnScreen) {
        super(Component.translatable("skyhub.hud.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        UnifiedRegistry.getInstance().ensureInitialized();
        rebuildVisibleWidgets();
    }

    private void rebuildVisibleWidgets() {
        List<HudWidget> all = UnifiedRegistry.getInstance().hudWidgets();

        // Build distinct mod filter pills
        Map<String, String> hudMods = new LinkedHashMap<>();
        for (HudWidget w : all) {
            hudMods.putIfAbsent(w.modId(), w.modName());
        }

        List<HudWidget> filtered = new ArrayList<>();
        for (HudWidget w : all) {
            if (filterModId != null && !filterModId.equals(w.modId())) continue;
            if (!showHidden && !w.isEnabled()) continue;
            filtered.add(w);
        }
        visibleWidgets = filtered;

        if (selectedWidget != null && !visibleWidgets.contains(selectedWidget)) {
            selectedWidget = null;
        }

        computeToolbarLayout(hudMods);
    }

    private void computeToolbarLayout(Map<String, String> hudMods) {
        int x = 8;
        backBtnX = x;
        backBtnW = 78;
        x += backBtnW + 6;

        List<ModFilterPill> pills = new ArrayList<>();
        int allW = 30;
        pills.add(new ModFilterPill(null, "All", SkyHubTheme.ACCENT_CYAN, x, allW));
        x += allW + 4;

        int right = width - 8;
        saveBtnW = 74;
        saveBtnX = right - saveBtnW;

        snapBtnW = 58;
        snapBtnX = saveBtnX - 5 - snapBtnW;

        hiddenBtnW = 68;
        hiddenBtnX = snapBtnX - 5 - hiddenBtnW;

        int maxPillRight = hiddenBtnX - 10;
        for (Map.Entry<String, String> e : hudMods.entrySet()) {
            String shortLabel = ModCatalog.shortLabel(e.getKey(), e.getValue());
            int pw = Math.max(34, font.width(shortLabel) + 12);
            if (x + pw > maxPillRight) break;
            pills.add(new ModFilterPill(e.getKey(), shortLabel, ModCatalog.accentColor(e.getKey()), x, pw));
            x += pw + 4;
        }
        modFilterPills = pills;
    }

    private void showToast(String msg) {
        this.toast = msg;
        this.toastUntil = System.currentTimeMillis() + 2400L;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Light translucent scrim so the live game world remains visible behind HUD boxes
        g.fill(0, 0, width, height, 0x7A070A10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBackground(g, mouseX, mouseY, partialTick);

        // Subtle alignment grid centerlines (very faint)
        g.verticalLine(width / 2, TOOLBAR_H, height, 0x22FFFFFF);
        g.horizontalLine(0, width, height / 2, 0x22FFFFFF);

        // Render active snap guides when dragging
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
            g.centeredText(font, "No HUD widgets discovered for the current filter.", width / 2, height / 2 - 8, SkyHubTheme.TEXT_PRIMARY);
            g.centeredText(font, "Join Hypixel SkyBlock or open a mod's native menu to initialize HUD registries.", width / 2, height / 2 + 8, SkyHubTheme.TEXT_SECONDARY);
        } else {
            for (HudWidget w : visibleWidgets) {
                if (w == selectedWidget) continue; // draw selected on top last
                renderWidgetBox(g, w, false, isOverWidget(w, mouseX, mouseY));
            }
            if (selectedWidget != null) {
                renderWidgetBox(g, selectedWidget, true, isOverWidget(selectedWidget, mouseX, mouseY));
            }
        }

        // Top floating toolbar
        renderToolbar(g, mouseX, mouseY);

        // Bottom inspector pill for selected widget
        if (selectedWidget != null) {
            renderSelectedInspector(g, selectedWidget, mouseX, mouseY);
        } else {
            String help = "Drag widgets to move  \u00B7  Scroll to scale  \u00B7  Arrow keys nudge  \u00B7  Hold Shift to bypass snap";
            int hw = font.width(help) + 18;
            int hx = (width - hw) / 2;
            int hy = height - 24;
            SkyHubTheme.drawBox(g, hx, hy, hw, 18, 0xDD0C1018, SkyHubTheme.BORDER_SUBTLE);
            g.centeredText(font, help, width / 2, hy + 5, SkyHubTheme.TEXT_SECONDARY);
        }

        if (System.currentTimeMillis() < toastUntil && !toast.isBlank()) {
            int tw = font.width(toast) + 18;
            SkyHubTheme.drawBox(g, (width - tw) / 2, TOOLBAR_H + 8, tw, 18, 0xEE101824, SkyHubTheme.ACCENT_GREEN);
            g.centeredText(font, toast, width / 2, TOOLBAR_H + 13, SkyHubTheme.ACCENT_GREEN);
        }
    }

    private void renderWidgetBox(GuiGraphicsExtractor g, HudWidget w, boolean selected, boolean hovered) {
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
        if (selected) {
            // Crisp corner handles
            int hs = 4;
            g.fill(x - 1, y - 1, x - 1 + hs, y - 1 + hs, accent);
            g.fill(x + bw - hs + 1, y - 1, x + bw + 1, y - 1 + hs, accent);
            g.fill(x - 1, y + bh - hs + 1, x - 1 + hs, y + bh + 1, accent);
            g.fill(x + bw - hs + 1, y + bh - hs + 1, x + bw + 1, y + bh + 1, accent);
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

        for (ModFilterPill pill : modFilterPills) {
            boolean active = (pill.modId() == null && filterModId == null)
                || (pill.modId() != null && pill.modId().equals(filterModId));
            SkyHubTheme.drawPill(g, font, pill.x(), py, pill.w(), 20, pill.label(),
                active ? SkyHubTheme.withAlpha(pill.accentColor(), 0x44) : SkyHubTheme.CARD_BG,
                active ? pill.accentColor() : SkyHubTheme.BORDER_SUBTLE,
                active ? SkyHubTheme.TEXT_PRIMARY : SkyHubTheme.TEXT_SECONDARY);
        }

        SkyHubTheme.drawPill(g, font, hiddenBtnX, py, hiddenBtnW, 20,
            showHidden ? "All HUDs" : "Active",
            showHidden ? SkyHubTheme.CARD_BG : SkyHubTheme.ACCENT_GREEN_BG,
            showHidden ? SkyHubTheme.BORDER_SUBTLE : SkyHubTheme.ACCENT_GREEN,
            showHidden ? SkyHubTheme.TEXT_SECONDARY : SkyHubTheme.ACCENT_GREEN);

        SkyHubTheme.drawPill(g, font, snapBtnX, py, snapBtnW, 20,
            snapEnabled ? "Snap: ON" : "Snap: OFF",
            snapEnabled ? SkyHubTheme.withAlpha(SkyHubTheme.ACCENT_CYAN, 0x33) : SkyHubTheme.CARD_BG,
            snapEnabled ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.BORDER_SUBTLE,
            snapEnabled ? SkyHubTheme.ACCENT_CYAN : SkyHubTheme.TEXT_MUTED);

        SkyHubTheme.drawPill(g, font, saveBtnX, py, saveBtnW, 20, "Save & Close",
            SkyHubTheme.ACCENT_GREEN_BG, SkyHubTheme.ACCENT_GREEN, SkyHubTheme.ACCENT_GREEN);
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

        // 1. Toolbar clicks
        if (my <= TOOLBAR_H) {
            if (mx >= backBtnX && mx <= backBtnX + backBtnW) {
                saveAllDirty();
                minecraft.gui.setScreen(returnScreen != null ? returnScreen : new SkyHubScreen(null));
                return true;
            }
            for (ModFilterPill pill : modFilterPills) {
                if (mx >= pill.x() && mx <= pill.x() + pill.w()) {
                    filterModId = pill.modId();
                    rebuildVisibleWidgets();
                    return true;
                }
            }
            if (mx >= hiddenBtnX && mx <= hiddenBtnX + hiddenBtnW) {
                showHidden = !showHidden;
                rebuildVisibleWidgets();
                return true;
            }
            if (mx >= snapBtnX && mx <= snapBtnX + snapBtnW) {
                snapEnabled = !snapEnabled;
                return true;
            }
            if (mx >= saveBtnX && mx <= saveBtnX + saveBtnW) {
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

        // 3. Hit-test widgets (topmost / selected first)
        HudWidget hit = findWidgetAt(mx, my);
        if (hit != null) {
            selectedWidget = hit;
            if (button == 1 && hit.canToggle()) {
                hit.setEnabled(!hit.isEnabled());
                showToast(hit.name() + ": " + (hit.isEnabled() ? "ON" : "OFF"));
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
        if (draggingWidget != null) {
            draggingWidget = null;
            activeGuides = List.of();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HudWidget target = findWidgetAt(mouseX, mouseY);
        if (target == null) target = selectedWidget;
        if (target != null && target.canScale()) {
            double delta = scrollY > 0 ? 0.05 : -0.05;
            target.setScale(target.getScale() + delta);
            selectedWidget = target;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_TAB) {
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
