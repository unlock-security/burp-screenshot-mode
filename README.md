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
- **Hide noisy headers** from a catalog of about 150 common headers in groups (content negotiation, caching, client
  hints, fetch metadata, CDNs, …): uncheck a header, or a whole group, to hide it. The screenshot window lists the
  headers of the message on screen, and the tab title counts how many it hides. Headers that aren't in the catalog are
  listed first, as custom headers; one used in both messages can be hidden in only one of them. By default, security
  headers and anything that can take part in request smuggling stay visible, as they can be the finding. Each header
  also says in words whether a rule redacts or highlights it, and links to that rule, or adds one with **Redact
  value…** or **Highlight…**.
- **Highlights**: painted as a translucent marker or as a rectangle, in any color.
- **Redactions**: blur, opaque box or replacement text (`[REDACTED]`).
- **Two kinds of rules**, chosen when you add one with **+**: a **header** rule names one or more headers and
  redacts their whole value (a highlight marks the whole line), in requests and responses; a **text pattern** rule
  matches a regex or literal text in the request, the response, the URL or any of them, and a redaction can be
  limited to selected capture groups (e.g. `session=([0-9a-f]+)` redacts only the value).
- **One list of rules**: each rule has an optional name, an on/off checkbox and a one-line summary, and opens into
  its editor when clicked. The built-in *Defaults* template comes with named redactions for credentials, cookies, API keys,
  passwords, tokens and JWTs, including in the URL, so a secret in the request line doesn't show up above it.
- **Rule checks**: in the screenshot window, each rule shows how many times it matched. A redaction with an invalid
  or too slow pattern is flagged on its line, on the *Rules* tab and above the preview, and **Copy** and **Save** ask
  before exporting a screenshot it may not fully cover.
- **Pretty view** for JSON, HTML, XML (SVG, RSS, SOAP, …), CSS and JavaScript bodies, like Burp's Pretty tab.
  Only line breaks and indentation change, so your rules still match and broken or malicious markup shows exactly as
  sent.
- **Right-click** in the preview to highlight or redact the selected text, or to hide, redact or highlight the header
  under the pointer, in one click.
- **Content width** presets for reports: full width, custom pixels, A4 (595px) or Letter (612px).
- **Crop** the captured height with the drag handle, or **Fit to content** to show the whole messages.
- **Metadata footer**: request timestamp and response size/round-trip time.
- **URL bar**: the full URL, only the path and query (the label reads *Path*), or no URL bar at all. Path only leaves
  out the scheme, host and port; the host can still appear in headers such as `Host`, `Origin` and `Referer`, so hide or
  redact those too if it must not show.
- **Templates**: save settings as named templates, pick a default, edit or duplicate them from the *Screenshot Mode*
  suite tab, and import/export them as JSON. Two built-in templates ship with the extension and are updated with it:
  *Defaults* (noisy headers hidden, secrets redacted) and *Empty* (messages as they are). They can't be edited or
  deleted: start a new template from one of them, or duplicate it. Your template keeps following its built-in
  template's hidden headers, except the ones you changed, so new releases can hide more noise for you; its rules and
  appearance are yours and never change by themselves.
- **Restore defaults** in each tab, from the built-in template yours is based on, after asking: the *Rules* tab adds
  back the rules you deleted (or replaces all your rules), the *Headers* tab drops your header changes, and the
  *Appearance* tab resets width, layout, theme, URL bar, body and footer. Each tab says what it would change.
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
3. Click **Copy image** (**⌘C** / **Ctrl+C**) or **Save PNG…** (**⌘S** / **Ctrl+S**). Copy only copies the
   screenshot when no text is selected; with a selection it copies the text, with blurred and covered values replaced
   by `[REDACTED]`.

From a message editor, the screenshot shows what the editor currently holds, including unsent edits in Repeater.\
When several rows are selected in a table, the first one is used.

Settings changed in the screenshot window apply to that message only, and are remembered if you screenshot the
same message again; a **Modified** label shows when they differ from the template. Use **Update template** or
**Save as new…** to keep them, or **Revert** to go back to the template. Switching template while they differ asks
first, so they aren't lost by accident. The **Screenshot Mode** suite tab manages the templates.

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
