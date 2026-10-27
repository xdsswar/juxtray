package xss.it.juxtray;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.util.Duration;

/**
 * A notification shown next to the tray by {@link TrayIcon#showNotification(TrayNotification)}.
 *
 * <p>Notifications are drawn by JavaFX, not by the OS, so they look the same
 * everywhere and are styled entirely with CSS, exactly like the tray menu.
 * They animate in and out, stack when several are open, pause their
 * countdown while hovered, and can carry action buttons.</p>
 *
 * <pre>{@code
 * TrayNotification done = new TrayNotification("Backup finished", "42 files copied.", NotificationType.SUCCESS);
 * Button open = new Button("Open folder");
 * open.setOnAction(e -> openFolder());
 * done.getActions().add(open);
 * done.setOnAction(e -> stage.show());   // click on the notification itself
 * tray.showNotification(done);
 * }</pre>
 *
 * <h2>Live updates</h2>
 * <p>Every property can be changed while the notification is showing (for
 * example to report progress); the notification updates and the stack
 * re-flows with an animation.</p>
 *
 * <h2>Icon</h2>
 * <p>The first of these that is set is shown: the {@link #setGraphic graphic}
 * node, the {@link #setImage image}, the built-in icon of the
 * {@link #setType type}, and finally (for {@link NotificationType#NONE}) the
 * tray icon's own image.</p>
 *
 * <h2>CSS structure</h2>
 * <pre>
 * .tray-notification            (VBox; plus :info / :success / :warning / :error, :hover, getStyleClass())
 *   .notification-body          (HBox)
 *     .notification-graphic     (StackPane)
 *       .notification-icon      (built-in type icon: a Region shaped by -fx-shape)
 *         .notification-glyph
 *     .notification-text        (VBox)
 *       .notification-title     (Label)
 *       .notification-message   (Label, wraps)
 *     .notification-close       (Button) &gt; .notification-close-icon
 *   .notification-actions       (HBox) &gt; your Buttons, each with .notification-action
 *   .notification-timer         (StackPane) &gt; .notification-timer-bar (shrinks as time runs out)
 * </pre>
 *
 * <p>Use on the JavaFX Application Thread only.</p>
 */
public final class TrayNotification {

    /** How long a notification stays when its duration is not changed: 5 seconds. */
    public static final Duration DEFAULT_DURATION = Duration.seconds(5);

    // Bold first line; null or empty hides the title.
    private final StringProperty title = new SimpleStringProperty(this, "title");

    // Body text; wraps over several lines. null or empty hides it.
    private final StringProperty message = new SimpleStringProperty(this, "message");

    // Kind of notification: default icon and CSS pseudo-class.
    private final ObjectProperty<NotificationType> type =
            new SimpleObjectProperty<>(this, "type", NotificationType.NONE);

    // Icon image; replaces the type's built-in icon.
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image");

    // Icon node; replaces both the image and the type's built-in icon.
    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    // Time before the notification closes on its own; Duration.INDEFINITE keeps it until closed.
    private final ObjectProperty<Duration> duration = new SimpleObjectProperty<>(this, "duration", DEFAULT_DURATION);

    // Whether the close button is shown.
    private final BooleanProperty closable = new SimpleBooleanProperty(this, "closable", true);

    // URL of a WAV/MP3 to play when shown; null uses the tray icon's notification sound.
    private final StringProperty sound = new SimpleStringProperty(this, "sound");

    // Whether no sound plays at all, not even the tray icon's default.
    private final BooleanProperty silent = new SimpleBooleanProperty(this, "silent");

    // Called when the notification itself is clicked (not one of its buttons). The notification then closes.
    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new SimpleObjectProperty<>(this, "onAction");

    // Called once the notification has been shown.
    private final ObjectProperty<EventHandler<Event>> onShown = new SimpleObjectProperty<>(this, "onShown");

    // Called once the notification has closed, for whatever reason.
    private final ObjectProperty<EventHandler<Event>> onHidden = new SimpleObjectProperty<>(this, "onHidden");

    // Whether the notification is on screen.
    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing");

    /** Action buttons, shown in a row under the text. */
    private final ObservableList<Button> actions = FXCollections.observableArrayList();

    /** Extra style classes for the notification's root node. */
    private final ObservableList<String> styleClass = FXCollections.observableArrayList();

    /** The tray icon showing this notification, or {@code null} while hidden. */
    private TrayIcon owner;

    /**
     * Creates an empty notification of type {@link NotificationType#NONE}.
     */
    public TrayNotification() {
    }

    /**
     * Creates a notification of type {@link NotificationType#NONE}.
     *
     * @param title   the title, or {@code null}
     * @param message the message, or {@code null}
     */
    public TrayNotification(String title, String message) {
        this(title, message, NotificationType.NONE);
    }

    /**
     * Creates a notification.
     *
     * @param title   the title, or {@code null}
     * @param message the message, or {@code null}
     * @param type    the kind of notification, or {@code null} for {@link NotificationType#NONE}
     */
    public TrayNotification(String title, String message, NotificationType type) {
        setTitle(title);
        setMessage(message);
        setType(type);
    }

    // ---- Properties ----------------------------------------------------------------------

    /**
     * The title: the bold first line.
     *
     * @return the title property
     */
    public StringProperty titleProperty() {
        return title;
    }

    /**
     * Gets the title.
     *
     * @return the title, or {@code null}
     */
    public String getTitle() {
        return title.get();
    }

    /**
     * Sets the title.
     *
     * @param value the title, or {@code null} for none
     */
    public void setTitle(String value) {
        title.set(value);
    }

    /**
     * The message: body text that wraps over several lines.
     *
     * @return the message property
     */
    public StringProperty messageProperty() {
        return message;
    }

    /**
     * Gets the message.
     *
     * @return the message, or {@code null}
     */
    public String getMessage() {
        return message.get();
    }

    /**
     * Sets the message.
     *
     * @param value the message, or {@code null} for none
     */
    public void setMessage(String value) {
        message.set(value);
    }

    /**
     * The kind of notification. It selects the built-in icon and the CSS
     * pseudo-class. {@code null} is treated as {@link NotificationType#NONE}.
     *
     * @return the type property
     */
    public ObjectProperty<NotificationType> typeProperty() {
        return type;
    }

    /**
     * Gets the kind of notification.
     *
     * @return the type, never {@code null}
     */
    public NotificationType getType() {
        NotificationType value = type.get();
        return value == null ? NotificationType.NONE : value;
    }

    /**
     * Sets the kind of notification.
     *
     * @param value the type, or {@code null} for {@link NotificationType#NONE}
     */
    public void setType(NotificationType value) {
        type.set(value == null ? NotificationType.NONE : value);
    }

    /**
     * Icon image. It replaces the type's built-in icon and is resampled with a
     * high-quality filter, so a large image (for example 256&times;256) looks
     * sharp at any scale.
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
     * @param value the image, or {@code null}
     */
    public void setImage(Image value) {
        image.set(value);
    }

    /**
     * Icon node. Any JavaFX node; it replaces the image and the built-in icon.
     *
     * @return the graphic property
     */
    public ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * Gets the icon node.
     *
     * @return the node, or {@code null}
     */
    public Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the icon node.
     *
     * @param value the node, or {@code null}
     */
    public void setGraphic(Node value) {
        graphic.set(value);
    }

    /**
     * How long the notification stays before closing on its own. The
     * countdown pauses while the mouse is over the notification. Use
     * {@link Duration#INDEFINITE} to keep it open until it is closed; a
     * {@code null} value means {@link #DEFAULT_DURATION}.
     *
     * @return the duration property
     */
    public ObjectProperty<Duration> durationProperty() {
        return duration;
    }

    /**
     * Gets how long the notification stays.
     *
     * @return the duration, never {@code null}
     */
    public Duration getDuration() {
        Duration value = duration.get();
        return value == null ? DEFAULT_DURATION : value;
    }

    /**
     * Sets how long the notification stays.
     *
     * @param value the duration, {@link Duration#INDEFINITE}, or {@code null} for the default
     */
    public void setDuration(Duration value) {
        duration.set(value);
    }

    /**
     * Whether the notification has a close button.
     *
     * @return the closable property
     */
    public BooleanProperty closableProperty() {
        return closable;
    }

    /**
     * Tells whether the close button is shown.
     *
     * @return {@code true} if it is
     */
    public boolean isClosable() {
        return closable.get();
    }

    /**
     * Shows or hides the close button. Default {@code true}.
     *
     * @param value {@code true} to show it
     */
    public void setClosable(boolean value) {
        closable.set(value);
    }

    /**
     * The sound played when the notification appears: the URL of a WAV or
     * MP3 file (any format JavaFX Media plays), for example
     * {@code getClass().getResource("ding.wav").toExternalForm()} or
     * {@code Path.of("ding.mp3").toUri().toString()}. {@code null} (the
     * default) uses {@link TrayIcon#setNotificationSound the tray icon's
     * sound}, and failing that the built-in sound of the notification's
     * {@link #setType type} ({@link TrayIcon#setUseDefaultSounds}). A file
     * that cannot be played is logged and skipped.
     *
     * @return the sound property
     */
    public StringProperty soundProperty() {
        return sound;
    }

    /**
     * Gets the sound URL.
     *
     * @return the URL, or {@code null} for the tray icon's default
     */
    public String getSound() {
        return sound.get();
    }

    /**
     * Sets the sound URL.
     *
     * @param value the URL, or {@code null} for the tray icon's default
     */
    public void setSound(String value) {
        sound.set(value);
    }

    /**
     * Whether the notification appears without any sound, even when the
     * tray icon has a default sound.
     *
     * @return the silent property
     */
    public BooleanProperty silentProperty() {
        return silent;
    }

    /**
     * Tells whether the notification is silent.
     *
     * @return {@code true} if no sound plays
     */
    public boolean isSilent() {
        return silent.get();
    }

    /**
     * Makes the notification silent. Default {@code false}.
     *
     * @param value {@code true} for no sound
     */
    public void setSilent(boolean value) {
        silent.set(value);
    }

    /**
     * Handler for a click on the notification itself (anywhere except its
     * buttons). The notification closes after the handler runs.
     *
     * @return the property
     */
    public ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * Gets the click handler.
     *
     * @return the handler, or {@code null}
     */
    public EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * Sets the click handler.
     *
     * @param value the handler, or {@code null}
     */
    public void setOnAction(EventHandler<ActionEvent> value) {
        onAction.set(value);
    }

    /**
     * Handler called when the notification has been shown.
     *
     * @return the property
     */
    public ObjectProperty<EventHandler<Event>> onShownProperty() {
        return onShown;
    }

    /**
     * Gets the shown handler.
     *
     * @return the handler, or {@code null}
     */
    public EventHandler<Event> getOnShown() {
        return onShown.get();
    }

    /**
     * Sets the shown handler.
     *
     * @param value the handler, or {@code null}
     */
    public void setOnShown(EventHandler<Event> value) {
        onShown.set(value);
    }

    /**
     * Handler called when the notification has closed: timed out, closed by
     * the user, clicked, an action chosen, pushed out of a full stack, or
     * hidden by code.
     *
     * @return the property
     */
    public ObjectProperty<EventHandler<Event>> onHiddenProperty() {
        return onHidden;
    }

    /**
     * Gets the hidden handler.
     *
     * @return the handler, or {@code null}
     */
    public EventHandler<Event> getOnHidden() {
        return onHidden.get();
    }

    /**
     * Sets the hidden handler.
     *
     * @param value the handler, or {@code null}
     */
    public void setOnHidden(EventHandler<Event> value) {
        onHidden.set(value);
    }

    /**
     * Whether the notification is currently on screen.
     *
     * @return the read-only showing property
     */
    public ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Tells whether the notification is on screen.
     *
     * @return {@code true} while shown
     */
    public boolean isShowing() {
        return showing.get();
    }

    /**
     * Action buttons, shown in a row under the text. Each one gets the style
     * class {@code notification-action}, and choosing one closes the
     * notification after the button's own handler runs.
     *
     * @return the live, modifiable list of buttons
     */
    public ObservableList<Button> getActions() {
        return actions;
    }

    /**
     * Extra style classes for the notification's root node, which always has
     * {@code tray-notification}.
     *
     * @return the live, modifiable list of style classes
     */
    public ObservableList<String> getStyleClass() {
        return styleClass;
    }

    // ---- Actions -------------------------------------------------------------------------

    /**
     * Closes the notification with its exit animation. Does nothing if it is
     * not showing.
     *
     * @throws IllegalStateException if not on the JavaFX Application Thread
     */
    public void hide() {
        if (owner != null) {
            owner.hideNotification(this);
        }
    }

    // ---- Called by TrayIcon ---------------------------------------------------------------

    /**
     * The tray icon currently showing this notification.
     *
     * @return the owner, or {@code null} while hidden
     */
    TrayIcon owner() {
        return owner;
    }

    /**
     * Marks the notification as shown by a tray icon and fires {@code onShown}.
     *
     * @param tray the tray icon showing it
     */
    void markShown(TrayIcon tray) {
        owner = tray;
        showing.set(true);
        EventHandler<Event> handler = getOnShown();
        if (handler != null) {
            handler.handle(new Event(this, null, Event.ANY));
        }
    }

    /**
     * Marks the notification as hidden and fires {@code onHidden}.
     */
    void markHidden() {
        owner = null;
        showing.set(false);
        EventHandler<Event> handler = getOnHidden();
        if (handler != null) {
            handler.handle(new Event(this, null, Event.ANY));
        }
    }
}
