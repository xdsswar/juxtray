package xss.it.juxtray.demo;

import javafx.scene.Scene;

/**
 * The demo window's own themes. Each one is a style class on the scene root,
 * defined in {@code app-themes.css}.
 *
 * <p>The tray icon follows the window through
 * {@code tray.styleSourceProperty().bind(stage.sceneProperty())}, so
 * switching the app theme restyles the tray menu and notifications too,
 * even while they are open. {@code app-themes.css} does that with rules
 * such as {@code .root.app-ocean .tray-notification}, which match because
 * Juxtray mirrors the root's style classes onto its popups.</p>
 */
enum AppTheme {

    /** JavaFX's default look; the tray keeps its built-in theme. */
    LIGHT("Light", null),

    /** A dark window, and a matching dark tray. */
    DARK("Dark", "app-dark"),

    /** A blue window, and a matching blue tray. */
    OCEAN("Ocean", "app-ocean");

    /** Label shown in the demo window. */
    final String label;

    /** Style class put on the scene root, or {@code null} for none. */
    private final String styleClass;

    /**
     * Creates a theme.
     *
     * @param label      display label
     * @param styleClass root style class, or {@code null}
     */
    AppTheme(String label, String styleClass) {
        this.label = label;
        this.styleClass = styleClass;
    }

    /**
     * Applies this theme to the demo scene: makes sure {@code app-themes.css}
     * is loaded and puts this theme's class (only) on the root.
     *
     * @param scene the demo window's scene
     */
    void applyTo(Scene scene) {
        String sheet = AppTheme.class.getResource("app-themes.css").toExternalForm();
        if (!scene.getStylesheets().contains(sheet)) {
            scene.getStylesheets().add(sheet);
        }
        for (AppTheme theme : values()) {
            if (theme.styleClass != null) {
                scene.getRoot().getStyleClass().remove(theme.styleClass);
            }
        }
        if (styleClass != null) {
            scene.getRoot().getStyleClass().add(styleClass);
        }
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
