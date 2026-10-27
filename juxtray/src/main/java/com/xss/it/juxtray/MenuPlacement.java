package com.xss.it.juxtray;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/**
 * Pure placement math for tray menus, the way native tray menus behave.
 *
 * <p>Everything is in JavaFX screen coordinates (logical pixels). JavaFX
 * already applies each monitor's scale factor in that space, so this logic
 * is DPI-independent and needs no native code.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><strong>Vertical:</strong> open <em>above</em> the anchor (the taskbar
 *       is usually at the bottom). If that does not fit, open below.</li>
 *   <li><strong>Horizontal:</strong> open to the <em>right</em> of the anchor.
 *       If that does not fit, open to the left.</li>
 *   <li>Finally, clamp the menu into the work area (the screen minus the
 *       taskbar). This makes a menu opened from a bottom taskbar sit just
 *       above it, from a top taskbar just below it, and from a side taskbar
 *       right beside it.</li>
 * </ul>
 */
public final class MenuPlacement {

    /** Utility class: no instances. */
    private MenuPlacement() {
        throw new AssertionError("No instances");
    }

    /**
     * Computes where the top-left corner of the menu content goes.
     *
     * @param anchor   where the menu was requested (normally the cursor)
     * @param width    width of the menu content (without shadow)
     * @param height   height of the menu content (without shadow)
     * @param workArea usable area of the anchor's screen (visual bounds)
     * @return top-left corner of the menu content
     */
    public static Point2D place(Point2D anchor, double width, double height, Rectangle2D workArea) {
        double x = anchor.getX();
        if (x + width > workArea.getMaxX()) {
            // No room on the right: open leftwards, ending at the anchor.
            x = anchor.getX() - width;
        }

        double y = anchor.getY() - height;
        if (y < workArea.getMinY()) {
            // No room above: open downwards, starting at the anchor.
            y = anchor.getY();
        }

        return new Point2D(
                clamp(x, workArea.getMinX(), workArea.getMaxX() - width),
                clamp(y, workArea.getMinY(), workArea.getMaxY() - height));
    }

    /**
     * Clamps a value into {@code [min, max]}. When the menu is bigger than the
     * area ({@code max < min}), it is pinned to {@code min}, so its top-left
     * corner (and the first items) stay visible.
     *
     * @param value the value
     * @param min   lower bound
     * @param max   upper bound
     * @return the clamped value
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }
}
