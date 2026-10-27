package com.xss.it.juxtray;

/**
 * One tray icon as seen by a platform back end.
 *
 * <p>This is the whole surface a back end must implement. It is deliberately
 * small and "dumb". It knows nothing about JavaFX menus, properties or event
 * handlers; all of that is pure Java in the public {@code TrayIcon}.</p>
 *
 * <p><strong>Threading:</strong> every method may be called from any thread.
 * Implementations serialise native work onto their own thread as needed.
 * Mutators are asynchronous: they return before the shell has been
 * updated.</p>
 */
public interface NativeTray extends AutoCloseable {

    /**
     * Replaces the icon image.
     *
     * @param pixels new image, already sized to {@link #iconSize()}, or
     *               {@code null} to show an empty slot
     */
    void setIcon(IconPixels pixels);

    /**
     * Replaces the hover tooltip.
     *
     * @param tooltip tooltip text, or {@code null} for none; long text is
     *                truncated to what the platform supports
     */
    void setTooltip(String tooltip);

    /**
     * Shows or hides the icon without destroying it.
     *
     * @param visible {@code true} to show the icon
     */
    void setVisible(boolean visible);

    /**
     * Ideal edge length of the square icon bitmap, in device pixels, for the
     * DPI of the monitor that currently shows the icon. When it changes, the
     * back end reports {@link NativeTrayEvent.Type#ICON_SIZE_CHANGED}.
     *
     * @return preferred icon size, for example 16, 20, 24 or 32
     */
    int iconSize();

    /**
     * Current mouse cursor position in device pixels.
     *
     * @return the cursor position, or {@code null} if the OS refused
     */
    PhysicalPoint cursorPosition();

    /**
     * Brings the top-level window with the given title to the foreground.
     *
     * <p>Tray menus must belong to the foreground window, or they will not
     * close when the user clicks elsewhere. JavaFX has no public way to force
     * this, so the back end does it natively.</p>
     *
     * @param title exact title of the window to activate
     */
    void activateWindow(String title);

    /**
     * Removes the icon from the tray and frees all native resources.
     * Calling it again has no effect.
     */
    @Override
    void close();
}
