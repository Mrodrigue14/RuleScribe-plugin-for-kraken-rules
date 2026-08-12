package com.kraken.plugin.types

/**
 * Type KEL, porté de `kraken.el.scope.type.Type`.
 *
 * Sept types natifs, plus deux cas particuliers qui gouvernent toute la
 * prudence du vérificateur :
 *
 * - [Any] — le type dynamique. Tout lui est assignable et comparable ; le
 *   moteur ne signale jamais rien à son contact.
 * - [Unknown] — « je n'ai pas su déduire ». Il n'existe pas comme tel dans le
 *   moteur, qui dispose du modèle complet ; ici il marque les expressions que
 *   le plugin ne sait pas typer, et toute vérification qui le rencontre
 *   s'abstient.
 *
 * Le DSL nomme ses champs autrement que KEL : `Integer` et `Decimal` sont tous
 * deux des `Number` (`TypeBuilder.toPrimitiveType`).
 */
sealed class KrakenType {

    object Boolean : KrakenType()
    object String : KrakenType()
    object Number : KrakenType()
    object Money : KrakenType()
    object Date : KrakenType()
    object DateTime : KrakenType()
    object TypeToken : KrakenType()

    /** Type dynamique : accepte et se compare à tout. */
    object Any : KrakenType()

    /** Non déduit. Toute vérification qui le rencontre doit s'abstenir. */
    object Unknown : KrakenType()

    /** Un contexte du modèle, désigné par son nom. */
    data class Context(val name: kotlin.String) : KrakenType()

    /** Collection d'éléments de [element]. */
    data class Array(val element: KrakenType) : KrakenType()

    val isDynamic: kotlin.Boolean get() = this == Any
    val isKnown: kotlin.Boolean get() = this != Unknown

    /** Nom affichable, tel que le moteur l'écrirait dans un message. */
    fun displayName(): kotlin.String = when (this) {
        is Array -> element.displayName() + "[]"
        is Context -> name
        TypeToken -> "Type"
        else -> this::class.simpleName ?: "Unknown"
    }

    /**
     * `Type.isAssignableFrom` : Money se rétrécit vers Number, jamais l'inverse.
     * Tout ce qui touche à [Any] ou [Unknown] passe.
     */
    fun isAssignableFrom(other: KrakenType): kotlin.Boolean = when {
        isDynamic || other.isDynamic -> true
        !isKnown || !other.isKnown -> true
        this == Number && other == Money -> true
        this is Array && other is Array -> element.isAssignableFrom(other.element)
        else -> this == other
    }

    /**
     * `Type.isComparableWith` : l'ordre (`<`, `>`, `<=`, `>=`) n'a de sens que
     * sur les numériques entre eux, les dates entre elles, les date-heures
     * entre elles. **Date et DateTime ne sont pas comparables** — c'est le
     * piège classique de KEL.
     *
     * Il n'y a volontairement pas de repli `this == other` : deux `String` ne
     * sont pas ordonnables, et le moteur refuse `a < b` sur deux `String`
     * exactement comme sur `Date` contre `DateTime`. Pour l'égalité, qui elle
     * accepte n'importe quels types assignables entre eux, voir
     * [isAssignableFrom].
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
        private fun areNumeric(a: KrakenType, b: KrakenType): kotlin.Boolean =
            (a == Number || a == Money) && (b == Number || b == Money)

        /**
         * Type KEL d'un nom écrit dans le DSL, qu'il vienne d'un champ de
         * contexte (`Decimal premium`), d'une signature de fonction
         * (`Number[]`) ou d'un paramètre.
         */
        fun fromDslName(raw: kotlin.String): KrakenType {
            val name = raw.trim()
            if (name.endsWith("[]")) return Array(fromDslName(name.removeSuffix("[]")))
            // `Date | DateTime` : une union n'est pas modélisée ici, et prétendre
            // le contraire produirait de faux diagnostics. On abstient.
            if (name.contains('|')) return Any
            if (name.startsWith("<")) return Any // générique non résolu
            // Le DSL Kraken est insensible à la casse — le corpus officiel
            // écrit tour à tour String, STRING et string pour le même type.
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
