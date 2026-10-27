package xss.it.juxtray.demo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;

/**
 * Minimal pure-Java PNG encoder (8-bit RGBA, no filtering).
 *
 * <p>It exists so the demo can save screenshots without pulling in
 * {@code java.desktop} (ImageIO) or {@code javafx.swing}. The format is
 * simple: a signature, then {@code IHDR}, {@code IDAT} (zlib-compressed rows,
 * each prefixed with filter type 0) and {@code IEND} chunks, each with a CRC-32.</p>
 */
final class PngWriter {

    /** The fixed 8-byte PNG file signature. */
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** Utility class: no instances. */
    private PngWriter() {
        throw new AssertionError("No instances");
    }

    /**
     * Writes an image to a PNG file, keeping its alpha channel.
     *
     * @param image the image to save
     * @param file  destination file (parent directories are created)
     * @throws IOException if writing fails
     */
    static void write(Image image, Path file) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        int[] argb = new int[width * height];
        // Non-premultiplied ARGB: what PNG's RGBA stores.
        reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);

        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(SIGNATURE);
            writeChunk(out, "IHDR", header(width, height));
            writeChunk(out, "IDAT", pixels(width, height, argb));
            writeChunk(out, "IEND", new byte[0]);
        }
    }

    /**
     * Builds the {@code IHDR} payload.
     *
     * @param width  image width
     * @param height image height
     * @return the 13-byte header
     * @throws IOException never (in-memory stream)
     */
    private static byte[] header(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(width);
        data.writeInt(height);
        data.writeByte(8);   // bit depth per channel
        data.writeByte(6);   // colour type 6 = RGBA
        data.writeByte(0);   // compression: deflate
        data.writeByte(0);   // filter method 0
        data.writeByte(0);   // no interlacing
        return bytes.toByteArray();
    }

    /**
     * Builds the compressed {@code IDAT} payload: each row is a filter byte
     * (0 = none) followed by RGBA bytes.
     *
     * @param width  image width
     * @param height image height
     * @param argb   pixels, non-premultiplied ARGB
     * @return zlib-compressed scanlines
     * @throws IOException never (in-memory stream)
     */
    private static byte[] pixels(int width, int height, int[] argb) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream zlib = new DeflaterOutputStream(bytes, new Deflater(Deflater.BEST_COMPRESSION))) {
            byte[] row = new byte[1 + width * 4];
            for (int y = 0; y < height; y++) {
                row[0] = 0;
                for (int x = 0; x < width; x++) {
                    int pixel = argb[y * width + x];
                    int i = 1 + x * 4;
                    row[i] = (byte) (pixel >>> 16);
                    row[i + 1] = (byte) (pixel >>> 8);
                    row[i + 2] = (byte) pixel;
                    row[i + 3] = (byte) (pixel >>> 24);
                }
                zlib.write(row);
            }
        }
        return bytes.toByteArray();
    }

    /**
     * Writes one chunk: length, type, payload, CRC-32 of type + payload.
     *
     * @param out     destination
     * @param type    four-letter chunk type
     * @param payload chunk data
     * @throws IOException if writing fails
     */
    private static void writeChunk(OutputStream out, String type, byte[] payload) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(payload);
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(payload.length);
        data.write(typeBytes);
        data.write(payload);
        data.writeInt((int) crc.getValue());
        data.flush();
    }
}
