package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.highlighter.KrakenSyntaxHighlighter

/**
 * Coloration sémantique des références.
 *
 * Ce que le lexer ne peut pas distinguer — un nom de contexte, un champ, un
 * nom inconnu, tous `IDENTIFIER` — l'annotator le sépare à partir de la
 * résolution de portée. Le point important est le cas négatif : un nom qui ne
 * se résout pas ne doit recevoir aucune couleur.
 */
class KrakenSemanticHighlightingTest : BasePlatformTestCase() {

    private val model = """
        Root Context Policy {
            String policyCd
            Child Coverage
        }

        Context Coverage {
            Money limitAmount
        }

        Context Elsewhere {
            String tag
        }
    """.trimIndent()

    /** Clés posées par l'annotator sur le texte donné. */
    private fun attributesFor(body: String, needle: String): List<String> {
        myFixture.configureByText(
            "sem.rules",
            """
            $model

            Rule "Under test" On Policy.policyCd {
                $body
            }
            """.trimIndent()
        )
        val text = myFixture.file.text
        val start = text.lastIndexOf(needle)
        require(start >= 0) { "'$needle' absent du fichier" }
        val range = start until (start + needle.length)
        return myFixture.doHighlighting()
            .filter { it.startOffset in range || it.endOffset - 1 in range }
            .mapNotNull { it.forcedTextAttributesKey?.externalName }
            .distinct()
    }

    /**
     * Une référence croisée vers un contexte que la cible `On` n'a pas pour
     * enfant. `Coverage`, lui, est un `Child` de `Policy` : il se résout donc
     * en champ, ce qui est le bon sens de lecture.
     */
    fun testACrossContextNameIsColouredAsAContext() {
        assertEquals(
            listOf(KrakenSyntaxHighlighter.CONTEXT_REFERENCE.externalName),
            attributesFor("Assert Elsewhere.tag != null", "Elsewhere")
        )
    }

    fun testAChildContextResolvesAsAField() {
        assertEquals(
            listOf(KrakenSyntaxHighlighter.FIELD_REFERENCE.externalName),
            attributesFor("Assert Coverage.limitAmount > 0", "Coverage")
        )
    }

    fun testAFieldOfTheTargetContextIsColouredAsAField() {
        assertEquals(
            listOf(KrakenSyntaxHighlighter.FIELD_REFERENCE.externalName),
            attributesFor("Assert policyCd != null", "policyCd")
        )
    }

    /** Une variable locale relève de la même catégorie qu'un champ. */
    fun testALocalVariableIsColouredAsAField() {
        assertEquals(
            listOf(KrakenSyntaxHighlighter.FIELD_REFERENCE.externalName),
            attributesFor("Assert set x to 1 return x > 0", "x >")
        )
    }

    /**
     * Le cas qui compte : colorer un nom inconnu comme un champ reviendrait à
     * affirmer qu'il en désigne un.
     */
    fun testAnUnresolvedNameGetsNoColour() {
        assertEquals(emptyList<String>(), attributesFor("Assert notAThing > 0", "notAThing"))
    }
}
