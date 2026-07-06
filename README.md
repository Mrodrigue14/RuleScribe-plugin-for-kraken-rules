# Kraken Rules — Plugin IntelliJ

Support du DSL Kraken `.rules` ([eisgroup/kraken-rules](https://github.com/eisgroup/kraken-rules)) pour IntelliJ IDEA.

> Plugin communautaire indépendant, sans affiliation avec EIS Group ni approbation
> de leur part. « Kraken » désigne le moteur open source Kraken Rules (Apache-2.0) ;
> la grammaire de ce plugin est dérivée de la grammaire ANTLR publiée dans ce repo,
> conformément à sa licence.

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
- **Structure View** (Alt+7), **repli de code** et **formateur** (Ctrl+Alt+L).
- **Live templates** : `rule`, `ep`, `ctx`, `dim` + Tab.
- **Complétion des champs** après `On Contexte.` (héritage `Is` et `Child` compris).
- **Résolution par namespace** : `Namespace`/`Include` délimitent la visibilité des règles.
- **Quick documentation** (Ctrl+Q) sur les règles ; icône de gouttière vers les EntryPoints référents.
- **6 inspections** : règle sans nom, référence inconnue, contexte inconnu, règles dupliquées, règle jamais référencée, dimension non déclarée.
- Voir [ROADMAP.md](ROADMAP.md) pour la suite (grammaire KEL typée, runner, stub index).

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

Le plugin empaqueté se trouve dans `build/distributions/kraken-rules-plugin-0.3.0.zip`.

> 💡 Pas envie de builder ? Chaque push sur `main` produit le zip automatiquement
> sur GitHub Actions — voir [Récupérer un build depuis GitHub Actions](#récupérer-un-build-depuis-github-actions).

### Build sur Ubuntu

```bash
# 1. Prérequis (JDK 17 + git)
sudo apt update && sudo apt install -y openjdk-17-jdk git

# 2. Récupérer les sources
git clone https://github.com/Mrodrigue14/kraken-rules-plugin.git
cd kraken-rules-plugin

# 3. Rendre le wrapper exécutable (si cloné depuis un commit fait sous Windows)
chmod +x gradlew

# 4. Tests + build
./gradlew test
./gradlew buildPlugin
```

Le zip est généré au même endroit : `build/distributions/kraken-rules-plugin-0.3.0.zip`.
Pour tester dans un IDE sandbox : `./gradlew runIde`.

### Autres tâches utiles

```bash
.\gradlew.bat runIde     # lance un IntelliJ sandbox avec le plugin (tester examples/demo.rules
                         # et le mini-projet multi-fichiers examples/multi/ — voir TESTING.md)
.\gradlew.bat test       # tests unitaires (parser, complétion, inspections, navigation, renommage)
.\gradlew.bat generateKrakenParser   # (re)génère le parser depuis src/main/bnf/Kraken.bnf
```

## Installation

1. `.\gradlew.bat buildPlugin`
2. Dans IntelliJ : *Settings → Plugins → ⚙ → Install Plugin from Disk…*
3. Sélectionner `build/distributions/kraken-rules-plugin-0.3.0.zip`
4. Redémarrer l'IDE et ouvrir un fichier `.rules` (par ex. `examples/demo.rules`,
   ou le mini-projet `examples/multi/` pour tester la navigation inter-fichiers —
   checklist complète dans [TESTING.md](TESTING.md))

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
- les expressions KEL sont analysées avec une grammaire structurée portée de
  `Kel.g4`/`Value.g4` (if/then/else, for/return, every/some/satisfies, appels,
  chaînes d'accès avec filtres `[...]` et spread `[*]`, `?.`/`?[`, `**`,
  littéraux de collection, blocs `set x to … return …`) — sans type-checking.

## Limitations connues (v0.1.0)

- `Import Rule … From …` est parsé mais n'affine pas encore la résolution
  (la visibilité est calculée au niveau des namespaces via `Include`).
- Les expressions KEL sont parsées structurellement mais pas typées (pas de type-checking).
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
sur **Ubuntu** (ubuntu-latest, JDK 17 Temurin, cache Gradle) :

- à chaque **push** sur `main` ;
- à chaque **pull request** vers `main` ;
- **manuellement** : onglet *Actions* → workflow *Build* → bouton *Run workflow*.

Étapes exécutées : `test` → `buildPlugin` → `verifyPluginConfiguration` →
publication du zip en artefact (et du rapport de tests en cas d'échec).

### Récupérer un build depuis GitHub Actions

Les zips du plugin buildés par la CI sont téléchargeables sans rien installer :

1. Ouvrir <https://github.com/Mrodrigue14/kraken-rules-plugin/actions>
2. Cliquer sur le dernier run vert du workflow **Build**
3. Descendre à la section **Artifacts** → télécharger `kraken-rules-plugin`
4. ⚠️ GitHub emballe l'artefact dans un zip supplémentaire : **extraire**
   `kraken-rules-plugin.zip` pour obtenir `kraken-rules-plugin-0.3.0.zip`
   (le nom du plugin contient toujours le numéro de version)
5. Installer ce zip *intérieur* via *Settings → Plugins → ⚙ → Install Plugin from Disk…*

Les artefacts sont conservés 90 jours par défaut.
