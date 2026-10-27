# AGENTS.md

This file provides guidance to any AI agent when working with code in this repository.

## What this is

A Burp Suite extension (Montoya API 2026.7, Java 21) that renders request/response pairs as report-ready PNG
screenshots. It **started as a port of Caido's [Screenshot Mode](https://github.com/caido-community/screenshot-mode)
plugin** and has since diverged on purpose, in features (Pretty view, screenshot theme, named capture groups,
template duplication, keyboard shortcuts, ...) and in UI (tabbed settings, Burp-native controls and styling). Caido
is a reference, not a spec: design new behavior for Burp users and follow Burp's conventions, without trying to
match Caido.

It is now an independent project: nothing, the template format included, has to stay compatible with Caido's plugin.
Some classes and comments still point to their Caido/TypeScript origin (`decorations.ts`, `types.ts`, `useCrop`,
"port of ...", "unlike Caido ..."). They are history, not a requirement: don't add new "matches Caido" or "diverges
from Caido" remarks, and drop stale ones when you rewrite the code around them.

## Commands

```bash
./gradlew build                      # compile, test, and produce build/libs/screenshot-mode-<version>.jar (fat jar incl. Gson)
./gradlew test                       # headless; also writes sample screenshots to build/preview/
./gradlew test -PpreviewUi           # also renders the screenshot window + suite tab (needs a desktop session)
./gradlew test --tests '*RuleEngineTest'                   # single test class
./gradlew test --tests '*RuleEngineTest.someMethodName'    # single test method

npm install                          # once: installs Prettier and the husky pre-commit hook (lint-staged)
npm run format                       # format all Java sources
npm run format:check                 # verify formatting
```

Java is formatted with Prettier + `prettier-plugin-java` (120 cols, 4-space indent), not with a Java formatter. The
pre-commit hook reformats staged `.java` files; run `npm run format` after editing so diffs match.

## Commits and releases

Commit messages **must** follow Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`,
`ci:`, ...; `!` or a `BREAKING CHANGE:` footer for breaking changes): a `commit-msg` hook (commitlint) rejects
anything else, and the type decides the next release (`fix` → patch, `feat` → minor, breaking → major, others →
none). Pick the type by user-visible impact, not by the size of the change.

Releases are fully automated (`.github/workflows/release.yml`, semantic-release configured in `.releaserc.json`) on
every push to `main`, which only accepts pull requests from `dev`. The version lives only in git tags: never edit a
version number in `build.gradle.kts` or elsewhere — Gradle reads it from `-PreleaseVersion`, defaulting to
`0.0.0-SNAPSHOT`. GitHub Actions in workflows are pinned to commit SHAs with the version in a trailing comment.

## Working process

Work the way the maintainer does:

1. **One change at a time.** Implement one step, verify it (see below), then stop and present it: what changed and
   why, how it was verified, and a proposed commit message. Wait for approval before committing, and again before
   starting the next step.
2. **Ask instead of guessing** when a request is ambiguous, breaks a guideline below or involves a real trade-off:
   explain it and offer the options, the recommended one first. Don't ask again about the settled decisions below.
3. **Commit only to `dev`.** Never commit to `main`, push or open a pull request unless asked. Stage files by name,
   never with `git add -A` or `git add .`: the maintainer may have files of their own in the working tree. Never
   commit scratch files, renders or harness copies.
4. **Keep each commit to one change.** When a fix grows beyond the step (a bug found while testing, say), say so and
   offer to commit it separately, with its own type.
5. **Commit bodies**, when needed, are plain prose saying what the user gets. A body line starting with `word:` makes
   commitlint warn about a footer: rephrase it.
6. **Report plainly**: what was done and verified, what failed (with the output) and what was left undone. Give the
   absolute path of every render.

Before presenting a step:

- `npm run format`, then `./gradlew build` with every test passing. When Gradle prints nothing useful, count tests and
  failures from `build/test-results/test/*.xml` (attributes `tests`, `failures`, `errors`).
- For a UI change, render it in Burp's light and dark themes with the `render-ui` skill
  ([.agents/skills/render-ui](.agents/skills/render-ui/SKILL.md): a harness that loads Burp's theme from its jar, and
  its instructions), and also at a narrow window and a larger Burp font when the layout changed. Look at the images.
- Update this file where behavior or architecture changed. README.md stays short: a feature gets at most a line
  there, and its details belong in the UI and in this file. Its images in `assets/` (screenshots and the demo GIF)
  are rebuilt with the `readme-assets` skill ([.agents/skills/readme-assets](.agents/skills/readme-assets/SKILL.md)).

Headless test gotchas: `ScreenshotDialog` can't be loaded headless (its static shortcut constant needs a Toolkit), so
put testable logic in `Widgets` or in static nested classes. Headless containers have no peer, so `validate()` does
nothing: lay out with `RenderPreviewTest.layoutTree` (`WrapLayoutTest.layOut` runs it twice, as a window would after a
wrapping row asks for another layout; `WrapLayoutTest.inView` checks a component isn't cut off). Before creating a test
file, check that the name isn't taken.

Swing behaves differently under Burp's look and feel than under plain Swing: Burp shows `<html>` label text as plain
text, for instance. Before reporting a rendering or security issue that depends on Swing, reproduce it with Burp's
look and feel (the `render-ui` harness loads it) or in Burp itself, and say how it was checked.

## UI/UX changes

Before making any UI/UX change, even a small one the user asks for, check it against UI/UX guidelines: Nielsen's
usability heuristics, the conventions of Swing/FlatLaf and of Burp's own UI (which controls Burp uses for the same job),
and accessibility (keyboard access, text contrast of at least 4.5:1, not relying on color or tooltips alone). If the
change violates any of them, don't implement it yet: warn the user, explain which guideline it breaks and why, propose
an alternative that doesn't, and ask which one to implement. Only go ahead directly when nothing is violated.
Update [ACCESSIBILITY.md](ACCESSIBILITY.md) when a change adds or changes a keyboard shortcut, or adds or removes a
known limitation.

Conventions of this UI:

- **Keep it simple**: the simplest design that meets the need, with no options, settings or concepts nobody asked for.
  No warnings or tooltips unless they are needed.
- **Confirmations** have exactly two options, through `Widgets.confirm` / `Widgets.choose`: the safe choice is the
  primary (orange) and default button. No "Don't ask me again".
- **Words, not icons or color alone**: rule status reads "redacted", "Error", "Off"; hidden header rows are muted and
  unchecked; tri-state checkboxes say "Partly shown" as their accessible description; icon-only buttons have an
  accessible name.
- **Names**: the rule target is "URL", the Appearance row is "URL bar".
- **Nothing disappears** in a narrow window or with a larger font: rows wrap (`WrapLayout`), and the window and sidebar
  minimum sizes grow with the font (`Widgets.scaled`). Text is cut with an ellipsis only where the full text is also
  shown elsewhere.
- **No buttons inside table rows**: row actions live in a details panel and the right-click menu, as in Burp.
- **Jumps between tabs** move the focus on purpose and can be undone ("‹ Back to Headers", undo).

## Settled decisions

Decided by the maintainer: build on them, and don't propose the alternatives again.

- Header rules stay minimal: a redaction replaces the whole value, a highlight marks the whole line, with no option to
  pick a part. Defaults that must keep part of a value (the Authorization scheme, the Set-Cookie name and flags) are
  named pattern rules, as those are often the finding.
- The Headers tab only shows or hides headers; every redaction and highlight is a rule of the Rules tab.
- A failed redaction doesn't block Copy or Save: they ask first, in a generic message that never lists rules or regexes.
- The path-only URL bar has no warning or tooltip about the host still showing in `Host`, `Origin` or `Referer`; the
  README notes it.

## Architecture

Package root: `src/main/java/it/unlocksecurity/burp/screenshotmode/`.

**Burp is kept at the edge.** Only the three top-level classes touch the Montoya API:
- `ScreenshotModeExtension` wires everything: adapts Burp preferences into a `PreferenceStore`, builds a `Host`,
  registers the suite tab, context menu and Ctrl+Shift+S hotkey (with a fallback for older Burp versions).
- `BurpMessages` converts `HttpRequestResponse` into the Burp-free `render.MessageData` record, copying out of Burp
  only the part of each message that can be shown. Its `key` (SHA-256 of URL + request bytes, read a chunk at a time)
  identifies "the same message" across invocations. It runs off the EDT: `ScreenshotLauncher.open(read, failed)` reads
  the message through `Widgets.inBackground` and opens the window for the latest read only.
- `ScreenshotContextMenu` picks the target message (editor content wins over table selection; a table offers it only
  for one selected row, for the menu and the hotkey alike).

Everything else (`model`, `storage`, `render`, `ui`) depends only on plain Java/Swing. `ui.Host` is the record of
suppliers/consumers (fonts, dark theme, owner window, logging, preferences) the UI needs from Burp. This is what lets
tests construct the full UI and render images without Burp — keep new Burp dependencies behind `Host` or
`PreferenceStore`.

**Rendering pipeline** (per message half, in `ui.ContentPanel`; a half that `Settings.messages` leaves out is not
rendered at all, so its rules report nothing):
`MessageText.fromBytes` (UTF-8 with ISO-8859-1 fallback, `\n` line breaks, truncation) →
`pretty.PrettyPrinter.format` (only in Pretty view; runs first so it still sees `Content-Type`; only adds line breaks
and indentation) →
`HeaderFilter.filter` (hides the headers `HeaderVisibility` hides in that message) →
`RuleEngine.apply` (highlights + redactions → `Decorated` text + spans) →
`HttpSyntax.tokenize` → `MessagePane`/`MessageView` paint it with a `Palette`.
Rules (`model.Rule`, the base of `HighlightRule` and `RedactionRule`) are of two `RuleType`s: a header rule matches
the value (for highlights, the whole line) of named headers in both messages, found by a linear scan of the header
lines; a pattern rule matches its regex or literal in each of its `targets`. `Rule.appliesTo` decides which rules run
on each part, off rules included.
The URL bar is a `MessageView` too: `RuleEngine.apply` with the rules that apply to the `URL`, no header filter and no
syntax colors. `RuleEngine.apply` also reports a `RuleStatus` per rule (matches, invalid regex, stopped early), which
`ContentPanel` collects by rule identity: the screenshot window shows it on each rule's line in `ui.RuleList` and
asks before exporting when a redaction failed.
`render.pretty` holds one formatter per body format behind `PrettyPrinter`, its only public class, writing through
`IndentWriter` (lazy line breaks and indentation, output capped at `MessageText.MAX_CHARS`).
`RuleEngine` applies `replace` redactions to the text itself, so all other spans are remapped from source to display
offsets. Regex matching runs against a deadline-checking `CharSequence` to guard the EDT from catastrophic backtracking
in user patterns. Message bodies come from servers under test and are processed on the EDT, so the pretty printers and
`HttpSyntax` scan them by hand in linear time, with the helpers in `render.Scan`: don't run `java.util.regex` patterns
with a repeated group, like `(?:"[^"]*"|[^>])*`, over a body, since they recurse once per character and overflow the
stack on long input. Blur has no Swing equivalent: `MessageView` post-processes the affected rows on an offscreen image
(`ui.Blur`, three box-blur passes). `ImageCapture` renders `ContentPanel` at `Settings.scale` and exports PNG to file
or clipboard (custom PNG `DataFlavor`, because macOS converts `imageFlavor` to TIFF). Save suggests
`ImageCapture.fileName`, built from the URL bar's `copyText` so what a rule redacts there stays out of the name.
Painting stays on the EDT, but reading the message out of Burp, PNG encoding and every file read or write (Save,
template import/export) run through `Widgets.inBackground`, a `SwingWorker` that reports back on the EDT: the BApp Store rejects file I/O on the EDT.

**State model:**
- `model.Settings` / `Template` are mutable POJOs with explicit `copy()`. `TemplateStore` hands out its own
  instances, which must be treated as read-only — copy `settings` before editing.
- `TemplateStore.templates()` lists the built-in templates (`Template.builtIn`: Defaults, then Empty) before the
  user's own. Built-in templates are never stored, updated or deleted; any template, built-in included, can be the
  default (★), and deleting the default one makes Defaults the default. Names are unique ignoring case and surrounding
  spaces, built-in names included (`TemplateStore.templateNamed`): create and rename refuse a taken name, the editor
  and Save as new… say so and disable saving (`TemplatesTab.nameProblem`), and import renames a clash to
  "name (imported)" (`freeName`, also used for Duplicate's "name (copy)"). Import and export go through
  `ui.TemplatePicker`, a checkbox list of the file's or the user's templates; for import it shows the names
  `TemplateStore.importNames` would give the ticked ones, the same names `importTemplates` then uses. New templates start from a built-in one
  ("Based on" in the editor) or duplicate any template.
- `Settings.basedOn` is that built-in **parent** (only built-in templates are parents; `null` is Empty, which hides
  nothing). In memory `Settings.headers` always holds the effective headers. `TemplateCodec` stores only
  `HeaderVisibility.changesFrom` the parent's headers and reads them back `over` the parent's, so the headers a
  template doesn't change follow its parent when a release changes it. Rules and appearance are copied once and never
  inherited. Copies and duplicates keep the parent. Each settings tab ends with "Based on …" and a Restore defaults
  that always takes the parent's settings (never Defaults by name, so a future built-in template works too): Rules
  adds the missing rules (`Settings.missing`, matched by name, so built-in rules must be named) or replaces them all,
  Headers drops the header changes, Appearance copies the parent's (`Settings.copyAppearance`). All ask first
  (`Widgets.confirm`/`choose`: two options, the safe one primary and default).
- Enums implement `model.Labeled`: `id()` is the stable id written to saved templates and exports, `label()` is UI
  text. Never change an existing `id()`.
- `ScreenshotLauncher` keeps one dialog open at a time and an LRU (50) of per-message `SessionState` (settings copy,
  template id, split ratio). Edits in the screenshot window affect only that session until the user explicitly
  updates/saves a template.
- `SettingsForm` mutates a `Settings` in place, in three tabs (Rules, Headers, Appearance), and is the sidebar of both
  the screenshot window and the template editor in `TemplatesTab`, which share its preview right-click actions
  (`onSelectionAction`). `TemplateCodec.sameSettings` (comparison of the stored form) drives the "Modified"/"Unsaved"
  state of both.
- The template editor previews `ui.TemplatePreview`: the sample message (`storage/sample-message.json`, read by
  `storage.SampleMessage`; something for every rule of Defaults to match, and a request time for the footer), edited in place and kept in memory by
  `TemplatesTab` (`TemplatePreview.Sample`), never stored. With the Rules and Headers tabs it shows one part beside its
  source (`ContentPanel.showOnly`); with Appearance, the whole screenshot. It renders with the same `ContentPanel` as
  the screenshot window and feeds the form the rule statuses and header hits of the one-part panel (it renders every
  part, whichever messages the template shows) and the sample's headers.
- Undo/redo: the screenshot window and the template editor each own a `ui.SettingsHistory` of `Settings` snapshots,
  told of every change through the form's `onChange` (a no-op change is no step; typing in a text field is one step,
  taken when it pauses). Undo restores the snapshot **in place** (`Settings.copyFrom`, which keeps the rule lists that
  `RuleList` holds) and calls `SettingsForm.reload(before)`, which refreshes every tab without rebuilding the form and
  shows the tab and the rule that changed. Changes made outside the form (preview right-click, Revert, dragging the
  height handle, recorded once when the drag ends) call `record()`; switching template calls `reset()`. History lives
  as long as the window or editor, not in `SessionState`.

**Persistence:** `TemplateStore` saves the user's templates (not the built-in ones) as one JSON string under the Burp
preference key `screenshot-mode.templates`; exports leave the built-in ones out too. If that value can't be read at
startup, it is copied to `screenshot-mode.templates.unreadable` before the store starts with the built-in templates
only, and the suite tab offers to export the copy. Window state is not part of templates: the screenshot window keeps
its bounds and sidebar width under `screenshot-mode.window` and `screenshot-mode.sidebarWidth`, saved when it closes
and ignored if the bounds no longer fit on a screen. `TemplateCodec` reads/writes the JSON storage format (current `VERSION = 3`) and
migrates v2, v1 and unversioned v0 payloads; the same codec handles template import/export. The built-in templates are
`storage/built-in-templates.json` in `src/main/resources`, an ordinary template export read by
`storage.BuiltInTemplates` at every start, so each release ships its own: edit the default hidden headers and rules
there (in Defaults), never change the built-in ids, and update the file whenever `VERSION` changes
(`BuiltInTemplatesTest` fails otherwise). Optional settings fields
(`theme`, `view`, `url`, `height`, `text`, `scale`, `messages`, `lineNumbers`, `basedOn`) default sensibly when missing; a `basedOn` this release doesn't ship reads as
none, with the stored headers taken as the template's own. Any schema change must keep loading the templates users already saved
or exported: bump `VERSION` with a migration when needed, and keep the migration chain working (covered by
`TemplateCodecTest`). Templates saved before version 3 have their exact copies of the old default redactions replaced
by the named rules of the current Defaults (`storage.LegacyDefaults`); rules the user edited are kept as they are.

The Headers tab only shows or hides headers: every redaction and highlight is a rule of the Rules tab. A header row
says which rules redact or highlight it: the header rules naming it, and the rules that changed its line in the message
on screen (`ContentPanel.headerHits`). Its actions open that rule, or add a header rule, through `HeadersTab.Links`.

**Header catalog:** `storage/header-catalog.json` (read by `storage.HeaderCatalogFile` into a `model.HeaderCatalog`)
lists the known headers in groups, with the messages each is used in and where it is defined. It is reference data,
read at every start and never stored in templates: templates only store `HeaderVisibility`, i.e. hidden groups by id
and per-header settings by name (changes from a parent also store shown groups), so a release can add headers to a
group and every template follows. Never change or
reuse a group `id`. Header settings that repeat their group's are dropped, so equal choices store the same; headers
the catalog doesn't know are kept even when shown. The v2-to-v3 migration needs the catalog: a group whose headers
were all hidden becomes a hidden group.
  