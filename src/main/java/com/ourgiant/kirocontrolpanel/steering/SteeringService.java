package com.ourgiant.kirocontrolpanel.steering;

import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.util.FrontMatterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads and writes .kiro/steering/*.md files — the exact files Kiro itself
 * reads, so changes here take effect there and vice versa.
 */
public class SteeringService {
    private static final Logger logger = LoggerFactory.getLogger(SteeringService.class);

    public List<SteeringDoc> listGlobal() {
        return list(KiroPaths.globalSteeringDir(), null);
    }

    public List<SteeringDoc> listWorkspace(Path workspaceRoot) {
        return list(KiroPaths.workspaceSteeringDir(workspaceRoot), workspaceRoot);
    }

    private List<SteeringDoc> list(Path dir, Path workspaceRoot) {
        List<SteeringDoc> docs = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return docs;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".md"))
                .sorted()
                .forEach(p -> {
                    try {
                        docs.add(load(p, workspaceRoot));
                    } catch (IOException e) {
                        logger.warn("Failed to read steering doc {}", p, e);
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to list steering directory {}", dir, e);
        }
        return docs;
    }

    public SteeringDoc load(Path filePath, Path workspaceRoot) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        FrontMatterParser.Document parsed = FrontMatterParser.parse(content);

        SteeringDoc doc = new SteeringDoc(filePath, workspaceRoot);
        Map<String, Object> fm = parsed.frontMatter();
        doc.setInclusion(stringOrDefault(fm.get("inclusion"), "always"));
        doc.setFileMatchPatterns(toStringList(fm.get("fileMatchPattern")));
        doc.setName(stringOrDefault(fm.get("name"), ""));
        doc.setDescription(stringOrDefault(fm.get("description"), ""));
        doc.setBody(parsed.body());
        return doc;
    }

    public void save(SteeringDoc doc) throws IOException {
        Path filePath = doc.getFilePath();
        Files.createDirectories(filePath.getParent());

        Map<String, Object> frontMatter = new LinkedHashMap<>();
        String inclusion = doc.getInclusion();
        boolean isPlainDefault = "always".equals(inclusion)
            && doc.getFileMatchPatterns().isEmpty()
            && doc.getName().isBlank()
            && doc.getDescription().isBlank();

        if (!isPlainDefault) {
            frontMatter.put("inclusion", inclusion);
            if ("fileMatch".equals(inclusion)) {
                List<String> patterns = doc.getFileMatchPatterns();
                frontMatter.put("fileMatchPattern", patterns.size() == 1 ? patterns.get(0) : patterns);
            } else if ("auto".equals(inclusion)) {
                frontMatter.put("name", doc.getName());
                frontMatter.put("description", doc.getDescription());
            }
        }

        String content = FrontMatterParser.serialize(frontMatter, doc.getBody());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        logger.info("Saved steering doc {}", filePath);
    }

    public void delete(SteeringDoc doc) throws IOException {
        Files.deleteIfExists(doc.getFilePath());
        logger.info("Deleted steering doc {}", doc.getFilePath());
    }

    private static String stringOrDefault(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
        } else if (value instanceof String s && !s.isBlank()) {
            result.add(s);
        }
        return result;
    }
}
