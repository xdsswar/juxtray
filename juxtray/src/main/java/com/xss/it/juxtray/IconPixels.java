package com.xss.it.juxtray;

import java.util.Objects;

/**
 * A platform-neutral bitmap: what every back end turns into its native icon.
 *
 * <p>Pixels are stored row by row, top to bottom, one {@code int} per pixel in
 * <strong>non-premultiplied ARGB</strong> ({@code 0xAARRGGBB}). On a
 * little-endian machine this is exactly the BGRA byte order Win32 expects, so
 * the Windows back end can copy the array as-is.</p>
 *
 * @param width  width in pixels, at least 1
 * @param height height in pixels, at least 1
 * @param argb   pixel data, exactly {@code width * height} entries
 */
public record IconPixels(int width, int height, int[] argb) {

    /**
     * Validates dimensions and buffer size.
     *
     * @throws IllegalArgumentException if a dimension is not positive or the
     *                                  buffer has the wrong length
     */
    public IconPixels {
        Objects.requireNonNull(argb, "argb");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Invalid size " + width + "x" + height);
        }
        if (argb.length != width * height) {
            throw new IllegalArgumentException(
                    "Expected " + (width * height) + " pixels, got " + argb.length);
        }
    }
}
