package com.xss.it.juxtray.win;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import com.xss.it.juxtray.IconPixels;
import com.xss.it.juxtray.NativeTray;
import com.xss.it.juxtray.NativeTrayEvent;
import com.xss.it.juxtray.NativeTrayEvent.Type;
import com.xss.it.juxtray.NativeTrayListener;
import com.xss.it.juxtray.PhysicalPoint;
import com.xss.it.juxtray.PhysicalRect;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.input.MouseButton;

/**
 * One Windows notification-area icon, driven through {@code Shell_NotifyIconW}.
 *
 * <p>All icons share the single hidden window of {@link WinTrayHost} and
 * are told apart by their {@code uID}, so creating many icons costs no
 * extra windows or threads.</p>
 *
 * <p><strong>Threading:</strong> fields without {@code volatile} are only
 * touched on the Win32 thread. Public methods may be called from any thread;
 * they hand the work to the Win32 thread through {@link WinTrayHost#execute}.</p>
 */
public final class WinTray implements NativeTray {

    /** Logger for failures that cannot be reported to a caller. */
    private static final Logger LOG = System.getLogger(WinTray.class.getName());

    /** Source of unique {@code uID}s. Starts at 1 because 0 is not a valid timer id. */
    private static final AtomicInteger IDS = new AtomicInteger(1);

    /**
     * How often to check whether the cursor left the icon, in milliseconds. The
     * shell has no "mouse left" notification, so we poll while hovering.
     */
    private static final int HOVER_POLL_MILLIS = 30;

    /** The shared Win32 thread and window. */
    private final WinTrayHost host;

    /** This icon's {@code uID}; also used as its hover timer id. */
    private final int id;

    /** Receiver of raw mouse events. */
    private final NativeTrayListener listener;

    /** Current {@code HICON}, or {@code NULL}. Owned by this object. */
    private volatile MemorySegment icon = MemorySegment.NULL;

    /** Icon edge length for the DPI of the icon's monitor; see {@link #refreshIconSize()}. */
    private volatile int iconSize = 16;

    /** Current tooltip, or {@code null}. */
    private String tooltip;

    /** Whether the icon is shown. */
    private boolean visible = true;

    /** Whether the shell currently knows this icon ({@code NIM_ADD} succeeded). */
    private volatile boolean added;

    /** Whether {@link #close()} was called. */
    private volatile boolean closed;

    /** Whether the cursor is currently over the icon. */
    private boolean hovering;

    /** Last cursor position seen while hovering, to drop duplicate move messages. */
    private PhysicalPoint lastHoverPosition;

    /**
     * Creates the object; {@link #create} does the native work.
     *
     * @param host     the shared Win32 host
     * @param listener receiver of raw events
     */
    private WinTray(WinTrayHost host, NativeTrayListener listener) {
        this.host = host;
        this.id = IDS.getAndIncrement();
        this.listener = listener;
    }

    /**
     * Creates a tray icon and adds it to the notification area. The icon starts
     * empty and without tooltip.
     *
     * @param listener receiver of raw mouse events, called on the Win32 thread
     * @return the new icon
     */
    public static WinTray create(NativeTrayListener listener) {
        Objects.requireNonNull(listener, "listener");
        WinTrayHost host = WinTrayHost.get();
        WinTray tray = new WinTray(host, listener);
        host.call(() -> {
            host.register(tray);
            tray.add();
            // Now that the shell placed the icon, size it for the DPI of its monitor.
            tray.iconSize = WinTrayHost.iconSizeAt(Shell32.iconRect(host.hwnd(), tray.id));
            return null;
        });
        return tray;
    }

    /**
     * The icon's {@code uID}.
     *
     * @return the id
     */
    int id() {
        return id;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Windows: builds a new {@code HICON}, sends {@code NIM_MODIFY} with
     * {@code NIF_ICON}, then destroys the previous {@code HICON}. Runs
     * asynchronously on the Win32 thread.</p>
     *
     * @param pixels {@inheritDoc}
     */
    @Override
    public void setIcon(IconPixels pixels) {
        host.execute(() -> {
            if (closed) {
                return;
            }
            MemorySegment previous = icon;
            icon = pixels == null ? MemorySegment.NULL : toIcon(pixels);
            update(Win32.NIF_ICON);
            // Only free the old icon once the shell has switched to the new one.
            User32.destroyIcon(previous);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Windows: {@code NIM_MODIFY} with {@code NIF_TIP}; the text is cut to
     * 127 UTF-16 units (without splitting a surrogate pair). Asynchronous.</p>
     *
     * @param tooltip {@inheritDoc}
     */
    @Override
    public void setTooltip(String tooltip) {
        host.execute(() -> {
            if (closed) {
                return;
            }
            this.tooltip = tooltip;
            update(Win32.NIF_TIP);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Windows: toggles {@code NIS_HIDDEN} through {@code NIF_STATE}. The
     * icon stays registered, keeps its {@code uID} and position, and still
     * gets re-added after an Explorer restart. Asynchronous.</p>
     *
     * @param visible {@inheritDoc}
     */
    @Override
    public void setVisible(boolean visible) {
        host.execute(() -> {
            if (closed) {
                return;
            }
            this.visible = visible;
            update(Win32.NIF_STATE);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Windows: {@code GetSystemMetricsForDpi(SM_CXSMICON)} for the DPI of
     * the monitor holding the icon. It is refreshed by
     * {@link #refreshIconSize()}.</p>
     *
     * @return {@inheritDoc}
     */
    @Override
    public int iconSize() {
        return iconSize;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Windows: {@code GetCursorPos}, which is thread-agnostic, so it is
     * called directly on the caller's thread.</p>
     *
     * @return {@inheritDoc}
     */
    @Override
    public PhysicalPoint cursorPosition() {
        return User32.cursorPosition();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Windows: {@code FindWindowW} by title, then
     * {@code SetForegroundWindow}. Windows only allows this right after user
     * input routed to our process, such as the tray click that opens the
     * menu.</p>
     *
     * @param title {@inheritDoc}
     */
    @Override
    public void activateWindow(String title) {
        User32.activateWindow(title);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Windows: remembers {@code GetForegroundWindow()}, runs the action,
     * then hands the foreground back with {@code SetForegroundWindow}. If the
     * action did activate our window, our process is now the foreground
     * process and therefore allowed to do that. If it did not, the previous
     * window still has the foreground and the call changes nothing. Both
     * functions work from any thread, so this runs on the caller's.</p>
     *
     * @param showAction {@inheritDoc}
     */
    @Override
    public void runWithoutActivation(Runnable showAction) {
        MemorySegment previous = User32.foregroundWindow();
        showAction.run();
        if (!Win32.isNull(previous) && previous.address() != User32.foregroundWindow().address()) {
            User32.setForegroundWindow(previous);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Windows: stops the hover timer, sends {@code NIM_DELETE}, stops
     * routing callbacks to this icon and destroys its {@code HICON}. The
     * native part runs asynchronously on the Win32 thread.</p>
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        host.execute(() -> {
            User32.killTimer(host.hwnd(), id);
            removeFromShell();
            host.unregister(this);
            User32.destroyIcon(icon);
            icon = MemorySegment.NULL;
        });
    }

    // ---- Win32 thread -------------------------------------------------------------------

    /**
     * Adds the icon to the shell with its full current state ({@code NIM_ADD}).
     *
     * <p>Failure is not fatal. When the app starts before Explorer, adding
     * fails, and the icon is added later when {@code TaskbarCreated}
     * arrives.</p>
     */
    private void add() {
        added = notifyShell(Win32.NIM_ADD,
                Win32.NIF_MESSAGE | Win32.NIF_ICON | Win32.NIF_TIP | Win32.NIF_STATE);
        if (!added) {
            LOG.log(Level.WARNING, "Shell_NotifyIconW(NIM_ADD) failed; waiting for the taskbar to appear");
        }
    }

    /**
     * Pushes some fields to the shell ({@code NIM_MODIFY}), or adds the icon if
     * the shell does not know it yet.
     *
     * @param flags {@code NIF_*} flags naming the fields that changed
     */
    private void update(int flags) {
        if (!added) {
            add();
            return;
        }
        if (!notifyShell(Win32.NIM_MODIFY, flags)) {
            LOG.log(Level.WARNING, "Shell_NotifyIconW(NIM_MODIFY) failed for flags 0x" + Integer.toHexString(flags));
        }
    }

    /**
     * Re-adds the icon after Explorer restarted.
     */
    void readd() {
        if (closed) {
            return;
        }
        // The new Explorer does not know us, but be safe in case it kept a stale entry.
        removeFromShell();
        hovering = false;
        add();
        // The new taskbar may live on another monitor, or at another scale.
        refreshIconSize();
    }

    /**
     * Recomputes the ideal icon size for the monitor that now shows the icon.
     * If it changed, tells the listener, so the image gets resampled to stay
     * crisp. Called on the Win32 thread after display or scaling changes.
     */
    void refreshIconSize() {
        if (closed) {
            return;
        }
        int size = WinTrayHost.iconSizeAt(Shell32.iconRect(host.hwnd(), id));
        if (size != iconSize) {
            iconSize = size;
            emit(Type.ICON_SIZE_CHANGED, null);
        }
    }

    /**
     * Removes the icon from the shell ({@code NIM_DELETE}). Called on the Win32
     * thread, or on the shutdown hook thread at exit.
     */
    void removeFromShell() {
        if (added) {
            added = false;
            notifyShell(Win32.NIM_DELETE, 0);
        }
    }

    /**
     * Fills a {@code NOTIFYICONDATAW} from the current state and sends it to the shell.
     *
     * @param message {@code NIM_ADD}, {@code NIM_MODIFY} or {@code NIM_DELETE}
     * @param flags   {@code NIF_*} flags naming the valid fields
     * @return {@code true} if the shell accepted the call
     */
    private boolean notifyShell(int message, int flags) {
        try (Arena arena = Arena.ofConfined()) {
            // allocate() zero-fills, so every field we do not set is 0 / NULL / empty string.
            MemorySegment data = arena.allocate(WinStructs.NOTIFYICONDATAW);
            data.set(JAVA_INT, WinStructs.NID_CB_SIZE, (int) WinStructs.NOTIFYICONDATAW.byteSize());
            data.set(ADDRESS, WinStructs.NID_HWND, host.hwnd());
            data.set(JAVA_INT, WinStructs.NID_ID, id);
            data.set(JAVA_INT, WinStructs.NID_FLAGS, flags);
            data.set(JAVA_INT, WinStructs.NID_CALLBACK_MESSAGE, WinTrayHost.WM_APP_TRAY);
            data.set(ADDRESS, WinStructs.NID_ICON, icon);
            writeTooltip(data, tooltip);
            // Only the NIS_HIDDEN bit is ours to change.
            data.set(JAVA_INT, WinStructs.NID_STATE, visible ? 0 : Win32.NIS_HIDDEN);
            data.set(JAVA_INT, WinStructs.NID_STATE_MASK, Win32.NIS_HIDDEN);
            return Shell32.notifyIcon(message, data);
        }
    }

    /**
     * Copies the tooltip into {@code szTip}, truncated to fit (127 chars + NUL).
     *
     * @param data    the {@code NOTIFYICONDATAW}
     * @param tooltip text to copy, or {@code null} for none
     */
    private static void writeTooltip(MemorySegment data, String tooltip) {
        if (tooltip == null) {
            return;
        }
        int length = Math.min(tooltip.length(), WinStructs.NID_TIP_CHARS - 1);
        // Don't cut a surrogate pair in half.
        if (length > 0 && length < tooltip.length() && Character.isHighSurrogate(tooltip.charAt(length - 1))) {
            length--;
        }
        for (int i = 0; i < length; i++) {
            data.set(JAVA_CHAR, WinStructs.NID_TIP + (long) i * Character.BYTES, tooltip.charAt(i));
        }
    }

    /**
     * Converts pixels into a new {@code HICON}.
     *
     * <p>We pass a 32 bpp colour bitmap with real alpha, plus an all-zero AND
     * mask. With alpha present, Windows ignores the mask and blends per pixel.
     * Non-premultiplied ARGB ints stored little-endian are exactly the BGRA
     * bytes {@code CreateIcon} wants, so the array is copied as-is.</p>
     *
     * @param pixels the image, top-down
     * @return the new icon handle
     */
    private static MemorySegment toIcon(IconPixels pixels) {
        int width = pixels.width();
        int height = pixels.height();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment colour = arena.allocate(JAVA_INT, (long) width * height);
            MemorySegment.copy(pixels.argb(), 0, colour, JAVA_INT, 0L, width * height);
            // 1 bpp mask; each row padded to a 16-bit boundary as CreateIcon requires.
            int maskStride = ((width + 15) / 16) * 2;
            MemorySegment mask = arena.allocate((long) maskStride * height);
            return User32.createIcon(width, height, mask, colour);
        }
    }

    /**
     * Turns a classic tray callback into raw events. Called by the host on the
     * Win32 thread.
     *
     * @param mouseMessage the {@code WM_*} mouse message from {@code lParam}
     */
    void onMouseMessage(int mouseMessage) {
        if (closed) {
            return;
        }
        switch (mouseMessage) {
            case Win32.WM_LBUTTONDOWN -> emit(Type.PRESSED, MouseButton.PRIMARY);
            case Win32.WM_LBUTTONUP -> emit(Type.RELEASED, MouseButton.PRIMARY);
            case Win32.WM_LBUTTONDBLCLK -> emit(Type.DOUBLE_CLICKED, MouseButton.PRIMARY);
            case Win32.WM_RBUTTONDOWN -> emit(Type.PRESSED, MouseButton.SECONDARY);
            case Win32.WM_RBUTTONUP -> emit(Type.RELEASED, MouseButton.SECONDARY);
            case Win32.WM_RBUTTONDBLCLK -> emit(Type.DOUBLE_CLICKED, MouseButton.SECONDARY);
            case Win32.WM_MBUTTONDOWN -> emit(Type.PRESSED, MouseButton.MIDDLE);
            case Win32.WM_MBUTTONUP -> emit(Type.RELEASED, MouseButton.MIDDLE);
            case Win32.WM_MBUTTONDBLCLK -> emit(Type.DOUBLE_CLICKED, MouseButton.MIDDLE);
            case Win32.WM_MOUSEMOVE -> onMouseMove();
            default -> {
                // Other notifications (keyboard select, balloon events) are not used yet.
            }
        }
    }

    /**
     * Turns {@code WM_MOUSEMOVE} into enter/move events and starts the poll
     * that detects when the cursor leaves.
     */
    private void onMouseMove() {
        PhysicalPoint position = User32.cursorPosition();
        if (!hovering) {
            hovering = true;
            lastHoverPosition = position;
            User32.setTimer(host.hwnd(), id, HOVER_POLL_MILLIS);
            emit(Type.ENTERED, null);
        } else if (!Objects.equals(position, lastHoverPosition)) {
            // The shell repeats WM_MOUSEMOVE without movement; only report real moves.
            lastHoverPosition = position;
            emit(Type.MOVED, null);
        }
    }

    /**
     * Hover poll: when the cursor is no longer inside the icon, stop polling
     * and report {@link Type#EXITED}. Called by the host on {@code WM_TIMER}.
     */
    void onHoverTimer() {
        PhysicalPoint position = User32.cursorPosition();
        PhysicalRect bounds = Shell32.iconRect(host.hwnd(), id);
        if (position != null && bounds != null && bounds.contains(position)) {
            return;
        }
        User32.killTimer(host.hwnd(), id);
        hovering = false;
        lastHoverPosition = null;
        emit(Type.EXITED, null);
    }

    /**
     * Sends one raw event to the listener. Listener failures are logged, not
     * thrown, because we are inside the window procedure.
     *
     * @param type   what happened
     * @param button which button, or {@code null}
     */
    private void emit(Type type, MouseButton button) {
        try {
            listener.onEvent(new NativeTrayEvent(type, button, Shell32.iconRect(host.hwnd(), id)));
        } catch (Throwable t) {
            LOG.log(Level.ERROR, "Tray event listener failed", t);
        }
    }
}
