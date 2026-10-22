package it.unlocksecurity.burp.screenshotmode.storage;

import it.unlocksecurity.burp.screenshotmode.model.Template;
import java.util.List;

/** Persisted state: the templates and the id of the default one (storage version 2). */
public record StoredData(List<Template> templates, String defaultTemplateId) {}
