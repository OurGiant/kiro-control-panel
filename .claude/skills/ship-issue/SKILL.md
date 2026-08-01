---
name: ship-issue
description: The project-specific extras for shipping a bug fix or feature to Kiro Control Panel, on top of the generic `java-swing-ship-issue` workflow — file a GitHub issue, branch off main, implement, verify, bump the patch version, and open a PR. Use whenever picking up a bug fix or feature for this repo.
---

# Shipping a change to Kiro Control Panel

This is the project-specific companion to the generic `java-swing-ship-issue`
skill — read that one for the full workflow (file → branch → implement →
verify → bump the patch version → PR) and the reasoning behind each step.
This file only covers what's actually specific to this repo.

## Build container

Maven runs in a Docker container, not on the host. This repo's `Dockerfile`
defines that environment (`docker build -t kiro-cp-maven .`) — build your
own rather than depending on a hand-set-up one. If you're working against
an existing long-lived container instead, its name can drift across
sessions; confirm with `docker ps -a --format '{{.Names}} {{.Status}}
{{.Image}}'` if a known name stops working.

```bash
docker exec <container> bash -c "cd /projects/kiro-control-panel && mvn -q test"
docker exec <container> bash -c "cd /projects/kiro-control-panel && mvn -q package -DskipTests"
```

## Update README for user-facing changes

If the change adds or materially changes a feature a user would notice (a
new tab, a new menu action, a new panel behavior), add or update its entry
in README.md's Features section, matching the style of the existing
entries there — this repo's convention is that README stays a complete,
current feature list, not just SPEC.md. Skip this for pure bug fixes,
internal refactors, or anything not user-visible.

`main` is a protected branch requiring PRs and passing status checks — even
a README-only change needs its own branch and PR, it can't be pushed to
`main` directly (confirmed the hard way on issue #94's follow-up: a direct
push to `main` was rejected with "Changes must be made through a pull
request").

## Everything else

See `java-swing-ship-issue` for: filing the issue, branch naming, testing/
verifying (this repo's own `verify` skill covers the dev-setup specifics),
the patch-version-bump convention, and the PR/CI checklist.
