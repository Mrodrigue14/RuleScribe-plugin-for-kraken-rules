"""Reads the plugin's sources for the tools, so they check the real tables instead of copies."""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEXER = os.path.join(ROOT, 'src/main/kotlin/com/kraken/plugin/parser/KrakenLexer.kt')


def read(path):
    with open(path, encoding='utf-8') as handle:
        return handle.read()


def strip_line_comments(text):
    return re.sub(r'//[^\n]*', '', text)


def _kotlin_map(source, name, key_pattern):
    """`key to KrakenTypes.TOKEN` pairs of the Kotlin map named [name]."""
    body = re.search(r'val ' + name + r'\b[^=]*=\s*mapOf\((.*?)\n\s*\)', source, re.S).group(1)
    return {key: token for key, token in re.findall(key_pattern + r'\s+to\s+KrakenTypes\.(\w+)', strip_line_comments(body))}


def lexer_tables():
    """KrakenLexer's keyword, two-character and single-character tables, and its single-character operators."""
    source = read(LEXER)
    keywords = _kotlin_map(source, 'KEYWORDS', r'"(\w+)"')
    two_char = _kotlin_map(source, 'TWO_CHAR_TOKENS', r'"(..)"')
    single_char = _kotlin_map(source, 'SINGLE_CHAR_TOKENS', r"'(.)'")
    operators = re.search(r'SINGLE_CHAR_OPERATORS\s*=\s*"([^"]*)"', source).group(1)
    return keywords, two_char, single_char, set(operators)
