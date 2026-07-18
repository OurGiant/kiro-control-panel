# Kiro Control Panel — Spec v1

## Vision

A system-tray-resident Java Swing app that exposes the Kiro IDE's built-in
management surfaces — MCP servers, Steering docs, Skills, Hooks, and Usage —
to CLI-only Kiro users. It edits the *same on-disk files* Kiro itself reads
(`~/.kiro/...` and `<workspace>/.kiro/...`), so nothing is duplicated or
proprietary: changes made here take effect in Kiro (IDE or CLI) immediately,
and vice versa.

Built as a sibling of `aws-idp-saml-ui`, reusing its proven patterns: FlatLaf
theming, minimize-to-tray, Maven shade single-jar packaging, slf4j/logback
logging.

## Development workflow

As of `v1.0.0`, this repo is issue/PR-driven rather than direct-to-`main`:

- **Every change starts as a GitHub issue.** Bugs, features, and chores all
  get filed first — this is what "release-driven" means in practice: a
  milestone (see below) only contains work that's actually been scoped as
  an issue, not whatever happened to get built in a given session.
- **One short-lived branch per issue**, named `feature/<issue-#>-slug` or
  `fix/<issue-#>-slug` — matches the convention already established in
  `aws-idp-saml-ui`'s history (`feature/83-macos-icns-icon`,
  `feature/84-fix-orphaned-browser-on-cancel`).
- **Merge to `main` via PR only**, referencing `Closes #<issue-#>` so
  merging auto-closes the issue. `main` has GitHub branch protection
  enabled: PRs required, force-pushes and branch deletion blocked,
  enforced even for admins. No approval count is required (solo
  maintainer), and required status checks aren't configured yet — see the
  note below.
- **Milestones represent releases** (e.g. `v1.1.0`). Assign issues to a
  milestone to define what's actually in scope for the next tag. When a
  milestone's issues are done, tag `main` at that commit
  (`vX.Y.Z`) — same as `v1.0.0`, no change to `build.yml`'s existing
  release-on-tag behavior.
- **No `develop`/`release` branches.** GitFlow's multi-branch ceremony
  earns its keep with multiple concurrent release trains; for a
  single-maintainer repo it's just friction. Plain GitHub Flow (branch →
  PR → merge → tag when ready) covers everything this project needs.

**Deliberately deferred:** required status checks on the branch protection
rule (i.e. blocking merge until `build.yml`'s CI jobs pass). The exact
check-run names weren't known in advance, and configuring the wrong ones
would silently and permanently block every future merge — safer to learn
the real names from an actual PR's CI run first, then add them.

## Non-goals (v1)

- Not a chat client — does not talk to models or run agents.
- Not a replacement for `kiro-cli` — a companion for config/file management
  it doesn't expose well to CLI users.
- Personal usage/credit balance: **no viable path exists yet, so this is
  held rather than shipped.** Ruled out, in order:
  1. Shelling out to `kiro-cli` for `/usage` output — doesn't work.
     `kiro-cli chat --no-interactive` explicitly excludes slash commands
     ("Interactive slash commands are not available" in headless mode);
     `/usage` only renders inside the live interactive REPL.
  2. Linking to the AWS-side web dashboard — not useful for most users.
     It's an **org-admin** surface (`q:ListDashboardMetrics` etc.), gated
     behind IAM Identity Center admin permissions most individual devs
     don't have, and shows org-wide aggregates, not a personal balance
     anyway.
  3. Calling the undocumented internal API the IDE itself uses
     (`q.us-east-1.amazonaws.com`, `X-Amz-Target:
     AmazonCodeWhispererService.GetUsageLimits`, reverse-engineered by
     third-party proxy projects) — **deliberately rejected**, not just
     hard. This is Kiro's private backend contract, not a published API;
     reimplementing its auth flow to call it directly is very likely a
     ToS violation (it's exactly what those proxy tools exist to route
     around) and it's an unversioned internal surface with no vendor
     support if it changes.

  v1's Usage tab is a static note pointing users at `kiro-cli` → `/usage`
  directly — most people using this tool already have the CLI open
  anyway. Revisit once kirodotdev/Kiro#7752 (a scriptable usage API) ships.

## Kiro file formats (source of truth, from kiro.dev docs)

| Surface  | Global path                | Workspace path                    | Format |
|----------|-----------------------------|------------------------------------|--------|
| MCP      | `~/.kiro/settings/mcp.json` | `<ws>/.kiro/settings/mcp.json`     | JSON — `mcpServers` map of `{command,args,env,disabled,autoApprove,disabledTools}` or `{url,headers,oauth,oauthScopes,...}` for remote servers. Workspace overrides global. Kiro reloads automatically on save. |
| Steering | `~/.kiro/steering/*.md`     | `<ws>/.kiro/steering/*.md`        | Markdown + YAML front matter: `inclusion: always\|fileMatch\|manual\|auto`, `fileMatchPattern`, `name`/`description` (for `auto`). |
| Skills   | `~/.kiro/skills/<name>/SKILL.md` | `<ws>/.kiro/skills/<name>/SKILL.md` | Folder per skill; `SKILL.md` (YAML front matter + instructions) is required, plus optional `scripts/`, `references/`, `assets/`. |
| Hooks    | — (workspace only)          | `<ws>/.kiro/hooks/*.json`         | JSON: `name`, `trigger` (e.g. `PostFileSave`), `matcher`, `action{type,command\|prompt}`, `timeout`, `enabled`. |
| Usage    | n/a (no local file)         | n/a                                | Held — no viable local/API source; see Non-goals. |

`KIRO_HOME` env var is honored to relocate the global `~/.kiro` root, matching
Kiro's own behavior.

## Feature scope

1. **MCP Servers panel** — table of servers scoped by Global / Workspace tabs;
   enable/disable toggle (writes `disabled`); add/edit/remove server via
   dialog (command/args/env/autoApprove/disabledTools, or url/headers/oauth
   for remote); raw-JSON fallback view for anything the form doesn't cover;
   "reveal file" action.
2. **Steering panel** — list per scope; embedded text editor for body +
   form for front-matter fields; "new from template"
   (product.md/tech.md/structure.md scaffolds); delete.
3. **Skills panel** — list per scope; edit `SKILL.md` (front matter + body);
   read-only tree view of `scripts/`/`references/`/`assets/`; scaffold new
   skill folder; delete.
4. **Hooks panel** — workspace-only list; enable/disable toggle; form editor
   for trigger/matcher/action/timeout + raw-JSON fallback; create/delete.
5. **Usage panel** — held (see Non-goals). Static note directing users to
   `kiro-cli` → `/usage` instead of a broken or ToS-risky feature.

## Cross-cutting behavior

- **Workspace picker**: user adds/pins project directories (a "recent
  workspaces" list, persisted via `java.util.prefs.Preferences` — no SQLite
  needed, our local state is tiny). Global-scope tabs are always visible;
  workspace-scope tabs appear per pinned workspace.
- **Live file watching**: `java.nio.file.WatchService` on active `.kiro`
  dirs, so edits made in Kiro IDE itself are reflected without manual
  refresh.
- **Tray-first UX**: window hidden by default; tray icon restores on click;
  tray menu offers quick actions (New Steering Doc, Open Workspace…, Exit).
  Close button minimizes to tray, same as `aws-idp-saml-ui`.
- **Theming**: port `ThemeManager` wholesale (FlatLaf + IntelliJ themes).
- **No AWS SDK / credential dependency in v1 core** — MCP/steering/skills/
  hooks are pure local file operations, so the app stays auth-free for its
  primary use cases (unlike `aws-idp-saml-ui`, which needs STS).

## Tech stack

- Java 24 (Corretto, matches the `maven-amz24` build container), Maven,
  `maven-shade-plugin` → single runnable jar (pattern copied from
  `aws-idp-saml-ui/pom.xml`).
- FlatLaf + `flatlaf-intellij-themes` + `flatlaf-extras`.
- Jackson (`jackson-databind`) for JSON (mcp.json, hooks/*.json) — new dep,
  since Kiro uses JSON here, not INI like the SAML app.
- SnakeYAML for front-matter parsing in steering/skill markdown.
- slf4j + logback (logging).
- JUnit 5 + Mockito (tests).
- Explicitly **not** carried over from `aws-idp-saml-ui`: Selenium, AWS SDK,
  ini4j, sqlite-jdbc — none of those are needed for pure file management.

## Proposed package layout

```
com.ourgiant.kirocontrolpanel/
├── TrayApp.java              # main() + tray icon lifecycle, X11 popup-position fix
├── MainWindow.java           # JFrame, tabbed panels, File/Config/Help menus
├── FirstRunDialog.java       # one-time welcome dialog
├── AboutDialog.java          # version + update-check display (Help > About)
├── LogViewerDialog.java      # live-ish log tail (Help > View Logs...)
├── ThemeManager.java         # ported from aws-idp-saml-ui
├── AppPreferences.java       # recent workspaces, theme, window bounds, first-run flag
├── KiroPaths.java            # resolves ~/.kiro vs KIRO_HOME, workspace .kiro
├── WorkspaceScope.java       # Global-vs-workspace scope record, shared across panels
├── mcp/        McpServerConfig.java McpConfigFile.java McpConfigService.java
│               McpServerTableModel.java McpServerFormPanel.java McpServerEditDialog.java
├── steering/   SteeringDoc.java SteeringService.java SteeringTemplates.java
│               SteeringPanel.java SteeringEditorDialog.java
├── skills/     Skill.java SkillService.java SkillsPanel.java SkillEditorDialog.java
├── hooks/      Hook.java HookAction.java HookFile.java HookEntry.java HookService.java
│               HookTableModel.java HookFormPanel.java HooksPanel.java HookEditDialog.java
├── usage/      UsagePanel.java   # static "held" note, see Non-goals
└── util/       FrontMatterParser.java JsonMapperFactory.java AppVersion.java LogPaths.java
                UpdateChecker.java WorkspaceScope[Bar|Registry].java RawJsonEditorDialog.java
                DirectoryWatcher.java DesktopUtils.java SwingLayoutUtils.java IconFactory.java
```

Note the `*FormPanel` split: `McpServerEditDialog`/`HookEditDialog` are thin
`JDialog` chrome around `McpServerFormPanel`/`HookFormPanel` (plain
`JPanel`s owning the actual Form/Raw-JSON-tab-sync logic). `JDialog`
construction throws `HeadlessException` in a headless environment (this
project's Maven container has no display); a plain `JPanel` doesn't. The
split exists specifically so the trickiest logic in the app — the tab
reentrancy guard, form↔JSON sync — has real automated test coverage instead
of being verified by hand each time.

## Milestones

- **M0** — done. Scaffold, tray icon, X11 popup-at-(0,0) fix ported from
  `aws-idp-saml-ui` (#71/#70).
- **M1** — done. MCP panel: structured CRUD + raw-JSON fallback, global +
  workspace.
- **M2** — done. Steering panel.
- **M3** — done. Skills panel, including the "SKILL.md must be in a
  subfolder" rule Kiro enforces.
- **M4** — done. Hooks panel (workspace-only; a workspace can spread hooks
  across multiple `.kiro/hooks/*.json` files, each shaped
  `{"version":"v1","hooks":[...]}` — confirmed against kiro.dev docs rather
  than assumed).
- **M5** — held, not a real feature. See Non-goals — static note only.
- **M6** — done, with one known gap. Live file watching (`DirectoryWatcher`,
  shared/debounced `WatchService`, wired into all four panels — Skills
  additionally watches each individual skill's own folder since a top-level
  watch only catches add/remove, not edits to an existing skill's files);
  first-run welcome dialog; native packaging via `jpackage` (bundled with
  the JDK, no separate install). Verified locally: `app-image` build reaches
  `MainWindow`'s `JFrame` construction before failing on "no X display" —
  the same point a plain `java -jar` run fails at in this headless
  container, confirming the packaging itself is correct. `.deb` isn't
  buildable in the `festive_bardeen` container (no `dpkg-deb`), and
  Windows/macOS installers can't be built at all outside their own OS
  (`jpackage` doesn't cross-compile) — both are wired into
  `.github/workflows/build.yml` (mirroring `aws-idp-saml-ui`'s matrix) to
  build on GitHub's real runners on tag push instead.
  **Resolved (issue #2):** Windows/macOS need real `.ico`/`.icns` files
  rather than the runtime-drawn `app-icon.png` Linux accepts directly. The
  `festive_bardeen` container turned out to have `dnf` access after all
  (confirmed while verifying issue #1's `.deb` build); `ImageMagick`
  (`convert`) generates `src/packaging/app-icon.ico` as a proper
  multi-resolution icon (256/128/64/48/32/16), and `libicns-utils`
  (`png2icns`) generates `src/packaging/app-icon.icns` from 16/32/48/128/256
  PNGs resized from the same 256×256 source — both committed, with the
  `--icon` flags wired into `build.yml`'s `build-windows`/`build-macos`
  jobs. Full end-to-end verification (that jpackage actually renders these
  correctly into a Windows `.exe`/app-image or macOS `.dmg`) can only happen
  on GitHub's real Windows/macOS runners at the next tagged release, since
  `jpackage` doesn't cross-compile and this container is Linux-only.

## Post-M6 polish ("Claude's Choice")

A self-directed evaluation pass over the whole codebase, prioritized and
executed as Tier 1 (real gaps) and Tier 2 (worthwhile cleanup):

- **Cross-panel workspace sync** (`WorkspaceRegistry`) — each panel's
  `WorkspaceScopeBar` used to only read `AppPreferences` at its own
  construction/reload time, so pinning a workspace in one panel silently
  didn't show up in another until something else triggered that panel's
  reload. Now every `WorkspaceScopeBar` instance registers with a shared
  registry and reloads whenever *any* instance changes the pinned list —
  while preserving each panel's own current selection unless the workspace
  it had selected was the one that got removed.
- **"Reveal File" action** (`DesktopUtils`) — added to all four panels;
  was in the original spec for MCP but never built.
- **Shared panel utilities** (`SwingLayoutUtils`) — the vertical
  side-button-panel construction and "Delete X? This cannot be undone."
  confirm dialog were near-identical copy-paste across all four panels;
  extracted once both were about to be touched anyway for the above.
- **Steering "new from template"** (`SteeringTemplates`) — creating a file
  named exactly `product.md`/`tech.md`/`structure.md` now scaffolds
  Kiro's standard starter content instead of an empty file.
- **`McpServerFormPanel` / `HookFormPanel` extraction** — the Form/Raw-JSON
  tab-sync logic (the trickiest code in the app: a reentrancy guard plus
  bidirectional JSON↔form sync) lived inside `McpServerEditDialog`/
  `HookEditDialog`, both `JDialog`s. `JDialog` construction throws
  `HeadlessException` in this project's headless build container, so that
  logic had zero automated coverage — it was verified once by hand and
  then trusted. Split into plain-`JPanel` form classes (`JPanel`
  constructs fine headlessly, confirmed empirically before committing to
  this approach) specifically to make it testable; 16 new tests now cover
  both directions of the sync, the reentrancy guard across repeated
  switches, and round-trip fidelity. The invalid-JSON *dialog* itself
  still can't be tested headlessly (`JOptionPane` has the same
  `HeadlessException` issue) — the parsing logic that feeds it was
  refactored to a dialog-free method (`tryParseRawJsonIntoForm`) so at
  least the "does this detect bad JSON" part has coverage; the "does it
  correctly show an error and revert the tab" part remains manual/host-only
  verification, same as everything else in `.claude/skills/verify/SKILL.md`.
- **About dialog** (`AboutDialog`, `AppVersion`, Help menu) — nothing in
  the running app showed a version before this; `version.properties` +
  `pom.xml` resource filtering ported from `aws-idp-saml-ui`.

56 tests total (up from 33 at the end of M6).

### Tier 3 — new features, evaluated separately

Two genuinely new features (not bug fixes) considered alongside the
polish above, plus one item deliberately skipped:

- **Skipped**: `DirectoryWatcher` self-write suppression (our own saves
  trigger a harmless redundant reload ~300ms later) — real complexity for
  no visible benefit, left as-is by design.
- **Log viewer** (`LogViewerDialog`, `LogPaths`, Help > View Logs...) —
  non-modal, auto-refreshes on a timer while open (skips re-reading the
  file if its size hasn't changed since the last tick), plus an "Open Log
  Folder..." button reusing `DesktopUtils`. Correction to the original
  Tier 3 writeup: this was *not* actually mirroring an `aws-idp-saml-ui`
  feature as first claimed — verified via `grep` that no such log viewer
  exists there; this is a fresh design specific to this app.
- **Update check** (`UpdateChecker`, wired into `AboutDialog`) — genuinely
  ported from `aws-idp-saml-ui`'s `SwingMain.fetchLatestRelease()`/
  `isNewerVersion()`, polling `GET /repos/OurGiant/kiro-control-panel/releases/latest`.
  Verified end-to-end on the host (real display, real network call): as
  of this writing the repo has no tagged releases yet, so it correctly
  falls back to "Could not check for updates" (confirmed via `gh api` that
  the endpoint genuinely 404s) rather than crashing — will start reporting
  real version comparisons once `build.yml`'s release job runs on a
  `v*` tag.

## Linux desktop integration (issue #1)

**Known, accepted limitation: COSMIC shows a generic icon and has no
system tray, regardless of anything on our end.** Confirmed by testing
the same build on the same physical machine under KDE, where both the
system tray and the correct custom icon work correctly — isolating the
problem to COSMIC's compositor, not this app:

- `SystemTray.isSupported()` returns `false` on COSMIC — no XEmbed
  systray protocol implementation. Nothing to work around from the app
  side; this is a missing compositor feature.
- COSMIC's dock/launcher unreliably resolves app icons for XWayland
  clients, matching known open upstream bugs:
  [pop-os/cosmic-epoch#2662](https://github.com/pop-os/cosmic-epoch/issues/2662)
  (generic "gear" icon until the icon cache is manually rebuilt),
  [pop-os/cosmic-epoch#2847](https://github.com/pop-os/cosmic-epoch/issues/2847)
  (missing icons more broadly).

**What we did fix**: the `.deb` package shipped no `.desktop` file at
all (no `--linux-shortcut` flag), so there was never any desktop
integration to speak of on *any* Linux desktop, COSMIC or otherwise —
no app-menu entry, no taskbar/dock icon association via `StartupWMClass`.
Added a custom `.desktop` template
(`src/packaging/linux/kiro-control-panel.desktop`) via jpackage's
`--resource-dir` override, since jpackage's own auto-generated template
(`jdk.jpackage.internal.resources/template.desktop`, extracted directly
from the JDK to confirm rather than guessed) has no `StartupWMClass`
field at all — a well-documented jpackage gap hit by several other Java
desktop projects (JetBrains, JabRef, JOSM). Our override preserves every
token the original template substitutes
(`APPLICATION_NAME`/`APPLICATION_DESCRIPTION`/`APPLICATION_LAUNCHER`/
`APPLICATION_ICON`/`DEPLOY_BUNDLE_CATEGORY`/`DESKTOP_MIMES`) and adds
`StartupWMClass=com-ourgiant-kirocontrolpanel-TrayApp` (the app's real
runtime WM_CLASS, confirmed via `xwininfo` earlier in development).

This is correct, standard Linux desktop integration regardless of the
COSMIC angle — GNOME/KDE/XFCE etc. all benefit from a real `.desktop`
entry (app-menu visibility, proper taskbar grouping) even though they
didn't strictly need it to show the icon correctly. Whether it also
happens to paper over COSMIC's specific bug is unconfirmed and not
guaranteed, given the upstream issues are still open — the system tray
gap is unaffected either way.

63 tests total (up from 56).

## Linux terminal/logging/launcher fix (issue #9)

Running the installed `.deb`/`.rpm` binary directly from a terminal
(`/opt/kiro-control-panel/bin/kiro-control-panel`) blocked the shell for
the app's entire lifetime and dumped the full logback stream to stdout —
neither is normal or wanted for a tray-resident GUI app. Three changes:

1. **Logging**: removed `logback.xml`'s `ConsoleAppender`; logs go only to
   `~/.kiro-control-panel/logs/app.log` (already viewable via "View
   Logs..." in the Help menu, from the Tier 3 polish pass).
2. **Terminal prompt**: added `util/ProcessDetacher`, called first thing in
   `TrayApp.main()`. On Linux, when running as the packaged native
   launcher (detected by checking `ProcessHandle.current().info().command()`
   isn't `java`/`javaw` — i.e. not a dev `java -jar` run), it relaunches
   itself with `ProcessBuilder` (stdio redirected to `/dev/null`, a
   `KIRO_CONTROL_PANEL_DETACHED=1` env var set to prevent re-triggering)
   and returns immediately, so the invoking shell gets its prompt back
   without ever touching Swing/AWT in the original process. Windows/macOS
   are untouched — this is scoped to the Linux terminal-blocking complaint
   specifically.
3. **PATH-accessible command**: overrode jpackage's Debian `postinst`/
   `prerm` templates (`src/packaging/linux/postinst`, `.../prerm`) via the
   same `--resource-dir` mechanism as the `.desktop` fix, adding exactly
   one line to each (`ln -sf /opt/kiro-control-panel/bin/kiro-control-panel
   /usr/bin/kiro-control-panel` on install, `rm -f
   .../kiro-control-panel` on uninstall) so `kiro-control-panel` works from
   anywhere without users needing the full path. Diffed against jpackage's
   extracted originals (`jdk.jpackage.internal.resources/template.postinst`
   `/template.prerm`) to confirm every existing token
   (`LAUNCHER_AS_SERVICE_SCRIPTS`, `DESKTOP_COMMANDS_INSTALL`/
   `_UNINSTALL`, etc.) is untouched, and `sh -n` confirms both are
   syntactically valid. Unlike the `.desktop` template (shared by both the
   rpm and deb bundlers, so verifiable locally via `--type rpm`),
   `postinst`/`prerm` are Debian-specific with no rpm equivalent — this
   couldn't be built or run end-to-end in the `festive_bardeen` container
   (no `dpkg-deb`, and it's not installable via `dnf` on Amazon Linux
   2023). The literal install path (`/opt/kiro-control-panel/bin/...`) was
   confirmed from the user's own real installed-`.deb` terminal output
   rather than guessed, so the symlink target is verified even though the
   maintainer-script mechanics aren't; full confirmation only happens once
   an actual tagged release builds the real `.deb` on GitHub's Ubuntu
   runner.

72 tests total.
