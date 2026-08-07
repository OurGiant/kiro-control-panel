# Kiro Control Panel

[![Build](https://github.com/OurGiant/kiro-control-panel/actions/workflows/build.yml/badge.svg)](https://github.com/OurGiant/kiro-control-panel/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/OurGiant/kiro-control-panel?label=Release)](https://github.com/OurGiant/kiro-control-panel/releases/latest)
[![License: MIT](https://img.shields.io/github/license/OurGiant/kiro-control-panel)](LICENSE)
[![Java 24](https://img.shields.io/badge/Java-24-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Platforms](https://img.shields.io/badge/platform-Linux%20%7C%20macOS%20%7C%20Windows-blue)](#installation)

A Java Swing system-tray app that gives CLI-only [Kiro](https://kiro.dev) users
the same MCP server, steering doc, skill, hook, and agent management the IDE
builds in — without needing the IDE. It reads and writes the exact files Kiro
itself uses (`~/.kiro/...` and `<workspace>/.kiro/...`), so changes made here
take effect in Kiro immediately, and vice versa.

See [SPEC.md](SPEC.md) for the full design writeup, including what was tried
and ruled out for usage/credit tracking, and the story behind every feature
below.

## Features

### Config management (Global + per-workspace, everywhere)

Every panel below has a Global tab plus one tab per pinned workspace, and
edits take effect in Kiro (IDE or CLI) immediately, since it's the same
on-disk files — nothing is synced or duplicated. Pinning a workspace in one
panel makes it available in every other panel too, and a "Reveal File"
action on each item opens its real location in the OS file manager. A live
filter field above each panel's list narrows it to matching entries as you
type — useful once a scope accumulates more MCP servers, docs, skills,
hooks, or agents than fit on screen at once. A "Duplicate..." action on
each item opens the same editor pre-filled with a copy of the selected
item's data under a suggested new name — a faster starting point than
"New..." when a new entry is mostly like an existing one. A "Copy to..."
action promotes an item to a different scope — Global to a pinned
workspace, or vice versa — pre-filling the same editor with a copy of its
data, keeping the original name unless the destination already has one by
that name.

- **MCP Servers**: add/edit/enable/disable/remove entries in `mcp.json`,
  with a Form or Raw JSON editor for each server; a "Browse Catalog..."
  picker for one-click adding known servers from
  [kiro.dev's known MCP server list](https://kiro.dev/docs/mcp/servers/),
  with a "Refresh" button that pulls the current list live from GitHub
- **Steering Docs**: create/edit/delete `.kiro/steering/*.md`, including
  front-matter inclusion modes (always/fileMatch/manual/auto), with
  scaffolded starter content for `product.md`/`tech.md`/`structure.md`
- **Skills**: create/edit/delete `.kiro/skills/<name>/SKILL.md`, with a
  read-only browser for bundled `scripts/`/`references/`/`assets/`
- **Hooks**: add/edit/enable/disable/remove workspace hooks across
  `.kiro/hooks/*.json`, with the same Form/Raw JSON editing
- **Agents**: create/edit/delete `.kiro/agents/*.json` custom agent configs,
  with a Form editor for the common fields and Raw JSON for the rest

### Usage (tab)

Shows your live plan/credit balance — plan name, credits used vs. cap,
billing cycle reset date, and overage status — fetched from the local
`kiro-cli` binary over its own Agent Client Protocol (ACP) interface, with a
"Refresh" button to pull the latest numbers on demand. This talks only to
your own already-authenticated local CLI process, never to Kiro's backend
directly; see [SPEC.md](SPEC.md)'s Non-goals section for the full story on
why this path was chosen over the ones that were ruled out. Falls back to a
static note pointing at `kiro-cli` → `/usage` if `kiro-cli` isn't installed
or the call fails for any reason.

### Sessions (tab)

Indexes every kiro-cli session transcript under `~/.kiro/sessions/cli/` into
a local SQLite/FTS5 database (never written under `~/.kiro` itself, so it
never trips the external-change monitor or gets swept into snapshots) and
shows them as a sortable table — date, working directory, opening prompt,
files touched — with a detail pane for the selected session (manifest
fields, touched files with "Reveal File", and "View Raw JSON..." for the
session's own sidecar file). The filter field above the table narrows
results in-memory as you type, same as every other panel; pressing Enter
instead runs a full-text search over the indexed message content (never
tool-call/result payloads) and shows ranked, snippet-highlighted matches.
Indexing runs in the background — an initial catch-up scan on launch, then
live incremental updates as kiro-cli writes new sessions or appends to
existing ones — so it never blocks the UI. A Sessions section in
`File > Settings` controls whether indexing is on (default), where the
index database lives, and offers a "Rebuild Index" button.

### Kiro Setup Scan (Help > Scan Kiro Setup...)

Scans MCP/Steering/Skills/Hooks/Agents across Global and every pinned
workspace for structural problems — invalid JSON/YAML, a stray skill file
sitting outside its own subfolder, an un-wrapped hooks array, a server or
hook missing a required field, and more. Mechanically-safe issues (a
misplaced skill file, a bare hooks array) get a one-click "Fix..." with a
preview before it's applied, or "Fix All..." to apply every fixable
finding in the list at once (useful when onboarding an existing `~/.kiro`
tree with several fixable issues); everything else gets an "Edit..."
button that opens the real in-app editor for that item — never an
external app, so the fix stays under this app's own tracking. "Rescan"
refreshes the list after fixing something. Runs silently on first launch
(only shows up if it finds something) and on demand any time afterward.

### Change Log (Help > View Change Log...)

Tracks every change to Kiro-managed files — both edits made through this
app and ones detected from outside it (Kiro IDE, hand-editing, another
process) — across Global and every pinned workspace, filterable by preset
time range (1 day / 1 week / 2 weeks / 1 month / 3 months).

### External change monitoring

Detects changes to `~/.kiro` made from outside the app and shows a tray
notification — since Kiro reads everything under there as instructions,
this also guards against adversarial poisoning of steering/skills/agents
content, not just accidental drift. `File > Pause Change Alerts` for a
one-off pause; a persistent on/off toggle lives in Settings.

### Optional git audit trail (File > Settings > Git)

"Track ~/.kiro Changes with Git" auto-commits every app-driven write under
the global `~/.kiro` tree to a local git repo, turning it into a
self-maintaining, revertible history you can inspect with git directly —
no in-app version browser needed.

### Periodic .kiro snapshots (File > Settings > Snapshots)

Zips the global `~/.kiro` tree to a destination folder you choose, on a
schedule (30 minutes / 1 hour / 4 hours / 12 hours / 1 day), pruning down
to a keep-last-N count after each one — plus a "Snapshot Now" button for
an ad-hoc backup at any time. Off by default until a destination is set.
`sessions/`, `extensions/`, and `.git` (Kiro- and tooling-managed ephemeral
state) are excluded from every snapshot.

### Launch kiro-cli... (every scope bar, and the tray icon menu)

Opens an independent terminal window (macOS Terminal.app, Windows
PowerShell 7, or Ubuntu's GNOME Terminal) running `kiro-cli` in the
current scope's directory — the app never reads or drives the session,
it's just a launcher.

### The app itself

- **Tray-resident**: minimizes to the system tray instead of exiting
- **Theming**: FlatLaf light/dark/IntelliJ themes via `File > Settings`
- **First-run welcome dialog** explaining the core "edits Kiro's own files"
  idea and tray-first UX
- **Help > View Logs...**: live-tailing view of the app's own log file
- **Help > About**: version display plus an update check against the
  latest GitHub release
- **Native installers** for Windows, macOS, and Linux (`.deb`/`.rpm`) via
  `jpackage`, with proper Linux desktop integration (app-menu entry,
  taskbar icon, `kiro-control-panel` on `PATH`)

## Prerequisites

- Java 24
- Maven 3.6+

## Installation

```bash
git clone <repository-url>
cd kiro-control-panel
mvn clean package
```

## Usage

Run the packaged jar:

```bash
java -jar target/kiro-control-panel-all.jar
```

The app starts in the system tray; click the tray icon to open the main
window. Closing the window minimizes to tray — use File > Quit (or the tray
icon's Exit) to actually exit.

### Pinning workspaces

Global-scope resources (`~/.kiro/...`) are always available. To manage a
project's workspace-scoped resources (`<workspace>/.kiro/...`), pin it via
"Add Workspace..." in any panel's scope bar.

## Project Structure

```
src/main/java/com/ourgiant/kirocontrolpanel/
├── TrayApp.java              # main() + tray icon lifecycle
├── MainWindow.java           # JFrame, tabbed panels, File/Help menus
├── SettingsDialog.java       # theme, git tracking, snapshots, and Windows-only prefs (File > Settings...)
├── ThemeManager.java         # FlatLaf theme switching
├── AppPreferences.java       # recent workspaces, theme, window bounds
├── KiroPaths.java            # resolves ~/.kiro vs KIRO_HOME, workspace .kiro
├── WorkspaceScope.java       # Global-vs-workspace scope shared across panels
├── mcp/                      # MCP server management (mcp.json)
├── steering/                 # Steering doc management (steering/*.md)
├── skills/                   # Skill management (skills/<name>/SKILL.md)
├── hooks/                    # Hook management (hooks/*.json)
├── agents/                   # Agent config management (agents/*.json)
├── diagnostics/              # Kiro Setup Scan (structural checks + fixes)
├── changelog/                # Change Log viewer + external-change watchers
├── snapshot/                 # Periodic .kiro backup (SnapshotService, SnapshotScheduler)
├── sessions/                 # Sessions tab: SQLite/FTS5 index of kiro-cli session history
├── usage/                    # Live usage via kiro-cli's ACP interface (KiroUsageService)
└── util/                     # FrontMatterParser, JsonMapperFactory,
                               # WorkspaceScopeBar, RawJsonEditorDialog, IconFactory,
                               # GitAutoCommitter, KiroFolderMonitor, KiroSessionLauncher
```

## Dependencies

- **Jackson**: JSON parsing for `mcp.json` and `hooks/*.json`
- **SnakeYAML**: front-matter parsing for steering docs and `SKILL.md`
- **FlatLaf**: modern Swing look and feel with themes
- **SLF4J/Logback**: logging framework
- **sqlite-jdbc**: local SQLite/FTS5 index for the Sessions tab
- **JUnit 5 / Mockito**: testing

## Development

```bash
mvn clean test      # run unit tests
mvn clean package   # build target/kiro-control-panel-all.jar
```

A `Dockerfile` is included for a reproducible Maven+JDK 24 build
environment (`docker build -t kiro-cp-maven .`) — see the comments in the
Dockerfile for the build/run/exec commands. The app itself is a Swing GUI
and must still run on the host, not inside the container.

## License

See [LICENSE](LICENSE) for details.
