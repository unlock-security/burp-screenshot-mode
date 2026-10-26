package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.MessageMode;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.storage.PreferenceStore;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** The README demo GIF: a mock Burp Repeater, then the real screenshot window, driven step by step. */
public class Demo {

    static final int W = 1240,
        H = 800,
        TITLE = 28;
    static final int FRAME_MS = 30;

    // --- The message ---

    static final String REQ_BODY =
        "{\"subject\":\"Cannot download invoice\",\"message\":\"The PDF link in my account returns an error.\"}";
    static final String RES_BODY =
        "{\"ticket\":{\"id\":48213,\"status\":\"open\",\"assignee\":{\"name\":\"Dana Whitfield\"," +
        "\"email\":\"dana.whitfield@example.com\",\"role\":\"admin\",\"reset_token\":\"f3a9c1e07b5d4c2a9e8f6b1d0c7a3e5b\"}}}";
    static final List<String> REQ_HEADERS = List.of(
        "POST /api/v2/support/tickets HTTP/2",
        "Host: helpdesk.example.com",
        "Cookie: PHPSESSID=9c4b2e7f1a3d8065b2e4f9c1a7d3b5e8; locale=en",
        "Content-Length: " + REQ_BODY.length(),
        "Sec-Ch-Ua-Platform: \"macOS\"",
        "Accept-Language: en-US,en;q=0.9",
        "Sec-Ch-Ua: \"Not A(Brand\";v=\"99\", \"Chromium\";v=\"141\"",
        "Content-Type: application/json",
        "Sec-Ch-Ua-Mobile: ?0",
        "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
        "Accept: application/json",
        "Sec-Fetch-Site: same-origin",
        "Sec-Fetch-Mode: cors",
        "Sec-Fetch-Dest: empty",
        "Referer: https://helpdesk.example.com/tickets/new",
        "Accept-Encoding: gzip, deflate, br",
        "Priority: u=1, i"
    );
    static final List<String> RES_HEADERS = List.of(
        "HTTP/2 201 Created",
        "Date: Sun, 18 Oct 2026 14:50:01 GMT",
        "Content-Type: application/json; charset=utf-8",
        "Content-Length: " + RES_BODY.length(),
        "Location: /api/v2/support/tickets/48213",
        "Server: nginx/1.25.3",
        "Cache-Control: no-store",
        "Vary: Origin",
        "Strict-Transport-Security: max-age=31536000",
        "X-Request-Id: 6f1c0d2a-7b3e-4c9a-8d21-5e0f9a3b7c64"
    );
    static final String REQ = String.join("\n", REQ_HEADERS) + "\n\n" + REQ_BODY;
    static final String RES = String.join("\n", RES_HEADERS) + "\n\n" + RES_BODY;
    static final int TOTAL = String.join("\r\n", RES_HEADERS).length() + 4 + RES_BODY.length();
    static final MessageData DATA = new MessageData(
        "helpdesk",
        "https://helpdesk.example.com:443/api/v2/support/tickets",
        REQ,
        RES,
        TOTAL,
        187L,
        ZonedDateTime.parse("2026-10-18T14:50:01Z")
    );

    // --- Scene state ---

    static JFrame burp;
    static JTextPane requestEditor;
    static JDialog dialog;
    static GifOut gif;
    static Point2D.Double pointer = new Point2D.Double(900, 640);
    static double ring = -1;
    static BufferedImage base;
    static BufferedImage finale;

    public static void main(String[] args) throws Exception {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(240_000);
            } catch (InterruptedException e) {
                return;
            }
            System.err.println("timed out");
            Runtime.getRuntime().halt(2);
        });
        watchdog.setDaemon(true);
        watchdog.start();

        RenderHarness.installBurpLaf(false, 1f);
        Font mono = new Font("Menlo", Font.PLAIN, 12);
        Host host = host(mono);
        TemplateStore store = new TemplateStore(host.preferences(), m -> {});
        store.initialize();
        SwingUtilities.invokeAndWait(() -> {
            RepaintManager.currentManager(new JPanel()).setDoubleBufferingEnabled(false);
            burp = mockBurp(mono);
            burp.setLocation(60, 60);
            burp.setVisible(true);
        });
        Thread.sleep(1200);
        gif = new GifOut(new File(args[0]));
        try {
            script(host, store);
            gif.close();
            System.out.println(gif.frames + " frames written to " + args[0] + ", at most " + gif.worst + " pixels off");
            System.exit(0);
        } catch (Throwable e) {
            // Never leave the windows open: a failed step ends the run.
            e.printStackTrace();
            gif.close();
            System.exit(1);
        }
    }

    static Host host(Font mono) {
        Map<String, String> preferences = new HashMap<>();
        PreferenceStore store = new PreferenceStore() {
            @Override
            public String get(String key) {
                return preferences.get(key);
            }

            @Override
            public void set(String key, String value) {
                preferences.put(key, value);
            }
        };
        Font ui = new JLabel().getFont();
        return new Host(() -> mono, () -> ui, () -> false, () -> burp, m -> {}, System.err::println, store);
    }

    // --- The storyboard ---

    static void script(Host host, TemplateStore store) throws Exception {
        // 1. Burp Repeater: right-click › Extensions › Screenshot Mode › Screenshot.
        hold(700);
        Point inEditor = at(requestEditor, textPoint(requestEditor, REQ_HEADERS.get(7), 18));
        move(inEditor);
        hold(200);
        AtomicReference<JPopupMenu> menu = new AtomicReference<>();
        AtomicReference<JMenu> extensions = new AtomicReference<>();
        AtomicReference<JMenu> screenshotMode = new AtomicReference<>();
        AtomicReference<JMenuItem> screenshot = new AtomicReference<>();
        click(true);
        act(() -> {
            JPopupMenu popup = repeaterMenu(extensions, screenshotMode, screenshot);
            Point p = SwingUtilities.convertPoint(burp.getRootPane(), toRoot(burp, inEditor), requestEditor);
            popup.show(requestEditor, p.x, p.y);
            menu.set(popup);
        });
        hold(300);
        move(center(extensions.get()));
        act(() ->
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[] {
                menu.get(),
                extensions.get(),
                extensions.get().getPopupMenu(),
            })
        );
        hold(220);
        move(center(screenshotMode.get()));
        act(() ->
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[] {
                menu.get(),
                extensions.get(),
                extensions.get().getPopupMenu(),
                screenshotMode.get(),
                screenshotMode.get().getPopupMenu(),
            })
        );
        hold(200);
        Point item = center(screenshot.get());
        move(new Point(item.x - 20, item.y));
        act(() ->
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[] {
                menu.get(),
                extensions.get(),
                extensions.get().getPopupMenu(),
                screenshotMode.get(),
                screenshotMode.get().getPopupMenu(),
                screenshot.get(),
            })
        );
        hold(200);
        click(false);
        act(() -> {
            MenuSelectionManager.defaultManager().clearSelectedPath();
            new ScreenshotLauncher(host, store).open(DATA);
        });
        Thread.sleep(600);
        act(() -> {
            dialog = (JDialog) showing(w -> w instanceof JDialog);
            Point origin = burp.getLocationOnScreen();
            dialog.setBounds(origin.x + 40, origin.y + 52, 1160, 724);
        });
        Thread.sleep(800);
        dirty();
        hold(1100);

        SettingsForm form = find(dialog, SettingsForm.class);
        ContentPanel content = find(dialog, ContentPanel.class);

        // 2. Select the token in the response, right-click › Highlight; make it a red rectangle.
        MessageView response = messageView(content, "reset_token");
        JScrollPane previewScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, content);
        // The key: a highlight that overlaps a redaction is dropped, and step 3 redacts the value's tail.
        int tokenStart = response.getText().indexOf("reset_token");
        Rectangle tokenRect = viewRect(response, tokenStart);
        Point tokenInContent = SwingUtilities.convertPoint(response, tokenRect.getLocation(), content);
        int scrollTo = Math.max(0, tokenInContent.y - previewScroll.getViewport().getHeight() + 80);
        move(at(previewScroll, new Point(previewScroll.getWidth() / 2, previewScroll.getHeight() / 2)));
        scroll(previewScroll, scrollTo);
        Point from = at(response, centerLeft(viewRect(response, tokenStart)));
        move(from);
        hold(150);
        for (int i = 0; i < 11; i++) {
            int end = tokenStart + i + 1;
            act(() -> {
                response.select(tokenStart, end);
                response.getCaret().setSelectionVisible(true);
            }, 0);
            pointer.setLocation(at(response, centerLeft(viewRect(response, end))));
            frame(FRAME_MS);
        }
        hold(250);
        click(true);
        act(() -> {
            Point p = SwingUtilities.convertPoint(dialog.getRootPane(), toRoot(dialog, point(pointer)), response);
            response.dispatchEvent(
                new MouseEvent(
                    response,
                    MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(),
                    InputEvent.BUTTON3_DOWN_MASK,
                    p.x,
                    p.y,
                    1,
                    true,
                    MouseEvent.BUTTON3
                )
            );
        });
        hold(450);
        JMenuItem highlight = menuItem("Highlight");
        clickMenuItem(highlight);
        hold(700);

        RuleList<?> highlights = form.highlightList();
        JComboBox<?> style = comboIn(highlights, c -> c.getItemAt(0) instanceof HighlightMode);
        chooseInCombo(style, HighlightMode.RECTANGLE);
        JComboBox<?> color = comboIn(highlights, c -> c.getItemAt(0) instanceof Widgets.ColorChoice);
        chooseInCombo(color, Widgets.ColorChoice.HIGHLIGHT.get(2));
        hold(900);

        // 3. A text pattern redaction with named groups: redact only the token's tail.
        JButton addRedaction = button(form, b ->
            "Add redactions rule".equals(b.getAccessibleContext().getAccessibleName())
        );
        reveal(addRedaction);
        move(center(addRedaction));
        click(false);
        act(addRedaction::doClick);
        hold(400);
        clickMenuItem(menuItem("Text pattern"));
        hold(500);
        RuleList<?> redactions = form.redactionList();
        JTextField name = find(redactions, JTextField.class, f -> f.isShowing() && f.getText().isEmpty());
        moveClick(center(name));
        type(name, "Token tail");
        JTextComponent pattern = find(
            redactions,
            JTextComponent.class,
            f -> f.isShowing() && "Pattern".equals(f.getAccessibleContext().getAccessibleName())
        );
        moveClick(center(pattern));
        type(pattern, "\"reset_token\":\"(?<head>[0-9a-f]{6})(?<tail>[0-9a-f]+)\"");
        hold(400);
        tick(checkBox(redactions, "Response"));
        tick(checkBox(redactions, "Request"));
        hold(600);
        JCheckBox head = checkBox(redactions, "head");
        reveal(head);
        tick(head);
        hold(1600);

        // 4. A header rule: find Server in the Headers tab, show it, highlight it.
        JTabbedPane tabs = form.tabs();
        selectTab(tabs, SettingsForm.HEADERS);
        HeadersTab headers = form.headersTab();
        JTextField filter = find(headers, JTextField.class, f ->
            "Filter headers".equals(f.getAccessibleContext().getAccessibleName())
        );
        moveClick(center(filter));
        type(filter, "serv");
        hold(500);
        JTable table = headers.table();
        AtomicReference<Integer> found = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            headers.select("Server");
            found.set(table.getSelectedRow());
            table.clearSelection();
        });
        int serverRow = found.get();
        int showColumn = table.getColumnCount() - 1;
        for (int c = 0; c < table.getColumnCount(); c++) {
            if ("Show".equals(table.getColumnName(c))) showColumn = c;
        }
        Rectangle cell = table.getCellRect(serverRow, showColumn, true);
        int column = showColumn;
        move(at(table, new Point(cell.x + cell.width / 2, cell.y + cell.height / 2)));
        click(false);
        act(() -> {
            table.setRowSelectionInterval(serverRow, serverRow);
            table.getActionMap().get("toggleShow").actionPerformed(null);
        });
        hold(700);
        JButton highlightHeader = button(headers.details(), b -> "Highlight…".equals(b.getText()) && b.isShowing());
        move(center(highlightHeader));
        click(false);
        act(highlightHeader::doClick);
        hold(500);
        scroll(previewScroll, Math.max(0, previewScroll.getVerticalScrollBar().getValue() - 120));
        hold(1500);

        // 5. Appearance: messages, URL bar, body, theme.
        selectTab(tabs, SettingsForm.APPEARANCE);
        hold(400);
        segment(form, MessageMode.REQUEST, 900);
        segment(form, MessageMode.RESPONSE, 900);
        segment(form, MessageMode.BOTH, 700);
        segment(form, UrlMode.FULL, 1000);
        segment(form, UrlMode.PATH, 600);
        segment(form, ViewMode.RAW, 1100);
        segment(form, ViewMode.PRETTY, 600);
        segment(form, ThemeMode.DARK, 1300);
        segment(form, ThemeMode.LIGHT, 700);

        // 6. Copy, then what ends up in the report.
        JButton copy = button(dialog.getRootPane(), b -> "Copy image".equals(b.getText()));
        move(center(copy));
        click(false);
        act(() -> {
            try {
                var status = ScreenshotDialog.class.getDeclaredMethod("showStatus", String.class, boolean.class);
                status.setAccessible(true);
                status.invoke(dialog, "Image copied", false);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        hold(1300);
        AtomicReference<BufferedImage> exported = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> exported.set(ImageCapture.render(content, 1)));
        BufferedImage last = scene();
        for (int i = 1; i <= 8; i++) {
            finale = finale(last, exported.get(), i / 8.0);
            gif.add(finale, FRAME_MS);
        }
        gif.add(finale, 3500);
    }

    // --- Actions ---

    /** Runs on the EDT, lets timers and layout settle, and repaints the scene for the next frames. */
    static void act(Runnable action) throws Exception {
        act(action, 250);
    }

    static void act(Runnable action, int settle) throws Exception {
        SwingUtilities.invokeAndWait(action);
        if (settle > 0) {
            Thread.sleep(settle);
        }
        dirty();
    }

    static void dirty() {
        base = null;
    }

    static void hold(int ms) throws Exception {
        frame(ms);
    }

    static void move(Point target) throws Exception {
        double x0 = pointer.x,
            y0 = pointer.y;
        double distance = target.distance(x0, y0);
        int steps = (int) Math.max(6, Math.min(22, distance / 28));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double e = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            pointer.setLocation(x0 + (target.x - x0) * e, y0 + (target.y - y0) * e);
            frame(FRAME_MS);
        }
    }

    static void click(boolean right) throws Exception {
        for (int i = 0; i < 4; i++) {
            ring = i / 4.0;
            frame(FRAME_MS);
        }
        ring = -1;
    }

    static void moveClick(Point target) throws Exception {
        move(target);
        click(false);
    }

    static void tick(AbstractButton box) throws Exception {
        reveal(box);
        move(center(box));
        click(false);
        act(box::doClick);
        hold(350);
    }

    static void type(JTextComponent field, String text) throws Exception {
        act(() -> field.requestFocusInWindow(), 50);
        for (char ch : text.toCharArray()) {
            act(() -> {
                try {
                    field.getDocument().insertString(field.getDocument().getLength(), String.valueOf(ch), null);
                } catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
            }, 40);
            frame(55);
        }
        Thread.sleep(300);
        dirty();
    }

    static void scroll(JScrollPane pane, int to) throws Exception {
        int from = pane.getVerticalScrollBar().getValue();
        for (int i = 1; i <= 10; i++) {
            int value = from + ((to - from) * i) / 10;
            act(() -> pane.getVerticalScrollBar().setValue(value), 0);
            frame(FRAME_MS);
        }
        Thread.sleep(200);
        dirty();
    }

    static void selectTab(JTabbedPane tabs, int index) throws Exception {
        Rectangle bounds = tabs.getBoundsAt(index);
        move(at(tabs, new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)));
        click(false);
        act(() -> tabs.setSelectedIndex(index), 400);
    }

    static <E> void segment(SettingsForm form, E value, int holdMs) throws Exception {
        SegmentedControl<?> control = find(
            form,
            SegmentedControl.class,
            c -> c.isShowing() && c.value().getClass() == value.getClass()
        );
        @SuppressWarnings("unchecked")
        SegmentedControl<E> typed = (SegmentedControl<E>) control;
        AbstractButton button = typed.button(value);
        move(center(button));
        click(false);
        act(() -> typed.click(value), 450);
        hold(holdMs);
    }

    static void chooseInCombo(JComboBox<?> combo, Object value) throws Exception {
        reveal(combo);
        moveClick(center(combo));
        act(combo::showPopup, 300);
        ComboPopup popup = (ComboPopup) combo.getUI().getAccessibleChild(combo, 0);
        JList<?> list = popup.getList();
        int index = -1;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).equals(value)) index = i;
        }
        Rectangle cell = list.getCellBounds(index, index);
        int chosen = index;
        hold(200);
        move(at(list, new Point(cell.x + Math.min(60, cell.width / 2), cell.y + cell.height / 2)));
        act(() -> list.setSelectedIndex(chosen), 50);
        hold(200);
        click(false);
        act(() -> {
            combo.setSelectedIndex(chosen);
            combo.hidePopup();
        });
        hold(300);
    }

    static void clickMenuItem(JMenuItem item) throws Exception {
        Point p = center(item);
        move(new Point(p.x - item.getWidth() / 2 + 40, p.y));
        JPopupMenu popup = (JPopupMenu) item.getParent();
        act(() -> MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[] { popup, item }), 50);
        hold(250);
        click(false);
        act(() -> {
            MenuSelectionManager.defaultManager().clearSelectedPath();
            popup.setVisible(false);
            item.doClick(0);
        });
    }

    static void reveal(JComponent component) throws Exception {
        act(
            () ->
                component.scrollRectToVisible(new Rectangle(0, -40, component.getWidth(), component.getHeight() + 80)),
            150
        );
    }

    // --- Finding things ---

    static Window showing(Predicate<Window> test) {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && test.test(window)) {
                return window;
            }
        }
        throw new IllegalStateException("no such window");
    }

    static <T> T find(Container root, Class<T> type) {
        return find(root, type, c -> true);
    }

    @SuppressWarnings("unchecked")
    static <T> T find(Container root, Class<T> type, Predicate<T> test) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && test.test((T) child)) {
                return (T) child;
            }
            if (child instanceof Container inner) {
                T found = find(inner, type, test);
                if (found != null) {
                    return found;
                }
            }
        }
        if (root instanceof JMenu menu) {
            return find(menu.getPopupMenu(), type, test);
        }
        return null;
    }

    static JButton button(Container root, Predicate<JButton> test) {
        JButton found = find(root, JButton.class, test);
        if (found == null) throw new IllegalStateException("no button");
        return found;
    }

    static JCheckBox checkBox(Container root, String text) {
        JCheckBox found = find(root, JCheckBox.class, b -> text.equals(b.getText()) && b.isShowing());
        if (found == null) throw new IllegalStateException("no checkbox " + text);
        return found;
    }

    static JComboBox<?> comboIn(Container root, Predicate<JComboBox<?>> test) {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        JComboBox<?> found = find(root, JComboBox.class, c -> c.isShowing() && c.getItemCount() > 0 && test.test(c));
        if (found == null) throw new IllegalStateException("no combo");
        return found;
    }

    static JMenuItem menuItem(String text) {
        for (Window window : Window.getWindows()) {
            if (!window.isShowing()) continue;
            JMenuItem item = find(
                window instanceof JDialog d
                    ? d.getRootPane()
                    : window instanceof JFrame f
                      ? f.getRootPane()
                      : window instanceof javax.swing.JWindow jw
                        ? jw.getRootPane()
                        : null,
                JMenuItem.class,
                i -> text.equals(i.getText()) && i.isShowing()
            );
            if (item != null) return item;
        }
        throw new IllegalStateException("no menu item " + text);
    }

    static MessageView messageView(ContentPanel content, String containing) {
        MessageView found = find(content, MessageView.class, v -> v.getText().contains(containing));
        if (found == null) throw new IllegalStateException("no view with " + containing);
        return found;
    }

    static Rectangle viewRect(JTextComponent view, int offset) {
        try {
            return view.modelToView2D(offset).getBounds();
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    static Point centerLeft(Rectangle r) {
        return new Point(r.x, r.y + r.height / 2);
    }

    static Point textPoint(JTextPane editor, String line, int column) {
        int offset = editor.getText().indexOf(line) + column;
        Rectangle r = viewRect(editor, offset);
        return new Point(r.x, r.y + r.height / 2);
    }

    // --- Coordinates: the canvas is the Burp window, title bar included ---

    static Point canvasOrigin() {
        return burp.getLocationOnScreen();
    }

    /** Where a point of a component is on the canvas. */
    static Point at(Component component, Point p) {
        Point screen = new Point(p);
        SwingUtilities.convertPointToScreen(screen, component);
        Point origin = canvasOrigin();
        return new Point(screen.x - origin.x, screen.y - origin.y);
    }

    static Point center(Component component) {
        return at(component, new Point(component.getWidth() / 2, component.getHeight() / 2));
    }

    static Point point(Point2D p) {
        return new Point((int) Math.round(p.getX()), (int) Math.round(p.getY()));
    }

    /** A canvas point in the root pane of a window. */
    static Point toRoot(Window window, Point canvas) {
        JRootPane root = ((javax.swing.RootPaneContainer) window).getRootPane();
        Point screen = new Point(canvas.x + canvasOrigin().x, canvas.y + canvasOrigin().y);
        SwingUtilities.convertPointFromScreen(screen, root);
        return screen;
    }

    // --- Frames ---

    static void frame(int delayMs) throws Exception {
        if (base == null) {
            SwingUtilities.invokeAndWait(() -> base = scene());
        }
        BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        g.drawImage(base, 0, 0, null);
        drawPointer(g);
        g.dispose();
        gif.add(frame, delayMs);
    }

    /** Every showing window, back to front, at its place on the canvas, with drawn macOS frames. */
    static BufferedImage scene() {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        List<Window> windows = new ArrayList<>();
        windows.add(burp);
        if (dialog != null && dialog.isShowing()) windows.add(dialog);
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && !windows.contains(window) && window instanceof javax.swing.RootPaneContainer) {
                windows.add(window);
            }
        }
        Point origin = canvasOrigin();
        for (Window window : windows) {
            JRootPane root = ((javax.swing.RootPaneContainer) window).getRootPane();
            Point rootScreen = root.getLocationOnScreen();
            int x = rootScreen.x - origin.x,
                y = rootScreen.y - origin.y;
            boolean decorated = window instanceof Frame || window instanceof Dialog;
            String title = window instanceof Frame f ? f.getTitle() : window instanceof Dialog d ? d.getTitle() : null;
            int top = decorated ? y - TITLE : y;
            int w = root.getWidth(),
                h = root.getHeight() + (decorated ? TITLE : 0);
            if (window != burp) {
                shadow(g, x, top, w, h, decorated ? 10 : 6);
            }
            Graphics2D wg = (Graphics2D) g.create();
            RoundRectangle2D shape = new RoundRectangle2D.Double(
                x,
                top,
                w,
                h,
                decorated ? 20 : 10,
                decorated ? 20 : 10
            );
            wg.clip(shape);
            if (decorated) {
                titleBar(wg, x, top, w, title);
            }
            wg.translate(x, y);
            root.paint(wg);
            wg.dispose();
            if (decorated) {
                g.setColor(new Color(0, 0, 0, 60));
                g.setStroke(new BasicStroke(1f));
                g.draw(shape);
            }
        }
        g.dispose();
        return image;
    }

    static void titleBar(Graphics2D g, int x, int y, int w, String title) {
        g.setColor(new Color(0xe9e9eb));
        g.fillRect(x, y, w, TITLE);
        g.setColor(new Color(0xd2d2d4));
        g.fillRect(x, y + TITLE - 1, w, 1);
        int[] lights = { 0xff5f57, 0xfebc2e, 0x28c840 };
        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(lights[i]));
            g.fill(new Ellipse2D.Double(x + 13 + i * 20, y + TITLE / 2.0 - 6, 12, 12));
        }
        if (title != null) {
            g.setFont(new JLabel().getFont().deriveFont(Font.BOLD, 13f));
            g.setColor(new Color(0x4d4d4d));
            FontMetrics m = g.getFontMetrics();
            // Centered, but never over the window buttons.
            int left = Math.max(x + 80, x + (w - m.stringWidth(title)) / 2);
            g.drawString(title, left, y + (TITLE - m.getHeight()) / 2 + m.getAscent());
        }
    }

    static void shadow(Graphics2D g, int x, int y, int w, int h, int size) {
        for (int i = size; i > 0; i--) {
            g.setColor(new Color(0, 0, 0, (int) (((28.0 / size) * (size - i + 1)) / 2)));
            g.fill(new RoundRectangle2D.Double(x - i, y - i + size / 3.0, w + 2 * i, h + 2 * i, 20 + i, 20 + i));
        }
    }

    static void drawPointer(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double x = pointer.x,
            y = pointer.y;
        if (ring >= 0) {
            double r = 7 + ring * 12;
            g.setColor(new Color(0x2f, 0x7b, 0xf6, (int) (150 * (1 - ring))));
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
        Polygon arrow = new Polygon();
        int[][] points = { { 0, 0 }, { 0, 17 }, { 4, 13 }, { 7, 20 }, { 10, 19 }, { 7, 12 }, { 12, 12 } };
        for (int[] p : points) arrow.addPoint((int) Math.round(x + p[0]), (int) Math.round(y + p[1]));
        g.setColor(Color.BLACK);
        g.fill(arrow);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(arrow);
    }

    /** The last scene dimmed, and the exported screenshot growing in over it. */
    static BufferedImage finale(BufferedImage last, BufferedImage exported, double t) {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(last, 0, 0, null);
        g.setColor(new Color(0, 0, 0, (int) (130 * t)));
        g.fillRect(0, 0, W, H);
        double fit = Math.min(1.0, (H - 60.0) / exported.getHeight());
        double scale = fit * (0.9 + 0.1 * t);
        int w = (int) (exported.getWidth() * scale),
            h = (int) (exported.getHeight() * scale);
        int x = (W - w) / 2,
            y = (H - h) / 2;
        g.setComposite(AlphaComposite.SrcOver.derive((float) t));
        shadow(g, x, y, w, h, 12);
        g.drawImage(exported, x, y, w, h, null);
        g.dispose();
        return image;
    }

    // --- The mock Burp window ---

    static JFrame mockBurp(Font mono) {
        JFrame frame = new JFrame("Burp Suite Professional");
        JTabbedPane top = new JTabbedPane();
        for (String tab : List.of(
            "Dashboard",
            "Target",
            "Proxy",
            "Intruder",
            "Repeater",
            "Collaborator",
            "Sequencer",
            "Decoder",
            "Comparer",
            "Logger",
            "Organizer",
            "Extensions",
            "Screenshot Mode"
        )) {
            top.addTab(tab, tab.equals("Repeater") ? repeater(mono) : new JPanel());
        }
        top.setSelectedIndex(4);
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
            )
        );
        status.add(new JLabel("Done"), BorderLayout.WEST);
        status.add(new JLabel(TOTAL + " bytes | 187 millis"), BorderLayout.EAST);
        JPanel root = new JPanel(new BorderLayout());
        root.add(top, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setSize(W, H);
        return frame;
    }

    static JComponent repeater(Font mono) {
        JTabbedPane tickets = new JTabbedPane();
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(Widgets.primary(Widgets.button("Send")));
        left.add(Widgets.button("Cancel"));
        left.add(Widgets.button("<"));
        left.add(Widgets.button(">"));
        toolbar.add(left, BorderLayout.WEST);
        JLabel target = new JLabel("Target: https://helpdesk.example.com      HTTP/2");
        target.setFont(target.getFont().deriveFont(Font.BOLD));
        toolbar.add(target, BorderLayout.EAST);

        requestEditor = editor(mono, REQ_HEADERS, pretty(REQ_BODY));
        JTextPane responseEditor = editor(mono, RES_HEADERS, pretty(RES_BODY));
        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            messagePanel("Request", List.of("Pretty", "Raw", "Hex"), requestEditor),
            messagePanel("Response", List.of("Pretty", "Raw", "Hex", "Render"), responseEditor)
        );
        split.setResizeWeight(0.5);
        split.setDividerLocation(615);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        tickets.addTab("Ticket", panel);
        tickets.addTab("+", new JPanel());
        return tickets;
    }

    static JComponent messagePanel(String title, List<String> views, JTextPane editor) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
        heading.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        JTabbedPane tabs = new JTabbedPane();
        JScrollPane scroll = new JScrollPane(editor);
        scroll.setRowHeaderView(new LineNumbers(editor));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        for (String view : views) {
            tabs.addTab(view, view.equals("Pretty") ? scroll : new JPanel());
        }
        JPanel search = new JPanel(new BorderLayout(10, 0));
        search.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JTextField field = new JTextField();
        Widgets.placeholder(field, "Search");
        search.add(field, BorderLayout.CENTER);
        search.add(new JLabel("0 highlights"), BorderLayout.EAST);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
        panel.add(search, BorderLayout.SOUTH);
        return panel;
    }

    static String pretty(String json) {
        StringBuilder out = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (char c : json.toCharArray()) {
            if (c == '"') inString = !inString;
            if (inString) {
                out.append(c);
                continue;
            }
            switch (c) {
                case '{' -> {
                    indent++;
                    out.append("{\n").append("  ".repeat(indent));
                }
                case '}' -> {
                    indent--;
                    out.append("\n").append("  ".repeat(indent)).append("}");
                }
                case ',' -> out.append(",\n").append("  ".repeat(indent));
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static JTextPane editor(Font mono, List<String> headers, String body) {
        JTextPane pane = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        pane.setFont(mono);
        pane.setEditable(false);
        pane.setMargin(new Insets(4, 8, 4, 8));
        StyledDocument doc = pane.getStyledDocument();
        Color name = new Color(0x1b3b9b),
            key = new Color(0x1b3b9b),
            string = new Color(0x217a3c),
            number = new Color(0x1a5fd0);
        try {
            for (int i = 0; i < headers.size(); i++) {
                String line = headers.get(i);
                int colon = line.indexOf(": ");
                if (i == 0 || colon < 0) {
                    append(doc, line, null, i == 0 && line.startsWith("HTTP"));
                } else {
                    append(doc, line.substring(0, colon), name, false);
                    append(doc, line.substring(colon), null, false);
                }
                append(doc, "\n", null, false);
            }
            append(doc, "\n", null, false);
            // JSON: keys and strings, numbers.
            int i = 0;
            while (i < body.length()) {
                char c = body.charAt(i);
                if (c == '"') {
                    int end = body.indexOf('"', i + 1) + 1;
                    boolean isKey = end < body.length() && body.charAt(end) == ':';
                    append(doc, body.substring(i, end), isKey ? key : string, false);
                    i = end;
                } else if (Character.isDigit(c)) {
                    int end = i;
                    while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
                    append(doc, body.substring(i, end), number, false);
                    i = end;
                } else {
                    append(doc, String.valueOf(c), null, false);
                    i++;
                }
            }
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        return pane;
    }

    static void append(StyledDocument doc, String text, Color color, boolean boldStatus) throws BadLocationException {
        SimpleAttributeSet set = new SimpleAttributeSet();
        if (color != null) StyleConstants.setForeground(set, color);
        if (boldStatus) {
            // "HTTP/2 201 Created" with the status code bold, as in Burp.
            int space = text.indexOf(' ');
            int second = text.indexOf(' ', space + 1);
            doc.insertString(doc.getLength(), text.substring(0, space + 1), set);
            SimpleAttributeSet bold = new SimpleAttributeSet();
            StyleConstants.setBold(bold, true);
            doc.insertString(doc.getLength(), text.substring(space + 1, second), bold);
            doc.insertString(doc.getLength(), text.substring(second), set);
            return;
        }
        doc.insertString(doc.getLength(), text, set);
    }

    /** Line numbers beside an editor whose lines don't wrap. */
    static final class LineNumbers extends JComponent {

        private final JTextPane editor;

        LineNumbers(JTextPane editor) {
            this.editor = editor;
            setFont(editor.getFont());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(36, editor.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics;
            g.setColor(editor.getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x8a8a8a));
            g.setFont(getFont());
            FontMetrics m = g.getFontMetrics();
            String text = editor.getText();
            int line = 1,
                offset = 0;
            while (offset <= text.length()) {
                try {
                    Rectangle r = editor.modelToView2D(offset).getBounds();
                    String number = Integer.toString(line);
                    g.drawString(number, getWidth() - 8 - m.stringWidth(number), r.y + m.getAscent());
                } catch (BadLocationException e) {
                    break;
                }
                int next = text.indexOf('\n', offset);
                if (next < 0) break;
                offset = next + 1;
                line++;
            }
        }
    }

    static JPopupMenu repeaterMenu(
        AtomicReference<JMenu> extensions,
        AtomicReference<JMenu> screenshotMode,
        AtomicReference<JMenuItem> screenshot
    ) {
        JPopupMenu menu = new JPopupMenu();
        int meta = InputEvent.META_DOWN_MASK;
        menu.add(new JMenuItem("Scan"));
        menu.add(new JMenuItem("Passive scan selected message"));
        menu.add(new JMenuItem("Active scan selected message"));
        menu.addSeparator();
        menu.add(item("Send to Intruder", KeyStroke.getKeyStroke(KeyEvent.VK_I, meta)));
        menu.add(item("Send to Repeater", KeyStroke.getKeyStroke(KeyEvent.VK_R, meta)));
        menu.add(new JMenuItem("Send to Sequencer"));
        menu.add(new JMenuItem("Send to Comparer"));
        menu.add(new JMenuItem("Send to Decoder"));
        menu.add(item("Send to Organizer", KeyStroke.getKeyStroke(KeyEvent.VK_O, meta)));
        menu.addSeparator();
        menu.add(withItem(new JMenu("Request in browser"), "In original session"));
        JMenu ext = new JMenu("Extensions");
        JMenu mode = new JMenu("Screenshot Mode");
        JMenuItem shot = new JMenuItem("Screenshot");
        mode.add(shot);
        ext.add(mode);
        menu.add(ext);
        menu.add(withItem(new JMenu("Engagement tools"), "Find comments"));
        menu.addSeparator();
        menu.add(new JMenuItem("Change request method"));
        menu.add(withItem(new JMenu("Change body encoding"), "URL-encoded"));
        menu.add(item("Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, meta)));
        menu.add(new JMenuItem("Copy URL"));
        menu.add(new JMenuItem("Copy as curl command (bash)"));
        menu.add(new JMenuItem("Copy to file"));
        menu.add(new JMenuItem("Paste from file"));
        menu.add(new JMenuItem("Save item"));
        extensions.set(ext);
        screenshotMode.set(mode);
        screenshot.set(shot);
        return menu;
    }

    static JMenuItem item(String text, KeyStroke key) {
        JMenuItem item = new JMenuItem(text);
        item.setAccelerator(key);
        return item;
    }

    static JMenu withItem(JMenu menu, String text) {
        menu.add(new JMenuItem(text));
        return menu;
    }
}
