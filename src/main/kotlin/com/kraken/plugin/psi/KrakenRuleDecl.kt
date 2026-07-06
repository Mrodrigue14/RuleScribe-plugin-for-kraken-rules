package com.kraken.plugin.psi

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.stubs.IStubElementType
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.stubs.KrakenRuleStub

/**
 * Déclaration `Rule "nom" On Contexte.champ { ... }`.
 *
 * Élément stubbé : quand le fichier n'est pas ouvert, le nom vient du stub
 * (index) sans charger l'AST. L'interface [StubBasedPsiElement] doit être
 * implémentée explicitement : c'est elle que DefaultStubBuilder vérifie.
 */
class KrakenRuleDecl : StubBasedPsiElementBase<KrakenRuleStub>,
    StubBasedPsiElement<KrakenRuleStub>,
    PsiNameIdentifierOwner {

    constructor(node: ASTNode) : super(node)

    constructor(stub: KrakenRuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getElementType(): IStubElementType<*, *> {
        val stub = this.stub
        @Suppress("UNCHECKED_CAST")
        return if (stub != null) {
            stub.stubType as IStubElementType<*, *>
        } else {
            node.elementType as IStubElementType<*, *>
        }
    }

    override fun toString(): String = "KrakenRuleDecl"

    override fun getNameIdentifier(): PsiElement? = nameLeaf()?.psi

    override fun getName(): String? {
        val stub = this.stub
        if (stub != null) return stub.name
        return nameLeaf()?.text?.let(KrakenPsiUtil::unquote)
    }

    override fun setName(name: String): PsiElement {
        val leaf = nameLeaf()
        if (leaf is LeafElement) {
            val quote = leaf.text.firstOrNull() ?: '"'
            leaf.replaceWithText("$quote$name$quote")
        }
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    fun hasTarget(): Boolean = node.findChildByType(KrakenTypes.RULE_TARGET) != null

    fun ruleKeyword(): PsiElement? = node.findChildByType(KrakenTypes.RULE_KW)?.psi

    private fun nameLeaf(): ASTNode? =
        node.findChildByType(KrakenTypes.RULE_NAME)?.findChildByType(KrakenTypes.STRING)
}
