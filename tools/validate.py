#!/usr/bin/env python3
"""Validation statique du plugin Kraken (sans compilation JVM)."""
import re, sys, os, glob
import xml.etree.ElementTree as ET

import os
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
errors, warnings = [], []

# ---------- 1. plugin.xml bien formé + classes existantes ----------
plugin_xml = os.path.join(ROOT, "src/main/resources/META-INF/plugin.xml")
tree = ET.parse(plugin_xml)
kt_files = glob.glob(os.path.join(ROOT, "src/main/kotlin/**/*.kt"), recursive=True)
kt_classes = set()
for f in kt_files:
    src = open(f, encoding="utf-8").read()
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
        errors.append(f"plugin.xml référence une classe absente: {c}")
print(f"[1] plugin.xml OK, {len(referenced)} classes référencées, {len(kt_classes)} classes Kotlin trouvées")

# ---------- 2. Analyse du BNF ----------
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
    errors.append(f"BNF: règles définies en double: {dup}")
rule_set = set(rule_defs)

# Références de règles et de tokens dans les corps
body_no_attrs = re.sub(r'\{[^{}]*\}', ' ', body)  # retire les blocs d'attributs {pin=...}
refs_lower = set(re.findall(r'\b([a-z_][a-z0-9_]*)\b', body_no_attrs)) - {'private'}
refs_upper = set(re.findall(r'\b([A-Z_][A-Z0-9_]*)\b', body_no_attrs))
for r in sorted(refs_lower - rule_set):
    errors.append(f"BNF: règle référencée mais non définie: {r}")
for t in sorted(refs_upper - tokens):
    errors.append(f"BNF: token référencé mais non déclaré: {t}")
unused_tokens = tokens - refs_upper - {'LINE_COMMENT', 'BLOCK_COMMENT', 'DOC_COMMENT'}
if unused_tokens:
    warnings.append(f"BNF: tokens déclarés mais inutilisés dans la grammaire: {sorted(unused_tokens)}")

# Récursion gauche directe
for m in re.finditer(r'^\s*(?:private\s+)?([a-z_][a-z0-9_]*)\s*::=\s*(.*?)(?=^\s*(?:private\s+)?[a-z_][a-z0-9_]*\s*::=|\Z)', body, flags=re.M | re.S):
    name, rhs = m.group(1), m.group(2)
    for alt in re.split(r'(?<![|])\|', rhs):
        first = re.match(r'\s*([a-z_][a-z0-9_]*)', alt)
        if first and first.group(1) == name:
            errors.append(f"BNF: récursion gauche directe dans {name}")
print(f"[2] BNF: {len(tokens)} tokens, {len(rule_set)} règles")

# ---------- 3. Cohérence KrakenTypes.* Kotlin <-> BNF ----------
def upper_snake(rule):
    return rule.upper()
generated_consts = tokens | {upper_snake(r) for r in rule_set}
kt_all = "\n".join(open(f, encoding="utf-8").read() for f in kt_files + glob.glob(os.path.join(ROOT, "src/test/kotlin/**/*.kt"), recursive=True))
used_consts = set(re.findall(r'KrakenTypes\.([A-Z_][A-Z0-9_]*)', kt_all))
for c in sorted(used_consts - generated_consts):
    errors.append(f"Kotlin utilise KrakenTypes.{c} qui ne sera pas généré par Grammar-Kit")
print(f"[3] KrakenTypes: {len(used_consts)} constantes utilisées côté Kotlin, toutes vérifiées")

# ---------- 4. Lexer Kotlin couvre tous les tokens du BNF ----------
lexer_src = open(os.path.join(ROOT, "src/main/kotlin/com/kraken/plugin/parser/KrakenLexer.kt"), encoding="utf-8").read()
lexer_tokens = set(re.findall(r'KrakenTypes\.([A-Z_][A-Z0-9_]*)', lexer_src))
missing = tokens - lexer_tokens
if missing:
    errors.append(f"Tokens du BNF jamais produits par le lexer: {sorted(missing)}")
print(f"[4] Lexer: produit {len(lexer_tokens)} types de tokens")

# ---------- 5. Braces équilibrées dans les .kt ----------
for f in kt_files:
    src = open(f, encoding="utf-8").read()
    src2 = re.sub(r'"""', '@@@', src)
    src2 = re.sub(r'@@@.*?@@@', '""', src2, flags=re.S)
    src2 = re.sub(r"'([^'\\]|\\.)'", "''", src2)   # chars AVANT strings ('"' sinon casse l'appariement)
    src2 = re.sub(r'"([^"\\]|\\.)*"', '""', src2)
    src2 = re.sub(r'//[^\n]*', '', src2)
    src2 = re.sub(r'/\*.*?\*/', '', src2, flags=re.S)
    if src2.count('{') != src2.count('}'):
        errors.append(f"Accolades déséquilibrées: {f} ({src2.count('{')} vs {src2.count('}')})")
print(f"[5] Braces: {len(kt_files)} fichiers Kotlin vérifiés")

print()
for w in warnings:
    print("WARN :", w)
for e in errors:
    print("ERREUR:", e)
print()
print("RESULTAT:", "ECHEC" if errors else "OK")
sys.exit(1 if errors else 0)
