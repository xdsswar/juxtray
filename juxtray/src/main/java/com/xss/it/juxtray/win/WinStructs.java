package com.xss.it.juxtray.win;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;

/**
 * Memory layouts of the Win32 structs we pass to native code (64-bit Windows).
 *
 * <p>Each layout spells out the C struct field by field, including the
 * padding the C compiler would insert, so {@code byteSize()} matches
 * {@code sizeof} in C. {@code structLayout} rejects misaligned members, so a
 * missing padding entry fails fast at class-load time. Field offsets are
 * computed from the layout (never hard-coded) and exposed as constants.</p>
 *
 * <p>Pointer-sized C types ({@code HWND}, {@code HICON}, {@code LPCWSTR},
 * {@code WNDPROC} ...) map to {@code ADDRESS}. {@code WPARAM}/{@code LPARAM}
 * map to {@code JAVA_LONG}. {@code UINT}/{@code DWORD}/{@code LONG} map to
 * {@code JAVA_INT}. {@code WCHAR} maps to {@code JAVA_CHAR}.</p>
 */
final class WinStructs {

    /**
     * {@code POINT}: a point in device pixels.
     * <pre>{@code
     * typedef struct tagPOINT { LONG x; LONG y; } POINT;   // 8 bytes
     * }</pre>
     */
    static final StructLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    ).withName("POINT");

    /**
     * {@code RECT}: a rectangle in device pixels; right/bottom are exclusive.
     * <pre>{@code
     * typedef struct tagRECT { LONG left; LONG top; LONG right; LONG bottom; } RECT;   // 16 bytes
     * }</pre>
     */
    static final StructLayout RECT = MemoryLayout.structLayout(
            JAVA_INT.withName("left"),
            JAVA_INT.withName("top"),
            JAVA_INT.withName("right"),
            JAVA_INT.withName("bottom")
    ).withName("RECT");

    /**
     * {@code GUID}: 128-bit identifier, 4-byte aligned.
     * <pre>{@code
     * typedef struct _GUID { unsigned long Data1; unsigned short Data2; unsigned short Data3; unsigned char Data4[8]; } GUID;
     * }</pre>
     */
    static final StructLayout GUID = MemoryLayout.structLayout(
            JAVA_INT.withName("Data1"),
            JAVA_SHORT.withName("Data2"),
            JAVA_SHORT.withName("Data3"),
            MemoryLayout.sequenceLayout(8, JAVA_BYTE).withName("Data4")
    ).withName("GUID");

    /**
     * {@code WNDCLASSEXW}: describes the window class of our hidden window.
     * <pre>{@code
     * typedef struct tagWNDCLASSEXW {
     *   UINT      cbSize;          //  0
     *   UINT      style;           //  4
     *   WNDPROC   lpfnWndProc;     //  8
     *   int       cbClsExtra;      // 16
     *   int       cbWndExtra;      // 20
     *   HINSTANCE hInstance;       // 24
     *   HICON     hIcon;           // 32
     *   HCURSOR   hCursor;         // 40
     *   HBRUSH    hbrBackground;   // 48
     *   LPCWSTR   lpszMenuName;    // 56
     *   LPCWSTR   lpszClassName;   // 64
     *   HICON     hIconSm;         // 72
     * } WNDCLASSEXW;               // 80 bytes
     * }</pre>
     */
    static final StructLayout WNDCLASSEXW = MemoryLayout.structLayout(
            JAVA_INT.withName("cbSize"),
            JAVA_INT.withName("style"),
            ADDRESS.withName("lpfnWndProc"),
            JAVA_INT.withName("cbClsExtra"),
            JAVA_INT.withName("cbWndExtra"),
            ADDRESS.withName("hInstance"),
            ADDRESS.withName("hIcon"),
            ADDRESS.withName("hCursor"),
            ADDRESS.withName("hbrBackground"),
            ADDRESS.withName("lpszMenuName"),
            ADDRESS.withName("lpszClassName"),
            ADDRESS.withName("hIconSm")
    ).withName("WNDCLASSEXW");

    /**
     * {@code MSG}: one message pulled off the thread's queue. We never read
     * it ourselves; it is only a buffer for GetMessage/DispatchMessage.
     * <pre>{@code
     * typedef struct tagMSG {
     *   HWND   hwnd;      //  0
     *   UINT   message;   //  8  (+4 padding)
     *   WPARAM wParam;    // 16
     *   LPARAM lParam;    // 24
     *   DWORD  time;      // 32
     *   POINT  pt;        // 36
     *   DWORD  lPrivate;  // 44
     * } MSG;              // 48 bytes
     * }</pre>
     */
    static final StructLayout MSG = MemoryLayout.structLayout(
            ADDRESS.withName("hwnd"),
            JAVA_INT.withName("message"),
            MemoryLayout.paddingLayout(4),
            JAVA_LONG.withName("wParam"),
            JAVA_LONG.withName("lParam"),
            JAVA_INT.withName("time"),
            POINT.withName("pt"),
            JAVA_INT.withName("lPrivate")
    ).withName("MSG");

    /**
     * {@code NOTIFYICONDATAW}: everything {@code Shell_NotifyIconW} needs to
     * know about one icon. We always pass the full, current (Vista+) size.
     * <pre>{@code
     * typedef struct _NOTIFYICONDATAW {
     *   DWORD cbSize;               //   0  (+4 padding)
     *   HWND  hWnd;                 //   8  window that receives callbacks
     *   UINT  uID;                  //  16  our per-icon id
     *   UINT  uFlags;               //  20  NIF_* : which fields are valid
     *   UINT  uCallbackMessage;     //  24  message posted on mouse activity (+4 padding)
     *   HICON hIcon;                //  32
     *   WCHAR szTip[128];           //  40  tooltip, NUL terminated
     *   DWORD dwState;              // 296  NIS_* values
     *   DWORD dwStateMask;          // 300  which NIS_* bits dwState sets
     *   WCHAR szInfo[256];          // 304  balloon text
     *   union { UINT uTimeout; UINT uVersion; };   // 816
     *   WCHAR szInfoTitle[64];      // 820  balloon title
     *   DWORD dwInfoFlags;          // 948
     *   GUID  guidItem;             // 952
     *   HICON hBalloonIcon;         // 968
     * } NOTIFYICONDATAW;            // 976 bytes
     * }</pre>
     */
    static final StructLayout NOTIFYICONDATAW = MemoryLayout.structLayout(
            JAVA_INT.withName("cbSize"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("hWnd"),
            JAVA_INT.withName("uID"),
            JAVA_INT.withName("uFlags"),
            JAVA_INT.withName("uCallbackMessage"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("hIcon"),
            MemoryLayout.sequenceLayout(128, JAVA_CHAR).withName("szTip"),
            JAVA_INT.withName("dwState"),
            JAVA_INT.withName("dwStateMask"),
            MemoryLayout.sequenceLayout(256, JAVA_CHAR).withName("szInfo"),
            JAVA_INT.withName("uVersion"),
            MemoryLayout.sequenceLayout(64, JAVA_CHAR).withName("szInfoTitle"),
            JAVA_INT.withName("dwInfoFlags"),
            GUID.withName("guidItem"),
            ADDRESS.withName("hBalloonIcon")
    ).withName("NOTIFYICONDATAW");

    /**
     * {@code NOTIFYICONIDENTIFIER}: names an icon for {@code Shell_NotifyIconGetRect}.
     * <pre>{@code
     * typedef struct _NOTIFYICONIDENTIFIER {
     *   DWORD cbSize;     //  0  (+4 padding)
     *   HWND  hWnd;       //  8
     *   UINT  uID;        // 16
     *   GUID  guidItem;   // 20  (+4 tail padding)
     * } NOTIFYICONIDENTIFIER;   // 40 bytes
     * }</pre>
     */
    static final StructLayout NOTIFYICONIDENTIFIER = MemoryLayout.structLayout(
            JAVA_INT.withName("cbSize"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("hWnd"),
            JAVA_INT.withName("uID"),
            GUID.withName("guidItem"),
            MemoryLayout.paddingLayout(4)
    ).withName("NOTIFYICONIDENTIFIER");

    // ---- Field offsets -------------------------------------------------------------------

    /** {@code POINT.x}. */
    static final long POINT_X = offset(POINT, "x");
    /** {@code POINT.y}. */
    static final long POINT_Y = offset(POINT, "y");

    /** {@code RECT.left}. */
    static final long RECT_LEFT = offset(RECT, "left");
    /** {@code RECT.top}. */
    static final long RECT_TOP = offset(RECT, "top");
    /** {@code RECT.right}. */
    static final long RECT_RIGHT = offset(RECT, "right");
    /** {@code RECT.bottom}. */
    static final long RECT_BOTTOM = offset(RECT, "bottom");

    /** {@code WNDCLASSEXW.cbSize}. */
    static final long WNDCLASSEXW_CB_SIZE = offset(WNDCLASSEXW, "cbSize");
    /** {@code WNDCLASSEXW.lpfnWndProc}. */
    static final long WNDCLASSEXW_WND_PROC = offset(WNDCLASSEXW, "lpfnWndProc");
    /** {@code WNDCLASSEXW.hInstance}. */
    static final long WNDCLASSEXW_INSTANCE = offset(WNDCLASSEXW, "hInstance");
    /** {@code WNDCLASSEXW.lpszClassName}. */
    static final long WNDCLASSEXW_CLASS_NAME = offset(WNDCLASSEXW, "lpszClassName");

    /** {@code NOTIFYICONDATAW.cbSize}. */
    static final long NID_CB_SIZE = offset(NOTIFYICONDATAW, "cbSize");
    /** {@code NOTIFYICONDATAW.hWnd}. */
    static final long NID_HWND = offset(NOTIFYICONDATAW, "hWnd");
    /** {@code NOTIFYICONDATAW.uID}. */
    static final long NID_ID = offset(NOTIFYICONDATAW, "uID");
    /** {@code NOTIFYICONDATAW.uFlags}. */
    static final long NID_FLAGS = offset(NOTIFYICONDATAW, "uFlags");
    /** {@code NOTIFYICONDATAW.uCallbackMessage}. */
    static final long NID_CALLBACK_MESSAGE = offset(NOTIFYICONDATAW, "uCallbackMessage");
    /** {@code NOTIFYICONDATAW.hIcon}. */
    static final long NID_ICON = offset(NOTIFYICONDATAW, "hIcon");
    /** {@code NOTIFYICONDATAW.szTip}. */
    static final long NID_TIP = offset(NOTIFYICONDATAW, "szTip");
    /** Capacity of {@code szTip} in UTF-16 units, including the terminating NUL. */
    static final int NID_TIP_CHARS = 128;
    /** {@code NOTIFYICONDATAW.dwState}. */
    static final long NID_STATE = offset(NOTIFYICONDATAW, "dwState");
    /** {@code NOTIFYICONDATAW.dwStateMask}. */
    static final long NID_STATE_MASK = offset(NOTIFYICONDATAW, "dwStateMask");

    /** {@code NOTIFYICONIDENTIFIER.cbSize}. */
    static final long NII_CB_SIZE = offset(NOTIFYICONIDENTIFIER, "cbSize");
    /** {@code NOTIFYICONIDENTIFIER.hWnd}. */
    static final long NII_HWND = offset(NOTIFYICONIDENTIFIER, "hWnd");
    /** {@code NOTIFYICONIDENTIFIER.uID}. */
    static final long NII_ID = offset(NOTIFYICONIDENTIFIER, "uID");

    /** Utility class: no instances. */
    private WinStructs() {
        throw new AssertionError("No instances");
    }

    /**
     * Byte offset of a named top-level field.
     *
     * @param layout the struct
     * @param field  field name as given in {@code withName}
     * @return offset in bytes from the start of the struct
     */
    private static long offset(StructLayout layout, String field) {
        return layout.byteOffset(PathElement.groupElement(field));
    }
}
