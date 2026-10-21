package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.render.Decorated;
import it.unlocksecurity.burp.screenshotmode.render.HttpSyntax;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.TransferHandler;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.LabelView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/**
 * Read-only, line-wrapping text view of one HTTP message with syntax colors and screenshot decorations.
 * Blur has no Swing equivalent, so the affected rows are post-processed on an offscreen image while painting.
 * Blur and opaque redactions only hide text on screen, so copying a selection replaces them with a placeholder.
 */
final class MessageView extends JTextPane {

    private static final float LINE_SPACING = 0.3f;
    /** CSS {@code blur(4px)}, approximated by three box blur passes. */
    private static final double BLUR_RADIUS = 4;

    private final float lineSpacing;
    private String text = "";
    private List<Decorated.Span> blurSpans = List.of();
    /** Blurred and covered ranges: still in the document, so never copied as they are. */
    private List<Decorated.Span> hiddenSpans = List.of();
    private List<Rectangle2D> blurRects;
    private int blurRectsWidth = -1;

    MessageView() {
        this(LINE_SPACING);
    }

    /** @param lineSpacing extra space between rows, as a fraction of the font height */
    MessageView(float lineSpacing) {
        this.lineSpacing = lineSpacing;
        setEditorKit(new WrapAnywhereEditorKit());
        setEditable(false);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        if (getCaret() instanceof DefaultCaret caret) {
            caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        }
        // Copy and drag go through here: read-only, so nothing is ever imported.
        setTransferHandler(
            new TransferHandler() {
                @Override
                public int getSourceActions(JComponent component) {
                    return COPY;
                }

                @Override
                protected Transferable createTransferable(JComponent component) {
                    return new StringSelection(copyText(getSelectionStart(), getSelectionEnd()));
                }
            }
        );
    }

    /**
     * The text between two offsets as it may leave the screenshot: each blurred or covered run becomes
     * {@code [REDACTED]}, like a replace redaction.
     */
    String copyText(int from, int to) {
        // Overlapping or touching ranges are one run, with one placeholder.
        List<Decorated.Span> sorted = new ArrayList<>(hiddenSpans);
        sorted.sort(Comparator.comparingInt(Decorated.Span::from));
        List<int[]> runs = new ArrayList<>();
        for (Decorated.Span span : sorted) {
            if (!runs.isEmpty() && span.from() <= runs.getLast()[1]) {
                runs.getLast()[1] = Math.max(runs.getLast()[1], span.to());
            } else {
                runs.add(new int[] { span.from(), span.to() });
            }
        }
        StringBuilder copy = new StringBuilder();
        int position = from;
        for (int[] run : runs) {
            if (run[1] <= position || run[0] >= to) {
                continue;
            }
            copy.append(text, position, Math.max(position, run[0]));
            copy.append(RedactionRule.DEFAULT_REPLACEMENT);
            position = Math.min(run[1], to);
        }
        if (position < to) {
            copy.append(text, position, to);
        }
        return copy.toString();
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    String text() {
        return text;
    }

    void render(Decorated decorated, List<HttpSyntax.Token> tokens, Palette palette, Font font) {
        text = decorated.text();
        DefaultStyledDocument document = new DefaultStyledDocument();
        try {
            SimpleAttributeSet base = new SimpleAttributeSet();
            StyleConstants.setFontFamily(base, font.getFamily());
            StyleConstants.setFontSize(base, font.getSize());
            StyleConstants.setForeground(base, palette.text());
            document.insertString(0, text, base);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }

        SimpleAttributeSet paragraph = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(paragraph, lineSpacing);
        document.setParagraphAttributes(0, text.length(), paragraph, false);

        for (HttpSyntax.Token token : tokens) {
            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setForeground(
                style,
                palette.forToken(token.type(), text.substring(token.from(), token.to()))
            );
            if (token.type() == HttpSyntax.TokenType.STATUS_CODE) {
                StyleConstants.setBold(style, true);
            }
            document.setCharacterAttributes(token.from(), token.to() - token.from(), style, false);
        }

        for (Decorated.Span span : decorated.spans()) {
            if (span.kind() == Decorated.Kind.OPAQUE || span.kind() == Decorated.Kind.REPLACED) {
                SimpleAttributeSet style = new SimpleAttributeSet();
                boolean opaque = span.kind() == Decorated.Kind.OPAQUE;
                StyleConstants.setBackground(style, palette.redaction());
                StyleConstants.setForeground(style, opaque ? palette.redaction() : palette.redactionText());
                document.setCharacterAttributes(span.from(), span.to() - span.from(), style, false);
            }
        }

        getHighlighter().removeAllHighlights();
        setFont(font);
        setBackground(palette.background());
        setForeground(palette.text());
        setSelectionColor(palette.selection());
        setSelectedTextColor(null);
        setDocument(document);

        for (Decorated.Span span : decorated.spans()) {
            if (span.kind() == Decorated.Kind.HIGHLIGHT || span.kind() == Decorated.Kind.RECTANGLE) {
                try {
                    getHighlighter().addHighlight(
                        span.from(),
                        span.to(),
                        new RulePainter(span.kind(), span.color(), text)
                    );
                } catch (BadLocationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        blurSpans = decorated.spans(Decorated.Kind.BLUR);
        hiddenSpans = decorated
            .spans()
            .stream()
            .filter(span -> span.kind() == Decorated.Kind.BLUR || span.kind() == Decorated.Kind.OPAQUE)
            .toList();
        blurRects = null;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (blurSpans.isEmpty()) {
            super.paintComponent(graphics);
            return;
        }

        Graphics2D g = (Graphics2D) graphics;
        Rectangle clip = g.getClipBounds();
        Rectangle bounds = new Rectangle(0, 0, getWidth(), getHeight());
        clip = clip == null ? bounds : clip.intersection(bounds);
        if (clip.isEmpty()) {
            return;
        }

        // Paint at device resolution so HiDPI screens and 2x captures stay sharp.
        AffineTransform transform = g.getTransform();
        double scaleX = Math.max(1, Math.abs(transform.getScaleX()));
        double scaleY = Math.max(1, Math.abs(transform.getScaleY()));
        BufferedImage image = new BufferedImage(
            (int) Math.ceil(clip.width * scaleX),
            (int) Math.ceil(clip.height * scaleY),
            BufferedImage.TYPE_INT_RGB
        );
        Graphics2D offscreen = image.createGraphics();
        offscreen.setRenderingHints(g.getRenderingHints());
        offscreen.scale(scaleX, scaleY);
        offscreen.translate(-clip.x, -clip.y);
        offscreen.setClip(clip);
        super.paintComponent(offscreen);
        offscreen.dispose();

        int radius = (int) Math.round(BLUR_RADIUS * scaleX);
        for (Rectangle2D rect : blurRects()) {
            if (!rect.intersects(clip)) {
                continue;
            }
            int x0 = (int) Math.floor((rect.getMinX() - clip.x) * scaleX);
            int y0 = (int) Math.floor((rect.getMinY() - clip.y) * scaleY);
            int x1 = (int) Math.ceil((rect.getMaxX() - clip.x) * scaleX);
            int y1 = (int) Math.ceil((rect.getMaxY() - clip.y) * scaleY);
            Rectangle region = new Rectangle(x0, y0, x1 - x0, y1 - y0).intersection(
                new Rectangle(0, 0, image.getWidth(), image.getHeight())
            );
            if (!region.isEmpty()) {
                Blur.apply(image, region, radius);
            }
        }

        g.drawImage(image, clip.x, clip.y, clip.width, clip.height, null);
    }

    private List<Rectangle2D> blurRects() {
        if (blurRects == null || blurRectsWidth != getWidth()) {
            List<Rectangle2D> rects = new ArrayList<>();
            for (Decorated.Span span : blurSpans) {
                rects.addAll(RowRects.of(this, text, span.from(), span.to()));
            }
            blurRects = rects;
            blurRectsWidth = getWidth();
        }
        return blurRects;
    }

    /**
     * Wraps runs without break opportunities (tokens, base64, long URLs) at any character, like Burp's and Caido's
     * editors. Swing only breaks a run at word boundaries when its minimum span fits the row, so the longest word
     * would otherwise overflow the view and be clipped.
     */
    private static final class WrapAnywhereEditorKit extends StyledEditorKit {

        private final ViewFactory factory = new ViewFactory() {
            private final ViewFactory delegate = WrapAnywhereEditorKit.super.getViewFactory();

            @Override
            public View create(Element element) {
                if (!AbstractDocument.ContentElementName.equals(element.getName())) {
                    return delegate.create(element);
                }
                return new LabelView(element) {
                    @Override
                    public float getMinimumSpan(int axis) {
                        return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
                    }
                };
            }
        };

        @Override
        public ViewFactory getViewFactory() {
            return factory;
        }
    }

    /** Paints highlight rules: a translucent fill with an underline, or an outline ({@code rectangle} mode). */
    private static final class RulePainter implements Highlighter.HighlightPainter {

        private final Decorated.Kind kind;
        private final Color color;
        private final String text;
        private List<Rectangle2D> rows;
        private int rowsWidth = -1;

        RulePainter(Decorated.Kind kind, Color color, String text) {
            this.kind = kind;
            this.color = color;
            this.text = text;
        }

        @Override
        public void paint(Graphics graphics, int p0, int p1, Shape bounds, JTextComponent component) {
            if (rows == null || rowsWidth != component.getWidth()) {
                rows = RowRects.of(component, text, p0, p1);
                rowsWidth = component.getWidth();
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (Rectangle2D row : rows) {
                if (kind == Decorated.Kind.RECTANGLE) {
                    // CSS "outline: 2px solid; outline-offset: 1px": a 2px stroke centred 2px outside the box.
                    g.setColor(color);
                    g.setStroke(new BasicStroke(2f));
                    g.draw(
                        new Rectangle2D.Double(row.getX() - 2, row.getY() - 2, row.getWidth() + 4, row.getHeight() + 4)
                    );
                } else {
                    g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 0x40));
                    g.fill(row);
                    g.setColor(color);
                    g.fill(new Rectangle2D.Double(row.getX(), row.getMaxY() - 2, row.getWidth(), 2));
                }
            }
            g.dispose();
        }
    }
}
