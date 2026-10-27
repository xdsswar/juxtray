package xss.it.juxtray.demo;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javafx.application.ColorScheme;
import xss.it.juxtray.TrayIcon;

/**
 * The menu looks the demo can switch between. Each one is just a combination
 * of public {@link TrayIcon} settings: the built-in theme on or off, its
 * forced colour scheme, and extra stylesheets.
 *
 * <p>{@code demo-menu.css} (the {@code .danger} Exit item, status dots) is
 * always added, to show that app-specific rules mix freely with any theme.</p>
 */
enum MenuTheme {

    /** Built-in theme, following the Windows light/dark setting live. */
    SYSTEM("Built-in (follows OS)", "system", true, null),

    /** Built-in theme, forced light. */
    LIGHT("Built-in light", "light", true, ColorScheme.LIGHT),

    /** Built-in theme, forced dark. */
    DARK("Built-in dark", "dark", true, ColorScheme.DARK),

    /** Built-in theme re-coloured and extended by {@code neon-menu.css}. */
    NEON("Neon (custom CSS)", "neon", true, ColorScheme.DARK, "neon-menu.css"),

    /** A completely different look from {@code terminal-menu.css}. */
    TERMINAL("Terminal (custom CSS)", "terminal", true, ColorScheme.DARK, "terminal-menu.css"),

    /** Built-in theme off: JavaFX's stock Modena look. */
    MODENA("Plain JavaFX (Modena)", "modena", false, null);

    /** Stylesheet added to every theme. */
    private static final String BASE = "demo-menu.css";

    /** Label shown in the demo window. */
    final String label;

    /** File-name friendly id, used for screenshots. */
    final String id;

    /** Whether the built-in theme is on. */
    private final boolean builtIn;

    /** Forced colour scheme of the built-in theme, or {@code null} to follow the OS. */
    private final ColorScheme scheme;

    /** Extra stylesheets (resource names next to this class). */
    private final String[] sheets;

    /**
     * Creates a theme.
     *
     * @param label   display label
     * @param id      file-name friendly id
     * @param builtIn whether the built-in theme is on
     * @param scheme  forced colour scheme, or {@code null}
     * @param sheets  extra stylesheet resource names
     */
    MenuTheme(String label, String id, boolean builtIn, ColorScheme scheme, String... sheets) {
        this.label = label;
        this.id = id;
        this.builtIn = builtIn;
        this.scheme = scheme;
        this.sheets = sheets;
    }

    /**
     * Applies this theme to a tray icon. Takes effect the next time the menu opens.
     *
     * @param tray the tray icon
     */
    void applyTo(TrayIcon tray) {
        List<String> urls = new ArrayList<>();
        urls.add(resource(BASE));
        for (String sheet : sheets) {
            urls.add(resource(sheet));
        }
        tray.setUseDefaultTheme(builtIn);
        tray.setMenuColorScheme(scheme);
        tray.getStylesheets().setAll(urls);
    }

    /**
     * Resolves a stylesheet next to this class.
     *
     * @param name resource name
     * @return the stylesheet URL as a string
     */
    private static String resource(String name) {
        URL url = MenuTheme.class.getResource(name);
        if (url == null) {
            throw new IllegalStateException("Missing stylesheet " + name);
        }
        return url.toExternalForm();
    }

    /**
     * Label for combo boxes.
     *
     * @return the display label
     */
    @Override
    public String toString() {
        return label;
    }
}
