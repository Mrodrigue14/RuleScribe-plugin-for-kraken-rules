# Guide de test manuel

Lancer un IDE sandbox avec le plugin : `.\gradlew.bat runIde` (Windows) ou
`./gradlew runIde` (Linux/macOS), puis ouvrir ce projet et le dossier `examples/`.

## Tests automatisés

```bash
.\gradlew.bat test    # 93 tests : parser, complétion, inspections, navigation
                      # inter-fichiers et bidirectionnelle, renommage,
                      # namespaces, quick doc, fonctions, inlays d'usages
```

## Checklist manuelle (dossier examples/multi/)

Le dossier `examples/multi/` est un mini-projet Kraken multi-fichiers :
contextes, règles et EntryPoints dans des fichiers séparés, trois namespaces
(`Policy` inclut `Base` ; `Other` est isolé).

### Navigation (policy-entrypoints.rules)
- [ ] Ctrl+clic sur `"Policy code mandatory"` → ouvre **policy-rules.rules** sur la règle
- [ ] Ctrl+clic sur `"Base sanity check"` → ouvre **base.rules** (namespace inclus)
- [ ] `"Je n'existe pas"` et `"Hidden elsewhere"` sont soulignés (références inconnues —
      `Other` n'est pas inclus par `Policy`)
- [ ] Dans **policy-rules.rules** : icône de gouttière sur chaque règle référencée →
      clic = navigation vers les items d'EntryPoint
- [ ] Dans **other.rules** : `"Hidden elsewhere"` n'a PAS d'icône de gouttière et
      est signalée « not referenced » — sa seule référence vient d'un namespace
      qui ne la voit pas (sémantique stricte)

### Navigation bidirectionnelle (plusieurs cibles)
- [ ] Ctrl+clic sur `"Postal code default"` dans `EntryPoint "Defaults"` → popup à
      **deux** entrées, une par variante `@Dimension` (`"state", "CA"` et
      `"state", "NY"`), l'annotation étant affichée sur chaque ligne
- [ ] Ctrl+clic sur le nom `"Policy code mandatory"` **dans sa déclaration**
      (policy-rules.rules) → popup listant `EntryPoint "Validation"` et
      `EntryPoint "Quick check"`, chacun avec son fichier
- [ ] Idem sur `"Base sanity check"` dans **base.rules** : les deux EntryPoints
      de `Policy` apparaissent (le namespace inclut `Base`)
- [ ] Ctrl+clic sur `EntryPoint "Validation"` imbriqué dans `"Defaults"` → saute
      à la déclaration de `"Validation"` dans le même fichier

### Inlays d'usages (toutes les déclarations)
- [ ] Au-dessus de chaque `Rule`, `EntryPoint` et `Function` : un inlay gris
      « N usages » (ou « no usages »)
- [ ] Clic sur l'inlay → popup standard d'usages, groupée par fichier avec
      l'aperçu du code
- [ ] Ctrl+B sur le NOM d'une déclaration → la même popup (et non plus la liste
      plate de libellés d'avant v0.8.1)
- [ ] `"Hidden elsewhere"` dans **other.rules** affiche « no usages » : sa seule
      référence vient d'un namespace qui ne la voit pas
- [ ] Ctrl+B sur un item d'EntryPoint → inchangé : saut direct, ou popup des
      variantes `@Dimension`

### Fonctions (policy-functions.rules et policy-rules.rules)
- [ ] Ctrl+Espace dans un corps de règle → les 55 natives (icône fonction, signature
      en légende) **et** `TotalLimit` / `ResolvePlanCd` du projet
- [ ] Ctrl+Q sur `Round` → description, exemples et « Since », venus du moteur
- [ ] Ctrl+Q sur `TotalLimit` → le commentaire `/** … */`, avec `@since` et `@parameter`
- [ ] Ctrl+Q sur `ResolvePlanCd` → mention « signature only, implemented in Java »
- [ ] Ctrl+P entre les parenthèses de `Round(` → les deux signatures (1 et 2 paramètres)
- [ ] Ctrl+B sur `TotalLimit` depuis policy-rules.rules → policy-functions.rules
- [ ] `Rnd(1.5)` souligné : « Unknown function 'Rnd' »
- [ ] `Round(1.5, 2, 3)` souligné : « Function 'Round' with 3 parameter(s) does not
      exist (declared with 1 or 2) » — le nom existe, c'est l'arité qui ne va pas
- [ ] `Round` et `TotalLimit` ont deux couleurs distinctes (native vs projet) ;
      `Rnd`, non résolu, garde la couleur d'un identifiant ordinaire

### Complétion
- [ ] Dans un `EntryPoint { }` : Ctrl+Espace propose les règles visibles (pas `"Hidden elsewhere"`)
- [ ] Après `On ` : propose `Policy`, `AddressInfo`, `BaseEntity` (namespace inclus)
- [ ] Après `On Policy.` : propose `policyCd`, `state`, `effectiveDate`, `AddressInfo`
      et `id` (hérité de `BaseEntity` via `Is`)
- [ ] Dans `@Dimension(` : propose `"state"` et `"plan"`
- [ ] Corps de règle : propose `Assert`, `Set Mandatory`, `Default To`…

### Édition
- [ ] Alt+7 : Structure View liste contextes, règles, entry points, dimensions
- [ ] Icônes ± dans la gouttière : replier un corps de règle / un bloc
- [ ] Ctrl+Alt+L : réindente le fichier
- [ ] Taper `rule` puis Tab : squelette de règle avec navigation entre variables
      (idem `ep`, `ctx`, `dim`)
- [ ] Ctrl+Q sur un nom de règle : popup avec description, cible et payload

### Refactoring et inspections
- [ ] Maj+F6 sur une règle dans policy-rules.rules : renomme aussi sa référence
      dans policy-entrypoints.rules
- [ ] Supprimer le nom d'une règle → erreur « Rule has no name »
- [ ] Dupliquer une règle sans `@Dimension` → avertissement « Duplicate rule »
- [ ] Une règle jamais référencée → « not referenced by any entry point »
- [ ] `@Dimension("inconnu", "x")` → « Dimension 'inconnu' is not declared »
- [ ] `On ContexteInconnu.x` → « Unknown context »
- [ ] Alt+Entrée dans une règle sans `On` → intention « Add missing 'On' clause »

### Vérifications hors IDE

```bash
python3 tools/validate.py                     # cohérence plugin.xml / BNF / lexer
python3 tools/sim_parser.py                   # grammaire vs fichiers de test
python3 tools/sim_parser.py examples/multi/*.rules
```
