package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplatePickerTest {

    private final Template report = Template.create("Report", BuiltInTemplates.defaults());
    private final Template copy = Template.create("report", BuiltInTemplates.defaults());
    private final Template other = Template.create("Other", BuiltInTemplates.defaults());

    /** Notes like an import's into a store that has no templates: the second of two "report"s is renamed. */
    private final TemplatePicker picker = new TemplatePicker(
        "Choose.",
        List.of(report, copy, other),
        List.of(report, copy, other),
        chosen -> {
            List<String> names = chosen
                .stream()
                .map(template -> template.name.toLowerCase())
                .toList();
            return java.util.stream.IntStream.range(0, chosen.size())
                .mapToObj(i -> names.subList(0, i).contains(names.get(i)) ? "renamed" : null)
                .toList();
        }
    );

    @Test
    void startsWithEveryTemplateTicked() {
        assertEquals(List.of(report, copy, other), picker.chosen());
        assertTrue(picker.all().isSelected());
        assertEquals(List.of("", "renamed", ""), picker.notes());
    }

    @Test
    void notesFollowWhatIsTicked() {
        picker.boxes().get(0).doClick();
        assertEquals(List.of(copy, other), picker.chosen());
        assertEquals(List.of("", "", ""), picker.notes(), "the second keeps its name without the first");
        assertFalse(picker.all().isSelected());
        assertEquals(null, picker.boxes().get(1).getAccessibleContext().getAccessibleDescription());
    }

    @Test
    void allTicksOrUnticksEveryTemplate() {
        int[] changes = { 0 };
        picker.setOnChange(() -> changes[0]++);
        picker.all().doClick();
        assertEquals(List.of(), picker.chosen());
        picker.all().doClick();
        assertEquals(3, picker.chosen().size());
        assertEquals(2, changes[0], "the dialog hears of each change, to enable its button");
        assertEquals("renamed", picker.boxes().get(1).getAccessibleContext().getAccessibleDescription());
    }
}
