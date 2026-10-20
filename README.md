# Screenshot Mode for Burp Suite

<p>
  <a href="https://github.com/unlock-security/burp-screenshot-mode/actions/workflows/release.yml"><img src="https://img.shields.io/github/actions/workflow/status/unlock-security/burp-screenshot-mode/release.yml?branch=main&style=for-the-badge&label=build" alt="Build status"></a>
  <a href="https://github.com/unlock-security/burp-screenshot-mode/releases/latest"><img src="https://img.shields.io/github/v/release/unlock-security/burp-screenshot-mode?style=for-the-badge" alt="Latest release"></a>
  <a href="https://openjdk.org/projects/jdk/21/"><img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://central.sonatype.com/artifact/net.portswigger.burp.extensions/montoya-api/2026.7"><img src="https://img.shields.io/badge/Montoya_API-2026.7-FF6633?style=for-the-badge" alt="Montoya API 2026.7"></a>
  <a href="https://portswigger.net/burp"><img src="https://img.shields.io/badge/Burp_Suite-Professional_%7C_Community-FF6633?style=for-the-badge&logo=burpsuite&logoColor=white" alt="Burp Suite Professional | Community"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/unlock-security/burp-screenshot-mode?style=for-the-badge" alt="License"></a>
  <a href="https://www.unlock-security.it/?utm_source=github&utm_medium=repo&utm_campaign=screenshot-mode"><img src="https://img.shields.io/badge/Made_by-Unlock_Security-blue?style=for-the-badge" alt="Made by Unlock Security"></a>
</p>

Take pretty screenshots of requests and responses directly inside Burp Suite, ready to drop into a report.
No external tools, no manual cropping and blurring.

> Started as a port of Caido's [Screenshot Mode](https://github.com/caido-community/screenshot-mode) plugin, it has
> since grown into an independent project, with its own features and a UI designed for Burp.

![From a Repeater request to a report-ready screenshot: open Screenshot Mode, highlight a leaked token, hide and show headers, switch to A4, pretty and stacked, update the template and copy](assets/demo.gif)

## Features

- **Live preview** of the request and response in a dedicated window, laid out side by side or stacked, with line
  numbers, wrapping and HTTP/JSON/XML/HTML syntax colors.
- **Hide noisy headers** (`Accept-*`, `Sec-*`, CDN headers, …) for both messages or only the request/response.
  Prefix a header with `!` to always show it.
- **Highlights**: regex or literal patterns, painted as a translucent marker or as a rectangle, in any color.
- **Redactions**: blur, opaque box or replacement text (`[REDACTED]`), optionally limited to selected capture
  groups (e.g. `session=([0-9a-f]+)` redacts only the value).
- Each highlight or redaction applies to the **request**, the **response** or the **URL**. The default rules also
  redact tokens and JWTs in the URL, so a secret hidden in the request line doesn't show up above it.
- **Pretty view** for JSON, HTML, XML (SVG, RSS, SOAP, …), CSS and JavaScript bodies, like Burp's Pretty tab.
  Only line breaks and indentation change, so your rules still match and broken or malicious markup shows exactly as
  sent.
- **Right-click selected text** in the preview to highlight it, redact it or hide its header in one click.
- **Content width** presets for reports: full width, custom pixels, A4 (595px) or Letter (612px).
- **Crop** the captured height with the drag handle, or **Fit to content** to show the whole messages.
- **Metadata footer**: request timestamp and response size/round-trip time.
- **Templates**: save settings as named templates, pick a default, edit or duplicate them from the *Screenshot Mode*
  suite tab, and import/export them as JSON.
- **Export** as a 2× PNG file or straight to the clipboard.

## Screenshots

The screenshot window, in Burp's light and dark themes:

![Screenshot Mode window, light theme](assets/window-light.png)
![Screenshot Mode window, dark theme](assets/window-dark.png)

Templates are managed in the *Screenshot Mode* suite tab:

![Templates tab](assets/templates.png)

What ends up in your report:

<p>
  <img src="assets/screenshot-light.png" alt="Exported screenshot, light" width="49%">
  <img src="assets/screenshot-dark.png" alt="Exported screenshot, dark" width="49%">
</p>

## Usage

1. Right-click a request anywhere in Burp (Repeater, Proxy, HTTP history, Target site map, Logger, …) and choose
   **Extensions › Screenshot Mode › Screenshot**, or press **Ctrl+Shift+S**.
2. Pick a template and tweak the rules (highlights and redactions), the hidden headers and the appearance, each in
   its own tab. The preview updates as you type.
3. Click **Copy to clipboard** (**⌘C** / **Ctrl+C**) or **Save PNG…** (**⌘S** / **Ctrl+S**). Copy only copies the
   screenshot when no text is selected; with a selection it copies the text as usual.

From a message editor, the screenshot shows what the editor currently holds, including unsent edits in Repeater.\
When several rows are selected in a table, the first one is used.

Settings changed in the screenshot window apply to that message only, and are remembered if you screenshot the
same message again; a **Modified** label shows when they differ from the template. Use **Update template** or
**Save as new…** to keep them, or **Revert** to go back to the template. The **Screenshot Mode** suite tab
manages the templates.

## Installation

Requires Burp Suite Professional or Community 2025.x or newer (Java 21).

1. Build the jar (see below), or grab it from the [releases](https://github.com/unlock-security/burp-screenshot-mode/releases).
2. In Burp, go to **Extensions › Installed › Add**, choose **Java** as the extension type and select
   `build/libs/screenshot-mode-<version>.jar`.

## Building

```bash
./gradlew build
```

This compiles against the Montoya API, runs the tests and produces a self-contained jar in `build/libs/`.

## Contributing

Bug reports, feature requests and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the
development setup, code formatting, commit conventions and how releases work.

## License

MIT, see [LICENSE](LICENSE). Originally based on the Caido plugin by Caido Labs Inc.
