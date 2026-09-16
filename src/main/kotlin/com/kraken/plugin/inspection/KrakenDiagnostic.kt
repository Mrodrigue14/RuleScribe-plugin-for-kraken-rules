package com.kraken.plugin.inspection

import java.text.MessageFormat

/**
 * Code and message of each diagnostic, copied from the Kraken engine so that the IDE
 * and the build describe a defect in the same words.
 *
 * The engine has two coded catalogues:
 * `kraken.model.project.validator.ValidationMessageBuilder` (`kv…`, design-time
 * validation) and `kraken.message.SystemMessageBuilder` (`kbs…`, project build). Their
 * `MessageFormat` patterns are copied verbatim; the bracketed code is what a developer
 * finds in the build log.
 *
 * Expression errors: the engine wraps `AstValidatingVisitor` messages, which have no
 * code, in `kvr049` (`{0} expression has error in ''{1}''. {2}`). The editor underline
 * already shows where, so only the code and the inner message are kept.
 *
 * Functions: the DSL writes a `Function` the same way with or without a body, but the
 * engine validates `Function` and `FunctionSignature` with different codes (a duplicate
 * generic bound is `kvf004` with a body, `kvf017` without). The body picks the code;
 * any other code would not match the build log.
 *
 * Engine quirks kept on purpose:
 *
 * - [IMPORT_AMBIGUOUS] uses `kbs027`, not `kbs028`: `kbs028` is declared but never
 *   used, and `ResourceKrakenProjectBuilder.validateRuleImportAmbiguity` emits
 *   `kbs027`. The message is RuleScribe's own, because the engine reuses the name
 *   clash pattern with ambiguity arguments and produces a garbled sentence.
 * - [SIGNATURE_PARAMETER_TYPE_UNION_GENERIC_MIX] uses `kvf021`, not `kvf022`: `kvf022`
 *   is declared but never used, and `FunctionSignatureValidator.validateParameters`
 *   emits `kvf021` (the unknown type code) for the union/generic mix. The message
 *   describes the mix, since "does not exist" would describe another defect.
 *
 * Checks the engine does not perform (unreferenced rule, undeclared dimension) have no
 * code: inventing one would look consistent and be wrong.
 */
internal enum class KrakenDiagnostic(val code: String, private val template: String) {

    // ValidationMessageBuilder: design-time validation.
    RULE_NAME_IS_NULL("kvr001", "Rule name is not defined."),
    RULE_TARGET_CONTEXT_UNKNOWN("kvr027", "Missing context definition with name ''{0}''."),
    DUPLICATE_RULE_VERSION("kvr053", "Rule version has duplicates. Rule version is uniquely identified by rule name and dimensions."),
    ENTRYPOINT_UNKNOWN_INCLUDE("kve002", "Included entry point ''{0}'' does not exist."),
    ENTRYPOINT_UNKNOWN_RULE("kve005", "Rule is included in entry point, but such rule does not exist: {0}."),

    // FunctionValidator: `Function` declaration with a body.
    FUNCTION_NATIVE_DUPLICATE("kvf003", "Function is not valid because native function with the same name exists: {0}."),
    FUNCTION_GENERIC_BOUND_DUPLICATE("kvf004", "Function is not valid because there are more than one generic bound for the same generic type name: {0}."),
    FUNCTION_GENERIC_BOUND_IS_ITSELF_GENERIC("kvf005", "Function is not valid because generic type bound ''{0}'' for generic ''{1}'' is itself a generic type."),
    FUNCTION_RETURN_TYPE_UNION_GENERIC_MIX("kvf007", "Function is not valid because return type ''{0}'' is a mix of union type and generic type. Such type definition is not supported."),
    FUNCTION_PARAMETER_DUPLICATE("kvf008", "Function is not valid because there are more than one parameter with the same name defined: {0}."),
    FUNCTION_PARAMETER_TYPE_UNION_GENERIC_MIX("kvf010", "Function is not valid because parameter type ''{0}'' is a mix of union type and generic type. Such type definition is not supported."),

    // FunctionSignatureValidator: the same syntax without a body.
    SIGNATURE_GENERIC_BOUND_DUPLICATE("kvf017", "Function signature is not valid because there are more than one generic bound for the same generic type name: {0}."),
    SIGNATURE_GENERIC_BOUND_IS_ITSELF_GENERIC("kvf018", "Function signature is not valid because generic type bound ''{0}'' for generic ''{1}'' is itself a generic type."),
    SIGNATURE_RETURN_TYPE_UNION_GENERIC_MIX("kvf020", "Function signature is not valid because return type ''{0}'' is a mix of union type and generic type. Such type definition is not supported."),
    SIGNATURE_PARAMETER_TYPE_UNION_GENERIC_MIX("kvf021", "Function signature is not valid because parameter type ''{0}'' is a mix of union type and generic type. Such type definition is not supported."),

    // AstValidatingVisitor messages, wrapped by kvr049.
    REFERENCE_NOT_FOUND("kvr049", "Reference ''{0}'' not found."),
    NOT_COMPARABLE("kvr049", "Operation {0} can only be performed on comparable types, but was performed on ''{1}'' and ''{2}''."),
    NOT_SAME_TYPE("kvr049", "Both sides of operator ''{0}'' must have same type, but left side was of type ''{1}'' and right side was of type ''{2}''."),
    INCOMPATIBLE_PARAMETER("kvr049", "Incompatible type ''{0}'' of function parameter at index {1} when invoking function {2}. Expected type is ''{3}''."),

    // SystemMessageBuilder: project build.
    IMPORT_UNKNOWN_RULE("kbs025", "Cannot import rule ''{0}'' from namespace ''{1}'' to ''{2}'', because rule does not exist."),
    IMPORT_UNKNOWN_NAMESPACE("kbs026", "Cannot import rule ''{0}'' from namespace ''{1}'' to ''{2}'', because namespace does not exist."),
    IMPORT_DUPLICATE("kbs027", "Cannot import rule ''{0}'' from namespace ''{1}'' to ''{2}'', because rule is already defined."),
    IMPORT_AMBIGUOUS("kbs027", "Cannot import rule ''{0}'' to ''{1}'', because it is imported from multiple namespaces: {2}."),
    ;

    /** Display message prefixed with the engine code. */
    fun format(vararg args: Any?): String = "[$code] " + MessageFormat.format(template, *args)
}
