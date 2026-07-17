package com.ourgiant.kirocontrolpanel.util;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared Jackson setup for Kiro's JSON config files (mcp.json, hooks/*.json):
 * tolerant of unknown fields on read, 2-space-indented objects and arrays on
 * write, so the output stays close to what Kiro itself would write.
 */
public final class JsonMapperFactory {

    private JsonMapperFactory() {
    }

    public static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    public static PrettyPrinter createPrettyPrinter() {
        DefaultIndenter indenter = new DefaultIndenter("  ", DefaultIndenter.SYS_LF);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }
}
