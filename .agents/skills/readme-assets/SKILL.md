---
name: readme-assets
description: Rebuild the README images of Screenshot Mode (assets/window-*.png, screenshot-*.png, templates.png and the demo.gif walkthrough) from the real UI in Burp's theme. Use when the UI changed enough that the README images are out of date, or to change the demo GIF's story.
---

# Rebuild the README images

Everything is drawn from the real Swing UI in Burp's own theme (loaded from its jar, like the `render-ui` skill),
with a made-up helpdesk request and response: no real Burp, no screen recording and no client data.

- [Screenshots.java](Screenshots.java): the screenshot window (with a "Client report" template and its open highlight
  rule), the PNG it exports, in light and dark, and the templates tab (light only), each in a drawn macOS frame.
- [Demo.java](Demo.java): `demo.gif`, 1240 × 800, about 45 s. A mock Burp Repeater, then the real screenshot window
  driven step by step with a drawn pointer and click rings.
- [GifOut.java](GifOut.java): the GIF encoder. No GIF tool is needed, and Java's own GIF writer can't be used: it
  ignores per-frame palettes.

## Steps

1. Run from the repository root (needs a desktop session; windows open on screen, so leave the mouse alone):
   ```bash
   .agents/skills/readme-assets/make.sh <scratch>/out              # screenshots and GIF
   .agents/skills/readme-assets/make.sh <scratch>/out gif          # or: screenshots
   ```
   Burp's jar is `$BURP_JAR`, by default the macOS install. Each run ends by itself: a step that fails prints its
   stack trace and exits, and a watchdog stops a run that hangs.
2. Look at every PNG, and at frames of the GIF from each step:
   ```bash
   java .agents/skills/readme-assets/GifFrames.java <scratch>/out/demo.gif <scratch>/frames 40 100 200 300 400 500
   ```
   The GIF run also prints how many pixels were off in its worst frame: more than a handful means the palettes are
   wrong.
3. Copy the images into `assets/`, and update the GIF's alt text in README.md if its story changed.

## The GIF's story

1. Repeater: right-click › Extensions › Screenshot Mode › Screenshot. The window opens on Defaults.
2. Select `reset_token` in the response preview, right-click › Highlight, then Rectangle and Red.
3. Redactions › + › Text pattern: "Token tail", `"reset_token":"(?<head>[0-9a-f]{6})(?<tail>[0-9a-f]+)"`, Response
   only, only the `tail` group: the value reads `f3a9c1[REDACTED]`.
4. Headers: search "serv", show Server, then Highlight… adds a header rule.
5. Appearance: Request, Response, Both; Full URL, Path only; Raw, Pretty; Dark, Light.
6. Copy image, then the exported screenshot over the dimmed window.

`script()` in Demo.java follows these steps; `hold(ms)` sets the pauses.

## Gotchas

- A highlight that overlaps a redaction is dropped by `RuleEngine`: that's why step 2 highlights the key, not the
  value whose tail step 3 redacts.
- The sample message must fit one A4 page once Server is shown, or the preview warns "Taller than one A4 page".
- Controls are driven through their own methods (`doClick`, `setSelectedItem`, document inserts) on the EDT, never
  with real input events, so focus doesn't matter. A modal dialog (Save as new…) must be opened with `invokeLater`.
- The window edits a copy of the template's settings: find its rules through the window's form, not the store.
- Copy image would overwrite the clipboard of whoever runs it: the GIF only shows its status text.
- Each GIF frame stores only what changed, with unchanged pixels transparent; a pixel whose shown color is off is
  drawn again, and the most frequent colors get exact palette entries. Without both, colors drift and old content
  ghosts through.
