package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.PreferenceStore;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** The README screenshots: the screenshot window, the exported screenshot and the templates tab. args: outDir light|dark */
public class Screenshots {

    static final String REQ = String.join(
        "\n",
        "POST /api/v2/support/tickets HTTP/2",
        "Host: helpdesk.example.com",
        "Cookie: PHPSESSID=8c0d1e77a1b94f3e9c52d7a6b0f4e218; locale=en",
        "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 15_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36",
        "Accept: application/json",
        "Accept-Language: en-US,en;q=0.9",
        "Accept-Encoding: gzip, deflate, br",
        "Content-Type: application/json",
        "Content-Length: 94",
        "Origin: https://helpdesk.example.com",
        "Referer: https://helpdesk.example.com/tickets/new",
        "Sec-Fetch-Site: same-origin",
        "Sec-Fetch-Mode: cors",
        "Sec-Fetch-Dest: empty",
        "Priority: u=1, i",
        "",
        "{\"subject\":\"Cannot download invoice\",\"message\":\"The PDF link in my account returns an error.\"}"
    );
    static final String RES = String.join(
        "\n",
        "HTTP/2 201 Created",
        "Date: Sun, 18 Oct 2026 14:50:01 GMT",
        "Content-Type: application/json; charset=utf-8",
        "Content-Length: 177",
        "Location: /api/v2/support/tickets/48213",
        "Server: nginx",
        "Cache-Control: no-store",
        "X-Request-Id: 5f1c2a9e-0b7d-4c1e-9a2f-6d3b8e4c7a10",
        "Strict-Transport-Security: max-age=63072000",
        "",
        "{\"ticket\":{\"id\":48213,\"status\":\"open\",\"assignee\":{\"name\":\"Dana Whitfield\"," +
            "\"email\":\"dana.whitfield@example.com\",\"role\":\"admin\"," +
            "\"reset_token\":\"f3a9c1e07b5d4c2a9e8f6b1d0c7a3e5b\"}}}"
    );
    static final MessageData DATA = new MessageData(
        "helpdesk",
        "https://helpdesk.example.com:443/api/v2/support/tickets",
        REQ,
        RES,
        482,
        187L,
        ZonedDateTime.parse("2026-10-18T14:50:01Z")
    );

    static HighlightRule leakedToken;

    public static void main(String[] args) throws Exception {
        // Never hang: a window stuck waiting for focus or a dialog ends the run.
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                return;
            }
            System.err.println("timed out");
            Runtime.getRuntime().halt(2);
        });
        watchdog.setDaemon(true);
        watchdog.start();
        File out = new File(args[0]);
        boolean dark = args[1].equals("dark");
        RenderHarness.installBurpLaf(dark, 1f);
        Font mono = new Font("Menlo", Font.PLAIN, 13);
        Host host = host(mono, dark);
        TemplateStore store = templates(host);

        // The screenshot window, with the highlight rule open.
        SwingUtilities.invokeAndWait(() -> new ScreenshotLauncher(host, store).open(DATA));
        Thread.sleep(800);
        JDialog dialog = dialog();
        SwingUtilities.invokeAndWait(() -> {
            dialog.setBounds(80, 80, 1440, 900);
            SettingsForm form = find(dialog, SettingsForm.class);
            form.tabs().setSelectedIndex(SettingsForm.RULES);
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> {
            try {
                // The window edits a copy of the template's settings: open its own rule.
                SettingsForm form = find(dialog, SettingsForm.class);
                java.lang.reflect.Field field = SettingsForm.class.getDeclaredField("settings");
                field.setAccessible(true);
                form.highlightList().reveal(((Settings) field.get(form)).highlights.getFirst());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() ->
            frame(dialog.getRootPane(), "Screenshot Mode", dark, new File(out, "window-" + args[1] + ".png"))
        );

        // What the screenshot window exports.
        SwingUtilities.invokeAndWait(() -> {
            try {
                ContentPanel content = find(dialog, ContentPanel.class);
                ImageCapture.save(ImageCapture.render(content, 2), new File(out, "screenshot-" + args[1] + ".png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            dialog.dispose();
        });

        // The templates panel, as in Burp's settings.
        if (!dark) {
            JFrame frame = new JFrame("Screenshot Mode");
            SwingUtilities.invokeAndWait(() -> {
                frame.setContentPane(new TemplatesTab(store, host));
                frame.setBounds(80, 80, 1216, 300);
                javax.swing.JTable table = find(frame.getContentPane(), javax.swing.JTable.class);
                table.setRowSelectionInterval(2, 2);
                frame.setVisible(true);
            });
            Thread.sleep(2000);
            SwingUtilities.invokeAndWait(() -> frame(frame.getRootPane(), null, false, new File(out, "templates.png")));
            frame.dispose();
        }
        System.exit(0);
    }

    static Host host(Font mono, boolean dark) {
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
        Font ui = new javax.swing.JLabel().getFont();
        return new Host(() -> mono, () -> ui, () -> dark, () -> null, m -> {}, System.err::println, store);
    }

    static TemplateStore templates(Host host) {
        TemplateStore store = new TemplateStore(host.preferences(), m -> {});
        store.initialize();

        Settings report = BuiltInTemplates.defaults();
        report.theme = ThemeMode.AUTO;
        report.scale = 2;
        leakedToken = HighlightRule.create("\"reset_token\":\\s*\"([0-9a-f]+)\"", RuleTarget.RESPONSE, MatchMode.REGEX);
        leakedToken.name = "Leaked reset token";
        leakedToken.mode = HighlightMode.RECTANGLE;
        leakedToken.color = "#ff0000";
        leakedToken.useCaptureGroups = true;
        leakedToken.selectedGroups = new java.util.ArrayList<>(List.of(1));
        report.highlights.add(leakedToken);
        RedactionRule emails = RedactionRule.create(
            "[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+",
            RuleTarget.RESPONSE,
            RedactionMode.BLUR,
            MatchMode.REGEX
        );
        emails.name = "Email addresses";
        report.redactions.add(emails);
        store.setDefaultTemplate(store.createTemplate("Client report", report).id);
        leakedToken = store.getDefaultTemplate().settings.highlights.getFirst();

        Settings internal = BuiltInTemplates.byId(BuiltInTemplates.EMPTY_ID).settings;
        store.createTemplate("Internal notes – full headers", internal);

        Settings slides = BuiltInTemplates.defaults();
        slides.theme = ThemeMode.DARK;
        slides.disposition = Disposition.HORIZONTAL;
        slides.widthMode = WidthMode.PIXEL;
        slides.widthPixels = 1200;
        slides.scale = 2;
        store.createTemplate("Slides (dark, 1200px)", slides);
        return store;
    }

    static JDialog dialog() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog dialog && dialog.isShowing()) {
                return dialog;
            }
        }
        throw new IllegalStateException("no window");
    }

    @SuppressWarnings("unchecked")
    static <T> T find(Container container, Class<T> type) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                return (T) child;
            }
            if (child instanceof Container inner) {
                T found = find(inner, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static final int MARGIN = 36;
    static final int TITLE = 28;

    /**
     * Paints the window's root pane at 2x inside a drawn macOS frame: rounded corners, a title bar with the traffic
     * lights ({@code title} null: no title bar), a hairline border and a soft shadow.
     */
    static void frame(javax.swing.JRootPane root, String title, boolean dark, File file) {
        try {
            int w = root.getWidth();
            int h = root.getHeight() + (title == null ? 0 : TITLE);
            int W = (w + 2 * MARGIN) * 2;
            int H = (h + 2 * MARGIN) * 2;
            java.awt.geom.RoundRectangle2D shape = new java.awt.geom.RoundRectangle2D.Double(
                MARGIN,
                MARGIN,
                w,
                h,
                20,
                20
            );

            // Shadow: the window's shape, blurred, a little lower.
            java.awt.image.BufferedImage shadow = new java.awt.image.BufferedImage(
                W,
                H,
                java.awt.image.BufferedImage.TYPE_INT_ARGB
            );
            java.awt.Graphics2D s = shadow.createGraphics();
            s.scale(2, 2);
            s.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            s.setColor(new java.awt.Color(0, 0, 0, dark ? 150 : 110));
            s.translate(0, 8);
            s.fill(shape);
            s.dispose();
            shadow = blur(shadow, 22);

            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                W,
                H,
                java.awt.image.BufferedImage.TYPE_INT_ARGB
            );
            java.awt.Graphics2D g = image.createGraphics();
            g.drawImage(shadow, 0, 0, null);
            g.scale(2, 2);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );
            // Cut the shadow out under the window, so translucent corners stay clean.
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fill(shape);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
            java.awt.Shape clip = g.getClip();
            g.clip(shape);
            g.translate(MARGIN, MARGIN);
            if (title != null) {
                g.setColor(new java.awt.Color(dark ? 0x2a2b2e : 0xe9e9eb));
                g.fillRect(0, 0, w, TITLE);
                g.setColor(new java.awt.Color(dark ? 0x1c1d20 : 0xd2d2d4));
                g.fillRect(0, TITLE - 1, w, 1);
                int[] lights = { 0xff5f57, 0xfebc2e, 0x28c840 };
                for (int i = 0; i < 3; i++) {
                    g.setColor(new java.awt.Color(lights[i]));
                    g.fill(new java.awt.geom.Ellipse2D.Double(14 + i * 20, TITLE / 2.0 - 6, 12, 12));
                }
                Font font = new javax.swing.JLabel().getFont().deriveFont(Font.BOLD, 13f);
                g.setFont(font);
                g.setColor(new java.awt.Color(dark ? 0xdcdcdc : 0x4d4d4d));
                java.awt.FontMetrics m = g.getFontMetrics();
                g.drawString(title, (w - m.stringWidth(title)) / 2, (TITLE - m.getHeight()) / 2 + m.getAscent());
                g.translate(0, TITLE);
            }
            javax.swing.RepaintManager.currentManager(root).setDoubleBufferingEnabled(false);
            root.paint(g);
            javax.swing.RepaintManager.currentManager(root).setDoubleBufferingEnabled(true);
            if (title != null) {
                g.translate(0, -TITLE);
            }
            g.translate(-MARGIN, -MARGIN);
            g.setClip(clip);
            g.setColor(new java.awt.Color(dark ? 0x525354 : 0xcacaca));
            g.setStroke(new java.awt.BasicStroke(0.5f));
            g.draw(shape);
            g.dispose();
            javax.imageio.ImageIO.write(image, "png", file);
            System.out.println(file.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Three box blurs of the given radius (in pixels at 2x): close to a Gaussian. */
    static java.awt.image.BufferedImage blur(java.awt.image.BufferedImage image, int radius) {
        int w = image.getWidth(),
            h = image.getHeight();
        int[] a = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                a[y * w + x] = image.getRGB(x, y) >>> 24;
            }
        }
        int[] b = new int[w * h];
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < h; y++) {
                int sum = 0;
                for (int x = -radius; x <= radius; x++) sum += a[y * w + Math.max(0, Math.min(w - 1, x))];
                for (int x = 0; x < w; x++) {
                    b[y * w + x] = sum / (2 * radius + 1);
                    sum += a[y * w + Math.min(w - 1, x + radius + 1)] - a[y * w + Math.max(0, x - radius)];
                }
            }
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int y = -radius; y <= radius; y++) sum += b[Math.max(0, Math.min(h - 1, y)) * w + x];
                for (int y = 0; y < h; y++) {
                    a[y * w + x] = sum / (2 * radius + 1);
                    sum += b[Math.min(h - 1, y + radius + 1) * w + x] - b[Math.max(0, y - radius) * w + x];
                }
            }
        }
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
            w,
            h,
            java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
        for (int i = 0; i < a.length; i++) {
            out.setRGB(i % w, i / w, a[i] << 24);
        }
        return out;
    }
}
