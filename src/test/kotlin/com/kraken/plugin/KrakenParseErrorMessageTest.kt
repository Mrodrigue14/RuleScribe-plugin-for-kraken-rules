package com.kraken.plugin

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Readability of syntax errors.
 *
 * Grammar-Kit builds these messages by listing everything that could follow. On a
 * broken KEL expression that reached over four hundred characters, with every token
 * prefixed by `KrakenTokenType.` and the thirteen binary operators listed one by one.
 */
class KrakenParseErrorMessageTest : BasePlatformTestCase() {

    private fun errors(source: String): List<String> = parseErrors(myFixture.configureByText("err.rules", source))

    private val brokenExpression = """
        Rule "R" On Policy.state {
            Assert Round(limit] > 0
        }
    """.trimIndent()

    /** The class name has no place in an error message. */
    fun testTokenNamesCarryNoClassPrefix() {
        val message = errors(brokenExpression).first()
        assertFalse(
            "\"KrakenTokenType.\" must not appear: $message",
            message.contains("KrakenTokenType"),
        )
    }

    /**
     * The thirteen binary operators are reported as one group; otherwise each is named and
     * the message triples in length.
     */
    fun testBinaryOperatorsAreReportedAsOneGroup() {
        val message = errors(brokenExpression).first()
        assertTrue("operators must be grouped: $message", message.contains("<operator>"))
        for (operator in listOf("instanceof", "satisfies", "Matches")) {
            assertFalse(
                "'$operator' must not be listed separately: $message",
                message.contains(operator),
            )
        }
    }

    /**
     * Deliberately loose threshold: it does not freeze the wording, it keeps grammar changes
     * from bringing back messages over 400 characters.
     */
    fun testMessagesStayReadableInLength() {
        for (message in errors(brokenExpression)) {
            assertTrue("message trop long (${message.length}) : $message", message.length < 150)
        }
    }

    /** The message still names what it found, not only what it expected. */
    fun testMessageStillNamesTheOffendingToken() {
        assertTrue(errors(brokenExpression).first().contains("got ']'"))
    }
}
