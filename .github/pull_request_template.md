## What and why

<!-- What this changes and the problem it solves. Link the issue it closes, e.g. "Closes #12". -->

## How it was tested

<!-- Tests added or changed, and what you checked by hand in Burp (Burp version, operating system). -->

## Checklist

- [ ] The pull request targets `dev` (only `dev` is merged into `main`).
- [ ] `./gradlew build` passes and `npm run format:check` reports no changes.
- [ ] Commit messages follow [Conventional Commits](https://www.conventionalcommits.org), typed by what users notice.
- [ ] Templates saved or exported with earlier versions still load (storage changes come with a migration and a test).
- [ ] README, CONTRIBUTING or AGENTS.md describe the new behavior, where they mention it.
- [ ] No client data in screenshots, requests, responses or test files.

### UI changes

<!-- Delete this section if the pull request doesn't change the UI. -->

- [ ] Screenshots before and after, in Burp's light and dark themes.
- [ ] Every new control can be reached and used with the keyboard alone, in a logical order, with a visible focus.
- [ ] Icon-only controls have an accessible name, and no information is only in a tooltip.
- [ ] Color is not the only way a state or an error is shown, and text keeps its contrast in both themes.
- [ ] The layout still works with a larger font size in Burp's settings.
- [ ] It follows [ACCESSIBILITY.md](../ACCESSIBILITY.md) and uses the controls Burp uses for the same job.
