# tools/

Maintenance scripts that aren't part of the Maven build or CI — run by hand,
occasionally, by a developer.

## generate-mcp-catalog.py

Regenerates `src/main/resources/mcp-catalog.json`, the bundled data behind
the MCP tab's "Browse Catalog..." feature, from
[kiro.dev's known MCP server list](https://kiro.dev/docs/mcp/servers/).

Re-run it whenever that page's list changes (new servers added, existing
configs updated) — there's no live fetch at app runtime, the catalog is a
static resource shipped with the app.

```
python3 tools/generate-mcp-catalog.py > src/main/resources/mcp-catalog.json
git diff src/main/resources/mcp-catalog.json
```

Always review the diff before committing — this is a scrape of a page
kiro.dev controls, not an API contract; a redesign there could change what
gets extracted (or silently extract nothing, which the script detects and
exits non-zero for, but a *partial* structure change might not).

No dependencies beyond Python 3's standard library.
