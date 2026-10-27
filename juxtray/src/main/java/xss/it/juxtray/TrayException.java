package xss.it.juxtray;

import java.io.Serial;

/**
 * Thrown when the operating system refuses a tray operation, or when the
 * current platform has no tray support.
 *
 * <p>This is an unchecked exception: tray failures are rare, usually not
 * recoverable by the caller, and should not force {@code try/catch} noise on
 * every property setter.</p>
 */
public class TrayException extends RuntimeException {

    /** Serialization version; exceptions are serializable, so this keeps the form stable. */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message human readable description of the failure
     */
    public TrayException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and the underlying cause.
     *
     * @param message human readable description of the failure
     * @param cause   the original error
     */
    public TrayException(String message, Throwable cause) {
        super(message, cause);
    }
}
