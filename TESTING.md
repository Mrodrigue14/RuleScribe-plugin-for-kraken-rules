# Guide de test manuel

Lancer un IDE sandbox avec le plugin : `.\gradlew.bat runIde` (Windows) ou
`./gradlew runIde` (Linux/macOS), puis ouvrir ce projet et le dossier `examples/`.

## Tests automatisés

```bash
.\gradlew.bat test    # 18 tests : parser, complétion, inspections, navigation
                      # inter-fichiers, renommage, namespaces, quick doc
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
