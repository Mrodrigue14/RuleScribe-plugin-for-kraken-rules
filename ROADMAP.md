# Roadmap — RuleScribe for Kraken Rules

## Current — v0.14.0

Shipped: full KEL expression grammar, stub-based Rule index, strict
namespace-aware resolution (Namespace/Include/Import Rule) with a cached
visibility model, bidirectional Ctrl+Click navigation that offers every valid
target, EntryPoint references with rename support, quick documentation,
structure view with distinct icons, folding, formatter, live templates,
12 inspections, function support inside rule bodies (built-in catalogue,
completion, parameter info, quick documentation, navigation, highlighting),
inlays on every declaration showing usage count and last author, identifier
resolution inside rule bodies, KEL type inference, diagnostics that carry the
engine's own codes, wording and severity, quick-fixes, spellchecking of prose,
semantic colouring of references, depth-coloured brackets, the engine's full
type production including unions, and a Move refactoring that reports what it
would break before writing. Published on the
JetBrains Marketplace, signed and shipped with SLSA build provenance and a
CycloneDX SBOM, and verified against every IntelliJ major since the target.
See plugin.xml change notes for the detailed history.
See [Type grammar gaps](#type-grammar-gaps) and [Function validation
(removed)](#function-validation-removed) below.

## v0.6.0 — Import Rule resolution ✅ (shipped)

`Import Rule "X" From NamespaceB` copies rule X from another namespace
into the current one — independent of Include.

- ✅ `Import Rule` references resolve to their source-namespace rule,
  regardless of Include (navigation, completion, Find Usages,
  unused-rule inspection).
- ✅ 4 new inspections mirroring engine validation: unknown source
  namespace, rule not found in source namespace, imported name
  collides with a local rule, ambiguous import (same name imported
  more than once).

## v0.7.0 — Performance foundations ✅ (shipped)

Lay the groundwork before type-checking makes resolution much more
frequent.

- ✅ **Cached namespace-visibility model** — `visibleFiles()` (called by
  nearly every resolution, completion and inspection) no longer re-reads
  every file's AST on each call; the namespace/include graph is computed
  once and invalidated on PSI modification.
- ✅ **Perf test on a synthetic 500-file project** — guards against a
  regression of the cache.
- ⏸️ Stub index for Context, EntryPoint, Dimension — **deferred**: after the
  visibility cache, measurements show no bottleneck, so this would be
  premature optimization (and it is heavy: BNF regen + stub-based PSI). Rule
  keeps its stub index. Revisit if profiling on very large projects shows
  completion is AST-bound.

## Understanding rule bodies

Everything up to v0.7.3 resolves the syntax *around* the logic: rule names,
EntryPoints, contexts, dimensions. Inside a rule body — the `Assert`, `When`,
`Default To` expressions — the grammar parses the structure (`function_call`,
`ref_expr`, `dot_access`, `set_var`, `for_expr`) but nothing resolves: no
Ctrl+B, no completion, no inspection.

The three releases below close that gap, in dependency order. They were
renumbered when this plan landed: type-checking (previously v0.8.0) needs
resolved symbols to work on, so it moves behind the two stages that produce
them, and editor polish (previously v0.9.0) moves with it.

Reference implementation, verified against eisgroup/kraken-rules 1.55.0
(Apache-2.0 — any catalogue derived from it carries the same attribution
treatment as `src/main/bnf/Kraken.bnf`; see NOTICE).

## v0.8.0 — Functions ✅ (shipped)

Independent of the two stages below: functions resolve by name and arity, with
no type inference involved. Cheapest useful step into rule bodies.

The engine merges three sources of callable functions in
`kraken.model.project.scope.ScopeBuilder`, in increasing precedence:

1. **Native Java** — static methods annotated `@ExpressionFunction("Name")` on
   classes implementing `FunctionLibrary`, discovered through
   `ServiceLoader` (`kraken.el.functionregistry.FunctionRegistry`). 55
   built-ins across 9 libraries (Math, String, Date, Money, Collection, Set,
   Quantifier, Type, GenericValue).
2. **`Function` with a body** — implemented in KEL, in the `.rules` file.
3. **`Function` without a body** — a signature that *declares* a Java function
   must exist. `KrakenProjectConverter` fails the build when no registered
   function matches it, or when the signature is incompatible. This is the
   explicit DSL → Java bridge.

A function's identity is `(name, parameter count)` — not its parameter types.

- **Bundled catalogue of the 55 native functions**, generated from the upstream
  annotations by a `tools/` script (same spirit as `sim_parser.py`: regenerable,
  never hand-edited). The annotations already carry everything needed —
  `@FunctionDocumentation` (description, `@Example` with expected result,
  `since`), `@ParameterDocumentation`, and explicit KEL types via
  `@ParameterType("Number[]")` / `@ReturnType` where they differ from the Java
  signature.
- **Completion** of function names inside expressions, with signature.
- **Parameter info** (Ctrl+P) and **quick documentation** (Ctrl+Q) fed by the
  catalogue: description, examples, `since`.
- **`Function` declarations resolve**: Ctrl+B from a call to its declaration,
  Find Usages, rename. Reuses the existing namespace-visibility machinery
  unchanged.
- **Doc comments** — the engine has a dedicated grammar for them
  (`FunctionDoc.g4`: `@since`, `@example`, `@result`, `@invalidExample`,
  `@parameter name - description`). The lexer already emits `DOC_COMMENT`.
- **Unknown-function inspection** on `(name, arity)`, matching the engine's own
  criterion.
- **Colors.** A lexer-based highlighter cannot tell a call from a variable —
  both are `IDENTIFIER`. This needs an Annotator, which sees `call_head`
  followed by `call_args`:
  - a `FUNCTION_CALL` attribute for any call — no catalogue needed;
  - distinct attributes for *built-in* vs *declared-in-DSL* functions, so a
    typo in a native name is visible before the inspection runs;
  - both added to the color settings page (17 attributes today) and its
    demo text.

## v0.8.1 — Usage inlays, and the platform's usages popup ✅ (shipped)

Small and self-contained — it depends on nothing else, and it *deletes* more
code than it adds.

Every IDE built on the platform shows a clickable `4 usages` inlay above a
declaration, and clicking it opens the standard Show Usages popup: usages
grouped by file, each with its source line and a preview. RuleScribe shows
neither. Its own Ctrl+Click chooser (built up in v0.7.1 and v0.7.3) lists bare
labels instead — readable, but a hand-built stand-in for something the platform
already does better.

- **Code Vision inlay.** Extend
  `com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider`
  (registered on `com.intellij.codeInsight.daemonBoundCodeVisionProvider` —
  it implements `DaemonBoundCodeVisionProvider`, not `CodeVisionProvider`) for
  `Rule`, `EntryPoint` and `Function` declarations. The base class already
  implements `handleClick` — which opens the usages popup — plus `getName` and
  `getGroupId`; only `acceptsFile`, `acceptsElement` and `getHint` remain,
  where `getHint` returns the count. Java's own
  `JavaReferencesCodeVisionProvider` is the model to follow.
- **Ctrl+B on a declaration should use that same popup.** Today
  `KrakenGotoDeclarationHandler` intercepts the declaration case and returns the
  usages *as if they were declaration targets*, which is what forces the plain
  chooser. Dropping that branch lets the platform's built-in Go To Declaration
  or Usages take over and show the rich popup instead.
- The strict namespace semantics survive the switch: `KrakenReferencesSearcher`
  already feeds Find Usages from the same `findRuleRefsVisibleTo` /
  `findEpRefsVisibleTo` helpers the current handler uses, so both paths filter
  identically. This is the fact that makes the change cheap rather than risky —
  confirm it with the existing cross-namespace tests before deleting anything.
- What is *not* replaced: reference → declaration. Offering every
  `@Dimension` variant of a rule (v0.7.3) stays a `GotoDeclarationHandler`
  concern; only the declaration → usages direction moves to the platform.

## v0.8.2 — Author and date inlays (VCS Code Vision) ✅ (shipped)

Next to `N usages`, the platform can show who last touched a block and when —
clicking opens the annotation gutter, where each line carries its own commit
date. It is the same Code Vision framework as v0.8.1, on a second track.

Nothing needs to be built for the VCS side itself: `VcsCodeVisionProvider`
already computes the author from the annotation data. What it lacks is a way to
know which PSI elements deserve an inlay in a language it does not know, which
is what the `vcs.codeVisionLanguageContext` extension point supplies — Java
registers `JavaVcsCodeVisionContext` there.

- Implement `VcsCodeVisionLanguageContext` for `Rule`, `EntryPoint`,
  `Context` and `Function` declarations. `VcsCodeVisionCurlyBracketLanguageContext`
  is the brace-delimited base to check first — Kraken declarations are
  `{ … }` blocks, so it may fit as-is.
- The inlay appears only when the file is under version control and annotations
  are available; there is nothing to do for the "no VCS" case.

## v0.9.0 — Identifier resolution inside expressions ✅ (shipped)

Port the engine's scope model *structurally*, without types. `kraken.el.scope`
defines five scope kinds — GLOBAL, LOCAL, PATH, FILTER, VARIABLES_MAP — with
precise shadowing rules (a PATH scope has no parent: only properties of the
object are reachable; a FILTER scope falls back to its parent).

- Resolve `ref_expr` to: local variables (`set x to …`), iteration variables
  (`for c in …`, `every c in …`), fields of the context targeted by `On`, and
  cross-context references.
- Ctrl+B and Find Usages on a field inside `Assert` / `When` / `Default To`.
- Completion after `.` driven by the resolved context rather than PSI shape.
- Unresolved-reference inspection, mirroring the engine's `Reference ''{0}''
  not found.` and `Attribute ''{0}'' not found in ''{1}''.`

## v0.10.0 — KEL type-checking ✅ (shipped, partially)

Port the reference algorithm from kraken-expression-language
(kraken.el.ast.validation.AstValidatingVisitor) rather than reinventing it.
Depends on v0.9.0: the validator works on resolved symbols.

- 7 native types (Boolean, String, Number, Money, Date, DateTime, Type)
  plus Any (dynamic) and Unknown; Money widens to Number one-way; Date
  and DateTime are NOT cross-comparable.
- Mirror the engine's 3-tier severity: ERROR (blocks evaluation),
  WARNING, INFO — not a blanket soft-warning mode.
- Type-aware field completion backed by inferred types instead of raw
  PSI shape.
- Validate against the 103-file corpus via tools/sim_parser.py.

## v0.11.0 — Diagnostics that match the engine ✅ (shipped)

Today every inspection message is worded by hand, so the IDE and the build say
different things about the same defect. The engine publishes two coded
catalogues we can align to:

- `kraken.model.project.validator.ValidationMessageBuilder` — 111 design-time
  codes (`kvr` rules, `kvf` functions, `kvc` contexts, `kve`, `kvx`, `kvn`,
  `kvd`), each with a message template and a severity (99 ERROR, 10 WARNING,
  3 INFO).
- `kraken.message.SystemMessageBuilder` — 56 runtime/conversion codes (`kbs`).

Work:

- ✅ `KrakenDiagnostic` carries the engine's code and wording on every
  inspection that has an equivalent, prefixed as `[kvr027]` so the same string
  is searchable in the IDE and in the build log. Two checks deliberately carry
  no code, because the engine has no equivalent: the unused-rule inspection,
  and the undeclared-dimension one (the engine filters unknown dimension names
  out silently rather than validating them).
- ✅ Severity comes from the engine's declaration; six inspections moved from
  WARNING to ERROR. `GENERIC_ERROR` was replaced by `GENERIC_ERROR_OR_WARNING`
  so the configured level actually governs — previously the code forced a
  severity regardless of the inspection profile.
- ✅ **Operator token tightened.** The lexer now recognises only the operators
  `Common.g4` defines. `a &|&~ b` used to lex as one `OP` that `binary_op`
  accepted; it now yields a `BAD_CHARACTER` at each offending character. `^`
  and `~` belong to no operator at all, and `&` alone is not one either.
  `tools/sim_parser.py` carried the identical maximal-run logic and changed in
  lockstep. Verified: 103/103 corpus files still parse.
- ✅ **A bug this uncovered.** `>=` and `<=` were never single tokens — `<`
  and `>` hit the single-character table first, so `a >= b` lexed as `GT` then
  `OP('=')`. The type inspection took that `=` for the right operand and
  abstained, so no wide comparison had ever been checked. Both are whole
  tokens now, which `binary_op` already accepted.
- ✅ **`pin=` reviewed, no change needed.** All 44 pins sit on the
  distinguishing token of their rule, which is the correct placement. Checked
  against deliberately broken input: a missing brace reports inside
  `rule_body`, an incomplete `if` reports inside `if_expr` rather than
  backtracking to something vague. The operator token was the real lever on
  error quality, as this section predicted.

## v0.12.0 — Editor polish ✅ (shipped)

- ✅ Quick-fixes on the two inspections that have a mechanical one: declare an
  undeclared `@Dimension` name, and annotate a duplicate rule. Neither invents
  a value — a dimension's type and the value separating two rules are not
  derivable from the file — so both leave a placeholder.
- ⏸️ Semantics for Function generic bounds (`<T is SomeType>`) — **deferred**.
  Giving them meaning while `type_ref` cannot parse two of the three
  constructs `Value.g4`'s type production defines (`#UnionType`,
  `#PlainTypePrecedence`) would build on the broken part. See
  [Type grammar gaps](#type-grammar-gaps) below.
- ✅ Spellchecking, restricted to the two prose positions the grammar defines:
  the string of a `description_clause`, and the *last* string of a
  `payload_message` (`Error "code" : "message"` puts the code first). Rule
  names and error codes are left alone — flagging `AZStateCoverateVisibility`
  on every line would bury the real typos.
- ✅ Semantic highlighting by what a reference resolves to, a context name
  apart from a field or variable. Nothing is painted when resolution fails:
  colouring an unknown name as a field would claim it denotes one.
- Bracket pair colorization: color matching `{}`/`()`/`[]` by nesting depth,
  most useful for nested KEL expressions. Two constraints decided up front:
  - **Red is reserved**, and excluded from the depth palette. It marks a brace
    whose partner is missing, or whose pairing is ambiguous because an
    intervening brace is unbalanced. A color that means *error* cannot also be
    one rainbow hue among others, or it stops meaning anything.
  - Depth colors and the unmatched color both belong to an Annotator rather
    than the lexer, since matching is a tree property — `KrakenBraceMatcher`
    already decides pairing, the unmatched case is what needs surfacing.

  Configurable in the color settings page next to the function-call attributes
  added in v0.8.0, and toggleable. Note: overlaps with the third-party Rainbow
  Brackets plugin — the value is built-in, DSL-tuned colors.

  **Shipped, with one correction to the plan above.** `KrakenBraceMatcher` does
  *not* already decide pairing: `PairedBraceMatcher` only exposes `getPairs`,
  `isPairedBracesAllowedBeforeType` and `getCodeConstructStart`, which drive
  the highlight of the pair under the caret and answer nothing about which
  brace partners which, nor about orphans. Pairing is computed with a stack in
  `KrakenBracketAnnotator`, once per file. The toggle is the platform's own
  `RainbowColorSettingsPage` checkbox rather than a bespoke setting; since the
  platform treats "unset" as off, unset counts as on here so the feature is
  not invisible on install.

## v1.0.0 — Stabilization (partly shipped in 0.13.0)

- ✅ Compatibility testing now covers the latest patch of **every** major
  since the target, not just the two endpoints: 2024.1, 2024.2, 2024.3, 2025.1
  and 2025.2 as of writing, resolved from JetBrains' own testable-builds list
  so the set advances on its own. An API removed in an intermediate major and
  restored later used to slip through. Five IDE downloads instead of two, which
  the weekly schedule absorbs.
- ⏸️ **K2 mode does not apply here** — not deferred, simply not a thing to
  test. K2 changes the Kotlin plugin's analysis engine; RuleScribe declares
  only `com.intellij.modules.platform`, depends on no Kotlin plugin, and uses
  no Kotlin PSI or analysis API. Being *written* in Kotlin is a compile-time
  fact with no bearing on it. Adding a "K2 run" would be theatre.
- ⏸️ **Migration to the IntelliJ Platform Gradle Plugin 2.x — deferred.**
  1.x works on Gradle 8.14.5, which is what CI runs, so nothing is broken
  today. What makes it worth waiting for a release of its own: 2.x renames the
  plugin id, restructures `intellij {}` into `intellijPlatform {}`, and changes
  how `runIde`, `buildPlugin`, `signPlugin`, `publishPlugin`,
  `runPluginVerifier` and `patchPluginXml` are configured — a large share of
  the ~200 commented lines in `build.gradle.kts`. And the publish pipeline has
  no dry run: the workflow fires on a tag, and a manual dispatch would publish
  to the Marketplace, so the only full rehearsal is a real release. When it
  happens it ships alone, with each task checked individually rather than as
  one green `gradlew` invocation.

  **Qodana stays parked behind it.** It was removed because its container
  ships a JDK that Gradle 8.14.5 cannot run under, which silently broke project
  resolution and produced 92% false positives. A Gradle 9 toolchain removes
  that incompatibility. It did earn its keep once — it found four dead
  functions before being removed.
- ✅ **Move rule to another file**, on F6. The obstacle was never the
  plumbing, it was the semantics: rule references are **by name and soft**, so
  a move changes no reference text at all — only whether those references
  still resolve. `KrakenMoveConflicts` computes, before anything is written,
  which references would stop resolving, counting `Import Rule` as its own
  axis: an import naming the destination namespace keeps working, one naming
  the old namespace does not. The user is warned with that count and can
  cancel; nothing is written until they accept.

  Built on `MoveHandlerDelegate.tryToMove` rather than `doMove`, since the
  platform has no natural destination container to offer for a rule — the
  handler runs the whole flow and picks the target with a `TreeFileChooser`
  restricted to `.rules`. `WriteCommandAction`, not `WriteAction`: the
  platform refuses document edits outside a command, and the command is what
  makes the move undoable. Insertion precedes deletion, so a failure leaves
  the rule duplicated — which an inspection already reports — rather than
  nowhere.

  Tests assert **resolution** after the move, never text, including the case
  where a reference keeps its exact wording and stops resolving.

- ⏸️ *Extract rule* stays deferred because it is still underspecified, not
  because it is hard. The phrase covers two different features: pulling an
  inline rule out of a `Rules { }` block into a top-level declaration (mostly
  formatting), or lifting a repeated KEL expression into a `Function` (real
  value, and the grammar already supports `Function f(params) : T { expr }`) —
  but that one is Extract **function**. Pick one deliberately before writing
  code.

- ⏸️ *Move EntryPoint* follows the same shape as Move rule and is a small
  step from it, once the rule case has been used in anger.
- ✅ `publish.yml` uses `actions/attest` instead of the deprecated
  `actions/attest-sbom`. The generic action makes the predicate type explicit
  where the old one implied it; its value is fixed by contract, since the
  release notes publish it in the verification command and every attestation
  already emitted carries it. Verified against the published v0.12.0 artifact
  before changing anything: `gh attestation verify … --predicate-type
  https://cyclonedx.org/bom` passes. The step runs before `publishPlugin`, so
  a mistake here aborts a release rather than shipping a broken one.

## Future / exploratory

- EntryPoint → Rule dependency graph visualization.
- Rule runner: execute an EntryPoint against a test JSON payload from the
  IDE (RunLineMarkerContributor + RunConfiguration + kraken-engine
  process + results tool window). Large effort, revisit once the above
  is stable.

### Decision Table (`.dtables`) — basic editor support

The "Kraken Rules & Decision Tables" training deck describes a second DSL:
the Decision Table Framework (DTF), an EIS/Genesis product used to represent
dimensional Kraken rules in a compact tabular form. Unlike `.rules`, DTF is
not part of the public eisgroup/kraken-rules repository — no published
grammar, no public test corpus, just a handful of example snippets in an
internal slide deck. That's the same gap that caused three failed releases
of function discovery (see "Function validation (removed)" above), so scope
this to what a lexer-level highlighter can get right without a verified
grammar behind it.

Structure shown in the deck:

```
@EntryPoint("dataGather", "issue", "propose")
@Category("KrakenRules")
Table "Limit Amounts" {
    InputColumn "EntryPoint" : entryPoint
    AspectColumn "Entity" : entity
    AspectColumn "Min" : min
    AspectColumn "Max" : max
}
```

**Confident starting scope:**
- Syntax highlighting for `Table`, `InputColumn`, `AspectColumn`, `Column`,
  the `@EntryPoint(...)` / `@Category(...)` / `@RuleOrder` annotations,
  string literals, and dotted paths (`dimension.age`, `aspect.score`) — a
  hand-written lexer in the same style as `KrakenLexer`. Highlighting alone
  doesn't need a grammar or PSI tree.
- Code folding on `Table "Name" { ... }` blocks, same brace-matching
  approach already used for `.rules`.
- Structure view listing the `Table` declarations in a file.
- Completion for the aspect names the deck documents by name (`default`,
  `min`, `max`, `mandatory`, `visible`, `accessible`, `entryPoints`,
  `applicability`, `relationshipType`) — these come from the training
  material itself, not from inference.

**Deliberately deferred**, until a real (or anonymized) `.dtables` file
exists to check assumptions against:
- A real BNF grammar and PSI tree. Worth building once the deck's handful of
  examples can be checked against actual files, not before.
- Code Vision or any cross-reference between a `Table` and the Rules Model
  that lists it (`.grules` files: `Model X`, `Namespace Y`, `EntryPoints
  [...]`, `Decision Tables [...]`) — a related but separate file format the
  deck also mentions, not scoped here.
- Any inspection that claims correctness (unknown aspect, bad column
  reference). Same trap as the removed function-discovery inspection: don't
  ship a check nobody here can verify against a real project.

## Deferred / not currently planned

Anything not listed above and not requested by users.

### Type grammar gaps

`Value.g4` defines the engine's type production as `identifier`,
`( type )` (`#PlainTypePrecedence`), `type[]` (`#ArrayType`),
`type | type` (`#UnionType`) and `<identifier>` (`#GenericType`).
RuleScribe's `type_ref` covers only the first, the array suffix, and a
differently-shaped generic:

```
type_ref ::= id (LT type_ref (COMMA type_ref)* GT)? (LBRACKET RBRACKET)?
```

So a union type fails to parse, verified against the real parser:
`Function GetDay(Date | DateTime d) : Number { 1 }`, the same in return
position, and in a bare signature. This matters more than it looks: the
plugin's own bundled catalogue carries `"type": "Date | DateTime"` for three
built-ins, and `KrakenType.fromDslName` already splits on `|` — the type layer
knows about unions, the grammar does not. Redeclaring such a signature in DSL
therefore gets a syntax error on valid Kraken.

The 103/103 corpus check does not catch this: no corpus `.rules` file declares
a union parameter, because those unions live in Java-annotated built-ins
rather than in DSL source.

**Closed.** `type_ref` now covers the whole production, unrolled into three
levels since Grammar-Kit descends recursively and `Value.g4` is left-recursive:
`type_ref → array_type (PIPE array_type)*`, `array_type → atom_type ([])*`,
`atom_type → ( type ) | <id> | id`. Order gives the precedence, so `[]` binds
tighter than `|`, as in the official grammar. `Name<A, B>` is not in `Value.g4`
but was always accepted, and stays — the parser is deliberately more permissive
than the engine.

`|` needed its own token. It was lexed as `OP`, and Grammar-Kit cannot match
"an `OP` whose text is `|`"; `PIPE` mirrors `OP_PIPE` in `Common.g4`. `||` is
still a two-character `OP`, caught before the single-character table — without
that ordering it would have split into two `PIPE`. `binary_op` accepts `PIPE`
so `a | b` keeps parsing as an expression.

### Function validation (removed)

v0.10.1 through v0.10.3 tried to make the "unknown function" inspection aware
of a project's own `@Native` Java functions and of functions declared in a
Maven dependency JAR, so calls to them wouldn't be misreported. Three
attempts, each fixing what the previous one got wrong (regex scan over
project sources, then an annotation-index search over the whole classpath,
then resolving classes registered in
`META-INF/services/kraken.el.functionregistry.FunctionLibrary`, the mechanism
the engine itself uses) — and the last of those still came back empty on a
real enterprise project the plugin was tested against, with no way to tell
whether that meant the target library isn't SPI-registered, or something else
entirely.

The `KrakenUnknownFunctionInspection` and the `KrakenProjectFunctions` /
`KrakenLibraryFunctions` discovery code have been removed rather than kept in
a half-working state. The problem isn't the search technique — it's that
verifying *any* discovery mechanism needs a real project with a real custom
Java function library to test against, and confidential proprietary source
can't be shared into this repo to build or debug that. Without one, every
attempt is a guess validated only against a synthetic fixture, which is
exactly the gap that let three releases ship without actually fixing the
reported case.

**Revisit only** with access to a non-confidential project (or a
consenting, anonymizable customer project) that declares custom Kraken Java
functions, so a fix can be verified against it directly instead of shipped
on faith. Until then, there is no "unknown function" inspection at all — a
typo'd function name is not flagged either, not just a legitimate custom one.
Completion, parameter info, quick documentation, navigation and highlighting
for the engine's 55 built-in functions and for DSL-declared `Function`s are
unaffected; they never depended on this inspection.
