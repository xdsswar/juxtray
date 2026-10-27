/**
 * Juxtray: native-looking system tray icons for JavaFX, with menus that are
 * real JavaFX nodes and therefore fully stylable with CSS.
 *
 * <p>Only {@code xss.it.juxtray} is exported. Everything under
 * {@code com.xss.it.juxtray} is internal and may change without notice.</p>
 *
 * <p>Native platform calls go through the Foreign Function &amp; Memory API,
 * so applications must launch with
 * {@code --enable-native-access=xss.it.juxtray}.</p>
 */
module xss.it.juxtray {
    // JavaFX types appear in our public API, so consumers get them transitively.
    requires transitive javafx.controls;

    // Public API.
    exports xss.it.juxtray;
}
