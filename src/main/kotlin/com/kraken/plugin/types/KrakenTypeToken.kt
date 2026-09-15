package com.kraken.plugin.types

/**
 * Syntactic shape of a DSL type, ported from the `type` production of `Value.g4`:
 * `identifier`, `( type )`, `type[]`, `type | type`, `<identifier>`.
 *
 * [KrakenType] answers "which type is this" and gives up on hard cases (unions and
 * generics both become `Any`). This answers "what is the type made of", which the engine
 * asks through `Type.isUnion()` and `Type.isGeneric()` to reject function signatures
 * mixing both (`kvf007`, `kvf010`).
 *
 * It works on the type's text rather than the PSI tree. The engine does the same
 * (`FunctionValidator` passes `function.getReturnType()`, a string, to
 * `ScopeBuilder.toType`), and `union_type`, `array_type` and `atom_type` are private BNF
 * rules, so `TYPE_REF` is a flat run of tokens where `<T>` and `Foo<T>` only differ by
 * what precedes the angle bracket.
 */
sealed class KrakenTypeToken {

    /** `Number`, `Policy`, and `Foo<A, B>`, which the engine does not know (see [parse]). */
    data class Plain(val name: String) : KrakenTypeToken()

    /** `<T>` — `#GenericType`. */
    data class Generic(val name: String) : KrakenTypeToken()

    /** `T[]` — `#ArrayType`. */
    data class Array(val element: KrakenTypeToken) : KrakenTypeToken()

    /** `A | B` — `#UnionType`. */
    data class Union(val left: KrakenTypeToken, val right: KrakenTypeToken) : KrakenTypeToken()

    /** `ArrayType.isGeneric` delegates to its element, `UnionType` to both members. */
    val isGeneric: Boolean
        get() = when (this) {
            is Generic -> true
            is Array -> element.isGeneric
            is Union -> left.isGeneric || right.isGeneric
            is Plain -> false
        }

    /**
     * `ArrayType.isUnion` delegates to its element: `(A | B)[]` is a union.
     *
     * `GenericType.isUnion` delegates to its bound, so `<T>` declared `T is Date | DateTime`
     * is a union for the engine. That is not reproduced: it needs the bounds environment,
     * and no engine test pins the case. Reporting less lets through code the engine rejects;
     * reporting more would condemn valid code.
     */
    val isUnion: Boolean
        get() = when (this) {
            is Union -> true
            is Array -> element.isUnion
            is Generic, is Plain -> false
        }

    companion object {

        /**
         * Parses a type's text, or returns `null` when it does not have the expected shape, in
         * which case the caller abstains.
         *
         * `Foo<A, B>` does not exist in `Value.g4`, but RuleScribe's grammar accepts it. Its
         * arguments are parsed and ignored: they do not make `Foo` generic, since the engine has
         * no semantics for this form.
         */
        fun parse(raw: String): KrakenTypeToken? {
            val tokens = tokenize(raw) ?: return null
            val parser = Parser(tokens)
            val type = parser.union() ?: return null
            return type.takeIf { parser.atEnd }
        }

        private val PUNCTUATION = "<>|[](),".toSet()

        private fun tokenize(raw: String): List<String>? {
            val tokens = mutableListOf<String>()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                when {
                    c.isWhitespace() -> i++

                    c in PUNCTUATION -> {
                        tokens += c.toString()
                        i++
                    }

                    c.isLetter() || c == '_' -> {
                        val start = i
                        while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] == '_')) i++
                        tokens += raw.substring(start, i)
                    }

                    else -> return null
                }
            }
            return tokens.takeIf { it.isNotEmpty() }
        }

        /** Recursive descent over the three BNF levels, in precedence order: `[]` binds tighter than `|`. */
        private class Parser(private val tokens: List<String>) {
            private var pos = 0

            val atEnd: Boolean get() = pos >= tokens.size

            private fun peek(): String? = tokens.getOrNull(pos)
            private fun accept(token: String): Boolean = (peek() == token).also { if (it) pos++ }

            fun union(): KrakenTypeToken? {
                var left = array() ?: return null
                while (accept("|")) {
                    val right = array() ?: return null
                    left = Union(left, right)
                }
                return left
            }

            private fun array(): KrakenTypeToken? {
                var type = atom() ?: return null
                while (peek() == "[") {
                    pos++
                    if (!accept("]")) return null
                    type = Array(type)
                }
                return type
            }

            private fun atom(): KrakenTypeToken? = when {
                accept("(") -> union()?.takeIf { accept(")") }

                accept("<") -> identifier()?.let { Generic(it) }?.takeIf { accept(">") }

                else -> identifier()?.let { name ->
                    if (accept("<")) typeArguments()?.let { Plain(name) } else Plain(name)
                }
            }

            /** `Foo<A, B>`: consumed to move forward, then discarded. */
            private fun typeArguments(): Unit? {
                do {
                    union() ?: return null
                } while (accept(","))
                return if (accept(">")) Unit else null
            }

            private fun identifier(): String? = peek()?.takeIf { it.first().isLetter() || it.first() == '_' }?.also { pos++ }
        }
    }
}
