package com.xss.it.juxtray;

/**
 * A rectangle in <em>physical</em> (device) pixels, using the Win32
 * {@code RECT} convention: {@code right} and {@code bottom} are exclusive.
 *
 * @param left   left edge, inclusive
 * @param top    top edge, inclusive
 * @param right  right edge, exclusive
 * @param bottom bottom edge, exclusive
 */
public record PhysicalRect(int left, int top, int right, int bottom) {

    /**
     * Width of the rectangle.
     *
     * @return {@code right - left}, never negative
     */
    public int width() {
        return Math.max(0, right - left);
    }

    /**
     * Height of the rectangle.
     *
     * @return {@code bottom - top}, never negative
     */
    public int height() {
        return Math.max(0, bottom - top);
    }

    /**
     * Tells whether a point lies inside this rectangle.
     *
     * @param point point to test
     * @return {@code true} when inside (right/bottom edges excluded)
     */
    public boolean contains(PhysicalPoint point) {
        return point.x() >= left && point.x() < right && point.y() >= top && point.y() < bottom;
    }
}
