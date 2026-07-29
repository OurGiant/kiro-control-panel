---
name: ship-issue
description: The standard workflow for shipping a bug fix or feature to Kiro Control Panel — file a GitHub issue, branch off main, implement, verify, bump the patch version, and open a PR. Use whenever picking up a bug fix or feature for this repo. Covers where Maven/tests run vs where the app runs, the per-issue patch-version-bump convention, and the PR checklist.
---

# Shipping a change to Kiro Control Panel

The workflow this repo has followed for every issue: file → branch → implement →
verify → **bump the patch version** → PR. The version-bump step is the one easy
to forget since nothing enforces it — it's a convention, not a build check.

## 1. File the issue

`gh issue create --title "..." --body "..."` — describe the bug/feature and,
for a bug, the root cause if already known. This becomes the PR's `fixes #N`
reference later.

## 2. Branch off up-to-date main

```bash
git checkout main && git pull --ff-only
git checkout -b fix/short-description   # or feature/short-description
```

If another PR merged since you last synced, `git pull --ff-only` catches
that before you branch — don't skip it.

## 3. Implement

Normal edits. Check `git status` before anything destructive per the usual
git safety rules.

## 4. Test and build

Maven only exists in the `festive_bardeen` Docker container (`/projects` is
bind-mounted from this host's projects directory), not on the host:

```bash
docker exec festive_bardeen bash -c "cd /projects/kiro-control-panel && mvn -q test"
docker exec festive_bardeen bash -c "cd /projects/kiro-control-panel && mvn -q package -DskipTests"
```

If the container isn't running (`is not running` error), `docker start
festive_bardeen` first.

## 5. Verify Swing UI changes for real, not just via tests

If the change touches a window/dialog/menu, don't stop at `mvn test` —
launch the built jar and drive the real UI. See this repo's own `verify`
skill for the specifics of this dev setup (build-in-container-run-on-host,
why screenshots don't work here, the JEditorPane sizing gotcha), and the
more general `verify-java-swing` skill for the underlying techniques (modal-
dialog `invokeAndWait` deadlock, synthetic `MouseEvent` dispatch, process
safety on a shared display).

## 6. Update README for user-facing changes

If the change adds or materially changes a feature a user would notice
(a new tab, a new menu action, a new panel behavior), add or update its
entry in README.md's Features section, matching the style of the
existing entries there — this repo's convention is that README stays a
complete, current feature list, not just SPEC.md. Skip this for pure bug
fixes, internal refactors, or anything not user-visible.

`main` is a protected branch requiring PRs and passing status checks —
even a README-only change needs its own branch and PR, it can't be
pushed to `main` directly (confirmed the hard way on issue #94's
follow-up: a direct push to `main` was rejected with "Changes must be
made through a pull request").

## 7. Bump the patch version

**Every issue-fixing PR bumps `pom.xml`'s `<version>` patch component by
one** — `M.m.X` → `M.m.(X+1)`, e.g. `1.2.1` → `1.2.2`. This lands in the
same PR as the fix/feature, not as a separate release PR.

```xml
<!-- pom.xml -->
<version>1.2.2</version>
```

`pom.xml` is the only file to touch — confirmed via
`grep -rn "1\.2\.1" --include="*.xml" --include="*.yml" --include="*.java"`:
the app's displayed version (`AboutDialog`, via `AppVersion.resolve()`)
reads it from the jar manifest at runtime, which Maven populates from
`pom.xml` at build time. There's no second place to edit, and no other
file hardcodes the current version (a version string appearing in a test
like `UpdateCheckerTest` is just an arbitrary fixture for comparison logic,
unrelated to the real app version — don't "fix" it to match).

**Skip this step** for pure housekeeping that isn't shipping a user-facing
change (e.g. branch cleanup, a memory/doc-only update, CI config tweaks
with no issue behind them).

**This is patch-only.** Bumping the minor or major version (`M.m` itself,
or resetting the patch number back to 0 for a real numbered release) is a
separate, deliberate decision the user makes when actually cutting a
release — not something this per-issue convention decides on its own.

## 8. Commit, push, open the PR

Commit message references the issue (`fixes #N` or `(fixes #N)` in the
subject). Push, then:

```bash
gh pr create --title "..." --body "$(cat <<'EOF'
## Summary
- Fixes #N: ...

## Test plan
- [x] ...
EOF
)"
```

## 9. Watch CI, then stop and wait

```bash
gh pr checks <N> --watch --interval 30
```

This is a real network call that can take a few minutes — run it via the
Bash tool's `run_in_background`, don't poll it manually. Report the PR as
ready once green. **Never merge without the user explicitly saying so for
that specific PR** — reporting "ready to merge" is not the same as
authorization to merge it.
