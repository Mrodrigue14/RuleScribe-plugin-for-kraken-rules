package com.kraken.plugin.types

/**
 * KEL type, ported from `kraken.el.scope.type.Type`.
 *
 * Seven native types plus two special cases that drive the checker's caution:
 *
 * - [Any], the dynamic type: everything is assignable to and comparable with it, and
 *   the engine never reports anything about it.
 * - [Unknown], "could not infer": the engine has no such type because it has the full
 *   model. It marks expressions the plugin cannot type, and any check that meets it
 *   abstains.
 *
 * The DSL names field types differently from KEL: `Integer` and `Decimal` are both
 * `Number` (`TypeBuilder.toPrimitiveType`).
 */
sealed class KrakenType {

    object Boolean : KrakenType()
    object String : KrakenType()
    object Number : KrakenType()
    object Money : KrakenType()
    object Date : KrakenType()
    object DateTime : KrakenType()
    object TypeToken : KrakenType()

    /** Dynamic type: accepts and compares with anything. */
    object Any : KrakenType()

    /** Not inferred. Any check that meets it must abstain. */
    object Unknown : KrakenType()

    data class Context(val name: kotlin.String) : KrakenType()

    data class Array(val element: KrakenType) : KrakenType()

    val isDynamic: kotlin.Boolean get() = this == Any
    val isKnown: kotlin.Boolean get() = this != Unknown

    /** Display name, as the engine writes it in messages. */
    fun displayName(): kotlin.String = when (this) {
        is Array -> element.displayName() + "[]"
        is Context -> name
        TypeToken -> "Type"
        else -> this::class.simpleName ?: "Unknown"
    }

    /**
     * `Type.isAssignableFrom`: Money narrows to Number, never the reverse. Anything
     * involving [Any] or [Unknown] passes.
     */
    fun isAssignableFrom(other: KrakenType): kotlin.Boolean = when {
        isDynamic || other.isDynamic -> true
        !isKnown || !other.isKnown -> true
        this == Number && other == Money -> true
        this is Array && other is Array -> element.isAssignableFrom(other.element)
        else -> this == other
    }

    /**
     * `Type.isComparableWith`: ordering (`<`, `>`, `<=`, `>=`) only applies to numbers,
     * dates, and date-times among themselves. Date and DateTime are not comparable.
     *
     * There is deliberately no `this == other` fallback: the engine rejects `a < b` on two
     * `String`s. Equality accepts any mutually assignable types; see [isAssignableFrom].
     */
    fun isComparableWith(other: KrakenType): kotlin.Boolean = when {
        isDynamic || other.isDynamic -> true
        !isKnown || !other.isKnown -> true
        areNumeric(this, other) -> true
        this == Date && other == Date -> true
        this == DateTime && other == DateTime -> true
        else -> false
    }

    companion object {
        private fun areNumeric(a: KrakenType, b: KrakenType): kotlin.Boolean = (a == Number || a == Money) && (b == Number || b == Money)

        /**
         * KEL type of a name written in the DSL: a context field (`Decimal premium`), a
         * function signature (`Number[]`), or a parameter.
         */
        fun fromDslName(raw: kotlin.String): KrakenType {
            val name = raw.trim()
            if (name.endsWith("[]")) return Array(fromDslName(name.removeSuffix("[]")))
            // Unions are not modelled; pretending otherwise would produce false diagnostics.
            if (name.contains('|')) return Any
            if (name.startsWith("<")) return Any // unresolved generic
            // The DSL is case-insensitive: the official corpus writes String, STRING and string.
            return when (name.lowercase()) {
                "boolean" -> Boolean
                "string" -> String
                "number", "integer", "decimal" -> Number
                "money" -> Money
                "date" -> Date
                "datetime" -> DateTime
                "type" -> TypeToken
                "any" -> Any
                "unknown" -> Unknown
                "" -> Unknown
                else -> Context(name)
            }
        }
    }
}
