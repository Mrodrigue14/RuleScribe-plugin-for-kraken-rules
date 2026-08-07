# Roadmap — RuleScribe for Kraken Rules

## Current — v0.10.4

Shipped: full KEL expression grammar, stub-based Rule index, strict
namespace-aware resolution (Namespace/Include/Import Rule) with a cached
visibility model, bidirectional Ctrl+Click navigation that offers every valid
target, EntryPoint references with rename support, quick documentation,
structure view with distinct icons, folding, formatter, live templates,
12 inspections, function support inside rule bodies (built-in catalogue,
completion, parameter info, quick documentation, navigation, highlighting),
inlays on every declaration showing usage count and last author, identifier
resolution inside rule bodies, and KEL type inference. Published on the
JetBrains Marketplace, signed and shipped with SLSA build provenance and a
CycloneDX SBOM. See plugin.xml change notes for the detailed history.
See [Function validation (removed)](#function-validation-removed) below.

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

## v0.11.0 — Diagnostics that match the engine

Today every inspection message is worded by hand, so the IDE and the build say
different things about the same defect. The engine publishes two coded
catalogues we can align to:

- `kraken.model.project.validator.ValidationMessageBuilder` — 111 design-time
  codes (`kvr` rules, `kvf` functions, `kvc` contexts, `kve`, `kvx`, `kvn`,
  `kvd`), each with a message template and a severity (99 ERROR, 10 WARNING,
  3 INFO).
- `kraken.message.SystemMessageBuilder` — 56 runtime/conversion codes (`kbs`).

Work:

- Carry the engine's code and wording on every inspection that has an
  equivalent, so a developer reads the same message in the IDE and in the build
  log. Where RuleScribe reports something the engine does not, say so plainly
  rather than inventing a code.
- Take severity from the engine's declaration instead of choosing one.
- **Tighten the operator token.** `&&` and `||` already parse — the lexer scans
  a maximal run of `+-=!?|&%^~` into a single `OP`, and `binary_op` accepts it.
  The problem is the opposite of a missing feature: `a &|&~ b` parses cleanly
  too. Replacing the blanket run with the operator set the official grammar
  actually defines (`Common.g4`) turns operator typos into a precise parse
  error at the right offset instead of a confusing failure further down the
  expression. Small change, and the main lever on parse-error quality.
- Review `pin=` placement in the BNF for the error messages it produces — pin
  position is what decides whether a broken expression reports one clear error
  or a cascade.

## v0.12.0 — Editor polish

- Quick-fixes for existing inspections (e.g. add missing `@Dimension`,
  add differentiating dimension on duplicate rules) — currently
  report-only.
- Semantics for Function generic bounds (`<T is SomeType>`) — currently
  parsed but inert.
- Spellchecker support inside strings (descriptions, messages).
- Semantic highlighting: visually distinguish resolved vs. unresolved
  references beyond the inspection squiggle.
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

## v1.0.0 — Stabilization

- Extend IntelliJ Platform compatibility testing (K2 mode, newer
  2024.x/2025.x builds) beyond the single pinned 2024.1.7 target.
- Migrate to the IntelliJ Platform Gradle Plugin 2.x (the 1.x line is already
  incompatible with Gradle 9). Once that lands, **re-evaluate Qodana**: it was
  removed because its container ships a JDK that Gradle 8.14.5 cannot run
  under, which silently broke project resolution and produced 92% false
  positives. A Gradle 9 toolchain removes that incompatibility. Qodana did earn
  its keep once — it found four dead functions before being removed.
- Refactorings: Extract rule, Move rule/EntryPoint to another
  namespace or file.
- `publish.yml`: replace the deprecated `actions/attest-sbom` with
  `actions/attest` (flagged by GitHub Actions since the v0.7.2 release run;
  no functional impact yet, just a deprecation warning).

## Future / exploratory

- EntryPoint → Rule dependency graph visualization.
- Rule runner: execute an EntryPoint against a test JSON payload from the
  IDE (RunLineMarkerContributor + RunConfiguration + kraken-engine
  process + results tool window). Large effort, revisit once the above
  is stable.

## Deferred / not currently planned

Anything not listed above and not requested by users.

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
