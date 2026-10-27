package com.xss.it.juxtray;

/**
 * Pure Java image resampling for tray icons.
 *
 * <p>The shell draws tray icons at one small size (16 px at 100 % scaling,
 * 24 px at 150 %, and so on). If we hand it a bigger bitmap it shrinks the
 * image with a cheap filter and the icon looks jagged. So we resample
 * ourselves with an <em>area-averaging</em> (box) filter. Every source pixel
 * contributes to the destination in proportion to how much of it the
 * destination pixel covers. That gives clean downscales from large artwork
 * and keeps pixel art crisp when upscaling.</p>
 *
 * <p>Averaging is done on <em>premultiplied</em> colour. Otherwise fully
 * transparent pixels (whose RGB is often black) would bleed dark fringes into
 * the edges of the icon.</p>
 */
public final class PixelScaler {

    /** Utility class: no instances. */
    private PixelScaler() {
        throw new AssertionError("No instances");
    }

    /**
     * Fits an image into a {@code size x size} square, keeping its aspect
     * ratio and centring it on a transparent background.
     *
     * @param source the image to fit
     * @param size   edge length of the square, in pixels
     * @return {@code source} itself when it already has that size, otherwise a
     *         new resampled image
     */
    public static IconPixels fit(IconPixels source, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Invalid size " + size);
        }
        if (source.width() == size && source.height() == size) {
            return source;
        }

        // Scale factor that makes the longer side exactly 'size'.
        double scale = Math.min((double) size / source.width(), (double) size / source.height());
        int width = Math.clamp(Math.round(source.width() * scale), 1, size);
        int height = Math.clamp(Math.round(source.height() * scale), 1, size);
        int[] scaled = resample(source, width, height);

        // Centre the scaled image in the square; untouched pixels stay 0 (transparent).
        int[] square = new int[size * size];
        int offsetX = (size - width) / 2;
        int offsetY = (size - height) / 2;
        for (int y = 0; y < height; y++) {
            System.arraycopy(scaled, y * width, square, (y + offsetY) * size + offsetX, width);
        }
        return new IconPixels(size, size, square);
    }

    /**
     * Resamples an image to an exact size with the area-averaging filter.
     *
     * @param source image to resample
     * @param width  target width, at least 1
     * @param height target height, at least 1
     * @return non-premultiplied ARGB pixels of the resampled image
     */
    static int[] resample(IconPixels source, int width, int height) {
        int[] src = source.argb();
        int srcWidth = source.width();
        int srcHeight = source.height();
        int[] out = new int[width * height];

        // How many source pixels one destination pixel spans on each axis.
        double spanX = (double) srcWidth / width;
        double spanY = (double) srcHeight / height;

        for (int dy = 0; dy < height; dy++) {
            // Source rows [y0, y1) covered by this destination row.
            double y0 = dy * spanY;
            double y1 = y0 + spanY;
            int firstRow = (int) y0;
            int lastRow = Math.min((int) Math.ceil(y1), srcHeight);

            for (int dx = 0; dx < width; dx++) {
                double x0 = dx * spanX;
                double x1 = x0 + spanX;
                int firstCol = (int) x0;
                int lastCol = Math.min((int) Math.ceil(x1), srcWidth);

                // Accumulators: alpha, premultiplied colour, total covered area.
                double alpha = 0;
                double red = 0;
                double green = 0;
                double blue = 0;
                double area = 0;

                for (int sy = firstRow; sy < lastRow; sy++) {
                    // Vertical coverage of source row 'sy' inside [y0, y1).
                    double coverY = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = firstCol; sx < lastCol; sx++) {
                        double coverX = Math.min(x1, sx + 1) - Math.max(x0, sx);
                        double weight = coverX * coverY;
                        int pixel = src[sy * srcWidth + sx];
                        double a = (pixel >>> 24) / 255.0;
                        alpha += a * weight;
                        red += ((pixel >>> 16) & 0xFF) * a * weight;
                        green += ((pixel >>> 8) & 0xFF) * a * weight;
                        blue += (pixel & 0xFF) * a * weight;
                        area += weight;
                    }
                }

                out[dy * width + dx] = compose(alpha, red, green, blue, area);
            }
        }
        return out;
    }

    /**
     * Turns premultiplied accumulators back into one non-premultiplied ARGB
     * pixel.
     *
     * @param alpha sum of weighted alpha (0..1 per sample)
     * @param red   sum of weighted, premultiplied red (0..255 per sample)
     * @param green sum of weighted, premultiplied green
     * @param blue  sum of weighted, premultiplied blue
     * @param area  total weight; the average is the sum divided by this
     * @return packed {@code 0xAARRGGBB}
     */
    private static int compose(double alpha, double red, double green, double blue, double area) {
        if (area <= 0 || alpha <= 0) {
            return 0;
        }
        int a = channel(alpha / area * 255.0);
        // Un-premultiply: divide the colour sums by the alpha sum (the areas cancel out).
        int r = channel(red / alpha);
        int g = channel(green / alpha);
        int b = channel(blue / alpha);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Rounds and clamps a channel value into {@code 0..255}.
     *
     * @param value raw channel value
     * @return channel byte as an int
     */
    private static int channel(double value) {
        return Math.clamp(Math.round(value), 0, 255);
    }
}
