# Accessibility

We want everyone who uses Burp Suite to be able to use Screenshot Mode, whatever their abilities and however they
use their computer. This page says what we aim for, what works today, what doesn't yet, and how to tell us about a
barrier.

## Our goal

Screenshot Mode aims at [WCAG 2.2](https://www.w3.org/TR/WCAG22/) level AA, as it applies to desktop software
([WCAG2ICT](https://www.w3.org/TR/wcag2ict-22/)). This is a goal, not a claim of conformance: the extension hasn't
been audited. It also follows Burp's own look and behavior, so it works like the rest of Burp, with the same
assistive technology.

Our priorities are:

- **Keyboard**: everything can be done without a mouse, with a visible focus and a logical tab order.
- **Readable text**: the extension follows Burp's font size and its light or dark theme.
- **Not color alone**: states and errors are written in words, not only shown by a color or an icon.
- **Screen readers**: controls have names, including the buttons that only show an icon.

## What works today

- Keyboard shortcuts for the main actions: **Ctrl+Shift+S** opens the screenshot window from a selected request,
  **⌘C** / **Ctrl+C** copies, **⌘S** / **Ctrl+S** saves (or saves the template, in the template editor), **Esc**
  closes the window, **⌘Z** / **Ctrl+Z** undoes and **⇧⌘Z** / **Ctrl+Y** redoes. In the *Rules* tab, **Enter**
  or **Space** opens and closes a rule's editor; in the *Headers* tab, **Space** shows or hides a header, and
  **Shift+F10** or the menu key opens its actions.
- Every size can be typed: width, height and text size have a field, so the drag handles beside the preview are
  never the only way.
- The window and its controls scale with the font size set in Burp's settings.
- Rule checks say in words how many times a rule matched and why it failed; the *Headers* tab says in words which
  rules redact or highlight each header.
- Highlights can be drawn as a rectangle as well as a translucent marker, so they don't depend on color only.
- Screenshots are images, which screen readers can't read. Select text in the preview and copy it to put the same
  request and response in your report as text, with the redacted values still hidden.

## Known limitations

- The extension hasn't been tested with screen readers yet (VoiceOver, NVDA, JAWS, Orca). On Windows, Java
  applications such as Burp need the [Java Access Bridge](https://docs.oracle.com/en/java/javase/21/access/enabling-and-testing-java-access-bridge.html)
  to talk to a screen reader.
- The right-click actions in the preview (highlight or redact the selected text) need a mouse. The same rules can
  be added from the *Rules* tab with **+**, and the header actions from the *Headers* tab.
- The contrast of highlights depends on the color you pick; the extension doesn't check it.
- An exported PNG has no alternative text: add it in your report.

If one of these blocks you, please tell us: it helps us decide what to fix first.

## Reporting a barrier

Open an [accessibility issue](https://github.com/unlock-security/burp-screenshot-mode/issues/new?template=accessibility.yml).
Tell us what you were trying to do, what got in the way, and the assistive technology you use, if any. You don't
need to know the cause or the fix.

We rate each report by how much it gets in the way:

- **Critical**: you can't complete a task at all.
- **Serious**: a task is very hard, though there is a workaround.
- **Moderate**: a task is slower or inconsistent.
- **Minor**: a small annoyance.

We acknowledge reports within **5 business days**, suggest a workaround when there is one, work on critical and
serious barriers first, and ask you to confirm the fix when it ships.

## Contributing

Pull requests that change the UI go through the accessibility checklist in the
[pull request template](.github/pull_request_template.md): keyboard access, visible focus, names for screen readers,
no meaning conveyed by color alone, and a larger font size in Burp. See [CONTRIBUTING.md](CONTRIBUTING.md) for the
rest.

Suggestions to make this page or the extension more accessible are welcome, as issues or pull requests.
