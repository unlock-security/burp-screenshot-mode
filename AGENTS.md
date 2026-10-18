# AGENTS.md

This file provides guidance to any AI agent when working with code in this repository.

## What this is

A Burp Suite extension (Montoya API 2026.7, Java 21) that renders request/response pairs as report-ready PNG
screenshots. It is a **port of Caido's [Screenshot Mode](https://github.com/caido-community/screenshot-mode)
plugin**: many classes and comments reference their Caido/TypeScript counterparts (`decorations.ts`, `types.ts`,
`useCrop`, `utils/screenshot.ts`, ...) and behavior is meant to match it. When changing behavior, check whether it
diverges from Caido and say so in a comment if it does intentionally (e.g. `Settings.theme`, which Caido doesn't
have).

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

## Architecture

Package root: `src/main/java/it/unlocksecurity/burp/screenshotmode/`.

**Burp is kept at the edge.** Only the three top-level classes touch the Montoya API:
- `ScreenshotModeExtension` wires everything: adapts Burp preferences into a `PreferenceStore`, builds a `Host`,
  registers the suite tab, context menu and Ctrl+Shift+S hotkey (with a fallback for older Burp versions).
- `BurpMessages` converts `HttpRequestResponse` into the Burp-free `render.MessageData` record. Its `key` (SHA-256 of
  URL + request bytes) identifies "the same message" across invocations.
- `ScreenshotContextMenu` picks the target message (editor content wins over table selection; first selected row).

Everything else (`model`, `storage`, `render`, `ui`) depends only on plain Java/Swing. `ui.Host` is the record of
suppliers/consumers (fonts, dark theme, owner window, logging, preferences) the UI needs from Burp. This is what lets
tests construct the full UI and render images without Burp — keep new Burp dependencies behind `Host` or
`PreferenceStore`.

**Rendering pipeline** (per message half, in `ui.ContentPanel`):
`MessageText.fromBytes` (UTF-8 with ISO-8859-1 fallback, `\n` line breaks, truncation) →
`HeaderFilter.filter` (hide headers; `!`-prefixed rules force-show) →
`RuleEngine.apply` (highlights + redactions → `Decorated` text + spans) →
`HttpSyntax.tokenize` → `MessagePane`/`MessageView` paint it with a `Palette`.
`RuleEngine` applies `replace` redactions to the text itself, so all other spans are remapped from source to
display offsets. Regex matching runs against a deadline-checking `CharSequence` to guard the EDT from catastrophic
backtracking in user patterns. Blur has no Swing equivalent: `MessageView` post-processes the affected rows on an
offscreen image (`ui.Blur`, three box-blur passes). `ImageCapture` renders `ContentPanel` at 2× and exports PNG to
file or clipboard (custom PNG `DataFlavor`, because macOS converts `imageFlavor` to TIFF).

**State model:**
- `model.Settings` / `Template` are mutable POJOs with explicit `copy()`. `TemplateStore` hands out its stored
  instances, which must be treated as read-only — copy `settings` before editing.
- Enums implement `model.Labeled`: `id()` is the stable storage id shared with Caido's JSON format, `label()` is UI
  text. Never change an existing `id()`.
- `ScreenshotLauncher` keeps one dialog open at a time and an LRU (50) of per-message `SessionState` (settings copy,
  template id, split ratio, crop). Edits in the screenshot window affect only that session until the user explicitly
  updates/saves a template.
- `SettingsForm` mutates a `Settings` in place and is reused by both the screenshot sidebar and the template editor
  in `TemplatesTab`.

**Persistence:** `TemplateStore` saves all templates as one JSON string under the Burp preference key
`screenshot-mode.templates`. `TemplateCodec` reads/writes Caido's storage format (current `VERSION = 2`) and
migrates v1 and unversioned v0 payloads; the same codec handles template import/export. Any schema change must stay
compatible with Caido's format and keep the migration chain working (covered by `TemplateCodecTest`).
  