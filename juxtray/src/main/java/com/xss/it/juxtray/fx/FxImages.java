package com.xss.it.juxtray.fx;

import com.xss.it.juxtray.IconPixels;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;

/**
 * Converts JavaFX images into {@link IconPixels}.
 */
public final class FxImages {

    /** Utility class: no instances. */
    private FxImages() {
        throw new AssertionError("No instances");
    }

    /**
     * Reads every pixel of a fully loaded image as non-premultiplied ARGB.
     *
     * @param image the image; must be loaded and without error
     * @return the pixels, or {@code null} if the image is empty or not readable yet
     */
    public static IconPixels toPixels(Image image) {
        PixelReader reader = image.getPixelReader();
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        if (reader == null || width < 1 || height < 1) {
            return null;
        }
        int[] argb = new int[width * height];
        // getIntArgbInstance() = non-premultiplied, which is what IconPixels stores.
        reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        return new IconPixels(width, height, argb);
    }
}
