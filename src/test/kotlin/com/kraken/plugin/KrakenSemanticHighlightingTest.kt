package com.kraken.plugin

import com.kraken.plugin.highlighter.KrakenSyntaxHighlighter

/**
 * Semantic colouring of references.
 *
 * What the lexer cannot tell apart (a context name, a field, an unknown name, all
 * `IDENTIFIER`), the annotator separates through scope resolution. The key case is the
 * negative one: an unresolved name gets no colour.
 */
class KrakenSemanticHighlightingTest : KrakenRuleBodyTestCase() {

    override val model = """
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

    /** Keys the annotator sets on [needle]. */
    private fun attributesFor(body: String, needle: String): List<String> {
        configureRule(body)
        val text = myFixture.file.text
        val start = text.lastIndexOf(needle)
        require(start >= 0) { "'$needle' is not in the file" }
        val range = start until (start + needle.length)
        return myFixture.doHighlighting()
            .filter { it.startOffset in range || it.endOffset - 1 in range }
            .mapNotNull { it.forcedTextAttributesKey?.externalName }
            .distinct()
    }

    /**
     * A cross reference to a context that is not a child of the `On` target. `Coverage` is
     * a `Child` of `Policy`, so it resolves as a field.
     */
    fun testACrossContextNameIsColouredAsAContext() {
        assertEquals(
            listOf(KrakenSyntaxHighlighter.CONTEXT_REFERENCE.externalName),
            attributesFor("Assert Elsewhere.tag != null", "Elsewhere"),
        )
    }

    fun testAChildContextResolvesAsAField() {
        assertEquals(
            listOf(KrakenSyntaxHighlighter.FIELD_REFERENCE.externalName),
            attributesFor("Assert Coverage.limitAmount > 0", "Coverage"),
        )
    }

    fun testAFieldOfTheTargetContextIsColouredAsAField() {
        assertEquals(
            listOf(KrakenSyntaxHighlighter.FIELD_REFERENCE.externalName),
            attributesFor("Assert policyCd != null", "policyCd"),
        )
    }

    fun testALocalVariableIsColouredAsAField() {
        assertEquals(
            listOf(KrakenSyntaxHighlighter.FIELD_REFERENCE.externalName),
            attributesFor("Assert set x to 1 return x > 0", "x >"),
        )
    }

    /** Colouring an unknown name as a field would claim that it is one. */
    fun testAnUnresolvedNameGetsNoColour() {
        assertEquals(emptyList<String>(), attributesFor("Assert notAThing > 0", "notAThing"))
    }
}
