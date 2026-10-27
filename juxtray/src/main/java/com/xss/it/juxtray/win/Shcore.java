package com.xss.it.juxtray.win;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * FFM bindings for the per-monitor DPI functions of {@code shcore.dll} (Windows 8.1+).
 */
final class Shcore {

    /** {@code shcore.dll}. */
    private static final SymbolLookup SHCORE = Win32.library("shcore");

    /** {@code MONITOR_DPI_TYPE} value: the DPI the user picked in Settings (scale × 96). */
    private static final int MDT_EFFECTIVE_DPI = 0;

    /**
     * {@code HRESULT GetDpiForMonitor(HMONITOR hmonitor, MONITOR_DPI_TYPE dpiType, UINT *dpiX, UINT *dpiY);}
     */
    private static final MethodHandle GET_DPI_FOR_MONITOR = Win32.bind(SHCORE, "GetDpiForMonitor",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    /** Utility class: no instances. */
    private Shcore() {
        throw new AssertionError("No instances");
    }

    /**
     * Effective DPI of a monitor: 96 at 100 % scaling, 144 at 150 %, and so on.
     *
     * @param monitor an {@code HMONITOR}
     * @return the horizontal DPI, or {@code 0} if the call failed
     */
    static int dpiForMonitor(MemorySegment monitor) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dpiX = arena.allocate(JAVA_INT);
            MemorySegment dpiY = arena.allocate(JAVA_INT);
            // HRESULT: S_OK (0) on success.
            if ((int) GET_DPI_FOR_MONITOR.invokeExact(monitor, MDT_EFFECTIVE_DPI, dpiX, dpiY) != 0) {
                return 0;
            }
            return dpiX.get(JAVA_INT, 0L);
        } catch (Throwable t) {
            throw Win32.failure("GetDpiForMonitor", t);
        }
    }
}
