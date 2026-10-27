package com.xss.it.juxtray;

/**
 * Receives raw events from a {@link NativeTray}.
 *
 * <p>Called on the back end's native thread. Implementations must return
 * quickly and must not touch JavaFX directly; hand the event to
 * {@code Platform.runLater} instead.</p>
 */
@FunctionalInterface
public interface NativeTrayListener {

    /**
     * Handles one raw event.
     *
     * @param event the event, never {@code null}
     */
    void onEvent(NativeTrayEvent event);
}
