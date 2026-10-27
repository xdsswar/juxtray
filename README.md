# Juxtray

**System tray icons for JavaFX: native where it must be, pure Java everywhere else.**

Built by **XTREME SOFTWARE SOLUTIONS**. Free and open source under the [MIT License](LICENSE): use it however you see fit, commercial or not.

Juxtray puts your JavaFX app in the system tray with a crisp, DPI-aware icon and a context menu that is a *real* JavaFX `ContextMenu`. That means you style it with plain JavaFX CSS: gradients, shadows, radii, fonts, pseudo-classes, custom items, anything.

It is written in pure Java on the **Foreign Function & Memory API**. It has **no JNI and no bundled native libraries**, and nothing native to compile. The only native calls go straight into DLLs that ship with Windows.

![License: MIT](https://img.shields.io/badge/License-MIT-green) ![Java 25](https://img.shields.io/badge/Java-25-orange) ![JavaFX 25](https://img.shields.io/badge/JavaFX-25-blue) ![Gradle 9](https://img.shields.io/badge/Gradle-9-02303A) ![Windows 10/11](https://img.shields.io/badge/Windows-10%20%7C%2011-0078D4)

> [!IMPORTANT]
> **Windows only for now.** Juxtray currently runs on **Windows 10 (1607+) and Windows 11**. macOS and Linux back ends are planned (see [Roadmap](#roadmap)). On other systems `Juxtray.isSupported()` returns `false` and creating a `TrayIcon` throws `TrayException`.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/menu-light.png" width="383" alt="Built-in light theme"><br><sub><b>Built-in · light</b></sub></td>
    <td align="center"><img src="docs/screenshots/menu-dark.png" width="383" alt="Built-in dark theme"><br><sub><b>Built-in · dark</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/menu-neon.png" width="413" alt="Neon custom CSS theme"><br><sub><b>Custom CSS · Neon</b></sub></td>
    <td align="center"><img src="docs/screenshots/menu-terminal.png" width="396" alt="Terminal custom CSS theme"><br><sub><b>Custom CSS · Terminal</b></sub></td>
  </tr>
</table>

<sub>Every image is the <b>same</b> JavaFX <code>ContextMenu</code> from the demo, with only the stylesheets changed. The screenshots are rendered by the demo itself (<code>--screenshots</code>), not mocked up.</sub>

---

## Contents

- [Features](#features)
- [CSS showcase](#css-showcase)
- [Platform support](#platform-support)
- [Requirements](#requirements)
- [Adding Juxtray to your project](#adding-juxtray-to-your-project)
- [Quick start](#quick-start)
- [The tray icon](#the-tray-icon)
- [Mouse events](#mouse-events)
- [The menu](#the-menu)
- [Styling the menu with CSS](#styling-the-menu-with-css)
- [Icons and HiDPI](#icons-and-hidpi)
- [Threading and lifecycle](#threading-and-lifecycle)
- [API reference](#api-reference)
- [How it works](#how-it-works)
- [Demo](#demo)
- [Building from source](#building-from-source)
- [Roadmap](#roadmap)
- [License](#license)

---

## Features

**Tray icon**
- Any JavaFX `Image` as the icon, including images still loading in the background.
- Resampled in pure Java with a high-quality, premultiplied area-average filter, so the icon has no dark fringes or jaggies.
- **Per-monitor DPI aware:** sized for the scale of the monitor that actually shows the icon (16 px at 100 %, 24 px at 150 %, ...). It re-renders automatically when scaling or the monitor layout changes.
- Tooltip, show/hide without destroying, and live updates through JavaFX properties.
- Survives **Explorer restarts**: the icon re-adds itself when the taskbar comes back, even for elevated apps.
- No ghost icons: a shutdown hook removes icons the app forgot to close.

**Menu**
- A real JavaFX `ContextMenu`: `MenuItem`, `CheckMenuItem`, `RadioMenuItem`, `Menu` (submenus), `SeparatorMenuItem`, `CustomMenuItem`, item graphics, accelerator text, disabled items.
- **Fully CSS-stylable**, exactly like any JavaFX menu. Stylesheets are scoped to the tray, so they never leak into your app (or the other way round).
- **Built-in Windows 11-style theme** that follows the OS **light/dark** setting (or is forced either way with `setMenuColorScheme`), with a colour palette exposed as CSS variables for two-line rethemes.
- **Native-like placement:** above a bottom taskbar, below a top one, beside a side one. It flips at screen edges, stays within the work area, works on every monitor (including negative coordinates), and is correct on mixed-DPI setups.
- Closes like a native tray menu: on item click, Escape, or a click anywhere else.
- Opens on right click (default), optionally on left click, or programmatically with `showMenu()`.

**Events**
- `onAction` for the classic "left click opens my window".
- `onTrayEvent` for everything: press/release per button, double-click, enter, move, exit. Each event carries the cursor position and icon bounds in JavaFX screen coordinates.
- Sealed record types, made for `switch` pattern matching.

**Engineering**
- JPMS module `xss.it.juxtray`. It exports only the public API; internals stay hidden.
- No dependencies besides JavaFX.
- FFM entry points are confined to two classes. Upcalls are crash-proof (they never let an exception reach native code).

---

## CSS showcase

The tray menu is a plain JavaFX `ContextMenu`, so **every JavaFX CSS feature works**. The demo builds *one* menu, and each look below comes from stylesheets alone. Every shot shows the same things:
- a **check item** (`.check-menu-item`) and a **submenu** (`.menu`) opened on hover;
- **radio items** (`.radio-menu-item`) with **graphics** (coloured `Circle`s styled through their own class `.status-dot`);
- a **disabled item** (`:disabled`) and **separators** (`.separator`);
- an Exit item with an **app-specific style class** (`.danger`);
- **hover** (`:focused`) on the open row and on a submenu item.

### Built-in theme: light & dark

<img src="docs/screenshots/menu-light.png" width="383" alt="Built-in light"> <img src="docs/screenshots/menu-dark.png" width="383" alt="Built-in dark">

Zero CSS needed. It follows Windows' light/dark setting live, or you can pin it:

```java
tray.setMenuColorScheme(ColorScheme.DARK);   // or LIGHT, or null to follow the OS
```

Your own classes still mix in. This is the demo's [`demo-menu.css`](demo/src/main/resources/xss/it/juxtray/demo/demo-menu.css), applied in every shot:

```css
.context-menu .menu-item.danger .label   { -fx-text-fill: #e5484d; }
.context-menu .menu-item.danger:focused  { -fx-background-color: rgba(229, 72, 77, 0.14); }
.context-menu .status-dot                { -fx-stroke: rgba(0, 0, 0, 0.15); }
```

```java
exit.getStyleClass().add("danger");
```

### Neon: retheme through variables, then go further

<img src="docs/screenshots/menu-neon.png" width="413" alt="Neon theme">

It starts by swapping the built-in palette variables, then adds what native menus can't do: a gradient background, a glowing border, gradient hover and glowing text. Full file: [`neon-menu.css`](demo/src/main/resources/xss/it/juxtray/demo/neon-menu.css).

```css
.context-menu {
    -tray-background: #140f2e;      /* built-in palette variables… */
    -tray-text: #e9d5ff;
    -tray-mark: #f0abfc;
    -tray-shadow: rgba(124, 58, 237, 0.55);

    -fx-background-color: linear-gradient(to bottom right, #1e1446, #0b0820);   /* …plus anything else */
    -fx-background-radius: 12;
    -fx-border-color: #7c3aed;
    -fx-border-radius: 12;
}

.context-menu .menu-item:focused {
    -fx-background-color: linear-gradient(to right, rgba(124, 58, 237, 0.55), rgba(236, 72, 153, 0.35));
}

.context-menu .menu-item:focused .label {
    -fx-text-fill: white;
    -fx-effect: dropshadow(gaussian, #f0abfc, 8, 0.3, 0, 0);
}

.context-menu .radio-menu-item:checked .label { -fx-font-weight: bold; }
```

### Terminal: a completely different look

<img src="docs/screenshots/menu-terminal.png" width="396" alt="Terminal theme">

Square corners, a monospace font, inverted selection, dashed separators and a phosphor glow. Full file: [`terminal-menu.css`](demo/src/main/resources/xss/it/juxtray/demo/terminal-menu.css).

```css
.context-menu {
    -tray-background: #0a0f0a;
    -tray-text: #33ff66;
    -tray-hover: #33ff66;           /* the selection is a solid green bar… */
    -fx-background-radius: 0;
    -fx-border-radius: 0;
    -fx-font-family: "Consolas";
    -fx-effect: dropshadow(gaussian, rgba(51, 255, 102, 0.35), 14, 0.2, 0, 0);
}

.context-menu .menu-item:focused .label,
.context-menu .menu:showing .label { -fx-text-fill: #0a0f0a; }   /* …with dark text on it */

.context-menu .separator-menu-item .line { -fx-border-style: dashed; }
```

### Plain JavaFX (Modena)

<img src="docs/screenshots/menu-modena.png" width="315" alt="Modena">

Turn the built-in theme off and you get stock JavaFX. Your own rules (like `.danger`) still apply:

```java
tray.setUseDefaultTheme(false);
```

---

## Platform support

| Platform | Status | Native strategy |
|---|---|---|
| **Windows 10 (1607+) / 11** | ✅ Implemented | FFM → `shell32`, `user32`, `shcore`, `kernel32` |
| macOS | 🗓️ Planned | FFM → Objective-C runtime / AppKit |
| Linux / BSD | 🗓️ Planned | **Pure Java** D-Bus (StatusNotifierItem), no native calls at all |

Use `Juxtray.isSupported()` to check at runtime.

---

## Requirements

- **JDK 25+**
- **JavaFX 25+**
- **Windows 10 version 1607 or later, or Windows 11.** Windows is the only supported OS for now.
- The JVM option **`--enable-native-access=xss.it.juxtray`** (FFM calls are restricted by default)

---

## Adding Juxtray to your project

Juxtray is distributed as source, not through Maven. Pick one of the following:

**Option 1: as a subproject (recommended).** Copy (or `git submodule add`) the `juxtray/` folder into your build, then:

```groovy
// settings.gradle
include 'juxtray'
```

```groovy
// your app's build.gradle
dependencies {
    implementation project(':juxtray')
}
```

The library's own `build.gradle` brings JavaFX (`base`, `graphics`, `controls`) in for you. The demo in this repository is wired exactly this way.

**Option 2: as a jar.** Build it with `./gradlew :juxtray:jar` and put `juxtray/build/libs/juxtray-<version>.jar` on your module path, next to the JavaFX 25 modules.

Then, either way, add this to your `module-info.java`:

```java
module my.app {
    requires xss.it.juxtray;   // also gives you javafx.controls transitively
}
```

And grant native access when launching (Gradle `application` plugin shown):

```groovy
application {
    applicationDefaultJvmArgs = ['--enable-native-access=xss.it.juxtray,javafx.graphics']
}
```

(`javafx.graphics` is optional; it only silences JavaFX's own native-loading warning.)

---

## Quick start

```java
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import xss.it.juxtray.TrayIcon;

public final class MyApp extends Application {

    private TrayIcon tray;

    @Override
    public void start(Stage stage) {
        // A tray app keeps running when its windows are closed.
        Platform.setImplicitExit(false);

        MenuItem open = new MenuItem("Open");
        open.setOnAction(e -> stage.show());

        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> {
            tray.close();
            Platform.exit();
        });

        tray = new TrayIcon(
                new Image(getClass().getResource("icon-256.png").toExternalForm()),
                "My App",
                new ContextMenu(open, new SeparatorMenuItem(), exit));

        // Left click: bring the window back. Right click opens the menu by default.
        tray.setOnAction(e -> stage.show());
    }
}
```

---

## The tray icon

`TrayIcon` follows JavaFX conventions: constructors plus observable properties.

```java
TrayIcon tray = new TrayIcon();                              // empty slot
TrayIcon tray = new TrayIcon(image);
TrayIcon tray = new TrayIcon(image, "Tooltip");
TrayIcon tray = new TrayIcon(image, "Tooltip", contextMenu);
```

The icon appears **as soon as it is constructed** and stays until `close()` (or JVM exit).

```java
tray.setImage(otherImage);          // swap the icon (e.g. status changes)
tray.setTooltip("Syncing… 42%");    // Windows shows up to 127 characters
tray.setVisible(false);             // hide without destroying; setVisible(true) brings it back
tray.close();                       // remove for good and free native resources
```

Every setting is a property, so it binds:

```java
tray.tooltipProperty().bind(statusText);
visibleCheckBox.selectedProperty().bindBidirectional(tray.visibleProperty());
```

---

## Mouse events

### Simple: `onAction`

Fired when the **primary (left)** button is released over the icon:

```java
tray.setOnAction(e -> stage.show());
```

On a double-click it fires for each of the two clicks.

### Everything: `onTrayEvent`

```java
tray.setOnTrayEvent(event -> {
    switch (event) {
        case TrayIconEvent.Click c when c.state() == ButtonState.RELEASED
                && c.button() == MouseButton.MIDDLE -> toggleMute();
        case TrayIconEvent.DoubleClick d -> openMainWindow();
        case TrayIconEvent.Enter e -> showPreviewPopup(e.bounds());
        case TrayIconEvent.Exit e -> hidePreviewPopup();
        default -> { }
    }
});
```

| Event | Fields | When |
|---|---|---|
| `Click` | `button`, `state` (`PRESSED` / `RELEASED`), `position`, `bounds` | any button goes down or up |
| `DoubleClick` | `button`, `position`, `bounds` | double-click (the two `Click`s are reported too) |
| `Enter` | `position`, `bounds` | cursor entered the icon |
| `Move` | `position`, `bounds` | cursor moved over the icon (duplicates filtered) |
| `Exit` | `position`, `bounds` | cursor left the icon |

All events have `source()` (the `TrayIcon`). They are delivered **on the JavaFX Application Thread**.

- `position` is the cursor in **JavaFX screen coordinates**, so you can use it directly with `stage.setX/Y`.
- `bounds` is the icon's rectangle in the same space. It is `Rectangle2D.EMPTY` when Windows can't tell, for example while the icon is in the collapsed overflow area.

---

## The menu

Pass any `ContextMenu`:

```java
CheckMenuItem notifications = new CheckMenuItem("Notifications");

Menu status = new Menu("Status");
ToggleGroup group = new ToggleGroup();
for (String s : List.of("Online", "Away", "Busy")) {
    RadioMenuItem item = new RadioMenuItem(s);
    item.setToggleGroup(group);
    status.getItems().add(item);
}

MenuItem quit = new MenuItem("Quit", new ImageView(powerIcon));   // graphics work too

tray.setContextMenu(new ContextMenu(notifications, status, new SeparatorMenuItem(), quit));
```

When the menu opens:

```java
tray.setMenuOnSecondaryClick(true);   // right click: default true
tray.setMenuOnPrimaryClick(true);     // left click:  default false
tray.showMenu();                      // open at the cursor programmatically
tray.hideMenu();
```

**Placement** mimics native tray menus. The menu opens above the cursor, starting at it, and is clamped into the screen's work area. On a bottom taskbar it sits just above the taskbar; on top or side taskbars it opens below or beside. It flips left at the right edge of the screen and works on any monitor at any scale.

**Closing:** choosing an item, pressing Escape, or clicking anywhere outside the menu closes it, like a native menu.

---

## Styling the menu with CSS

The menu is a standard JavaFX `ContextMenu`, so **all of JavaFX CSS applies**.

### Adding stylesheets

```java
tray.getStylesheets().add(getClass().getResource("tray.css").toExternalForm());
```

`TrayIcon.getStylesheets()` works like `Scene.getStylesheets()`, but it is **scoped to the tray menu**:

- Your application's scene stylesheets do **not** affect the tray menu.
- Tray stylesheets do **not** leak into your windows.
- Changes apply the next time the menu opens.

### Selectors

| Selector | Targets |
|---|---|
| `.context-menu` | the menu container (also every submenu) |
| `.context-menu.tray-menu` | only the root tray menu |
| `.menu-item`, `.menu-item .label` | items and their text |
| `.menu` | items that open submenus; `.menu > .right-container > .arrow` is the arrow |
| `.check-menu-item`, `.radio-menu-item` | toggles; `> .left-container > .check` / `.radio` are the marks |
| `.separator-menu-item .line` | separators |
| `.accelerator-text` | shortcut text |
| `:focused`, `:pressed`, `:disabled`, `:checked`, `:showing` | states |
| `.your-class`, `#your-id` | anything you add with `getStyleClass()` / `setId()` |

Inline styles work too: `menu.setStyle(...)`, `item.setStyle(...)`.

### The built-in theme

A Windows 11-style theme is applied first and follows the OS light/dark setting automatically. Pin it with `tray.setMenuColorScheme(ColorScheme.LIGHT)` or `ColorScheme.DARK`, or pass `null` to follow the OS again. It exposes its palette as **looked-up colours**, so a retheme can be this small:

```css
.context-menu {
    -tray-background: #1e1b4b;
    -tray-text: #e0e7ff;
    -tray-hover: rgba(167, 139, 250, 0.18);
}
```

| Variable | Used for |
|---|---|
| `-tray-background` | menu background |
| `-tray-border` | menu border |
| `-tray-text` | item text |
| `-tray-text-secondary` | accelerator text, submenu arrows |
| `-tray-hover` | focused item / open submenu background |
| `-tray-pressed` | pressed item background |
| `-tray-separator` | separator line |
| `-tray-mark` | check and radio marks |
| `-tray-shadow` | drop shadow colour |

(JavaFX only supports looked-up *colours*; sizes such as radii are set directly.)

To start from scratch on plain Modena instead:

```java
tray.setUseDefaultTheme(false);
```

### A bolder example

```css
.context-menu {
    -fx-background-color: linear-gradient(to bottom right, #1e1446, #0b0820);
    -fx-background-radius: 12;
    -fx-border-color: #7c3aed;
    -fx-border-radius: 12;
    -fx-effect: dropshadow(gaussian, rgba(124, 58, 237, 0.55), 18, 0, 0, 6);
}

.context-menu .menu-item {
    -fx-background-radius: 8;
    -fx-padding: 8 14 8 14;
}

.context-menu .menu-item:focused {
    -fx-background-color: linear-gradient(to right, rgba(124, 58, 237, 0.55), rgba(236, 72, 153, 0.35));
}

.context-menu .menu-item:focused .label {
    -fx-text-fill: white;
    -fx-effect: dropshadow(gaussian, #f0abfc, 8, 0.3, 0, 0);
}

.context-menu .menu-item.danger .label {
    -fx-text-fill: #f87171;
}
```

The full version ships in the demo: [`neon-menu.css`](demo/src/main/resources/xss/it/juxtray/demo/neon-menu.css).

---

## Icons and HiDPI

- Supply a **large, square** image (256×256 is ideal). Juxtray resamples it to the exact pixel size Windows wants, so it looks sharp at every scale.
- Non-square images are fitted and centred on a transparent background.
- Images loading in the background (`new Image(url, true)`) are applied once loaded. Load errors are logged, not thrown.
- Juxtray picks the size from the DPI of the monitor that actually shows the tray, not the "system DPI". After you change display scaling or rearrange monitors, the icon re-renders within about half a second.
- Changing the icon at runtime (status indicators, progress, badges) is cheap: just call `setImage(...)`.

Generating icons in code is easy with a `Canvas` snapshot:

```java
Canvas canvas = new Canvas(256, 256);
GraphicsContext g = canvas.getGraphicsContext2D();
g.setFill(Color.web("#22c55e"));
g.fillRoundRect(8, 8, 240, 240, 72, 72);
SnapshotParameters params = new SnapshotParameters();
params.setFill(Color.TRANSPARENT);
tray.setImage(canvas.snapshot(params, null));
```

---

## Threading and lifecycle

- **Use `TrayIcon` on the JavaFX Application Thread only**, after the JavaFX runtime has started. That covers constructing it, setters, `showMenu`, and `close`. Calling from another thread throws `IllegalStateException`. From a background thread, wrap calls in `Platform.runLater`.
- **Handlers run on the JavaFX Application Thread**, so you can touch the UI directly.
- **Call `Platform.setImplicitExit(false)`** in tray apps. Otherwise JavaFX exits when the last window closes, and an invisible helper window that hosts the menu counts as a window.
- **`close()` removes the icon.** If the JVM exits without it, a shutdown hook removes leftover icons, so no "ghost" icon stays in the taskbar.
- Juxtray never calls `Platform.exit()` itself.

---

## API reference

Package `xss.it.juxtray` (the only exported package):

| Type | Purpose |
|---|---|
| `TrayIcon` | the tray icon: properties, events, menu, lifecycle |
| `TrayIconEvent` | sealed event hierarchy: `Click`, `DoubleClick`, `Enter`, `Move`, `Exit` |
| `ButtonState` | `PRESSED` / `RELEASED` |
| `TrayException` | unchecked; the OS refused an operation, or the platform is unsupported |
| `Juxtray` | `isSupported()` |

`TrayIcon` members:

| Property / method | Type | Default | Notes |
|---|---|---|---|
| `image` | `Image` | `null` | resampled to the tray size |
| `tooltip` | `String` | `null` | max 127 chars shown |
| `visible` | `boolean` | `true` | hide without destroying |
| `contextMenu` | `ContextMenu` | `null` | any JavaFX context menu |
| `menuOnPrimaryClick` | `boolean` | `false` | left click opens the menu |
| `menuOnSecondaryClick` | `boolean` | `true` | right click opens the menu |
| `useDefaultTheme` | `boolean` | `true` | built-in light/dark menu theme |
| `menuColorScheme` | `ColorScheme` | `null` | force the built-in theme light/dark; `null` follows the OS |
| `onAction` | `EventHandler<ActionEvent>` | `null` | primary click released |
| `onTrayEvent` | `Consumer<? super TrayIconEvent>` | `null` | all mouse events |
| `getStylesheets()` | `ObservableList<String>` | empty | menu stylesheets (scoped to the tray) |
| `showMenu()` / `hideMenu()` | | | open at cursor / close |
| `close()` / `isClosed()` | | | remove the icon, free resources |

Full Javadoc: `./gradlew :juxtray:javadoc` → `juxtray/build/docs/javadoc/index.html`.

---

## How it works

```
your app ──► xss.it.juxtray.TrayIcon           (public API, JavaFX properties & events)
                 │
                 ├── MenuPlacement, PixelScaler  pure Java: menu placement, icon resampling
                 ├── MenuHost                    pure JavaFX: hosts & styles the ContextMenu
                 ▼
             NativeTray                          tiny internal SPI per platform
                 ▼
             WinTray / WinTrayHost               Windows back end, FFM only
                 ▼
             shell32 · user32 · shcore · kernel32   (DLLs that ship with Windows)
```

**What is native, exactly?** Only the calls that cannot be avoided. Windows exposes the notification area solely through `Shell_NotifyIconW`, which needs a window to receive callbacks:

| DLL | Functions | Why |
|---|---|---|
| `shell32` | `Shell_NotifyIconW`, `Shell_NotifyIconGetRect` | add/modify/remove the icon, find its position |
| `user32` | `RegisterClassExW`, `CreateWindowExW`, `DefWindowProcW`, `GetMessageW`, `TranslateMessage`, `DispatchMessageW`, `PostMessageW` | hidden callback window + message loop |
| `user32` | `RegisterWindowMessageW`, `ChangeWindowMessageFilterEx` | survive Explorer restarts (also when elevated) |
| `user32` | `CreateIcon`, `DestroyIcon` | pixels → `HICON` |
| `user32` | `GetCursorPos`, `SetTimer`, `KillTimer` | hover enter/leave detection |
| `user32` | `FindWindowW`, `SetForegroundWindow` | make the menu close on outside clicks |
| `user32`, `shcore` | `MonitorFromRect`, `GetDpiForMonitor`, `GetDpiForSystem`, `GetSystemMetricsForDpi`, `SetThreadDpiAwarenessContext` | per-monitor DPI icon sizing |
| `kernel32` | `GetModuleHandleW` | window class registration |

The window procedure is the **single** FFM upcall. Everything else is pure Java: the menu (JavaFX), its placement, CSS theming, image resampling, event translation, coordinate conversion and threading.

A few design notes:
- All icons share **one** hidden window on one daemon thread (`juxtray-win32`), told apart by their `uID`. It is a real (invisible) top-level window, because message-only windows never get the `TaskbarCreated` broadcast.
- Struct layouts (`NOTIFYICONDATAW` is 976 bytes) are spelled out field by field with padding, and unit tests pin their sizes and offsets against the Windows SDK.
- The menu needs an owner window, so Juxtray uses an invisible utility stage that is shown only while the menu is open. JavaFX popups copy their owner scene's stylesheets, which is how tray stylesheets reach the menu and every submenu without touching your app.

---

## Demo

```bash
./gradlew :demo:run
```

The demo shows:
- a generated icon whose colour follows the **Status** submenu (radio items);
- check items, a disabled item, separators and a submenu;
- live controls for tooltip, visibility and left-click menu;
- a **menu theme switcher**: built-in (follows OS / light / dark), custom **Neon** and **Terminal** CSS, and plain Modena;
- radio items with graphics, and an Exit item with a custom `.danger` style class;
- "Show tray menu at cursor";
- a live **event log** (clicks, double-clicks, enter/move/exit with positions and icon bounds).

Closing the window sends the demo to the tray. Use **Exit** in the tray menu to quit.

To re-render the README screenshots, run the demo in screenshot mode. It opens the real menu in each theme, renders it with JavaFX at 2×, and writes transparent PNGs (pure Java, no screen capture):

```bash
./gradlew :demo:run --args="--screenshots=$PWD/docs/screenshots"
```

---

## Building from source

```bash
./gradlew build                        # compile, test, javadoc, jars
./gradlew :juxtray:test                # unit tests
./gradlew :juxtray:javadoc             # public API docs
./gradlew :demo:run                    # run the demo
./gradlew :juxtray:jar                 # build the library jar
```

Toolchain: JDK 25, Gradle 9 (wrapper included, Groovy DSL), JavaFX 25.

```
juxtray/                 the library (module xss.it.juxtray)
  src/main/java/xss/it/juxtray/        public API
  src/main/java/com/xss/it/juxtray/    internals (+ fx/, win/)
  src/main/resources/.../fx/           built-in menu themes
  src/test/java/                       unit tests
demo/                    the demo app (module xss.it.juxtray.demo)
docs/screenshots/        README images (rendered by the demo)
```

Unit tests cover the pure-Java parts: the icon resampler, the menu placement rules for all taskbar positions and multi-monitor coordinates, and the Win32 struct layouts.

---

## Roadmap

- [ ] macOS back end (FFM → Objective-C runtime, `NSStatusItem`, template images)
- [ ] Linux/BSD back end (pure-Java D-Bus, StatusNotifierItem)
- [ ] Balloon / toast notifications
- [ ] Stable icon identity via GUID (keeps the user's "always show" choice across updates)
- [ ] Keyboard activation of the tray icon (`NIN_SELECT` / `NIN_KEYSELECT`)
- [ ] Windows 10-styled built-in theme variant

---

## License

Copyright © 2026 **XTREME SOFTWARE SOLUTIONS**.

Juxtray is released under the **[MIT License](LICENSE)**. You can use it, copy it, modify it, merge it into your own projects, publish, distribute, sublicense and sell it, in open-source or commercial software. The only condition is to keep the copyright and license notice with substantial copies of the code.
