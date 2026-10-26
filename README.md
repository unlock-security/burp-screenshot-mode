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

![From a Repeater request to a report-ready screenshot: open Screenshot Mode from the context menu, highlight the leaked reset token, redact its tail with a named capture group, find the Server header and highlight it, try request only, response only, the full URL, raw and dark, and copy](assets/demo.gif)

## Features

- **Live preview** of the request and response, side by side or stacked, with syntax colors and a Pretty view for
  JSON, HTML, XML, CSS and JavaScript bodies.
- **Redact** secrets with replacement text, a black box or blur, and **highlight** what matters with a marker or a
  rectangle. Rules match headers or a regex, optionally only some of its capture groups.
- **Safe by default**: the built-in *Defaults* template redacts credentials, cookies, API keys, passwords, tokens and
  JWTs, even in the URL. Each rule shows how many times it matched, and Copy and Save ask first if a redaction fails.
- **Hide noisy headers** from a catalog of about 150 common ones, one by one or a whole group at a time.
- **Right-click** in the preview to redact, highlight or hide what's under the pointer.
- **Report-ready size**: A4, Letter, fit to content or a custom width and height, at 1×, 2× or 3×, in a light or dark
  theme, with Burp's text size or your own.
- **Show only what matters**: the request, the response or both; the full URL, the path only or no URL bar; line
  numbers, timestamp and response time.
- **Templates** to reuse your settings: pick a default, share them as JSON, start from the built-in *Defaults* or
  *Empty*.
- **Undo and redo** every change.
- **Copy** to the clipboard, or **save** a PNG with a name that says what it shows, such as
  `POST-api-v1-users-42-200.png`.

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

1. Right-click a request anywhere in Burp and choose **Extensions › Screenshot Mode › Screenshot**, or select it and
   press **Ctrl+Shift+S**.
2. Pick a template and adjust the rules, headers and appearance: the preview updates as you type.
3. **Copy image** (**⌘C** / **Ctrl+C**) or **Save PNG…** (**⌘S** / **Ctrl+S**).

Changes made in the screenshot window apply to that message only: **Update template** or **Save as new…** keeps
them. The *Screenshot Mode* suite tab manages the templates.

> [!NOTE]
> The path-only URL bar hides the host from the URL, not from headers such as `Host` or `Referer`.

## Installation

Requires Burp Suite Professional or Community 2025.x or newer.

1. Download the jar from the [latest release](https://github.com/unlock-security/burp-screenshot-mode/releases/latest),
   or build it.
2. In Burp, go to **Extensions › Installed › Add**, choose **Java** as the extension type and select the jar.

## Building

```bash
./gradlew build
```

The jar ends up in `build/libs/`.

## Contributing

Bug reports, feature requests and pull requests are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md) and the
[code of conduct](CODE_OF_CONDUCT.md). Report vulnerabilities privately, as described in [SECURITY.md](SECURITY.md).
For accessibility, see [ACCESSIBILITY.md](ACCESSIBILITY.md).

## License

MIT, see [LICENSE](LICENSE). Originally based on the Caido plugin by Caido Labs Inc.
