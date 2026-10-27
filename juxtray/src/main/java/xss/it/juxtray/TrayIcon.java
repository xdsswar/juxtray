package xss.it.juxtray;

import com.xss.it.juxtray.IconPixels;
import com.xss.it.juxtray.NativeTray;
import com.xss.it.juxtray.NativeTrayEvent;
import com.xss.it.juxtray.NativeTrays;
import com.xss.it.juxtray.PhysicalPoint;
import com.xss.it.juxtray.PhysicalRect;
import com.xss.it.juxtray.PixelScaler;
import com.xss.it.juxtray.fx.FxImages;
import com.xss.it.juxtray.fx.MenuHost;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;

/**
 * An icon in the system tray (the Windows notification area), with an
 * optional menu that is a regular JavaFX {@link ContextMenu}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Platform.setImplicitExit(false);              // keep running with no window open
 *
 * MenuItem open = new MenuItem("Open");
 * MenuItem exit = new MenuItem("Exit");
 * exit.setOnAction(e -> Platform.exit());
 *
 * TrayIcon tray = new TrayIcon(new Image("app-256.png"), "My App", new ContextMenu(open, exit));
 * tray.setOnAction(e -> stage.show());           // left click
 * tray.getStylesheets().add("my-tray-menu.css"); // style the menu with ordinary JavaFX CSS
 * }</pre>
 *
 * <h2>Styling the menu</h2>
 * <p>The menu is a real JavaFX {@link ContextMenu}, so all of JavaFX CSS
 * applies: {@code .context-menu}, {@code .menu-item}, {@code .menu},
 * {@code .check-menu-item}, {@code .radio-menu-item},
 * {@code .separator-menu-item}, pseudo-classes such as {@code :focused} or
 * {@code :disabled}, effects, gradients, and inline {@code setStyle}. The root
 * menu also gets the style class {@code tray-menu}.</p>
 * <p>A built-in Windows 11-like theme is applied first. It follows the OS
 * light/dark setting (or a forced one, see {@link #setMenuColorScheme}) and exposes its palette as looked-up colours
 * ({@code -tray-background}, {@code -tray-text}, {@code -tray-hover} ...),
 * so a stylesheet added through {@link #getStylesheets()} can retheme it
 * with a couple of lines or replace it entirely. Turn it off with
 * {@link #setUseDefaultTheme(boolean)}. The application's own scene
 * stylesheets do <em>not</em> apply to tray menus, and tray stylesheets do
 * not leak into the application.</p>
 *
 * <h2>Threading and lifecycle</h2>
 * <p>Create, use and close tray icons on the JavaFX Application Thread only,
 * after the JavaFX runtime has started. All events are delivered on that
 * thread too. The icon appears as soon as it is constructed and stays until
 * {@link #close()} is called or the JVM exits.</p>
 * <p>Tray applications usually call {@code Platform.setImplicitExit(false)}.
 * Otherwise JavaFX shuts down when the last window closes, and that includes
 * the invisible window hosting the tray menu.</p>
 *
 * <h2>Icon image</h2>
 * <p>Any JavaFX {@link Image} works, including ones still loading in the
 * background. It is resampled to the exact tray size with a high-quality
 * filter. Give it a large, square image (for example 256&times;256) for the
 * best result on high-DPI screens.</p>
 *
 * <p>Requires the JVM option {@code --enable-native-access=xss.it.juxtray}.</p>
 */
public final class TrayIcon implements AutoCloseable {

    /** Logger for non-fatal problems such as images that fail to load. */
    private static final Logger LOG = System.getLogger(TrayIcon.class.getName());

    /** The platform icon. */
    private final NativeTray peer;

    /** Owner and positioning logic for the context menu. */
    private final MenuHost menuHost;

    /** User stylesheets for the menu. */
    private final ObservableList<String> stylesheets = FXCollections.observableArrayList();

    /** Re-applies the image once a background-loading image has finished. */
    private final ChangeListener<Number> imageProgressListener = (_, _, progress) -> {
        if (progress.doubleValue() >= 1.0) {
            applyImage();
        }
    };

    /** Reads the cursor position in JavaFX coordinates; created lazily. */
    private Robot robot;

    /** Set once {@link #close()} has run. */
    private boolean closed;

    // The icon image. null leaves an empty slot in the tray.
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image");

    // Text shown when hovering the icon; null for none. Windows shows at most 127 characters.
    private final StringProperty tooltip = new SimpleStringProperty(this, "tooltip");

    // Whether the icon is shown. A hidden icon keeps its state and can be shown again; use close() to remove it for good.
    private final BooleanProperty visible = new SimpleBooleanProperty(this, "visible", true);

    // The menu to show, or null for none.
    private final ObjectProperty<ContextMenu> contextMenu = new SimpleObjectProperty<>(this, "contextMenu");

    // Whether a primary (left) click opens the menu. Off by default, as on Windows.
    private final BooleanProperty menuOnPrimaryClick = new SimpleBooleanProperty(this, "menuOnPrimaryClick", false);

    // Whether a secondary (right) click opens the menu. On by default.
    private final BooleanProperty menuOnSecondaryClick =
            new SimpleBooleanProperty(this, "menuOnSecondaryClick", true);

    // Whether the built-in menu theme is applied under the user stylesheets.
    private final BooleanProperty useDefaultTheme = new SimpleBooleanProperty(this, "useDefaultTheme", true);

    // Forces the light or dark built-in theme; null follows the OS colour scheme.
    private final ObjectProperty<ColorScheme> menuColorScheme = new SimpleObjectProperty<>(this, "menuColorScheme");

    // Called when the icon is clicked with the primary (left) button.
    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new SimpleObjectProperty<>(this, "onAction");

    // Receives every mouse event on the icon.
    private final ObjectProperty<Consumer<? super TrayIconEvent>> onTrayEvent =
            new SimpleObjectProperty<>(this, "onTrayEvent");

    /**
     * Creates an empty tray icon and adds it to the tray.
     *
     * @throws IllegalStateException if not called on the JavaFX Application Thread
     * @throws TrayException         if the platform is unsupported or refuses the icon
     */
    public TrayIcon() {
        checkFxThread();
        // The native listener runs on the Win32 thread; hop to JavaFX before touching anything.
        peer = NativeTrays.create(event -> Platform.runLater(() -> dispatch(event)));
        menuHost = new MenuHost(stylesheets, useDefaultTheme, menuColorScheme, peer::activateWindow);

        image.addListener((_, oldImage, _) -> {
            if (oldImage != null) {
                oldImage.progressProperty().removeListener(imageProgressListener);
            }
            applyImage();
        });
        tooltip.addListener((_, _, text) -> {
            checkFxThread();
            if (!closed) {
                peer.setTooltip(text);
            }
        });
        visible.addListener((_, _, shown) -> {
            checkFxThread();
            if (!closed) {
                peer.setVisible(shown);
            }
        });
        contextMenu.addListener((_, _, _) -> menuHost.hide());
    }

    /**
     * Creates a tray icon with an image and adds it to the tray.
     *
     * @param image the icon image, or {@code null}
     */
    public TrayIcon(Image image) {
        this();
        setImage(image);
    }

    /**
     * Creates a tray icon with an image and tooltip and adds it to the tray.
     *
     * @param image   the icon image, or {@code null}
     * @param tooltip the tooltip, or {@code null}
     */
    public TrayIcon(Image image, String tooltip) {
        this(image);
        setTooltip(tooltip);
    }

    /**
     * Creates a tray icon with an image, tooltip and menu and adds it to the tray.
     *
     * @param image       the icon image, or {@code null}
     * @param tooltip     the tooltip, or {@code null}
     * @param contextMenu the menu, or {@code null}
     */
    public TrayIcon(Image image, String tooltip, ContextMenu contextMenu) {
        this(image, tooltip);
        setContextMenu(contextMenu);
    }

    // ---- Properties ----------------------------------------------------------------------

    /**
     * The icon image.
     *
     * @return the image property
     */
    public ObjectProperty<Image> imageProperty() {
        return image;
    }

    /**
     * Gets the icon image.
     *
     * @return the image, or {@code null}
     */
    public Image getImage() {
        return image.get();
    }

    /**
     * Sets the icon image.
     *
     * @param value the image, or {@code null} for an empty slot
     */
    public void setImage(Image value) {
        image.set(value);
    }

    /**
     * The hover tooltip.
     *
     * @return the tooltip property
     */
    public StringProperty tooltipProperty() {
        return tooltip;
    }

    /**
     * Gets the hover tooltip.
     *
     * @return the tooltip, or {@code null}
     */
    public String getTooltip() {
        return tooltip.get();
    }

    /**
     * Sets the hover tooltip.
     *
     * @param value the tooltip, or {@code null} for none
     */
    public void setTooltip(String value) {
        tooltip.set(value);
    }

    /**
     * Whether the icon is shown.
     *
     * @return the visible property
     */
    public BooleanProperty visibleProperty() {
        return visible;
    }

    /**
     * Tells whether the icon is shown.
     *
     * @return {@code true} when shown
     */
    public boolean isVisible() {
        return visible.get();
    }

    /**
     * Shows or hides the icon.
     *
     * @param value {@code true} to show
     */
    public void setVisible(boolean value) {
        visible.set(value);
    }

    /**
     * The menu shown on click.
     *
     * @return the context menu property
     */
    public ObjectProperty<ContextMenu> contextMenuProperty() {
        return contextMenu;
    }

    /**
     * Gets the menu.
     *
     * @return the menu, or {@code null}
     */
    public ContextMenu getContextMenu() {
        return contextMenu.get();
    }

    /**
     * Sets the menu.
     *
     * @param value the menu, or {@code null} for none
     */
    public void setContextMenu(ContextMenu value) {
        contextMenu.set(value);
    }

    /**
     * Whether a primary (left) click opens the menu.
     *
     * @return the property
     */
    public BooleanProperty menuOnPrimaryClickProperty() {
        return menuOnPrimaryClick;
    }

    /**
     * Tells whether a primary click opens the menu.
     *
     * @return {@code true} if it does
     */
    public boolean isMenuOnPrimaryClick() {
        return menuOnPrimaryClick.get();
    }

    /**
     * Sets whether a primary click opens the menu. Default {@code false}.
     *
     * @param value {@code true} to open the menu on primary click
     */
    public void setMenuOnPrimaryClick(boolean value) {
        menuOnPrimaryClick.set(value);
    }

    /**
     * Whether a secondary (right) click opens the menu.
     *
     * @return the property
     */
    public BooleanProperty menuOnSecondaryClickProperty() {
        return menuOnSecondaryClick;
    }

    /**
     * Tells whether a secondary click opens the menu.
     *
     * @return {@code true} if it does
     */
    public boolean isMenuOnSecondaryClick() {
        return menuOnSecondaryClick.get();
    }

    /**
     * Sets whether a secondary click opens the menu. Default {@code true}.
     *
     * @param value {@code true} to open the menu on secondary click
     */
    public void setMenuOnSecondaryClick(boolean value) {
        menuOnSecondaryClick.set(value);
    }

    /**
     * Whether the built-in menu theme is applied.
     *
     * @return the property
     */
    public BooleanProperty useDefaultThemeProperty() {
        return useDefaultTheme;
    }

    /**
     * Tells whether the built-in menu theme is applied.
     *
     * @return {@code true} if it is
     */
    public boolean isUseDefaultTheme() {
        return useDefaultTheme.get();
    }

    /**
     * Turns the built-in menu theme on or off. With it off, menus fall back to
     * JavaFX's Modena look plus whatever {@link #getStylesheets()} adds.
     *
     * @param value {@code true} to apply the built-in theme
     */
    public void setUseDefaultTheme(boolean value) {
        useDefaultTheme.set(value);
    }

    /**
     * Which variant of the built-in menu theme to use: {@link ColorScheme#LIGHT},
     * {@link ColorScheme#DARK}, or {@code null} (the default) to follow the OS
     * setting live. Has no effect while {@link #isUseDefaultTheme()} is off.
     *
     * @return the property
     */
    public ObjectProperty<ColorScheme> menuColorSchemeProperty() {
        return menuColorScheme;
    }

    /**
     * Gets the forced colour scheme of the built-in menu theme.
     *
     * @return the forced scheme, or {@code null} when following the OS
     */
    public ColorScheme getMenuColorScheme() {
        return menuColorScheme.get();
    }

    /**
     * Forces the light or dark built-in menu theme.
     *
     * @param value the scheme to use, or {@code null} to follow the OS
     */
    public void setMenuColorScheme(ColorScheme value) {
        menuColorScheme.set(value);
    }

    /**
     * Handler for primary (left) clicks, fired when the button is released.
     * When the user double-clicks, it fires for each of the two clicks.
     *
     * @return the property
     */
    public ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * Gets the primary click handler.
     *
     * @return the handler, or {@code null}
     */
    public EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * Sets the primary click handler.
     *
     * @param value the handler, or {@code null}
     */
    public void setOnAction(EventHandler<ActionEvent> value) {
        onAction.set(value);
    }

    /**
     * Handler for every mouse event on the icon (clicks, double-clicks,
     * enter, move, exit). See {@link TrayIconEvent}.
     *
     * @return the property
     */
    public ObjectProperty<Consumer<? super TrayIconEvent>> onTrayEventProperty() {
        return onTrayEvent;
    }

    /**
     * Gets the mouse event handler.
     *
     * @return the handler, or {@code null}
     */
    public Consumer<? super TrayIconEvent> getOnTrayEvent() {
        return onTrayEvent.get();
    }

    /**
     * Sets the mouse event handler.
     *
     * @param value the handler, or {@code null}
     */
    public void setOnTrayEvent(Consumer<? super TrayIconEvent> value) {
        onTrayEvent.set(value);
    }

    /**
     * Stylesheet URLs applied to the menu, on top of the built-in theme. They
     * work exactly like {@code Scene.getStylesheets()}. Changes take effect
     * the next time the menu opens.
     *
     * @return the live, modifiable list of stylesheet URLs
     */
    public ObservableList<String> getStylesheets() {
        return stylesheets;
    }

    // ---- Actions -------------------------------------------------------------------------

    /**
     * Opens the context menu at the mouse cursor, as if the icon had been
     * clicked. Does nothing if there is no menu.
     *
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    public void showMenu() {
        checkFxThread();
        showMenuAt(robot().getMousePosition());
    }

    /**
     * Closes the context menu if it is open.
     *
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    public void hideMenu() {
        checkFxThread();
        menuHost.hide();
    }

    /**
     * Removes the icon from the tray and releases its native resources. The
     * icon cannot be used afterwards. Calling this again has no effect.
     *
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    @Override
    public void close() {
        checkFxThread();
        if (closed) {
            return;
        }
        closed = true;
        Image current = getImage();
        if (current != null) {
            current.progressProperty().removeListener(imageProgressListener);
        }
        menuHost.dispose();
        peer.close();
    }

    /**
     * Tells whether {@link #close()} has been called.
     *
     * @return {@code true} once closed
     */
    public boolean isClosed() {
        return closed;
    }

    // ---- Internals -----------------------------------------------------------------------

    /**
     * Pushes the current image to the tray. If it is still loading, waits for
     * it to finish first.
     */
    private void applyImage() {
        checkFxThread();
        if (closed) {
            return;
        }
        Image current = getImage();
        if (current == null) {
            peer.setIcon(null);
            return;
        }
        if (current.isError()) {
            LOG.log(Level.WARNING, "Tray icon image failed to load: " + current.getUrl(), current.getException());
            return;
        }
        if (current.getProgress() < 1.0) {
            // Background loading: try again once done. Remove first so the listener is never registered twice.
            current.progressProperty().removeListener(imageProgressListener);
            current.progressProperty().addListener(imageProgressListener);
            return;
        }
        current.progressProperty().removeListener(imageProgressListener);
        IconPixels pixels = FxImages.toPixels(current);
        peer.setIcon(pixels == null ? null : PixelScaler.fit(pixels, peer.iconSize()));
    }

    /**
     * Turns a raw native event into a public event, notifies the handlers, and
     * opens the menu when the click calls for it. Runs on the JavaFX thread.
     *
     * @param event the raw event
     */
    private void dispatch(NativeTrayEvent event) {
        if (closed) {
            return;
        }
        if (event.type() == NativeTrayEvent.Type.ICON_SIZE_CHANGED) {
            // Scaling or monitors changed: resample the image for the new size. Not a user event.
            applyImage();
            return;
        }
        Point2D position = robot().getMousePosition();
        Rectangle2D bounds = toFxBounds(event.bounds(), position);
        MouseButton button = event.button();

        TrayIconEvent trayEvent = switch (event.type()) {
            case PRESSED -> new TrayIconEvent.Click(this, button, ButtonState.PRESSED, position, bounds);
            case RELEASED -> new TrayIconEvent.Click(this, button, ButtonState.RELEASED, position, bounds);
            case DOUBLE_CLICKED -> new TrayIconEvent.DoubleClick(this, button, position, bounds);
            case ENTERED -> new TrayIconEvent.Enter(this, position, bounds);
            case MOVED -> new TrayIconEvent.Move(this, position, bounds);
            case EXITED -> new TrayIconEvent.Exit(this, position, bounds);
            case ICON_SIZE_CHANGED -> throw new AssertionError("handled above");
        };

        Consumer<? super TrayIconEvent> handler = getOnTrayEvent();
        if (handler != null) {
            handler.accept(trayEvent);
        }

        if (event.type() != NativeTrayEvent.Type.RELEASED || closed) {
            return;
        }
        if (button == MouseButton.PRIMARY) {
            EventHandler<ActionEvent> action = getOnAction();
            if (action != null) {
                action.handle(new ActionEvent(this, null));
            }
        }
        boolean wantsMenu = (button == MouseButton.PRIMARY && isMenuOnPrimaryClick())
                || (button == MouseButton.SECONDARY && isMenuOnSecondaryClick());
        if (wantsMenu && !closed) {
            showMenuAt(position);
        }
    }

    /**
     * Opens the menu at a JavaFX screen position, if there is a menu.
     *
     * @param position anchor in JavaFX screen coordinates
     */
    private void showMenuAt(Point2D position) {
        ContextMenu menu = getContextMenu();
        if (menu != null && !closed) {
            menuHost.show(menu, position.getX(), position.getY());
        }
    }

    /**
     * Converts icon bounds from device pixels to JavaFX screen coordinates.
     *
     * <p>JavaFX does not publish how it maps device pixels to its own screen
     * coordinates across monitors with different scaling. So we anchor on the
     * cursor, which we know in <em>both</em> spaces: the Robot gives its JavaFX
     * position, and the OS gives its device position. The icon is right next
     * to the cursor on the same monitor, so its offset from the cursor only
     * has to be divided by that monitor's scale.</p>
     *
     * @param rect   bounds in device pixels, or {@code null}
     * @param cursor cursor in JavaFX coordinates
     * @return the bounds in JavaFX coordinates, or {@link Rectangle2D#EMPTY}
     */
    private Rectangle2D toFxBounds(PhysicalRect rect, Point2D cursor) {
        if (rect == null) {
            return Rectangle2D.EMPTY;
        }
        PhysicalPoint device = peer.cursorPosition();
        if (device == null) {
            return Rectangle2D.EMPTY;
        }
        Screen screen = Screen.getScreensForRectangle(cursor.getX(), cursor.getY(), 1, 1).stream()
                .findFirst()
                .orElse(Screen.getPrimary());
        double scaleX = screen.getOutputScaleX();
        double scaleY = screen.getOutputScaleY();
        return new Rectangle2D(
                cursor.getX() + (rect.left() - device.x()) / scaleX,
                cursor.getY() + (rect.top() - device.y()) / scaleY,
                rect.width() / scaleX,
                rect.height() / scaleY);
    }

    /**
     * The JavaFX robot, created on first use (it must be created on the JavaFX thread).
     *
     * @return the robot
     */
    private Robot robot() {
        if (robot == null) {
            robot = new Robot();
        }
        return robot;
    }

    /**
     * Guards the threading contract.
     *
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    private static void checkFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException(
                    "TrayIcon must be used on the JavaFX Application Thread; current thread is "
                            + Thread.currentThread().getName());
        }
    }
}
