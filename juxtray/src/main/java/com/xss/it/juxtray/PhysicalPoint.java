package com.xss.it.juxtray;

/**
 * A point in <em>physical</em> (device) pixels, as reported by the OS.
 *
 * <p>Not to be confused with JavaFX screen coordinates, which are logical
 * pixels divided by the monitor's scale factor. Conversion happens in
 * the public {@code TrayIcon} on the JavaFX thread.</p>
 *
 * @param x horizontal position in device pixels
 * @param y vertical position in device pixels
 */
public record PhysicalPoint(int x, int y) {
}
