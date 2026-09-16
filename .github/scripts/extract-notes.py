#!/usr/bin/env python3
"""Extracts the release notes of a given version from the <change-notes> block of
plugin.xml and converts them to Markdown for a GitHub Release.

Usage: extract-notes.py <version>   (e.g. extract-notes.py 0.5.7)
Writes the Markdown to stdout. Exits with an error if the version is not found
(the workflow then falls back to auto-generated notes).
"""
import re
import sys

PLUGIN_XML = "src/main/resources/META-INF/plugin.xml"


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: extract-notes.py <version>", file=sys.stderr)
        return 2
    version = sys.argv[1].strip()

    xml = open(PLUGIN_XML, encoding="utf-8").read()
    m = re.search(r"<change-notes><!\[CDATA\[(.*?)\]\]></change-notes>", xml, re.S)
    if not m:
        print("change-notes not found in plugin.xml", file=sys.stderr)
        return 1
    notes = m.group(1)

    # Block of THIS version: from <b>version</b> to the next <b> (or the end).
    block_re = re.compile(
        r"<b>\s*" + re.escape(version) + r"\s*</b>(.*?)(?=<b>|\Z)", re.S
    )
    bm = block_re.search(notes)
    if not bm:
        print(f"no notes block for version {version}", file=sys.stderr)
        return 1
    block = bm.group(1)

    # <li> -> Markdown bullet, then strip the remaining tags.
    block = re.sub(r"<li>\s*", "\n- ", block)
    block = re.sub(r"</li>", "", block)
    block = re.sub(r"<[^>]+>", "", block)

    # Decode a few common HTML entities.
    for ent, ch in (("&lt;", "<"), ("&gt;", ">"), ("&amp;", "&"), ("&quot;", '"')):
        block = block.replace(ent, ch)

    # Whitespace cleanup.
    block = "\n".join(re.sub(r"[ \t]+", " ", ln).strip() for ln in block.splitlines())
    block = re.sub(r"\n{3,}", "\n\n", block).strip()

    print(block)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
