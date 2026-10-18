package it.unlocksecurity.burp.screenshotmode;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import java.awt.Component;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JMenuItem;

/**
 * "Screenshot" context menu item, available in message editors (Repeater, Proxy, ...) and in tables of requests
 * (Proxy history, site map, Logger, ...). With several rows selected, the first one is used, as in Caido.
 */
final class ScreenshotContextMenu implements ContextMenuItemsProvider {

    private final Consumer<HttpRequestResponse> open;

    ScreenshotContextMenu(Consumer<HttpRequestResponse> open) {
        this.open = open;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        Optional<HttpRequestResponse> target = target(
            event.messageEditorRequestResponse(),
            event.selectedRequestResponses()
        );
        if (target.isEmpty()) {
            return List.of();
        }
        JMenuItem item = new JMenuItem("Screenshot");
        item.addActionListener(action -> open.accept(target.get()));
        return List.of(item);
    }

    static Optional<HttpRequestResponse> target(
        Optional<MessageEditorHttpRequestResponse> editor,
        List<HttpRequestResponse> selected
    ) {
        if (editor.isPresent()) {
            return Optional.ofNullable(editor.get().requestResponse());
        }
        return selected
            .stream()
            .filter(item -> item != null && item.request() != null)
            .findFirst();
    }
}
