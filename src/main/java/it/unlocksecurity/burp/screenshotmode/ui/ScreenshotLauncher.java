package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Opens the screenshot window, one at a time, restoring the state previously used for the same message. */
public final class ScreenshotLauncher {

    private static final int MAX_REMEMBERED = 50;

    private final Host host;
    private final TemplateStore store;
    private final Map<String, SessionState> states = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SessionState> eldest) {
            return size() > MAX_REMEMBERED;
        }
    };
    private ScreenshotDialog current;
    /** Counts {@link #open(Callable, Consumer)} and {@link #close()} calls: only the latest read opens a window. */
    private int reads;

    public ScreenshotLauncher(Host host, TemplateStore store) {
        this.host = host;
        this.store = store;
    }

    /**
     * Reads the message off the Swing thread, as copying and hashing a large one takes a while, then opens it. A read
     * that finishes after another {@code open} or a {@link #close()} is dropped.
     *
     * @param failed told of a failure to read the message or to open the window, on the Swing thread
     */
    public void open(Callable<MessageData> read, Consumer<Throwable> failed) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> open(read, failed));
            return;
        }
        int id = ++reads;
        Widgets.inBackground(
            read,
            data -> {
                if (id == reads) {
                    try {
                        open(data);
                    } catch (RuntimeException | StackOverflowError e) {
                        failed.accept(e);
                    }
                }
            },
            error -> {
                if (id == reads) {
                    failed.accept(error);
                }
            }
        );
    }

    public void open(MessageData data) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> open(data));
            return;
        }
        close();
        SessionState state = states.computeIfAbsent(data.key(), key -> {
            Template template = store.getDefaultTemplate();
            return new SessionState(template.settings.copy(), template.id);
        });
        current = new ScreenshotDialog(host.owner().get(), host, store, state, data);
        current.setVisible(true);
    }

    public void close() {
        reads++;
        if (current != null) {
            current.dispose();
            current = null;
        }
    }
}
