# Browser Selector

A Windows 11 browser selector application built with Java 21. When you click a link anywhere on Windows, this app prompts you to choose which browser to use, with support for domain-specific defaults.

## Features

- **Browser Detection** - Automatically detects all installed browsers (Chrome, Canary, Brave, Firefox, Edge, Opera, etc.)
- **Profile Support** - Detects Chrome/Firefox/Edge profiles as separate options
- **URL Pattern Rules** - Wildcard patterns like `*.google.com` or `github.com/*`
- **Incognito Mode** - Shift+click to open in private browsing
- **Simple/Advanced Mode** - Toggle to show/hide power-user features
- **Native Look** - Modern Windows 11-style UI with light/dark theme support

## Requirements

- Windows 11/10
- Java 21+ (bundled in release)

## Building

```bash
mvn clean package
```

## Creating Windows EXE

```bash
jpackage --type app-image \
  --name "BrowserSelector" \
  --input target \
  --main-jar browser-selector.jar \
  --main-class com.browserselector.Main \
  --app-version 1.0.0 \
  --win-console false \
  --dest dist
```

## Usage

1. Run `BrowserSelector.exe --settings` to open settings
2. Click "Register as Default Browser"
3. Follow Windows prompts to set as default
4. Click any link - Browser Selector will prompt you to choose

## Tech Stack

- Java 21 (records, pattern matching, virtual threads)
- FlatLaf (modern Swing look and feel)
- SQLite (preferences storage)
- JNA (Windows registry access)

## License

MIT
