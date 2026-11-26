package dev.skyhub.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-math snapping engine for the Phase 2 Unified HUD Canvas.
 * Snaps dragged widgets to screen edges, screen centerlines, and sibling widget edges,
 * producing visual guide lines for crisp alignment.
 */
public final class HudSnapEngine {
    public record Box(String id, int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public int centerX() { return x + width / 2; }
        public int centerY() { return y + height / 2; }
    }

    public record SnapGuide(boolean vertical, int position, boolean isCenterGuide) {}

    public record SnapResult(int x, int y, List<SnapGuide> guides) {}

    public enum ResizeCorner {
        NW, NE, SW, SE
    }

    public record ResizeResult(int x, int y, double scale) {}

    private HudSnapEngine() {}

    /**
     * Computes uniform scale and anchored top-left position when dragging one of the
     * 4 corner handles of a HUD widget. Projects the drag vector onto the widget's
     * aspect-ratio ray so dragging at any angle scales smoothly while keeping the
     * opposite corner anchored in place.
     */
    public static ResizeResult computeCornerResize(
        ResizeCorner corner,
        int anchorX,
        int anchorY,
        double mouseX,
        double mouseY,
        int baseWidth,
        int baseHeight,
        int minY,
        int screenWidth,
        int screenHeight
    ) {
        double bw = Math.max(1.0, baseWidth);
        double bh = Math.max(1.0, baseHeight);

        double desiredW = switch (corner) {
            case SE, NE -> mouseX - anchorX;
            case SW, NW -> anchorX - mouseX;
        };
        double desiredH = switch (corner) {
            case SE, SW -> mouseY - anchorY;
            case NE, NW -> anchorY - mouseY;
        };

        // Closest point on the uniform-scale ray (s * bw, s * bh)
        double rawScale = (desiredW * bw + desiredH * bh) / (bw * bw + bh * bh);
        double clampedScale = Math.max(0.25, Math.min(4.0, Math.round(rawScale * 100.0) / 100.0));

        int scaledW = Math.max(44, (int) Math.round(bw * clampedScale));
        int scaledH = Math.max(18, (int) Math.round(bh * clampedScale));

        int newX = switch (corner) {
            case SE, NE -> anchorX;
            case SW, NW -> anchorX - scaledW;
        };
        int newY = switch (corner) {
            case SE, SW -> anchorY;
            case NE, NW -> anchorY - scaledH;
        };

        newX = Math.max(0, Math.min(Math.max(0, screenWidth - scaledW), newX));
        newY = Math.max(minY, Math.min(Math.max(minY, screenHeight - scaledH), newY));
        return new ResizeResult(newX, newY, clampedScale);
    }

    public static SnapResult snap(
        Box moving,
        List<Box> others,
        int screenWidth,
        int screenHeight,
        int thresholdPx,
        boolean snappingEnabled
    ) {
        int clampedX = Math.max(0, Math.min(Math.max(0, screenWidth - moving.width()), moving.x()));
        int clampedY = Math.max(0, Math.min(Math.max(0, screenHeight - moving.height()), moving.y()));

        if (!snappingEnabled || thresholdPx <= 0) {
            return new SnapResult(clampedX, clampedY, List.of());
        }

        int bestX = clampedX;
        int bestDx = thresholdPx + 1;
        SnapGuide xGuide = null;

        int bestY = clampedY;
        int bestDy = thresholdPx + 1;
        SnapGuide yGuide = null;

        // 1. Screen edges & screen center
        int screenCenterX = screenWidth / 2;
        int screenCenterY = screenHeight / 2;

        int[][] xScreenTargets = {
            {4, 0, 0},                                       // left margin 4px
            {screenWidth - 4 - moving.width(), screenWidth - 4, 0}, // right margin 4px
            {screenCenterX - moving.width() / 2, screenCenterX, 1}  // horizontal center
        };
        for (int[] t : xScreenTargets) {
            int d = Math.abs(clampedX - t[0]);
            if (d <= thresholdPx && d < bestDx) {
                bestDx = d;
                bestX = t[0];
                xGuide = new SnapGuide(true, t[1], t[2] == 1);
            }
        }

        int[][] yScreenTargets = {
            {4, 0, 0},
            {screenHeight - 4 - moving.height(), screenHeight - 4, 0},
            {screenCenterY - moving.height() / 2, screenCenterY, 1}
        };
        for (int[] t : yScreenTargets) {
            int d = Math.abs(clampedY - t[0]);
            if (d <= thresholdPx && d < bestDy) {
                bestDy = d;
                bestY = t[0];
                yGuide = new SnapGuide(false, t[1], t[2] == 1);
            }
        }

        // 2. Sibling widget edges (left/right/center, top/bottom/center)
        if (others != null) {
            for (Box other : others) {
                if (other.id().equals(moving.id())) continue;

                // X alignments
                int[][] xCandidates = {
                    {other.x(), other.x()},                                       // left -> left
                    {other.right() - moving.width(), other.right()},              // right -> right
                    {other.right() + 4, other.right() + 4},                       // left -> other right + gap
                    {other.x() - moving.width() - 4, other.x() - 4},              // right -> other left - gap
                    {other.centerX() - moving.width() / 2, other.centerX()}       // center -> center
                };
                for (int[] c : xCandidates) {
                    int d = Math.abs(clampedX - c[0]);
                    if (d <= thresholdPx && d < bestDx) {
                        bestDx = d;
                        bestX = c[0];
                        xGuide = new SnapGuide(true, c[1], false);
                    }
                }

                // Y alignments
                int[][] yCandidates = {
                    {other.y(), other.y()},
                    {other.bottom() - moving.height(), other.bottom()},
                    {other.bottom() + 4, other.bottom() + 4},
                    {other.y() - moving.height() - 4, other.y() - 4},
                    {other.centerY() - moving.height() / 2, other.centerY()}
                };
                for (int[] c : yCandidates) {
                    int d = Math.abs(clampedY - c[0]);
                    if (d <= thresholdPx && d < bestDy) {
                        bestDy = d;
                        bestY = c[0];
                        yGuide = new SnapGuide(false, c[1], false);
                    }
                }
            }
        }

        bestX = Math.max(0, Math.min(Math.max(0, screenWidth - moving.width()), bestX));
        bestY = Math.max(0, Math.min(Math.max(0, screenHeight - moving.height()), bestY));

        List<SnapGuide> guides = new ArrayList<>(2);
        if (xGuide != null) guides.add(xGuide);
        if (yGuide != null) guides.add(yGuide);
        return new SnapResult(bestX, bestY, guides);
    }
}
