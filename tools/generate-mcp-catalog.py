#!/usr/bin/env python3
"""Regenerates src/main/resources/mcp-catalog.json from kiro.dev's known
MCP server list (https://kiro.dev/docs/mcp/servers/).

That page server-renders a single <table> (Name / Install / Description
columns); the "Add to Kiro" button in the Install column is a deep link
of the form /launch/mcp/add/?name=<slug>&config=<url-encoded-json>, where
the config param is already a real McpServerConfig-shaped JSON blob. This
script extracts every row, decodes the deep link, and emits a JSON array
matching McpCatalogEntry's fields (slug, name, description, requirement,
config) sorted by name.

Usage:
    python3 tools/generate-mcp-catalog.py > src/main/resources/mcp-catalog.json
    git diff src/main/resources/mcp-catalog.json   # review before committing

See tools/README.md for when/why to re-run this.
"""
import html
import json
import re
import sys
import urllib.request
from urllib.parse import parse_qs, urlparse

SOURCE_URL = "https://kiro.dev/docs/mcp/servers/"
ADD_LINK_PREFIX = "/launch/mcp/add/"

TAG_RE = re.compile(r"<[^>]+>")
TD_RE = re.compile(r"<td>(.*?)</td>", re.DOTALL)
TR_RE = re.compile(r"<tr>(.*?)</tr>", re.DOTALL)
STRONG_RE = re.compile(r"<strong>(.*?)</strong>", re.DOTALL)
HREF_RE = re.compile(r'href="(' + re.escape(ADD_LINK_PREFIX) + r'\?[^"]*)"')
REQUIRES_RE = re.compile(r"^(.*?)\s*(Requires\s+.+?)\.?\s*$", re.DOTALL)


def fetch(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8")


def strip_tags(cell_html: str) -> str:
    text = TAG_RE.sub("", cell_html)
    return html.unescape(text).strip()


def find_servers_table(page_html: str) -> str:
    marker = "<th>Name</th><th>Install</th><th>Description</th>"
    header_idx = page_html.find(marker)
    if header_idx == -1:
        raise ValueError("Could not locate the MCP servers table header on the page")
    table_start = page_html.rfind("<table", 0, header_idx)
    table_end = page_html.find("</table>", header_idx) + len("</table>")
    return page_html[table_start:table_end]


def split_description(text: str) -> tuple[str, str | None]:
    match = REQUIRES_RE.match(text)
    if not match:
        return text, None
    description = match.group(1).strip().rstrip(".")
    requirement = match.group(2).strip()
    return description, requirement


def parse_entry(row_html: str) -> dict | None:
    href_match = HREF_RE.search(row_html)
    if href_match is None:
        return None

    cells = TD_RE.findall(row_html)
    if len(cells) < 3:
        return None

    strong_match = STRONG_RE.search(cells[0])
    name = strip_tags(strong_match.group(1) if strong_match else cells[0])

    href = html.unescape(href_match.group(1))
    query = parse_qs(urlparse(href).query)
    slug = query.get("name", [None])[0]
    config_raw = query.get("config", [None])[0]
    if slug is None or config_raw is None:
        return None
    config = json.loads(config_raw)

    description, requirement = split_description(strip_tags(cells[2]))

    return {
        "slug": slug,
        "name": name,
        "description": description,
        "requirement": requirement,
        "config": config,
    }


def main() -> None:
    page_html = fetch(SOURCE_URL)
    table_html = find_servers_table(page_html)

    entries = []
    seen_slugs = set()
    for row_html in TR_RE.findall(table_html):
        entry = parse_entry(row_html)
        if entry is None or entry["slug"] in seen_slugs:
            continue
        seen_slugs.add(entry["slug"])
        entries.append(entry)

    if not entries:
        print("No catalog entries extracted -- page structure may have changed", file=sys.stderr)
        sys.exit(1)

    entries.sort(key=lambda e: e["name"].lower())
    json.dump(entries, sys.stdout, indent=2, ensure_ascii=False)
    sys.stdout.write("\n")
    print(f"Extracted {len(entries)} entries", file=sys.stderr)


if __name__ == "__main__":
    main()
