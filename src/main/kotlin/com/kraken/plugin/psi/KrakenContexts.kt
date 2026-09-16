package com.kraken.plugin.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.parser.KrakenTypes

/** Context declarations, their fields and children, and `Is` inheritance. */
object KrakenContexts {

    fun findContextNamesVisible(from: PsiFile?): List<String> = KrakenNamespaces.visibleFiles(from)
        .flatMap { contextDecls(it).mapNotNull { decl -> contextName(decl) } }
        .distinct()

    /**
     * Every visible declaration of this context name.
     *
     * Several files in one namespace can declare the same context (a repository hosting
     * several products, or test fixtures next to the code). Keeping only one, by file
     * order, breaks resolution of fields that exist only in the others.
     */
    fun findContextDecls(from: PsiFile?, name: String): List<PsiElement> = KrakenNamespaces.visibleFiles(from)
        .flatMap { contextDecls(it) }
        .filter { contextName(it) == name }

    fun findContextDecl(from: PsiFile?, name: String): PsiElement? = findContextDecls(from, name).firstOrNull()

    fun contextDecls(file: KrakenFile): List<PsiElement> = PsiTreeUtil.collectElements(file) { it.node?.elementType == KrakenTypes.CONTEXT_DECL }.toList()

    fun contextName(contextDecl: PsiElement): String? = KrakenPsiUtil.firstIdAfter(contextDecl.node, KrakenTypes.CONTEXT_KW)?.text

    /** Field and child names of a context, including those inherited through `Is Parent`. */
    fun contextFieldNames(from: PsiFile?, contextName: String): List<String> = contextMembers(from, contextName).mapNotNull { memberName(it) }.distinct().toList()

    /**
     * Field and child declarations of every visible [context] declaration, each followed by
     * those it inherits through `Is`. Same-named declarations are all walked, so a member
     * declared in only one of them is still found.
     */
    fun contextMembers(from: PsiFile?, context: String, depth: Int = 0): Sequence<ASTNode> = sequence {
        if (depth > MAX_INHERITANCE_DEPTH) return@sequence
        for (decl in findContextDecls(from, context)) {
            yieldAll(decl.node.getChildren(CONTEXT_MEMBERS).asSequence())
            val inherited = decl.node.findChildByType(KrakenTypes.INHERITED_CONTEXTS) ?: continue
            for (parent in inherited.getChildren(KrakenPsiUtil.ID_TOKENS)) {
                yieldAll(contextMembers(from, parent.text, depth + 1))
            }
        }
    }

    /** `String policyCd` → `policyCd`; `Child Address` → `Address`. */
    fun memberName(member: ASTNode): String? = when (member.elementType) {
        KrakenTypes.FIELD_DECL -> fieldName(member)
        KrakenTypes.CHILD_DECL -> KrakenPsiUtil.firstIdAfter(member, KrakenTypes.CHILD_KW)?.text
        else -> null
    }

    /** Field name: the last identifier before `:` (`[External] Type [*] name`). */
    private fun fieldName(fieldDecl: ASTNode): String? {
        var last: String? = null
        var child = fieldDecl.firstChildNode
        while (child != null) {
            if (child.elementType == KrakenTypes.COLON) break
            if (child.elementType in KrakenPsiUtil.ID_TOKENS) last = child.text
            child = child.treeNext
        }
        return last
    }

    /** Guards against an `Is` cycle between contexts. */
    private const val MAX_INHERITANCE_DEPTH = 4

    private val CONTEXT_MEMBERS = TokenSet.create(KrakenTypes.FIELD_DECL, KrakenTypes.CHILD_DECL)
}
