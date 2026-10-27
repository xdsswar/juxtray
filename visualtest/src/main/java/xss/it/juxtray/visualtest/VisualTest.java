package xss.it.juxtray.visualtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import xss.it.juxtray.NotificationAnimation;
import xss.it.juxtray.NotificationType;
import xss.it.juxtray.TrayIcon;
import xss.it.juxtray.TrayIconEvent;
import xss.it.juxtray.TrayNotification;

/**
 * End-to-end visual test: a real tray icon, driven by real mouse and
 * keyboard input, checked by state and by screenshots.
 *
 * <p><strong>It takes over the mouse and keyboard for about two minutes.</strong>
 * Don't touch them while it runs. Run it with {@code gradlew :visualtest:run};
 * screenshots go to {@code visualtest/build/screenshots}, a PASS/FAIL line
 * is printed per check, and the process exits with status 1 if any check
 * failed. Windows only, like the library today.</p>
 *
 * <p>Covered: the icon, tooltip and hover events; left and right clicks;
 * menu placement, submenus, check items, closing by Escape, by an outside
 * click and by choosing an item; keyboard navigation; the built-in light
 * and dark themes, a custom stylesheet, plain Modena, live restyling and the
 * application style source; notifications of every type, stacking, the
 * open limit, action buttons, hover-pause, body click, close button, live
 * updates, re-showing while fading out, positions and animations, two tray
 * icons sharing a corner, focus; hide/show, a failing image and close.</p>
 *
 * <p>If Windows keeps the icon in the hidden-icons overflow (the default for
 * new icons on Windows 11), the test opens the flyout first, like a user.</p>
 */
public final class VisualTest {

    /** Background colour the test's application theme gives the menu, as {@code Color.toString()}. */
    private static final String RED = "0x601010ff";

    /** Input, screenshots and results. */
    private final Driver driver;

    /** Path of an extra stylesheet to apply to the menu (the demo's Neon theme). */
    private final Path neonStylesheet;

    /** Every tray event received, as text; written by the FX thread, read by the test thread. */
    private final List<String> events = new CopyOnWriteArrayList<>();

    /** How often the tray's {@code onAction} ran. */
    private final AtomicInteger actions = new AtomicInteger();

    /** When the run started, for the timestamps in {@link #events}. */
    private final long startMillis = System.currentTimeMillis();

    /** Name of the last menu item chosen, or {@code null}. */
    private volatile String chosen;

    /** The application window: an outside-click target and the style source. */
    private Stage app;

    /** The tray icon under test. */
    private TrayIcon tray;

    /** Its menu. */
    private ContextMenu menu;

    /** The menu's check item. */
    private CheckMenuItem checkItem;

    /** The menu's submenu. */
    private Menu submenu;

    /**
     * Bounds of the overflow chevron when the icon lives in the closed
     * overflow flyout, else {@code null}. See {@link #pointAtIcon()}.
     */
    private Rectangle2D chevron;

    /**
     * Creates the test.
     *
     * @param driver         input, screenshots and results
     * @param neonStylesheet path of the demo's Neon menu stylesheet
     */
    private VisualTest(Driver driver, Path neonStylesheet) {
        this.driver = driver;
        this.neonStylesheet = neonStylesheet;
    }

    /**
     * Runs every scenario, prints the report and exits.
     *
     * @param args output folder, then the path of the Neon stylesheet
     * @throws Exception if the test cannot even start
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: VisualTest <screenshot folder> <neon-menu.css>");
            System.exit(2);
        }
        Driver driver = new Driver(Path.of(args[0]));
        driver.start();
        VisualTest test = new VisualTest(driver, Path.of(args[1]));
        try {
            test.runAll();
        } catch (Throwable t) {
            t.printStackTrace();
            driver.check("scenario ran to completion", false, t);
        } finally {
            test.cleanUp();
        }
        System.out.println("==== RESULTS ====");
        driver.results().forEach(System.out::println);
        System.out.println("==== TRAY EVENTS ====");
        test.events.forEach(System.out::println);
        System.out.println(driver.failures() == 0 ? "ALL PASSED" : driver.failures() + " CHECK(S) FAILED");
        System.out.println("Screenshots: " + Path.of(args[0]).toAbsolutePath());
        Platform.exit();
        System.exit(driver.failures() == 0 ? 0 : 1);
    }

    /**
     * The whole run, in order. Later scenarios assume the state earlier ones
     * leave behind (the menu closed, no notifications open).
     *
     * @throws Exception if a scenario cannot continue
     */
    private void runAll() throws Exception {
        setUp();
        Rectangle2D icon = findIcon();
        if (icon == null) {
            return;
        }
        hoverAndClicks();
        menuBasics();
        menuThemes();
        styleSource();
        keyboard();
        notifications();
        notificationInteraction();
        notificationPlacement();
        iconLifecycle(icon);
    }

    // ---- Set-up -------------------------------------------------------------------------

    /**
     * Opens the application window and creates the tray icon with a menu
     * holding every kind of item.
     */
    private void setUp() {
        driver.run(() -> {
            app = new Stage();
            app.setTitle("Juxtray visual test");
            Scene scene = new Scene(new StackPane(new Label("Juxtray visual test (don't touch the mouse)")), 520, 300);
            // The application theme the tray follows through styleSource.
            scene.getStylesheets().add(css(".root.red-theme .context-menu { -tray-background: #601010; "
                    + "-tray-text: white; }"));
            app.setScene(scene);
            app.setX(80);
            app.setY(80);
            app.show();
            for (Screen screen : Screen.getScreens()) {
                log("screen " + screen.getBounds() + " visual " + screen.getVisualBounds()
                        + " scale " + screen.getOutputScaleX());
            }

            MenuItem open = new MenuItem("Open window");
            open.setOnAction(_ -> chosen = "open");
            checkItem = new CheckMenuItem("Check me");
            ToggleGroup group = new ToggleGroup();
            RadioMenuItem radioA = new RadioMenuItem("Radio A");
            radioA.setToggleGroup(group);
            radioA.setSelected(true);
            RadioMenuItem radioB = new RadioMenuItem("Radio B");
            radioB.setToggleGroup(group);
            submenu = new Menu("More", null, new MenuItem("Sub one"), new MenuItem("Sub two"), radioA, radioB);
            MenuItem disabled = new MenuItem("Disabled item");
            disabled.setDisable(true);
            menu = new ContextMenu(open, checkItem, new SeparatorMenuItem(), submenu, disabled,
                    new SeparatorMenuItem(), new MenuItem("Exit"));

            tray = new TrayIcon(makeIcon(), "Juxtray visual test", menu);
            tray.setOnAction(_ -> actions.incrementAndGet());
            tray.setOnTrayEvent(event -> log(describe(event)));
            // A timeline of the menu and its host's focus, so a menu that closes on its own can be
            // traced to what closed it (focus loss, auto-hide, ...).
            menu.showingProperty().addListener((_, _, showing) -> log("menu showing=" + showing));
            // Keep the run quiet; sounds are not something a screenshot can check.
            tray.setUseDefaultSounds(false);
        });
        Stage menuHost = driver.call(() -> TrayProbe.menuHostStage(tray));
        driver.run(() -> menuHost.focusedProperty().addListener((_, _, focused) ->
                log("menu host focused=" + focused)));
        Driver.pause(1500);
    }

    /**
     * Adds a line to the event log, stamped with the time since the start.
     *
     * @param text what happened
     */
    private void log(String text) {
        events.add(String.format("%7d ms  %s", System.currentTimeMillis() - startMillis, text));
    }

    /**
     * Locates the icon and works out whether it sits in the overflow flyout.
     *
     * @return the bounds the shell reported first, or {@code null} if the
     *         icon cannot be found (the run stops then)
     * @throws ReflectiveOperationException if the internals changed
     */
    private Rectangle2D findIcon() throws ReflectiveOperationException {
        Rectangle2D icon = iconBounds();
        driver.check("icon bounds available", icon != null && icon.getWidth() > 0, icon);
        if (icon == null) {
            driver.shot("00-taskbar", trayCorner(900, 120));
            return null;
        }
        // In the closed overflow the shell reports the chevron; hovering it gives no Enter.
        Point2D center = center(icon);
        driver.move(new Point2D(center.getX(), center.getY() - 200));
        Driver.pause(300);
        events.clear();
        driver.move(center);
        Driver.pause(1200);
        if (events.stream().noneMatch(event -> event.contains("  Enter"))) {
            chevron = icon;
            log("icon is in the overflow; chevron at " + chevron);
            driver.shot("00-chevron", around(icon, 250, 120));
            pointAtIcon();
            Rectangle2D inFlyout = iconBounds();
            driver.check("icon has its own bounds once the flyout opens", !chevron.equals(inFlyout), inFlyout);
            driver.shot("01-icon-in-flyout", around(inFlyout, 200, 200));
        } else {
            driver.shot("01-icon", around(icon, 120, 30));
        }
        return icon;
    }

    // ---- Icon and mouse -----------------------------------------------------------------

    /**
     * Hover (tooltip, paired enter/exit, exact bounds) and both clicks.
     *
     * @throws ReflectiveOperationException if the internals changed
     */
    private void hoverAndClicks() throws ReflectiveOperationException {
        // Start off the icon (the flyout stays open), then come back onto it.
        Point2D icon = pointAtIcon();
        driver.move(new Point2D(icon.getX(), icon.getY() - 400));
        Driver.pause(500);
        events.clear();
        icon = pointAtIcon();
        Rectangle2D bounds = iconBounds();
        Driver.pause(2000);
        driver.shot("02-tooltip", around(bounds, 250, 150));
        Optional<String> enter = events.stream().filter(event -> event.contains("  Enter")).findFirst();
        driver.check("hover sends Enter", enter.isPresent(), null);
        String expected = "bounds=" + bounds;
        driver.check("event bounds are the icon's bounds", enter.orElse("").contains(expected),
                enter.orElse("") + " vs " + bounds);

        driver.move(new Point2D(icon.getX(), icon.getY() - 400));
        Driver.pause(600);
        long enters = events.stream().filter(event -> event.contains("  Enter")).count();
        long exits = events.stream().filter(event -> event.contains("  Exit")).count();
        driver.check("leaving sends Exit, paired with Enter", enters == 1 && exits == 1, enters + "/" + exits);

        pointAtIcon();
        driver.click(MouseButton.PRIMARY);
        Driver.pause(600);
        driver.check("left click runs onAction once", actions.get() == 1, "actions=" + actions.get());
        driver.check("left click does not open the menu", !menuShowing(), null);
    }

    // ---- Menu ---------------------------------------------------------------------------

    /**
     * Opening, placement, submenu, check item, and every way of closing.
     *
     * @throws ReflectiveOperationException if the internals changed
     */
    private void menuBasics() throws ReflectiveOperationException {
        Point2D icon = openMenu();
        driver.check("right click opens the menu", menuShowing(), null);
        if (!menuShowing()) {
            throw new IllegalStateException("The menu did not open; later scenarios need it");
        }
        Rectangle2D workArea = driver.call(() -> Screen.getPrimary().getVisualBounds());
        Rectangle2D content = driver.call(() -> screenBounds(menu.getSkin().getNode()));
        driver.check("menu stays inside the work area", content.getMinY() >= workArea.getMinY()
                && content.getMaxY() <= workArea.getMaxY() + 0.5 && content.getMaxX() <= workArea.getMaxX() + 0.5,
                content);
        // The menu opens above the cursor (or below it when there is no room), touching it.
        driver.check("menu opens at the cursor", Math.abs(content.getMaxY() - icon.getY()) < 2
                || Math.abs(content.getMinY() - icon.getY()) < 2, "menu " + content + " cursor " + icon);
        long preselected = driver.call(() -> menu.getSkin().getNode().lookupAll(".menu-item").stream()
                .filter(item -> item.getPseudoClassStates().toString().contains("focused")).count());
        driver.check("no item is preselected", preselected == 0, "focused=" + preselected);
        driver.shot("03-menu", around(menuWindow(), 30, 30));

        driver.move(itemPoint(".menu"));
        Driver.pause(900);
        driver.check("hovering a submenu opens it", driver.call(submenu::isShowing), null);
        driver.shot("04-submenu", submenuArea());

        // Escape closes the submenu first, then the menu.
        driver.type(KeyCode.ESCAPE);
        Driver.pause(300);
        if (menuShowing()) {
            driver.type(KeyCode.ESCAPE);
            Driver.pause(400);
        }
        driver.check("Escape closes the menu", !menuShowing(), null);
        Stage menuHost = driver.call(() -> TrayProbe.menuHostStage(tray));
        driver.check("its invisible host stage closes too", !driver.call(menuHost::isShowing), null);

        openMenu();
        driver.check("the menu opens again", menuShowing(), null);
        driver.move(appCenter());
        driver.click(MouseButton.PRIMARY);
        Driver.pause(600);
        driver.check("clicking elsewhere closes the menu", !menuShowing(), null);
        driver.check("and its host stage", !driver.call(menuHost::isShowing), null);

        openMenu();
        driver.move(itemPoint(".check-menu-item"));
        Driver.pause(300);
        driver.check("the menu is still open before choosing an item", menuShowing(), null);
        driver.shot("03b-menu-before-choosing", around(menuWindow(), 30, 30));
        driver.click(MouseButton.PRIMARY);
        Driver.pause(600);
        driver.check("clicking a check item toggles it", driver.call(checkItem::isSelected), null);
        driver.check("choosing an item closes the menu", !menuShowing(), null);
    }

    /**
     * The built-in themes, a custom stylesheet, plain Modena and a live change.
     *
     * @throws ReflectiveOperationException if the internals changed
     */
    private void menuThemes() throws ReflectiveOperationException {
        driver.run(() -> tray.setColorScheme(ColorScheme.DARK));
        menuShot("05-menu-dark");
        driver.run(() -> tray.setColorScheme(ColorScheme.LIGHT));
        menuShot("06-menu-light");
        driver.run(() -> tray.getStylesheets().add(neonStylesheet.toUri().toString()));
        menuShot("07-menu-neon");
        driver.run(() -> {
            tray.getStylesheets().clear();
            tray.setUseDefaultTheme(false);
        });
        menuShot("08-menu-modena");
        driver.run(() -> {
            tray.setUseDefaultTheme(true);
            tray.setColorScheme(ColorScheme.LIGHT);
        });

        openMenu();
        String before = menuBackground();
        driver.run(() -> tray.setColorScheme(ColorScheme.DARK));
        Driver.pause(400);
        driver.check("an open menu restyles live", !before.equals(menuBackground()), before + " -> " + menuBackground());
        driver.shot("09-menu-live-dark", around(menuWindow(), 30, 30));
        closeMenu();
        driver.run(() -> tray.setColorScheme(null));
    }

    /**
     * The application style source: its root classes reach the menu and the
     * submenu, live, and are gone again on the next open once removed.
     *
     * @throws ReflectiveOperationException if the internals changed
     */
    private void styleSource() throws ReflectiveOperationException {
        driver.run(() -> {
            tray.setStyleSource(app.getScene());
            app.getScene().getRoot().getStyleClass().add("red-theme");
        });
        openMenu();
        driver.check("an app root class themes the menu", RED.equals(menuBackground()), menuBackground());
        driver.move(itemPoint(".menu"));
        Driver.pause(900);
        String submenuBackground = driver.call(() -> Window.getWindows().stream()
                .filter(window -> window instanceof ContextMenu && window != menu && window.isShowing())
                .map(window -> background(((ContextMenu) window).getSkin().getNode()))
                .findFirst().orElse("none"));
        driver.check("and the submenu", RED.equals(submenuBackground), submenuBackground);
        driver.shot("10-menu-style-source", submenuArea());

        driver.run(() -> app.getScene().getRoot().getStyleClass().remove("red-theme"));
        Driver.pause(400);
        driver.check("removing the class restyles the open menu", !RED.equals(menuBackground()), menuBackground());
        driver.run(() -> app.getScene().getRoot().getStyleClass().add("red-theme"));
        Driver.pause(400);
        driver.check("adding it back restyles it again", RED.equals(menuBackground()), menuBackground());
        closeMenu();

        driver.run(() -> app.getScene().getRoot().getStyleClass().remove("red-theme"));
        openMenu();
        driver.check("after removal, the next open is unthemed", !RED.equals(menuBackground()), menuBackground());
        driver.shot("11-menu-style-source-removed", around(menuWindow(), 30, 30));
        closeMenu();
        driver.run(() -> tray.setStyleSource(null));
    }

    /**
     * {@code showMenu()}/{@code hideMenu()} from code, and the keyboard:
     * Down selects the first item even after the mouse used another one,
     * Enter runs it.
     *
     * @throws ReflectiveOperationException if the internals changed
     */
    private void keyboard() throws ReflectiveOperationException {
        driver.run(() -> tray.showMenu());
        Driver.pause(500);
        driver.check("showMenu() opens the menu", menuShowing(), null);
        driver.run(() -> tray.hideMenu());
        Driver.pause(300);
        driver.check("hideMenu() closes it", !menuShowing(), null);

        openMenu();
        // Park the mouse off the menu, so hover does not move the selection.
        driver.move(new Point2D(app.getX() + 10, app.getY() - 40));
        Driver.pause(200);
        driver.type(KeyCode.DOWN);
        Driver.pause(250);
        driver.shot("12-menu-keyboard", around(menuWindow(), 30, 30));
        driver.type(KeyCode.ENTER);
        Driver.pause(500);
        driver.check("Down + Enter runs the first item", "open".equals(chosen) && !menuShowing(), "chosen=" + chosen);
    }

    // ---- Notifications ------------------------------------------------------------------

    /**
     * One of each type, both themes, and the open limit.
     */
    private void notifications() {
        driver.move(appCenter());
        List<TrayNotification> shown = driver.call(() -> {
            List<TrayNotification> list = new ArrayList<>();
            tray.setMaxNotifications(5);
            for (NotificationType type : NotificationType.values()) {
                TrayNotification notification = new TrayNotification(type + " title", "Message for a "
                        + type.name().toLowerCase() + " notification that is long enough to wrap onto a second line.",
                        type);
                notification.setDuration(Duration.INDEFINITE);
                list.add(notification);
                tray.showNotification(notification);
            }
            return list;
        });
        Driver.pause(1200);
        driver.shot("13-notifications", trayCorner(520, 900));
        driver.check("five notifications show", driver.call(() -> shown.stream().allMatch(TrayNotification::isShowing)),
                null);
        driver.run(() -> tray.setColorScheme(ColorScheme.DARK));
        Driver.pause(400);
        driver.shot("14-notifications-dark", trayCorner(520, 900));

        driver.run(() -> {
            tray.setColorScheme(null);
            tray.setMaxNotifications(3);
            tray.showNotification("Sixth", "Pushes out the oldest ones");
        });
        Driver.pause(1000);
        long stillShowing = driver.call(() -> shown.stream().filter(TrayNotification::isShowing).count());
        driver.check("the open limit closes the oldest", stillShowing == 2, "showing=" + stillShowing);
        driver.shot("15-notifications-limit", trayCorner(520, 700));
        driver.run(() -> tray.hideNotifications());
        Driver.pause(800);
        driver.run(() -> tray.setMaxNotifications(4));
    }

    /**
     * Action buttons, hover-pause, live updates, re-showing while fading out,
     * body click and close button.
     *
     * @throws ReflectiveOperationException if the internals changed
     */
    private void notificationInteraction() throws ReflectiveOperationException {
        AtomicInteger installs = new AtomicInteger();
        TrayNotification update = driver.call(() -> {
            TrayNotification notification = new TrayNotification("Update ready", "Version 2.0 can be installed now.",
                    NotificationType.INFO);
            Button install = new Button("Install");
            install.setOnAction(_ -> installs.incrementAndGet());
            notification.getActions().addAll(install, new Button("Later"));
            notification.setDuration(Duration.INDEFINITE);
            tray.showNotification(notification);
            return notification;
        });
        Driver.pause(900);
        driver.move(driver.call(() -> center(screenBounds(update.getActions().getFirst()))));
        Driver.pause(300);
        driver.shot("16-notification-actions", trayCorner(520, 400));
        driver.click(MouseButton.PRIMARY);
        Driver.pause(800);
        driver.check("an action button runs its handler", installs.get() == 1, "installs=" + installs.get());
        driver.check("and closes the notification", !driver.call(update::isShowing), null);

        TrayNotification timed = driver.call(() -> {
            TrayNotification notification = new TrayNotification("Hover me", "The countdown pauses while hovered.");
            notification.setDuration(Duration.seconds(2));
            tray.showNotification(notification);
            return notification;
        });
        Driver.pause(700);
        driver.move(driver.call(() -> center(screenBounds(notificationNode("Hover me", ".tray-notification")))));
        Driver.pause(3000);
        driver.shot("17-notification-paused", trayCorner(520, 300));
        driver.check("a hovered notification outlives its duration", driver.call(timed::isShowing), null);
        driver.move(appCenter());
        Driver.pause(3200);
        driver.check("it closes once the mouse leaves", !driver.call(timed::isShowing), null);

        TrayNotification progress = driver.call(() -> {
            TrayNotification notification = new TrayNotification("Downloading", "0 %");
            notification.setDuration(Duration.INDEFINITE);
            tray.getStylesheets().add(css(".tray-notification.loud { -tray-background: #fff3b0; }"));
            tray.showNotification(notification);
            return notification;
        });
        Driver.pause(800);
        driver.run(() -> {
            progress.setMessage("75 % - almost there; this message is longer now and wraps onto another line");
            progress.setType(NotificationType.SUCCESS);
            progress.getStyleClass().add("loud");
        });
        Driver.pause(700);
        driver.shot("18-notification-live-update", trayCorner(520, 300));
        driver.check("style classes apply live", driver.call(() ->
                notificationNode("Downloading", ".tray-notification").getStyleClass().contains("loud")), null);

        List<String> sequence = new CopyOnWriteArrayList<>();
        driver.run(() -> {
            progress.setOnShown(_ -> sequence.add("shown"));
            progress.setOnHidden(_ -> sequence.add("hidden"));
            progress.hide();
        });
        // Shown again while its exit animation still plays.
        Driver.pause(60);
        driver.run(() -> tray.showNotification(progress));
        Driver.pause(1000);
        driver.check("re-shown while fading out, it stays", driver.call(progress::isShowing), "sequence=" + sequence);
        driver.check("onHidden runs before onShown", sequence.equals(List.of("hidden", "shown")), sequence);
        driver.run(progress::hide);
        Driver.pause(600);
        driver.check("hide() still works afterwards", !driver.call(progress::isShowing), null);
        driver.run(() -> tray.getStylesheets().clear());

        AtomicInteger bodyClicks = new AtomicInteger();
        TrayNotification clickable = driver.call(() -> {
            TrayNotification notification = new TrayNotification("Click me", "Clicking the body runs onAction.");
            notification.setOnAction(_ -> bodyClicks.incrementAndGet());
            notification.setDuration(Duration.INDEFINITE);
            tray.showNotification(notification);
            return notification;
        });
        Driver.pause(900);
        Rectangle2D message = driver.call(() -> screenBounds(notificationNode("Click me", ".notification-message")));
        driver.move(new Point2D(message.getMinX() + 10, message.getMinY() + message.getHeight() / 2));
        Driver.pause(300);
        driver.click(MouseButton.PRIMARY);
        Driver.pause(800);
        driver.check("clicking the body runs onAction and closes it",
                bodyClicks.get() == 1 && !driver.call(clickable::isShowing), "clicks=" + bodyClicks.get());

        TrayNotification closable = driver.call(() -> {
            TrayNotification notification = new TrayNotification("Close me", "Uses the X button.");
            notification.setDuration(Duration.INDEFINITE);
            tray.showNotification(notification);
            return notification;
        });
        Driver.pause(900);
        driver.move(driver.call(() -> center(screenBounds(notificationNode("Close me", ".notification-close")))));
        Driver.pause(300);
        driver.shot("19-notification-close-button", trayCorner(520, 300));
        driver.click(MouseButton.PRIMARY);
        Driver.pause(800);
        driver.check("the close button closes it", !driver.call(closable::isShowing), null);
        Stage notificationHost = driver.call(() -> TrayProbe.notificationHostStage(tray));
        driver.check("the host stage closes with the last notification",
                !driver.call(notificationHost::isShowing), null);
    }

    /**
     * Positions, animations, two tray icons sharing a corner, and focus.
     */
    private void notificationPlacement() {
        Rectangle2D screen = driver.call(() -> Screen.getPrimary().getBounds());
        driver.run(() -> {
            tray.setNotificationPosition(Pos.TOP_RIGHT);
            tray.setNotificationAnimation(NotificationAnimation.ZOOM);
            tray.showNotification("Top right", "Zoom animation", NotificationType.WARNING);
        });
        Driver.pause(900);
        driver.shot("20-notification-top-right", new Rectangle2D(screen.getMaxX() - 520, screen.getMinY(), 520, 260));
        driver.run(() -> {
            tray.setNotificationPosition(Pos.BOTTOM_CENTER);
            tray.setNotificationAnimation(NotificationAnimation.FADE);
            tray.showNotification("Bottom centre", "Fade animation; the top-right one glides here.",
                    NotificationType.ERROR);
        });
        Driver.pause(1200);
        driver.shot("21-notification-bottom-center",
                new Rectangle2D(screen.getMinX() + screen.getWidth() / 2 - 300, screen.getMaxY() - 450, 600, 450));
        driver.run(() -> {
            tray.hideNotifications();
            tray.setNotificationPosition(null);
            tray.setNotificationAnimation(null);
        });
        Driver.pause(800);

        TrayIcon second = driver.call(() -> {
            TrayIcon icon = new TrayIcon(makeIcon(), "Second icon");
            icon.setUseDefaultSounds(false);
            for (TrayIcon owner : List.of(tray, icon)) {
                TrayNotification notification = new TrayNotification(owner == tray ? "From icon 1" : "From icon 2",
                        owner == tray ? "First tray icon" : "Second tray icon");
                notification.setDuration(Duration.INDEFINITE);
                owner.showNotification(notification);
            }
            return icon;
        });
        Driver.pause(1000);
        driver.shot("22-two-icons", trayCorner(520, 400));
        List<Rectangle2D> boxes = driver.call(() -> showingNotifications().stream()
                .map(root -> screenBounds(root.lookup(".tray-notification"))).toList());
        driver.check("two icons' notifications do not overlap",
                boxes.size() == 2 && !boxes.getFirst().intersects(boxes.getLast()), boxes);
        driver.run(() -> {
            tray.hideNotifications();
            second.hideNotifications();
        });
        Driver.pause(700);
        driver.run(second::close);

        driver.run(() -> {
            app.toFront();
            app.requestFocus();
        });
        Driver.pause(400);
        driver.run(() -> tray.showNotification("Focus", "Should not steal the focus"));
        Driver.pause(800);
        driver.check("a notification does not steal the focus", driver.call(app::isFocused), null);
        driver.run(() -> tray.hideNotifications());
        Driver.pause(600);
    }

    // ---- Icon lifecycle -----------------------------------------------------------------

    /**
     * Hide and show, a failing background image, and close.
     *
     * @param icon where the icon (or the overflow chevron) was at the start
     * @throws ReflectiveOperationException if the internals changed
     */
    private void iconLifecycle(Rectangle2D icon) throws ReflectiveOperationException {
        driver.run(() -> {
            tray.setTooltip("Changed tooltip");
            tray.setVisible(false);
        });
        Driver.pause(800);
        driver.shot("23-icon-hidden", around(icon, 120, 30));
        driver.run(() -> tray.setVisible(true));
        Driver.pause(800);
        driver.check("the icon shows again", iconBounds() != null, null);

        // Must only log a warning; the icon keeps its previous image.
        driver.run(() -> tray.setImage(new Image("file:///does/not/exist.png", true)));
        Driver.pause(800);
        driver.check("a failing image keeps the icon alive", !driver.call(tray::isClosed) && iconBounds() != null,
                null);
        driver.run(() -> tray.setImage(makeIcon()));
        Driver.pause(500);

        driver.run(() -> tray.close());
        Driver.pause(800);
        driver.check("close() removes the icon", iconBounds() == null, null);
        driver.shot("24-after-close", around(icon, 120, 30));
    }

    /**
     * Closes whatever is left, so an aborted run leaves no icon behind.
     */
    private void cleanUp() {
        try {
            driver.run(() -> {
                if (tray != null && !tray.isClosed()) {
                    tray.close();
                }
                if (app != null) {
                    app.close();
                }
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    // ---- Helpers ------------------------------------------------------------------------

    /**
     * Puts the cursor on the icon. When the icon lives in the closed
     * overflow flyout, clicks the chevron first to open it.
     *
     * @return where the cursor now is
     * @throws ReflectiveOperationException if the internals changed
     */
    private Point2D pointAtIcon() throws ReflectiveOperationException {
        Rectangle2D bounds = iconBounds();
        // The flyout opens with an animation and may ignore a click that comes too early, so
        // wait for the icon to move out of the chevron, and click again if it never does.
        for (int attempt = 0; attempt < 3 && chevron != null && chevron.equals(bounds); attempt++) {
            driver.move(center(chevron));
            Driver.pause(300);
            driver.click(MouseButton.PRIMARY);
            for (int waited = 0; waited < 2000 && chevron.equals(bounds); waited += 100) {
                Driver.pause(100);
                bounds = iconBounds();
            }
        }
        // Let the flyout finish its opening animation before pointing into it.
        Driver.pause(300);
        bounds = iconBounds();
        Point2D point = center(bounds);
        driver.move(point);
        Driver.pause(300);
        return point;
    }

    /**
     * Right-clicks the icon to open the menu.
     *
     * @return where the cursor is
     * @throws ReflectiveOperationException if the internals changed
     */
    private Point2D openMenu() throws ReflectiveOperationException {
        Point2D icon = pointAtIcon();
        driver.click(MouseButton.SECONDARY);
        Driver.pause(800);
        return icon;
    }

    /**
     * Closes the menu with Escape (twice if a submenu was open).
     */
    private void closeMenu() {
        driver.type(KeyCode.ESCAPE);
        Driver.pause(300);
        if (menuShowing()) {
            driver.type(KeyCode.ESCAPE);
            Driver.pause(400);
        }
    }

    /**
     * Opens the menu, checks it opened, screenshots it and closes it.
     *
     * @param name screenshot name
     * @throws ReflectiveOperationException if the internals changed
     */
    private void menuShot(String name) throws ReflectiveOperationException {
        openMenu();
        driver.check(name + " opens", menuShowing(), null);
        driver.shot(name, around(menuWindow(), 30, 30));
        closeMenu();
    }

    /**
     * The icon bounds, read on the JavaFX thread.
     *
     * @return the bounds, or {@code null}
     */
    private Rectangle2D iconBounds() {
        return driver.call(() -> TrayProbe.iconBounds(tray));
    }

    /**
     * Whether the tray menu is open.
     *
     * @return {@code true} while it shows
     */
    private boolean menuShowing() {
        return driver.call(menu::isShowing);
    }

    /**
     * The tray menu's window bounds, including its shadow.
     *
     * @return the bounds
     */
    private Rectangle2D menuWindow() {
        return driver.call(() -> new Rectangle2D(menu.getX(), menu.getY(), menu.getWidth(), menu.getHeight()));
    }

    /**
     * Area around the open menu with room for its submenu to the right.
     *
     * @return the area
     */
    private Rectangle2D submenuArea() {
        Rectangle2D window = menuWindow();
        return new Rectangle2D(window.getMinX() - 30, window.getMinY() - 160, window.getWidth() + 420,
                window.getHeight() + 220);
    }

    /**
     * The open menu's background colour.
     *
     * @return the colour as {@code Color.toString()}, for example {@code 0x601010ff}
     */
    private String menuBackground() {
        return driver.call(() -> background(menu.getSkin().getNode()));
    }

    /**
     * Where to point at the first item matching a selector in the open menu.
     *
     * @param selector CSS selector, such as {@code .menu}
     * @return a point inside the item
     */
    private Point2D itemPoint(String selector) {
        return driver.call(() -> {
            Rectangle2D bounds = screenBounds(menu.getSkin().getNode().lookup(selector));
            return new Point2D(bounds.getMinX() + 30, bounds.getMinY() + bounds.getHeight() / 2);
        });
    }

    /**
     * The middle of the application window: a safe place to click.
     *
     * @return the point
     */
    private Point2D appCenter() {
        return driver.call(() -> new Point2D(app.getX() + app.getWidth() / 2, app.getY() + app.getHeight() / 2));
    }

    /**
     * Roots of the notification popups currently on screen. FX thread only.
     *
     * @return their scene roots
     */
    private static List<Node> showingNotifications() {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Popup && window.isShowing())
                .map(window -> (Node) window.getScene().getRoot())
                .filter(root -> root.lookup(".tray-notification") != null)
                .toList();
    }

    /**
     * Finds a node inside the open notification with a given title. FX thread only.
     *
     * @param title    the notification's title
     * @param selector CSS selector of the node, such as {@code .notification-close}
     * @return the node
     * @throws IllegalStateException if no such notification shows
     */
    private static Node notificationNode(String title, String selector) {
        return showingNotifications().stream()
                .filter(root -> root.lookup(".notification-title") instanceof Label label
                        && title.equals(label.getText()))
                .map(root -> root.lookup(selector))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No notification titled " + title));
    }

    /**
     * A node's first background fill. FX thread only.
     *
     * @param node a region
     * @return the fill as {@code toString()}
     */
    private static String background(Node node) {
        return String.valueOf(((Region) node).getBackground().getFills().getFirst().getFill());
    }

    /**
     * A node's bounds on screen. FX thread only.
     *
     * @param node the node
     * @return its layout bounds in JavaFX screen coordinates
     */
    private static Rectangle2D screenBounds(Node node) {
        Bounds bounds = node.localToScreen(node.getLayoutBounds());
        return new Rectangle2D(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
    }

    /**
     * The centre of a rectangle.
     *
     * @param rect the rectangle
     * @return its centre
     */
    private static Point2D center(Rectangle2D rect) {
        return new Point2D(rect.getMinX() + rect.getWidth() / 2, rect.getMinY() + rect.getHeight() / 2);
    }

    /**
     * A rectangle grown by margins on each side.
     *
     * @param rect       the rectangle
     * @param horizontal margin left and right
     * @param vertical   margin above and below
     * @return the bigger rectangle
     */
    private static Rectangle2D around(Rectangle2D rect, double horizontal, double vertical) {
        return new Rectangle2D(rect.getMinX() - horizontal, rect.getMinY() - vertical,
                rect.getWidth() + 2 * horizontal, rect.getHeight() + 2 * vertical);
    }

    /**
     * The bottom-right corner of the primary screen, where the tray is.
     *
     * @param width  area width
     * @param height area height
     * @return the area
     */
    private Rectangle2D trayCorner(double width, double height) {
        Rectangle2D screen = driver.call(() -> Screen.getPrimary().getBounds());
        return new Rectangle2D(screen.getMaxX() - width, screen.getMaxY() - height, width, height);
    }

    /**
     * Turns CSS text into a {@code data:} URL, usable as a stylesheet.
     *
     * @param text the CSS
     * @return the URL
     */
    private static String css(String text) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Draws the test icon: a blue ball with a white "J", 256 px. FX thread only.
     *
     * @return the image
     */
    private static Image makeIcon() {
        Canvas canvas = new Canvas(256, 256);
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setFill(new RadialGradient(0, 0, 0.35, 0.3, 0.8, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#8be9fd")), new Stop(1, Color.web("#3b2fd6"))));
        graphics.fillOval(8, 8, 240, 240);
        graphics.setFill(Color.WHITE);
        graphics.setFont(Font.font("Segoe UI", 150));
        graphics.fillText("J", 92, 185);
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return canvas.snapshot(parameters, null);
    }

    /**
     * One line of text for the event log.
     *
     * @param event the tray event
     * @return its description
     */
    private static String describe(TrayIconEvent event) {
        String detail = switch (event) {
            case TrayIconEvent.Click click -> " " + click.button() + " " + click.state();
            case TrayIconEvent.DoubleClick doubleClick -> " " + doubleClick.button();
            default -> "";
        };
        return event.getClass().getSimpleName() + detail + " pos=" + event.position() + " bounds=" + event.bounds();
    }
}
