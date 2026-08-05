package com.kraken.plugin.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Quick documentation (Ctrl+Q) pour les règles Kraken : nom, cible,
 * description, condition, payload et dimensions — et pour les fonctions,
 * qu'elles soient déclarées dans le projet ou natives au moteur.
 */
class KrakenDocumentationProvider : AbstractDocumentationProvider() {

    /**
     * Une fonction native n'a aucune déclaration à résoudre : c'est l'appel
     * lui-même qui porte la documentation. Sans ce point d'entrée, Ctrl+Q sur
     * `Round(x)` n'afficherait rien.
     */
    override fun getCustomDocumentationElement(
        editor: com.intellij.openapi.editor.Editor,
        file: com.intellij.psi.PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? = contextElement?.let {
        com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, KrakenFunctionCall::class.java, false)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        (element as? KrakenFunctionDecl)?.let { return KrakenFunctionDoc.render(it) }
        (element as? KrakenFunctionCall)?.let { call ->
            KrakenPsiUtil.findFunctionVisible(call, call.functionName, call.argumentCount)
                ?.let { return KrakenFunctionDoc.render(it) }
            KrakenFunctionCatalog.find(call.functionName, call.argumentCount)
                ?.let { return KrakenFunctionDoc.render(it) }
            return null
        }

        val rule = element as? KrakenRuleDecl ?: return null
        val name = rule.name ?: return null

        val sb = StringBuilder()
        sb.append("<b>Rule</b> \"").append(StringUtil.escapeXmlEntities(name)).append("\"")

        rule.node.findChildByType(KrakenTypes.RULE_TARGET)?.let {
            sb.append("<br/><b>On</b> ").append(StringUtil.escapeXmlEntities(compact(it.text.removePrefix("On").trim())))
        }

        val annotations = rule.node.getChildren(TokenSet.create(KrakenTypes.ANNOTATION))
        if (annotations.isNotEmpty()) {
            sb.append("<br/><b>Annotations:</b> ")
            sb.append(annotations.joinToString(" ") { StringUtil.escapeXmlEntities(compact(it.text)) })
        }

        val body = rule.node.findChildByType(KrakenTypes.RULE_BODY)
        if (body != null) {
            body.findChildrenRecursively(KrakenTypes.DESCRIPTION_CLAUSE).firstOrNull()?.let { desc ->
                desc.findChildByType(KrakenTypes.STRING)?.let {
                    sb.append("<br/><b>Description:</b> ")
                        .append(StringUtil.escapeXmlEntities(KrakenPsiUtil.unquote(it.text)))
                }
            }
            for (clauseType in listOf(
                KrakenTypes.WHEN_CLAUSE, KrakenTypes.SET_PAYLOAD,
                KrakenTypes.DEFAULT_PAYLOAD, KrakenTypes.ASSERT_PAYLOAD
            )) {
                for (clause in body.findChildrenRecursively(clauseType)) {
                    sb.append("<br/><code>")
                        .append(StringUtil.escapeXmlEntities(StringUtil.shortenTextWithEllipsis(compact(clause.text), 120, 0)))
                        .append("</code>")
                }
            }
        }
        return sb.toString()
    }

    private fun compact(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    private fun com.intellij.lang.ASTNode.findChildrenRecursively(
        type: com.intellij.psi.tree.IElementType
    ): List<com.intellij.lang.ASTNode> {
        val out = mutableListOf<com.intellij.lang.ASTNode>()
        var child = firstChildNode
        while (child != null) {
            if (child.elementType == type) out.add(child)
            out.addAll(child.findChildrenRecursively(type))
            child = child.treeNext
        }
        return out
    }
}
