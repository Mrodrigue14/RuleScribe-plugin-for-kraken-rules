package com.kraken.plugin.inspection

import java.text.MessageFormat

/**
 * Code et libellé de chaque diagnostic, repris du moteur Kraken.
 *
 * Sans cela, l'IDE et le build décrivent le même défaut avec des mots
 * différents, et rien ne relie les deux. Le moteur publie deux catalogues
 * codés — `kraken.model.project.validator.ValidationMessageBuilder` (codes
 * `kv…`, validation design-time) et `kraken.message.SystemMessageBuilder`
 * (codes `kbs…`, construction du projet) — dont les libellés sont des motifs
 * `MessageFormat`. On les recopie tels quels : le code préfixé entre crochets
 * est la clé que le développeur retrouve dans le log de build.
 *
 * **Erreurs d'expression.** Le moteur enveloppe les messages de
 * `AstValidatingVisitor` (qui n'ont pas de code propre) dans `kvr049`, dont le
 * motif est `{0} expression has error in ''{1}''. {2}` : le mot-clé du payload,
 * l'expression entière, puis le message. Dans un éditeur, le soulignement
 * indique déjà l'endroit, donc on garde le code et le message interne et on
 * laisse tomber l'enveloppe.
 *
 * **Deux bizarreries du moteur, assumées telles quelles.** [IMPORT_AMBIGUOUS]
 * porte `kbs027` et non `kbs028` : `kbs028` est déclaré dans
 * `SystemMessageBuilder` mais n'est référencé nulle part, et
 * `ResourceKrakenProjectBuilder.validateRuleImportAmbiguity` émet `kbs027`.
 * C'est donc `kbs027` qui apparaît dans le log de build pour ce cas. Son
 * libellé, en revanche, est propre à RuleScribe : le moteur réutilise le motif
 * de la collision en lui passant des arguments d'ambiguïté, ce qui produit une
 * phrase incohérente qu'il ne servirait à rien de recopier.
 *
 * Les vérifications que le moteur ne fait pas (règle jamais référencée,
 * dimension non déclarée) n'ont pas de code : leur inventer un aurait l'air
 * cohérent et serait faux.
 */
internal enum class KrakenDiagnostic(val code: String, private val template: String) {

    // ValidationMessageBuilder — validation design-time.
    RULE_NAME_IS_NULL("kvr001", "Rule name is not defined."),
    RULE_TARGET_CONTEXT_UNKNOWN("kvr027", "Missing context definition with name ''{0}''."),
    DUPLICATE_RULE_VERSION("kvr053", "Rule version has duplicates. Rule version is uniquely identified by rule name and dimensions."),
    ENTRYPOINT_UNKNOWN_INCLUDE("kve002", "Included entry point ''{0}'' does not exist."),
    ENTRYPOINT_UNKNOWN_RULE("kve005", "Rule is included in entry point, but such rule does not exist: {0}."),

    // AstValidatingVisitor, enveloppés par kvr049 (voir la KDoc).
    REFERENCE_NOT_FOUND("kvr049", "Reference ''{0}'' not found."),
    NOT_COMPARABLE("kvr049", "Operation {0} can only be performed on comparable types, but was performed on ''{1}'' and ''{2}''."),
    NOT_SAME_TYPE("kvr049", "Both sides of operator ''{0}'' must have same type, but left side was of type ''{1}'' and right side was of type ''{2}''."),
    INCOMPATIBLE_PARAMETER("kvr049", "Incompatible type ''{0}'' of function parameter at index {1} when invoking function {2}. Expected type is ''{3}''."),

    // SystemMessageBuilder — construction du projet.
    IMPORT_UNKNOWN_RULE("kbs025", "Cannot import rule ''{0}'' from namespace ''{1}'' to ''{2}'', because rule does not exist."),
    IMPORT_UNKNOWN_NAMESPACE("kbs026", "Cannot import rule ''{0}'' from namespace ''{1}'' to ''{2}'', because namespace does not exist."),
    IMPORT_DUPLICATE("kbs027", "Cannot import rule ''{0}'' from namespace ''{1}'' to ''{2}'', because rule is already defined."),
    IMPORT_AMBIGUOUS("kbs027", "Cannot import rule ''{0}'' to ''{1}'', because it is imported from multiple namespaces: {2}.");

    /** Libellé prêt à afficher, préfixé du code du moteur. */
    fun format(vararg args: Any?): String = "[$code] " + MessageFormat.format(template, *args)
}
