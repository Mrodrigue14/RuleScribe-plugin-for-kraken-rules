# Manual testing guide

Start a sandbox IDE with the plugin: `.\gradlew.bat runIde` (Windows) or
`./gradlew runIde` (Linux/macOS), then open this project and the `examples/` folder.

## Automated tests

```bash
.\gradlew.bat test    # parser, completion, inspections, cross-file and bidirectional
                      # navigation, rename, namespaces, quick doc, functions, inlays,
                      # expression scopes, types
```

## Manual checklist (examples/multi/)

`examples/multi/` is a small multi-file Kraken project: contexts, rules and
EntryPoints in separate files, and three namespaces (`Policy` includes `Base`;
`Other` is isolated).

### Navigation (policy-entrypoints.rules)
- [ ] Ctrl+click on `"Policy code mandatory"` → opens **policy-rules.rules** at the rule
- [ ] Ctrl+click on `"Base sanity check"` → opens **base.rules** (included namespace)
- [ ] `"Does not exist"` and `"Hidden elsewhere"` are underlined (unknown references;
      `Other` is not included by `Policy`)
- [ ] In **policy-rules.rules**: a gutter icon on each referenced rule → clicking it
      navigates to the EntryPoint items
- [ ] In **other.rules**: `"Hidden elsewhere"` has NO gutter icon and is reported as
      "not referenced": its only reference comes from a namespace that cannot see it
      (strict semantics)

### Bidirectional navigation (several targets)
- [ ] Ctrl+click on `"Postal code default"` in `EntryPoint "Defaults"` → popup with
      **two** entries, one per `@Dimension` variant (`"state", "CA"` and
      `"state", "NY"`), each line showing its annotation
- [ ] Ctrl+click on the name `"Policy code mandatory"` **in its declaration**
      (policy-rules.rules) → popup listing `EntryPoint "Validation"` and
      `EntryPoint "Quick check"`, each with its file
- [ ] Same on `"Base sanity check"` in **base.rules**: both `Policy` EntryPoints
      appear (the namespace includes `Base`)
- [ ] Ctrl+click on the `EntryPoint "Validation"` nested in `"Defaults"` → jumps to
      the `"Validation"` declaration in the same file

### Usage inlays (all declarations)
- [ ] Above each `Rule`, `EntryPoint` and `Function`: a grey "N usages" inlay (or
      "no usages")
- [ ] Clicking the inlay → the standard usages popup, grouped by file with a code
      preview
- [ ] Ctrl+B on a declaration's NAME → the same popup
- [ ] `"Hidden elsewhere"` in **other.rules** shows "no usages": its only reference
      comes from a namespace that cannot see it
- [ ] Ctrl+B on an EntryPoint item → a direct jump, or a popup of the `@Dimension`
      variants

### Author and date inlays (requires a git repository)
- [ ] Next to "N usages": a second inlay with the block's last author
- [ ] Also present on `Context`s, even though they have no usages to count
- [ ] Clicking it → opens the annotation in the gutter, one date per line
- [ ] On a file outside version control: no author inlay, and "N usages" still shows

### Functions (policy-functions.rules and policy-rules.rules)
- [ ] Ctrl+Space in a rule body → the 55 natives (function icon, signature as a hint)
      **and** the project's `TotalLimit` / `ResolvePlanCd`
- [ ] Ctrl+Q on `Round` → description, examples and "Since", from the engine
- [ ] Ctrl+Q on `TotalLimit` → the `/** … */` comment, with `@since` and `@parameter`
- [ ] Ctrl+Q on `ResolvePlanCd` → "signature only, implemented in Java"
- [ ] Ctrl+P inside `Round(` → both signatures (1 and 2 parameters)
- [ ] Ctrl+B on `TotalLimit` from policy-rules.rules → policy-functions.rules
- [ ] `Round` and `TotalLimit` have two distinct colours (native vs project);
      `Rnd`, unresolved, keeps the plain identifier colour

### Identifiers in expressions (policy-rules.rules)
- [ ] Ctrl+B on `effectiveDate` in an `Assert` → the field in policy-contexts.rules
- [ ] Ctrl+B on `Policy` in `When Policy.policyCd != null` → the root context
- [ ] Ctrl+B on `policyCd` in the same `When` → the field, through the access chain
- [ ] In policy-functions.rules, Ctrl+B on `coverages` → the `TotalLimit` parameter
- [ ] Type `Assert whatever > 0` → "[kvr049] Reference 'whatever' not found."
- [ ] Type `Assert Policy.whatever > 0` → **nothing**: a chain segment cannot be
      judged without types
- [ ] Type `Assert Count(Policy.Coverage[limitAmount > 0]) = 1` → Ctrl+B on
      `limitAmount` leads to the `Coverage` field, and nothing is underlined: in a
      filter, the predicate sees the filtered element's fields

### Types (policy-rules.rules)
- [ ] `Assert effectiveDate < Today()` → **nothing**: Date against Date
- [ ] Type `Assert effectiveDate < 2020-01-01T10:00:00Z` → "[kvr049] Operation
      LessThan can only be performed on comparable types…", the classic KEL trap
- [ ] Type `Assert policyCd < policyCd` → the same message: two `String`s cannot be
      ordered either
- [ ] Type `Assert policyCd = policyCd` → **nothing**: equality accepts it
- [ ] Type `Assert Round(policyCd) > 0` → "[kvr049] Incompatible type 'String'
      of function parameter at index 0…"
- [ ] `Assert Round(TotalLimit(Policy.Coverage), 2) > 0` → **nothing**: a call's type
      is its return type, not that of its arguments

### Completion
- [ ] In an `EntryPoint { }`: Ctrl+Space offers the visible rules (not `"Hidden elsewhere"`)
- [ ] After `On `: offers `Policy`, `AddressInfo`, `BaseEntity` (included namespace)
- [ ] After `On Policy.`: offers `policyCd`, `state`, `effectiveDate`, `AddressInfo`
      and `id` (inherited from `BaseEntity` through `Is`)
- [ ] In `@Dimension(`: offers `"state"` and `"plan"`
- [ ] Rule body: offers `Assert`, `Set Mandatory`, `Default To`…

### Editing
- [ ] Alt+7: the Structure View lists contexts, rules, entry points and dimensions
- [ ] ± icons in the gutter: fold a rule body or a block
- [ ] Ctrl+Alt+L: reindents the file
- [ ] Type `rule` then Tab: rule skeleton with navigation between variables
      (also `ep`, `ctx`, `dim`)
- [ ] Ctrl+Q on a rule name: popup with description, target and payload

### Refactoring and inspections
- [ ] Shift+F6 on a rule in policy-rules.rules: also renames its reference in
      policy-entrypoints.rules
- [ ] Delete a rule's name → error "[kvr001] Rule name is not defined."
- [ ] Duplicate a rule without `@Dimension` → warning "[kvr053] Rule version has duplicates…"
- [ ] A rule that is never referenced → "not referenced by any entry point"
- [ ] `@Dimension("unknown", "x")` → "Dimension 'unknown' is not declared"
- [ ] `On UnknownContext.x` → "[kvr027] Missing context definition with name…"
- [ ] Alt+Enter in a rule without `On` → the "Add missing 'On' clause" intention

### Coloured brackets (examples/brackets-broken.rules)
This file is deliberately unbalanced; do not "fix" it.
- [ ] Rule "Nested": the three nesting levels have three distinct colours, none of
      them red
- [ ] Rule "Unclosed paren": the `(` that is never closed is red
- [ ] Rule "Stray close": the extra `)` is red
- [ ] Rule "Mismatched": `Round(limit]` → the mismatched `]` **and** the `(` left
      alone are red
- [ ] Rule "Null safe": nothing in `Coverage?[limit > 0]` is red, since `?[` counts
      as an opener
- [ ] Settings → Editor → Color Scheme → Kraken Rules: the "Rainbow" checkbox turns
      off the depth colours, but unmatched brackets stay red

### Checks outside the IDE

```bash
python3 tools/validate.py                     # plugin.xml / BNF / lexer consistency
python3 tools/sim_parser.py                   # grammar against the test files
python3 tools/sim_parser.py examples/multi/*.rules
```
