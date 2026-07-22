# Roadmap — RuleScribe for Kraken Rules

## Current — v0.5.5

Shipped: full KEL expression grammar, stub-based Rule index, strict
namespace-aware resolution (Namespace/Include), EntryPoint references with
rename support, reverse navigation, quick documentation, structure view
with distinct icons, folding, formatter, live templates, 6 inspections.
Published on the JetBrains Marketplace. See plugin.xml change notes for
the detailed history.

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

## v0.7.0 — Performance foundations

Lay the groundwork before type-checking makes resolution much more
frequent.

- Stub index for Context, EntryPoint, Dimension (today only Rule has one).
- CachedValuesManager around namespace-visibility computation.
- Perf test on a synthetic 500-file project.

## v0.8.0 — KEL type-checking

Port the reference algorithm from kraken-expression-language
(kraken.el.ast.validation.AstValidatingVisitor) rather than reinventing it.

- 7 native types (Boolean, String, Number, Money, Date, DateTime, Type)
  plus Any (dynamic) and Unknown; Money widens to Number one-way; Date
  and DateTime are NOT cross-comparable.
- Mirror the engine's 3-tier severity: ERROR (blocks evaluation),
  WARNING, INFO — not a blanket soft-warning mode.
- Type-aware field completion backed by inferred types instead of raw
  PSI shape.
- Validate against the 103-file corpus via tools/sim_parser.py.

## v0.9.0 — Editor polish

- Quick-fixes for existing inspections (e.g. add missing `@Dimension`,
  add differentiating dimension on duplicate rules) — currently
  report-only.
- Semantics for Function generic bounds (`<T is SomeType>`) — currently
  parsed but inert.
- Spellchecker support inside strings (descriptions, messages).
- Semantic highlighting: visually distinguish resolved vs. unresolved
  references beyond the inspection squiggle.

## v1.0.0 — Stabilization

- Extend IntelliJ Platform compatibility testing (K2 mode, newer
  2024.x/2025.x builds) beyond the single pinned 2024.1.7 target.
- Refactorings: Extract rule, Move rule/EntryPoint to another
  namespace or file.

## Future / exploratory

- EntryPoint → Rule dependency graph visualization.
- Rule runner: execute an EntryPoint against a test JSON payload from the
  IDE (RunLineMarkerContributor + RunConfiguration + kraken-engine
  process + results tool window). Large effort, revisit once the above
  is stable.

## Deferred / not currently planned

Anything not listed above and not requested by users.
