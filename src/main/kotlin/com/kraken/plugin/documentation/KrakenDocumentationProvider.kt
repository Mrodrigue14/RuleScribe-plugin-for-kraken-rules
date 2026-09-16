package com.kraken.plugin.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenDeclarations
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl
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

    private fun renderCall(call: KrakenFunctionCall): String? {
        KrakenDeclarations.findFunctionVisible(call, call.functionName, call.argumentCount)
            ?.let { return KrakenFunctionDoc.render(it) }
        return KrakenFunctionCatalog.find(call.functionName, call.argumentCount)?.let { KrakenFunctionDoc.render(it) }
    }

    private fun renderRule(rule: KrakenRuleDecl): String? {
        val name = rule.name ?: return null
        val sb = StringBuilder()
        sb.append("<b>Rule</b> \"").append(escape(name)).append("\"")

        rule.node.findChildByType(KrakenTypes.RULE_TARGET)?.let { target ->
            val path = target.text.drop(target.findChildByType(KrakenTypes.ON_KW)?.textLength ?: 0)
            sb.append("<br/><b>On</b> ").append(escape(KrakenPresentations.compact(path)))
        }

        val annotations = rule.node.getChildren(TokenSet.create(KrakenTypes.ANNOTATION))
        if (annotations.isNotEmpty()) {
            sb.append("<br/><b>Annotations:</b> ")
            sb.append(annotations.joinToString(" ") { escape(KrakenPresentations.compact(it.text)) })
        }

        val body = rule.node.findChildByType(KrakenTypes.RULE_BODY)?.psi ?: return sb.toString()
        descendantsOf(body, KrakenTypes.DESCRIPTION_CLAUSE).firstOrNull()
            ?.node?.findChildByType(KrakenTypes.STRING)
            ?.let { sb.append("<br/><b>Description:</b> ").append(escape(StringUtil.unquoteString(it.text))) }
        for (clauseType in SUMMARISED_CLAUSES) {
            for (clause in descendantsOf(body, clauseType)) {
                val summary = StringUtil.shortenTextWithEllipsis(KrakenPresentations.compact(clause.text), MAX_CLAUSE_LENGTH, 0)
                sb.append("<br/><code>").append(escape(summary)).append("</code>")
            }
        }
        return sb.toString()
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
