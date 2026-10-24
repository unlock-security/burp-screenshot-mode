package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Template;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

/**
 * The templates to import from a file or to export to one, as checkboxes, all ticked at first. A note under a ticked
 * template can say what happens to it, such as the name an import gives it.
 */
final class TemplatePicker extends JPanel {

    /** The list's height, at most, before it scrolls. */
    private static final int VISIBLE_HEIGHT = 300;

    private final List<Template> templates;
    private final Function<List<Template>, List<String>> notes;
    private final List<JCheckBox> boxes = new ArrayList<>();
    private final List<JLabel> noteLabels = new ArrayList<>();
    private final JCheckBox all;
    private Runnable onChange = () -> {};

    /**
     * @param notes for the ticked templates, in order, a note for each, or {@code null} for none
     */
    TemplatePicker(String intro, List<Template> templates, Function<List<Template>, List<String>> notes) {
        super(new BorderLayout(0, 8));
        this.templates = List.copyOf(templates);
        this.notes = notes;
        setOpaque(false);

        Widgets.Stack list = new Widgets.Stack(2, false);
        for (Template template : templates) {
            JCheckBox box = new JCheckBox(template.name, true);
            box.addActionListener(event -> changed());
            JLabel note = Widgets.hint("");
            // Under the name, past the checkbox.
            note.setBorder(BorderFactory.createEmptyBorder(0, textIndent(box), 2, 0));
            note.setVisible(false);
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.add(box, BorderLayout.NORTH);
            row.add(note, BorderLayout.CENTER);
            list.addItem(row);
            boxes.add(box);
            noteLabels.add(note);
        }
        JScrollPane scroll = new JScrollPane(list) {
            @Override
            public Dimension getPreferredSize() {
                // As tall as the list, notes included, up to a few rows; then it scrolls.
                Dimension size = list.getPreferredSize();
                Insets insets = getInsets();
                return new Dimension(
                    Math.max(
                        320,
                        size.width + getVerticalScrollBar().getPreferredSize().width + insets.left + insets.right
                    ),
                    Math.min(VISIBLE_HEIGHT, size.height + insets.top + insets.bottom)
                );
            }
        };
        scroll.setBorder(BorderFactory.createLineBorder(Widgets.borderColor()));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        all = new JCheckBox("All (" + templates.size() + ")", true);
        all.addActionListener(event -> {
            boxes.forEach(box -> box.setSelected(all.isSelected()));
            changed();
        });
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(new JLabel(intro), BorderLayout.NORTH);
        if (templates.size() > 1) {
            top.add(all, BorderLayout.CENTER);
        }
        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        updateNotes();
    }

    /** Where a checkbox's text starts. */
    private static int textIndent(JCheckBox box) {
        Icon icon = box.getIcon() != null ? box.getIcon() : UIManager.getIcon("CheckBox.icon");
        return box.getInsets().left + (icon == null ? 0 : icon.getIconWidth() + box.getIconTextGap());
    }

    /** The ticked templates, in the order given. */
    List<Template> chosen() {
        List<Template> chosen = new ArrayList<>();
        for (int i = 0; i < templates.size(); i++) {
            if (boxes.get(i).isSelected()) {
                chosen.add(templates.get(i));
            }
        }
        return chosen;
    }

    List<JCheckBox> boxes() {
        return boxes;
    }

    JCheckBox all() {
        return all;
    }

    /** The note under each template, empty when it has none. */
    List<String> notes() {
        return noteLabels
            .stream()
            .map(label -> label.isVisible() ? label.getText() : "")
            .toList();
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    private void changed() {
        all.setSelected(boxes.stream().allMatch(JCheckBox::isSelected));
        updateNotes();
        onChange.run();
    }

    private void updateNotes() {
        List<String> chosenNotes = notes.apply(chosen());
        int next = 0;
        for (int i = 0; i < templates.size(); i++) {
            String note = boxes.get(i).isSelected() ? chosenNotes.get(next++) : null;
            noteLabels.get(i).setText(note == null ? "" : note);
            noteLabels.get(i).setVisible(note != null);
            boxes.get(i).getAccessibleContext().setAccessibleDescription(note);
        }
        revalidate();
    }

    /**
     * Shows the picker in a dialog with {@code action}, the primary button, and Cancel. The action is disabled while
     * nothing is ticked.
     *
     * @return the ticked templates, or empty if the dialog was cancelled or closed
     */
    static Optional<List<Template>> show(Component parent, String title, String action, TemplatePicker picker) {
        JButton act = Widgets.primary(Widgets.button(action));
        JButton cancel = Widgets.button("Cancel");
        picker.setOnChange(() -> act.setEnabled(!picker.chosen().isEmpty()));
        JOptionPane pane = new JOptionPane(
            picker,
            JOptionPane.PLAIN_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            new Object[] { act, cancel },
            act
        );
        // Custom option buttons don't close the pane on their own.
        act.addActionListener(event -> pane.setValue(act));
        cancel.addActionListener(event -> pane.setValue(cancel));
        JDialog dialog = pane.createDialog(parent, title);
        dialog.getRootPane().setDefaultButton(act);
        dialog.setVisible(true);
        dialog.dispose();
        return pane.getValue() == act ? Optional.of(picker.chosen()) : Optional.empty();
    }
}
