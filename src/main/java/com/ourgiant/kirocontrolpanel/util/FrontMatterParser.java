package com.ourgiant.kirocontrolpanel.util;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses/serializes the YAML-front-matter-plus-Markdown-body format Kiro
 * uses for steering docs and SKILL.md files: a YAML block delimited by
 * "---" lines at the very start of the file, followed by Markdown content.
 */
public final class FrontMatterParser {

    private static final String DELIMITER = "---";

    private FrontMatterParser() {
    }

    public record Document(Map<String, Object> frontMatter, String body) {
    }

    public static Document parse(String content) {
        if (content == null) {
            return new Document(new LinkedHashMap<>(), "");
        }
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith(DELIMITER + "\n")) {
            return new Document(new LinkedHashMap<>(), content);
        }

        int closingLineStart = normalized.indexOf("\n" + DELIMITER, DELIMITER.length());
        if (closingLineStart < 0) {
            return new Document(new LinkedHashMap<>(), content);
        }

        String yamlBlock = normalized.substring(DELIMITER.length() + 1, closingLineStart);
        String body = normalized.substring(closingLineStart + 1 + DELIMITER.length());
        if (body.startsWith("\n")) {
            body = body.substring(1);
        }

        // Explicit SafeConstructor rather than the bare Yaml() default: front matter
        // can come from a pinned workspace's files, so this must stay restricted to
        // plain data (maps/lists/scalars) even if a future dependency change or edit
        // elsewhere ever alters what the library's default constructor allows.
        Yaml safeYaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object loaded = safeYaml.load(yamlBlock);
        Map<String, Object> frontMatter = new LinkedHashMap<>();
        if (loaded instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                frontMatter.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return new Document(frontMatter, body);
    }

    public static String serialize(Map<String, Object> frontMatter, String body) {
        StringBuilder sb = new StringBuilder();
        if (frontMatter != null && !frontMatter.isEmpty()) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            sb.append(DELIMITER).append('\n');
            sb.append(new Yaml(options).dump(frontMatter));
            sb.append(DELIMITER).append('\n');
        }
        sb.append(body == null ? "" : body);
        return sb.toString();
    }
}
