package com.ourgiant.kirocontrolpanel.steering;

import java.util.Map;

/** Starter bodies for Kiro's three foundational steering docs, keyed by exact file name. */
final class SteeringTemplates {

    private static final Map<String, String> TEMPLATES = Map.of(
        "product.md", """
            # Product

            What this project is, who it's for, and what problem it solves.

            ## Users

            ## Objectives
            """,
        "tech.md", """
            # Tech Stack

            Frameworks, libraries, and tools this project uses, plus any
            technical constraints Kiro should follow when suggesting code.

            ## Stack

            ## Constraints
            """,
        "structure.md", """
            # Project Structure

            File organization, naming conventions, and import patterns so
            generated code fits the existing codebase.

            ## Layout

            ## Conventions
            """
    );

    private SteeringTemplates() {
    }

    /** @return the starter body for {@code fileName}, or an empty string if it's not one of the foundational docs. */
    static String bodyFor(String fileName) {
        return TEMPLATES.getOrDefault(fileName, "");
    }
}
