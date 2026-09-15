package com.kraken.plugin.highlighter

import com.intellij.lang.Language
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.options.colors.RainbowColorSettingsPage
import com.kraken.plugin.lang.KrakenIcons
import com.kraken.plugin.lang.KrakenLanguage
import javax.swing.Icon

/**
 * Implements [RainbowColorSettingsPage] to get the platform-managed "Rainbow"
 * checkbox instead of a custom setting; [KrakenBracketAnnotator] reads it through
 * `RainbowHighlighter.isRainbowEnabled`. It only controls the depth colours: the red
 * of unmatched braces always shows, since it reports an error.
 */
class KrakenColorSettingsPage :
    ColorSettingsPage,
    RainbowColorSettingsPage {

    override fun isRainbowType(type: TextAttributesKey?): Boolean = type in KrakenSyntaxHighlighter.BRACKET_DEPTH

    override fun getLanguage(): Language = KrakenLanguage

    override fun getIcon(): Icon = KrakenIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = KrakenSyntaxHighlighter()

    override fun getDemoText(): String = """
        // Sample Kraken .rules file
        Namespace Policy

        /**
         * Root context.
         */
        Root Context Policy {
            String policyCd
            Child AddressInfo
        }

        Dimension "state" : String

        /**
         * Limits of all coverages.
         * @since 1.0.0
         */
        Function Limits(Coverage[] coverages) : Number[] {
            coverages.limitAmount
        }

        @Dimension("state", "CA")
        Rule "Set AddressInfo.postalCode to state name" On AddressInfo.postalCode {
            Description "Forces the California postal code"
            Priority 10
            When Policy.policyCd != null and <nativeFn>Count</nativeFn>(Policy.riskItems) > 0
            Reset To "CA"
        }

        Rule "Assert effective date" On Policy.effectiveDate {
            Assert effectiveDate < <nativeFn>Today</nativeFn>()
            Error "code" : "The date must be in the past"
            Overridable
        }

        Rule "Assert limits" On Policy.policyCd {
            Assert <nativeFn>Sum</nativeFn>(<declaredFn>Limits</declaredFn>(Policy.coverages)) > 0
        }

        EntryPoint "Validation" {
            "Assert effective date",
            "Set AddressInfo.postalCode to state name"
        }
    """.trimIndent()

    /**
     * Function colours are applied by [KrakenFunctionAnnotator], not by the lexical
     * highlighter, so the preview would not show them. These tags make them visible in
     * the demo text.
     */
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "nativeFn" to KrakenSyntaxHighlighter.NATIVE_FUNCTION,
        "declaredFn" to KrakenSyntaxHighlighter.DECLARED_FUNCTION,
    )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Kraken Rules"

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", KrakenSyntaxHighlighter.KEYWORD),
            AttributesDescriptor("String", KrakenSyntaxHighlighter.STRING),
            AttributesDescriptor("Number", KrakenSyntaxHighlighter.NUMBER),
            AttributesDescriptor("Line comment", KrakenSyntaxHighlighter.LINE_COMMENT),
            AttributesDescriptor("Block comment", KrakenSyntaxHighlighter.BLOCK_COMMENT),
            AttributesDescriptor("Documentation comment", KrakenSyntaxHighlighter.DOC_COMMENT),
            AttributesDescriptor("Annotation", KrakenSyntaxHighlighter.ANNOTATION),
            AttributesDescriptor("Identifier", KrakenSyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("Function call//Built-in", KrakenSyntaxHighlighter.NATIVE_FUNCTION),
            AttributesDescriptor("Function call//Declared in the project", KrakenSyntaxHighlighter.DECLARED_FUNCTION),
            AttributesDescriptor("Reference//Context name", KrakenSyntaxHighlighter.CONTEXT_REFERENCE),
            AttributesDescriptor("Reference//Field or variable", KrakenSyntaxHighlighter.FIELD_REFERENCE),
            AttributesDescriptor("Operator", KrakenSyntaxHighlighter.OPERATOR),
            AttributesDescriptor("Braces", KrakenSyntaxHighlighter.BRACES),
            AttributesDescriptor("Parentheses", KrakenSyntaxHighlighter.PARENTHESES),
            AttributesDescriptor("Brackets", KrakenSyntaxHighlighter.BRACKETS),
            AttributesDescriptor("Comma", KrakenSyntaxHighlighter.COMMA),
            AttributesDescriptor("Dot", KrakenSyntaxHighlighter.DOT),
            AttributesDescriptor("Bad character", KrakenSyntaxHighlighter.BAD_CHARACTER),
            AttributesDescriptor("Nesting//Depth 1", KrakenSyntaxHighlighter.BRACKET_DEPTH[0]),
            AttributesDescriptor("Nesting//Depth 2", KrakenSyntaxHighlighter.BRACKET_DEPTH[1]),
            AttributesDescriptor("Nesting//Depth 3", KrakenSyntaxHighlighter.BRACKET_DEPTH[2]),
            AttributesDescriptor("Nesting//Unmatched bracket", KrakenSyntaxHighlighter.UNMATCHED_BRACKET),
        )
    }
}
