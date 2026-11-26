package dev.skyhub.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared dark obsidian/slate visual design system for SkyHub UI screens.
 */
public final class SkyHubTheme {
    // Surfaces
    public static final int BACKDROP       = 0xC8090C12;
    public static final int PANEL_BG       = 0xF210141D;
    public static final int SIDEBAR_BG     = 0xF50C1018;
    public static final int CARD_BG        = 0xEE161C28;
    public static final int CARD_HOVER     = 0xF51D2536;
    public static final int CARD_SELECTED  = 0xF5212B3F;
    public static final int INPUT_BG       = 0xFF0A0E15;

    // Borders
    public static final int BORDER_SUBTLE  = 0xFF232C3D;
    public static final int BORDER_ACTIVE  = 0xFF38BDF8;
    public static final int BORDER_MUTED   = 0xFF1A2130;

    // Text
    public static final int TEXT_PRIMARY   = 0xFFF1F5F9;
    public static final int TEXT_SECONDARY = 0xFF94A3B8;
    public static final int TEXT_MUTED     = 0xFF64748B;

    // Semantic accents
    public static final int ACCENT_CYAN    = 0xFF38BDF8;
    public static final int ACCENT_GREEN   = 0xFF22C55E;
    public static final int ACCENT_GREEN_BG = 0xFF14532D;
    public static final int ACCENT_AMBER   = 0xFFF59E0B;
    public static final int ACCENT_AMBER_BG = 0xFF451A03;
    public static final int ACCENT_RED     = 0xFFEF4444;
    public static final int GUIDE_CENTER   = 0xDDF472B6;
    public static final int GUIDE_EDGE     = 0xDD38BDF8;

    private SkyHubTheme() {}

    public static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    public static void drawBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int fillColor, int borderColor) {
        g.fill(x, y, x + w, y + h, fillColor);
        g.outline(x, y, w, h, borderColor);
    }

    public static void drawPill(
        GuiGraphicsExtractor g,
        Font font,
        int x,
        int y,
        int w,
        int h,
        String label,
        int fillColor,
        int borderColor,
        int textColor
    ) {
        drawBox(g, x, y, w, h, fillColor, borderColor);
        int textX = x + (w - font.width(label)) / 2;
        int textY = y + (h - font.lineHeight) / 2 + 1;
        g.text(font, label, textX, textY, textColor, false);
    }

    public static int drawBadge(
        GuiGraphicsExtractor g,
        Font font,
        int x,
        int y,
        String label,
        int accentColor
    ) {
        int padX = 4;
        int w = font.width(label) + padX * 2;
        int h = 12;
        int bg = withAlpha(accentColor, 0x34);
        int border = withAlpha(accentColor, 0x99);
        drawBox(g, x, y, w, h, bg, border);
        g.text(font, label, x + padX, y + 2, accentColor, false);
        return w;
    }

    public static String truncate(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 8 || font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellW = font.width(ellipsis);
        String trimmed = font.plainSubstrByWidth(text, Math.max(0, maxWidth - ellW));
        return trimmed + ellipsis;
    }

    /**
     * Draws a crisp pixel chevron (right when collapsed, down when expanded) inside a 7x7 box.
     */
    public static void drawChevron(GuiGraphicsExtractor g, int x, int y, boolean expanded, int color) {
        if (expanded) {
            // Down-pointing chevron (7x4)
            g.fill(x, y + 1, x + 7, y + 2, color);
            g.fill(x + 1, y + 2, x + 6, y + 3, color);
            g.fill(x + 2, y + 3, x + 5, y + 4, color);
            g.fill(x + 3, y + 4, x + 4, y + 5, color);
        } else {
            // Right-pointing chevron (4x7)
            g.fill(x + 1, y, x + 2, y + 7, color);
            g.fill(x + 2, y + 1, x + 3, y + 6, color);
            g.fill(x + 3, y + 2, x + 4, y + 5, color);
            g.fill(x + 4, y + 3, x + 5, y + 4, color);
        }
    }

    /**
     * Draws a crisp 10x10 pixel-art circular refresh icon centered in a square button.
     */
    public static void drawRefreshIcon(GuiGraphicsExtractor g, int cx, int cy, int color) {
        int ox = cx - 5;
        int oy = cy - 5;
        // Top arc
        g.fill(ox + 3, oy + 1, ox + 8, oy + 2, color);
        g.fill(ox + 2, oy + 2, ox + 3, oy + 3, color);
        // Left arc
        g.fill(ox + 1, oy + 3, ox + 2, oy + 7, color);
        // Bottom arc
        g.fill(ox + 2, oy + 7, ox + 3, oy + 8, color);
        g.fill(ox + 3, oy + 8, ox + 7, oy + 9, color);
        g.fill(ox + 7, oy + 7, ox + 8, oy + 8, color);
        // Arrowhead on top-right
        g.fill(ox + 7, oy, ox + 8, oy + 4, color);
        g.fill(ox + 8, oy + 1, ox + 9, oy + 4, color);
        g.fill(ox + 6, oy + 3, ox + 9, oy + 4, color);
    }
}
