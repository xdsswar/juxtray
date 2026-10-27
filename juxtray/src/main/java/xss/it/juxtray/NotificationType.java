package xss.it.juxtray;

/**
 * The kind of a {@link TrayNotification}. It picks the default icon and is
 * exposed to CSS as a pseudo-class on the notification
 * ({@code .tray-notification:info}, {@code :success}, {@code :warning},
 * {@code :error}), so themes can colour each kind differently.
 */
public enum NotificationType {

    /**
     * A plain notification. It shows the tray icon's image as its icon and
     * sets no pseudo-class.
     */
    NONE,

    /** Information. Pseudo-class {@code :info}. */
    INFO,

    /** Something finished well. Pseudo-class {@code :success}. */
    SUCCESS,

    /** Something needs attention. Pseudo-class {@code :warning}. */
    WARNING,

    /** Something failed. Pseudo-class {@code :error}. */
    ERROR
}
