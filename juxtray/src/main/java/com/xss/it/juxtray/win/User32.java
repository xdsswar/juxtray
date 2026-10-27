package com.xss.it.juxtray.win;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import com.xss.it.juxtray.PhysicalPoint;
import com.xss.it.juxtray.PhysicalRect;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * FFM bindings for the {@code user32.dll} functions Juxtray needs: window
 * class/window creation, the message loop, timers, icons and a few helpers.
 *
 * <p>Each native function has one {@link MethodHandle} constant with the C
 * signature in its Javadoc, plus a thin static wrapper with Java-friendly
 * parameter types. The wrappers call {@code invokeExact}, so argument types
 * must match the {@link FunctionDescriptor} exactly. They also turn the
 * checked {@link Throwable} of method handles into an unchecked exception.</p>
 */
final class User32 {

    /** {@code user32.dll}. */
    private static final SymbolLookup USER32 = Win32.library("user32");

    /** {@code ATOM RegisterClassExW(const WNDCLASSEXW *lpwcx);} with last-error capture. */
    private static final MethodHandle REGISTER_CLASS_EX = Win32.bind(USER32, "RegisterClassExW",
            FunctionDescriptor.of(JAVA_SHORT, ADDRESS), Win32.CAPTURE_LAST_ERROR);

    /**
     * {@code HWND CreateWindowExW(DWORD dwExStyle, LPCWSTR lpClassName, LPCWSTR lpWindowName,
     * DWORD dwStyle, int X, int Y, int nWidth, int nHeight, HWND hWndParent, HMENU hMenu,
     * HINSTANCE hInstance, LPVOID lpParam);} with last-error capture.
     */
    private static final MethodHandle CREATE_WINDOW_EX = Win32.bind(USER32, "CreateWindowExW",
            FunctionDescriptor.of(ADDRESS,
                    JAVA_INT, ADDRESS, ADDRESS, JAVA_INT,
                    JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            Win32.CAPTURE_LAST_ERROR);

    /** {@code LRESULT DefWindowProcW(HWND hWnd, UINT Msg, WPARAM wParam, LPARAM lParam);} */
    private static final MethodHandle DEF_WINDOW_PROC = Win32.bind(USER32, "DefWindowProcW",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));

    /** {@code BOOL GetMessageW(LPMSG lpMsg, HWND hWnd, UINT wMsgFilterMin, UINT wMsgFilterMax);} */
    private static final MethodHandle GET_MESSAGE = Win32.bind(USER32, "GetMessageW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

    /** {@code BOOL TranslateMessage(const MSG *lpMsg);} */
    private static final MethodHandle TRANSLATE_MESSAGE = Win32.bind(USER32, "TranslateMessage",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** {@code LRESULT DispatchMessageW(const MSG *lpMsg);} */
    private static final MethodHandle DISPATCH_MESSAGE = Win32.bind(USER32, "DispatchMessageW",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS));

    /** {@code BOOL PostMessageW(HWND hWnd, UINT Msg, WPARAM wParam, LPARAM lParam);} */
    private static final MethodHandle POST_MESSAGE = Win32.bind(USER32, "PostMessageW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));

    /** {@code UINT RegisterWindowMessageW(LPCWSTR lpString);} */
    private static final MethodHandle REGISTER_WINDOW_MESSAGE = Win32.bind(USER32, "RegisterWindowMessageW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /**
     * {@code BOOL ChangeWindowMessageFilterEx(HWND hwnd, UINT message, DWORD action,
     * PCHANGEFILTERSTRUCT pChangeFilterStruct);}
     */
    private static final MethodHandle CHANGE_WINDOW_MESSAGE_FILTER_EX = Win32.bind(USER32,
            "ChangeWindowMessageFilterEx",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

    /** {@code BOOL GetCursorPos(LPPOINT lpPoint);} */
    private static final MethodHandle GET_CURSOR_POS = Win32.bind(USER32, "GetCursorPos",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** {@code BOOL SetForegroundWindow(HWND hWnd);} */
    private static final MethodHandle SET_FOREGROUND_WINDOW = Win32.bind(USER32, "SetForegroundWindow",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** {@code HWND GetForegroundWindow(void);} */
    private static final MethodHandle GET_FOREGROUND_WINDOW = Win32.bind(USER32, "GetForegroundWindow",
            FunctionDescriptor.of(ADDRESS));

    /** {@code HWND FindWindowW(LPCWSTR lpClassName, LPCWSTR lpWindowName);} */
    private static final MethodHandle FIND_WINDOW = Win32.bind(USER32, "FindWindowW",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    /** {@code UINT_PTR SetTimer(HWND hWnd, UINT_PTR nIDEvent, UINT uElapse, TIMERPROC lpTimerFunc);} */
    private static final MethodHandle SET_TIMER = Win32.bind(USER32, "SetTimer",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS));

    /** {@code BOOL KillTimer(HWND hWnd, UINT_PTR uIDEvent);} */
    private static final MethodHandle KILL_TIMER = Win32.bind(USER32, "KillTimer",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));

    /**
     * {@code HICON CreateIcon(HINSTANCE hInstance, int nWidth, int nHeight, BYTE cPlanes,
     * BYTE cBitsPixel, const BYTE *lpbANDbits, const BYTE *lpbXORbits);} with last-error capture.
     */
    private static final MethodHandle CREATE_ICON = Win32.bind(USER32, "CreateIcon",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_BYTE, JAVA_BYTE, ADDRESS, ADDRESS),
            Win32.CAPTURE_LAST_ERROR);

    /** {@code BOOL DestroyIcon(HICON hIcon);} */
    private static final MethodHandle DESTROY_ICON = Win32.bind(USER32, "DestroyIcon",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** {@code HMONITOR MonitorFromRect(LPCRECT lprc, DWORD dwFlags);} */
    private static final MethodHandle MONITOR_FROM_RECT = Win32.bind(USER32, "MonitorFromRect",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

    /** {@code UINT GetDpiForSystem(void);} (Windows 10 1607+) */
    private static final MethodHandle GET_DPI_FOR_SYSTEM = Win32.bind(USER32, "GetDpiForSystem",
            FunctionDescriptor.of(JAVA_INT));

    /** {@code int GetSystemMetricsForDpi(int nIndex, UINT dpi);} (Windows 10 1607+) */
    private static final MethodHandle GET_SYSTEM_METRICS_FOR_DPI = Win32.bind(USER32, "GetSystemMetricsForDpi",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));

    /** {@code DPI_AWARENESS_CONTEXT SetThreadDpiAwarenessContext(DPI_AWARENESS_CONTEXT dpiContext);} */
    private static final MethodHandle SET_THREAD_DPI_AWARENESS_CONTEXT = Win32.bind(USER32,
            "SetThreadDpiAwarenessContext", FunctionDescriptor.of(ADDRESS, ADDRESS));

    /** Utility class: no instances. */
    private User32() {
        throw new AssertionError("No instances");
    }

    /**
     * Registers a window class. A class that is already registered in this
     * process is not an error: it simply gets reused.
     *
     * @param windowClass a filled {@code WNDCLASSEXW}
     */
    static void registerClassEx(MemorySegment windowClass) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment state = Win32.captureState(arena);
            short atom = (short) REGISTER_CLASS_EX.invokeExact(state, windowClass);
            int error = Win32.lastError(state);
            if (atom == 0 && error != Win32.ERROR_CLASS_ALREADY_EXISTS) {
                throw Win32.failure("RegisterClassExW", error);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw Win32.failure("RegisterClassExW", t);
        }
    }

    /**
     * Creates a window ({@code CreateWindowExW}).
     *
     * @param exStyle   {@code WS_EX_*} flags
     * @param className registered class name ({@code LPCWSTR})
     * @param style     {@code WS_*} flags
     * @param instance  module handle that registered the class
     * @return the new window handle
     */
    static MemorySegment createWindowEx(int exStyle, MemorySegment className, int style, MemorySegment instance) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment state = Win32.captureState(arena);
            MemorySegment hwnd = (MemorySegment) CREATE_WINDOW_EX.invokeExact(state,
                    exStyle, className, MemorySegment.NULL, style,
                    Win32.CW_USEDEFAULT, 0, Win32.CW_USEDEFAULT, 0,
                    MemorySegment.NULL, MemorySegment.NULL, instance, MemorySegment.NULL);
            if (Win32.isNull(hwnd)) {
                throw Win32.failure("CreateWindowExW", Win32.lastError(state));
            }
            return hwnd;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw Win32.failure("CreateWindowExW", t);
        }
    }

    /**
     * Default processing for any message we do not handle ourselves.
     *
     * @param hwnd    target window
     * @param message message id
     * @param wParam  first message parameter
     * @param lParam  second message parameter
     * @return the message result
     */
    static long defWindowProc(MemorySegment hwnd, int message, long wParam, long lParam) {
        try {
            return (long) DEF_WINDOW_PROC.invokeExact(hwnd, message, wParam, lParam);
        } catch (Throwable t) {
            throw Win32.failure("DefWindowProcW", t);
        }
    }

    /**
     * Blocks until a message arrives for the calling thread.
     *
     * @param msg buffer laid out as {@code MSG}
     * @return {@code > 0} for a message, {@code 0} for {@code WM_QUIT}, {@code -1} on error
     */
    static int getMessage(MemorySegment msg) {
        try {
            return (int) GET_MESSAGE.invokeExact(msg, MemorySegment.NULL, 0, 0);
        } catch (Throwable t) {
            throw Win32.failure("GetMessageW", t);
        }
    }

    /**
     * Translates virtual-key messages into character messages.
     *
     * @param msg message from {@link #getMessage}
     */
    static void translateMessage(MemorySegment msg) {
        try {
            int ignored = (int) TRANSLATE_MESSAGE.invokeExact(msg);
        } catch (Throwable t) {
            throw Win32.failure("TranslateMessage", t);
        }
    }

    /**
     * Sends a message to its window procedure.
     *
     * @param msg message from {@link #getMessage}
     */
    static void dispatchMessage(MemorySegment msg) {
        try {
            long ignored = (long) DISPATCH_MESSAGE.invokeExact(msg);
        } catch (Throwable t) {
            throw Win32.failure("DispatchMessageW", t);
        }
    }

    /**
     * Queues a message for a window, without waiting. Safe from any thread.
     *
     * @param hwnd    target window
     * @param message message id
     * @param wParam  first parameter
     * @param lParam  second parameter
     * @return {@code true} if the message was queued
     */
    static boolean postMessage(MemorySegment hwnd, int message, long wParam, long lParam) {
        try {
            return (int) POST_MESSAGE.invokeExact(hwnd, message, wParam, lParam) != 0;
        } catch (Throwable t) {
            throw Win32.failure("PostMessageW", t);
        }
    }

    /**
     * Gets the system-wide id of a named message.
     *
     * @param name message name, for example {@code "TaskbarCreated"}
     * @return the message id, or {@code 0} on failure
     */
    static int registerWindowMessage(String name) {
        try (Arena arena = Arena.ofConfined()) {
            return (int) REGISTER_WINDOW_MESSAGE.invokeExact(Win32.wide(arena, name));
        } catch (Throwable t) {
            throw Win32.failure("RegisterWindowMessageW", t);
        }
    }

    /**
     * Lets a message through User Interface Privilege Isolation. This is
     * needed when our process runs elevated and Explorer does not.
     *
     * @param hwnd    window that should receive the message
     * @param message the message id
     */
    static void allowMessage(MemorySegment hwnd, int message) {
        try {
            int ignored = (int) CHANGE_WINDOW_MESSAGE_FILTER_EX.invokeExact(
                    hwnd, message, Win32.MSGFLT_ALLOW, MemorySegment.NULL);
        } catch (Throwable t) {
            throw Win32.failure("ChangeWindowMessageFilterEx", t);
        }
    }

    /**
     * Current cursor position in device pixels (for the calling thread's DPI awareness).
     *
     * @return the position, or {@code null} if the call failed (for example on a secure desktop)
     */
    static PhysicalPoint cursorPosition() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment point = arena.allocate(WinStructs.POINT);
            if ((int) GET_CURSOR_POS.invokeExact(point) == 0) {
                return null;
            }
            return new PhysicalPoint(
                    point.get(JAVA_INT, WinStructs.POINT_X),
                    point.get(JAVA_INT, WinStructs.POINT_Y));
        } catch (Throwable t) {
            throw Win32.failure("GetCursorPos", t);
        }
    }

    /**
     * Finds a top-level window by its exact title and makes it the foreground window.
     *
     * @param title window title to search for
     * @return {@code true} if the window was found and activated
     */
    static boolean activateWindow(String title) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hwnd = (MemorySegment) FIND_WINDOW.invokeExact(MemorySegment.NULL, Win32.wide(arena, title));
            if (Win32.isNull(hwnd)) {
                return false;
            }
            return (int) SET_FOREGROUND_WINDOW.invokeExact(hwnd) != 0;
        } catch (Throwable t) {
            throw Win32.failure("SetForegroundWindow", t);
        }
    }

    /**
     * The window the user is currently working in.
     *
     * @return its {@code HWND}, or {@code NULL} when no window has the
     *         foreground (for example while focus is changing)
     */
    static MemorySegment foregroundWindow() {
        try {
            return (MemorySegment) GET_FOREGROUND_WINDOW.invokeExact();
        } catch (Throwable t) {
            throw Win32.failure("GetForegroundWindow", t);
        }
    }

    /**
     * Makes a window the foreground window.
     *
     * @param hwnd the window
     * @return {@code true} if Windows allowed it
     */
    static boolean setForegroundWindow(MemorySegment hwnd) {
        try {
            return (int) SET_FOREGROUND_WINDOW.invokeExact(hwnd) != 0;
        } catch (Throwable t) {
            throw Win32.failure("SetForegroundWindow", t);
        }
    }

    /**
     * Creates or restarts a timer that posts {@code WM_TIMER} to a window.
     *
     * @param hwnd    window that receives {@code WM_TIMER}
     * @param id      timer id (arrives as {@code wParam}); must be non-zero
     * @param millis  interval in milliseconds
     */
    static void setTimer(MemorySegment hwnd, long id, int millis) {
        try {
            long ignored = (long) SET_TIMER.invokeExact(hwnd, id, millis, MemorySegment.NULL);
        } catch (Throwable t) {
            throw Win32.failure("SetTimer", t);
        }
    }

    /**
     * Stops a timer started with {@link #setTimer}. Stopping a timer that is
     * not running is harmless.
     *
     * @param hwnd window the timer belongs to
     * @param id   timer id
     */
    static void killTimer(MemorySegment hwnd, long id) {
        try {
            int ignored = (int) KILL_TIMER.invokeExact(hwnd, id);
        } catch (Throwable t) {
            throw Win32.failure("KillTimer", t);
        }
    }

    /**
     * Creates an icon from raw bitmaps ({@code CreateIcon}).
     *
     * @param width   width in pixels
     * @param height  height in pixels
     * @param andMask 1 bpp AND mask, rows padded to 16 bits
     * @param xorBits 32 bpp BGRA colour bits, top-down
     * @return the new {@code HICON}; free it with {@link #destroyIcon}
     */
    static MemorySegment createIcon(int width, int height, MemorySegment andMask, MemorySegment xorBits) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment state = Win32.captureState(arena);
            MemorySegment icon = (MemorySegment) CREATE_ICON.invokeExact(state,
                    MemorySegment.NULL, width, height, (byte) 1, (byte) 32, andMask, xorBits);
            if (Win32.isNull(icon)) {
                throw Win32.failure("CreateIcon", Win32.lastError(state));
            }
            return icon;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw Win32.failure("CreateIcon", t);
        }
    }

    /**
     * Frees an icon created by {@link #createIcon}. A {@code NULL} handle is ignored.
     *
     * @param icon the icon handle
     */
    static void destroyIcon(MemorySegment icon) {
        if (Win32.isNull(icon)) {
            return;
        }
        try {
            int ignored = (int) DESTROY_ICON.invokeExact(icon);
        } catch (Throwable t) {
            throw Win32.failure("DestroyIcon", t);
        }
    }

    /**
     * The monitor that contains (most of) a rectangle.
     *
     * @param rect rectangle in device pixels, or {@code null} for the primary monitor
     * @return the {@code HMONITOR}; never {@code NULL} thanks to {@code MONITOR_DEFAULTTOPRIMARY}
     */
    static MemorySegment monitorFromRect(PhysicalRect rect) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment area = arena.allocate(WinStructs.RECT);
            if (rect != null) {
                area.set(JAVA_INT, WinStructs.RECT_LEFT, rect.left());
                area.set(JAVA_INT, WinStructs.RECT_TOP, rect.top());
                area.set(JAVA_INT, WinStructs.RECT_RIGHT, rect.right());
                area.set(JAVA_INT, WinStructs.RECT_BOTTOM, rect.bottom());
            }
            // An empty rect at (0,0) falls back to the primary monitor via the flag.
            return (MemorySegment) MONITOR_FROM_RECT.invokeExact(area, Win32.MONITOR_DEFAULTTOPRIMARY);
        } catch (Throwable t) {
            throw Win32.failure("MonitorFromRect", t);
        }
    }

    /**
     * The system DPI (the primary monitor's DPI when the user signed in).
     *
     * @return the DPI, for example 96 or 144
     */
    static int systemDpi() {
        try {
            return (int) GET_DPI_FOR_SYSTEM.invokeExact();
        } catch (Throwable t) {
            throw Win32.failure("GetDpiForSystem", t);
        }
    }

    /**
     * Recommended tray (small) icon size at a given DPI.
     *
     * @param dpi the DPI of the monitor showing the icon
     * @return edge length in device pixels, for example 16 at 96 DPI or 24 at 144 DPI
     */
    static int smallIconSize(int dpi) {
        try {
            int size = (int) GET_SYSTEM_METRICS_FOR_DPI.invokeExact(Win32.SM_CXSMICON, dpi);
            // 0 means failure; fall back to the standard 16 px scaled by DPI.
            return size > 0 ? size : Math.max(16, Math.round(16 * dpi / 96f));
        } catch (Throwable t) {
            throw Win32.failure("GetSystemMetricsForDpi", t);
        }
    }

    /**
     * Switches the calling thread to per-monitor-v2 DPI awareness, so all
     * coordinates it sees are real device pixels.
     */
    static void usePerMonitorDpiAwareness() {
        try {
            MemorySegment ignored = (MemorySegment) SET_THREAD_DPI_AWARENESS_CONTEXT.invokeExact(
                    Win32.DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
        } catch (Throwable t) {
            throw Win32.failure("SetThreadDpiAwarenessContext", t);
        }
    }
}
