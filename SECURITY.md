# Security policy

Screenshot Mode exists to keep secrets out of report screenshots, so we take anything that defeats it seriously.

## Supported versions

Only the [latest release](https://github.com/unlock-security/burp-screenshot-mode/releases/latest) gets security
fixes: a fix ships as a new release, never as a patch to an older one. Update to the latest release before reporting.

## Reporting a vulnerability

**Please don't open a public issue.** Report it privately instead:

- with GitHub's [private vulnerability reporting](https://github.com/unlock-security/burp-screenshot-mode/security/advisories/new)
  (preferred), or
- by email to [security@unlock-security.it](mailto:security@unlock-security.it), if you can't use GitHub.

Please include:

- the Screenshot Mode, Burp Suite and Java versions, and your operating system;
- what you did and what happened, with the smallest request, response or template that shows it;
- what an attacker gains, or what leaks, and under which conditions.

Remove client data from anything you send: a made-up request that shows the problem is just as good.

## What counts

For example:

- a secret that stays readable in an exported screenshot, in the text copied from the preview or in the suggested
  file name, although a rule that matches it reports no error (a built-in *Defaults* rule included);
- a request or response, which comes from a server under test, that makes Burp hang, crash or run code when it is
  screenshotted;
- a template file that, when imported, does anything other than add templates.

Not in scope: a secret that no rule matches (the extension only redacts what its rules match, so check the preview
before exporting), and vulnerabilities in Burp Suite itself, which go to
[PortSwigger](https://portswigger.net/burp/documentation/desktop/getting-started/report-an-issue).

## What happens next

- We acknowledge your report within **5 business days**.
- We confirm the issue, or explain why we don't consider it one, and keep you posted while we work on a fix.
- The fix ships in a new release, followed by a [security advisory](https://github.com/unlock-security/burp-screenshot-mode/security/advisories)
  that credits you, unless you'd rather stay anonymous.

Please give us the time to release a fix before disclosing the issue publicly.
