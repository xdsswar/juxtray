package com.xss.it.juxtray.win;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import xss.it.juxtray.TrayException;

/**
 * Shared plumbing for every Win32 binding: the native linker, library
 * lookup, {@code GetLastError} capture, wide-string helpers and the Win32
 * constants we use.
 *
 * <p>Constant names match the Windows SDK headers exactly, so they can be
 * searched for in Microsoft's documentation.</p>
 */
final class Win32 {

    /** The platform C linker (on Windows x64: the Microsoft x64 calling convention). */
    static final Linker LINKER = Linker.nativeLinker();

    /**
     * Linker option asking the JVM to save {@code GetLastError()} right after
     * the call. This is needed because the JVM itself may make Win32 calls
     * that overwrite the thread's last-error value before Java code could
     * read it.
     */
    static final Linker.Option CAPTURE_LAST_ERROR = Linker.Option.captureCallState("GetLastError");

    /** Layout of the buffer that receives captured call state. */
    static final StructLayout CAPTURE_STATE = Linker.Option.captureStateLayout();

    /** Reads the captured {@code GetLastError} value out of a capture buffer. */
    private static final VarHandle LAST_ERROR =
            CAPTURE_STATE.varHandle(PathElement.groupElement("GetLastError"));

    // ---- Window messages (WinUser.h) -------------------------------------------------

    /** Sent when a timer created with {@code SetTimer} fires. */
    static final int WM_TIMER = 0x0113;
    /** Mouse moved. */
    static final int WM_MOUSEMOVE = 0x0200;
    /** Left button down. */
    static final int WM_LBUTTONDOWN = 0x0201;
    /** Left button up. */
    static final int WM_LBUTTONUP = 0x0202;
    /** Left button double-click. */
    static final int WM_LBUTTONDBLCLK = 0x0203;
    /** Right button down. */
    static final int WM_RBUTTONDOWN = 0x0204;
    /** Right button up. */
    static final int WM_RBUTTONUP = 0x0205;
    /** Right button double-click. */
    static final int WM_RBUTTONDBLCLK = 0x0206;
    /** Middle button down. */
    static final int WM_MBUTTONDOWN = 0x0207;
    /** Middle button up. */
    static final int WM_MBUTTONUP = 0x0208;
    /** Middle button double-click. */
    static final int WM_MBUTTONDBLCLK = 0x0209;
    /** Broadcast when a system-wide setting changes (includes display scaling changes). */
    static final int WM_SETTINGCHANGE = 0x001A;
    /** Broadcast when the display resolution or layout changes. */
    static final int WM_DISPLAYCHANGE = 0x007E;
    /** Sent when the DPI of the monitor hosting a window changes. */
    static final int WM_DPICHANGED = 0x02E0;
    /** First message id free for private use by an application. */
    static final int WM_APP = 0x8000;

    // ---- Window styles ---------------------------------------------------------------

    /** Plain overlapped window; it is never shown, so the exact style hardly matters. */
    static final int WS_OVERLAPPED = 0x00000000;
    /** Tool window: never appears in the taskbar or Alt+Tab. */
    static final int WS_EX_TOOLWINDOW = 0x00000080;
    /** Mouse input passes through the window. */
    static final int WS_EX_TRANSPARENT = 0x00000020;
    /** Layered window (needed together with transparency). */
    static final int WS_EX_LAYERED = 0x00080000;
    /** The window never becomes the active window on its own. */
    static final int WS_EX_NOACTIVATE = 0x08000000;
    /** "Let Windows pick" for {@code CreateWindowExW} position and size. */
    static final int CW_USEDEFAULT = 0x80000000;

    // ---- Misc --------------------------------------------------------------------------

    /** {@code ChangeWindowMessageFilterEx} action: let the message through UIPI. */
    static final int MSGFLT_ALLOW = 1;
    /** {@code GetSystemMetrics} index: recommended small icon width. */
    static final int SM_CXSMICON = 49;
    /** {@code MonitorFromRect} flag: use the primary monitor when the rect touches none. */
    static final int MONITOR_DEFAULTTOPRIMARY = 0x00000001;
    /** The reference DPI of 100 % scaling. */
    static final int USER_DEFAULT_SCREEN_DPI = 96;
    /** {@code RegisterClassExW} error when the class already exists in this process. */
    static final int ERROR_CLASS_ALREADY_EXISTS = 1410;
    /**
     * {@code DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2}, a pseudo-handle whose
     * value is {@code -4}. It makes coordinates on our thread true device
     * pixels on every monitor, the same mode JavaFX uses.
     */
    static final MemorySegment DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = MemorySegment.ofAddress(-4L);

    // ---- Shell_NotifyIconW (shellapi.h) ----------------------------------------------

    /** Add an icon to the notification area. */
    static final int NIM_ADD = 0x00000000;
    /** Modify an existing icon. */
    static final int NIM_MODIFY = 0x00000001;
    /** Remove an icon. */
    static final int NIM_DELETE = 0x00000002;
    /** {@code uCallbackMessage} is valid. */
    static final int NIF_MESSAGE = 0x00000001;
    /** {@code hIcon} is valid. */
    static final int NIF_ICON = 0x00000002;
    /** {@code szTip} is valid. */
    static final int NIF_TIP = 0x00000004;
    /** {@code dwState} / {@code dwStateMask} are valid. */
    static final int NIF_STATE = 0x00000008;
    /** Icon state flag: hidden. */
    static final int NIS_HIDDEN = 0x00000001;

    /** Utility class: no instances. */
    private Win32() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads a system DLL for the lifetime of the process.
     *
     * <p>The global arena is right here: these are core OS libraries that stay
     * loaded anyway, and their handles are shared by every tray icon.</p>
     *
     * @param name DLL name without extension, for example {@code "user32"}
     * @return a lookup for the DLL's exported symbols
     */
    // Restricted by design: this is one of the three places Juxtray crosses into native code.
    @SuppressWarnings("restricted")
    static SymbolLookup library(String name) {
        return SymbolLookup.libraryLookup(name, Arena.global());
    }

    /**
     * Creates a downcall handle for one exported function.
     *
     * @param library    the DLL to search
     * @param function   exported function name, exactly as in the SDK
     * @param descriptor C signature of the function
     * @param options    extra linker options, such as {@link #CAPTURE_LAST_ERROR}
     * @return a method handle that calls the native function
     */
    // Restricted by design: every downcall in Juxtray is created here.
    @SuppressWarnings("restricted")
    static MethodHandle bind(SymbolLookup library, String function, FunctionDescriptor descriptor,
                             Linker.Option... options) {
        return LINKER.downcallHandle(library.findOrThrow(function), descriptor, options);
    }

    /**
     * Allocates a zeroed call-state buffer for a {@link #CAPTURE_LAST_ERROR} call.
     *
     * @param arena arena that owns the buffer
     * @return the buffer, passed as the first argument of the downcall
     */
    static MemorySegment captureState(Arena arena) {
        return arena.allocate(CAPTURE_STATE);
    }

    /**
     * Reads the {@code GetLastError} value captured by a downcall.
     *
     * @param state buffer passed to a {@link #CAPTURE_LAST_ERROR} downcall
     * @return the Win32 error code
     */
    static int lastError(MemorySegment state) {
        return (int) LAST_ERROR.get(state, 0L);
    }

    /**
     * Encodes a Java string as a NUL-terminated UTF-16LE {@code LPCWSTR}.
     *
     * @param arena arena that owns the native copy
     * @param text  the string to encode
     * @return pointer to the wide string
     */
    static MemorySegment wide(Arena arena, String text) {
        return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
    }

    /**
     * Tells whether a handle or pointer returned by Win32 is {@code NULL}.
     *
     * @param handle the returned segment
     * @return {@code true} for a null pointer
     */
    static boolean isNull(MemorySegment handle) {
        return handle == null || handle.address() == 0L;
    }

    /**
     * Builds the exception for a Win32 call that reported failure.
     *
     * @param function name of the failing function
     * @param error    {@code GetLastError} value
     * @return the exception to throw
     */
    static TrayException failure(String function, int error) {
        return new TrayException(function + " failed (Win32 error " + error + ")");
    }

    /**
     * Wraps a {@link Throwable} thrown by {@code MethodHandle.invokeExact}.
     *
     * <p>Downcalls only throw for programming errors (bad signature, closed
     * arena), so this always means a bug in Juxtray, never a user error.</p>
     *
     * @param function name of the native function being called
     * @param cause    what was thrown
     * @return the exception to throw
     */
    static TrayException failure(String function, Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        return new TrayException("Native call " + function + " failed", cause);
    }
}
