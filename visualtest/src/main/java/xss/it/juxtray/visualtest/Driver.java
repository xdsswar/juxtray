package xss.it.juxtray.visualtest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;

/**
 * The test's hands and eyes: runs code on the JavaFX thread, sends real
 * mouse and keyboard input through the JavaFX {@link Robot}, captures the
 * screen, and records check results.
 *
 * <p>The scenarios run on the main thread and only <em>wait</em> there, so
 * JavaFX keeps rendering, animating and delivering tray events between
 * steps. Everything that touches JavaFX goes through {@link #call} or
 * {@link #run}.</p>
 */
final class Driver {

    /** Where screenshots are written. */
    private final Path outputDir;

    /** One line per check, in order, for the final report. */
    private final List<String> results = new ArrayList<>();

    /** Number of failed checks. */
    private int failures;

    /** Sends input and captures the screen; created on the JavaFX thread in {@link #start()}. */
    private Robot robot;

    /**
     * Creates a driver.
     *
     * @param outputDir folder for the screenshots; created if missing
     */
    Driver(Path outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Starts the JavaFX runtime and creates the robot.
     *
     * @throws IOException if the output folder cannot be created
     */
    void start() throws IOException {
        Files.createDirectories(outputDir);
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> started.complete(null));
        started.join();
        run(() -> {
            // A tray app keeps running with no window open; hiding a host stage must not exit.
            Platform.setImplicitExit(false);
            robot = new Robot();
        });
    }

    /**
     * Runs code on the JavaFX thread and waits for its result.
     *
     * @param task the code
     * @param <T>  result type
     * @return what the code returned
     */
    <T> T call(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(task.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.join();
        } catch (CompletionException e) {
            // Report the real failure, not the wrapper.
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    /**
     * Runs code on the JavaFX thread and waits for it to finish.
     *
     * @param task the code
     */
    void run(Runnable task) {
        call(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Waits on the calling (test) thread, letting JavaFX and the tray work.
     *
     * @param millis how long to wait
     */
    static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    /**
     * Moves the real mouse cursor.
     *
     * @param point target in JavaFX screen coordinates
     */
    void move(Point2D point) {
        run(() -> robot.mouseMove(point));
    }

    /**
     * Clicks a mouse button where the cursor is.
     *
     * @param button the button
     */
    void click(MouseButton button) {
        run(() -> robot.mouseClick(button));
    }

    /**
     * Presses and releases a key.
     *
     * @param key the key
     */
    void type(KeyCode key) {
        run(() -> robot.keyType(key));
    }

    /**
     * Records a check result and prints it at once, so progress is visible.
     *
     * @param name   what was checked
     * @param passed whether it held
     * @param detail extra information for the report, or {@code null}
     */
    void check(String name, boolean passed, Object detail) {
        String line = (passed ? "PASS " : "FAIL ") + name + (detail == null ? "" : "  [" + detail + "]");
        if (!passed) {
            failures++;
        }
        results.add(line);
        System.out.println(line);
    }

    /**
     * Number of failed checks so far.
     *
     * @return the count
     */
    int failures() {
        return failures;
    }

    /**
     * All check results so far, in order.
     *
     * @return the report lines
     */
    List<String> results() {
        return List.copyOf(results);
    }

    /**
     * Captures part of the screen to {@code <name>.png}. The area is clipped
     * to the primary screen, where the tray lives. Pixels are captured at
     * device resolution, so the files show exactly what the user sees.
     *
     * @param name file name without extension
     * @param area area in JavaFX screen coordinates
     */
    void shot(String name, Rectangle2D area) {
        run(() -> {
            Rectangle2D screen = Screen.getPrimary().getBounds();
            double x = Math.max(screen.getMinX(), area.getMinX());
            double y = Math.max(screen.getMinY(), area.getMinY());
            double width = Math.min(screen.getMaxX(), area.getMaxX()) - x;
            double height = Math.min(screen.getMaxY(), area.getMaxY()) - y;
            WritableImage image = robot.getScreenCapture(null, x, y, width, height, false);
            writePng(image, outputDir.resolve(name + ".png"));
        });
    }

    /**
     * Writes a JavaFX image as a PNG file, with the pure-Java {@link PngWriter}.
     *
     * @param image the image
     * @param file  the target file
     */
    private static void writePng(WritableImage image, Path file) {
        try {
            PngWriter.write(image, file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
