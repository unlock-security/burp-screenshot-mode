package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.MessageText;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.storage.PreferenceStore;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Renders screenshots without Burp and writes them to {@code build/preview} for visual inspection. Run with
 * {@code ./gradlew test -PpreviewUi} on a desktop to also render the screenshot window and the suite tab.
 */
class RenderPreviewTest {

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    private static final Font UI = new Font(Font.SANS_SERIF, Font.PLAIN, 13);

    static MessageData sample() {
        String request = """
        POST /api/v1/users/42/profile?expand=roles HTTP/1.1\r
        Host: app.example.com\r
        User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)\r
        Accept: application/json\r
        Accept-Encoding: gzip, deflate, br\r
        Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiIsInJvbGUiOiJ1c2VyIn0.c2lnbmF0dXJl\r
        Cookie: session=7f3c9a1b2d4e5f60; theme=dark\r
        Content-Type: application/json\r
        Content-Length: 61\r
        \r
        {"displayName": "Mallory", "role": "admin", "verified": true}""";
        String response = """
        HTTP/1.1 200 OK\r
        Date: Sat, 18 Oct 2026 10:00:00 GMT\r
        Content-Type: application/json; charset=utf-8\r
        Server: nginx\r
        X-Backend: 192.168.10.23\r
        \r
        {"id": 42, "displayName": "Mallory", "role": "admin", "email": "mallory@example.com", "internalNote": "privilege escalation via mass assignment of the role attribute", "updated": "2026-10-18T10:00:00Z"}""";
        return new MessageData(
            "sample",
            "https://app.example.com:443/api/v1/users/42/profile?expand=roles",
            MessageText.fromBytes(request.getBytes(StandardCharsets.UTF_8)),
            MessageText.fromBytes(response.getBytes(StandardCharsets.UTF_8)),
            response.length(),
            187L,
            ZonedDateTime.parse("2026-10-18T12:00:00+02:00")
        );
    }

    static Settings sampleSettings() {
        Settings settings = Settings.defaults();
        settings.showTimestamp = true;

        HighlightRule role = HighlightRule.create("\"role\": \"admin\"", RuleTarget.REQUEST, MatchMode.STRING);
        HighlightRule escalated = HighlightRule.create("\"role\": \"admin\"", RuleTarget.RESPONSE, MatchMode.STRING);
        escalated.mode = HighlightMode.RECTANGLE;
        escalated.color = "#ff4d4f";
        HighlightRule note = HighlightRule.create("privilege escalation", RuleTarget.RESPONSE, MatchMode.STRING);
        note.color = "#4ade80";
        settings.highlights.add(role);
        settings.highlights.add(escalated);
        settings.highlights.add(note);

        RedactionRule token = RedactionRule.create(
            "Bearer (\\S+)",
            RuleTarget.REQUEST,
            RedactionMode.BLUR,
            MatchMode.REGEX
        );
        token.useCaptureGroups = true;
        token.selectedGroups.add(1);
        RedactionRule session = RedactionRule.create(
            "session=([0-9a-f]+)",
            RuleTarget.REQUEST,
            RedactionMode.REPLACE,
            MatchMode.REGEX
        );
        session.useCaptureGroups = true;
        session.selectedGroups.add(1);
        RedactionRule ip = RedactionRule.create(
            "192\\.168\\.\\d+\\.\\d+",
            RuleTarget.RESPONSE,
            RedactionMode.OPAQUE,
            MatchMode.REGEX
        );
        RedactionRule email = RedactionRule.create(
            "[\\w.]+@example\\.com",
            RuleTarget.RESPONSE,
            RedactionMode.BLUR,
            MatchMode.REGEX
        );
        settings.redactions.add(token);
        settings.redactions.add(session);
        settings.redactions.add(ip);
        settings.redactions.add(email);
        return settings;
    }

    private static BufferedImage render(Settings settings, Palette palette, int width, int height) throws Exception {
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ContentPanel content = new ContentPanel();
            content.render(sample(), settings, palette, MONO, UI, "18 Oct 2026, 12:00:00", "214 bytes | 187ms");
            content.setSize(width, height);
            layoutTree(content);
            content.setSplitRatio(0.5);
            layoutTree(content);
            image.set(ImageCapture.render(content));
        });
        return image.get();
    }

    static void layoutTree(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static File output(String name) {
        File directory = new File(System.getProperty("preview.dir", "build/preview"));
        directory.mkdirs();
        return new File(directory, name);
    }

    private static boolean hasVariation(BufferedImage image) {
        int first = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y += 7) {
            for (int x = 0; x < image.getWidth(); x += 7) {
                if (image.getRGB(x, y) != first) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void rendersSideBySideDark() throws Exception {
        BufferedImage image = render(sampleSettings(), Palette.DARK, 1100, 520);
        assertEquals(2200, image.getWidth());
        assertEquals(1040, image.getHeight());
        assertTrue(hasVariation(image));
        ImageCapture.save(image, output("side-by-side-dark.png"));
    }

    @Test
    void rendersStackedLight() throws Exception {
        Settings settings = sampleSettings();
        settings.disposition = Disposition.VERTICAL;
        settings.theme = ThemeMode.LIGHT;
        BufferedImage image = render(settings, Palette.LIGHT, 612, 900);
        assertTrue(hasVariation(image));
        ImageCapture.save(image, output("stacked-light.png"));
    }

    @Test
    void rendersPrettyBodies() throws Exception {
        Settings settings = sampleSettings();
        settings.view = ViewMode.PRETTY;
        BufferedImage image = render(settings, Palette.DARK, 1100, 700);
        assertTrue(hasVariation(image));
        ImageCapture.save(image, output("pretty-dark.png"));
    }

    @Test
    void blurAltersThePixelsItCovers() throws Exception {
        Settings blurred = Settings.defaults();
        blurred.redactions.add(
            RedactionRule.create("Mallory", RuleTarget.REQUEST, RedactionMode.BLUR, MatchMode.STRING)
        );
        Settings plain = Settings.defaults();
        BufferedImage a = render(blurred, Palette.DARK, 800, 700);
        BufferedImage b = render(plain, Palette.DARK, 800, 700);
        int differing = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        assertTrue(differing > 100, "blur should change the redacted area, changed " + differing);
        assertTrue(differing < (a.getWidth() * a.getHeight()) / 20, "blur must stay local, changed " + differing);
    }

    @Test
    void rendersWindowsWhenRequested() throws Exception {
        if (System.getProperty("preview.ui") == null || GraphicsEnvironment.isHeadless()) {
            return;
        }
        Map<String, String> values = new HashMap<>();
        PreferenceStore preferences = new PreferenceStore() {
            @Override
            public String get(String key) {
                return values.get(key);
            }

            @Override
            public void set(String key, String value) {
                values.put(key, value);
            }
        };
        Host host = new Host(
            () -> MONO,
            () -> UI,
            () -> true,
            () -> null,
            System.out::println,
            System.err::println,
            preferences
        );
        TemplateStore store = new TemplateStore(preferences, System.out::println);
        store.initialize();
        store.updateTemplate(store.defaultTemplateId(), null, sampleSettings());

        SwingUtilities.invokeAndWait(() -> {
            ScreenshotLauncher launcher = new ScreenshotLauncher(host, store);
            launcher.open(sample());
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> {
            for (java.awt.Window window : java.awt.Window.getWindows()) {
                if (window instanceof ScreenshotDialog dialog && dialog.isShowing()) {
                    saveComponent((JComponent) dialog.getContentPane(), "window.png");
                    dialog.dispose();
                }
            }
            TemplatesTab tab = new TemplatesTab(store, host);
            tab.setSize(1200, 500);
            layoutTree(tab);
            layoutTree(tab);
            saveComponent(tab, "templates-tab.png");

            for (boolean wide : new boolean[] { false, true }) {
                Widgets.Stack holder = new Widgets.Stack(0);
                holder.setOpaque(true);
                holder.addItem(new SettingsForm(sampleSettings(), () -> {}, MONO, wide));
                holder.setSize(wide ? 1100 : 360, 2000);
                layoutTree(holder);
                layoutTree(holder);
                holder.setSize(holder.getWidth(), holder.getPreferredSize().height);
                layoutTree(holder);
                saveComponent(holder, wide ? "form-wide.png" : "form-sidebar.png");
            }
        });
    }

    private static void saveComponent(JComponent component, String name) {
        BufferedImage image = new BufferedImage(
            component.getWidth(),
            component.getHeight(),
            BufferedImage.TYPE_INT_RGB
        );
        java.awt.Graphics2D g = image.createGraphics();
        component.printAll(g);
        g.dispose();
        try {
            ImageCapture.save(image, output(name));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
