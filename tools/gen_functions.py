#!/usr/bin/env python3
"""Génère le catalogue des fonctions natives KEL depuis les sources du moteur.

Les 55 fonctions intégrées de Kraken sont des méthodes Java statiques annotées
`@ExpressionFunction` dans des classes `FunctionLibrary` ; le moteur les découvre
par `ServiceLoader` au démarrage. RuleScribe est un plugin d'analyse statique :
il n'exécute rien et n'a aucune dépendance de compilation vers le moteur. Il lui
faut donc une copie figée de ce catalogue, et cette copie doit être REGÉNÉRÉE,
jamais éditée à la main — même règle que `src/main/gen`.

    python3 tools/gen_functions.py ../kraken-rules

Sortie : src/main/resources/functions/kel-functions.json

La correspondance type Java → type KEL reproduit `FunctionRegistry.fromJavaType`
du moteur (voir `kraken-expression-language`). Une annotation `@ParameterType` /
`@ReturnType` explicite, quand elle est présente, prime sur la signature Java —
exactement comme dans `getParameterType` / `getReturnType`.

Source : eisgroup/kraken-rules (Apache-2.0). Voir NOTICE.
"""

import json
import re
import sys
from pathlib import Path

LIBRARY_DIR = (
    "kraken-expression-language/src/main/java/kraken/el/functionregistry/functions"
)
OUTPUT = Path(__file__).resolve().parent.parent / (
    "src/main/resources/functions/kel-functions.json"
)

# FunctionRegistry.fromJavaType : les types Java reconnus, avant repli sur le
# nom simple de la classe. L'ordre n'a pas d'importance, les clés sont exactes.
JAVA_TO_KEL = {
    "Number": "Number",
    "BigDecimal": "Number",
    "Integer": "Number",
    "Long": "Number",
    "Double": "Number",
    "String": "String",
    "MonetaryAmount": "Money",
    "Boolean": "Boolean",
    "LocalDate": "Date",
    "LocalDateTime": "DateTime",
    "Object": "Any",
    "Map": "Any",
}

COLLECTIONS = {"Collection", "List", "Set", "Iterable"}


def kel_type(java_type: str, generics: set) -> str:
    """Type KEL correspondant à un type Java, façon FunctionRegistry."""
    java_type = java_type.strip()

    match = re.fullmatch(r"(\w+)\s*<(.+)>", java_type)
    if match:
        raw, arg = match.group(1), match.group(2).strip()
        if raw in COLLECTIONS:
            # Collection<?> et Collection<? extends X> : le moteur retient la
            # borne supérieure, ou Any en son absence.
            if arg == "?":
                return "Any[]"
            arg = re.sub(r"^\?\s+extends\s+", "", arg)
            return kel_type(arg, generics) + "[]"
        return "Any"

    if java_type in COLLECTIONS:
        return "Any[]"
    if java_type in generics:
        return "<" + java_type + ">"
    return JAVA_TO_KEL.get(java_type, java_type)


def strip_comments(source: str) -> str:
    """Retire les commentaires de bloc, sauf les annotations qu'on parse."""
    return re.sub(r"//[^\n]*", "", source)


def unescape(text: str) -> str:
    """Concatène un littéral Java multi-lignes en une seule chaîne."""
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', text)
    joined = "".join(parts)
    return joined.replace('\\"', '"').replace("\\n", "\n").replace("\\\\", "\\")


def parse_annotation_args(text: str) -> str:
    """Contenu entre les parenthèses d'une annotation, parenthèses équilibrées."""
    depth = 0
    for index, char in enumerate(text):
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[1:index]
    return ""


def parse_examples(block: str) -> list:
    examples = []
    for raw in re.finditer(r"@Example\s*\(", block):
        args = parse_annotation_args(block[raw.end() - 1:])
        value = re.search(r'value\s*=\s*((?:"[^"]*"\s*\+?\s*)+)', args)
        if value is None:
            value = re.match(r'\s*((?:"[^"]*"\s*\+?\s*)+)', args)
        result = re.search(r'result\s*=\s*((?:"[^"]*"\s*\+?\s*)+)', args)
        if value:
            examples.append({
                "expression": unescape(value.group(1)),
                "result": unescape(result.group(1)) if result else None,
            })
    return examples


def parse_parameters(signature: str, block: str, generics: set) -> list:
    """Paramètres d'une méthode, avec leur type KEL et leur nom documenté."""
    inner = parse_annotation_args("(" + signature.split("(", 1)[1])
    if not inner.strip():
        return []

    # Découpe sur les virgules de premier niveau : les génériques en contiennent.
    parts, depth, current = [], 0, ""
    for char in inner:
        if char in "<(":
            depth += 1
        elif char in ">)":
            depth -= 1
        if char == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += char
    parts.append(current)

    parameters = []
    for part in parts:
        part = part.strip()
        if not part:
            continue
        declared = re.search(r'@ParameterType\s*\(\s*"([^"]*)"', part)
        documented = re.search(r'@ParameterDocumentation\s*\(([^)]*)\)', part)
        name = None
        if documented:
            found = re.search(r'name\s*=\s*"([^"]*)"', documented.group(1))
            name = found.group(1) if found else None
        # Le nom du paramètre Java est le dernier identifiant de la déclaration.
        java = re.sub(r"@\w+\s*(\([^)]*\))?", "", part).strip()
        tokens = java.rsplit(" ", 1)
        java_name = tokens[-1] if len(tokens) == 2 else None
        java_type = tokens[0].strip() if len(tokens) == 2 else java

        parameters.append({
            "name": name or java_name,
            "type": declared.group(1) if declared else kel_type(java_type, generics),
            "required": "@NotNull" in part,
        })
    return parameters


def parse_library(path: Path) -> dict:
    source = strip_comments(path.read_text(encoding="utf-8"))

    library = {"name": path.stem, "description": None, "since": None}
    doc = re.search(r"@LibraryDocumentation\s*\(", source)
    if doc:
        args = parse_annotation_args(source[doc.end() - 1:])
        for key in ("name", "description", "since"):
            found = re.search(key + r'\s*=\s*((?:"[^"]*"\s*\+?\s*)+)', args)
            if found:
                library[key] = unescape(found.group(1))

    functions = []
    for marker in re.finditer(r'@ExpressionFunction\s*\(\s*"([^"]*)"\s*\)', source):
        name = marker.group(1)
        # Le bloc d'annotations précède l'annotation @ExpressionFunction ; la
        # signature la suit, jusqu'à l'accolade ouvrante du corps.
        start = source.rfind("@FunctionDocumentation", 0, marker.start())
        block = source[start if start != -1 else marker.start():marker.start()]
        signature = source[marker.end():source.find("{", marker.end())]

        generics = set(re.findall(r"generic\s*=\s*\"([^\"]*)\"", block))
        generics |= set(re.findall(r"<\s*(\w)\s*>\s*\w", signature))

        description = re.search(
            r'description\s*=\s*((?:"[^"]*"\s*\+?\s*)+)', block
        )
        since = re.search(r'since\s*=\s*"([^"]*)"', block)
        returns = re.search(r'@ReturnType\s*\(\s*"([^"]*)"', block + signature)
        java_return = signature.strip().split("static", 1)[-1].strip()
        # `public static <T> Collection<T> join(…)` : la liste de paramètres de
        # type précède le type de retour, il faut la retirer avant de le lire.
        if java_return.startswith("<"):
            depth = 0
            for index, char in enumerate(java_return):
                depth += (char == "<") - (char == ">")
                if depth == 0:
                    java_return = java_return[index + 1:].strip()
                    break
        java_return = java_return.split("(")[0].rsplit(" ", 1)[0].strip()

        functions.append({
            "name": name,
            "library": library["name"],
            "parameters": parse_parameters(signature, block, generics),
            "returnType": returns.group(1) if returns
            else kel_type(java_return, generics),
            "description": unescape(description.group(1)) if description else None,
            "since": since.group(1) if since else None,
            "examples": parse_examples(block),
        })

    library["functions"] = functions
    return library


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2

    engine = Path(sys.argv[1])
    directory = engine / LIBRARY_DIR
    if not directory.is_dir():
        print(f"Répertoire introuvable : {directory}")
        return 1

    libraries = [parse_library(p) for p in sorted(directory.glob("*Functions.java"))]
    functions = [f for library in libraries for f in library["functions"]]

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(
            {
                "source": "eisgroup/kraken-rules",
                "libraries": [
                    {k: v for k, v in library.items() if k != "functions"}
                    for library in libraries
                ],
                "functions": sorted(
                    functions, key=lambda f: (f["name"], len(f["parameters"]))
                ),
            },
            indent=2,
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )

    print(f"{len(functions)} fonctions, {len(libraries)} bibliothèques -> {OUTPUT}")
    missing = [f["name"] for f in functions if not f["description"]]
    if missing:
        print(f"Sans description : {', '.join(sorted(set(missing)))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
