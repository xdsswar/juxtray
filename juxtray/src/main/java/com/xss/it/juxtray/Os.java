package com.xss.it.juxtray;

import java.util.Locale;

/**
 * The desktop operating system families Juxtray knows how to drive.
 *
 * <p>Detection is done once, from the {@code os.name} system property, and
 * cached in {@link #CURRENT}. Each family maps to exactly one tray back end.</p>
 */
public enum Os {

    /** Windows: {@code Shell_NotifyIconW} through FFM. */
    WINDOWS,

    /** macOS: {@code NSStatusItem} through the Objective-C runtime via FFM. */
    MACOS,

    /** Linux and BSDs: StatusNotifierItem spoken over D-Bus in pure Java. */
    LINUX,

    /** Anything else: tray icons are not supported. */
    UNSUPPORTED;

    /** The operating system this JVM runs on, resolved once at class load. */
    public static final Os CURRENT = detect();

    /**
     * Maps {@code os.name} to a family.
     *
     * @return the detected family, never {@code null}
     */
    private static Os detect() {
        // Lower-case with a fixed locale so e.g. Turkish 'I' does not break matching.
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return WINDOWS;
        }
        if (name.startsWith("mac")) {
            return MACOS;
        }
        if (name.contains("linux") || name.contains("bsd")) {
            return LINUX;
        }
        return UNSUPPORTED;
    }
}
