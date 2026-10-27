package com.xss.it.juxtray.win;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Checks our FFM struct layouts against {@code sizeof} and {@code offsetof}
 * from the 64-bit Windows SDK. A wrong layout silently corrupts native
 * memory, so these numbers are pinned here.
 */
class WinStructsTest {

    /** {@code sizeof} of every struct we pass to Win32. */
    @Test
    void sizesMatchWindowsSdk() {
        assertEquals(8, WinStructs.POINT.byteSize(), "POINT");
        assertEquals(16, WinStructs.RECT.byteSize(), "RECT");
        assertEquals(16, WinStructs.GUID.byteSize(), "GUID");
        assertEquals(80, WinStructs.WNDCLASSEXW.byteSize(), "WNDCLASSEXW");
        assertEquals(48, WinStructs.MSG.byteSize(), "MSG");
        assertEquals(976, WinStructs.NOTIFYICONDATAW.byteSize(), "NOTIFYICONDATAW");
        assertEquals(40, WinStructs.NOTIFYICONIDENTIFIER.byteSize(), "NOTIFYICONIDENTIFIER");
    }

    /** {@code offsetof} of the {@code NOTIFYICONDATAW} fields we write. */
    @Test
    void notifyIconDataOffsetsMatchWindowsSdk() {
        assertEquals(0, WinStructs.NID_CB_SIZE);
        assertEquals(8, WinStructs.NID_HWND);
        assertEquals(16, WinStructs.NID_ID);
        assertEquals(20, WinStructs.NID_FLAGS);
        assertEquals(24, WinStructs.NID_CALLBACK_MESSAGE);
        assertEquals(32, WinStructs.NID_ICON);
        assertEquals(40, WinStructs.NID_TIP);
        assertEquals(296, WinStructs.NID_STATE);
        assertEquals(300, WinStructs.NID_STATE_MASK);
    }

    /** {@code offsetof} of the {@code WNDCLASSEXW} and {@code NOTIFYICONIDENTIFIER} fields we write. */
    @Test
    void otherOffsetsMatchWindowsSdk() {
        assertEquals(8, WinStructs.WNDCLASSEXW_WND_PROC);
        assertEquals(24, WinStructs.WNDCLASSEXW_INSTANCE);
        assertEquals(64, WinStructs.WNDCLASSEXW_CLASS_NAME);
        assertEquals(8, WinStructs.NII_HWND);
        assertEquals(16, WinStructs.NII_ID);
    }
}
