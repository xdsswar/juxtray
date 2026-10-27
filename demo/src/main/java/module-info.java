/**
 * Demo application for Juxtray.
 */
module xss.it.juxtray.demo {
    requires xss.it.juxtray;

    // JavaFX instantiates the Application subclass reflectively.
    exports xss.it.juxtray.demo to javafx.graphics;
}
