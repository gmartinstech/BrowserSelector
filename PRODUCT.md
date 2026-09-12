# Product

<!-- impeccable:product-schema 1 -->

## Platform

windows-desktop

Native Windows 10/11 desktop app (Java 21, Swing + FlatLaf). Impeccable's platform enum (web/ios/android/adaptive) has no desktop value, so the truth is recorded as-is; iOS/Android guidance does not apply.

## Users

Windows power users who keep multiple browsers and browser profiles (work/personal split, engine preference, isolation) and open links from anywhere in Windows — email clients, chat apps, Office, terminals. Their job: land every link in the right browser or profile on the first try, without repeatedly flipping their system default and without being nagged for domains they have already decided. Secondary audience: open-source users who install from GitHub releases and judge the tool within the first few links.

## Product Purpose

Browser Selector registers itself as a Windows browser handler. When any app opens a link, it either auto-routes the URL to a browser via a matching wildcard rule or shows a compact picker listing detected browsers and profiles (Shift+click for incognito/private). Preferences, rules, and detected browsers live in a local SQLite database. Success means every link lands in the right destination on the first attempt, with minimal friction and without permanently surrendering the system default.

## Positioning

One consistent prompt for every link in Windows, plus per-domain wildcard rules with priority that auto-route known domains — so the prompt only appears when a human decision actually matters. The OS default-browser setting can only send everything to one place; Browser Selector makes per-link choice and per-domain defaults coexist, with profile-aware destinations in the same prompt.

## Operating Context

- Launched by Windows as a URL handler: the link arrives as a command-line argument. A rule match launches silently; otherwise the picker dialog shows.
- Settings open via `--settings` (or no arguments); registering as default browser happens in-app through Windows prompts.
- The picker must work when no other app window exists — it creates a hidden owner frame to be reliable as a URL handler.
- Distributed as a jpackage app-image and an MSIX package; releases are automated through GitHub Actions.

## Capabilities and Constraints

- Detects installed browsers (Chrome, Canary, Brave, Firefox, Edge, Opera, and others) and treats browser profiles as separate selectable destinations.
- Wildcard URL rules (`*.google.com`, `github.com/*`) with priority; the first matching rule launches without a prompt.
- Shift+click / Shift+Enter opens the link incognito/private.
- Simple/Advanced mode: Advanced adds the Browsers tab and rule reordering; the default stays simple.
- Theme follows the system light/dark setting or a manual override.
- Fully local: SQLite for data, JNA for registry access; no network features, no telemetry.
- Non-Windows demo mode exists for development/testing.
- Open decision: the Settings window title currently reads "Browser Switch" while the product is named "Browser Selector" — naming should be unified.

## Brand Commitments

- Name: "Browser Selector".
- App icon assets exist at `src/main/resources/icon.svg`, `icon.png`, and `icon.ico` and anchor the identity.

## Evidence on Hand

- README with the confirmed feature list and usage flow.
- Working build: `target/BrowserSelector/BrowserSelector.exe` (jpackage app-image); MSIX packaging assets under `packaging/msix/`.
- CI: build/release and version-bump workflows in `.github/workflows/`.
- No screenshots, testimonials, benchmarks, or user research exist in the repository — none may be fabricated.

## Product Principles

1. Decide once, then never ask again — rules exist so the prompt only appears when it matters.
2. The prompt is the product — instant to appear, obvious to read, gone in one keystroke.
3. Feel like Windows — follow the system's look and conventions; never read as a foreign app.
4. Local by default — every rule, preference, and action stays on the machine.
5. Simple first — the default surface stays approachable; power features are opt-in.
