---
name: render-ui
description: Render Screenshot Mode's real Swing UI (screenshot window, sidebar tabs, template editor, dialogs) in Burp's own light and dark themes to PNG, and report cut or truncated text. Use to check any UI change visually before presenting it, at a narrow window or a larger Burp font too.
---

# Render the UI in Burp's theme

The tests render screenshots without Burp (`build/preview/`), but in the default Swing look. This harness loads
Burp's real theme classes from its jar, so what you see is what users see in Burp, in both themes.

## Steps

1. Copy [RenderHarness.java](RenderHarness.java) to your scratch directory (it stays out of the repository) and edit
   `main()` for what you need to see. It is in package `...ui`, so it reaches package-private classes. Out of the
   box it renders the sidebar's three tabs offscreen and the real screenshot window at 1300 and 760 px wide.
2. Run it from the repository root, in light and dark:
   ```bash
   .agents/skills/render-ui/render.sh <scratch>/RenderHarness.java <scratch>/out        # Burp's 13 pt font
   .agents/skills/render-ui/render.sh <scratch>/RenderHarness.java <scratch>/out 1.5    # a larger Burp font
   ```
   The script compiles the project, then the harness against the project classes, Gson and Burp's jar. Burp's jar is
   `$BURP_JAR`, by default the macOS install (`/Applications/Burp Suite.app/Contents/Resources/app/burpsuite.jar`).
   It prints the path of each PNG, and the text cut by its parent or truncated with "…".
3. Look at every PNG (light and dark), and give the user their absolute paths.

## Techniques that work

- **Panels offscreen** (`shoot`): an undecorated `JFrame` made displayable with `addNotify()` (Burp's table UI
  paints no rows otherwise), painted with `paint()` and double buffering off, not `printAll()`/`paintAll()` (JTable
  drops the row selection when painting for print).
- **The real window** (`openWindow`): a `Host` with in-memory preferences and a `TemplateStore`, then
  `ScreenshotLauncher.open`; resize with `setSize`, sleep, then paint the content pane. Use it whenever rows wrap: the
  offscreen path doesn't run the extra layout that a wrapping row (`WrapLayout`) asks for.
- **Dialogs** (confirmations, `JFileChooser`): a `javax.swing.Timer` polls `Window.getWindows()`, finds the chooser or
  the option buttons, and calls `approveSelection()` / `doClick()`.
- **Larger fonts**: a larger `defaultFont` in `UIManager` before installing the look and feel, as `installBurpLaf`
  does. Don't scale `UIManager` fonts one by one afterwards: keys such as `CheckBox.font` follow `defaultFont` and get
  scaled twice.
- **Theme colors**: print `UIManager.getColor(key)` after installing the look and feel; `Palette.contrast` gives WCAG
  contrast ratios.
- `Widgets.wrappingHint` settles its height with `invokeLater`: inside `invokeAndWait` it can look wrong.
