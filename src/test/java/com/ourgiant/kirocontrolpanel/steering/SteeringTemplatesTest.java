package com.ourgiant.kirocontrolpanel.steering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteeringTemplatesTest {

    @Test
    void knownFileNamesReturnNonEmptyTemplates() {
        assertTrue(SteeringTemplates.bodyFor("product.md").contains("# Product"));
        assertTrue(SteeringTemplates.bodyFor("tech.md").contains("# Tech Stack"));
        assertTrue(SteeringTemplates.bodyFor("structure.md").contains("# Project Structure"));
    }

    @Test
    void unknownFileNameReturnsEmptyString() {
        assertEquals("", SteeringTemplates.bodyFor("api-conventions.md"));
    }
}
