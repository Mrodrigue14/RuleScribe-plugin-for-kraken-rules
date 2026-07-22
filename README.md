# RuleScribe — Kraken Rules DSL support for IntelliJ

**RuleScribe** brings IntelliJ IDEA language support to the Kraken Rules DSL
(`.rules` files / KEL,
[eisgroup/kraken-rules](https://github.com/eisgroup/kraken-rules)).

> Independent community plugin, not affiliated with or endorsed by EIS Group.
> "Kraken" refers to the open-source Kraken Rules engine (Apache-2.0); this
> plugin's grammar is derived from the ANTLR grammar published in that
> repository, in accordance with its license.

## Features

- **Syntax highlighting**: keywords, strings, numbers, comments (`//`, `/* */`, `/** */`),
  `@Dimension` annotations, operators. Customizable in
  *Settings → Editor → Color Scheme → Kraken Rules*.
- **Code completion** (Ctrl+Space), context-aware:
  - top level: `Rule`, `EntryPoint`, `Context`, `Namespace`, `Dimension`, `Function`…
  - rule body: `Assert`, `Set Mandatory`, `Default To`, `Reset To`, `When`, `Priority`, `Error`…
  - inside `@Dimension(...)`: dimensions declared in the project
  - after `On`: declared context names
  - inside an `EntryPoint { ... }`: visible rules and other EntryPoints (with icons
    and origin file), excluding already-listed items and the current EntryPoint
- **Field completion** after `On Context.` and inside expressions
  (`When Policy.<Ctrl+Space>`), including `Is` inheritance and `Child` contexts.
- **Navigation**: Ctrl+B / Ctrl+click both ways — from an EntryPoint item to the
  declaration, and from a `Rule`/`EntryPoint` declaration name to the EntryPoints
  referencing it (popup when several); Find Usages (multi-word names supported);
  gutter icons on referenced rules and entry points.
- **Rename refactoring**: renaming a `Rule` or an `EntryPoint` (Shift+F6) updates
  all of its references.
- **Namespace-aware resolution**: `Namespace`/`Include` bound visibility in both
  directions — a reference living in a namespace that cannot see the declaration
  does not count for navigation, Find Usages, or the unused-rule inspection
  (mirrors the Kraken engine).
- **`Import Rule … From …` resolution**: an imported rule behaves as if it were
  declared in the importing namespace (navigation, completion, Find Usages,
  unused-rule inspection), independently of `Include` — exactly like the
  engine's rule-import semantics.
- **Structure View** (Alt+7), **code folding** and **formatter** (Ctrl+Alt+L).
- **Live templates**: `rule`, `ep`, `ctx`, `dim` + Tab.
- **Quick documentation** (Ctrl+Q) on rules: description, target, payloads, dimensions.
- **Intention**: *Add missing 'On' clause* (Alt+Enter on a rule without a target).
- **10 inspections**: rule without name, unresolved rule/entry point reference,
  unknown context, duplicate rules without a differentiating `@Dimension`,
  rule never referenced by any entry point, undeclared dimension, and 4
  rule-import checks mirroring the engine (unknown source namespace, rule
  missing from the source namespace, import colliding with a local rule,
  ambiguous import).
- **Stub-based index**: rule resolution goes through a persistent index —
  fast even on projects with hundreds of `.rules` files.
- See [ROADMAP.md](ROADMAP.md) for what's next (KEL type-checking, rule runner).

## Requirements

- Internet access (the first build downloads Gradle, the IntelliJ Platform ~1 GB,
  and Grammar-Kit)
- JDK 17 — auto-provisioned by Gradle (toolchain + foojay resolver) if missing

## Build

```bash
# Windows
.\gradlew.bat buildPlugin

# Linux / macOS
./gradlew buildPlugin
```

The packaged plugin is written to `build/distributions/rulescribe-0.6.0.zip`.

> 💡 Don't want to build? Every push to `main` produces the zip automatically
> on GitHub Actions — see [Grabbing a build from GitHub Actions](#grabbing-a-build-from-github-actions).

### Building on Ubuntu

```bash
# 1. Prerequisites (JDK 17 + git)
sudo apt update && sudo apt install -y openjdk-17-jdk git

# 2. Get the sources
git clone https://github.com/Mrodrigue14/kraken-rules-plugin.git
cd kraken-rules-plugin

# 3. Make the wrapper executable (if cloned from a Windows-made commit)
chmod +x gradlew

# 4. Test + build
./gradlew test
./gradlew buildPlugin
```

The zip lands in the same place: `build/distributions/rulescribe-0.6.0.zip`.
To try it in a sandbox IDE: `./gradlew runIde`.

### Other useful tasks

```bash
.\gradlew.bat runIde     # launches a sandbox IntelliJ with the plugin (try examples/demo.rules
                         # and the multi-file sample project examples/multi/ — see TESTING.md)
.\gradlew.bat test       # unit tests (parser, completion, inspections, navigation, rename)
.\gradlew.bat generateKrakenParser   # (re)generates the parser from src/main/bnf/Kraken.bnf
```

## Installation

1. `.\gradlew.bat buildPlugin`
2. In IntelliJ: *Settings → Plugins → ⚙ → Install Plugin from Disk…*
3. Select `build/distributions/rulescribe-0.6.0.zip`
4. Restart the IDE and open a `.rules` file (e.g. `examples/demo.rules`, or the
   multi-file sample project `examples/multi/` to try cross-file navigation —
   full manual checklist in [TESTING.md](TESTING.md))

## Architecture

```
src/main/bnf/Kraken.bnf          Grammar-Kit grammar (parser generated into src/main/gen)
src/main/kotlin/com/kraken/plugin/
  lang/         Language, FileType, ParserDefinition, Commenter, BraceMatcher
  parser/       KrakenLexer (hand-written, case-insensitive like the official ANTLR)
  psi/          Custom PSI elements (Rule, EntryPoint, references, rename, stubs)
  highlighter/  Syntax highlighting + color settings page
  completion/   Context-aware completion + live template context
  inspection/   Inspections + "Add missing 'On' clause" intention
  navigation/   GotoDeclaration, FindUsages, gutter line markers, references search
  structure/    Structure View + code folding
  formatter/    Code formatter
  documentation/ Quick documentation (Ctrl+Q)
```

The grammar is derived from the official ANTLR grammar (`KrakenDSL.g4`,
`Common.g4`, `Value.g4`) of the kraken-rules repository. It is deliberately
**more tolerant** than the original:

- a rule's name and its `On` clause are optional at the parser level
  (inspections report their absence instead);
- KEL expressions are parsed with a structured grammar ported from
  `Kel.g4`/`Value.g4` (if/then/else, for/return, every/some/satisfies, calls,
  access chains with filters `[...]` and spread `[*]`, `?.`/`?[`, `**`,
  type casts `(Type) expr`, collection literals, `set x to … return …` variable
  blocks) — no type-checking.

## Known limitations

- KEL expressions are parsed structurally but not typed (no type-checking).
- `Function` generic bounds (`<T is SomeType>`) are supported syntactically
  but carry no semantics.

## Grammar validation

The grammar has been validated against the complete corpus of the official
[eisgroup/kraken-rules](https://github.com/eisgroup/kraken-rules) repository:
**103/103 real `.rules` files accepted** (via `tools/sim_parser.py`, a Python
simulation of the Grammar-Kit parser that also emulates pin semantics).

```bash
python3 tools/validate.py      # plugin.xml / BNF / lexer / KrakenTypes consistency
python3 tools/sim_parser.py    # parses the test files with the simulated grammar
python3 tools/sim_parser.py path/to/your/file.rules   # try one of your own files
```

Covered constructs: `Namespace`/`Include`/`Import Rule … From`, `Context(s)`
(`Is` inheritance, fields, `Child *X : {a, b}`), `ExternalContext` /
`ExternalEntity`, `Rule(s)` (Description, Priority, When, every payload:
Assert / Assert Matches / Length / Size / Number Min Max Step / In,
Set Mandatory/Hidden/Disabled, Default/Reset To, Error/Warn/Info messages,
Overridable), `EntryPoint(s)` (nested, references), `Dimension`, `Function`
(generics, KEL bodies), `@Dimension` / `@ServerSideOnly` / `@NotStrict` /
`@ForbidTarget` / `@ForbidReference` annotations, type casts (`(Type) expr`, incl. chained `((Type) expr).field`),
date/datetime literals (`2020-01-01T00:00:00Z`), multi-line strings with `${...}` templates,
keywords usable as identifiers (`info`, `to`, `context`, …).

## Continuous integration

The GitHub Actions workflow `.github/workflows/build.yml` builds and tests the
plugin on **Ubuntu** (ubuntu-latest, JDK 17 Temurin, Gradle cache):

- on every **push** to `main`;
- on every **pull request** targeting `main`;
- **manually**: *Actions* tab → *Build* workflow → *Run workflow* button.

Steps: `test` → `buildPlugin` → `verifyPluginConfiguration` → upload of the
zip as a build artifact (and of the test report on failure).

### Grabbing a build from GitHub Actions

CI-built plugin zips can be downloaded without installing anything:

1. Open <https://github.com/Mrodrigue14/kraken-rules-plugin/actions>
2. Click the latest green run of the **Build** workflow
3. Scroll to the **Artifacts** section → download `rulescribe`
4. ⚠️ GitHub wraps artifacts in an extra zip: **extract**
   `rulescribe.zip` to get `rulescribe-0.6.0.zip`
   (the plugin's name always contains the version number)
5. Install that *inner* zip via *Settings → Plugins → ⚙ → Install Plugin from Disk…*

Artifacts are kept for 30 days.

## Security

RuleScribe is an editor-only language plugin with a deliberately minimal
attack surface:

- **Zero third-party runtime dependencies** in the shipped artifact —
  verified continuously (the OWASP scan reports **0 dependencies, 0
  vulnerabilities** on the shipped classpath). The only declared dependency,
  JUnit, is test-scoped and never distributed.
- **No network calls, no code execution, no access to credentials** — it only
  statically parses and inspects the `.rules` files open in your project.

Every change is checked automatically in CI:

- **CodeQL** — static analysis (SAST) of the Kotlin/Java code.
- **poutine** (BoostSecurity) — SAST of the CI/CD pipelines themselves
  (injection, unsafe triggers, supply-chain).
- **Dependabot** — dependency vulnerability alerts + dependency/action updates.
- **OWASP Dependency-Check** — CVEs in shipped dependencies; fails on
  CVSS ≥ 7.0.
- **IntelliJ Plugin Verifier** — binary compatibility across IntelliJ versions.

The build/release pipeline is hardened (least-privilege workflows, no
`pull_request_target`, no untrusted code run with secrets, isolated publish
token, tag-gated releases with tests + verification before publish). Every
Marketplace upload is re-verified server-side by JetBrains — recent releases
are verified compatible with **IntelliJ IDEA 2024.1 through 2026.2**.

Full details and vulnerability reporting: **[SECURITY.md](SECURITY.md)**.

## License

Licensed under the [PolyForm Shield License 1.0.0](LICENSE) — see `LICENSE`.
In short: you may freely use, modify and redistribute this plugin for any
purpose **except** providing a product that competes with it (or with any
product the licensor provides using it). This restriction does not expire.

The grammar (`src/main/bnf/Kraken.bnf`) is derived from the Apache-2.0 licensed
[eisgroup/kraken-rules](https://github.com/eisgroup/kraken-rules) project; the
Apache-2.0 terms for those portions are preserved in
[`third_party/Apache-2.0.txt`](third_party/Apache-2.0.txt) and attributed in
[`NOTICE`](NOTICE).
This is an independent community plugin, not affiliated with or endorsed by EIS
Group.
