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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
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

    /**
     * A {@link #hint} that wraps at word boundaries to the width the layout gives it, instead of being cut off in a
     * narrow column. It asks for at most {@code width} pixels, so it never makes its column wider. A text area rather
     * than an {@code <html>} label, which Burp shows as markup.
     */
    static JTextArea wrappingHint(String text, int width) {
        JTextArea area = new JTextArea(text) {
            @Override
            public Dimension getPreferredSize() {
                // The height is the one for the current width, once laid out.
                Dimension size = super.getPreferredSize();
                return new Dimension(Math.min(size.width, width), size.height);
            }
        };
        JLabel style = hint("");
        area.setFont(style.getFont());
        area.setForeground(style.getForeground());
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setHighlighter(null);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        // A wrapping text area only knows its height once it has a width: lay out again when the line count changes.
        area.addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (area.getPreferredSize().height != area.getHeight()) {
                        SwingUtilities.invokeLater(area::revalidate);
                    }
                }
            }
        );
        return area;
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

    /**
     * Asks for a line of text, like {@link JOptionPane#showInputDialog(Component, Object, String, int)} but with OK as
     * the primary, default button.
     *
     * @return the text entered, or {@code null} when cancelled
     */
    static String askText(Component parent, String label, String title) {
        JTextField field = new JTextField(20);
        JButton ok = primary(button("OK"));
        JButton cancel = button("Cancel");
        JOptionPane pane = new JOptionPane(
            new Object[] { label, field },
            JOptionPane.PLAIN_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            new Object[] { ok, cancel }
        );
        // Custom option buttons don't close the pane on their own.
        ok.addActionListener(event -> pane.setValue(ok));
        cancel.addActionListener(event -> pane.setValue(cancel));
        JDialog dialog = pane.createDialog(parent, title);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.addWindowListener(
            new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent event) {
                    field.requestFocusInWindow();
                }
            }
        );
        dialog.setVisible(true);
        dialog.dispose();
        return pane.getValue() == ok ? field.getText() : null;
    }

    /** A borderless icon button, like the ones in Burp's toolbars; used where a bordered button would be noise. */
    static JButton flatIconButton(Icon icon, String tooltip, Runnable action) {
        JButton button = iconButton(icon, tooltip, action);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        return button;
    }

    static boolean isFlatLaf() {
        for (Class<?> c = UIManager.getLookAndFeel().getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("com.formdev.flatlaf.FlatLaf")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts {@code component} inside the right end of {@code field}, like the regex toggle of a search box. FlatLaf
     * (Burp) supports this natively; other look and feels get the component next to the field.
     */
    static JComponent withTrailing(JTextField field, JComponent component) {
        if (isFlatLaf()) {
            field.putClientProperty("JTextField.trailingComponent", component);
            return field;
        }
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.setOpaque(false);
        panel.add(field, BorderLayout.CENTER);
        panel.add(component, BorderLayout.EAST);
        return panel;
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

    /** A thin vertical rule separating groups of buttons in a toolbar. */
    static JComponent separator() {
        javax.swing.JSeparator separator = new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(9, 22));
        return separator;
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

    /** A rounded color square. */
    record SwatchIcon(Color color, int size) implements Icon {
        SwatchIcon(Color color) {
            this(color, 16);
        }

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
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    /** Vector icons drawn in the component's foreground color, so they follow Burp's light and dark themes. */
    enum Glyph implements Icon {
        PLUS,
        /** Neutral, red while the pointer is over its button: destructive, but calm in a long list of rules. */
        TRASH,
        /** Shows that a closed section opens, like the sections of Burp's Inspector; {@link #CHEVRON_UP} once open. */
        CHEVRON_DOWN,
        CHEVRON_UP;

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hot = c instanceof AbstractButton button && button.getModel().isRollover() && button.isEnabled();
            g.setColor(this == TRASH && hot ? errorColor() : c.getForeground());
            g.translate(x, y);
            switch (this) {
                case PLUS -> {
                    g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(7, 2, 7, 12);
                    g.drawLine(2, 7, 12, 7);
                }
                case TRASH -> {
                    g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Float(2, 3.5f, 12, 3.5f));
                    Path2D handle = new Path2D.Float();
                    handle.moveTo(5, 3.5f);
                    handle.lineTo(5.5f, 1.5f);
                    handle.lineTo(8.5f, 1.5f);
                    handle.lineTo(9, 3.5f);
                    g.draw(handle);
                    Path2D can = new Path2D.Float();
                    can.moveTo(3.2f, 3.5f);
                    can.lineTo(4, 12.5f);
                    can.lineTo(10, 12.5f);
                    can.lineTo(10.8f, 3.5f);
                    g.draw(can);
                    g.draw(new Line2D.Float(6, 6, 6, 10.5f));
                    g.draw(new Line2D.Float(8, 6, 8, 10.5f));
                }
                case CHEVRON_DOWN, CHEVRON_UP -> {
                    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    float tip = this == CHEVRON_DOWN ? 9.5f : 4.5f;
                    float ends = this == CHEVRON_DOWN ? 4.5f : 9.5f;
                    Path2D chevron = new Path2D.Float();
                    chevron.moveTo(3, ends);
                    chevron.lineTo(7, tip);
                    chevron.lineTo(11, ends);
                    g.draw(chevron);
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
