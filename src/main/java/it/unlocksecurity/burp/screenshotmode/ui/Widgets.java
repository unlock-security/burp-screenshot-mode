package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.render.Palette;
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
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/** Small Swing building blocks shared by the screenshot window and the template editor. */
final class Widgets {

    private Widgets() {}

    private static final Color MUTED = new ThemeColor("Label.disabledForeground", Color.GRAY);
    private static final Color ERROR = new ThemeColor("Colors.ui.text.error", new Color(0xc4314b));
    private static final Color SUCCESS = new ThemeColor("Colors.ui.icon.success", new Color(0x387723));
    private static final Color LINK = new ThemeColor("Colors.ui.text.link", new Color(0xad5b28));

    /** Secondary text, such as hints. */
    static Color mutedColor() {
        return MUTED;
    }

    static Color borderColor() {
        Color color = UIManager.getColor("Separator.foreground");
        return color != null ? color : Color.LIGHT_GRAY;
    }

    static Color errorColor() {
        return ERROR;
    }

    static Color successColor() {
        return SUCCESS;
    }

    /** Burp's color for text that takes you somewhere, such as a link back to a list. */
    static Color linkColor() {
        return LINK;
    }

    /**
     * A text color of Burp's theme, looked up each time it is painted. Components keep a color set with
     * {@code setForeground} across Burp's theme switches, so a fixed color would stay the old theme's. Where the theme's
     * color is too faint for text on the panel background (Burp Dark's disabled grey), it is mixed with the label
     * color until it reaches 4.5:1 (WCAG AA).
     */
    private static final class ThemeColor extends Color {

        private final String key;
        private final Color fallback;

        ThemeColor(String key, Color fallback) {
            super(fallback.getRGB());
            this.key = key;
            this.fallback = fallback;
        }

        @Override
        public int getRGB() {
            Color color = themeColor(key, fallback);
            Color background = themeColor("Panel.background", Color.WHITE);
            Color text = themeColor("Label.foreground", Color.BLACK);
            Color readable = color;
            for (int step = 1; step <= 10 && Palette.contrast(readable, background) < 4.5; step++) {
                readable = mix(color, text, step / 10.0);
            }
            return readable.getRGB();
        }

        private static Color mix(Color from, Color to, double amount) {
            return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount)
            );
        }
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

    /** The menu shortcut of a key as written in tooltips: "⌘Z" on macOS, "Ctrl+Z" elsewhere. */
    static String shortcut(int keyCode) {
        String key = KeyEvent.getKeyText(keyCode);
        return isMac() ? "⌘" + key : "Ctrl+" + key;
    }

    /** The modifier of menu shortcuts: ⌘ on macOS, Ctrl elsewhere. Unlike the toolkit's, also known headless. */
    static int menuShortcutMask() {
        return isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    /**
     * Asks for a line of text, like {@link JOptionPane#showInputDialog(Component, Object, String, int)} but with OK as
     * the primary, default button.
     *
     * @return the text entered, or {@code null} when cancelled
     */
    static String askText(Component parent, String label, String title) {
        return askText(parent, label, title, text -> null);
    }

    /**
     * Like {@link #askText(Component, String, String)}, with OK disabled while {@code problem} finds one in the text:
     * it returns what is wrong, shown under the field, {@code ""} to only disable OK, or {@code null} if nothing is.
     */
    static String askText(Component parent, String label, String title, Function<String, String> problem) {
        JTextField field = new JTextField(20);
        JButton ok = primary(button("OK"));
        JButton cancel = button("Cancel");
        // A space keeps a line for the message, which the dialog is sized for when it opens.
        JLabel error = new JLabel(" ");
        error.setForeground(errorColor());
        Runnable check = () -> {
            String found = problem.apply(field.getText());
            ok.setEnabled(found == null);
            error.setText(found == null || found.isEmpty() ? " " : found);
            field.putClientProperty("JComponent.outline", found == null || found.isEmpty() ? null : "error");
            field.getAccessibleContext().setAccessibleDescription(found == null || found.isEmpty() ? null : found);
            // A message wider than the dialog widens it, once: it never shrinks back, so it doesn't jump while typing.
            Window window = SwingUtilities.getWindowAncestor(error);
            if (window != null && error.getPreferredSize().width > error.getWidth()) {
                window.pack();
            }
        };
        check.run();
        onTextChange(field, text -> check.run());
        JOptionPane pane = new JOptionPane(
            new Object[] { label, field, error },
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

    /**
     * Asks a two-option question whose safe choice is the primary, default one.
     *
     * @return whether the user chose {@code action}
     */
    static boolean confirm(Component parent, String title, String message, String action, String safe) {
        return action.equals(choose(parent, title, message, action, safe));
    }

    /**
     * Like {@link #confirm}, for two choices that both do something.
     *
     * @return the label of the chosen option, or {@code null} if the dialog was closed
     */
    static String choose(Component parent, String title, String message, String action, String safe) {
        JButton act = button(action);
        JButton keep = primary(button(safe));
        JOptionPane pane = new JOptionPane(
            message,
            JOptionPane.WARNING_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            new Object[] { act, keep },
            keep
        );
        // Custom option buttons don't close the pane on their own.
        act.addActionListener(event -> pane.setValue(act));
        keep.addActionListener(event -> pane.setValue(keep));
        JDialog dialog = pane.createDialog(parent, title);
        dialog.getRootPane().setDefaultButton(keep);
        dialog.setVisible(true);
        dialog.dispose();
        return pane.getValue() == act ? action : pane.getValue() == keep ? safe : null;
    }

    /**
     * Asks where to save a file, adding {@code extension} when the name lacks it. Before replacing a file that exists it
     * asks, with Cancel as the safe choice, which goes back to the chooser to pick another name.
     *
     * @return the file to write, or {@code null} if the user cancelled
     */
    static File chooseSaveFile(Component parent, JFileChooser chooser, String extension) {
        while (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (
                !file
                    .getName()
                    .toLowerCase()
                    .endsWith("." + extension)
            ) {
                file = new File(file.getParentFile(), file.getName() + "." + extension);
            }
            if (
                !file.exists() ||
                confirm(
                    parent,
                    chooser.getDialogTitle(),
                    "\"" + file.getName() + "\" already exists. Replace it?",
                    "Replace",
                    "Cancel"
                )
            ) {
                return file;
            }
            chooser.setSelectedFile(file);
        }
        return null;
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
     * A text field for long values, such as patterns: it wraps at any character and grows a line at a time, so the
     * whole value stays in view. It holds one line of text: line breaks, typed or pasted, are dropped, and Tab moves
     * on. It looks like a text field, focus ring and error outline included.
     */
    static JTextArea wrappingField(String text, int columns) {
        JTextArea field = new JTextArea(text) {
            @Override
            public void updateUI() {
                super.updateUI();
                // Burp's theme switch calls this again: take the text field's look each time.
                Border border = UIManager.getBorder("TextField.border");
                if (border != null) {
                    setBorder(border);
                }
                Insets margin = UIManager.getInsets("TextField.margin");
                if (margin != null) {
                    setMargin(margin);
                }
                Color background = UIManager.getColor("TextField.background");
                if (background != null) {
                    setBackground(background);
                }
            }

            /** The {@link #placeholder}, which FlatLaf paints in text fields only. */
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getText().isEmpty() && getClientProperty("JTextField.placeholderText") instanceof String text) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setFont(getFont());
                    g.setColor(themeColor("TextField.placeholderForeground", mutedColor()));
                    Insets insets = getInsets();
                    g.drawString(text, insets.left, insets.top + g.getFontMetrics().getAscent());
                    g.dispose();
                }
            }

            /** As wide as {@code columns}, not as the text: the layout gives it the width, and the text wraps. */
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                Insets insets = getInsets();
                return new Dimension(getColumns() * getColumnWidth() + insets.left + insets.right, size.height);
            }
        };
        field.setColumns(columns);
        field.setLineWrap(true);
        field.setCaretPosition(0);
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new SingleLine());
        // Inherit Tab and Shift+Tab, which a text area otherwise types.
        field.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        field.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        // Its height is only known once it has a width: lay out again after the first pass.
        field.addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (field.getPreferredSize().height != field.getHeight()) {
                        field.revalidate();
                    }
                }
            }
        );
        return field;
    }

    /** Drops line breaks, typed or pasted, from a document that holds one line. */
    static final class SingleLine extends DocumentFilter {

        @Override
        public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
            throws BadLocationException {
            super.insertString(bypass, offset, text.replaceAll("[\r\n]", ""), attributes);
        }

        @Override
        public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
            throws BadLocationException {
            super.replace(bypass, offset, length, text == null ? null : text.replaceAll("[\r\n]", ""), attributes);
        }
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

    /** {@code value} ("x,y,width,height") if it lies whole on one of {@code screens}, else {@code null}. */
    static Rectangle boundsOnScreen(String value, List<Rectangle> screens) {
        try {
            String[] parts = value.split(",");
            Rectangle bounds = new Rectangle(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
            );
            for (Rectangle screen : screens) {
                if (!bounds.isEmpty() && screen.contains(bounds)) {
                    return bounds;
                }
            }
        } catch (RuntimeException e) {
            // Not written by this version: use the default bounds.
        }
        return null;
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

    /** A named color of a rule, stored as its hex value. */
    record ColorChoice(String name, String hex) {
        static final List<ColorChoice> HIGHLIGHT = List.of(
            new ColorChoice("Yellow", "#ffff00"),
            new ColorChoice("Orange", "#ff9800"),
            new ColorChoice("Red", "#f44336"),
            new ColorChoice("Green", "#4caf50"),
            new ColorChoice("Blue", "#2196f3"),
            new ColorChoice("Purple", "#9c27b0")
        );

        Color color() {
            return RuleEngine.parseColor(hex, Color.GRAY);
        }
    }

    /**
     * A choice among a few named colors, each shown by its name and a swatch, like Burp's highlight menu. A color
     * that is not one of them, from an older template, stays as a "Custom" choice.
     */
    static JComboBox<ColorChoice> colorCombo(List<ColorChoice> choices, String hex, Consumer<String> onChange) {
        List<ColorChoice> items = new ArrayList<>(choices);
        ColorChoice selected = choices
            .stream()
            .filter(choice -> choice.hex().equalsIgnoreCase(hex))
            .findFirst()
            .orElseGet(() -> {
                ColorChoice custom = new ColorChoice("Custom", hex);
                items.add(custom);
                return custom;
            });
        JComboBox<ColorChoice> combo = new JComboBox<>(items.toArray(ColorChoice[]::new));
        combo.setSelectedItem(selected);
        combo.setRenderer(
            new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
                ) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof ColorChoice choice) {
                        setText(choice.name());
                        setIcon(new SwatchIcon(choice.color(), 12));
                        setIconTextGap(6);
                    }
                    return this;
                }
            }
        );
        combo.getAccessibleContext().setAccessibleName("Color");
        combo.addActionListener(event -> {
            if (combo.getSelectedItem() instanceof ColorChoice choice) {
                onChange.accept(choice.hex());
            }
        });
        return combo;
    }

    /**
     * Makes {@code field} a search field the Burp way: a magnifier at its start and, while it has text, a clear button
     * at its end (FlatLaf only). Escape clears it too, from the keyboard; when it is empty, Escape goes on to the
     * window.
     */
    static void searchField(JTextField field) {
        field.putClientProperty("JTextField.leadingIcon", Glyph.SEARCH);
        field.putClientProperty("JTextField.showClearButton", true);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "clearSearch");
        field.getActionMap().put(
            "clearSearch",
            new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    field.setText("");
                }

                @Override
                public boolean accept(Object sender) {
                    return !field.getText().isEmpty();
                }
            }
        );
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
        CHEVRON_UP,
        /** The magnifier of a search field, muted like FlatLaf's own. */
        SEARCH;

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hot = c instanceof AbstractButton button && button.getModel().isRollover() && button.isEnabled();
            g.setColor(this == TRASH && hot ? errorColor() : this == SEARCH ? mutedColor() : c.getForeground());
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
                case SEARCH -> {
                    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new java.awt.geom.Ellipse2D.Float(2, 2, 7.5f, 7.5f));
                    g.draw(new Line2D.Float(8.6f, 8.6f, 12, 12));
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
