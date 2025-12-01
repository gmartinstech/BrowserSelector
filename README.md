# Browser Selector

[![Build and Release](https://github.com/gsilva/BrowserSelector/actions/workflows/build.yml/badge.svg)](https://github.com/gsilva/BrowserSelector/actions/workflows/build.yml)
[![GitHub release](https://img.shields.io/github/v/release/gsilva/BrowserSelector)](https://github.com/gsilva/BrowserSelector/releases/latest)

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

```powershell
jpackage --type app-image `
  --name "BrowserSelector" `
  --input target `
  --main-jar browser-selector-1.0.0.jar `
  --main-class com.browserselector.Main `
  --java-options "--enable-native-access=ALL-UNNAMED" `
  --dest dist
```

## Releases

Releases are automated via GitHub Actions:

1. Go to **Actions** > **Version Bump**
2. Click **Run workflow**
3. Select bump type: `patch`, `minor`, or `major`
4. The workflow will update the version, create a tag, and trigger a release build

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
