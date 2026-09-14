# Browser Selector

[![Build and Release](https://github.com/gmartinstech/BrowserSelector/actions/workflows/build.yml/badge.svg)](https://github.com/gmartinstech/BrowserSelector/actions/workflows/build.yml)
[![GitHub release](https://img.shields.io/github/v/release/gmartinstech/BrowserSelector)](https://github.com/gmartinstech/BrowserSelector/releases/latest)

A Windows 11 browser selector application built with Java 21. When you click a link anywhere on Windows, this app prompts you to choose which browser to use, with support for domain-specific defaults.

## Features

- **Browser Detection** - Automatically detects all installed browsers (Chrome, Canary, Brave, Firefox, Edge, Opera, etc.)
- **Profile Support** - Detects Chrome/Firefox/Edge profiles as separate options
- **URL Pattern Rules** - Wildcard patterns like `*.google.com` or `github.com/*`
- **Incognito Mode** - Shift+click to open in private browsing
- **Link Piling** - Links opened together pile into one picker window, each with its own browser choice; one action opens them all
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

Requires **JDK 24+** (JDK 25 recommended). Build the jar first, stage it on its own, then package:

```powershell
mvn clean package -DskipTests

# Stage only the shaded jar (--input target would also bundle original-*.jar)
New-Item -ItemType Directory -Force -Path app-input
Copy-Item target/browser-selector-*.jar app-input/ -Exclude original-*

jpackage --type app-image `
  --name "BrowserSwitch" `
  --input app-input `
  --main-jar (Get-ChildItem app-input/browser-selector-*.jar).Name `
  --main-class com.browserselector.Main `
  --app-version (Select-Xml -Path pom.xml -XPath "//*[local-name()='project']/*[local-name()='version']").Node.InnerText `
  --vendor "MartinsTech" `
  --icon src/main/resources/icon.ico `
  --java-options "--enable-native-access=ALL-UNNAMED" `
  --dest dist
```

Then make the launcher single-process (see Troubleshooting below):

```powershell
$cfg = "dist\BrowserSwitch\app\BrowserSwitch.cfg"
(Get-Content $cfg -Raw) -replace '(\[Application\])', "`$1`nwin.norestart=true" |
  Set-Content $cfg -NoNewline -Encoding ASCII
```

### Troubleshooting: "GetMessage() failed. System error [1400]"

If the app shows an error dialog like `GetMessage() failed. System error [1400]
(invalid window handle)`, that message comes from the jpackage launcher itself, not the
Java app. Since JDK 21 the GUI launcher relaunches itself and the parent process waits
in a Win32 `GetMessage()` loop on a hidden message-only window (JDK-8294699). If that
window's handle goes stale while the loop is pumping, `GetMessage` fails with
`ERROR_INVALID_WINDOW_HANDLE` (1400) and the launcher pops this dialog.

The fix is to make the launcher start the JVM in-process instead of relaunching, by
setting `win.norestart=true` in the `[Application]` section of
`app\BrowserSwitch.cfg` (supported since JDK 24, JDK-8340311). This removes the
second process and the message loop entirely, so the error can no longer occur.
This is what the CI pipeline and the instructions above now do.

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
