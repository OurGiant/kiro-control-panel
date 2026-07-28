package com.ourgiant.kirocontrolpanel.diagnostics;

import java.io.IOException;

/**
 * A mechanical, content-preserving repair for a {@link Finding} -- only
 * ever offered when the fix is unambiguous from the file's existing
 * content (e.g. moving a file, wrapping an array). Never invents content
 * (names, patterns, descriptions), since that would be a judgment call on
 * the user's real config, not a structural repair.
 */
public interface Fix {
    /** Human-readable description of what {@link #apply()} will do, shown for confirmation before running it. */
    String previewText();

    void apply() throws IOException;
}
