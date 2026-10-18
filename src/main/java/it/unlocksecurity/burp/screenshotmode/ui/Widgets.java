package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.render.RuleEngine;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** Small Swing building blocks shared by the screenshot window and the template editor. */
final class Widgets {

    private Widgets() {}

    static Color mutedColor() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : Color.GRAY;
    }

    static Color borderColor() {
        Color color = UIManager.getColor("Separator.foreground");
        return color != null ? color : Color.LIGHT_GRAY;
    }

    static Color errorColor() {
        return new Color(0xd9534f);
    }

    static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 1));
        label.setForeground(mutedColor());
        return label;
    }

    /** A titled block of the settings form, with an optional hint and an optional button on the right. */
    static JPanel section(String title, String hint, JComponent action, JComponent content) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JPanel labels = new JPanel(new BorderLayout(0, 2));
        labels.setOpaque(false);
        labels.add(title(title), BorderLayout.NORTH);
        if (hint != null) {
            labels.add(hint(hint), BorderLayout.CENTER);
        }
        header.add(labels, BorderLayout.CENTER);
        if (action != null) {
            JPanel actionHolder = new JPanel(new BorderLayout());
            actionHolder.setOpaque(false);
            actionHolder.add(action, BorderLayout.NORTH);
            header.add(actionHolder, BorderLayout.EAST);
        }

        JPanel section = new JPanel(new BorderLayout(0, 8));
        section.setOpaque(false);
        section.add(header, BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        return section;
    }

    /** A panel with a thin rounded outline, used for rule cards and template editor groups. */
    static JPanel card(LayoutManager layout) {
        JPanel card = new JPanel(layout);
        card.setOpaque(false);
        card.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor(), 1, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            )
        );
        return card;
    }

    static JPanel row(Component... components) {
        JPanel row = new JPanel(new WrapLayout(6, 0));
        row.setOpaque(false);
        for (Component component : components) {
            row.add(component);
        }
        return row;
    }

    /**
     * Burp's theme gives disabled buttons near-black bold text on a lighter background than enabled ones (Light
     * theme: {@code #4B4B4B} on white vs {@code #2B2B2B} on {@code #EEEEEE}), so they look clickable. Fade text,
     * background and border into the panel instead. A FlatLaf style is resolved against the current theme, so it
     * follows Burp's light/dark switch; other look and feels ignore it.
     */
    private static final String DISABLED_STYLE =
        "disabledText: mix($Button.foreground,$Panel.background,35%);" +
        "disabledBackground: $Panel.background;" +
        "disabledBorderColor: mix($Button.borderColor,$Panel.background,60%)";

    static <B extends AbstractButton> B greyedWhenDisabled(B button) {
        button.putClientProperty("FlatLaf.style", DISABLED_STYLE);
        return button;
    }

    static JButton button(String text) {
        return greyedWhenDisabled(new JButton(text));
    }

    static JButton iconButton(Icon icon, String tooltip, Runnable action) {
        JButton button = greyedWhenDisabled(new JButton(icon));
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(4, 6, 4, 6));
        button.addActionListener(event -> action.run());
        return button;
    }

    static <E> SegmentedControl<E> segmented(E[] values, E selected, Consumer<E> onChange) {
        return new SegmentedControl<>(values, selected, onChange);
    }

    /** Burp's orange call-to-action style ({@code [style]Button.primary} in its theme); one per view. */
    static JButton primary(JButton button) {
        button.putClientProperty("FlatLaf.styleClass", "primary");
        return button;
    }

    /** A small rounded status label, such as "Modified", in the theme's warning tones. */
    static JLabel chip(String text) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(themeColor("Colors.palette.warning.7", new Color(0xf3e2c9)));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.dispose();
                setForeground(themeColor("Colors.palette.warning.2", new Color(0x976a14)));
                super.paintComponent(graphics);
            }
        };
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 1));
        label.setBorder(BorderFactory.createEmptyBorder(2, 9, 2, 9));
        return label;
    }

    /** A color of Burp's palette (theme JSON), resolved at paint time so it follows theme switches. */
    static Color themeColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    static JButton colorButton(String hex, Consumer<String> onChange) {
        JButton button = greyedWhenDisabled(new JButton());
        button.setMargin(new Insets(4, 6, 4, 6));
        button.setToolTipText("Choose color");
        button.setIcon(new SwatchIcon(RuleEngine.parseColor(hex, Color.YELLOW)));
        button.addActionListener(event -> {
            Color current = ((SwatchIcon) button.getIcon()).color;
            Color chosen = JColorChooser.showDialog(button, "Choose Color", current);
            if (chosen != null) {
                button.setIcon(new SwatchIcon(chosen));
                onChange.accept(RuleEngine.toHex(chosen));
            }
        });
        return button;
    }

    static void onTextChange(JTextComponent component, Consumer<String> onChange) {
        component.getDocument().addDocumentListener(
            new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    onChange.accept(component.getText());
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    onChange.accept(component.getText());
                }

                @Override
                public void changedUpdate(DocumentEvent e) {}
            }
        );
    }

    static void placeholder(JTextComponent component, String text) {
        // Honoured by FlatLaf-based look and feels such as Burp's; harmless elsewhere.
        component.putClientProperty("JTextField.placeholderText", text);
        if (component.getToolTipText() == null) {
            component.setToolTipText(text);
        }
    }

    /** Coalesces bursts of calls (e.g. typing) into one run after a short pause. */
    static Runnable debounce(int millis, Runnable action) {
        Timer timer = new Timer(millis, event -> action.run());
        timer.setRepeats(false);
        return timer::restart;
    }

    /** Vertical stack of full-width components, top-aligned. */
    static final class Stack extends JPanel implements Scrollable {

        private final int gap;
        private int count;
        private final boolean tracksWidth;

        Stack(int gap, boolean tracksWidth) {
            super(new GridBagLayout());
            this.gap = gap;
            this.tracksWidth = tracksWidth;
            setOpaque(false);
            addFiller();
        }

        Stack(int gap) {
            this(gap, true);
        }

        void addItem(Component component) {
            remove(getComponentCount() - 1);
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = count++;
            constraints.weightx = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.NORTH;
            constraints.insets = new Insets(constraints.gridy == 0 ? 0 : gap, 0, 0, 0);
            add(component, constraints);
            addFiller();
        }

        void clear() {
            removeAll();
            count = 0;
            addFiller();
        }

        private void addFiller() {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 10_000;
            constraints.weighty = 1;
            add(javax.swing.Box.createGlue(), constraints);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return tracksWidth;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }

    private record SwatchIcon(Color color) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillRoundRect(x, y, getIconWidth(), getIconHeight(), 4, 4);
            g.setColor(Color.GRAY);
            g.drawRoundRect(x, y, getIconWidth() - 1, getIconHeight() - 1, 4, 4);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    /** Vector icons drawn in the component's foreground color, so they follow Burp's light and dark themes. */
    enum Glyph implements Icon {
        PLUS,
        DELETE;

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(this == DELETE ? errorColor() : c.getForeground());
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.translate(x, y);
            switch (this) {
                case PLUS -> {
                    g.drawLine(7, 2, 7, 12);
                    g.drawLine(2, 7, 12, 7);
                }
                case DELETE -> {
                    g.drawLine(3, 3, 11, 11);
                    g.drawLine(11, 3, 3, 11);
                }
            }
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}
