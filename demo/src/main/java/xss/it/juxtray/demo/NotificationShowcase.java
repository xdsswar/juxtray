package xss.it.juxtray.demo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import xss.it.juxtray.NotificationType;
import xss.it.juxtray.TrayIcon;
import xss.it.juxtray.TrayNotification;

/**
 * Renders real stacks of tray notifications to PNG files, the README
 * screenshots for notifications.
 *
 * <p>Like {@link MenuShowcase}, this does not capture the screen. It shows
 * notifications through the public API, waits for their entry animation to
 * end, renders each notification popup's scene graph with {@code snapshot}
 * at 2&times; scale, and composes them at their real relative positions.</p>
 */
final class NotificationShowcase {

    /** Render scale, for crisp images on high-DPI displays and GitHub. */
    private static final double SCALE = 2;

    /** Time for the entry animations to finish before rendering. */
    private static final Duration SETTLE = Duration.millis(900);

    /** The tray icon that shows the notifications. */
    private final TrayIcon tray;

    /** The image used as the custom notification icon. */
    private final Image customImage;

    /** Destination directory. */
    private final Path directory;

    /** Called when all shots are done. */
    private final Runnable onDone;

    /**
     * One screenshot: a file name and how to style the tray for it.
     *
     * @param id    file-name id, written as {@code notification-<id>.png}
     * @param setup prepares the tray's styling
     */
    private record Look(String id, Consumer<TrayIcon> setup) {
    }

    /**
     * Creates the renderer.
     *
     * @param tray        the tray icon
     * @param customImage image for the notification with a custom icon
     * @param directory   where to write the PNG files
     * @param onDone      called when finished
     */
    NotificationShowcase(TrayIcon tray, Image customImage, Path directory, Runnable onDone) {
        this.tray = tray;
        this.customImage = customImage;
        this.directory = directory;
        this.onDone = onDone;
    }

    /**
     * Renders every look, one after the other. Must be called on the FX thread.
     */
    void run() {
        // An off-screen application scene in the Ocean theme, to show the tray following it.
        Scene appScene = new Scene(new StackPane(), 10, 10);
        AppTheme.OCEAN.applyTo(appScene);
        // Rendering screenshots should not chime.
        tray.setUseDefaultSounds(false);

        next(List.of(
                new Look("light", t -> {
                    MenuTheme.LIGHT.applyTo(t);
                    t.setStyleSource(null);
                }),
                new Look("dark", t -> {
                    MenuTheme.DARK.applyTo(t);
                    t.setStyleSource(null);
                }),
                new Look("app-theme", t -> {
                    MenuTheme.SYSTEM.applyTo(t);
                    t.setStyleSource(appScene);
                })).iterator());
    }

    /**
     * Shows the stack in the next look, waits, renders it, then continues.
     *
     * @param looks remaining looks
     */
    private void next(Iterator<Look> looks) {
        if (!looks.hasNext()) {
            tray.setStyleSource(null);
            onDone.run();
            return;
        }
        Look look = looks.next();
        look.setup().accept(tray);
        showStack();
        after(() -> {
            try {
                Path file = directory.resolve("notification-" + look.id() + ".png");
                PngWriter.write(composeNotifications(), file);
                System.out.println("Wrote " + file.toAbsolutePath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                tray.hideNotifications();
            }
            after(() -> next(looks));
        });
    }

    /**
     * Shows the sample stack, oldest first so the newest ends up nearest the
     * corner, like in real use.
     */
    private void showStack() {
        TrayNotification custom = new TrayNotification("Reminder", "Stand-up starts in 5 minutes.");
        custom.setImage(customImage);
        tray.showNotification(sticky(custom));

        tray.showNotification(sticky(new TrayNotification("Disk almost full",
                "Only 2.1 GB left on C:. Free some space to keep backups running.", NotificationType.WARNING)));

        TrayNotification backup = new TrayNotification("Backup complete",
                "1,284 files were copied to D:\\Backups.", NotificationType.SUCCESS);
        Button dismiss = new Button("Dismiss");
        Button open = new Button("Open folder");
        open.setDefaultButton(true);
        backup.getActions().addAll(dismiss, open);
        tray.showNotification(sticky(backup));
    }

    /**
     * Gives a notification a long countdown, so it stays for the shot and
     * still shows its countdown bar.
     *
     * @param notification the notification
     * @return the same notification
     */
    private static TrayNotification sticky(TrayNotification notification) {
        notification.setDuration(Duration.minutes(1));
        return notification;
    }

    /**
     * Runs an action once the animations had time to finish.
     *
     * @param action what to run on the FX thread
     */
    private static void after(Runnable action) {
        PauseTransition pause = new PauseTransition(SETTLE);
        pause.setOnFinished(_ -> action.run());
        pause.play();
    }

    /**
     * Renders every showing notification popup and composes them, at their
     * real relative positions, into one transparent image.
     *
     * @return the composed image at {@link #SCALE}
     */
    private static WritableImage composeNotifications() {
        record Shot(WritableImage image, double x, double y) {
        }
        List<Shot> shots = new ArrayList<>();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Window window : Window.getWindows()) {
            if (!(window instanceof Popup popup) || !popup.isShowing()) {
                continue;
            }
            Parent root = popup.getScene().getRoot();
            Node notification = root.lookup(".tray-notification");
            if (notification == null) {
                continue;
            }
            // The popup content is the transparent frame around the notification, shadow included.
            Bounds bounds = root.getBoundsInParent();
            double x = popup.getX() + bounds.getMinX();
            double y = popup.getY() + bounds.getMinY();

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            params.setTransform(Transform.scale(SCALE, SCALE));
            shots.add(new Shot(root.snapshot(params, null), x, y));

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + bounds.getWidth());
            maxY = Math.max(maxY, y + bounds.getHeight());
        }
        if (shots.isEmpty()) {
            throw new IllegalStateException("No notification is showing");
        }

        Canvas canvas = new Canvas((maxX - minX) * SCALE, (maxY - minY) * SCALE);
        GraphicsContext g = canvas.getGraphicsContext2D();
        for (Shot shot : shots) {
            g.drawImage(shot.image(), (shot.x() - minX) * SCALE, (shot.y() - minY) * SCALE);
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }
}
