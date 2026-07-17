package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontMatterParserTest {

    @Test
    void parsesFrontMatterAndBody() {
        String content = """
            ---
            inclusion: fileMatch
            fileMatchPattern: "components/**/*.tsx"
            ---
            # Body content
            More text.
            """;

        FrontMatterParser.Document doc = FrontMatterParser.parse(content);

        assertEquals("fileMatch", doc.frontMatter().get("inclusion"));
        assertEquals("components/**/*.tsx", doc.frontMatter().get("fileMatchPattern"));
        assertTrue(doc.body().startsWith("# Body content"));
    }

    @Test
    void treatsContentWithoutFrontMatterAsPlainBody() {
        String content = "# Just a heading\nNo front matter here.\n";

        FrontMatterParser.Document doc = FrontMatterParser.parse(content);

        assertTrue(doc.frontMatter().isEmpty());
        assertEquals(content, doc.body());
    }

    @Test
    void parsesListValuedFrontMatterField() {
        String content = """
            ---
            inclusion: fileMatch
            fileMatchPattern:
              - "**/*.ts"
              - "**/*.tsx"
            ---
            Body.
            """;

        FrontMatterParser.Document doc = FrontMatterParser.parse(content);

        assertEquals(List.of("**/*.ts", "**/*.tsx"), doc.frontMatter().get("fileMatchPattern"));
    }

    @Test
    void serializeThenParseRoundTrips() {
        Map<String, Object> frontMatter = new LinkedHashMap<>();
        frontMatter.put("inclusion", "auto");
        frontMatter.put("name", "api-conventions");
        frontMatter.put("description", "REST API conventions for this service");

        String serialized = FrontMatterParser.serialize(frontMatter, "# API Conventions\n\nUse nouns, not verbs.\n");
        FrontMatterParser.Document reparsed = FrontMatterParser.parse(serialized);

        assertEquals("auto", reparsed.frontMatter().get("inclusion"));
        assertEquals("api-conventions", reparsed.frontMatter().get("name"));
        assertEquals("REST API conventions for this service", reparsed.frontMatter().get("description"));
        assertEquals("# API Conventions\n\nUse nouns, not verbs.\n", reparsed.body());
    }

    @Test
    void omitsFrontMatterBlockWhenEmpty() {
        String serialized = FrontMatterParser.serialize(new LinkedHashMap<>(), "Just body text.\n");

        assertEquals("Just body text.\n", serialized);
    }
}
