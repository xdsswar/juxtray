package com.xss.it.juxtray.fx;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Keeps the CSS of an invisible host stage, and of every popup it owns
 * (directly or through other popups), in sync with Juxtray's theme, the
 * user's tray stylesheets and, optionally, an application scene.
 *
 * <p>The resulting stylesheet order is:</p>
 * <ol>
 *   <li>the built-in theme (light or dark), when enabled;</li>
 *   <li>the stylesheets of the <em>style source</em> scene, when one is set;</li>
 *   <li>the tray's own stylesheets.</li>
 * </ol>
 *
 * <p>The style source also lends its user-agent stylesheet, and the style
 * classes, inline style and pseudo-classes of its root node. They are
 * mirrored onto the host scene's root and the root of each popup, so theme
 * rules written against the
 * application root (for example {@code .root.dark .context-menu} or
 * {@code .root:dark}) match inside tray menus and notifications too. All of
 * this is live: when the application switches theme, open menus and
 * notifications restyle at once.</p>
 *
 * <p>Popups are found through {@link Window#getWindows()}: whenever a window
 * shows whose owner chain ends at the host stage, it is styled. That covers
 * context menus, their submenus, notification popups and any tooltips
 * inside them, with no cooperation from those classes.</p>
 *
 * <p>All methods must be called on the JavaFX Application Thread.</p>
 */
public final class StyleLink {

    /** The style class every scene root carries; never mirrored or removed. */
    private static final String ROOT_CLASS = "root";

    /** The host stage whose popups are styled. */
    private final Stage host;

    /** Built-in light theme. */
    private final URL lightTheme;

    /** Built-in dark theme. */
    private final URL darkTheme;

    /** The tray's own stylesheets, applied last. */
    private final ObservableList<String> userStylesheets;

    /** Whether the built-in theme is applied. */
    private final ObservableBooleanValue useDefaultTheme;

    /** Forced light/dark variant of the built-in theme, or a {@code null} value to follow the OS. */
    private final ObservableValue<ColorScheme> colorScheme;

    /** The application scene to follow, or a {@code null} value for none. */
    private final ObservableValue<Scene> styleSource;

    /**
     * What was mirrored onto the host scene's root.
     *
     * <p>This is what makes {@code .root.dark .context-menu} work for menus.
     * CSS selectors match along the <em>styleable</em> parent chain, and for a
     * {@code ContextMenu} owned by a window that chain is
     * {@code ContextMenuContent → ContextMenu → owner scene root}: the host
     * root, not the root of the popup's own scene. Plain {@code Popup}s
     * (notifications) are matched through their popup root instead, which is
     * why popup roots are mirrored too. Changing a popup root also makes an
     * open menu recompute its CSS, which a change on the host root alone
     * would not, since the menu's nodes are not in the host's scene.</p>
     */
    private final Mirrored hostMirrored = new Mirrored();

    /** Popup scenes currently styled by this link, with what was mirrored onto their roots. */
    private final Map<Window, Mirrored> attached = new HashMap<>();

    /** Recomputes everything when any input changes. */
    private final InvalidationListener updater = _ -> update();

    /** Follows the style source to its current root. */
    private final InvalidationListener rootTracker = _ -> trackRoot();

    /** Styles popups as they appear and forgets them as they go. */
    private final ListChangeListener<Window> windowTracker = change -> {
        while (change.next()) {
            for (Window removed : change.getRemoved()) {
                detach(removed);
            }
            for (Window added : change.getAddedSubList()) {
                // Only popups are styled here; the host's own scene is handled by update().
                if (added instanceof PopupWindow && isOwnedByHost(added)) {
                    attach(added);
                }
            }
        }
    };

    /** The style source scene we currently listen to. */
    private Scene trackedScene;

    /** The root of {@link #trackedScene} we currently listen to. */
    private Parent trackedRoot;

    /** The stylesheets currently in force, in order. */
    private List<String> stylesheets = List.of();

    /**
     * Links a host stage. The host's scene must already be set.
     *
     * @param host            the invisible owner of the popups to style
     * @param lightTheme      built-in light theme, or {@code null}
     * @param darkTheme       built-in dark theme, or {@code null}
     * @param userStylesheets the tray's own stylesheets; watched
     * @param useDefaultTheme whether to apply the built-in theme; watched
     * @param colorScheme     forced light/dark variant, or a {@code null} value to follow the OS; watched
     * @param styleSource     application scene to follow, or a {@code null} value; watched
     */
    public StyleLink(Stage host, URL lightTheme, URL darkTheme, ObservableList<String> userStylesheets,
                     ObservableBooleanValue useDefaultTheme, ObservableValue<ColorScheme> colorScheme,
                     ObservableValue<Scene> styleSource) {
        this.host = Objects.requireNonNull(host, "host");
        this.lightTheme = lightTheme;
        this.darkTheme = darkTheme;
        this.userStylesheets = Objects.requireNonNull(userStylesheets, "userStylesheets");
        this.useDefaultTheme = Objects.requireNonNull(useDefaultTheme, "useDefaultTheme");
        this.colorScheme = Objects.requireNonNull(colorScheme, "colorScheme");
        this.styleSource = Objects.requireNonNull(styleSource, "styleSource");

        userStylesheets.addListener(updater);
        useDefaultTheme.addListener(updater);
        colorScheme.addListener(updater);
        Platform.getPreferences().colorSchemeProperty().addListener(updater);
        styleSource.addListener(rootTracker);
        Window.getWindows().addListener(windowTracker);
        trackRoot();
    }

    /**
     * Stops listening to everything. The host and its popups keep their
     * current styles.
     */
    public void dispose() {
        userStylesheets.removeListener(updater);
        useDefaultTheme.removeListener(updater);
        colorScheme.removeListener(updater);
        Platform.getPreferences().colorSchemeProperty().removeListener(updater);
        styleSource.removeListener(rootTracker);
        Window.getWindows().removeListener(windowTracker);
        untrack();
        attached.clear();
    }

    /**
     * Re-attaches listeners to the current style source scene and its root,
     * then recomputes.
     */
    private void trackRoot() {
        untrack();
        trackedScene = styleSource.getValue();
        if (trackedScene != null) {
            trackedScene.getStylesheets().addListener(updater);
            trackedScene.userAgentStylesheetProperty().addListener(updater);
            trackedScene.rootProperty().addListener(rootTracker);
            trackedRoot = trackedScene.getRoot();
            if (trackedRoot != null) {
                trackedRoot.getStyleClass().addListener(updater);
                trackedRoot.styleProperty().addListener(updater);
                trackedRoot.getPseudoClassStates().addListener(updater);
            }
        }
        update();
    }

    /**
     * Removes every listener added by {@link #trackRoot()}.
     */
    private void untrack() {
        if (trackedScene != null) {
            trackedScene.getStylesheets().removeListener(updater);
            trackedScene.userAgentStylesheetProperty().removeListener(updater);
            trackedScene.rootProperty().removeListener(rootTracker);
        }
        if (trackedRoot != null) {
            trackedRoot.getStyleClass().removeListener(updater);
            trackedRoot.styleProperty().removeListener(updater);
            trackedRoot.getPseudoClassStates().removeListener(updater);
        }
        trackedScene = null;
        trackedRoot = null;
    }

    /**
     * Recomputes the stylesheets and pushes them, plus the mirrored root
     * state, to the host scene and every attached popup.
     */
    private void update() {
        List<String> sheets = new ArrayList<>();
        if (useDefaultTheme.get()) {
            ColorScheme scheme = colorScheme.getValue() != null
                    ? colorScheme.getValue()
                    : Platform.getPreferences().getColorScheme();
            URL theme = scheme == ColorScheme.DARK ? darkTheme : lightTheme;
            if (theme != null) {
                sheets.add(theme.toExternalForm());
            }
        }
        if (trackedScene != null) {
            sheets.addAll(trackedScene.getStylesheets());
        }
        sheets.addAll(userStylesheets);
        stylesheets = List.copyOf(sheets);

        // Popups copy their root owner's stylesheets when they open, so the host scene must
        // always carry the full list. Its root also gets the mirrored state: see hostMirrored.
        apply(host.getScene(), hostMirrored);
        attached.forEach((window, mirrored) -> apply(window.getScene(), mirrored));
    }

    /**
     * Starts styling a popup that just appeared.
     *
     * @param window the popup
     */
    private void attach(Window window) {
        Mirrored mirrored = new Mirrored();
        attached.put(window, mirrored);
        apply(window.getScene(), mirrored);
    }

    /**
     * Stops styling a popup that just went away, and takes the mirrored root
     * state back off it.
     *
     * <p>Context menus and their submenus are reused: the same popup window,
     * with the same scene root, is shown again the next time the menu opens.
     * If the mirrored classes stayed on that root, a fresh {@link Mirrored}
     * would not know about them when the popup reappears, so a class the
     * style source has dropped in the meantime (for example {@code dark}
     * after the application switched to light) would stick to the menu
     * forever.</p>
     *
     * @param window the popup that was hidden
     */
    private void detach(Window window) {
        Mirrored mirrored = attached.remove(window);
        Scene scene = window.getScene();
        if (mirrored != null && scene != null && scene.getRoot() != null) {
            mirrored.applyTo(scene.getRoot(), null);
        }
    }

    /**
     * Applies the current stylesheets, user-agent stylesheet and mirrored
     * root state to one scene.
     *
     * @param scene    the scene, or {@code null} (ignored)
     * @param mirrored what was mirrored onto this scene's root before
     */
    private void apply(Scene scene, Mirrored mirrored) {
        if (scene == null) {
            return;
        }
        if (!scene.getStylesheets().equals(stylesheets)) {
            scene.getStylesheets().setAll(stylesheets);
        }
        scene.setUserAgentStylesheet(trackedScene == null ? null : trackedScene.getUserAgentStylesheet());
        if (scene.getRoot() != null) {
            mirrored.applyTo(scene.getRoot(), trackedRoot);
        }
    }

    /**
     * Tells whether a window is a popup that belongs to the host stage,
     * directly or through other popups (submenus, tooltips...).
     *
     * @param window the window to check
     * @return {@code true} if its owner chain ends at the host
     */
    private boolean isOwnedByHost(Window window) {
        Window current = window;
        while (current instanceof PopupWindow popup) {
            current = popup.getOwnerWindow();
        }
        return current == host;
    }

    /**
     * What has been mirrored from the style source onto one root (the host
     * scene's or a popup's), so it can be taken back off precisely without
     * touching the root's own classes (such as {@code root} and
     * {@code popup}), pseudo-classes or inline style.
     */
    private static final class Mirrored {

        /** Style classes we added. */
        private final List<String> styleClasses = new ArrayList<>();

        /** Pseudo-classes we switched on. */
        private final Set<PseudoClass> pseudoClasses = new HashSet<>();

        /** Whether {@link #ownStyle} has been captured yet. */
        private boolean ownStyleKnown;

        /** The target's own inline style, from before anything was mirrored onto it. */
        private String ownStyle;

        /**
         * Makes a root carry the source root's classes, inline style and
         * pseudo-classes, replacing what was mirrored before.
         *
         * @param target the host or popup root
         * @param source the style source root, or {@code null} to mirror nothing
         */
        void applyTo(Parent target, Parent source) {
            if (!ownStyleKnown) {
                // The host root has its own inline style (a transparent background); keep it.
                ownStyle = target.getStyle();
                ownStyleKnown = true;
            }
            target.getStyleClass().removeAll(styleClasses);
            styleClasses.clear();
            for (PseudoClass pseudoClass : pseudoClasses) {
                target.pseudoClassStateChanged(pseudoClass, false);
            }
            pseudoClasses.clear();

            if (source == null) {
                target.setStyle(ownStyle);
                return;
            }
            for (String styleClass : source.getStyleClass()) {
                if (!ROOT_CLASS.equals(styleClass) && !target.getStyleClass().contains(styleClass)) {
                    target.getStyleClass().add(styleClass);
                    styleClasses.add(styleClass);
                }
            }
            for (PseudoClass pseudoClass : source.getPseudoClassStates()) {
                // Leave interaction states alone; they describe the source, not a theme. Skip states
                // the target already has, so taking ours off later never clears one of its own.
                String name = pseudoClass.getPseudoClassName();
                if (!name.equals("hover") && !name.equals("pressed") && !name.equals("focused")
                        && !name.equals("focus-within") && !name.equals("focus-visible")
                        && !target.getPseudoClassStates().contains(pseudoClass)) {
                    target.pseudoClassStateChanged(pseudoClass, true);
                    pseudoClasses.add(pseudoClass);
                }
            }
            // The source's inline style goes after the target's own, so its declarations win.
            String sourceStyle = source.getStyle();
            if (sourceStyle == null || sourceStyle.isBlank()) {
                target.setStyle(ownStyle);
            } else if (ownStyle == null || ownStyle.isBlank()) {
                target.setStyle(sourceStyle);
            } else {
                String separator = ownStyle.strip().endsWith(";") ? " " : "; ";
                target.setStyle(ownStyle + separator + sourceStyle);
            }
        }
    }
}
