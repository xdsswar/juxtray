package com.xss.it.juxtray.win;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import com.xss.it.juxtray.PhysicalRect;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * FFM bindings for the notification-area functions of {@code shell32.dll}.
 */
final class Shell32 {

    /** {@code shell32.dll}. */
    private static final SymbolLookup SHELL32 = Win32.library("shell32");

    /** {@code BOOL Shell_NotifyIconW(DWORD dwMessage, PNOTIFYICONDATAW lpData);} */
    private static final MethodHandle SHELL_NOTIFY_ICON = Win32.bind(SHELL32, "Shell_NotifyIconW",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));

    /**
     * {@code HRESULT Shell_NotifyIconGetRect(const NOTIFYICONIDENTIFIER *identifier, RECT *iconLocation);}
     */
    private static final MethodHandle SHELL_NOTIFY_ICON_GET_RECT = Win32.bind(SHELL32, "Shell_NotifyIconGetRect",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    /** Utility class: no instances. */
    private Shell32() {
        throw new AssertionError("No instances");
    }

    /**
     * Adds, modifies or deletes a notification-area icon.
     *
     * @param message {@code NIM_ADD}, {@code NIM_MODIFY} or {@code NIM_DELETE}
     * @param data    a filled {@code NOTIFYICONDATAW}
     * @return {@code true} on success
     */
    static boolean notifyIcon(int message, MemorySegment data) {
        try {
            return (int) SHELL_NOTIFY_ICON.invokeExact(message, data) != 0;
        } catch (Throwable t) {
            throw Win32.failure("Shell_NotifyIconW", t);
        }
    }

    /**
     * Where an icon is on screen.
     *
     * @param hwnd window that owns the icon
     * @param id   the icon's {@code uID}
     * @return bounds in device pixels, or {@code null} when the shell does
     *         not know the icon (removed, or no taskbar yet). While the icon
     *         sits in the closed overflow flyout, Windows 11 does not fail
     *         but returns the bounds of the flyout's chevron button.
     */
    static PhysicalRect iconRect(MemorySegment hwnd, int id) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment identifier = arena.allocate(WinStructs.NOTIFYICONIDENTIFIER);
            identifier.set(JAVA_INT, WinStructs.NII_CB_SIZE, (int) WinStructs.NOTIFYICONIDENTIFIER.byteSize());
            identifier.set(ADDRESS, WinStructs.NII_HWND, hwnd);
            identifier.set(JAVA_INT, WinStructs.NII_ID, id);

            MemorySegment rect = arena.allocate(WinStructs.RECT);
            // HRESULT: S_OK (0) on success, any other value is a failure.
            if ((int) SHELL_NOTIFY_ICON_GET_RECT.invokeExact(identifier, rect) != 0) {
                return null;
            }
            return new PhysicalRect(
                    rect.get(JAVA_INT, WinStructs.RECT_LEFT),
                    rect.get(JAVA_INT, WinStructs.RECT_TOP),
                    rect.get(JAVA_INT, WinStructs.RECT_RIGHT),
                    rect.get(JAVA_INT, WinStructs.RECT_BOTTOM));
        } catch (Throwable t) {
            throw Win32.failure("Shell_NotifyIconGetRect", t);
        }
    }
}
