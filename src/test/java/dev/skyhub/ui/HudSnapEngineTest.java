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
}
