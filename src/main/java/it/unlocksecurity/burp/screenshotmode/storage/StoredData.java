package it.unlocksecurity.burp.screenshotmode.storage;

import it.unlocksecurity.burp.screenshotmode.model.Template;
import java.util.List;

/** Persisted state, equivalent to the Caido plugin's V2 storage schema. */
public record StoredData(List<Template> templates, String defaultTemplateId) {}
