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
import com.xss.it.juxtray.fx.NotificationHost;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
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
 * light/dark setting (or a forced one, see {@link #setColorScheme}) and exposes its palette as looked-up colours
 * ({@code -tray-background}, {@code -tray-text}, {@code -tray-hover} ...),
 * so a stylesheet added through {@link #getStylesheets()} can retheme it
 * with a couple of lines or replace it entirely. Turn it off with
 * {@link #setUseDefaultTheme(boolean)}. The application's own scene
 * stylesheets do <em>not</em> apply to tray menus, and tray stylesheets do
 * not leak into the application.</p>
 *
 * <h2>Notifications</h2>
 * <p>{@link #showNotification(TrayNotification)} pops up a notification next
 * to the tray. Notifications are JavaFX nodes too: animated (see
 * {@link #setNotificationAnimation}), stacked, with icons, action buttons and
 * a countdown that pauses on hover, and styled with the same CSS as the
 * menu ({@code .tray-notification} and friends, documented on
 * {@link TrayNotification}). They have their own built-in light/dark theme,
 * which follows the same settings as the menu theme.</p>
 * <pre>{@code
 * tray.showNotification("Upload complete", "report.pdf is online.", NotificationType.SUCCESS);
 * }</pre>
 *
 * <h2>Following the application's theme</h2>
 * <p>Set a {@link #setStyleSource style source} and the menu and notifications
 * follow an application scene, live: its stylesheets, its user-agent
 * stylesheet, and its root's style classes, inline style and pseudo-classes.
 * When the application switches theme (swaps a stylesheet, toggles a
 * {@code dark} class on its root, ...), the tray restyles with it. A global
 * {@code Application.setUserAgentStylesheet} theme reaches the tray without
 * any of this.</p>
 * <pre>{@code
 * tray.styleSourceProperty().bind(stage.sceneProperty());
 * }</pre>
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

    /** Owner, layout and animations of the notifications. */
    private final NotificationHost notificationHost;

    /** User stylesheets for the menu. */
    private final ObservableList<String> stylesheets = FXCollections.observableArrayList();

    /**
     * Re-applies the image once a background-loading image has finished, or
     * has failed. It watches both {@code progress} and {@code error}, because
     * a failed background load is reported through {@code error} and is not
     * guaranteed to also drive the progress to 1.
     */
    private final InvalidationListener imageLoadListener = _ -> {
        Image current = getImage();
        // Reading the values re-validates the properties, so later changes are reported again.
        if (current != null && (current.isError() || current.getProgress() >= 1.0)) {
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

    // Forces the light or dark built-in theme (menu and notifications); null follows the OS colour scheme.
    private final ObjectProperty<ColorScheme> colorScheme = new SimpleObjectProperty<>(this, "colorScheme");

    // Application scene whose styles the menu and notifications follow; null for none.
    private final ObjectProperty<Scene> styleSource = new SimpleObjectProperty<>(this, "styleSource");

    // Where notifications stack; null picks the screen corner next to the taskbar.
    private final ObjectProperty<Pos> notificationPosition = new SimpleObjectProperty<>(this, "notificationPosition");

    // How notifications appear and disappear.
    private final ObjectProperty<NotificationAnimation> notificationAnimation =
            new SimpleObjectProperty<>(this, "notificationAnimation", NotificationAnimation.SLIDE);

    // How many notifications may be open at once; the oldest closes when one more opens.
    private final IntegerProperty maxNotifications = new SimpleIntegerProperty(this, "maxNotifications", 4);

    // URL of a WAV/MP3 played for notifications without their own sound; null for silence.
    private final StringProperty notificationSound = new SimpleStringProperty(this, "notificationSound");

    // Volume of notification sounds, 0 to 1.
    private final DoubleProperty notificationVolume = new SimpleDoubleProperty(this, "notificationVolume", 1.0);

    // Whether notifications with no sound set anywhere play the built-in sound of their type.
    private final BooleanProperty useDefaultSounds = new SimpleBooleanProperty(this, "useDefaultSounds", true);

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
        MenuHost menu = null;
        try {
            menu = new MenuHost(stylesheets, useDefaultTheme, colorScheme, styleSource, peer::activateWindow);
            notificationHost = new NotificationHost(stylesheets, useDefaultTheme, colorScheme, styleSource,
                    notificationPosition, notificationAnimation, maxNotifications, this::getImage,
                    peer::runWithoutActivation, notificationSound, notificationVolume, useDefaultSounds);
        } catch (RuntimeException | Error e) {
            // The icon is already in the tray; don't leave it there (and its HICON alive) when
            // the constructor fails, since the caller never gets an object to close.
            if (menu != null) {
                menu.dispose();
            }
            peer.close();
            throw e;
        }
        menuHost = menu;

        image.addListener((_, oldImage, _) -> {
            if (oldImage != null) {
                unwatchLoading(oldImage);
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
     * Which variant of the built-in themes (menu and notifications) to use:
     * {@link ColorScheme#LIGHT}, {@link ColorScheme#DARK}, or {@code null}
     * (the default) to follow the OS setting live. Has no effect while
     * {@link #isUseDefaultTheme()} is off.
     *
     * @return the property
     */
    public ObjectProperty<ColorScheme> colorSchemeProperty() {
        return colorScheme;
    }

    /**
     * Gets the forced colour scheme of the built-in themes (menu and notifications).
     *
     * @return the forced scheme, or {@code null} when following the OS
     */
    public ColorScheme getColorScheme() {
        return colorScheme.get();
    }

    /**
     * Forces the light or dark built-in themes (menu and notifications).
     *
     * @param value the scheme to use, or {@code null} to follow the OS
     */
    public void setColorScheme(ColorScheme value) {
        colorScheme.set(value);
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
     * The application scene whose styles the menu and notifications follow.
     *
     * <p>Its stylesheets are applied after the built-in theme and before
     * {@link #getStylesheets()}. Its user-agent stylesheet is used too, and its
     * root's style classes, inline style and pseudo-classes are copied onto
     * the root of every menu, submenu and notification, so rules such as
     * {@code .root.dark .context-menu} or {@code .root:dark .tray-notification}
     * match. Everything is tracked live, including open menus and
     * notifications. To follow whatever scene a stage shows:</p>
     * <pre>{@code
     * tray.styleSourceProperty().bind(stage.sceneProperty());
     * }</pre>
     * <p>Application stylesheets usually target the application's own nodes,
     * so they only change the tray where they mention its selectors, or where
     * they redefine the {@code -tray-*} colours. Combine with
     * {@link #setUseDefaultTheme(boolean) setUseDefaultTheme(false)} to let the
     * application's theme decide everything.</p>
     *
     * @return the property
     */
    public ObjectProperty<Scene> styleSourceProperty() {
        return styleSource;
    }

    /**
     * Gets the application scene whose styles the tray follows.
     *
     * @return the scene, or {@code null}
     */
    public Scene getStyleSource() {
        return styleSource.get();
    }

    /**
     * Sets the application scene whose styles the tray follows.
     *
     * @param value the scene, or {@code null} for none
     */
    public void setStyleSource(Scene value) {
        styleSource.set(value);
    }

    /**
     * Where notifications appear. Corners and edge centres are supported:
     * the vertical part picks the top or bottom edge, the horizontal part
     * the left side, centre or right side. Notifications always stack from a
     * screen edge, so a vertical part other than {@code TOP} (such as
     * {@code CENTER} or {@code BASELINE}) means the bottom edge: for example
     * {@link Pos#CENTER_LEFT} behaves like {@link Pos#BOTTOM_LEFT}.
     * {@code null} (the default) picks the corner next to the taskbar, where
     * the tray is. Notifications are shown on the primary screen, which is
     * where the Windows tray lives.
     *
     * @return the property
     */
    public ObjectProperty<Pos> notificationPositionProperty() {
        return notificationPosition;
    }

    /**
     * Gets where notifications appear.
     *
     * @return the position, or {@code null} for automatic
     */
    public Pos getNotificationPosition() {
        return notificationPosition.get();
    }

    /**
     * Sets where notifications appear. Open notifications glide to the new place.
     *
     * @param value the position, or {@code null} for automatic
     */
    public void setNotificationPosition(Pos value) {
        notificationPosition.set(value);
    }

    /**
     * How notifications appear and disappear. Default
     * {@link NotificationAnimation#SLIDE}; {@code null} means the same.
     *
     * @return the property
     */
    public ObjectProperty<NotificationAnimation> notificationAnimationProperty() {
        return notificationAnimation;
    }

    /**
     * Gets how notifications appear and disappear.
     *
     * @return the animation, or {@code null}
     */
    public NotificationAnimation getNotificationAnimation() {
        return notificationAnimation.get();
    }

    /**
     * Sets how notifications appear and disappear.
     *
     * @param value the animation
     */
    public void setNotificationAnimation(NotificationAnimation value) {
        notificationAnimation.set(value);
    }

    /**
     * How many notifications may be open at once. When one more opens, the
     * oldest closes. Default 4; values below 1 count as 1.
     *
     * @return the property
     */
    public IntegerProperty maxNotificationsProperty() {
        return maxNotifications;
    }

    /**
     * Gets how many notifications may be open at once.
     *
     * @return the limit
     */
    public int getMaxNotifications() {
        return maxNotifications.get();
    }

    /**
     * Sets how many notifications may be open at once.
     *
     * @param value the limit
     */
    public void setMaxNotifications(int value) {
        maxNotifications.set(value);
    }

    /**
     * The sound played for every notification that does not set its own
     * ({@link TrayNotification#setSound}) and is not
     * {@link TrayNotification#setSilent silent}: the URL of a WAV or MP3 file
     * (any format JavaFX Media plays). {@code null} (the default) falls back
     * to the built-in sound of each notification's type, see
     * {@link #setUseDefaultSounds(boolean)}.
     * <pre>{@code
     * tray.setNotificationSound(getClass().getResource("chime.wav").toExternalForm());
     * }</pre>
     *
     * @return the property
     */
    public StringProperty notificationSoundProperty() {
        return notificationSound;
    }

    /**
     * Gets the default notification sound.
     *
     * @return the URL, or {@code null} for none
     */
    public String getNotificationSound() {
        return notificationSound.get();
    }

    /**
     * Sets the default notification sound.
     *
     * @param value the URL, or {@code null} for none
     */
    public void setNotificationSound(String value) {
        notificationSound.set(value);
    }

    /**
     * Whether notifications play Juxtray's built-in sounds when no sound is
     * set anywhere (neither on the notification nor through
     * {@link #setNotificationSound}). Each {@link NotificationType} has its
     * own short chime: a soft rising one for {@code NONE}, a clear bell for
     * {@code INFO}, an ascending arpeggio for {@code SUCCESS}, a double tone
     * for {@code WARNING} and a falling tone for {@code ERROR}. Default
     * {@code true}; turn it off for silent notifications unless a sound is set.
     *
     * @return the property
     */
    public BooleanProperty useDefaultSoundsProperty() {
        return useDefaultSounds;
    }

    /**
     * Tells whether the built-in sounds are used.
     *
     * @return {@code true} if they are
     */
    public boolean isUseDefaultSounds() {
        return useDefaultSounds.get();
    }

    /**
     * Turns the built-in notification sounds on or off.
     *
     * @param value {@code true} to play them when no other sound is set
     */
    public void setUseDefaultSounds(boolean value) {
        useDefaultSounds.set(value);
    }

    /**
     * Volume of notification sounds, from 0 (muted) to 1 (full, the default).
     * Values outside that range are clamped.
     *
     * @return the property
     */
    public DoubleProperty notificationVolumeProperty() {
        return notificationVolume;
    }

    /**
     * Gets the notification sound volume.
     *
     * @return the volume, 0 to 1
     */
    public double getNotificationVolume() {
        return notificationVolume.get();
    }

    /**
     * Sets the notification sound volume.
     *
     * @param value the volume, 0 to 1
     */
    public void setNotificationVolume(double value) {
        notificationVolume.set(value);
    }

    /**
     * Stylesheet URLs applied to the menu and the notifications, on top of the
     * built-in themes and the {@link #setStyleSource style source}. They work
     * exactly like {@code Scene.getStylesheets()}, and changes apply at once,
     * even to an open menu or notification.
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
     * Shows a notification next to the tray. If it is already showing here,
     * its countdown restarts; if another tray icon shows it, it moves here.
     * Does nothing once this icon is closed.
     *
     * @param notification the notification
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    public void showNotification(TrayNotification notification) {
        checkFxThread();
        Objects.requireNonNull(notification, "notification");
        if (closed) {
            return;
        }
        TrayIcon current = notification.owner();
        if (current != null && current != this) {
            // Closed at once, so its onHidden runs before it is shown here.
            current.notificationHost.hide(notification, false);
        }
        if (notificationHost.show(notification, notification::markHidden)) {
            notification.markShown(this);
        }
    }

    /**
     * Shows a plain notification with the tray icon's image.
     *
     * @param title   the title, or {@code null}
     * @param message the message, or {@code null}
     * @return the notification, for example to {@link TrayNotification#hide() hide} it later
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    public TrayNotification showNotification(String title, String message) {
        return showNotification(title, message, NotificationType.NONE);
    }

    /**
     * Shows a notification of a given type.
     *
     * @param title   the title, or {@code null}
     * @param message the message, or {@code null}
     * @param type    the kind of notification (icon and CSS pseudo-class)
     * @return the notification, for example to {@link TrayNotification#hide() hide} it later
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    public TrayNotification showNotification(String title, String message, NotificationType type) {
        TrayNotification notification = new TrayNotification(title, message, type);
        showNotification(notification);
        return notification;
    }

    /**
     * Closes every open notification of this icon, with their exit animations.
     *
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    public void hideNotifications() {
        checkFxThread();
        notificationHost.hideAll(true);
    }

    /**
     * Closes one notification with its exit animation. Called by
     * {@link TrayNotification#hide()}.
     *
     * @param notification the notification
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    void hideNotification(TrayNotification notification) {
        checkFxThread();
        notificationHost.hide(notification, true);
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
            unwatchLoading(current);
        }
        menuHost.dispose();
        notificationHost.dispose();
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
            // Stop watching first: error and progress may both fire, and the failure is logged once.
            unwatchLoading(current);
            LOG.log(Level.WARNING, "Tray icon image failed to load: " + current.getUrl(), current.getException());
            return;
        }
        if (current.getProgress() < 1.0) {
            // Background loading: try again once done (or failed). Remove first so the listener
            // is never registered twice.
            unwatchLoading(current);
            current.progressProperty().addListener(imageLoadListener);
            current.errorProperty().addListener(imageLoadListener);
            return;
        }
        unwatchLoading(current);
        IconPixels pixels = FxImages.toPixels(current);
        peer.setIcon(pixels == null ? null : PixelScaler.fit(pixels, peer.iconSize()));
    }

    /**
     * Stops waiting for an image to finish loading.
     *
     * @param target the image that was being watched
     */
    private void unwatchLoading(Image target) {
        target.progressProperty().removeListener(imageLoadListener);
        target.errorProperty().removeListener(imageLoadListener);
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
        // The Robot reports the centre of the cursor's device pixel (cursor on device pixel 2442
        // at 100 % reads 2442.5), while the OS gives that pixel's top-left corner. Step back half a
        // device pixel so both name the same point; otherwise every bound is off by half a pixel.
        double cornerX = cursor.getX() - 0.5 / scaleX;
        double cornerY = cursor.getY() - 0.5 / scaleY;
        return new Rectangle2D(
                cornerX + (rect.left() - device.x()) / scaleX,
                cornerY + (rect.top() - device.y()) / scaleY,
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
