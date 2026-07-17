package com.ourgiant.kirocontrolpanel.steering;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteeringServiceTest {

    @TempDir
    Path workspaceRoot;

    private final SteeringService service = new SteeringService();

    private Path steeringDir;

    @BeforeEach
    void setUp() throws IOException {
        steeringDir = workspaceRoot.resolve(".kiro").resolve("steering");
        Files.createDirectories(steeringDir);
    }

    @Test
    void savedDefaultInclusionDocHasNoFrontMatterBlock() throws IOException {
        SteeringDoc doc = new SteeringDoc(steeringDir.resolve("tech.md"), workspaceRoot);
        doc.setBody("# Tech Stack\n\nJava 24, Maven.\n");

        service.save(doc);

        String written = Files.readString(steeringDir.resolve("tech.md"));
        assertFalse(written.startsWith("---"), "plain 'always' doc with no other fields should skip front matter");
        assertEquals("# Tech Stack\n\nJava 24, Maven.\n", written);
    }

    @Test
    void savedFileMatchDocRoundTripsThroughLoad() throws IOException {
        SteeringDoc doc = new SteeringDoc(steeringDir.resolve("react-conventions.md"), workspaceRoot);
        doc.setInclusion("fileMatch");
        doc.setFileMatchPatterns(List.of("components/**/*.tsx", "**/*.jsx"));
        doc.setBody("# React Conventions\n");

        service.save(doc);
        SteeringDoc reloaded = service.load(steeringDir.resolve("react-conventions.md"), workspaceRoot);

        assertEquals("fileMatch", reloaded.getInclusion());
        assertEquals(List.of("components/**/*.tsx", "**/*.jsx"), reloaded.getFileMatchPatterns());
        assertEquals("# React Conventions\n", reloaded.getBody());
    }

    @Test
    void savedAutoInclusionDocRoundTripsNameAndDescription() throws IOException {
        SteeringDoc doc = new SteeringDoc(steeringDir.resolve("api-conventions.md"), workspaceRoot);
        doc.setInclusion("auto");
        doc.setName("api-conventions");
        doc.setDescription("REST API conventions for this service");
        doc.setBody("# API Conventions\n");

        service.save(doc);
        SteeringDoc reloaded = service.load(steeringDir.resolve("api-conventions.md"), workspaceRoot);

        assertEquals("auto", reloaded.getInclusion());
        assertEquals("api-conventions", reloaded.getName());
        assertEquals("REST API conventions for this service", reloaded.getDescription());
    }

    @Test
    void listWorkspaceReturnsAllMarkdownFilesSorted() throws IOException {
        Files.writeString(steeringDir.resolve("tech.md"), "# Tech\n");
        Files.writeString(steeringDir.resolve("product.md"), "# Product\n");
        Files.writeString(steeringDir.resolve("structure.md"), "# Structure\n");
        Files.writeString(steeringDir.resolve("ignored.txt"), "not markdown");

        List<SteeringDoc> docs = service.listWorkspace(workspaceRoot);

        assertEquals(3, docs.size());
        assertEquals(List.of("product.md", "structure.md", "tech.md"),
            docs.stream().map(SteeringDoc::getFileName).toList());
    }

    @Test
    void deleteRemovesFileFromDisk() throws IOException {
        SteeringDoc doc = new SteeringDoc(steeringDir.resolve("tech.md"), workspaceRoot);
        doc.setBody("# Tech\n");
        service.save(doc);
        assertTrue(Files.exists(doc.getFilePath()));

        service.delete(doc);

        assertFalse(Files.exists(doc.getFilePath()));
    }

    @Test
    void listWorkspaceReturnsEmptyWhenSteeringDirMissing() throws IOException {
        Files.delete(steeringDir);

        List<SteeringDoc> docs = service.listWorkspace(workspaceRoot);

        assertTrue(docs.isEmpty());
    }
}
