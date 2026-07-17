# Kiro Control Panel

A Java Swing system-tray app that gives CLI-only [Kiro](https://kiro.dev) users
the same MCP server, steering doc, skill, and hook management the IDE builds
in — without needing the IDE. It reads and writes the exact files Kiro itself
uses (`~/.kiro/...` and `<workspace>/.kiro/...`), so changes made here take
effect in Kiro immediately, and vice versa.

See [SPEC.md](SPEC.md) for the full design writeup, including what was tried
and ruled out for usage/credit tracking.

## Features

- **MCP Servers**: add/edit/enable/disable/remove entries in `mcp.json`
  (global and per-workspace), with a Form or Raw JSON editor for each server
- **Steering Docs**: create/edit/delete `.kiro/steering/*.md`, including
  front-matter inclusion modes (always/fileMatch/manual/auto)
- **Skills**: create/edit/delete `.kiro/skills/<name>/SKILL.md`, with a
  read-only browser for bundled `scripts/`/`references/`/`assets/`
- **Hooks**: add/edit/enable/disable/remove workspace hooks across
  `.kiro/hooks/*.json`, with the same Form/Raw JSON editing
- **Tray-resident**: minimizes to the system tray instead of exiting; FlatLaf
  theming (light/dark/IntelliJ themes) under Config > Theme

Usage/credit tracking is intentionally not implemented — see SPEC.md's
Non-goals section for why.

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
├── MainWindow.java           # JFrame, tabbed panels, File/Config menus
├── ThemeManager.java         # FlatLaf theme switching
├── AppPreferences.java       # recent workspaces, theme, window bounds
├── KiroPaths.java            # resolves ~/.kiro vs KIRO_HOME, workspace .kiro
├── WorkspaceScope.java       # Global-vs-workspace scope shared across panels
├── mcp/                      # MCP server management (mcp.json)
├── steering/                 # Steering doc management (steering/*.md)
├── skills/                   # Skill management (skills/<name>/SKILL.md)
├── hooks/                    # Hook management (hooks/*.json)
├── usage/                    # Static placeholder — see SPEC.md
└── util/                     # FrontMatterParser, JsonMapperFactory,
                               # WorkspaceScopeBar, RawJsonEditorDialog, IconFactory
```

## Dependencies

- **Jackson**: JSON parsing for `mcp.json` and `hooks/*.json`
- **SnakeYAML**: front-matter parsing for steering docs and `SKILL.md`
- **FlatLaf**: modern Swing look and feel with themes
- **SLF4J/Logback**: logging framework
- **JUnit 5 / Mockito**: testing

## Development

```bash
mvn clean test      # run unit tests
mvn clean package   # build target/kiro-control-panel-all.jar
```

## License

See [LICENSE](LICENSE) for details.
