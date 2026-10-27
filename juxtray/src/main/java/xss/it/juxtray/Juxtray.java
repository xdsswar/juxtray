package xss.it.juxtray;

import com.xss.it.juxtray.Os;

/**
 * Library-level entry point and capability queries.
 *
 * <p>This class is a static utility and cannot be instantiated.</p>
 */
public final class Juxtray {

    /** Utility class: no instances. */
    private Juxtray() {
        throw new AssertionError("No instances");
    }

    /**
     * Tells whether the current platform has a tray back end.
     *
     * <p>Only Windows is implemented at the moment; macOS and Linux will
     * follow. A {@code true} result means Juxtray has code for this OS. It does
     * not guarantee that a tray host (the taskbar) is running right now.</p>
     *
     * @return {@code true} when {@link TrayIcon} can be created on this OS
     */
    public static boolean isSupported() {
        return Os.CURRENT == Os.WINDOWS;
    }
}
