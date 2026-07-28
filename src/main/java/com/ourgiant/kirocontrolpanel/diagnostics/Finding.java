package com.ourgiant.kirocontrolpanel.diagnostics;

import com.ourgiant.kirocontrolpanel.WorkspaceScope;

import java.nio.file.Path;

/**
 * One structural problem found by {@link KiroSetupScanner} in a Kiro config
 * file. {@code fix} is null for anything that can't be safely auto-repaired
 * (most findings -- see {@link Fix}'s javadoc); such findings are still
 * reportable, just navigable ("Open File") rather than one-click-fixable.
 */
public record Finding(
    KiroSurface surface,
    WorkspaceScope scope,
    FindingSeverity severity,
    Path path,
    String message,
    Fix fix
) {
    public boolean isFixable() {
        return fix != null;
    }
}
