package com.kraken.plugin.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenFunctionTarget
import com.kraken.plugin.psi.KrakenPresentations
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Quick documentation (Ctrl+Q) for Kraken rules (name, target, description,
 * condition, payload, dimensions) and for functions, declared or native.
 */
class KrakenDocumentationProvider : AbstractDocumentationProvider() {

    /**
     * A native function has no declaration to resolve, so the call itself carries the
     * documentation; without this, Ctrl+Q on `Round(x)` would show nothing.
     */
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? = contextElement?.let { PsiTreeUtil.getParentOfType(it, KrakenFunctionCall::class.java, false) }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? = when (element) {
        is KrakenFunctionDecl -> KrakenFunctionDoc.render(element)
        is KrakenFunctionCall -> renderCall(element)
        is KrakenRuleDecl -> renderRule(element)
        else -> null
    }

    private fun renderCall(call: KrakenFunctionCall): String? = when (val target = call.target()) {
        is KrakenFunctionTarget.Declared -> KrakenFunctionDoc.render(target.declaration)
        is KrakenFunctionTarget.Native -> KrakenFunctionDoc.render(target.function)
        null -> null
    }

    private fun renderRule(rule: KrakenRuleDecl): String? {
        val name = rule.name ?: return null
        return buildString {
            append("<b>Rule</b> \"").append(escape(name)).append("\"")

            rule.node.findChildByType(KrakenTypes.RULE_TARGET)?.let { target ->
                val path = target.text.drop(target.findChildByType(KrakenTypes.ON_KW)?.textLength ?: 0)
                append("<br/><b>On</b> ").append(escape(KrakenPresentations.compact(path)))
            }

            val annotations = KrakenPresentations.annotationsOf(rule)
            if (annotations.isNotEmpty()) {
                append("<br/><b>Annotations:</b> ").append(annotations.joinToString(" ") { escape(it) })
            }

            val body = rule.node.findChildByType(KrakenTypes.RULE_BODY)?.psi ?: return@buildString
            descendantsOf(body, KrakenTypes.DESCRIPTION_CLAUSE).firstOrNull()
                ?.node?.findChildByType(KrakenTypes.STRING)
                ?.let { append("<br/><b>Description:</b> ").append(escape(StringUtil.unquoteString(it.text))) }
            for (clauseType in SUMMARISED_CLAUSES) {
                for (clause in descendantsOf(body, clauseType)) {
                    val summary = StringUtil.shortenTextWithEllipsis(KrakenPresentations.compact(clause.text), MAX_CLAUSE_LENGTH, 0)
                    append("<br/><code>").append(escape(summary)).append("</code>")
                }
            }
        }
    }

    private fun descendantsOf(root: PsiElement, type: IElementType): List<PsiElement> = PsiTreeUtil.collectElements(root) { it.node?.elementType == type }.toList()

    private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)

    private companion object {
        val SUMMARISED_CLAUSES = listOf(
            KrakenTypes.WHEN_CLAUSE,
            KrakenTypes.SET_PAYLOAD,
            KrakenTypes.DEFAULT_PAYLOAD,
            KrakenTypes.ASSERT_PAYLOAD,
        )

        const val MAX_CLAUSE_LENGTH = 120
    }
}
