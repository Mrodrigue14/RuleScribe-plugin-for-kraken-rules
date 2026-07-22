#!/usr/bin/env python3
"""Extrait les notes de version d'une version donnée depuis le bloc
<change-notes> de plugin.xml, et les convertit en Markdown pour une
GitHub Release.

Usage: extract-notes.py <version>   (ex. extract-notes.py 0.5.7)
Écrit le Markdown sur stdout. Si la version n'est pas trouvée, sort en
erreur (le workflow retombera alors sur des notes auto-générées).
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
        print("change-notes introuvable dans plugin.xml", file=sys.stderr)
        return 1
    notes = m.group(1)

    # Bloc de CETTE version : de <b>version</b> jusqu'au prochain <b> (ou la fin).
    block_re = re.compile(
        r"<b>\s*" + re.escape(version) + r"\s*</b>(.*?)(?=<b>|\Z)", re.S
    )
    bm = block_re.search(notes)
    if not bm:
        print(f"aucun bloc de notes pour la version {version}", file=sys.stderr)
        return 1
    block = bm.group(1)

    # <li> -> puce Markdown, puis on retire les balises restantes.
    block = re.sub(r"<li>\s*", "\n- ", block)
    block = re.sub(r"</li>", "", block)
    block = re.sub(r"<[^>]+>", "", block)

    # Décoder quelques entités HTML courantes.
    for ent, ch in (("&lt;", "<"), ("&gt;", ">"), ("&amp;", "&"), ("&quot;", '"')):
        block = block.replace(ent, ch)

    # Nettoyage des espaces.
    block = "\n".join(re.sub(r"[ \t]+", " ", ln).strip() for ln in block.splitlines())
    block = re.sub(r"\n{3,}", "\n\n", block).strip()

    print(block)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
