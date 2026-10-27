package xss.it.juxtray.visualtest;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import xss.it.juxtray.TrayIcon;

/**
 * Reads a few Juxtray internals by reflection, for things the public API
 * deliberately does not offer.
 *
 * <p>The main one is where the icon is. The public API only reports icon
 * bounds with mouse events, but the test has to know where to point the
 * mouse <em>before</em> any event happened. So it asks the Windows back end
 * directly ({@code Shell_NotifyIconGetRect} through {@code Shell32}). The
 * build opens the internal packages to this module at run time; the library
 * itself exports nothing more.</p>
 *
 * <p>Field and method names here must follow the internals they read; if
 * one is renamed, this class fails loudly with a
 * {@link ReflectiveOperationException}.</p>
 */
final class TrayProbe {

    /** Utility class: no instances. */
    private TrayProbe() {
        throw new AssertionError("No instances");
    }

    /**
     * Where the shell shows the icon right now.
     *
     * <p>While the icon sits in the closed overflow flyout, Windows 11
     * returns the bounds of the flyout's chevron button instead. Call on the
     * JavaFX thread (it reads the screen scale).</p>
     *
     * @param tray the tray icon
     * @return the bounds in JavaFX screen coordinates (the tray is on the
     *         primary screen), or {@code null} when the shell does not know
     *         the icon (for example after {@code close()})
     * @throws ReflectiveOperationException if the internals changed
     */
    static Rectangle2D iconBounds(TrayIcon tray) throws ReflectiveOperationException {
        Object peer = field(tray, "peer");
        Object host = field(peer, "host");
        Method hwnd = host.getClass().getDeclaredMethod("hwnd");
        hwnd.setAccessible(true);
        int id = (int) field(peer, "id");

        Class<?> shell32 = Class.forName("com.xss.it.juxtray.win.Shell32");
        Method iconRect = shell32.getDeclaredMethod("iconRect", MemorySegment.class, int.class);
        iconRect.setAccessible(true);
        Object rect = iconRect.invoke(null, hwnd.invoke(host), id);
        if (rect == null) {
            return null;
        }
        // PhysicalRect is a record in device pixels: left, top, right, bottom.
        int left = (int) rect.getClass().getMethod("left").invoke(rect);
        int top = (int) rect.getClass().getMethod("top").invoke(rect);
        int right = (int) rect.getClass().getMethod("right").invoke(rect);
        int bottom = (int) rect.getClass().getMethod("bottom").invoke(rect);
        // On the primary screen device (0, 0) is JavaFX (0, 0), so dividing by its scale is enough.
        double scale = Screen.getPrimary().getOutputScaleX();
        return new Rectangle2D(left / scale, top / scale, (right - left) / scale, (bottom - top) / scale);
    }

    /**
     * The invisible stage that owns the tray menu.
     *
     * @param tray the tray icon
     * @return the stage
     * @throws ReflectiveOperationException if the internals changed
     */
    static Stage menuHostStage(TrayIcon tray) throws ReflectiveOperationException {
        return (Stage) field(field(tray, "menuHost"), "stage");
    }

    /**
     * The invisible stage that owns the notification popups.
     *
     * @param tray the tray icon
     * @return the stage
     * @throws ReflectiveOperationException if the internals changed
     */
    static Stage notificationHostStage(TrayIcon tray) throws ReflectiveOperationException {
        return (Stage) field(field(tray, "notificationHost"), "stage");
    }

    /**
     * Reads a private field.
     *
     * @param target the object
     * @param name   the field name
     * @return the value
     * @throws ReflectiveOperationException if there is no such field
     */
    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
