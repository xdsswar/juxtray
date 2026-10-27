package com.xss.it.juxtray.fx;

import com.xss.it.juxtray.MenuPlacement;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.PopupWindow.AnchorLocation;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Shows a JavaFX {@link ContextMenu} for a tray icon.
 *
 * <p>A JavaFX popup needs an owner window, and a tray icon has none. So
 * every tray icon gets an invisible <em>host stage</em>:</p>
 * <ul>
 *   <li>{@link StageStyle#UTILITY}, so it never shows up in the taskbar or in
 *       Alt+Tab;</li>
 *   <li>fully transparent ({@code opacity 0}), 1&times;1, placed at the cursor;</li>
 *   <li>shown only while the menu is open.</li>
 * </ul>
 *
 * <p><strong>Why the menu is fully CSS-stylable:</strong> when a JavaFX popup
 * opens, it copies the stylesheets of its root owner's scene. The host
 * stage's scene carries Juxtray's default theme followed by the user's
 * stylesheets, so the menu, its items and all submenus are styled by plain
 * JavaFX CSS ({@code .context-menu}, {@code .menu-item}, {@code .menu},
 * {@code .check-menu-item}, {@code .radio-menu-item},
 * {@code .separator-menu-item}...). Because these stylesheets live only on
 * the host scene, they never leak into the application's own windows. A
 * {@link StyleLink} keeps them, and the open menus, in sync with the theme
 * and the optional application style source.</p>
 *
 * <p><strong>Closing:</strong> the host stage is activated (made the
 * foreground window) when the menu opens. Clicking anywhere else deactivates
 * it, and that hides the menu. This mirrors the Win32
 * {@code SetForegroundWindow} rule for tray menus.</p>
 *
 * <p><strong>Placement:</strong> the menu is first shown invisible to
 * measure its laid-out content. It is then moved to the spot computed by
 * {@link MenuPlacement} (above or below the cursor, flipped and clamped into
 * the work area) and revealed, all in one FX event. Everything is in JavaFX
 * logical coordinates, so it is correct on any per-monitor scale. While the
 * menu is open, its {@code autoFix} and anchor location are taken over; the
 * user's values are restored when it closes.</p>
 *
 * <p>All methods must be called on the JavaFX Application Thread.</p>
 */
public final class MenuHost {

    /** Style class added to every menu shown by Juxtray, for targeted CSS. */
    public static final String STYLE_CLASS = "tray-menu";

    /** Counter used to give every host stage a unique title. */
    private static final AtomicInteger COUNTER = new AtomicInteger();

    /** Built-in light theme. */
    private static final URL LIGHT_THEME = MenuHost.class.getResource("tray-menu-light.css");

    /** Built-in dark theme. */
    private static final URL DARK_THEME = MenuHost.class.getResource("tray-menu-dark.css");

    /** The invisible owner of the popup. */
    private final Stage stage;

    /** Brings a window to the foreground, by title (native helper). */
    private final Consumer<String> activator;

    /** Keeps the host scene and the open menus styled. */
    private final StyleLink styleLink;

    /** Hides the host stage when the menu closes. */
    private final ChangeListener<Boolean> menuShowingListener = (_, _, showing) -> {
        if (!showing) {
            onMenuHidden();
        }
    };

    /** The menu currently shown, or {@code null}. */
    private ContextMenu current;

    /** The user's {@code autoFix} value for {@link #current}, restored on hide. */
    private boolean savedAutoFix;

    /** The user's anchor location for {@link #current}, restored on hide. */
    private AnchorLocation savedAnchorLocation;

    /**
     * Creates the host stage (not shown yet).
     *
     * @param userStylesheets stylesheets to apply on top of the default theme; watched for changes
     * @param useDefaultTheme whether to apply the built-in theme; watched for changes
     * @param colorScheme     forced light/dark variant of the built-in theme, or a {@code null}
     *                        value to follow the OS; watched for changes
     * @param styleSource     application scene whose styles the menu follows, or a {@code null}
     *                        value for none; watched for changes
     * @param activator       native helper that brings a window (by title) to the foreground
     */
    public MenuHost(ObservableList<String> userStylesheets, ObservableBooleanValue useDefaultTheme,
                    ObservableValue<ColorScheme> colorScheme, ObservableValue<Scene> styleSource,
                    Consumer<String> activator) {
        this.activator = Objects.requireNonNull(activator, "activator");

        Region root = new Region();
        root.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(root, 1, 1, Color.TRANSPARENT);

        stage = new Stage(StageStyle.UTILITY);
        stage.setScene(scene);
        // A unique title lets the native side find this exact window to activate it.
        stage.setTitle("juxtray-menu-host-" + ProcessHandle.current().pid() + "-" + COUNTER.incrementAndGet());
        stage.setOpacity(0);
        stage.setWidth(1);
        stage.setHeight(1);
        stage.setResizable(false);
        stage.setAlwaysOnTop(true);
        // Losing focus means the user clicked somewhere else: close the menu.
        stage.focusedProperty().addListener((_, wasFocused, focused) -> {
            if (wasFocused && !focused) {
                hide();
            }
        });

        styleLink = new StyleLink(stage, LIGHT_THEME, DARK_THEME, userStylesheets, useDefaultTheme,
                colorScheme, styleSource);
    }

    /**
     * Opens a menu at a screen position. Any menu already open is closed first.
     *
     * @param menu the menu to show
     * @param x    JavaFX screen x of the anchor (normally the cursor)
     * @param y    JavaFX screen y of the anchor
     */
    public void show(ContextMenu menu, double x, double y) {
        Objects.requireNonNull(menu, "menu");
        hide();
        if (!menu.getStyleClass().contains(STYLE_CLASS)) {
            menu.getStyleClass().add(STYLE_CLASS);
        }

        stage.setX(x);
        stage.setY(y);
        stage.show();
        stage.toFront();
        stage.requestFocus();
        // JavaFX cannot force itself to the foreground; ask Windows directly. A tray click
        // gives our process the right to do so.
        activator.accept(stage.getTitle());

        current = menu;
        menu.showingProperty().addListener(menuShowingListener);

        // We place the menu ourselves (MenuPlacement), so take over the popup settings that
        // would interfere, and remember the user's values to restore them on hide.
        savedAutoFix = menu.isAutoFix();
        savedAnchorLocation = menu.getAnchorLocation();
        double savedOpacity = menu.getOpacity();
        menu.setAutoFix(false);
        // Anchor the visible content, not the window: the window also contains the drop shadow,
        // which would otherwise offset the menu from the cursor.
        menu.setAnchorLocation(AnchorLocation.CONTENT_TOP_LEFT);

        // The menu's size is only known once its skin is laid out, i.e. after show(). Show it
        // invisible, measure, move it to its final place, then reveal it. All of this happens in
        // one FX event, before the next render pulse, so there is no visible jump.
        menu.setOpacity(0);
        menu.show(stage, x, y);
        place(menu, new Point2D(x, y));
        menu.setOpacity(savedOpacity);
    }

    /**
     * Moves a showing menu to its final position next to the anchor.
     *
     * @param menu   the showing menu
     * @param anchor where the menu was requested, in JavaFX screen coordinates
     */
    private static void place(ContextMenu menu, Point2D anchor) {
        Skin<?> skin = menu.getSkin();
        Node content = skin == null ? null : skin.getNode();
        if (content == null) {
            return;
        }
        Bounds size = content.getLayoutBounds();
        Point2D position = MenuPlacement.place(anchor, size.getWidth(), size.getHeight(), workAreaAt(anchor));
        menu.setAnchorX(position.getX());
        menu.setAnchorY(position.getY());
    }

    /**
     * Work area (screen minus taskbar) of the screen containing a point.
     *
     * <p>The cursor usually sits <em>on</em> the taskbar, outside the visual
     * bounds, so the screen is found by its full bounds and its visual bounds
     * are returned. Each JavaFX {@link Screen} already carries its own scale, so
     * this is correct on mixed-DPI setups.</p>
     *
     * @param point a point in JavaFX screen coordinates
     * @return visual bounds of that screen (the primary screen if none contains the point)
     */
    private static Rectangle2D workAreaAt(Point2D point) {
        return Screen.getScreensForRectangle(point.getX(), point.getY(), 1, 1).stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    /**
     * Closes the open menu, if any.
     */
    public void hide() {
        if (current != null) {
            // Normally the showing listener does the clean-up.
            current.hide();
        }
        if (current != null) {
            // The listener did not run: the menu never actually opened (for example because it
            // was already showing somewhere else), so hide() changed nothing. Clean up anyway,
            // or the host stage and the taken-over popup settings would stay behind.
            onMenuHidden();
        } else if (stage.isShowing()) {
            stage.hide();
        }
    }

    /**
     * Whether a menu is currently open.
     *
     * @return {@code true} while a menu is shown
     */
    public boolean isShowing() {
        return current != null && current.isShowing();
    }

    /**
     * Closes the menu and the host stage, and stops listening to global state.
     * The host cannot be used afterwards.
     */
    public void dispose() {
        hide();
        styleLink.dispose();
        stage.close();
    }

    /**
     * Clean-up after the menu closed, however that happened (item chosen,
     * Escape, focus lost...).
     */
    private void onMenuHidden() {
        if (current != null) {
            current.showingProperty().removeListener(menuShowingListener);
            // Hand the menu back exactly as the user configured it.
            current.setAutoFix(savedAutoFix);
            current.setAnchorLocation(savedAnchorLocation);
            current = null;
        }
        stage.hide();
    }
}
