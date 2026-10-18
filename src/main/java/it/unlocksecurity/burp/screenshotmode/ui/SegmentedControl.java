package it.unlocksecurity.burp.screenshotmode.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * One choice among a few values, drawn as joined segments in a single outline (the Swing take on PrimeVue's
 * SelectButton). Colors come from the look and feel's button keys at paint time, so it follows Burp's theme.
 * Keyboard: Left/Right (or Up/Down) move the selection while focused.
 */
final class SegmentedControl<E> extends JComponent {

    private static final int PAD_X = 12;
    private static final int PAD_Y = 4;
    private static final int ARC = 8;

    private final List<E> values;
    private final List<String> labels = new ArrayList<>();
    private final Consumer<E> onChange;
    private int selected;
    private int hover = -1;

    SegmentedControl(E[] values, E selected, Consumer<E> onChange) {
        this(values, selected, String::valueOf, onChange);
    }

    SegmentedControl(E[] values, E selected, Function<E, String> label, Consumer<E> onChange) {
        this.values = List.of(values);
        this.onChange = onChange;
        for (E value : values) {
            labels.add(label.apply(value));
        }
        this.selected = Math.max(0, this.values.indexOf(selected));
        setFocusable(true);
        setOpaque(false);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    requestFocusInWindow();
                    select(segmentAt(e.getX()), true);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setHover(segmentAt(e.getX()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(-1);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(
            new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int step = switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> -1;
                        case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> 1;
                        default -> 0;
                    };
                    if (step != 0 && isEnabled()) {
                        select(
                            Math.floorMod(SegmentedControl.this.selected + step, SegmentedControl.this.values.size()),
                            true
                        );
                        e.consume();
                    }
                }
            }
        );
        addFocusListener(
            new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            }
        );
    }

    E value() {
        return values.get(selected);
    }

    /** Selects a value without notifying the listener. */
    void setValue(E value) {
        int index = values.indexOf(value);
        if (index >= 0) {
            select(index, false);
        }
    }

    void setLabel(E value, String label) {
        int index = values.indexOf(value);
        if (index >= 0 && !labels.get(index).equals(label)) {
            labels.set(index, label);
            revalidate();
            repaint();
        }
    }

    /** Simulates a click on a segment, for tests. */
    void click(E value) {
        select(values.indexOf(value), true);
    }

    private void select(int index, boolean notify) {
        if (index < 0 || index == selected) {
            return;
        }
        selected = index;
        repaint();
        getAccessibleContext().firePropertyChange(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, null, labels.get(index));
        if (notify) {
            onChange.accept(values.get(index));
        }
    }

    private void setHover(int index) {
        if (hover != index) {
            hover = index;
            repaint();
        }
    }

    private Font font() {
        Font font = UIManager.getFont("Button.font");
        return font != null ? font : getFont();
    }

    private int[] widths() {
        FontMetrics metrics = getFontMetrics(font());
        int[] widths = new int[labels.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = metrics.stringWidth(labels.get(i)) + 2 * PAD_X;
        }
        return widths;
    }

    private int segmentAt(int x) {
        int left = 0;
        int[] widths = widths();
        for (int i = 0; i < widths.length; i++) {
            if (x >= left && x < left + widths[i]) {
                return i;
            }
            left += widths[i];
        }
        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        int width = 1;
        for (int w : widths()) {
            width += w;
        }
        return new Dimension(width, getFontMetrics(font()).getHeight() + 2 * PAD_Y + 2);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int[] widths = widths();
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        int height = getHeight() - 1;
        Shape outline = new RoundRectangle2D.Double(0.5, 0.5, total, height, ARC, ARC);

        g.setColor(color(isEnabled() ? "Button.background" : "Panel.background", new Color(0xeeeeee)));
        g.fill(outline);
        int left = 0;
        for (int i = 0; i < widths.length; i++) {
            Area segment = new Area(outline);
            segment.intersect(new Area(new Rectangle2D.Double(left + 0.5, 0, widths[i], getHeight())));
            if (i == selected) {
                g.setColor(color("Button.selectedBackground", new Color(0x26649d)));
                if (!isEnabled()) {
                    g.setColor(blend(g.getColor(), color("Panel.background", Color.WHITE)));
                }
                g.fill(segment);
            } else if (i == hover && isEnabled()) {
                g.setColor(color("Button.hoverBackground", new Color(0xdfdfdf)));
                g.fill(segment);
            }
            left += widths[i];
        }

        g.setColor(color("Button.borderColor", new Color(0xdfdfdf)));
        g.setStroke(new BasicStroke(1f));
        left = 0;
        for (int i = 0; i < widths.length - 1; i++) {
            left += widths[i];
            if (i != selected && i + 1 != selected) {
                g.drawLine(left, 3, left, height - 3);
            }
        }
        if (isFocusOwner()) {
            g.setColor(color("Component.focusColor", new Color(0x5384b2)));
            g.setStroke(new BasicStroke(2f));
        }
        g.draw(outline);

        g.setFont(font());
        FontMetrics metrics = g.getFontMetrics();
        int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        left = 0;
        for (int i = 0; i < widths.length; i++) {
            Color text =
                i == selected
                    ? color("Button.selectedForeground", Color.WHITE)
                    : color("Button.foreground", Color.DARK_GRAY);
            if (!isEnabled()) {
                text = blend(text, color("Panel.background", Color.WHITE));
            }
            g.setColor(text);
            g.drawString(labels.get(i), left + PAD_X, baseline);
            left += widths[i];
        }
        g.dispose();
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    private static Color blend(Color a, Color b) {
        return new Color(
            (a.getRed() + 2 * b.getRed()) / 3,
            (a.getGreen() + 2 * b.getGreen()) / 3,
            (a.getBlue() + 2 * b.getBlue()) / 3
        );
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.RADIO_BUTTON;
                }

                @Override
                public String getAccessibleName() {
                    return labels.get(selected);
                }
            };
        }
        return accessibleContext;
    }
}
