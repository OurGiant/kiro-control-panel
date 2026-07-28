package com.ourgiant.kirocontrolpanel.diagnostics;

/**
 * How wrong a {@link Finding} is. {@code INVALID} means the file failed to
 * parse at all (bad JSON / bad YAML front matter) -- never auto-repaired,
 * since there's no way to guess intended content from broken syntax.
 * {@code STRUCTURAL} means the file parses fine but doesn't match the shape
 * Kiro expects (e.g. a skill file outside its own subfolder) -- sometimes
 * mechanically auto-fixable.
 */
public enum FindingSeverity {
    INVALID,
    STRUCTURAL
}
