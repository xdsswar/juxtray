package com.xss.it.juxtray;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

/**
 * Tests for notification stacking and the automatic corner choice.
 */
class NotificationPlacementTest {

    /** A 1920x1080 screen. */
    private static final Rectangle2D SCREEN = new Rectangle2D(0, 0, 1920, 1080);

    /** Work area with a 48 px taskbar at the bottom. */
    private static final Rectangle2D BOTTOM_TASKBAR = new Rectangle2D(0, 0, 1920, 1032);

    /** Notification content size used by most tests. */
    private static final Dimension2D SIZE = new Dimension2D(360, 100);

    /** The automatic corner follows the taskbar. */
    @Test
    void autoCornerFollowsTaskbar() {
        assertEquals(Pos.BOTTOM_RIGHT, NotificationPlacement.autoCorner(SCREEN, BOTTOM_TASKBAR));
        assertEquals(Pos.TOP_RIGHT,
                NotificationPlacement.autoCorner(SCREEN, new Rectangle2D(0, 48, 1920, 1032)));
        assertEquals(Pos.BOTTOM_LEFT,
                NotificationPlacement.autoCorner(SCREEN, new Rectangle2D(64, 0, 1856, 1080)));
        // A taskbar on the right leaves the work area's left edge where it was.
        assertEquals(Pos.BOTTOM_RIGHT,
                NotificationPlacement.autoCorner(SCREEN, new Rectangle2D(0, 0, 1856, 1080)));
    }

    /** Bottom-right: the newest sits in the corner, older ones above it. */
    @Test
    void bottomRightStacksUpwards() {
        List<Point2D> p = NotificationPlacement.stack(BOTTOM_TASKBAR, Pos.BOTTOM_RIGHT,
                List.of(SIZE, new Dimension2D(360, 60)));
        double m = NotificationPlacement.MARGIN;
        assertEquals(new Point2D(1920 - m - 360, 1032 - m - 100), p.get(0));
        assertEquals(new Point2D(1920 - m - 360, 1032 - m - 100 - NotificationPlacement.GAP - 60), p.get(1));
    }

    /** Top-left: the newest sits in the corner, older ones below it. */
    @Test
    void topLeftStacksDownwards() {
        Rectangle2D area = new Rectangle2D(0, 48, 1920, 1032);
        List<Point2D> p = NotificationPlacement.stack(area, Pos.TOP_LEFT, List.of(SIZE, SIZE));
        double m = NotificationPlacement.MARGIN;
        assertEquals(new Point2D(m, 48 + m), p.get(0));
        assertEquals(new Point2D(m, 48 + m + 100 + NotificationPlacement.GAP), p.get(1));
    }

    /** Centre positions are centred horizontally in the work area. */
    @Test
    void centreIsCentred() {
        List<Point2D> p = NotificationPlacement.stack(BOTTOM_TASKBAR, Pos.BOTTOM_CENTER, List.of(SIZE));
        assertEquals((1920 - 360) / 2.0, p.getFirst().getX());
    }

    /** Works on a secondary screen with negative coordinates. */
    @Test
    void negativeCoordinates() {
        Rectangle2D area = new Rectangle2D(-1280, -200, 1280, 984);
        List<Point2D> p = NotificationPlacement.stack(area, Pos.BOTTOM_RIGHT, List.of(SIZE));
        double m = NotificationPlacement.MARGIN;
        assertEquals(new Point2D(-m - 360, 784 - m - 100), p.getFirst());
    }
}
