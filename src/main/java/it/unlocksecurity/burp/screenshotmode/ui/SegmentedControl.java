package it.unlocksecurity.burp.screenshotmode.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;

/**
 * One choice among a few values: standard toggle buttons in a {@link ButtonGroup}, joined in one rounded outline and
 * styled as FlatLaf toolbar buttons, so each option is a real button that assistive technology announces with its
 * state. Like a group of radio buttons, it is a single Tab stop: only the selected button takes focus, and Left/Right
 * (or Up/Down) move the selection.
 */
final class SegmentedControl<E> extends JPanel {

    /**
     * Disabled, the selected option would get the panel's background and Burp's near-black disabled text, standing out
     * from the faded others as if it alone were enabled: fade its fill and text instead, so it still shows as chosen.
     */
    private static final String DISABLED_SELECTED =
        ";toolbar.disabledSelectedBackground: mix($Button.toolbar.selectedBackground,$Panel.background,35%)" +
        ";toolbar.disabledSelectedForeground: mix($Button.toolbar.selectedForeground,$Panel.background,45%)";

    private final List<E> values;
    private final List<JToggleButton> buttons = new ArrayList<>();
    private final Consumer<E> onChange;
    private int selected;

    SegmentedControl(E[] values, E selected, Consumer<E> onChange) {
        this(values, selected, String::valueOf, onChange);
    }

    SegmentedControl(E[] values, E selected, Function<E, String> label, Consumer<E> onChange) {
        // Wraps an option that doesn't fit to a second row, inside the outline, rather than hiding it (large fonts).
        super(new WrapLayout(0, 0, true));
        this.values = List.of(values);
        this.onChange = onChange;
        this.selected = Math.max(0, this.values.indexOf(selected));
        setOpaque(false);
        setBorder(new Outline());

        ButtonGroup group = new ButtonGroup();
        KeyAdapter arrows = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int step = switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> -1;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> 1;
                    default -> 0;
                };
                if (step != 0 && isEnabled()) {
                    int next = Math.floorMod(
                        SegmentedControl.this.selected + step,
                        SegmentedControl.this.values.size()
                    );
                    select(next, true);
                    buttons.get(next).requestFocusInWindow();
                    e.consume();
                }
            }
        };
        FocusAdapter focus = new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        };
        for (int i = 0; i < values.length; i++) {
            int index = i;
            JToggleButton button = Widgets.greyedWhenDisabled(new JToggleButton(label.apply(values[i])));
            button.putClientProperty("FlatLaf.style", button.getClientProperty("FlatLaf.style") + DISABLED_SELECTED);
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.setMargin(new Insets(2, 10, 2, 10));
            button.addActionListener(event -> select(index, true));
            button.addKeyListener(arrows);
            button.addFocusListener(focus);
            group.add(button);
            buttons.add(button);
            add(button);
        }
        select(this.selected, false);
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
        if (index >= 0 && !buttons.get(index).getText().equals(label)) {
            buttons.get(index).setText(label);
            revalidate();
            repaint();
        }
    }

    /** Simulates a click on an option, for tests. */
    void click(E value) {
        buttons.get(values.indexOf(value)).doClick();
    }

    /** The button of an option, for tests. */
    JToggleButton button(E value) {
        return buttons.get(values.indexOf(value));
    }

    @Override
    public void setToolTipText(String text) {
        super.setToolTipText(text);
        // Set on the panel only, it would never show: the buttons cover it.
        if (buttons != null) {
            buttons.forEach(button -> button.setToolTipText(text));
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (buttons != null) {
            buttons.forEach(button -> button.setEnabled(enabled));
        }
    }

    private void select(int index, boolean notify) {
        boolean changed = index != selected;
        selected = index;
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setSelected(i == index);
            // A single Tab stop, as for a group of radio buttons.
            buttons.get(i).setFocusable(i == index);
        }
        if (notify && changed) {
            onChange.accept(values.get(index));
        }
    }

    private boolean hasFocus(Component component) {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner != null && SwingUtilities.isDescendingFrom(owner, component);
    }

    /** The rounded outline joining the buttons, in the focus color while one of them has focus. */
    private final class Outline extends AbstractBorder {

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(2, 2, 2, 2);
            return insets;
        }

        @Override
        public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            // Wrapped, the control takes the width of its cell: the outline hugs the widest row instead.
            int right = 0;
            for (Component button : ((Container) c).getComponents()) {
                right = Math.max(right, button.getX() + button.getWidth());
            }
            width = Math.min(width, right + getBorderInsets(c).right - x);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = hasFocus(c);
            g.setColor(color(focused ? "Component.focusColor" : "Component.borderColor", Color.GRAY));
            g.setStroke(new BasicStroke(focused ? 2f : 1f));
            float inset = focused ? 1f : 0.5f;
            int arc = Math.max(UIManager.getInt("Component.arc"), 6);
            g.draw(new RoundRectangle2D.Float(x + inset, y + inset, width - 2 * inset, height - 2 * inset, arc, arc));
            g.dispose();
        }
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
