package xss.it.juxtray;

/**
 * How tray notifications appear and disappear. Stacked notifications always
 * glide to their new place when one above or below them closes.
 *
 * @see TrayIcon#setNotificationAnimation(NotificationAnimation)
 */
public enum NotificationAnimation {

    /** Slides in from the nearest screen edge while fading in; slides back out. */
    SLIDE,

    /** Fades in and out in place. */
    FADE,

    /** Grows from slightly smaller while fading in; shrinks while fading out. */
    ZOOM,

    /** Appears and disappears at once. */
    NONE
}
