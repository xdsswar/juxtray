package xss.it.juxtray.demo;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.util.function.Function;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
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
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import xss.it.juxtray.Juxtray;
import xss.it.juxtray.NotificationAnimation;
import xss.it.juxtray.NotificationType;
import xss.it.juxtray.TrayIcon;
import xss.it.juxtray.TrayIconEvent;
import xss.it.juxtray.TrayNotification;

/**
 * Showcase for Juxtray: a tray icon with a CSS-styled JavaFX menu, animated
 * notifications, live controls for every tray property, app themes the tray
 * follows, and a log of tray events.
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

    /** Which icon a notification from the Notifications tab shows. */
    private enum IconChoice {
        /** The type's built-in icon, or the tray icon for {@code NONE}. */
        DEFAULT("Default (type icon / app icon)"),
        /** A drawn 256 px image, resampled by Juxtray. */
        IMAGE("Custom image"),
        /** Any node: here, a spinning progress indicator. */
        NODE("Custom node (spinner)");

        /** Label shown in the combo box. */
        final String label;

        /**
         * Creates a choice.
         *
         * @param label display label
         */
        IconChoice(String label) {
            this.label = label;
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

    /** The tray icon under test. */
    private TrayIcon tray;

    /** The demo window. */
    private Stage stage;

    /** Event log shown in the window. */
    private final ListView<String> log = new ListView<>();

    /** The Status submenu (kept so the screenshot mode can open it). */
    private Menu statusMenu;

    /** Tray menu toggle: announce status changes with a notification. */
    private CheckMenuItem notifyStatus;

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
                new MenuShowcase(tray, statusMenu, directory,
                        () -> new NotificationShowcase(tray, drawBell(), directory, this::exit).run()).run();
                return;
            }
        }

        stage.setTitle("Juxtray Demo");
        stage.setScene(new Scene(buildControls(), 580, 800));
        AppTheme.LIGHT.applyTo(stage.getScene());
        // The tray menu and notifications follow whatever scene the window shows: its
        // stylesheets and its root's classes. Switching the app theme restyles the tray too.
        tray.styleSourceProperty().bind(stage.sceneProperty());
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

        notifyStatus = new CheckMenuItem("Notifications");
        notifyStatus.setSelected(true);
        notifyStatus.setOnAction(_ -> append("Notifications " + (notifyStatus.isSelected() ? "on" : "off")));

        MenuItem test = new MenuItem("Send test notification");
        test.setOnAction(_ -> tray.showNotification("Hello from the tray",
                "This notification is a JavaFX node, styled with CSS.", NotificationType.INFO));

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
            item.setOnAction(_ -> setStatus(value));
            statusMenu.getItems().add(item);
        }

        MenuItem disabled = new MenuItem("Disabled item");
        disabled.setDisable(true);

        MenuItem exit = new MenuItem("Exit");
        // Custom style class: coloured red by demo-menu.css, in every theme.
        exit.getStyleClass().add("danger");
        exit.setOnAction(_ -> exit());

        return new ContextMenu(open, new SeparatorMenuItem(), notifyStatus, test, statusMenu, disabled,
                new SeparatorMenuItem(), exit);
    }

    /**
     * Applies a status: new icon and tooltip, and a notification carrying the
     * new icon when notifications are on.
     *
     * @param status the new status
     */
    private void setStatus(Status status) {
        Image icon = drawIcon(status);
        tray.setImage(icon);
        tray.setTooltip("Juxtray Demo — " + status.label);
        append("Status → " + status.label);
        if (notifyStatus.isSelected()) {
            TrayNotification notification = new TrayNotification("Status changed",
                    "You are now " + status.label.toLowerCase() + ".");
            notification.setImage(icon);
            show(notification);
        }
    }

    /**
     * Builds the window: tabs with every tray and notification setting, plus
     * the event log.
     *
     * @return the root node
     */
    private VBox buildControls() {
        Tab trayTab = new Tab("Tray", buildTrayTab());
        Tab notificationTab = new Tab("Notifications", buildNotificationTab());
        TabPane tabs = new TabPane(trayTab, notificationTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Button clear = new Button("Clear log");
        clear.setOnAction(_ -> log.getItems().clear());
        HBox logHeader = new HBox(8, new Label("Tray events"), clear);
        logHeader.setAlignment(Pos.CENTER_LEFT);

        VBox.setVgrow(log, Priority.ALWAYS);
        VBox root = new VBox(10, tabs, logHeader, log);
        root.setPadding(new Insets(10, 14, 14, 14));
        return root;
    }

    /**
     * The Tray tab: tooltip, visibility, menu behaviour, menu theme and app theme.
     *
     * @return the tab content
     */
    private Node buildTrayTab() {
        TextField tooltip = new TextField(tray.getTooltip());
        tooltip.textProperty().bindBidirectional(tray.tooltipProperty());

        CheckBox visible = new CheckBox("Icon visible");
        visible.selectedProperty().bindBidirectional(tray.visibleProperty());

        CheckBox leftMenu = new CheckBox("Open menu on left click too");
        leftMenu.selectedProperty().bindBidirectional(tray.menuOnPrimaryClickProperty());

        // Menu theme: built-in (OS / light / dark), custom CSS themes, or plain Modena.
        ComboBox<MenuTheme> menuTheme = new ComboBox<>();
        menuTheme.getItems().addAll(MenuTheme.values());
        menuTheme.getSelectionModel().select(MenuTheme.SYSTEM);
        menuTheme.valueProperty().addListener((_, _, selected) -> selected.applyTo(tray));

        // App theme: restyles this window, and the tray follows (styleSource).
        ComboBox<AppTheme> appTheme = new ComboBox<>();
        appTheme.getItems().addAll(AppTheme.values());
        appTheme.getSelectionModel().select(AppTheme.LIGHT);
        appTheme.valueProperty().addListener((_, _, selected) -> selected.applyTo(stage.getScene()));

        Button showMenu = new Button("Show tray menu at cursor");
        showMenu.setOnAction(_ -> tray.showMenu());

        GridPane grid = form();
        grid.addRow(0, new Label("Tooltip"), tooltip);
        grid.addRow(1, new Label("Tray theme"), menuTheme);
        grid.addRow(2, new Label("App theme"), appTheme);

        Label hint = new Label("Right-click the tray icon to open its menu; left-click toggles this window. "
                + "The tray follows the app theme, live.");
        hint.setWrapText(true);
        VBox box = new VBox(10, hint, grid, visible, leftMenu, showMenu);
        box.setPadding(new Insets(12, 2, 4, 2));
        return box;
    }

    /**
     * The Notifications tab: compose and show notifications, and tune
     * animation, position and stacking.
     *
     * @return the tab content
     */
    private Node buildNotificationTab() {
        TextField title = new TextField("Build finished");
        TextField message = new TextField("All 128 tests passed in 42 seconds.");

        ComboBox<NotificationType> type = new ComboBox<>();
        type.getItems().addAll(NotificationType.values());
        type.setValue(NotificationType.SUCCESS);

        ComboBox<IconChoice> icon = new ComboBox<>();
        icon.getItems().addAll(IconChoice.values());
        icon.setValue(IconChoice.DEFAULT);

        ComboBox<Duration> duration = new ComboBox<>();
        duration.getItems().addAll(Duration.seconds(3), Duration.seconds(5), Duration.seconds(10),
                Duration.INDEFINITE);
        duration.setValue(TrayNotification.DEFAULT_DURATION);
        duration.setConverter(converter(d -> d.isIndefinite() ? "Until closed" : (int) d.toSeconds() + " s"));

        CheckBox actions = new CheckBox("Action buttons");
        actions.setSelected(true);
        CheckBox closable = new CheckBox("Close button");
        closable.setSelected(true);

        ComboBox<NotificationAnimation> animation = new ComboBox<>();
        animation.getItems().addAll(NotificationAnimation.values());
        animation.valueProperty().bindBidirectional(tray.notificationAnimationProperty());

        // "Auto" maps to a null position: the corner next to the taskbar.
        String auto = "Auto (next to the taskbar)";
        ComboBox<String> position = new ComboBox<>();
        position.getItems().add(auto);
        for (Pos pos : new Pos[] {Pos.BOTTOM_RIGHT, Pos.BOTTOM_LEFT, Pos.TOP_RIGHT, Pos.TOP_LEFT,
                Pos.BOTTOM_CENTER, Pos.TOP_CENTER}) {
            position.getItems().add(pos.name());
        }
        position.setValue(auto);
        position.valueProperty().addListener((_, _, choice) ->
                tray.setNotificationPosition(auto.equals(choice) ? null : Pos.valueOf(choice)));

        Spinner<Integer> max = new Spinner<>(1, 8, tray.getMaxNotifications());
        tray.maxNotificationsProperty().bind(max.valueProperty());

        // Sound: Juxtray's built-in per-type sounds (the default), the demo's own chime,
        // silence, or any WAV/MP3 the user picks.
        String builtIn = "Built-in (one per type)";
        String chime = "Demo chime (custom WAV)";
        String none = "None";
        String pick = "Choose a WAV or MP3…";
        ComboBox<String> sound = new ComboBox<>();
        sound.getItems().addAll(builtIn, chime, none, pick);
        sound.setValue(builtIn);
        sound.valueProperty().addListener((_, previous, choice) -> {
            if (builtIn.equals(choice)) {
                tray.setNotificationSound(null);
                tray.setUseDefaultSounds(true);
            } else if (chime.equals(choice)) {
                tray.setNotificationSound(resource("chime.wav"));
            } else if (none.equals(choice)) {
                tray.setNotificationSound(null);
                tray.setUseDefaultSounds(false);
            } else if (pick.equals(choice)) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Notification sound");
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Sounds (WAV, MP3)", "*.wav", "*.mp3"));
                File file = chooser.showOpenDialog(stage);
                if (file == null) {
                    // Cancelled: go back to the previous choice once this change has finished.
                    Platform.runLater(() -> sound.setValue(previous));
                    return;
                }
                tray.setNotificationSound(file.toURI().toString());
                append("Notification sound → " + file.getName());
                // Let the user hear it right away.
                show(new TrayNotification("New sound", file.getName(), NotificationType.INFO));
            }
        });

        Slider volume = new Slider(0, 1, tray.getNotificationVolume());
        tray.notificationVolumeProperty().bind(volume.valueProperty());

        Button show = new Button("Show notification");
        show.setDefaultButton(true);
        show.setOnAction(_ -> {
            TrayNotification notification = new TrayNotification(title.getText(), message.getText(), type.getValue());
            switch (icon.getValue()) {
                case IMAGE -> notification.setImage(drawBell());
                case NODE -> notification.setGraphic(new ProgressIndicator());
                case DEFAULT -> {
                    // Built-in icon for the type, or the tray icon for NONE.
                }
            }
            notification.setDuration(duration.getValue());
            notification.setClosable(closable.isSelected());
            if (actions.isSelected()) {
                Button later = new Button("Later");
                later.setOnAction(_ -> append("Action: Later"));
                Button open = new Button("Open");
                open.setDefaultButton(true);
                open.setOnAction(_ -> {
                    append("Action: Open");
                    showWindow();
                });
                notification.getActions().addAll(later, open);
            }
            show(notification);
        });

        Button allTypes = new Button("One of each type");
        allTypes.setOnAction(_ -> {
            show(new TrayNotification("Plain", "Uses the tray icon as its icon.", NotificationType.NONE));
            show(new TrayNotification("Info", "A new version is available.", NotificationType.INFO));
            show(new TrayNotification("Success", "Your files are backed up.", NotificationType.SUCCESS));
            show(new TrayNotification("Warning", "The disk is almost full.", NotificationType.WARNING));
            show(new TrayNotification("Error", "The server could not be reached.", NotificationType.ERROR));
        });

        Button progress = new Button("Live progress");
        progress.setOnAction(_ -> showProgress());

        Button hideAll = new Button("Hide all");
        hideAll.setOnAction(_ -> tray.hideNotifications());

        GridPane grid = form();
        grid.addRow(0, new Label("Title"), title);
        grid.addRow(1, new Label("Message"), message);
        grid.addRow(2, new Label("Type"), type);
        grid.addRow(3, new Label("Icon"), icon);
        grid.addRow(4, new Label("Duration"), duration);
        grid.addRow(5, new Label("Animation"), animation);
        grid.addRow(6, new Label("Position"), position);
        grid.addRow(7, new Label("Max open"), max);
        grid.addRow(8, new Label("Sound"), sound);
        grid.addRow(9, new Label("Volume"), volume);

        VBox box = new VBox(10, grid, new HBox(16, actions, closable),
                new HBox(8, show, allTypes, progress, hideAll));
        box.setPadding(new Insets(12, 2, 4, 2));
        return box;
    }

    /**
     * Shows a notification whose content changes while it is open: a fake
     * download that counts up, then turns into a success notification that
     * closes on its own.
     */
    private void showProgress() {
        TrayNotification notification = new TrayNotification("Downloading update", "Starting…",
                NotificationType.INFO);
        notification.setGraphic(new ProgressIndicator());
        // Stay until done; switching to a finite duration later starts the countdown.
        notification.setDuration(Duration.INDEFINITE);
        show(notification);

        int[] percent = {0};
        Timeline ticker = new Timeline(new KeyFrame(Duration.millis(60), _ -> {
            percent[0] += 2;
            notification.setMessage("juxtray-update.zip — " + percent[0] + " %");
        }));
        ticker.setCycleCount(50);
        ticker.setOnFinished(_ -> {
            notification.setGraphic(null);
            notification.setType(NotificationType.SUCCESS);
            notification.setTitle("Update downloaded");
            notification.setMessage("Restart the app to finish installing.");
            notification.setDuration(Duration.seconds(5));
        });
        // Stop counting if the user closes it early.
        notification.showingProperty().addListener((_, _, showing) -> {
            if (!showing) {
                ticker.stop();
            }
        });
        ticker.play();
    }

    /**
     * Shows a notification and logs its life cycle.
     *
     * @param notification the notification
     */
    private void show(TrayNotification notification) {
        String name = "\"" + notification.getTitle() + "\"";
        notification.setOnShown(_ -> append("Notification shown: " + name));
        notification.setOnHidden(_ -> append("Notification hidden: " + name));
        notification.setOnAction(_ -> {
            append("Notification clicked: " + name);
            showWindow();
        });
        tray.showNotification(notification);
    }

    /**
     * Resolves a resource next to this class as a URL string.
     *
     * @param name resource name
     * @return the URL
     */
    private static String resource(String name) {
        return DemoApp.class.getResource(name).toExternalForm();
    }

    /**
     * A two-column form grid.
     *
     * @return the grid
     */
    private static GridPane form() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        return grid;
    }

    /**
     * A display-only string converter for combo boxes.
     *
     * @param format turns a value into its label
     * @param <T>    the value type
     * @return the converter
     */
    private static <T> StringConverter<T> converter(Function<T, String> format) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return format.apply(value);
            }

            @Override
            public T fromString(String text) {
                throw new UnsupportedOperationException("Display only");
            }
        };
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
        return snapshot(canvas);
    }

    /**
     * Draws a bell on a purple circle, for the "custom image" notification icon.
     *
     * @return the image, 256 px
     */
    private static Image drawBell() {
        double size = 256;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#a855f7")), new Stop(1, Color.web("#6d28d9"))));
        g.fillOval(0, 0, size, size);
        g.setFill(Color.WHITE);
        // Bell body: a dome on a flared base.
        g.beginPath();
        g.moveTo(128, 52);
        g.bezierCurveTo(84, 52, 72, 92, 72, 128);
        g.lineTo(72, 160);
        g.lineTo(56, 180);
        g.lineTo(200, 180);
        g.lineTo(184, 160);
        g.lineTo(184, 128);
        g.bezierCurveTo(184, 92, 172, 52, 128, 52);
        g.closePath();
        g.fill();
        // Clapper.
        g.fillOval(110, 186, 36, 28);
        return snapshot(canvas);
    }

    /**
     * Snapshots a canvas with a transparent background.
     *
     * @param canvas the drawing
     * @return the image
     */
    private static Image snapshot(Canvas canvas) {
        SnapshotParameters params = new SnapshotParameters();
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
