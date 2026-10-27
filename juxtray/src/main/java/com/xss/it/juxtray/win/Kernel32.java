package com.xss.it.juxtray.win;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * FFM bindings for the {@code kernel32.dll} functions Juxtray needs.
 */
final class Kernel32 {

    /** {@code kernel32.dll}. */
    private static final SymbolLookup KERNEL32 = Win32.library("kernel32");

    /** {@code HMODULE GetModuleHandleW(LPCWSTR lpModuleName);} */
    private static final MethodHandle GET_MODULE_HANDLE = Win32.bind(KERNEL32, "GetModuleHandleW",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    /** Utility class: no instances. */
    private Kernel32() {
        throw new AssertionError("No instances");
    }

    /**
     * Handle of the executable that started this process (normally
     * {@code java.exe} or a jpackage launcher). Window classes are registered
     * against it.
     *
     * @return the process's main module handle
     */
    static MemorySegment processModule() {
        try {
            return (MemorySegment) GET_MODULE_HANDLE.invokeExact(MemorySegment.NULL);
        } catch (Throwable t) {
            throw Win32.failure("GetModuleHandleW", t);
        }
    }
}
