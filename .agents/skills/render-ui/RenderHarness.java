package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.PreferenceStore;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LookAndFeel;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

/**
 * Renders the real UI in Burp's own look and feel (light or dark) to PNG at 2x. Not part of the build: copy it to a
 * scratch directory, edit main() for what you want to see, and run it with render.sh (see SKILL.md).
 *
 * <p>args: outDir light|dark [fontScale]
 */
public class RenderHarness {

    static final String REQ = String.join(
        "\n",
        "POST /api/login?token=abc HTTP/2",
        "Host: shop.example.com",
        "Cookie: session=abc",
        "User-Agent: Mozilla/5.0",
        "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln",
        "Accept: */*",
        "Sec-Fetch-Mode: cors",
        "Content-Length: 2",
        "X-Backend: blue",
        "",
        "{\"role\": \"admin\"}"
    );
    static final String RES = String.join(
        "\n",
        "HTTP/2 200 OK",
        "Date: Tue, 20 Oct 2026 10:00:00 GMT",
        "Content-Type: application/json",
        "Content-Length: 120",
        "Set-Cookie: sid=1; Secure",
        "Strict-Transport-Security: max-age=63072000",
        "X-Frame-Options: DENY",
        "Server: nginx",
        "X-Backend: blue",
        "",
        "{\"role\": \"admin\", \"token\": \"x\"}"
    );
    static final MessageData DATA = new MessageData(
        "k",
        "https://shop.example.com/api/login?token=abc",
        REQ,
        RES,
        RES.length(),
        120L,
        ZonedDateTime.parse("2026-10-20T10:00:00Z")
    );
    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        String theme = args[1];
        float fontScale = args.length > 2 ? Float.parseFloat(args[2]) : 1f;
        installBurpLaf(theme.equals("dark"), fontScale);
        String suffix = "-" + theme + ".png";

        // Panels, offscreen: the screenshot window's sidebar, one tab at a time.
        SwingUtilities.invokeAndWait(() -> {
            try {
                Settings settings = BuiltInTemplates.defaults();
                settings.highlights.add(HighlightRule.forHeaders(List.of("Strict-Transport-Security")));
                SettingsForm form = sidebar(settings, DATA);
                for (int tab : List.of(SettingsForm.RULES, SettingsForm.HEADERS, SettingsForm.APPEARANCE)) {
                    form.tabs().setSelectedIndex(tab);
                    shoot(form, 400, 900, new File(out, "sidebar-" + tab + suffix));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // The real screenshot window, at a wide and a narrow size: this path also runs the relayouts that wrapping
        // rows ask for, which the offscreen one misses.
        for (int width : List.of(1300, 760)) {
            JDialog dialog = openWindow(DATA, BuiltInTemplates.defaults());
            SwingUtilities.invokeAndWait(() -> dialog.setSize(width, 820));
            Thread.sleep(1500);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    report(dialog.getContentPane(), "window " + width);
                    paint((JComponent) dialog.getContentPane(), new File(out, "window-" + width + suffix));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                dialog.dispose();
            });
        }
        System.exit(0);
    }

    /**
     * Burp's real theme classes, loaded from its jar by reflection (they are not on the compile classpath). A font
     * scale other than 1 works like Burp's font size setting: a larger {@code defaultFont} before the look and feel
     * is installed, which every control's font follows. Don't scale the fonts in {@code UIManager} one by one
     * afterwards: some keys, such as {@code CheckBox.font}, follow {@code defaultFont} and would be scaled twice.
     */
    static void installBurpLaf(boolean dark, float fontScale) throws Exception {
        if (fontScale != 1f) {
            Font base = new JLabel().getFont();
            UIManager.put(
                "defaultFont",
                new javax.swing.plaf.FontUIResource(base.getFamily(), Font.PLAIN, Math.round(13 * fontScale))
            );
        }
        String json = new String(
            RenderHarness.class
                .getResourceAsStream(dark ? "/burp/theme/DarkTheme.json" : "/burp/theme/LightTheme.json")
                .readAllBytes()
        );
        Object parser = Class.forName("burp.theme.JsonThemeParser").getConstructor().newInstance();
        Object props = parser.getClass().getMethod("parse", String.class).invoke(parser, json);
        LookAndFeel laf = (LookAndFeel) Class.forName("burp.theme.BurpThemeLaf")
            .getConstructor(String.class, boolean.class, Properties.class)
            .newInstance(dark ? "Dark" : "Light", dark, props);
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(laf);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** A sidebar form fed like the screenshot window does after each render: rule results and header hits. */
    static SettingsForm sidebar(Settings settings, MessageData data) {
        ContentPanel content = new ContentPanel();
        content.render(data, settings, Palette.LIGHT, MONO, MONO, null, null);
        SettingsForm form = new SettingsForm(settings, () -> {}, MONO, HeadersTab.InMessage.of(data));
        form.showRuleStatuses(content.ruleStatuses());
        form.showHeaderHits(content.headerHits());
        return form;
    }

    /** A host like Burp's, with preferences kept in memory. */
    static Host host() {
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
        return new Host(() -> MONO, () -> MONO, () -> false, () -> null, m -> {}, System.err::println, store);
    }

    /** Opens the screenshot window on {@code data}, with {@code settings} as the default template. */
    static JDialog openWindow(MessageData data, Settings settings) throws Exception {
        Host host = host();
        TemplateStore templates = new TemplateStore(host.preferences(), m -> {});
        templates.initialize();
        templates.setDefaultTemplate(templates.createTemplate("Harness", settings).id);
        SwingUtilities.invokeAndWait(() -> new ScreenshotLauncher(host, templates).open(data));
        Thread.sleep(500);
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog dialog && dialog.isShowing()) {
                return dialog;
            }
        }
        throw new IllegalStateException("the screenshot window didn't open");
    }

    /**
     * Lays the component out in an undecorated frame made displayable with addNotify() (Burp's table UI paints no rows
     * otherwise) and paints it.
     */
    static void shoot(JComponent component, int width, int height, File file) throws Exception {
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout());
        root.add(component);
        frame.setContentPane(root);
        frame.setSize(width, height);
        frame.addNotify();
        for (int i = 0; i < 3; i++) {
            frame.validate();
        }
        paint(root, file);
        root.remove(component);
        frame.dispose();
    }

    /**
     * Paints at 2x with paint() and double buffering off, NOT printAll(): JTable drops the row selection when painting
     * for print.
     */
    static void paint(JComponent root, File file) throws Exception {
        BufferedImage image = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.scale(2, 2);
        RepaintManager.currentManager(root).setDoubleBufferingEnabled(false);
        root.paint(g);
        g.dispose();
        ImageIO.write(image, "png", file);
        System.out.println(file.getAbsolutePath());
    }

    /** Prints the text components cut by their parent and the labels truncated with "…". */
    static void report(Container container, String where) {
        for (Component child : container.getComponents()) {
            if (!child.isShowing()) {
                continue;
            }
            if (child instanceof JTextComponent || child instanceof JLabel) {
                boolean cut = child.getX() + child.getWidth() > container.getWidth();
                boolean truncated =
                    child instanceof JLabel label &&
                    label.getText() != null &&
                    !label.getText().isEmpty() &&
                    label.getWidth() < label.getPreferredSize().width;
                if (cut || truncated) {
                    System.out.println(where + ": " + (cut ? "cut " : "truncated ") + describe(child));
                }
            }
            if (child instanceof Container inner) {
                report(inner, where);
            }
        }
    }

    private static String describe(Component component) {
        String text =
            component instanceof JLabel label
                ? label.getText()
                : component instanceof JTextComponent field
                  ? field.getText()
                  : "";
        return component.getClass().getSimpleName() + " \"" + text + "\" " + component.getBounds();
    }
}
