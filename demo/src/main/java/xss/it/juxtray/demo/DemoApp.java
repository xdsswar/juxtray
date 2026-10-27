package xss.it.juxtray.demo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import xss.it.juxtray.Juxtray;
import xss.it.juxtray.TrayIcon;
import xss.it.juxtray.TrayIconEvent;

/**
 * Showcase for Juxtray: a tray icon with a CSS-styled JavaFX menu, live
 * controls for every tray property, and a log of tray mouse events.
 *
 * <p>Closing the window only hides it; the app keeps living in the tray. Use
 * <em>Exit</em> in the tray menu to quit.</p>
 */
public final class DemoApp extends Application {

    /** Timestamp format for the event log. */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Maximum number of lines kept in the event log. */
    private static final int MAX_LOG_LINES = 200;

    /** Status colours; picking a status in the tray menu recolours the icon. */
    private enum Status {
        /** Available. */
        ONLINE("Online", Color.web("#22c55e"), Color.web("#15803d")),
        /** Away from keyboard. */
        AWAY("Away", Color.web("#f59e0b"), Color.web("#b45309")),
        /** Do not disturb. */
        BUSY("Busy", Color.web("#ef4444"), Color.web("#b91c1c"));

        /** Label shown in menus. */
        final String label;
        /** Gradient start colour of the icon. */
        final Color light;
        /** Gradient end colour of the icon. */
        final Color dark;

        /**
         * Creates a status.
         *
         * @param label menu label
         * @param light gradient start
         * @param dark  gradient end
         */
        Status(String label, Color light, Color dark) {
            this.label = label;
            this.light = light;
            this.dark = dark;
        }
    }

    /** The tray icon under test. */
    private TrayIcon tray;

    /** The demo window. */
    private Stage stage;

    /** Event log shown in the window. */
    private final ListView<String> log = new ListView<>();

    /** The Status submenu (kept so the screenshot mode can open it). */
    private Menu statusMenu;

    /** Command line flag that renders the README screenshots and exits. */
    private static final String SCREENSHOTS_FLAG = "--screenshots=";

    /**
     * Builds the tray icon and the demo window.
     *
     * @param primaryStage the primary stage supplied by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        // A tray app must survive with every window closed.
        Platform.setImplicitExit(false);
        stage = primaryStage;

        if (!Juxtray.isSupported()) {
            stage.setScene(new Scene(new Label("Tray icons are not supported on this platform yet."), 420, 120));
            stage.show();
            return;
        }

        tray = new TrayIcon(drawIcon(Status.ONLINE), "Juxtray Demo — Online", buildMenu());
        // A left click toggles the window; the menu opens on right click.
        tray.setOnAction(_ -> toggleWindow());
        tray.setOnTrayEvent(this::logEvent);
        MenuTheme.SYSTEM.applyTo(tray);

        // Screenshot mode: render every menu theme to PNG, then quit (no window).
        for (String arg : getParameters().getRaw()) {
            if (arg.startsWith(SCREENSHOTS_FLAG)) {
                Path directory = Path.of(arg.substring(SCREENSHOTS_FLAG.length()));
                new MenuShowcase(tray, statusMenu, directory, this::exit).run();
                return;
            }
        }

        stage.setTitle("Juxtray Demo");
        stage.setScene(new Scene(buildControls(), 520, 560));
        // Closing the window just sends the app back to the tray.
        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.hide();
        });
        stage.show();
    }

    /**
     * Builds the tray menu: plain items, a check item, a submenu with radio
     * items carrying graphics, a disabled item, separators and an exit item
     * with a custom style class ({@code danger}).
     *
     * @return the menu
     */
    private ContextMenu buildMenu() {
        MenuItem open = new MenuItem("Open Juxtray Demo");
        open.setOnAction(_ -> showWindow());

        CheckMenuItem notifications = new CheckMenuItem("Notifications");
        notifications.setSelected(true);
        notifications.setOnAction(_ -> append("Notifications " + (notifications.isSelected() ? "on" : "off")));

        // Status submenu: radio items that recolour the tray icon.
        statusMenu = new Menu("Status");
        ToggleGroup statusGroup = new ToggleGroup();
        for (Status value : Status.values()) {
            // A graphic per item: a coloured dot, styled further by demo-menu.css (.status-dot).
            Circle dot = new Circle(5, value.light);
            dot.getStyleClass().add("status-dot");
            RadioMenuItem item = new RadioMenuItem(value.label, dot);
            item.setToggleGroup(statusGroup);
            item.setSelected(value == Status.ONLINE);
            item.setOnAction(_ -> {
                tray.setImage(drawIcon(value));
                tray.setTooltip("Juxtray Demo — " + value.label);
                append("Status → " + value.label);
            });
            statusMenu.getItems().add(item);
        }

        MenuItem disabled = new MenuItem("Disabled item");
        disabled.setDisable(true);

        MenuItem exit = new MenuItem("Exit");
        // Custom style class: coloured red by demo-menu.css, in every theme.
        exit.getStyleClass().add("danger");
        exit.setOnAction(_ -> exit());

        return new ContextMenu(open, new SeparatorMenuItem(), notifications, statusMenu, disabled,
                new SeparatorMenuItem(), exit);
    }

    /**
     * Builds the window: controls for every tray property plus the event log.
     *
     * @return the root node
     */
    private VBox buildControls() {
        TextField tooltip = new TextField(tray.getTooltip());
        tooltip.textProperty().bindBidirectional(tray.tooltipProperty());
        HBox tooltipRow = new HBox(8, new Label("Tooltip"), tooltip);
        tooltipRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tooltip, Priority.ALWAYS);

        CheckBox visible = new CheckBox("Icon visible");
        visible.selectedProperty().bindBidirectional(tray.visibleProperty());

        CheckBox leftMenu = new CheckBox("Open menu on left click too");
        leftMenu.selectedProperty().bindBidirectional(tray.menuOnPrimaryClickProperty());

        // Menu theme: built-in (OS / light / dark), custom CSS themes, or plain Modena.
        ComboBox<MenuTheme> theme = new ComboBox<>();
        theme.getItems().addAll(MenuTheme.values());
        theme.getSelectionModel().select(MenuTheme.SYSTEM);
        theme.valueProperty().addListener((_, _, selected) -> selected.applyTo(tray));
        HBox themeRow = new HBox(8, new Label("Menu theme"), theme);
        themeRow.setAlignment(Pos.CENTER_LEFT);

        Button showMenu = new Button("Show tray menu at cursor");
        showMenu.setOnAction(_ -> tray.showMenu());

        Button clear = new Button("Clear log");
        clear.setOnAction(_ -> log.getItems().clear());

        VBox.setVgrow(log, Priority.ALWAYS);
        VBox root = new VBox(10,
                new Label("Right-click the tray icon to open its menu; left-click toggles this window."),
                tooltipRow, visible, leftMenu, themeRow, new HBox(8, showMenu, clear),
                new Label("Tray events"), log);
        root.setPadding(new Insets(14));
        return root;
    }

    /**
     * Draws the demo icon: a rounded square with a gradient and a "J".
     * Drawn at 256 px; Juxtray resamples it to the exact tray size.
     *
     * @param status which colour to use
     * @return the icon image
     */
    private static Image drawIcon(Status status) {
        double size = 256;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, status.light), new Stop(1, status.dark)));
        g.fillRoundRect(8, 8, size - 16, size - 16, 72, 72);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 180));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("J", size / 2, size * 0.76);

        SnapshotParameters params = new SnapshotParameters();
        // Transparent outside the rounded square.
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    /**
     * Adds one tray event to the log. Hover moves are frequent, so they are summarised.
     *
     * @param event the event
     */
    private void logEvent(TrayIconEvent event) {
        String text = switch (event) {
            case TrayIconEvent.Click click -> "Click " + click.button() + " " + click.state();
            case TrayIconEvent.DoubleClick dbl -> "Double-click " + dbl.button();
            case TrayIconEvent.Enter _ -> "Enter";
            case TrayIconEvent.Move _ -> "Move";
            case TrayIconEvent.Exit _ -> "Exit";
        };
        append(String.format("%s @ (%.0f, %.0f) icon %s", text,
                event.position().getX(), event.position().getY(), describe(event)));
    }

    /**
     * Formats the icon bounds of an event.
     *
     * @param event the event
     * @return a short "x,y wxh" description, or "?" when unknown
     */
    private static String describe(TrayIconEvent event) {
        Rectangle2D b = event.bounds();
        if (b.getWidth() <= 0) {
            return "?";
        }
        return String.format("%.0f,%.0f %.0fx%.0f", b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
    }

    /**
     * Appends a timestamped line to the log, keeping it bounded and scrolled to the end.
     *
     * @param line text to add
     */
    private void append(String line) {
        log.getItems().add(LocalTime.now().format(TIME) + "  " + line);
        if (log.getItems().size() > MAX_LOG_LINES) {
            log.getItems().removeFirst();
        }
        log.scrollTo(log.getItems().size() - 1);
    }

    /** Shows the window if hidden, hides it if shown. */
    private void toggleWindow() {
        if (stage.isShowing()) {
            stage.hide();
        } else {
            showWindow();
        }
    }

    /** Shows and focuses the window. */
    private void showWindow() {
        stage.show();
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    /** Removes the tray icon and quits. */
    private void exit() {
        tray.close();
        Platform.exit();
    }

    /**
     * Launches the JavaFX runtime.
     *
     * @param args command line arguments, passed through to JavaFX
     */
    public static void main(String[] args) {
        launch(args);
    }
}
