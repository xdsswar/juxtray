package xss.it.juxtray;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseButton;

/**
 * Something the mouse did to a {@link TrayIcon}.
 *
 * <p>This is a sealed hierarchy of records, made for pattern matching:</p>
 *
 * <pre>{@code
 * tray.setOnTrayEvent(event -> {
 *     switch (event) {
 *         case TrayIconEvent.Click c when c.state() == ButtonState.RELEASED -> ...
 *         case TrayIconEvent.DoubleClick d -> ...
 *         default -> { }
 *     }
 * });
 * }</pre>
 *
 * <p>All coordinates are JavaFX screen coordinates (logical pixels, the same
 * space as {@code Stage.setX}), so they can be used directly to position
 * windows. Events are always delivered on the JavaFX Application Thread.</p>
 */
public sealed interface TrayIconEvent {

    /**
     * The tray icon the event happened on.
     *
     * @return the source icon, never {@code null}
     */
    TrayIcon source();

    /**
     * Mouse cursor position when the event was dispatched.
     *
     * @return cursor position in JavaFX screen coordinates
     */
    Point2D position();

    /**
     * Area the icon occupies on screen, or {@link Rectangle2D#EMPTY} when the
     * shell cannot tell (for example, when the icon sits in the overflow
     * flyout and the flyout is closed).
     *
     * @return icon bounds in JavaFX screen coordinates
     */
    Rectangle2D bounds();

    /**
     * A mouse button was pressed or released over the icon.
     *
     * @param source   the icon
     * @param button   which button
     * @param state    pressed or released
     * @param position cursor position
     * @param bounds   icon bounds
     */
    record Click(TrayIcon source, MouseButton button, ButtonState state, Point2D position, Rectangle2D bounds)
            implements TrayIconEvent {
    }

    /**
     * A mouse button was double-clicked over the icon. The two individual
     * clicks are reported as {@link Click} events as well.
     *
     * @param source   the icon
     * @param button   which button
     * @param position cursor position
     * @param bounds   icon bounds
     */
    record DoubleClick(TrayIcon source, MouseButton button, Point2D position, Rectangle2D bounds)
            implements TrayIconEvent {
    }

    /**
     * The cursor entered the icon.
     *
     * @param source   the icon
     * @param position cursor position
     * @param bounds   icon bounds
     */
    record Enter(TrayIcon source, Point2D position, Rectangle2D bounds) implements TrayIconEvent {
    }

    /**
     * The cursor moved while over the icon.
     *
     * @param source   the icon
     * @param position cursor position
     * @param bounds   icon bounds
     */
    record Move(TrayIcon source, Point2D position, Rectangle2D bounds) implements TrayIconEvent {
    }

    /**
     * The cursor left the icon.
     *
     * @param source   the icon
     * @param position cursor position
     * @param bounds   icon bounds
     */
    record Exit(TrayIcon source, Point2D position, Rectangle2D bounds) implements TrayIconEvent {
    }
}
