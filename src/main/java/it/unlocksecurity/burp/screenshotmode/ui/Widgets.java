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
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
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

    static JButton iconButton(Icon icon, String tooltip, Runnable action) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(4, 6, 4, 6));
        button.addActionListener(event -> action.run());
        return button;
    }

    /** Mutually exclusive toggle buttons, the Swing take on PrimeVue's SelectButton. */
    static <E> JPanel segmented(E[] values, E selected, Consumer<E> onChange) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        // A FlowLayout gap would also pad the outer edges, so separate the buttons with struts instead.
        int gap = 0;
        for (E value : values) {
            JToggleButton button = new JToggleButton(value.toString(), value.equals(selected));
            button.setMargin(new Insets(3, 10, 3, 10));
            button.addActionListener(event -> onChange.accept(value));
            group.add(button);
            if (gap > 0) {
                panel.add(javax.swing.Box.createHorizontalStrut(gap));
            }
            panel.add(button);
            gap = 6;
        }
        return panel;
    }

    /** Selects the button of a {@link #segmented} panel showing the given value, without firing listeners. */
    static void selectSegment(JPanel segmented, Object value) {
        for (Component component : segmented.getComponents()) {
            if (component instanceof AbstractButton button && button.getText().equals(value.toString())) {
                button.getModel().setSelected(true);
            }
        }
    }

    static JButton colorButton(String hex, Consumer<String> onChange) {
        JButton button = new JButton();
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
        DELETE,
        RESET;

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
                case RESET -> {
                    g.draw(new Arc2D.Double(2, 2, 10, 10, 90, 270, Arc2D.OPEN));
                    Path2D arrow = new Path2D.Double();
                    arrow.moveTo(5, 0);
                    arrow.lineTo(7.5, 2);
                    arrow.lineTo(5, 4.5);
                    g.draw(arrow);
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
