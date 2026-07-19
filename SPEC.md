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
| Agents   | `~/.kiro/agents/*.json`     | `<ws>/.kiro/agents/*.json`        | JSON, one file per agent (filename minus `.json` is the identity by default): `name`, `description`, `prompt`, `mcpServers`, `tools`, `toolAliases`, `allowedTools`, `toolsSettings`, `resources`, `hooks`, `includeMcpJson`, `model`, `keyboardShortcut`, `welcomeMessage` — all optional. |
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
5. **Agents panel** (issue #13) — list per scope, one file per agent; form
   editor for `description`/`prompt`/`model`/`keyboardShortcut`/
   `welcomeMessage`/`tools`/`allowedTools`/`includeMcpJson` + raw-JSON
   fallback for everything else the schema allows (`name`, `mcpServers`,
   `toolAliases`, `toolsSettings`, `resources`, `hooks`) — same "form covers
   the common case, raw JSON covers the rest, unmodeled fields round-trip
   losslessly" philosophy as the MCP panel; create/delete. Purely config-file
   CRUD, same as every other panel — never executes an agent or talks to a
   model, consistent with the "does not talk to models or run agents"
   non-goal above.
6. **Usage panel** — held (see Non-goals). Static note directing users to
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

## MCP server catalog (issue #11)

Lowers the friction of adding an MCP server: a new "Browse Catalog..."
button in the MCP tab opens a non-modal, searchable browser over kiro.dev's
[known MCP server list](https://kiro.dev/docs/mcp/servers/) (~39 entries),
and picking one opens the existing "Add MCP Server" dialog pre-filled and
still fully editable — reusing all of that dialog's existing validation and
persistence rather than building a parallel add path. Scoped to MCP only;
skills/steering catalogs are explicit future work.

**Bundled, not live-fetched.** `src/main/resources/mcp-catalog.json` is
generated by `tools/generate-mcp-catalog.py` (stdlib-only Python, re-run by
hand whenever kiro.dev's list changes — see `tools/README.md`) and loaded
from the classpath at runtime via `McpCatalogService`, the same pattern
`version.properties`/`AppVersion` already use. No live network call happens
from the catalog dialog itself. The generator initially assumed the deep
link's `config=` JSON would be reachable via a plain `curl` fetch; the page
turned out to be Next.js-rendered, but empirically the actual server list is
still present in the initial server-rendered HTML (inside a `<table>`, not
hidden behind client-side JS), just not where a first grep expected it —
confirmed by downloading the raw response before writing any parsing logic,
rather than assuming a headless-browser dependency was needed.

**Two bugs caught by end-to-end verification, not by unit tests.** Both
`McpServerFormPanelTest`-style unit tests and a real driven-UI harness
(`.claude/skills/verify/SKILL.md` / `verify-java-swing`'s
invokeLater-not-invokeAndWait pattern for modal dialogs) were used; the
harness caught two real bugs the unit tests structurally couldn't:
1. `McpCatalogDialog` originally captured its `McpConfigFile` once at
   construction. `McpPanel.reloadTable()` replaces that object wholesale
   (a fresh disk read) after every persist, so a *second* catalog-driven
   add in the same browsing session silently mutated the now-stale object
   and never made it to disk. Fixed by threading a `Supplier<McpConfigFile>`
   (`tableModel::getConfig`) through instead of a captured reference, so
   every add re-queries the live object.
2. `McpServerConfig.isRemote()` had no `@JsonIgnore`, so Jackson's bean
   introspection serialized it as a real `"remote"` field in every
   `mcp.json` write (pre-existing, not introduced by this feature, but
   surfaced by the harness's exact-JSON check — none of the existing tests
   asserted the full serialized shape). Fixed with `@JsonIgnore`, with a
   regression test added to `McpConfigServiceTest`.

`McpServerEditDialog` gained a fourth-argument constructor overload for the
pre-fill case (`suggestedName`, `McpServerConfig prefill`) rather than
extending the existing 3-arg one — collapsing them via constructor
delegation was tried first and rejected: the existing 3-arg constructor's
edit-mode rename/duplicate-check logic depends on `originalName` being set
to the *existing* server name, which the pre-fill "always an add" case must
never do, so the two constructors need genuinely different `originalName`
semantics, not just different initial field values.

84 tests total.

## Agents tab (issue #13)

A missed, fundamental feature area: Kiro supports custom agents defined as
JSON config files (`.kiro/agents/*.json` workspace, `~/.kiro/agents/*.json`
global), directly analogous to MCP/Steering/Skills/Hooks. Confirmed via
kiro.dev docs (not guessed) — the full schema has 13 optional fields
(`name`, `description`, `prompt`, `mcpServers`, `tools`, `toolAliases`,
`allowedTools`, `toolsSettings`, `resources`, `hooks`, `includeMcpJson`,
`model`, `keyboardShortcut`, `welcomeMessage`).

**File-per-item, not map-in-one-file.** Unlike `mcp.json`/hooks, each agent
is its own file, with the filename (minus `.json`) as the identity by
default. Architecturally this is closer to Steering's shape (flat
file-per-item, `JList` not `JTable`) than MCP/Hooks' (a map/array inside one
shared file) — `AgentService`/`AgentsPanel` mirror `SteeringService`/
`SteeringPanel` closely. But the *editor dialog* mirrors `HookFormPanel`/
`HookEditDialog`'s Form+Raw-JSON-tab split instead of `SteeringEditorDialog`'s
plain-fields shape, since Agent's schema is real structured JSON (needs the
Raw JSON escape hatch), not simple YAML front matter.

**v1 scope**: Form tab covers 8 fields (`description`, `prompt`, `model`,
`keyboardShortcut`, `welcomeMessage`, `tools`, `allowedTools`,
`includeMcpJson`); the other 6 (including `name` itself) are
Raw-JSON-tab-only, round-tripped losslessly via a Jackson `@JsonAnyGetter`/
`@JsonAnySetter` catch-all on `AgentConfig` (same pattern as
`McpServerConfig.extra`) so a Form-tab-only edit never destroys them. No
`name` field in the Form tab at all — no file-per-item resource in this app
supports rename (Steering/Skills don't either).

**Identity fields vs. Jackson deserialization.** `AgentConfig` is fully
Jackson-bound (unlike `SteeringDoc`, which isn't Jackson-bound at all —
`FrontMatterParser` handles YAML manually). Its `filePath`/`workspaceRoot`
identity fields are `@JsonIgnore`d and `final`, set via a constructor that
accepts `null` for transient/scratch instances (e.g. built from the Raw
JSON tab, never persisted directly). Real loads use
`mapper.readerForUpdating(new AgentConfig(filePath, workspaceRoot))` rather
than a no-arg constructor + Jackson-injected fields, so the identity fields
stay genuinely `final` without fighting Jackson's usual instantiation path.

**Verified end-to-end**, not just unit-tested, using the same
invokeLater-not-invokeAndWait modal-dialog harness technique documented for
the MCP catalog feature (`AgentEditDialog` and `JOptionPane.showInputDialog`
for New... are both modal). A hand-crafted fixture with `mcpServers`/
`toolAliases`/`hooks` populated confirmed those fields survive a real,
Form-tab-only edit through the actual UI (not just `JsonMapperFactory`
directly) — this was the single highest-risk piece of the design, given a
near-identical class of bug (`McpServerConfig.isRemote()` missing
`@JsonIgnore`) had already slipped past unit tests once in the MCP catalog
feature.

98 tests total.

## WorkspaceScopeBar narrow-window clipping (issue #18)

Shipped in 1.1.0, caught by the user running a real local build afterward
(`WorkspaceScopeBar.java` itself was unchanged between v1.0.0 and v1.1.0 —
a latent bug, not a regression from that release's changes). At a narrow
window width, the "Scope: [combo] [Add Workspace...] [Remove Workspace]"
bar wraps to multiple rows via its `FlowLayout`, but everything past the
first row was clipped/invisible.

**Root cause, confirmed with a geometry harness before writing any fix**
(construct `WorkspaceScopeBar` at 300px width, read real `getPreferredSize()`/
`getSize()`/child `getBounds()` — the same technique documented in
`.claude/skills/verify/SKILL.md`, applied to layout geometry instead of
HTML sizing this time): `java.awt.FlowLayout.preferredLayoutSize()` always
reports a *single-row* size regardless of the container's actual width —
it has no concept of wrapping in its own preferred-size math, only in the
separate `layoutContainer()` pass. So `BorderLayout.NORTH` (which sizes its
child to `getPreferredSize().height`) under-allocates height the moment the
bar actually wraps, silently clipping every row past the first. Harness
output before the fix: `preferredSize=[width=1252,height=27]` (one row)
vs. actual wrapped content needing `height=97` (four rows) — a real,
measurable 70px shortfall, not a cosmetic one-off.

**Fix**: `util/WrapLayout.java`, a small `FlowLayout` subclass that
recomputes `preferredLayoutSize()`/`minimumLayoutSize()` by walking the
container's actual current width and simulating the same row-wrapping
`FlowLayout` does at layout time — a well-known, standard pattern for this
exact JDK limitation. `WorkspaceScopeBar` now uses it in place of plain
`FlowLayout`; same harness re-run after the fix confirms
`preferredSize().height` now matches the actual wrapped content height
(97px) and no child extends past the bar's allocated bounds. Covered by a
new `WrapLayoutTest` using fixed-size placeholder components (not real
buttons/labels) so the expected pixel math doesn't depend on font metrics.

This is the second bug in a row (after the MCP catalog's stale-config-
reference and `@JsonIgnore` gap) caught only by driving the real UI, not by
`mvn test` alone — worth treating as a pattern, not a coincidence, when
deciding how much manual/E2E verification a change needs before it ships.

101 tests total.

## Headless layout-clipping regression suite (issue #22)

Closes the gap the previous entry called out: automated coverage that
would have caught issue #18 (`WorkspaceScopeBar` clipped at a narrow
window width) before it shipped. `testsupport/LayoutAssertions.java` +
`testsupport/PanelLayoutClippingTest.java` check every `JPanel`-rooted
component `MainWindow` can show (the 6 tabs + 3 reusable dialog form
panels) for clipped children at the app's real default size (900×600) and
a deliberately narrow one (300×600, matching the diagnostic width that
originally found #18). Deliberately excludes the 11 `JDialog`/`JFrame`
classes — `Window` construction itself is what's blocked headlessly in
this project's CI, not layout computation; those stay covered only by the
manual `verify`/`verify-java-swing` process. No new CI infrastructure
needed: `pom.xml` has no Surefire include/exclude, so these are just two
more `*Test.java` files every existing `mvn package` job already picks up.

**Two real bugs caught while building this, both before either landed
anywhere near "done":**

1. `Container.validate()` doesn't reliably trigger layout in this fully
   headless setup (no peer at all, not even a dummy one) — its own
   `isValid()`/peer bookkeeping silently no-ops, leaving descendants at
   `(0,0,0,0)`. Found by writing `LayoutAssertions`' own negative-control
   self-test first (a deliberately-broken `FlowLayout` fixture that must
   throw) and watching it *not* throw — proving the utility itself was
   broken before it was ever pointed at a real component. Fixed by walking
   the tree manually, calling `doLayout()` (which does work) one container
   level at a time, confirmed against a standalone diagnostic before
   committing to the fix.
2. Once pointed at the real panels, `PanelLayoutClippingTest` immediately
   found a second, previously-unknown clipping bug: `UsagePanel`'s
   `JEditorPane` note sat directly in `BorderLayout.NORTH` with no
   `JScrollPane` — same mechanism class as #18 (a `NORTH` child's
   allocated height depends on its own `preferredSize()`, which for HTML
   content depends on the width it reflows at), but with no scrollbar
   fallback at all, so a narrow width could clip text with no way to see
   the rest. Fixed by wrapping it in a `JScrollPane` in `CENTER`, matching
   the pattern already used everywhere else in this app for exactly this
   kind of content.
3. CI then surfaced a third issue on `build-macos (macos-15-intel, x64)`
   only (not Linux, Windows, or macOS arm64): `JEditorPane.getPreferredSize()`
   threw a `NullPointerException` deep inside Swing's own HTML layout
   engine (`FlowView$FlowStrategy.layoutRow` → `CompositeView.replace`,
   "child is null") when `UsagePanel` was tested at 300px. This is a real,
   pre-existing JDK/Swing bug (font-metrics-dependent HTML flow-breaking
   at pathologically narrow effective widths — the kind of bug tracked as
   JDK-8202537), not app code, and didn't reproduce locally on Linux,
   confirming the platform-dependence rather than chasing a phantom.
   Genuinely concerning, though: this is the same effective width a real
   user could resize their actual window to. Fixed at the root rather than
   dodging the specific test width: `MainWindow` now enforces a real
   `MINIMUM_SIZE` (640×480, both via `setMinimumSize` for interactive
   resizing and by clamping restored bounds, since `setMinimumSize` alone
   doesn't constrain a stale narrow size persisted from before this fix)
   so the app can no longer reach that pathological width at all in
   production. `PanelLayoutClippingTest` tests `UsagePanel` at that new
   floor (640px) instead of the shared `NARROW_WIDTH` (300px, which the
   other 5 `WorkspaceScopeBar`-embedding panels still use, preserving full
   wrapping-forcing coverage for the #18 bug class specifically) — per-row
   width overrides the parameterized test's `(name, factory)` → `(name,
   factory, narrowWidth)` shape, rather than moving to 640 everywhere,
   which would have silently weakened #18 coverage: in a clean CI
   environment with no pinned workspaces, `WorkspaceScopeBar`'s content
   may not actually wrap at 640px, only at something as narrow as 300px.

**Verification that the suite actually would have caught #18** (not just
plausibly might have): temporarily reverted `WorkspaceScopeBar` back to
plain `FlowLayout` and confirmed exactly the 5 panels that embed it
(MCP/Steering/Skills/Hooks/Agents) failed with the identical clipping
signature ("Remove Workspace" button exceeding `WorkspaceScopeBar`'s
bounds) as the real incident, then restored `WrapLayout` and confirmed all
9 pass again.

113 tests total.

## Launch kiro-cli in an independent terminal window (issue #24)

Bridges this app's config management and kiro-cli's actual interactive
sessions without becoming a chat client: a "Launch kiro-cli..." button in
`WorkspaceScopeBar` (so it's available from every panel that embeds one)
spawns `kiro-cli` in a brand new, completely independent OS-native terminal
window, in whatever directory the current scope points to (a pinned
workspace root, or the user's home directory for Global — `~/.kiro` is a
config dir, not a place to work). The app never reads, controls, or drives
the session afterward, same spirit as the existing "Reveal File..."
buttons. An embedded terminal/PTY was considered and explicitly rejected
first — see `[[feedback_not_a_chat_client_boundary]]`-class prior
decision — this stays a pure launcher.

**Scoped to exactly three terminals in v1**, per explicit decision: macOS
Terminal.app, Windows PowerShell 7 (`pwsh.exe`, not the older
`powershell.exe`), and Ubuntu's `gnome-terminal`. No fallback chain through
other terminal emulators — documented in release notes, other terminals
are GitHub issue feature requests if wanted, not a v1 goal.

`util/KiroSessionLauncher.java` mirrors the `DesktopUtils`/`ProcessDetacher`
split established earlier: pure, parameterized, fully unit-tested
command-building logic (`detect()`, one command-array builder per OS, PATH-scan
helpers) plus a thin, side-effecting `launchSession()` wrapper that follows
`DesktopUtils`'s dialog+log error convention (nothing fails silently, since
this is always a direct button click) rather than `ProcessDetacher`'s
silent-boolean convention (appropriate there for a background relaunch,
not here).

**Two escaping layers on macOS, one each elsewhere, all real-world
verified rather than hand-trusted:**
- Linux: no escaping needed at all — `ProcessBuilder` passes argv directly
  (no shell), so the directory is one array element handed straight to
  `gnome-terminal --working-directory=`.
- Windows: PowerShell single-quote escaping (`'` → `''`), used with
  `-LiteralPath` specifically to avoid wildcard/provider expansion on `[`,
  `]`, `*` that `-Path` would trigger.
- macOS: POSIX shell single-quote escaping (`'` → `'\''`) for the inner
  `cd '<dir>' && kiro-cli`, then AppleScript double-quoted string-literal
  escaping (backslash first, then quote — order matters) of that whole
  result, since it's embedded in an `osascript -e 'do script "..."'` call.

The shell-escaping layer is verified by actually invoking a real `bash -c
"printf '%s' '<escaped>'"` in the test and comparing output to the
original string, for several adversarial inputs (spaces, `'`, `"`) — the
strongest available proof for that layer, not just a hand-derived expected
string. The PowerShell and AppleScript layers use round-trip tests
instead (encode then manually reverse, assert equality) since neither
interpreter is available in this dev environment — safer than hand-tracing
a two-layer nested escape by eye, which is exactly the kind of arithmetic
a person gets subtly wrong.

**Discovered by actually reading the installed binary, not assumed**:
Ubuntu's `/usr/bin/gnome-terminal` is a Canonical Python wrapper
(`Gio.Subprocess` + `wait_async`) that blocks until the spawned terminal
*window* closes, not just until the terminal server starts — so
`launchSession` never calls `waitFor()` on the Linux/Windows paths (fire
and forget only), and macOS is the one platform where a short bounded
`waitFor(5s)` on `osascript` itself is safe (it's a short-lived Apple-Event
dispatcher, not the terminal session).

**Verification is asymmetric across the three platforms, stated plainly
rather than overclaimed:**
- Linux: unit tests + a real manual smoke test on this dev host — clicked
  the actual button, confirmed a real `gnome-terminal` window opened and
  `kiro-cli` launched correctly in the right directory (then cleaned up
  the spawned session, since it was a live kiro-cli process using real
  resources, not something to leave running unattended).
- macOS/Windows: the pure command-building/escaping logic gets exercised
  for free on real `macos-latest`/`macos-15-intel`/`windows-latest` CI
  runners via the existing `build.yml` matrix (no separate dispatch job
  needed — `KiroSessionLauncherTest` is a normal `*Test.java` file, picked
  up by the `mvn package` every build job already runs). What CI *cannot*
  prove: whether a real, visible, focused GUI terminal window actually
  appears and behaves correctly — CI runners aren't attended interactive
  sessions. macOS's one-time Automation/Accessibility permission prompt in
  particular can only be confirmed by a real user on real hardware; that's
  expected first-run behavior, not a bug, and is called out in release notes.

**Also on the tray icon's own right-click menu** (`TrayApp.java`), not just
`WorkspaceScopeBar` — a "Launch kiro-cli..." item between "Show" and
"Exit". The tray menu has no "currently selected scope" concept the way a
panel's `WorkspaceScopeBar` does (nothing is "selected" at the tray-icon
level), so this always launches in the home directory rather than trying
to infer or remember a scope — kept deliberately simple for this first
pass rather than adding a submenu of pinned workspaces or tracking
last-used scope globally.

**Found by the user testing the built feature, not by CI**: `WorkspaceScopeBar`'s
combo box had no cap on displayed text width — a pinned workspace's label
is its full absolute path, and `JComboBox` sizes itself to its widest
rendered item, so one long path balloons the combo's (and so the whole
bar's) preferred width past the window edge, even after resizing. Fixed
with a custom `ListCellRenderer` that truncates a label longer than 40
characters to `"..." + <tail>` (keeping the more useful, distinguishing
end of the path visible rather than the common leading segments), plus a
tooltip (on both the dropdown items and the combo's own closed-box display
area, which needs its tooltip set separately — the renderer only covers
the popup) showing the full untruncated path. Covered by a
`truncateForDisplay` unit test plus a reflection-based geometry test
proving the real `JComboBox`'s `getPreferredSize().width` stays bounded
regardless of pinned-path length, mirroring the `LayoutAssertions`
technique from the issue #22 suite (construct real components, measure
real geometry, assert a bound) applied to a single component's own
intrinsic sizing rather than parent/child clipping.

129 tests total.

## Scheduled MCP catalog regeneration (issue #27, companion to issue #26)

`src/main/resources/mcp-catalog.json` was previously only regenerated by
hand, occasionally. `.github/workflows/refresh-mcp-catalog.yml` (the
second workflow file in this repo, alongside `build.yml`) runs
`tools/generate-mcp-catalog.py` weekly (Monday 13:00 UTC — start of the
work week, mid-morning across most timezones) and on `workflow_dispatch`
for on-demand runs.

**Deliberately stops short of opening a PR.** The first design used
`peter-evans/create-pull-request`, but that needs the repo-wide "Allow
GitHub Actions to create and approve pull requests" setting — confirmed
off here via `gh api repos/OurGiant/kiro-control-panel/actions/permissions/workflow`
→ `"can_approve_pull_request_reviews": false` — and left off *by choice*,
not just inertia: that single toggle also grants the unrelated, more
sensitive "approve PRs" capability (there's no narrower "create only"
option), which is exactly the kind of review-bypass surface worth not
opening just to save one manual step. Instead:
1. Pushes the diff straight to a fixed branch, `chore/refresh-mcp-catalog`
   (`git push --force` — this branch is disposable/always-regenerated, so
   force-pushing to it every run is intentional, not a hazard). Needs only
   `contents: write`, already an ordinary permission with no special gate.
2. Opens a GitHub *issue* (not a PR) linking to the branch and the compare
   view, with the exact `gh pr create` command to run — `issues: write` is
   also an ordinary permission, entirely separate from the PR-creation
   toggle. Guards against duplicate issues piling up across unattended
   weeks by checking for an already-open `catalog-refresh`-labeled issue
   first and skipping creation if one exists.

Net effect: the tedious part (scrape, diff, stage a branch) is automated;
opening and merging the actual PR stays a manual, human-initiated action
every time — not because the tooling can't do it, but because doing it
that way avoids needing the elevated permission at all, rather than
needing it and trusting a workflow not to misuse it.

**Other design choices worth recording:**
- **Write to a temp path, then `mv`, never a direct `>` redirect to the
  destination.** A naive `python3 ... > src/main/resources/mcp-catalog.json`
  truncates the destination the instant it opens, before the script's exit
  code is known — if the generator throws or hits its own "zero entries
  extracted" guard, that would already have zeroed out the previously-good
  file in the checkout before the step fails. Redirect-to-scratch-then-`mv`
  means a failing run leaves the checked-out file untouched and no
  branch-push/issue step ever runs.
- **`git diff --quiet` gate before pushing/opening an issue** — an
  explicit, greppable "no-op this week" log line when kiro.dev's list
  hasn't changed, rather than pushing an empty no-op branch every week.
- **`actions/setup-python` even though `ubuntu-latest` ships Python
  already and the script is stdlib-only.** The script uses PEP 604 union
  syntax (`dict | None`), needing 3.10+ — fine on current runners, but
  pinning explicitly matches `build.yml`'s own philosophy (it pins JDK 24
  explicitly rather than trusting whatever a runner happens to ship) and
  insulates against a future image change silently breaking this months
  from now with no code change to explain why.

Companion issue #26 (the in-app Refresh button) has no dependency on any
of this — it reads whatever's already on `main`, regardless of whether
this workflow has ever run, or whether its branch's PR has been opened
and merged yet.
