package dev.skyhub.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudSnapEngineTest {

    @Test
    void snapsToScreenCenterAndReportsCenterGuide() {
        var moving = new HudSnapEngine.Box("w1", 448, 258, 100, 20);
        // Screen 1000x540 -> center X = 500 -> box left should snap to 450; center Y = 270 -> box top snaps to 260
        var res = HudSnapEngine.snap(moving, List.of(), 1000, 540, 5, true);
        assertEquals(450, res.x());
        assertEquals(260, res.y());
        assertEquals(2, res.guides().size());
        assertTrue(res.guides().stream().allMatch(HudSnapEngine.SnapGuide::isCenterGuide));
    }

    @Test
    void snapsToSiblingWidgetEdges() {
        var other = new HudSnapEngine.Box("other", 120, 80, 100, 30);
        var moving = new HudSnapEngine.Box("moving", 123, 113, 100, 20);
        // X aligns to other.x (120); Y aligns to other.bottom + 4 (114)
        var res = HudSnapEngine.snap(moving, List.of(other), 1000, 540, 5, true);
        assertEquals(120, res.x());
        assertEquals(114, res.y());
        assertEquals(2, res.guides().size());
    }

    @Test
    void bypassesSnapWhenDisabledButClampsToViewport() {
        var moving = new HudSnapEngine.Box("w1", -25, 999, 100, 20);
        var res = HudSnapEngine.snap(moving, List.of(), 800, 500, 5, false);
        assertEquals(0, res.x());
        assertEquals(480, res.y());
        assertTrue(res.guides().isEmpty());
    }

    @Test
    void resizesFromSouthEastAndNorthWestCornersKeepingOppositeAnchored() {
        // Base 100x40 at (100, 100). Drag SE handle to (300, 180) -> 2.0x scale, top-left stays (100, 100)
        var se = HudSnapEngine.computeCornerResize(
            HudSnapEngine.ResizeCorner.SE, 100, 100, 300, 180, 100, 40, 32, 1000, 600
        );
        assertEquals(2.0, se.scale(), 1e-4);
        assertEquals(100, se.x());
        assertEquals(100, se.y());

        // Drag NW handle with opposite anchor at (300, 200) to (150, 140) -> 1.5x scale -> new top-left (150, 140)
        var nw = HudSnapEngine.computeCornerResize(
            HudSnapEngine.ResizeCorner.NW, 300, 200, 150, 140, 100, 40, 32, 1000, 600
        );
        assertEquals(1.5, nw.scale(), 1e-4);
        assertEquals(150, nw.x());
        assertEquals(140, nw.y());
    }
}
