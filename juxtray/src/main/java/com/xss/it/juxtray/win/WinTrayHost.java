package com.xss.it.juxtray.win;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import com.xss.it.juxtray.PhysicalRect;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import xss.it.juxtray.TrayException;

/**
 * The single Win32 "engine room" shared by all tray icons of the process.
 *
 * <p>It owns:</p>
 * <ul>
 *   <li>a dedicated platform thread, {@code juxtray-win32}, running a classic
 *       {@code GetMessageW} loop. Windows delivers tray callbacks to the thread
 *       that created the window, so all icon work happens here;</li>
 *   <li>one hidden, never-shown top-level window. The shell posts every icon's
 *       mouse activity to it, and it also receives the broadcast
 *       {@code "TaskbarCreated"} message when Explorer restarts. A message-only
 *       window would not get that broadcast, which is why we need a real,
 *       invisible top-level window;</li>
 *   <li>the window procedure, implemented in Java as an FFM <em>upcall</em>
 *       stub;</li>
 *   <li>a task queue, so other threads can run code on the Win32 thread by
 *       queueing a task and posting {@link #WM_APP_TASKS}.</li>
 * </ul>
 *
 * <p>The host is created lazily on first use and lives until the process
 * exits. A shutdown hook removes any icons still present, so the taskbar is
 * not left with "ghost" icons when the application forgets to close them.</p>
 */
final class WinTrayHost {

    /** Logger for native-side problems that cannot be thrown to a caller. */
    private static final Logger LOG = System.getLogger(WinTrayHost.class.getName());

    /** Private message: "drain the task queue". */
    static final int WM_APP_TASKS = Win32.WM_APP + 1;

    /** Private message the shell posts for icon mouse activity ({@code uCallbackMessage}). */
    static final int WM_APP_TRAY = Win32.WM_APP + 2;

    /** Name of our window class. */
    private static final String CLASS_NAME = "JuxtrayTrayWindow";

    /**
     * Native function pointer to {@link #windowProc}. It is created once and
     * kept for the life of the process, because Windows may call the window
     * procedure at any time.
     */
    private static final MemorySegment WINDOW_PROC = createWindowProcStub();

    /** Guards lazy creation of {@link #instance}. */
    private static final Object LOCK = new Object();

    /** The one host, or {@code null} until first use. */
    private static volatile WinTrayHost instance;

    /** Work queued by other threads, drained on the Win32 thread. */
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /** Live icons by {@code uID}. Concurrent because the shutdown hook reads it too. */
    private final Map<Integer, WinTray> trays = new ConcurrentHashMap<>();

    /** Completed once the window exists (or failed if it could not be created). */
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    /** The Win32 thread. */
    private final Thread thread;

    /** Our hidden window. Valid once {@link #ready} completed. */
    private volatile MemorySegment hwnd = MemorySegment.NULL;

    /** Id of the {@code "TaskbarCreated"} broadcast, or 0 before registration. */
    private volatile int taskbarCreatedMessage;

    /**
     * Set when the message loop has ended. From then on nothing drains the
     * task queue, so {@link #execute} refuses new work instead of letting
     * {@link #call} wait forever.
     */
    private volatile boolean stopped;

    /**
     * Timer id for the debounced "displays changed" re-check. Tray timers use
     * their small positive {@code uID}s, so this value can never collide.
     */
    private static final long DISPLAY_TIMER_ID = 0x7FFF_0001L;

    /**
     * Delay before re-checking icon sizes after a display change, in milliseconds.
     * Windows sends a burst of messages and Explorer re-lays out the taskbar
     * afterwards, so we wait for things to settle instead of reacting to each one.
     */
    private static final int DISPLAY_SETTLE_MILLIS = 400;

    /**
     * Starts the Win32 thread. Use {@link #get()} instead.
     */
    private WinTrayHost() {
        thread = Thread.ofPlatform()
                .name("juxtray-win32")
                // Daemon: an idle tray thread must never keep the JVM alive.
                .daemon(true)
                .unstarted(this::run);
    }

    /**
     * Returns the host, creating the thread and window on first call.
     *
     * @return the ready host
     * @throws TrayException if the hidden window could not be created
     */
    static WinTrayHost get() {
        WinTrayHost host = instance;
        if (host == null) {
            synchronized (LOCK) {
                host = instance;
                if (host == null) {
                    host = new WinTrayHost();
                    // Publish before starting, so windowProc can find it from the first message.
                    instance = host;
                    host.thread.start();
                    Runtime.getRuntime().addShutdownHook(
                            Thread.ofPlatform().name("juxtray-shutdown").unstarted(host::removeAllFromShell));
                }
            }
        }
        try {
            host.ready.join();
        } catch (CompletionException e) {
            throw new TrayException("Could not start the Win32 tray thread", e.getCause());
        }
        return host;
    }

    /**
     * Our hidden window, to which icon callbacks are posted.
     *
     * @return the window handle
     */
    MemorySegment hwnd() {
        return hwnd;
    }

    /**
     * Recommended icon size for the monitor that shows a tray icon.
     *
     * <p>The taskbar (and therefore the icon) lives on one specific monitor,
     * which can have a different scale factor than the primary one. So the
     * size comes from <em>that</em> monitor's DPI, not the system DPI.</p>
     *
     * @param iconBounds where the icon is, in device pixels, or {@code null} if
     *                   unknown (the primary monitor is used then)
     * @return edge length in device pixels, for example 16 at 100 % or 24 at 150 %
     */
    static int iconSizeAt(PhysicalRect iconBounds) {
        int dpi = Shcore.dpiForMonitor(User32.monitorFromRect(iconBounds));
        if (dpi <= 0) {
            dpi = User32.systemDpi();
        }
        return User32.smallIconSize(dpi > 0 ? dpi : Win32.USER_DEFAULT_SCREEN_DPI);
    }

    /**
     * Runs a task on the Win32 thread, asynchronously. When already on that
     * thread, the task runs immediately.
     *
     * @param task the work to do
     * @throws TrayException if the Win32 thread has stopped
     */
    void execute(Runnable task) {
        if (Thread.currentThread() == thread) {
            task.run();
            return;
        }
        if (stopped) {
            throw stoppedFailure();
        }
        tasks.add(task);
        // The loop may have ended between the check and add(). Its final drain then either ran
        // the task already, or never saw it; in the second case remove() succeeds and we report
        // it, because nothing else ever will.
        if (stopped && tasks.remove(task)) {
            throw stoppedFailure();
        }
        // Wake the message loop; it drains the queue when it handles WM_APP_TASKS.
        User32.postMessage(hwnd, WM_APP_TASKS, 0L, 0L);
    }

    /**
     * The exception for work handed to a Win32 thread that is no longer running.
     *
     * @return the exception to throw
     */
    private static TrayException stoppedFailure() {
        return new TrayException("The Win32 tray thread has stopped");
    }

    /**
     * Runs a task on the Win32 thread and waits for its result.
     *
     * <p>Safe from the JavaFX thread: the Win32 thread never waits for JavaFX,
     * so this cannot deadlock.</p>
     *
     * @param task the work to do
     * @param <T>  result type
     * @return what the task returned
     */
    <T> T call(Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(task, this::execute).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new TrayException("Tray operation failed", e.getCause());
        }
    }

    /**
     * Tracks a new icon so callbacks for its {@code uID} reach it. Win32 thread only.
     *
     * @param tray the icon
     */
    void register(WinTray tray) {
        trays.put(tray.id(), tray);
    }

    /**
     * Stops tracking an icon. Win32 thread only.
     *
     * @param tray the icon
     */
    void unregister(WinTray tray) {
        trays.remove(tray.id());
    }

    /**
     * Body of the Win32 thread: create the window, then pump messages forever.
     */
    private void run() {
        // Confined arena: the class name and MSG buffer belong to this thread for its whole life.
        try (Arena arena = Arena.ofConfined()) {
            // Match JavaFX: true device pixels on every monitor.
            User32.usePerMonitorDpiAwareness();

            MemorySegment module = Kernel32.processModule();
            MemorySegment className = Win32.wide(arena, CLASS_NAME);

            MemorySegment windowClass = arena.allocate(WinStructs.WNDCLASSEXW);
            windowClass.set(JAVA_INT, WinStructs.WNDCLASSEXW_CB_SIZE, (int) WinStructs.WNDCLASSEXW.byteSize());
            windowClass.set(ADDRESS, WinStructs.WNDCLASSEXW_WND_PROC, WINDOW_PROC);
            windowClass.set(ADDRESS, WinStructs.WNDCLASSEXW_INSTANCE, module);
            windowClass.set(ADDRESS, WinStructs.WNDCLASSEXW_CLASS_NAME, className);
            User32.registerClassEx(windowClass);

            // WS_EX_TOOLWINDOW keeps the window out of the taskbar even after an Explorer restart;
            // the others make sure it can never be activated or receive mouse input itself.
            hwnd = User32.createWindowEx(
                    Win32.WS_EX_NOACTIVATE | Win32.WS_EX_TRANSPARENT | Win32.WS_EX_LAYERED | Win32.WS_EX_TOOLWINDOW,
                    className, Win32.WS_OVERLAPPED, module);

            int taskbarCreated = User32.registerWindowMessage("TaskbarCreated");
            if (taskbarCreated != 0) {
                // Elevated processes would otherwise never see the broadcast from a non-elevated Explorer.
                User32.allowMessage(hwnd, taskbarCreated);
            }
            taskbarCreatedMessage = taskbarCreated;
            ready.complete(null);

            MemorySegment msg = arena.allocate(WinStructs.MSG);
            // GetMessageW returns 0 for WM_QUIT and -1 on error; both end the loop.
            while (User32.getMessage(msg) > 0) {
                User32.translateMessage(msg);
                User32.dispatchMessage(msg);
            }
        } catch (Throwable t) {
            // If startup failed this reaches the caller of get(); otherwise just record it.
            if (!ready.completeExceptionally(t)) {
                LOG.log(Level.ERROR, "Win32 tray thread died", t);
            }
        } finally {
            // Refuse new work, then run what is already queued one last time, so no caller of
            // call() is left waiting for a result that would never come.
            stopped = true;
            drainTasks();
        }
    }

    /**
     * Builds the upcall stub that lets Windows call {@link #windowProc}.
     *
     * <p>C signature: {@code LRESULT CALLBACK WndProc(HWND, UINT, WPARAM, LPARAM)}.
     * The stub lives in the global arena, so it is never freed.</p>
     *
     * @return the native function pointer
     */
    // Restricted by design: the only upcall in Juxtray.
    @SuppressWarnings("restricted")
    private static MemorySegment createWindowProcStub() {
        try {
            MethodHandle target = MethodHandles.lookup().findStatic(WinTrayHost.class, "windowProc",
                    MethodType.methodType(long.class, MemorySegment.class, int.class, long.class, long.class));
            return Win32.LINKER.upcallStub(target,
                    FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG),
                    Arena.global());
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * The window procedure, called by Windows on the Win32 thread.
     *
     * <p>An exception escaping an upcall crashes the whole JVM, so this method
     * catches everything and falls back to {@code DefWindowProcW}.</p>
     *
     * @param hwnd    window receiving the message
     * @param message message id
     * @param wParam  first parameter
     * @param lParam  second parameter
     * @return the message result
     */
    private static long windowProc(MemorySegment hwnd, int message, long wParam, long lParam) {
        try {
            WinTrayHost host = instance;
            if (host != null && host.handle(message, wParam, lParam)) {
                return 0L;
            }
        } catch (Throwable t) {
            LOG.log(Level.ERROR, "Error in tray window procedure", t);
        }
        try {
            return User32.defWindowProc(hwnd, message, wParam, lParam);
        } catch (Throwable t) {
            LOG.log(Level.ERROR, "DefWindowProcW failed", t);
            return 0L;
        }
    }

    /**
     * Handles our own messages.
     *
     * @param message message id
     * @param wParam  first parameter
     * @param lParam  second parameter
     * @return {@code true} if handled; {@code false} to let Windows do its default
     */
    private boolean handle(int message, long wParam, long lParam) {
        if (message == WM_APP_TASKS) {
            drainTasks();
            return true;
        }
        if (message == WM_APP_TRAY) {
            // Classic (version 0) callback: wParam = icon uID, lParam = mouse message.
            WinTray tray = trays.get((int) wParam);
            if (tray != null) {
                tray.onMouseMessage((int) lParam);
            }
            return true;
        }
        if (message == Win32.WM_DISPLAYCHANGE || message == Win32.WM_SETTINGCHANGE
                || message == Win32.WM_DPICHANGED) {
            // Monitors, resolution or scaling may have changed: re-check icon sizes once
            // things settle. Re-arming the same timer id restarts it (debounce).
            User32.setTimer(hwnd, DISPLAY_TIMER_ID, DISPLAY_SETTLE_MILLIS);
            // Not "handled": DefWindowProcW still does its default work.
            return false;
        }
        if (message == Win32.WM_TIMER && wParam == DISPLAY_TIMER_ID) {
            User32.killTimer(hwnd, DISPLAY_TIMER_ID);
            trays.values().forEach(WinTray::refreshIconSize);
            return true;
        }
        if (message == Win32.WM_TIMER) {
            // Each icon uses its uID as its hover timer id.
            WinTray tray = trays.get((int) wParam);
            if (tray != null) {
                tray.onHoverTimer();
                return true;
            }
            return false;
        }
        int taskbarCreated = taskbarCreatedMessage;
        if (taskbarCreated != 0 && message == taskbarCreated) {
            // Explorer (re)started and forgot every icon: add ours back.
            trays.values().forEach(WinTray::readd);
            return true;
        }
        return false;
    }

    /**
     * Runs every queued task. A failing task is logged and does not stop the others.
     */
    private void drainTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                LOG.log(Level.ERROR, "Tray task failed", t);
            }
        }
    }

    /**
     * Shutdown hook: removes all icons still in the tray. Runs on the hook
     * thread, because the Win32 thread may already be gone at this point.
     */
    private void removeAllFromShell() {
        trays.values().forEach(WinTray::removeFromShell);
    }
}
