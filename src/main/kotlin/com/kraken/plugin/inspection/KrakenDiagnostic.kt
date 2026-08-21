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
 * **Fonctions : deux catalogues pour une seule syntaxe.** Le DSL écrit une
 * `Function` de la même façon qu'elle ait un corps ou non, mais le moteur en
 * fait deux objets distincts — `Function` et `FunctionSignature` — validés par
 * deux classes aux codes différents. Une borne générique dupliquée est `kvf004`
 * dans le premier cas et `kvf017` dans le second. La présence du corps est donc
 * ce qui choisit le code ; s'en dispenser afficherait dans l'IDE un code que le
 * log de build ne contient pas, ce qui est pire que de n'en afficher aucun.
 *
 * **Trois bizarreries du moteur, assumées telles quelles.** [IMPORT_AMBIGUOUS]
 * porte `kbs027` et non `kbs028` : `kbs028` est déclaré dans
 * `SystemMessageBuilder` mais n'est référencé nulle part, et
 * `ResourceKrakenProjectBuilder.validateRuleImportAmbiguity` émet `kbs027`.
 * C'est donc `kbs027` qui apparaît dans le log de build pour ce cas. Son
 * libellé, en revanche, est propre à RuleScribe : le moteur réutilise le motif
 * de la collision en lui passant des arguments d'ambiguïté, ce qui produit une
 * phrase incohérente qu'il ne servirait à rien de recopier.
 *
 * [SIGNATURE_PARAMETER_TYPE_UNION_GENERIC_MIX] porte `kvf021` et non `kvf022` :
 * `kvf022` est déclaré dans `ValidationMessageBuilder` mais n'est référencé
 * nulle part, et `FunctionSignatureValidator.validateParameters` émet `kvf021`
 * — le code du type *inconnu* — dans sa branche du mélange union/générique.
 * C'est donc `kvf021` qui apparaît dans le log. Son libellé, en revanche, est
 * celui du mélange : recopier « does not exist » décrirait un autre défaut que
 * celui qui est souligné.
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

    // FunctionValidator — déclaration `Function` avec corps.
    FUNCTION_NATIVE_DUPLICATE("kvf003", "Function is not valid because native function with the same name exists: {0}."),
    FUNCTION_GENERIC_BOUND_DUPLICATE("kvf004", "Function is not valid because there are more than one generic bound for the same generic type name: {0}."),
    FUNCTION_GENERIC_BOUND_IS_ITSELF_GENERIC("kvf005", "Function is not valid because generic type bound ''{0}'' for generic ''{1}'' is itself a generic type."),
    FUNCTION_RETURN_TYPE_UNION_GENERIC_MIX("kvf007", "Function is not valid because return type ''{0}'' is a mix of union type and generic type. Such type definition is not supported."),
    FUNCTION_PARAMETER_DUPLICATE("kvf008", "Function is not valid because there are more than one parameter with the same name defined: {0}."),
    FUNCTION_PARAMETER_TYPE_UNION_GENERIC_MIX("kvf010", "Function is not valid because parameter type ''{0}'' is a mix of union type and generic type. Such type definition is not supported."),

    // FunctionSignatureValidator — même syntaxe, mais sans corps (voir la KDoc).
    SIGNATURE_GENERIC_BOUND_DUPLICATE("kvf017", "Function signature is not valid because there are more than one generic bound for the same generic type name: {0}."),
    SIGNATURE_GENERIC_BOUND_IS_ITSELF_GENERIC("kvf018", "Function signature is not valid because generic type bound ''{0}'' for generic ''{1}'' is itself a generic type."),
    SIGNATURE_RETURN_TYPE_UNION_GENERIC_MIX("kvf020", "Function signature is not valid because return type ''{0}'' is a mix of union type and generic type. Such type definition is not supported."),
    SIGNATURE_PARAMETER_TYPE_UNION_GENERIC_MIX("kvf021", "Function signature is not valid because parameter type ''{0}'' is a mix of union type and generic type. Such type definition is not supported."),

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
