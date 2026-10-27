package com.xss.it.juxtray;

import com.xss.it.juxtray.win.WinTray;
import xss.it.juxtray.TrayException;

/**
 * Picks the right {@link NativeTray} implementation for the running OS.
 */
public final class NativeTrays {

    /** Utility class: no instances. */
    private NativeTrays() {
        throw new AssertionError("No instances");
    }

    /**
     * Creates a native tray icon and adds it to the system tray.
     *
     * @param listener receiver for raw mouse events (called on a native thread)
     * @return the new native tray icon
     * @throws TrayException if the OS is not supported or refuses the icon
     */
    public static NativeTray create(NativeTrayListener listener) {
        return switch (Os.CURRENT) {
            case WINDOWS -> WinTray.create(listener);
            case MACOS, LINUX, UNSUPPORTED ->
                    throw new TrayException("Tray icons are not supported on " + Os.CURRENT + " yet");
        };
    }
}
