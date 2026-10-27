package com.xss.it.juxtray;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Dimension2D;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;

/**
 * Pure placement math for tray notifications.
 *
 * <p>Everything is in JavaFX screen coordinates (logical pixels), so the
 * result is DPI-correct by construction and needs no native code.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Notifications stack in one corner (or edge centre) of the work area,
 *       {@link #MARGIN} away from its edges.</li>
 *   <li>The newest notification (index 0) sits closest to the edge; older
 *       ones are pushed away from it, {@link #GAP} apart.</li>
 *   <li>With no explicit position, the corner next to the taskbar is used:
 *       bottom-right for the usual bottom taskbar, top-right for a top one,
 *       bottom-left for a taskbar on the left. That is where the tray is, and
 *       where native notifications appear.</li>
 * </ul>
 */
public final class NotificationPlacement {

    /** Distance between the notifications and the edges of the work area. */
    public static final double MARGIN = 12;

    /** Vertical distance between two stacked notifications. */
    public static final double GAP = 8;

    /** Utility class: no instances. */
    private NotificationPlacement() {
        throw new AssertionError("No instances");
    }

    /**
     * Picks the corner nearest to the taskbar, by comparing a screen's full
     * bounds with its work area: the taskbar is wherever they differ.
     *
     * @param screen   full bounds of the screen
     * @param workArea visual bounds of the same screen (screen minus taskbar)
     * @return {@link Pos#TOP_LEFT}, {@link Pos#TOP_RIGHT}, {@link Pos#BOTTOM_LEFT}
     *         or {@link Pos#BOTTOM_RIGHT}
     */
    public static Pos autoCorner(Rectangle2D screen, Rectangle2D workArea) {
        boolean taskbarTop = workArea.getMinY() > screen.getMinY();
        boolean taskbarLeft = workArea.getMinX() > screen.getMinX();
        if (taskbarTop) {
            return taskbarLeft ? Pos.TOP_LEFT : Pos.TOP_RIGHT;
        }
        return taskbarLeft ? Pos.BOTTOM_LEFT : Pos.BOTTOM_RIGHT;
    }

    /**
     * Computes the top-left corner of each notification in a stack.
     *
     * <p>Only the vertical ({@link VPos#TOP} or anything else, meaning
     * bottom) and horizontal ({@link HPos#LEFT}, {@link HPos#CENTER} or
     * {@link HPos#RIGHT}) parts of {@code position} are used.</p>
     *
     * @param workArea usable area of the screen (visual bounds)
     * @param position where to stack the notifications
     * @param sizes    content size of each notification (without shadow), newest first
     * @return the top-left corner of each notification's content, in the same order
     */
    public static List<Point2D> stack(Rectangle2D workArea, Pos position, List<Dimension2D> sizes) {
        boolean top = position.getVpos() == VPos.TOP;
        HPos horizontal = position.getHpos();
        List<Point2D> result = new ArrayList<>(sizes.size());
        // Distance from the anchor edge to the next free slot.
        double offset = MARGIN;
        for (Dimension2D size : sizes) {
            double x = switch (horizontal) {
                case LEFT -> workArea.getMinX() + MARGIN;
                case CENTER -> workArea.getMinX() + (workArea.getWidth() - size.getWidth()) / 2;
                case RIGHT -> workArea.getMaxX() - MARGIN - size.getWidth();
            };
            double y = top
                    ? workArea.getMinY() + offset
                    : workArea.getMaxY() - offset - size.getHeight();
            result.add(new Point2D(x, y));
            offset += size.getHeight() + GAP;
        }
        return result;
    }
}
