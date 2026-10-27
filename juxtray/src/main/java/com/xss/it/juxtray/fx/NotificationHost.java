package com.xss.it.juxtray.fx;

import com.xss.it.juxtray.IconPixels;
import com.xss.it.juxtray.NotificationPlacement;
import com.xss.it.juxtray.PixelScaler;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.ColorScheme;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.geometry.Dimension2D;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import xss.it.juxtray.NotificationAnimation;
import xss.it.juxtray.NotificationType;
import xss.it.juxtray.TrayNotification;

/**
 * Shows {@link TrayNotification}s as animated JavaFX popups stacked next to
 * the tray.
 *
 * <p><strong>Windows:</strong> every notification is a {@link Popup} owned
 * by one invisible host stage ({@link StageStyle#UTILITY}, opacity 0,
 * always on top), shown only while notifications are open. Popups never
 * appear in the taskbar or Alt+Tab, and because their owner is topmost they
 * stay above other applications. Showing the host would normally activate
 * it and steal the keyboard focus; the {@code focusGuard} hands it straight
 * back to the window the user was working in.</p>
 *
 * <p><strong>Styling:</strong> a {@link StyleLink} gives the host and every
 * popup the built-in notification theme, the optional application style
 * source and the tray's stylesheets, live. Each notification is a plain
 * node tree with style classes (see {@link TrayNotification}), so all of it
 * is CSS. Each popup's content is a transparent frame with room for the
 * drop shadow, clipped so that slide animations appear to come out of the
 * screen edge.</p>
 *
 * <p><strong>Layout:</strong> {@link NotificationPlacement} computes where
 * each notification goes. Notifications are measured after CSS is applied,
 * and re-measured when their content changes; the others then glide to
 * their new places.</p>
 *
 * <p>All methods must be called on the JavaFX Application Thread.</p>
 */
public final class NotificationHost {

    /** Style class of a notification's root node. */
    public static final String STYLE_CLASS = "tray-notification";

    /** Transparent space around each notification for its drop shadow, in logical pixels. */
    private static final double SHADOW_SPACE = 24;

    /** Icon size used when CSS does not give {@code .notification-graphic} a preferred width. */
    private static final double DEFAULT_ICON_SIZE = 32;

    /** Length of the entry animation. */
    private static final Duration ENTRY_TIME = Duration.millis(320);

    /** Length of the exit animation. */
    private static final Duration EXIT_TIME = Duration.millis(220);

    /** Length of the glide when the stack re-flows. */
    private static final Duration MOVE_TIME = Duration.millis(260);

    /** Decelerating curve: quick start, soft landing. Used for entries and moves. */
    private static final Interpolator EASE_OUT = Interpolator.SPLINE(0.16, 1, 0.3, 1);

    /** Accelerating curve. Used for exits. */
    private static final Interpolator EASE_IN = Interpolator.SPLINE(0.4, 0, 1, 1);

    /** Scale a notification starts from with {@link NotificationAnimation#ZOOM}. */
    private static final double ZOOM_FROM = 0.85;

    /** Pseudo-class per notification type; {@link NotificationType#NONE} has none. */
    private static final PseudoClass INFO = PseudoClass.getPseudoClass("info");

    /** Pseudo-class of {@link NotificationType#SUCCESS}. */
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");

    /** Pseudo-class of {@link NotificationType#WARNING}. */
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");

    /** Pseudo-class of {@link NotificationType#ERROR}. */
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    /** Built-in light theme. */
    private static final URL LIGHT_THEME = NotificationHost.class.getResource("tray-notification-light.css");

    /** Built-in dark theme. */
    private static final URL DARK_THEME = NotificationHost.class.getResource("tray-notification-dark.css");

    /** Counter used to give every host stage a unique title. */
    private static final AtomicInteger COUNTER = new AtomicInteger();

    /** The invisible owner of the popups. */
    private final Stage stage;

    /** Keeps the host and the popups styled. */
    private final StyleLink styleLink;

    /** Where to stack notifications; a {@code null} value picks the corner next to the taskbar. */
    private final ObservableValue<Pos> position;

    /** How notifications appear and disappear. */
    private final ObservableValue<NotificationAnimation> animation;

    /** How many notifications may be open at once. */
    private final ObservableIntegerValue maxVisible;

    /** The tray icon's image, shown by {@link NotificationType#NONE} notifications. */
    private final Supplier<Image> appIcon;

    /** Runs an action that shows a window without stealing the keyboard focus. */
    private final Consumer<Runnable> focusGuard;

    /** Sound URL for notifications that do not set their own, or a {@code null} value for silence. */
    private final ObservableValue<String> defaultSound;

    /** Volume of notification sounds, 0 to 1. */
    private final ObservableDoubleValue volume;

    /** Whether notifications without any configured sound play the built-in sound of their type. */
    private final ObservableBooleanValue useDefaultSounds;

    /** Plays and caches the sounds. */
    private final NotificationSounds sounds = new NotificationSounds();

    /** Re-flows the stack when the position setting changes. */
    private final InvalidationListener positionListener = _ -> layoutStack(true);

    /** Open notifications, newest first. Closing ones are already removed. */
    private final List<Toast> toasts = new ArrayList<>();

    /**
     * Notifications playing their exit animation: out of the stack, but still
     * on screen and not yet reported as hidden.
     */
    private final List<Toast> leaving = new ArrayList<>();

    /** Number of popups still on screen, including those playing their exit animation. */
    private int onScreen;

    /**
     * Creates the host stage (not shown yet).
     *
     * @param userStylesheets tray stylesheets; watched
     * @param useDefaultTheme whether to apply the built-in theme; watched
     * @param colorScheme     forced light/dark variant, or a {@code null} value to follow the OS; watched
     * @param styleSource     application scene to follow, or a {@code null} value; watched
     * @param position        where to stack notifications, or a {@code null} value for automatic; watched
     * @param animation       how notifications appear; read for each animation
     * @param maxVisible      how many notifications may be open at once; read when one is shown
     * @param appIcon         supplies the tray icon's image
     * @param focusGuard      runs an action that shows a window without stealing the keyboard focus
     * @param defaultSound    sound URL for notifications without their own, or a {@code null} value
     *                        for silence; read when a notification opens
     * @param volume          volume of notification sounds, 0 to 1; read when a notification opens
     * @param useDefaultSounds whether notifications with no sound configured anywhere play the
     *                        built-in sound of their type; read when a notification opens
     */
    public NotificationHost(ObservableList<String> userStylesheets, ObservableBooleanValue useDefaultTheme,
                            ObservableValue<ColorScheme> colorScheme, ObservableValue<Scene> styleSource,
                            ObservableValue<Pos> position, ObservableValue<NotificationAnimation> animation,
                            ObservableIntegerValue maxVisible, Supplier<Image> appIcon,
                            Consumer<Runnable> focusGuard, ObservableValue<String> defaultSound,
                            ObservableDoubleValue volume, ObservableBooleanValue useDefaultSounds) {
        this.defaultSound = Objects.requireNonNull(defaultSound, "defaultSound");
        this.volume = Objects.requireNonNull(volume, "volume");
        this.useDefaultSounds = Objects.requireNonNull(useDefaultSounds, "useDefaultSounds");
        this.position = Objects.requireNonNull(position, "position");
        this.animation = Objects.requireNonNull(animation, "animation");
        this.maxVisible = Objects.requireNonNull(maxVisible, "maxVisible");
        this.appIcon = Objects.requireNonNull(appIcon, "appIcon");
        this.focusGuard = Objects.requireNonNull(focusGuard, "focusGuard");

        Region root = new Region();
        root.setStyle("-fx-background-color: transparent;");
        stage = new Stage(StageStyle.UTILITY);
        stage.setScene(new Scene(root, 1, 1, Color.TRANSPARENT));
        stage.setTitle("juxtray-notification-host-" + ProcessHandle.current().pid() + "-"
                + COUNTER.incrementAndGet());
        stage.setOpacity(0);
        stage.setWidth(1);
        stage.setHeight(1);
        stage.setResizable(false);
        // Popups owned by a topmost window are topmost too, so notifications stay above other apps.
        stage.setAlwaysOnTop(true);

        styleLink = new StyleLink(stage, LIGHT_THEME, DARK_THEME, userStylesheets, useDefaultTheme,
                colorScheme, styleSource);
        position.addListener(positionListener);
    }

    /**
     * Shows a notification. If it is already open here, its countdown restarts
     * instead.
     *
     * @param notification the notification
     * @param onHidden     called once the notification has fully closed
     * @return {@code true} if a new notification was opened, {@code false} if
     *         an open one was refreshed
     */
    public boolean show(TrayNotification notification, Runnable onHidden) {
        Objects.requireNonNull(notification, "notification");
        for (Toast toast : toasts) {
            if (toast.notification == notification) {
                toast.startTimer();
                return false;
            }
        }
        // Shown again while its exit animation still plays: end that one now. Otherwise its
        // onHidden would run after this new one opened, and mark a visible notification hidden.
        finishLeaving(notification);
        Toast toast = new Toast(notification, onHidden);
        toasts.addFirst(toast);
        toast.open();

        // Too many: close the oldest ones.
        int max = Math.max(1, maxVisible.get());
        while (toasts.size() > max) {
            toasts.getLast().close(true);
        }
        return true;
    }

    /**
     * Closes a notification, if it is open here.
     *
     * @param notification the notification
     * @param animate      {@code false} to close it at once, with no animation
     */
    public void hide(TrayNotification notification, boolean animate) {
        for (Toast toast : List.copyOf(toasts)) {
            if (toast.notification == notification) {
                toast.close(animate);
            }
        }
        if (!animate) {
            // "At once" also cuts short an exit animation that is already playing.
            finishLeaving(notification);
        }
    }

    /**
     * Closes every open notification.
     *
     * @param animate {@code false} to close them at once, with no animation
     */
    public void hideAll(boolean animate) {
        for (Toast toast : List.copyOf(toasts)) {
            toast.close(animate);
        }
        if (!animate) {
            for (Toast toast : List.copyOf(leaving)) {
                toast.finish();
            }
        }
    }

    /**
     * Ends the exit animation of a notification at once, if one is playing.
     *
     * @param notification the notification
     */
    private void finishLeaving(TrayNotification notification) {
        for (Toast toast : List.copyOf(leaving)) {
            if (toast.notification == notification) {
                toast.finish();
            }
        }
    }

    /**
     * Closes everything at once and stops listening to global state. The host
     * cannot be used afterwards.
     */
    public void dispose() {
        hideAll(false);
        sounds.dispose();
        position.removeListener(positionListener);
        styleLink.dispose();
        stage.close();
    }

    // ---- Stack ---------------------------------------------------------------------------

    /**
     * Where notifications stack: the configured position, or the corner next
     * to the taskbar.
     *
     * @return the position, never {@code null}
     */
    private Pos resolvedPosition() {
        Pos configured = position.getValue();
        if (configured != null) {
            return configured;
        }
        Screen screen = Screen.getPrimary();
        return NotificationPlacement.autoCorner(screen.getBounds(), screen.getVisualBounds());
    }

    /**
     * Moves every open notification to its place in the stack.
     *
     * @param animate whether already-placed notifications glide there
     */
    private void layoutStack(boolean animate) {
        if (toasts.isEmpty()) {
            return;
        }
        // The tray lives on the primary screen's taskbar.
        Rectangle2D workArea = Screen.getPrimary().getVisualBounds();
        List<Dimension2D> sizes = toasts.stream().map(Toast::size).toList();
        List<Point2D> places = NotificationPlacement.stack(workArea, resolvedPosition(), sizes);
        for (int i = 0; i < toasts.size(); i++) {
            toasts.get(i).moveTo(places.get(i), animate);
        }
    }

    /**
     * Shows the host stage, if it is not shown yet, without stealing focus.
     */
    private void ensureHostShown() {
        if (stage.isShowing()) {
            return;
        }
        Rectangle2D workArea = Screen.getPrimary().getVisualBounds();
        stage.setX(workArea.getMaxX() - 1);
        stage.setY(workArea.getMaxY() - 1);
        focusGuard.accept(stage::show);
    }

    /**
     * Book-keeping after a popup left the screen: hides the host once the
     * last one is gone (it would otherwise linger as an invisible window).
     */
    private void popupGone() {
        onScreen--;
        if (onScreen <= 0) {
            onScreen = 0;
            stage.hide();
        }
    }

    /**
     * URL of the built-in sound for a notification type: a short chime
     * bundled with Juxtray ({@code sounds/notification-<type>.wav}).
     *
     * @param type the notification type
     * @return the URL, or {@code null} if the resource is missing
     */
    private static String builtInSound(NotificationType type) {
        URL url = NotificationHost.class.getResource("sounds/notification-" + type.name().toLowerCase(Locale.ROOT)
                + ".wav");
        return url == null ? null : url.toExternalForm();
    }

    /**
     * Tells whether an event target is, or sits inside, a button, so a click
     * on a button is not also taken as a click on the notification.
     *
     * @param target the event target
     * @param root   the notification root, where the search stops
     * @return {@code true} if a button was hit
     */
    private static boolean isInsideButton(EventTarget target, Node root) {
        Node node = target instanceof Node n ? n : null;
        while (node != null && node != root) {
            if (node instanceof ButtonBase) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    // ---- One notification ----------------------------------------------------------------

    /**
     * The view of one notification: its popup, node tree, animations and
     * countdown, plus every listener it put on the {@link TrayNotification},
     * so they can all be removed when it closes.
     */
    private final class Toast {

        /** The notification shown. */
        private final TrayNotification notification;

        /** Called once the popup is gone. */
        private final Runnable onHidden;

        /** The window. */
        private final Popup popup = new Popup();

        /** Transparent, clipped frame that leaves room for the shadow. */
        private final StackPane frame = new StackPane();

        /** The visible notification, {@code .tray-notification}. */
        private final VBox root = new VBox();

        /** Holds the icon, {@code .notification-graphic}. */
        private final StackPane graphicBox = new StackPane();

        /** The title, {@code .notification-title}. */
        private final Label title = new Label();

        /** The message, {@code .notification-message}. */
        private final Label message = new Label();

        /** The close button, {@code .notification-close}. */
        private final Button close = new Button();

        /** The action buttons row, {@code .notification-actions}. */
        private final HBox actionsBox = new HBox();

        /** The countdown track, {@code .notification-timer}. */
        private final StackPane timerTrack = new StackPane();

        /** Fraction of time left, 1 to 0; drives the countdown bar. */
        private final DoubleProperty remaining = new SimpleDoubleProperty(1);

        /** Current window position of the popup, animated when the stack re-flows. */
        private final DoubleProperty windowX = new SimpleDoubleProperty();

        /** Current window position of the popup, animated when the stack re-flows. */
        private final DoubleProperty windowY = new SimpleDoubleProperty();

        /** Re-measures and re-flows when content changes. */
        private final InvalidationListener contentListener = _ -> contentChanged();

        /** Rebuilds the icon when the graphic, image or type changes. */
        private final InvalidationListener graphicListener = _ -> {
            updateGraphic();
            contentChanged();
        };

        /** Restarts the countdown when the duration changes. */
        private final InvalidationListener durationListener = _ -> startTimer();

        /** Rebuilds the action row when the action list changes. */
        private final ListChangeListener<Button> actionsListener = _ -> {
            updateActions();
            contentChanged();
        };

        /** Keeps the root's style classes in sync with {@link TrayNotification#getStyleClass()}. */
        private final InvalidationListener styleClassListener = _ -> updateStyleClasses();

        /** Closes the notification after an action button fired. */
        private final EventHandler<ActionEvent> actionCloser = _ -> close(true);

        /** Buttons that currently carry {@link #actionCloser}. */
        private final List<Button> wiredActions = new ArrayList<>();

        /** Counts down; {@code null} for notifications that stay. */
        private Timeline timer;

        /** The running entry or exit animation, if any. */
        private Animation transition;

        /** The running stack glide, if any. */
        private Timeline glide;

        /** Measured content size (without the shadow frame). */
        private Dimension2D size = new Dimension2D(0, 0);

        /** Whether the popup has been placed at least once. */
        private boolean placed;

        /** Whether closing has started. */
        private boolean closing;

        /** Whether {@link #finish()} has run; the popup is gone for good then. */
        private boolean finished;

        /**
         * Builds the node tree and binds it to the notification.
         *
         * @param notification the notification to show
         * @param onHidden     called once the popup is gone
         */
        Toast(TrayNotification notification, Runnable onHidden) {
            this.notification = notification;
            this.onHidden = onHidden;

            title.getStyleClass().add("notification-title");
            title.textProperty().bind(notification.titleProperty());
            title.visibleProperty().bind(notification.titleProperty().isNotEmpty());
            title.managedProperty().bind(title.visibleProperty());

            message.getStyleClass().add("notification-message");
            message.setWrapText(true);
            message.textProperty().bind(notification.messageProperty());
            message.visibleProperty().bind(notification.messageProperty().isNotEmpty());
            message.managedProperty().bind(message.visibleProperty());

            VBox text = new VBox(title, message);
            text.getStyleClass().add("notification-text");
            HBox.setHgrow(text, Priority.ALWAYS);

            graphicBox.getStyleClass().add("notification-graphic");
            graphicBox.managedProperty().bind(graphicBox.visibleProperty());

            Region closeIcon = new Region();
            closeIcon.getStyleClass().add("notification-close-icon");
            close.setGraphic(closeIcon);
            close.getStyleClass().setAll("notification-close");
            close.setFocusTraversable(false);
            close.visibleProperty().bind(notification.closableProperty());
            close.managedProperty().bind(close.visibleProperty());
            close.setOnAction(_ -> close(true));

            HBox body = new HBox(graphicBox, text, close);
            body.getStyleClass().add("notification-body");

            actionsBox.getStyleClass().add("notification-actions");
            actionsBox.managedProperty().bind(actionsBox.visibleProperty());

            Region timerBar = new Region();
            timerBar.getStyleClass().add("notification-timer-bar");
            // The bar spans the track's content area (inside its CSS padding) times the time left.
            timerBar.maxWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(0, timerTrack.getWidth() - timerTrack.snappedLeftInset()
                            - timerTrack.snappedRightInset()) * remaining.get(),
                    timerTrack.widthProperty(), timerTrack.insetsProperty(), remaining));
            timerTrack.getChildren().add(timerBar);
            timerTrack.setAlignment(Pos.CENTER_LEFT);
            timerTrack.getStyleClass().add("notification-timer");
            timerTrack.managedProperty().bind(timerTrack.visibleProperty());

            root.getChildren().addAll(body, actionsBox, timerTrack);
            updateStyleClasses();
            root.setOpacity(0);
            // A click on the notification itself (not on a button) is its action.
            root.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY && !isInsideButton(event.getTarget(), root)) {
                    EventHandler<ActionEvent> action = notification.getOnAction();
                    if (action != null) {
                        action.handle(new ActionEvent(notification, null));
                    }
                    close(true);
                }
            });
            // Hovering pauses the countdown, so the user has time to read.
            root.hoverProperty().addListener((_, _, hovered) -> {
                if (timer != null && !closing) {
                    if (hovered) {
                        timer.pause();
                    } else {
                        timer.play();
                    }
                }
            });

            frame.getChildren().add(root);
            frame.setStyle("-fx-background-color: transparent;");
            frame.setPadding(new Insets(SHADOW_SPACE));
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(frame.widthProperty());
            clip.heightProperty().bind(frame.heightProperty());
            frame.setClip(clip);

            popup.getContent().add(frame);
            popup.setAutoHide(false);
            popup.setAutoFix(false);
            popup.setHideOnEscape(false);
            popup.setConsumeAutoHidingEvents(false);

            windowX.addListener((_, _, x) -> popup.setAnchorX(x.doubleValue()));
            windowY.addListener((_, _, y) -> popup.setAnchorY(y.doubleValue()));

            notification.titleProperty().addListener(contentListener);
            notification.messageProperty().addListener(contentListener);
            notification.closableProperty().addListener(contentListener);
            notification.graphicProperty().addListener(graphicListener);
            notification.imageProperty().addListener(graphicListener);
            notification.typeProperty().addListener(graphicListener);
            notification.durationProperty().addListener(durationListener);
            notification.getActions().addListener(actionsListener);
            notification.getStyleClass().addListener(styleClassListener);
            updateActions();
        }

        /**
         * Gives the root {@code tray-notification} plus the notification's own
         * style classes. Called once at creation and whenever that list changes,
         * so classes added or removed while the notification shows apply at once.
         */
        private void updateStyleClasses() {
            // The root is a plain VBox with no style classes of its own, so it can be rebuilt.
            List<String> classes = new ArrayList<>();
            classes.add(STYLE_CLASS);
            classes.addAll(notification.getStyleClass());
            root.getStyleClass().setAll(classes);
            if (popup.isShowing()) {
                // A class can change padding, width or font: measure again and re-flow.
                contentChanged();
            }
        }

        /**
         * Shows the popup invisibly, measures it, places the stack and plays
         * the entry animation.
         */
        void open() {
            ensureHostShown();
            onScreen++;
            Rectangle2D workArea = Screen.getPrimary().getVisualBounds();
            // Show it where it will roughly end up; it is invisible until measured and placed.
            focusGuard.accept(() -> popup.show(stage, workArea.getMaxX(), workArea.getMaxY()));
            updateGraphic();
            measure();
            layoutStack(true);
            playEntry();
            playSound();
        }

        /**
         * Plays the notification's sound: its own, else the tray's default,
         * unless it is silent.
         */
        private void playSound() {
            if (notification.isSilent()) {
                return;
            }
            String sound = notification.getSound();
            if (sound == null) {
                sound = defaultSound.getValue();
            }
            if (sound == null && useDefaultSounds.get()) {
                sound = builtInSound(notification.getType());
            }
            sounds.play(sound, volume.get());
        }

        /**
         * The measured content size.
         *
         * @return width and height without the shadow frame
         */
        Dimension2D size() {
            return size;
        }

        /**
         * Measures the content after CSS and fixes the frame to that size plus
         * the shadow space. Text wraps, so the height is computed for the
         * actual width.
         */
        private void measure() {
            root.applyCss();
            double width = Math.ceil(root.prefWidth(-1));
            double height = Math.ceil(root.prefHeight(width));
            size = new Dimension2D(width, height);
            double frameWidth = width + 2 * SHADOW_SPACE;
            double frameHeight = height + 2 * SHADOW_SPACE;
            frame.setMinSize(frameWidth, frameHeight);
            frame.setPrefSize(frameWidth, frameHeight);
            frame.setMaxSize(frameWidth, frameHeight);
            frame.resize(frameWidth, frameHeight);
            frame.layout();
        }

        /**
         * Re-measures after a content change and lets the stack re-flow.
         */
        private void contentChanged() {
            if (closing) {
                return;
            }
            measure();
            layoutStack(true);
        }

        /**
         * Moves the popup so its content's top-left corner lands on a point.
         *
         * @param contentTopLeft target, in JavaFX screen coordinates
         * @param animate        glide there instead of jumping (ignored on first placement)
         */
        void moveTo(Point2D contentTopLeft, boolean animate) {
            double x = contentTopLeft.getX() - SHADOW_SPACE;
            double y = contentTopLeft.getY() - SHADOW_SPACE;
            if (glide != null) {
                glide.stop();
                glide = null;
            }
            if (!placed || !animate || animation.getValue() == NotificationAnimation.NONE) {
                placed = true;
                windowX.set(x);
                windowY.set(y);
                // The listeners only fire on change; make sure the popup is really there.
                popup.setAnchorX(x);
                popup.setAnchorY(y);
                return;
            }
            if (windowX.get() == x && windowY.get() == y) {
                return;
            }
            glide = new Timeline(new KeyFrame(MOVE_TIME,
                    new KeyValue(windowX, x, EASE_OUT),
                    new KeyValue(windowY, y, EASE_OUT)));
            glide.play();
        }

        /**
         * Puts the right icon into the graphic box: the graphic node, the image,
         * the type's built-in icon, or the tray icon's image. Also updates the
         * type pseudo-classes.
         */
        private void updateGraphic() {
            NotificationType type = notification.getType();
            root.pseudoClassStateChanged(INFO, type == NotificationType.INFO);
            root.pseudoClassStateChanged(SUCCESS, type == NotificationType.SUCCESS);
            root.pseudoClassStateChanged(WARNING, type == NotificationType.WARNING);
            root.pseudoClassStateChanged(ERROR, type == NotificationType.ERROR);

            Node content;
            if (notification.getGraphic() != null) {
                content = notification.getGraphic();
            } else if (notification.getImage() != null) {
                content = imageView(notification.getImage());
            } else if (type != NotificationType.NONE) {
                Region glyph = new Region();
                glyph.getStyleClass().add("notification-glyph");
                StackPane icon = new StackPane(glyph);
                icon.getStyleClass().add("notification-icon");
                content = icon;
            } else {
                Image image = appIcon.get();
                content = image == null ? null : imageView(image);
            }
            if (content == null) {
                graphicBox.getChildren().clear();
                graphicBox.setVisible(false);
            } else {
                graphicBox.getChildren().setAll(content);
                graphicBox.setVisible(true);
            }
        }

        /**
         * Builds an image view at the icon size. Loaded images are resampled
         * with the area-average filter at the screen's output scale, which is
         * much sharper than letting the view shrink a large image.
         *
         * @param image the image
         * @return the view
         */
        private ImageView imageView(Image image) {
            graphicBox.applyCss();
            double iconSize = graphicBox.getPrefWidth() > 0 ? graphicBox.getPrefWidth() : DEFAULT_ICON_SIZE;
            ImageView view = new ImageView();
            view.setFitWidth(iconSize);
            view.setFitHeight(iconSize);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            IconPixels pixels = image.getProgress() >= 1.0 && !image.isError() ? FxImages.toPixels(image) : null;
            if (pixels == null) {
                // Still loading or unreadable: let JavaFX scale it as it arrives.
                view.setImage(image);
                return view;
            }
            double scale = Screen.getPrimary().getOutputScaleX();
            int device = (int) Math.round(iconSize * Math.max(1, scale));
            view.setImage(FxImages.toImage(PixelScaler.fit(pixels, device)));
            return view;
        }

        /**
         * Rebuilds the action row from the notification's buttons, making
         * each one close the notification once its own handler has run.
         */
        private void updateActions() {
            for (Button button : wiredActions) {
                button.removeEventHandler(ActionEvent.ACTION, actionCloser);
            }
            wiredActions.clear();
            for (Button button : notification.getActions()) {
                if (!button.getStyleClass().contains("notification-action")) {
                    button.getStyleClass().add("notification-action");
                }
                button.addEventHandler(ActionEvent.ACTION, actionCloser);
                wiredActions.add(button);
            }
            actionsBox.getChildren().setAll(notification.getActions());
            actionsBox.setVisible(!notification.getActions().isEmpty());
        }

        /**
         * Starts (or restarts) the countdown, if the notification has a finite
         * duration. The countdown bar is shown only then.
         */
        void startTimer() {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            if (closing) {
                return;
            }
            Duration duration = notification.getDuration();
            boolean finite = !duration.isIndefinite() && !duration.isUnknown() && duration.greaterThan(Duration.ZERO);
            boolean wasVisible = timerTrack.isVisible();
            timerTrack.setVisible(finite);
            if (wasVisible != finite) {
                contentChanged();
            }
            remaining.set(1);
            if (!finite) {
                return;
            }
            timer = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(remaining, 1)),
                    new KeyFrame(duration, new KeyValue(remaining, 0, Interpolator.LINEAR)));
            timer.setOnFinished(_ -> close(true));
            if (root.isHover()) {
                // Restarted under the mouse: wait for the mouse to leave.
                timer.pause();
            } else {
                timer.play();
            }
        }

        /**
         * Plays the entry animation, then starts the countdown.
         */
        private void playEntry() {
            NotificationAnimation kind = animationKind();
            if (kind == NotificationAnimation.NONE) {
                root.setOpacity(1);
                startTimer();
                return;
            }
            List<KeyValue> start = new ArrayList<>();
            List<KeyValue> end = new ArrayList<>();
            start.add(new KeyValue(root.opacityProperty(), 0));
            end.add(new KeyValue(root.opacityProperty(), 1, EASE_OUT));
            switch (kind) {
                case SLIDE -> {
                    Point2D from = slideOffset();
                    start.add(new KeyValue(root.translateXProperty(), from.getX()));
                    start.add(new KeyValue(root.translateYProperty(), from.getY()));
                    end.add(new KeyValue(root.translateXProperty(), 0, EASE_OUT));
                    end.add(new KeyValue(root.translateYProperty(), 0, EASE_OUT));
                }
                case ZOOM -> {
                    start.add(new KeyValue(root.scaleXProperty(), ZOOM_FROM));
                    start.add(new KeyValue(root.scaleYProperty(), ZOOM_FROM));
                    end.add(new KeyValue(root.scaleXProperty(), 1, EASE_OUT));
                    end.add(new KeyValue(root.scaleYProperty(), 1, EASE_OUT));
                }
                case FADE, NONE -> {
                    // Opacity only.
                }
            }
            transition = new Timeline(
                    new KeyFrame(Duration.ZERO, start.toArray(KeyValue[]::new)),
                    new KeyFrame(ENTRY_TIME, end.toArray(KeyValue[]::new)));
            transition.setOnFinished(_ -> {
                transition = null;
                startTimer();
            });
            transition.play();
        }

        /**
         * Closes the notification: leaves the stack at once (so the others
         * re-flow), plays the exit animation, then removes the popup.
         *
         * @param animate {@code false} to remove it at once
         */
        void close(boolean animate) {
            if (closing) {
                return;
            }
            closing = true;
            toasts.remove(this);
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            if (transition != null) {
                transition.stop();
                transition = null;
            }
            layoutStack(animate);

            NotificationAnimation kind = animationKind();
            if (!animate || kind == NotificationAnimation.NONE) {
                finish();
                return;
            }
            // Still on screen until the animation ends; tracked so it can be cut short.
            leaving.add(this);
            List<KeyValue> end = new ArrayList<>();
            end.add(new KeyValue(root.opacityProperty(), 0, EASE_IN));
            switch (kind) {
                case SLIDE -> {
                    Point2D to = slideOffset();
                    end.add(new KeyValue(root.translateXProperty(), to.getX(), EASE_IN));
                    end.add(new KeyValue(root.translateYProperty(), to.getY(), EASE_IN));
                }
                case ZOOM -> {
                    end.add(new KeyValue(root.scaleXProperty(), ZOOM_FROM, EASE_IN));
                    end.add(new KeyValue(root.scaleYProperty(), ZOOM_FROM, EASE_IN));
                }
                case FADE, NONE -> {
                    // Opacity only.
                }
            }
            transition = new Timeline(new KeyFrame(EXIT_TIME, end.toArray(KeyValue[]::new)));
            transition.setOnFinished(_ -> finish());
            transition.play();
        }

        /**
         * Final clean-up: hides the popup, removes every listener from the
         * notification and reports that it is gone. Runs when the exit
         * animation ends, or earlier to cut it short; only the first call
         * does anything.
         */
        private void finish() {
            if (finished) {
                return;
            }
            finished = true;
            // Normally the exit animation itself got us here; when cut short it must not
            // keep running (its onFinished would call back into a finished toast).
            closing = true;
            toasts.remove(this);
            leaving.remove(this);
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            if (transition != null) {
                transition.stop();
                transition = null;
            }
            if (glide != null) {
                glide.stop();
                glide = null;
            }
            notification.titleProperty().removeListener(contentListener);
            notification.messageProperty().removeListener(contentListener);
            notification.closableProperty().removeListener(contentListener);
            notification.graphicProperty().removeListener(graphicListener);
            notification.imageProperty().removeListener(graphicListener);
            notification.typeProperty().removeListener(graphicListener);
            notification.durationProperty().removeListener(durationListener);
            notification.getActions().removeListener(actionsListener);
            notification.getStyleClass().removeListener(styleClassListener);
            for (Button button : wiredActions) {
                button.removeEventHandler(ActionEvent.ACTION, actionCloser);
            }
            wiredActions.clear();
            // Unbind and release user nodes so they can be shown again in another notification.
            title.textProperty().unbind();
            message.textProperty().unbind();
            title.visibleProperty().unbind();
            message.visibleProperty().unbind();
            close.visibleProperty().unbind();
            actionsBox.getChildren().clear();
            graphicBox.getChildren().clear();
            popup.hide();
            popupGone();
            if (onHidden != null) {
                onHidden.run();
            }
        }

        /**
         * The animation setting, never {@code null}.
         *
         * @return the animation kind
         */
        private NotificationAnimation animationKind() {
            NotificationAnimation kind = animation.getValue();
            return kind == null ? NotificationAnimation.SLIDE : kind;
        }

        /**
         * Where a sliding notification comes from (and goes back to): just out
         * of its frame, towards the nearest screen edge. Side stacks slide
         * sideways; centred stacks slide vertically.
         *
         * @return translate offset of the hidden position
         */
        private Point2D slideOffset() {
            Pos pos = resolvedPosition();
            double dx = size.getWidth() + SHADOW_SPACE;
            double dy = size.getHeight() + SHADOW_SPACE;
            if (pos.getHpos() == HPos.RIGHT) {
                return new Point2D(dx, 0);
            }
            if (pos.getHpos() == HPos.LEFT) {
                return new Point2D(-dx, 0);
            }
            return new Point2D(0, pos.getVpos() == VPos.TOP ? -dy : dy);
        }
    }
}
