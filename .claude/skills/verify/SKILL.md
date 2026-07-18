---
name: verify
description: How to build and launch Kiro Control Panel to verify a Swing UI change on this specific dev setup — where to build, where to run, why screenshots don't work here, and the JEditorPane sizing-measurement gotcha. Use before trusting the generic verify-java-swing skill's screenshot step; read that skill too for the underlying techniques (modal-dialog deadlock, synthetic MouseEvent dispatch, process safety).
---

# Verifying Kiro Control Panel

This is the project-specific companion to the generic `verify-java-swing`
skill — read that one for the underlying techniques (modal-dialog
`invokeAndWait` deadlock, synthetic `MouseEvent` dispatch, process safety).
This file is what to actually type on *this* machine.

## Build here, run there

Maven only exists in the `festive_bardeen` docker container, not on the
host — but the host has Java (Corretto) and is where the real desktop
session lives, so build in the container and run on the host:

```bash
docker exec -w /projects/kiro-control-panel festive_bardeen mvn -B package -DskipTests
# /projects is bind-mounted from the host's projects directory, so the jar lands at:
java -jar target/kiro-control-panel-all.jar   # run this on the HOST, not in the container
```

The container itself is fully headless (no `DISPLAY` at all) — don't try to
verify a GUI change by running the jar inside it, it'll die at `JFrame`
construction with a `HeadlessException`. That's a different failure mode
than the host's screenshot problem below; don't confuse the two.

Main class: `com.ourgiant.kirocontrolpanel.TrayApp`.

## Screenshots don't work here — skip straight to reflection

The host desktop is COSMIC on Wayland (`DISPLAY=:1` via XWayland).
`java.awt.Robot.createScreenCapture(...)` reliably returns solid black for
this app's windows. Confirmed dead ends, already explored — don't re-try
these:

- `cosmic-screenshot --interactive=false` (the CLI screenshot tool) —
  captures the visible workspace, but new AWT/Swing windows land on a
  *different* COSMIC workspace than whatever's currently in view, so it
  misses them even though `xwininfo`/`_NET_ACTIVE_WINDOW` confirm the
  window genuinely exists and is even focused.
- `xdotool`/`wmctrl` would fix the above (bring the window's workspace into
  view) but aren't installed, and there's no passwordless `sudo` to install
  them non-interactively.
- D-Bus workspace-switch API — `cosmic-comp`/`CosmicWorkspaces` don't expose
  one (`busctl --user introspect com.system76.CosmicWorkspaces /` returns
  nothing but the generic DBus/Properties interfaces).
- AT-SPI accessibility bus — Java's Swing/AWT doesn't register with it
  without extra JVM flags to enable the bridge, so it can't see this app's
  windows either.

Go straight to the generic skill's reflection-based fallback: launch via a
small harness, drive the app with real component APIs
(`button.doClick()`, `dispatchEvent(...)`), and read state back via
`getText()`/component geometry instead of pixels.

## First-run dialog needs its flag cleared to reappear

`FirstRunDialog` only shows when `AppPreferences.isFirstRunComplete()` is
false. It's backed by `java.util.prefs`, stored on disk at
`~/.java/.userPrefs/com/ourgiant/kirocontrolpanel/prefs.xml`. To see it
again:

```bash
find ~/.java/.userPrefs -ipath '*kirocontrolpanel*' -exec rm -rf {} +
```

## `SystemTray.isSupported()` is false here — expected, not a bug

COSMIC doesn't implement the XEmbed systray protocol AWT relies on, so the
app logs "System tray is not supported on this platform" and falls back to
`EXIT_ON_CLOSE`. Don't chase this as a regression.

## JEditorPane/HTML sizing verification: measure the real laid-out size

If you're checking whether HTML content in a `JEditorPane` fits its
`JScrollPane` (the kind of bug `FirstRunDialog` had), two wrong ways to
measure it that look plausible but aren't:

- `editorPane.getPreferredSize()` with no width constraint reports the
  **unwrapped** natural size (e.g. 1416px wide for a few short paragraphs)
  — not what actually renders, meaningless for a "does it fit" check.
- `editorPane.setSize(viewportWidth, Short.MAX_VALUE)` followed by
  `getPreferredSize()` — the `setSize()` call alone does *not* trigger the
  HTML view to relayout; you'll still get the stale unwrapped size back.

What actually works: read the component's **real, already-laid-out size**
(`view.getSize()`) *after* the dialog has been packed and shown. Then
watch for this quirk: `JTextComponent.getScrollableTracksViewportHeight()`
stretches the view to exactly fill the viewport's height whenever the
content is shorter than the available space — so seeing
`actualLaidOutSize == viewportSize` with the scrollbar hidden is the
*correct* "content fits" signal, not a measurement artifact where you're
just reading the viewport size back at yourself. If that ever looks
suspicious, cross-check with a control test: build a throwaway dialog with
the same HTML and a deliberately too-small viewport (e.g. 500x50) and
confirm the harness reports overflow — if it does, the technique is sound
and the real reading can be trusted.

Minimal harness shape (full example from the `FirstRunDialog` sizing fix
verification — adapt the class/content):

```java
// Modal dialogs: invokeLater, NOT invokeAndWait (see verify-java-swing's deadlock note)
SwingUtilities.invokeLater(() -> new FirstRunDialog(parent).setVisible(true));

FirstRunDialog dlg = null;
for (int i = 0; i < 50 && dlg == null; i++) {
    for (Window w : Window.getWindows()) {
        if (w instanceof FirstRunDialog frd && frd.isVisible()) { dlg = frd; break; }
    }
    if (dlg == null) Thread.sleep(100);
}

JScrollPane scrollPane = /* find it in dlg.getContentPane().getComponents() */;
JViewport vp = scrollPane.getViewport();
Dimension viewportSize = vp.getExtentSize();
Dimension actualLaidOutSize = vp.getView().getSize();   // NOT getPreferredSize()
boolean scrollbarNeeded = actualLaidOutSize.height > viewportSize.height;
```

Compile against the shaded jar and run on the host:

```bash
javac -cp target/kiro-control-panel-all.jar -d <scratch-dir> Harness.java
java -cp "target/kiro-control-panel-all.jar:<scratch-dir>" Harness
```
