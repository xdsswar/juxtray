package com.xss.it.juxtray;

import javafx.scene.input.MouseButton;

/**
 * Raw mouse activity on a native tray icon, as produced by a back end.
 *
 * <p>Back ends create these on their own native thread. The public
 * {@code TrayIcon} moves them to the JavaFX thread, converts coordinates and
 * turns them into public {@code TrayIconEvent}s.</p>
 *
 * @param type   what happened
 * @param button the button involved, or {@code null} for hover and size events
 * @param bounds icon bounds in device pixels, or {@code null} if unknown
 */
public record NativeTrayEvent(Type type, MouseButton button, PhysicalRect bounds) {

    /**
     * Kinds of raw tray events.
     */
    public enum Type {

        /** A button went down. */
        PRESSED,

        /** A button came up. */
        RELEASED,

        /** A button was double-clicked. */
        DOUBLE_CLICKED,

        /** The cursor entered the icon. */
        ENTERED,

        /** The cursor moved inside the icon. */
        MOVED,

        /** The cursor left the icon. */
        EXITED,

        /**
         * The ideal icon size changed (display scaling or monitor layout
         * changed). Not a mouse event: the icon image must be resampled to
         * the new {@code NativeTray.iconSize()} and set again.
         */
        ICON_SIZE_CHANGED
    }
}
