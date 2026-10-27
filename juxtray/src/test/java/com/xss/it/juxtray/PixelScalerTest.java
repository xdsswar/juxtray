package com.xss.it.juxtray;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Tests for the area-averaging resampler used to size tray icons.
 */
class PixelScalerTest {

    /** Opaque red. */
    private static final int RED = 0xFFFF0000;

    /** Opaque blue. */
    private static final int BLUE = 0xFF0000FF;

    /** An image already at the target size is returned untouched. */
    @Test
    void sameSizeIsReturnedAsIs() {
        IconPixels source = solid(16, 16, RED);
        assertSame(source, PixelScaler.fit(source, 16));
    }

    /** Downscaling a solid colour keeps exactly that colour. */
    @Test
    void solidColourSurvivesDownscale() {
        IconPixels result = PixelScaler.fit(solid(256, 256, RED), 24);
        assertEquals(24, result.width());
        int[] expected = new int[24 * 24];
        Arrays.fill(expected, RED);
        assertArrayEquals(expected, result.argb());
    }

    /** Two pixels averaged into one give the mean colour. */
    @Test
    void averagesNeighbours() {
        IconPixels source = new IconPixels(2, 1, new int[] {RED, BLUE});
        int[] result = PixelScaler.resample(source, 1, 1);
        assertEquals(0xFF800080, result[0]);
    }

    /**
     * Transparent pixels must not darken their neighbours: averaging opaque
     * white with transparent black gives half-transparent <em>white</em>, not grey.
     */
    @Test
    void transparentPixelsDoNotBleedColour() {
        IconPixels source = new IconPixels(2, 1, new int[] {0xFFFFFFFF, 0x00000000});
        int[] result = PixelScaler.resample(source, 1, 1);
        assertEquals(0x80FFFFFF, result[0]);
    }

    /** Non-square images keep their aspect ratio and are centred in the square. */
    @Test
    void wideImageIsLetterboxed() {
        IconPixels result = PixelScaler.fit(solid(32, 16, RED), 16);
        // 32x16 -> 16x8, centred vertically: rows 0-3 and 12-15 stay transparent.
        assertEquals(0, result.argb()[0]);
        assertEquals(RED, result.argb()[4 * 16]);
        assertEquals(RED, result.argb()[11 * 16 + 15]);
        assertEquals(0, result.argb()[12 * 16]);
    }

    /**
     * Builds a single-colour image.
     *
     * @param width  width
     * @param height height
     * @param argb   the colour
     * @return the image
     */
    private static IconPixels solid(int width, int height, int argb) {
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, argb);
        return new IconPixels(width, height, pixels);
    }
}
