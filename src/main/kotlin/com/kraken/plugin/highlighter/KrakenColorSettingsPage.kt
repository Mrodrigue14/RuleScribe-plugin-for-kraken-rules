package com.kraken.plugin.highlighter

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.kraken.plugin.lang.KrakenIcons
import javax.swing.Icon

class KrakenColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon = KrakenIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = KrakenSyntaxHighlighter()

    override fun getDemoText(): String = """
        // Exemple de fichier Kraken .rules
        Namespace Policy

        /**
         * Contexte racine.
         */
        Root Context Policy {
            String policyCd
            Child AddressInfo
        }

        Dimension "state" : String

        /**
         * Limites de toutes les garanties.
         * @since 1.0.0
         */
        Function Limits(Coverage[] coverages) : Number[] {
            coverages.limitAmount
        }

        @Dimension("state", "CA")
        Rule "Set AddressInfo.postalCode to state name" On AddressInfo.postalCode {
            Description "Force le code postal en Californie"
            Priority 10
            When Policy.policyCd != null and <nativeFn>Count</nativeFn>(Policy.riskItems) > 0
            Reset To "CA"
        }

        Rule "Assert effective date" On Policy.effectiveDate {
            Assert effectiveDate < <nativeFn>Today</nativeFn>()
            Error "code" : "La date doit être dans le passé"
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
     * Les couleurs de fonction sont posées par [KrakenFunctionAnnotator], pas
     * par le highlighter lexical : l'aperçu de cette page ne les verrait donc
     * pas. Ces balises les rendent visibles dans le texte de démonstration.
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
            AttributesDescriptor("Operator", KrakenSyntaxHighlighter.OPERATOR),
            AttributesDescriptor("Braces", KrakenSyntaxHighlighter.BRACES),
            AttributesDescriptor("Parentheses", KrakenSyntaxHighlighter.PARENTHESES),
            AttributesDescriptor("Brackets", KrakenSyntaxHighlighter.BRACKETS),
            AttributesDescriptor("Comma", KrakenSyntaxHighlighter.COMMA),
            AttributesDescriptor("Dot", KrakenSyntaxHighlighter.DOT),
            AttributesDescriptor("Bad character", KrakenSyntaxHighlighter.BAD_CHARACTER)
        )
    }
}
