package com.xss.it.juxtray;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

/**
 * Tests for tray menu placement against the four taskbar positions.
 */
class MenuPlacementTest {

    /** A 1920x1080 screen with a 48 px taskbar at the bottom. */
    private static final Rectangle2D BOTTOM_TASKBAR = new Rectangle2D(0, 0, 1920, 1032);

    /** Menu content width. */
    private static final double W = 200;

    /** Menu content height. */
    private static final double H = 300;

    /** Clicking in a bottom taskbar puts the menu just above it, starting at the cursor. */
    @Test
    void bottomTaskbarOpensAboveIt() {
        Point2D p = MenuPlacement.place(new Point2D(1500, 1060), W, H, BOTTOM_TASKBAR);
        assertEquals(new Point2D(1500, 1032 - H), p);
    }

    /** Near the right edge the menu flips left so it ends at the cursor. */
    @Test
    void rightEdgeFlipsLeft() {
        Point2D p = MenuPlacement.place(new Point2D(1900, 1060), W, H, BOTTOM_TASKBAR);
        assertEquals(new Point2D(1900 - W, 1032 - H), p);
    }

    /** A top taskbar: no room above, so the menu opens below, inside the work area. */
    @Test
    void topTaskbarOpensBelowIt() {
        Rectangle2D area = new Rectangle2D(0, 48, 1920, 1032);
        Point2D p = MenuPlacement.place(new Point2D(1500, 20), W, H, area);
        assertEquals(new Point2D(1500, 48), p);
    }

    /** A left taskbar: the menu is pushed into the work area, right of the taskbar. */
    @Test
    void leftTaskbarOpensBesideIt() {
        Rectangle2D area = new Rectangle2D(48, 0, 1872, 1080);
        Point2D p = MenuPlacement.place(new Point2D(20, 900), W, H, area);
        assertEquals(new Point2D(48, 900 - H), p);
    }

    /** A right taskbar: the menu opens leftwards and stays out of the taskbar. */
    @Test
    void rightTaskbarOpensBesideIt() {
        Rectangle2D area = new Rectangle2D(0, 0, 1872, 1080);
        Point2D p = MenuPlacement.place(new Point2D(1900, 900), W, H, area);
        assertEquals(new Point2D(1872 - W, 900 - H), p);
    }

    /** Negative coordinates (a monitor left of / above the primary) work the same way. */
    @Test
    void worksOnSecondaryMonitorsWithNegativeCoordinates() {
        Rectangle2D area = new Rectangle2D(-1920, -1080, 1920, 1032);
        Point2D p = MenuPlacement.place(new Point2D(-400, -30), W, H, area);
        assertEquals(new Point2D(-400, -48 - H), p);
    }
}
