package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes

/** `Context Policy Is Base { String policyCd  Child Vehicle }` declaration. */
class KrakenContextDecl(node: ASTNode) : ASTWrapperPsiElement(node) {

    override fun getName(): String? = KrakenPsiUtil.firstIdAfter(node, KrakenTypes.CONTEXT_KW)?.text

    /** Contexts named after `Is`. */
    val parentNames: List<String>
        get() = node.findChildByType(KrakenTypes.INHERITED_CONTEXTS)
            ?.getChildren(KrakenPsiUtil.ID_TOKENS)
            ?.map { it.text }
            .orEmpty()

    val members: List<KrakenContextMember>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, KrakenContextMember::class.java)
}

/** A field or child of a context, which references in expressions resolve to. */
sealed class KrakenContextMember(node: ASTNode) : ASTWrapperPsiElement(node) {

    /** `Coverage* coverages`, `Child* Vehicle`. */
    val isCollection: Boolean
        get() = node.findChildByType(KrakenTypes.STAR) != null
}

/** `Child Address`: the name is also the context of the child. */
class KrakenChildDecl(node: ASTNode) : KrakenContextMember(node) {

    override fun getName(): String? = KrakenPsiUtil.firstIdAfter(node, KrakenTypes.CHILD_KW)?.text
}

/** `[External] Type [*] name [: path]`. */
class KrakenFieldDecl(node: ASTNode) : KrakenContextMember(node) {

    override fun getName(): String? = identifiers().lastOrNull()

    val typeName: String?
        get() = identifiers().takeIf { it.size >= 2 }?.first()

    private fun identifiers(): List<String> = KrakenPsiUtil.identifiersBefore(node, KrakenTypes.COLON)
}
