# Kraken Rules — Plugin IntelliJ

Support du DSL Kraken `.rules` ([eisgroup/kraken-rules](https://github.com/eisgroup/kraken-rules)) pour IntelliJ IDEA.

## Fonctionnalités

- **Coloration syntaxique** : mots-clés, chaînes, nombres, commentaires (`//`, `/* */`, `/** */`), annotations `@Dimension`, opérateurs. Personnalisable dans *Settings → Editor → Color Scheme → Kraken Rules*.
- **Complétion de code** (Ctrl+Espace), sensible au contexte :
  - haut de fichier : `Rule`, `EntryPoint`, `Context`, `Namespace`, `Dimension`, `Function`…
  - corps de règle : `Assert`, `Set Mandatory`, `Default To`, `Reset To`, `When`, `Priority`, `Error`…
  - dans `@Dimension(...)` : les dimensions déclarées dans le projet
  - après `On` : les noms de contextes déclarés
  - dans un `EntryPoint { ... }` : les noms de règles existantes
- **Inspections** :
  - `Rule` sans nom (erreur)
  - référence de règle introuvable dans un `EntryPoint` (avertissement)
- **Intention** : *Add missing 'On' clause* (Alt+Entrée dans une règle sans cible)
- **Navigation** : Ctrl+B / Ctrl+clic depuis un nom de règle dans un `EntryPoint` vers la déclaration `Rule` ; Find Usages.
- **Renommage** : renommer une `Rule` (Maj+F6) met à jour ses références dans les `EntryPoint`.

## Prérequis

- Internet (le premier build télécharge Gradle, la plateforme IntelliJ ~1 Go et Grammar-Kit)
- JDK 17 — provisionné automatiquement par Gradle (toolchain + foojay resolver) s'il est absent

## Build

```bash
# Windows
.\gradlew.bat buildPlugin

# Linux / macOS
./gradlew buildPlugin
```

Le plugin empaqueté se trouve dans `build/distributions/kraken-rules-plugin-0.1.0.zip`.

### Autres tâches utiles

```bash
.\gradlew.bat runIde     # lance un IntelliJ sandbox avec le plugin (tester examples/demo.rules)
.\gradlew.bat test       # tests unitaires (parser, complétion, inspections, navigation, renommage)
.\gradlew.bat generateKrakenParser   # (re)génère le parser depuis src/main/bnf/Kraken.bnf
```

## Installation

1. `.\gradlew.bat buildPlugin`
2. Dans IntelliJ : *Settings → Plugins → ⚙ → Install Plugin from Disk…*
3. Sélectionner `build/distributions/kraken-rules-plugin-0.1.0.zip`
4. Redémarrer l'IDE et ouvrir un fichier `.rules` (par ex. `examples/demo.rules`)

## Architecture

```
src/main/bnf/Kraken.bnf          Grammaire Grammar-Kit (génère le parser dans src/main/gen)
src/main/kotlin/com/kraken/plugin/
  lang/         Language, FileType, ParserDefinition, Commenter, BraceMatcher
  parser/       KrakenLexer (lexer manuscrit, insensible à la casse comme l'ANTLR officiel)
  psi/          Éléments PSI personnalisés (Rule, EntryPoint, références, renommage)
  highlighter/  Coloration syntaxique + page de configuration des couleurs
  completion/   Complétion contextuelle
  inspection/   Inspections + intention "Add missing 'On' clause"
  navigation/   GotoDeclaration, FindUsages
```

La grammaire est dérivée de la grammaire ANTLR officielle (`KrakenDSL.g4`, `Common.g4`)
du repo kraken-rules. Elle est volontairement **plus tolérante** que l'originale :

- le nom d'une règle et sa clause `On` sont optionnels au niveau du parser
  (ce sont les inspections qui signalent leur absence) ;
- les expressions KEL (`When`, `Assert`, `Default To`…) sont analysées de manière
  permissive : suite de tokens avec parenthèses/crochets/accolades équilibrés,
  sans validation sémantique du langage d'expression.

## Limitations connues (v0.1.0)

- Pas de résolution inter-namespace (`Include` / `Import Rule … From …` sont parsés
  mais les références sont résolues sur l'ensemble du projet, sans tenir compte
  des espaces de noms).
- Les expressions KEL ne sont pas validées (pas de type-checking).
- Les bornes génériques de `Function` (`<T is SomeType>`) sont supportées
  syntaxiquement mais sans sémantique.

## Validation de la grammaire

La grammaire a été validée contre le corpus complet du repo officiel
[eisgroup/kraken-rules](https://github.com/eisgroup/kraken-rules) :
**103/103 fichiers `.rules` réels acceptés** (via `tools/sim_parser.py`,
une simulation PEG du parser Grammar-Kit).

```bash
python3 tools/validate.py      # cohérence plugin.xml / BNF / lexer / KrakenTypes
python3 tools/sim_parser.py    # parse les fichiers de test avec la grammaire simulée
python3 tools/sim_parser.py chemin/vers/fichier.rules   # tester un fichier à toi
```

Constructions couvertes : `Namespace`/`Include`/`Import Rule … From`,
`Context(s)` (héritage `Is`, champs, `Child *X : {a, b}`), `ExternalContext` /
`ExternalEntity`, `Rule(s)` (Description, Priority, When, tous les payloads :
Assert / Assert Matches / Length / Size / Number Min Max Step / In,
Set Mandatory/Hidden/Disabled, Default/Reset To, messages Error/Warn/Info,
Overridable), `EntryPoint(s)` (imbriqués, références), `Dimension`, `Function`
(génériques, corps KEL), annotations `@Dimension` / `@ServerSideOnly` /
`@NotStrict` / `@ForbidTarget` / `@ForbidReference`, littéraux date/datetime
(`2020-01-01T00:00:00Z`), chaînes multi-lignes avec templates `${...}`,
mots-clés utilisables comme identifiants (`info`, `to`, `context`, …).

## Intégration continue

Le workflow GitHub Actions `.github/workflows/build.yml` build et teste le plugin
sur **Ubuntu** à chaque push/PR sur `main` (JDK 17, cache Gradle). Le zip du plugin
est publié comme artefact de build : onglet *Actions* du repo → dernier run →
section *Artifacts* → `kraken-rules-plugin`. Le workflow peut aussi être lancé
manuellement (*Run workflow*).
