package com.kraken.plugin.types

/**
 * Forme syntaxique d'un type écrit dans le DSL, portée de la production `type`
 * de `Value.g4` : `identifier`, `( type )`, `type[]`, `type | type`, `<identifier>`.
 *
 * [KrakenType] répond « quel type est-ce » et abandonne dès que la question est
 * dure — une union et un générique y deviennent tous deux `Any`. Ici la
 * question est différente et purement structurelle : *de quoi ce type est-il
 * fait*. Le moteur la pose exactement ainsi, via `Type.isUnion()` et
 * `Type.isGeneric()`, pour refuser les signatures de fonction qui mélangent les
 * deux (`kvf007`, `kvf010`).
 *
 * On travaille sur le **texte** du type plutôt que sur l'arbre PSI, pour deux
 * raisons. Le moteur fait de même — `FunctionValidator` lit
 * `function.getReturnType()`, une chaîne, et la donne à `ScopeBuilder.toType`.
 * Et surtout `union_type`, `array_type` et `atom_type` sont des règles privées
 * du BNF : elles ne produisent aucun nœud, si bien que `TYPE_REF` est une suite
 * de tokens à plat où `<T>` et `Foo<T>` ne se distinguent que par ce qui précède
 * le chevron. Reparser la chaîne coûte moins cher que de deviner cela.
 */
sealed class KrakenTypeToken {

    /** `Number`, `Policy` — et `Foo<A, B>`, que le moteur ne connaît pas (voir [parse]). */
    data class Plain(val name: String) : KrakenTypeToken()

    /** `<T>` — `#GenericType`. */
    data class Generic(val name: String) : KrakenTypeToken()

    /** `T[]` — `#ArrayType`. */
    data class Array(val element: KrakenTypeToken) : KrakenTypeToken()

    /** `A | B` — `#UnionType`. */
    data class Union(val left: KrakenTypeToken, val right: KrakenTypeToken) : KrakenTypeToken()

    /** `ArrayType.isGeneric` délègue à son élément, `UnionType` à ses deux membres. */
    val isGeneric: Boolean
        get() = when (this) {
            is Generic -> true
            is Array -> element.isGeneric
            is Union -> left.isGeneric || right.isGeneric
            is Plain -> false
        }

    /**
     * `ArrayType.isUnion` délègue à son élément : `(A | B)[]` est une union.
     *
     * `GenericType.isUnion` délègue, lui, à sa **borne** — `<T>` déclaré
     * `T is Date | DateTime` est une union pour le moteur. On ne le reproduit
     * pas : cela demanderait de résoudre l'environnement des bornes, et aucun
     * test du moteur ne fixe ce cas. Signaler moins que le moteur laisse passer
     * du code qu'il refusera ; signaler plus condamnerait du code valide.
     */
    val isUnion: Boolean
        get() = when (this) {
            is Union -> true
            is Array -> element.isUnion
            is Generic, is Plain -> false
        }

    companion object {

        /**
         * Analyse le texte d'un type, ou renvoie `null` s'il n'a pas la forme
         * attendue — auquel cas l'appelant s'abstient plutôt que de deviner.
         *
         * `Foo<A, B>` n'existe pas dans `Value.g4` mais la grammaire de
         * RuleScribe l'accepte depuis toujours, délibérément plus permissive que
         * le moteur. Ses arguments sont analysés puis ignorés : ils ne rendent
         * pas `Foo` générique, puisque le moteur n'a aucune sémantique pour
         * cette forme.
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
                    c in PUNCTUATION -> { tokens += c.toString(); i++ }
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

        /**
         * Descente récursive sur les trois niveaux du BNF, dans l'ordre qui
         * donne les priorités : `[]` lie plus fort que `|`.
         */
        private class Parser(private val tokens: List<String>) {
            private var pos = 0

            val atEnd: Boolean get() = pos >= tokens.size

            private fun peek(): String? = tokens.getOrNull(pos)
            private fun accept(token: String): Boolean =
                (peek() == token).also { if (it) pos++ }

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

            /** `Foo<A, B>` : consommés pour avancer, puis oubliés. */
            private fun typeArguments(): Unit? {
                do {
                    union() ?: return null
                } while (accept(","))
                return if (accept(">")) Unit else null
            }

            private fun identifier(): String? =
                peek()?.takeIf { it.first().isLetter() || it.first() == '_' }?.also { pos++ }
        }
    }
}
