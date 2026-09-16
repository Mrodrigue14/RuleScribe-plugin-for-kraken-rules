#!/usr/bin/env python3
"""Static validation of the Kraken plugin (no JVM compilation)."""
import re, sys, os, glob
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
errors, warnings = [], []

# 1. Well-formed plugin.xml, and every class it references exists.
plugin_xml = os.path.join(ROOT, "src/main/resources/META-INF/plugin.xml")
tree = ET.parse(plugin_xml)
def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


kt_sources = {f: read(f) for f in glob.glob(os.path.join(ROOT, "src/main/kotlin/**/*.kt"), recursive=True)}
kt_classes = set()
for src in kt_sources.values():
    pkg = re.search(r'package\s+([\w.]+)', src).group(1)
    for m in re.finditer(r'\b(?:class|object)\s+(\w+)', src):
        kt_classes.add(pkg + "." + m.group(1))

referenced = set()
for el in tree.iter():
    for attr in ("implementationClass", "implementation", "forClass"):
        v = el.get(attr)
        if v and v.startswith("com.kraken"):
            referenced.add(v)
    if el.tag in ("className",):
        referenced.add(el.text.strip())
for c in sorted(referenced):
    if c not in kt_classes:
        errors.append(f"plugin.xml references a missing class: {c}")
print(f"[1] plugin.xml OK, {len(referenced)} referenced classes, {len(kt_classes)} Kotlin classes found")

# 2. BNF analysis.
bnf = open(os.path.join(ROOT, "src/main/bnf/Kraken.bnf"), encoding="utf-8").read()
bnf_body = re.sub(r'/\*.*?\*/', '', bnf, flags=re.S)
bnf_body = re.sub(r'//[^\n]*', '', bnf_body)

header_match = re.search(r'^\{(.*?)^\}', bnf_body, flags=re.S | re.M)
header = header_match.group(1)
tokens_block = re.search(r'tokens\s*=\s*\[(.*?)^\s*\]\s*$', header, flags=re.S | re.M).group(1)
tokens = set(re.findall(r'^\s*([A-Z_][A-Z0-9_]*)\s*=', tokens_block, flags=re.M))
body = bnf_body[header_match.end():]

rule_defs = re.findall(r'^\s*(?:private\s+)?([a-z_][a-z0-9_]*)\s*::=', body, flags=re.M)
dup = [r for r in set(rule_defs) if rule_defs.count(r) > 1]
if dup:
    errors.append(f"BNF: rules defined twice: {dup}")
rule_set = set(rule_defs)

# Rule and token references in rule bodies
body_no_attrs = re.sub(r'\{[^{}]*\}', ' ', body)  # strip attribute blocks {pin=...}
refs_lower = set(re.findall(r'\b([a-z_][a-z0-9_]*)\b', body_no_attrs)) - {'private'}
refs_upper = set(re.findall(r'\b([A-Z_][A-Z0-9_]*)\b', body_no_attrs))
for r in sorted(refs_lower - rule_set):
    errors.append(f"BNF: rule referenced but not defined: {r}")
for t in sorted(refs_upper - tokens):
    errors.append(f"BNF: token referenced but not declared: {t}")
unused_tokens = tokens - refs_upper - {'LINE_COMMENT', 'BLOCK_COMMENT', 'DOC_COMMENT'}
if unused_tokens:
    warnings.append(f"BNF: tokens declared but unused in the grammar: {sorted(unused_tokens)}")

# Direct left recursion
for m in re.finditer(r'^\s*(?:private\s+)?([a-z_][a-z0-9_]*)\s*::=\s*(.*?)(?=^\s*(?:private\s+)?[a-z_][a-z0-9_]*\s*::=|\Z)', body, flags=re.M | re.S):
    name, rhs = m.group(1), m.group(2)
    for alt in re.split(r'(?<![|])\|', rhs):
        first = re.match(r'\s*([a-z_][a-z0-9_]*)', alt)
        if first and first.group(1) == name:
            errors.append(f"BNF: direct left recursion in {name}")
print(f"[2] BNF: {len(tokens)} tokens, {len(rule_set)} rules")

# 3. KrakenTypes.* consistency, Kotlin against the BNF.
generated_consts = tokens | {r.upper() for r in rule_set}
test_sources = [read(f) for f in glob.glob(os.path.join(ROOT, "src/test/kotlin/**/*.kt"), recursive=True)]
kt_all = "\n".join(list(kt_sources.values()) + test_sources)
used_consts = set(re.findall(r'KrakenTypes\.([A-Z_][A-Z0-9_]*)', kt_all))
for c in sorted(used_consts - generated_consts):
    errors.append(f"Kotlin uses KrakenTypes.{c}, which Grammar-Kit will not generate")
print(f"[3] KrakenTypes: {len(used_consts)} constants used in Kotlin, all checked")

# 4. The Kotlin lexer covers every BNF token.
lexer_src = read(os.path.join(ROOT, "src/main/kotlin/com/kraken/plugin/parser/KrakenLexer.kt"))
lexer_tokens = set(re.findall(r'KrakenTypes\.([A-Z_][A-Z0-9_]*)', lexer_src))
missing = tokens - lexer_tokens
if missing:
    errors.append(f"BNF tokens the lexer never produces: {sorted(missing)}")
print(f"[4] Lexer: produces {len(lexer_tokens)} token types")

# 5. Every inspection has its HTML description.
# Without the file the IDE shows the inspection with no explanation and reports
# nothing, so the failure is silent. The file name must match the shortName exactly.
descriptions = os.path.join(ROOT, "src/main/resources/inspectionDescriptions")
short_names = [
    el.get("shortName")
    for el in tree.iter("localInspection")
    if el.get("shortName")
]
for short in sorted(short_names):
    if not os.path.exists(os.path.join(descriptions, short + ".html")):
        errors.append(f"inspection without a description: inspectionDescriptions/{short}.html")
print(f"[5] Inspections: {len(short_names)} declared, descriptions checked")

# 6. Balanced braces in .kt files.
for f, src in kt_sources.items():
    src2 = re.sub(r'"""', '@@@', src)
    src2 = re.sub(r'@@@.*?@@@', '""', src2, flags=re.S)
    src2 = re.sub(r"'([^'\\]|\\.)'", "''", src2)   # chars BEFORE strings (otherwise '"' breaks the matching)
    src2 = re.sub(r'"([^"\\]|\\.)*"', '""', src2)
    src2 = re.sub(r'//[^\n]*', '', src2)
    src2 = re.sub(r'/\*.*?\*/', '', src2, flags=re.S)
    if src2.count('{') != src2.count('}'):
        errors.append(f"Unbalanced braces: {f} ({src2.count('{')} vs {src2.count('}')})")
print(f"[6] Braces: {len(kt_sources)} Kotlin files checked")

print()
for w in warnings:
    print("WARN:", w)
for e in errors:
    print("ERROR:", e)
print()
print("RESULT:", "FAILED" if errors else "OK")
sys.exit(1 if errors else 0)
