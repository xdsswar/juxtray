package xss.it.juxtray.demo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Window;
import javafx.util.Duration;
import xss.it.juxtray.TrayIcon;

/**
 * Renders the demo's real tray menu in every {@link MenuTheme} to PNG files.
 * These are the README screenshots.
 *
 * <p>This does not capture the screen. It opens the actual tray menu through
 * the public API ({@link TrayIcon#showMenu()}), opens the Status submenu, then
 * asks JavaFX to render each open popup's scene graph ({@code snapshot}) at
 * 2&times; scale on a transparent background. The popups are then composed
 * at their real relative positions. So the images show exactly what users
 * see: real CSS, real shadows, real layout.</p>
 *
 * <p>To show hover styling without a mouse, the submenu is opened with a
 * synthetic {@code MOUSE_ENTERED}, and the {@code :focused} pseudo-class is
 * set on the open "Status" row and one submenu item.</p>
 */
final class MenuShowcase {

    /** Render scale, for crisp images on high-DPI displays and GitHub. */
    private static final double SCALE = 2;

    /** Transparent margin around the composed menus, in logical pixels. */
    private static final double MARGIN = 28;

    /** Time for popups to lay out and apply CSS before rendering. */
    private static final Duration SETTLE = Duration.millis(350);

    /** The {@code :focused} pseudo-class, used to fake a hovered item. */
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");

    /** The tray icon whose menu is rendered. */
    private final TrayIcon tray;

    /** The submenu to open in every shot. */
    private final Menu submenu;

    /** Destination directory. */
    private final Path directory;

    /** Called when all shots are done (or failed). */
    private final Runnable onDone;

    /**
     * Creates the renderer.
     *
     * @param tray      the tray icon (its menu is rendered)
     * @param submenu   a submenu of that menu, kept open in every shot
     * @param directory where to write {@code menu-<theme>.png}
     * @param onDone    called when finished
     */
    MenuShowcase(TrayIcon tray, Menu submenu, Path directory, Runnable onDone) {
        this.tray = tray;
        this.submenu = submenu;
        this.directory = directory;
        this.onDone = onDone;
    }

    /**
     * Renders every theme, one after the other. Must be called on the FX thread.
     */
    void run() {
        next(List.of(MenuTheme.LIGHT, MenuTheme.DARK, MenuTheme.NEON, MenuTheme.TERMINAL, MenuTheme.MODENA)
                .iterator());
    }

    /**
     * Opens the menu in the next theme, waits for it to settle, renders it,
     * then continues with the rest.
     *
     * @param themes remaining themes
     */
    private void next(Iterator<MenuTheme> themes) {
        if (!themes.hasNext()) {
            onDone.run();
            return;
        }
        MenuTheme theme = themes.next();
        theme.applyTo(tray);
        tray.showMenu();
        // The submenu can only open once the menu's skin exists, so wait a moment first.
        after(() -> {
            openSubmenuLikeTheMouse();
            after(() -> {
                hoverSecondItemOfSubmenu();
                after(() -> {
                try {
                    Path file = directory.resolve("menu-" + theme.id + ".png");
                    PngWriter.write(composeOpenMenus(), file);
                    System.out.println("Wrote " + file.toAbsolutePath());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } finally {
                    tray.hideMenu();
                }
                after(() -> next(themes));
                });
            });
        });
    }

    /**
     * Runs an action once popups had time to settle.
     *
     * @param action what to run on the FX thread
     */
    private static void after(Runnable action) {
        PauseTransition pause = new PauseTransition(SETTLE);
        pause.setOnFinished(_ -> action.run());
        pause.play();
    }

    /**
     * Opens the submenu the way a user does: by hovering its item.
     *
     * <p>{@code Menu.show()} alone only flips the menu's {@code showing} flag.
     * The context menu skin opens the submenu popup from its mouse handlers.
     * So a synthetic {@code MOUSE_ENTERED} is delivered to the submenu's item
     * node (the one carrying the {@code .menu} style class).</p>
     */
    private void openSubmenuLikeTheMouse() {
        ContextMenu root = tray.getContextMenu();
        if (root == null || root.getScene() == null) {
            return;
        }
        Node item = root.getScene().getRoot().lookup(".menu");
        if (item == null) {
            return;
        }
        Bounds screen = item.localToScreen(item.getBoundsInLocal());
        double x = screen.getCenterX();
        double y = screen.getCenterY();
        Event.fireEvent(item, new MouseEvent(MouseEvent.MOUSE_ENTERED,
                item.getBoundsInLocal().getCenterX(), item.getBoundsInLocal().getCenterY(), x, y,
                MouseButton.NONE, 0, false, false, false, false, false, false, false,
                true, false, false, null));
    }

    /**
     * Sets a deterministic hover state, like a mouse resting on the second
     * submenu item: the open "Status" row and that item get {@code :focused},
     * and every other item loses it (the skin auto-focuses some on open).
     */
    private void hoverSecondItemOfSubmenu() {
        ContextMenu root = tray.getContextMenu();
        if (root != null && root.getScene() != null) {
            Parent rootContent = root.getScene().getRoot();
            rootContent.lookupAll(".menu-item").forEach(item -> item.pseudoClassStateChanged(FOCUSED, false));
            Node opener = rootContent.lookup(".menu");
            if (opener != null) {
                opener.pseudoClassStateChanged(FOCUSED, true);
            }
        }
        ContextMenu popup = findSubmenuPopup();
        if (popup == null) {
            return;
        }
        List<Node> items = new ArrayList<>(popup.getScene().getRoot().lookupAll(".menu-item"));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).pseudoClassStateChanged(FOCUSED, i == 1);
        }
    }

    /**
     * Finds the popup window showing the submenu's items: the open context
     * menu that is not the root tray menu.
     *
     * @return the submenu popup, or {@code null}
     */
    private ContextMenu findSubmenuPopup() {
        for (Window window : Window.getWindows()) {
            if (window instanceof ContextMenu popup && popup.isShowing() && popup != tray.getContextMenu()) {
                return popup;
            }
        }
        return null;
    }

    /**
     * Renders every open context menu popup and composes them, at their real
     * relative screen positions, into one transparent image.
     *
     * @return the composed image at {@link #SCALE}
     */
    private static WritableImage composeOpenMenus() {
        List<Shot> shots = new ArrayList<>();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Window window : Window.getWindows()) {
            if (!(window instanceof ContextMenu popup) || !popup.isShowing()) {
                continue;
            }
            Parent root = popup.getScene().getRoot();
            // boundsInParent includes the drop shadow; it is relative to the popup's scene,
            // whose origin is the popup window's screen position.
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
            throw new IllegalStateException("No tray menu is showing");
        }

        Canvas canvas = new Canvas((maxX - minX + 2 * MARGIN) * SCALE, (maxY - minY + 2 * MARGIN) * SCALE);
        GraphicsContext g = canvas.getGraphicsContext2D();
        // Windows are listed back to front, so the submenu is drawn over its parent, as on screen.
        for (Shot shot : shots) {
            g.drawImage(shot.image(), (shot.x() - minX + MARGIN) * SCALE, (shot.y() - minY + MARGIN) * SCALE);
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    /**
     * One rendered popup and its screen position.
     *
     * @param image rendered popup (at {@link #SCALE})
     * @param x     screen x of the image's top-left corner, logical pixels
     * @param y     screen y of the image's top-left corner, logical pixels
     */
    private record Shot(WritableImage image, double x, double y) {
    }
}
