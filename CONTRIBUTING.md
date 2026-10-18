# Contributing to Screenshot Mode

Thanks for helping improve the extension! To report a bug or ask for a feature, open an
[issue](https://github.com/unlock-security/burp-screenshot-mode/issues/new/choose) using one of the templates.
Please remove client data from any screenshot or request you attach.

This extension is a port of Caido's [Screenshot Mode](https://github.com/caido-community/screenshot-mode) plugin, and
its behavior is meant to match it. If a change makes it diverge from Caido on purpose, say so in a code comment.

## Building and testing

```bash
./gradlew build
```

This compiles against the Montoya API, runs the tests and produces a self-contained jar in `build/libs/`.
Local builds are versioned `0.0.0-SNAPSHOT`; release builds get their version from the release tag
(`./gradlew build -PreleaseVersion=1.2.3`).

The tests also render sample screenshots without Burp into `build/preview/`. On a desktop session,
`./gradlew test -PpreviewUi` additionally renders the screenshot window and the suite tab.

## Code formatting

Java sources are formatted with [Prettier](https://prettier.io) and
[prettier-plugin-java](https://github.com/jhipster/prettier-java) (configuration in `.prettierrc.json`).
Run `npm install` once after cloning: it installs Prettier and enables a git pre-commit hook (husky + lint-staged)
that formats the staged Java files on every commit.

- **VS Code** formats Java files on save with the recommended Prettier extension (`esbenp.prettier-vscode`), as
  configured in `.vscode/settings.json`.
- `npm run format` formats the whole codebase, `npm run format:check` verifies it (e.g. in CI).

## Branches, commits and releases

Work happens on `dev`; `main` only receives pull requests from `dev`, merged with a **merge commit** (not squash, so
`dev` and `main` don't diverge). Every push to `main` runs the tests and, when warranted, publishes a release: no
version number is ever edited by hand.

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org), which decide the next version:

| Commit | Example | Release |
|--------|---------|---------|
| `fix:` | `fix(ui): keep the crop height when switching template` | patch (`1.4.2` → `1.4.3`) |
| `feat:` | `feat: export screenshots as PDF` | minor (`1.4.2` → `1.5.0`) |
| `!` after the type, or a `BREAKING CHANGE:` footer | `feat!: new template format` | major (`1.4.2` → `2.0.0`) |
| `docs:`, `chore:`, `test:`, `refactor:`, `ci:`, … | `docs: explain capture groups` | none |

`npm install` also adds a `commit-msg` hook that rejects non-conforming messages, and CI checks the commits of every
pull request.

On each push to `main`, [`.github/workflows/release.yml`](.github/workflows/release.yml) runs the CI checks, then
[semantic-release](https://semantic-release.gitbook.io) computes the version from the commits since the last tag,
builds the jar with that version, tags the commit `vX.Y.Z` and publishes a GitHub release with generated notes and
the jar attached. Pull requests and pushes to `dev` run [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
(build and tests, formatting, commit messages, and for pull requests into `main`, that they come from `dev`).
The first release is `v1.0.0`, provided the commits on `main` include at least one `feat:` or `fix:`.

After every push to `main`, the release workflow also brings `dev` up to date with it, without rewriting history:
`dev` is fast-forwarded to `main` (so both point to the same commit, release tags included), or, if new commits
landed on `dev` in the meantime, `main` is merged into it with a `chore: merge main into dev` commit. Pull `dev`
after a release to get it locally. This needs `dev` to accept pushes from GitHub Actions, so don't put a
pull-request ruleset on it.

## Project layout

```
src/main/java/it/unlocksecurity/burp/screenshotmode/
├── ScreenshotModeExtension.java   Montoya entry point: suite tab, context menu, hotkey
├── BurpMessages.java              HttpRequestResponse → MessageData adapter
├── ScreenshotContextMenu.java
├── model/                         Settings, templates and rules (port of types.ts / schemas)
├── storage/                       JSON codec with migrations, template store on Burp preferences
├── render/                        Header filter, rule engine, syntax tokenizer, palettes
└── ui/                            Screenshot window, preview/capture, settings form, templates tab
```
