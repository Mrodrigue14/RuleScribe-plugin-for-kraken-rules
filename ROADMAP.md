# Roadmap — Kraken Rules Plugin

## v0.2.0 (actuel)

Structure view, folding, formatter, live templates, complétion des champs après
`On Contexte.` (héritage compris), résolution par namespace (`Namespace`/`Include`),
line markers de références, quick documentation (Ctrl+Q), 6 inspections.

## v0.3.0 — Grammaire KEL complète ✅ (livrée — reste le type-checking, déplacé en v0.5+)

Remplacer l'analyse permissive des expressions (`expression ::= expr_item+`) par
la vraie grammaire KEL, portée depuis `Kel.g4` / `Value.g4` du repo eisgroup.

- Étendre `Kraken.bnf` : précédence des opérateurs (via `extends` Grammar-Kit),
  accès `?.`, `[]`, `if/then/else`, `for/every/some … satisfies`, `instanceof`/`typeof`.
- PSI typé pour les expressions → complétion des champs *dans* les expressions
  (`When Policy.<caret>`), et à terme un type-checker minimal
  (String/Number/Boolean/Money/Date + collections).
- Valider avec `tools/sim_parser.py` étendu contre le corpus (103 fichiers).
- Risque principal : régressions de tolérance. Garder le fallback permissif en
  cas d'échec de parse d'expression (recoverWhile sur la clause).

## Runner de règles (reporté — non planifié pour l'instant)

Exécuter un `EntryPoint` sur un payload JSON de test depuis l'IDE.

- `RunLineMarkerContributor` (icône ▶ sur chaque EntryPoint) + `RunConfiguration`
  dédiée (chemin du JSON, entryPoint, dimensions).
- Exécution via le moteur Java `kraken-engine` (dépendance Maven ajoutée à la
  run configuration de l'utilisateur, pas au plugin) dans un process séparé.
- Affichage des résultats dans une Tool Window (règles évaluées, échecs de
  validation, erreurs d'expression).

## v0.4.0 — Performances sur gros projets ✅ (stub index des Rule livré ; CachedValues et stubs Context/EntryPoint restent à faire)

- Stub index pour `Rule`, `EntryPoint`, `Context`, `Dimension` (résolution O(1)
  au lieu du scan des fichiers).
- `CachedValuesManager` sur les calculs de visibilité namespace.
- Tests de performance sur un projet synthétique de 500 fichiers.

## Divers / dette

- Renommage des EntryPoints avec mise à jour des références `EntryPoint "x"`.
- Spellchecker dans les chaînes (descriptions, messages).
- Icônes distinctes règle/entrypoint/contexte dans la Structure View.
