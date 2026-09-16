package com.kraken.plugin.psi

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.stubs.KrakenRuleStub

/**
 * `Rule "name" On Context.field { ... }` declaration.
 *
 * Stub-based: when the file is not open, the name comes from the stub index without
 * loading the AST. [StubBasedPsiElement] must be implemented explicitly because
 * DefaultStubBuilder checks for it, while `getElementType()` is left to
 * StubBasedPsiElementBase, which already resolves it from the stub or the node.
 */
class KrakenRuleDecl :
    StubBasedPsiElementBase<KrakenRuleStub>,
    StubBasedPsiElement<KrakenRuleStub>,
    PsiNameIdentifierOwner {

    constructor(node: ASTNode) : super(node)

    constructor(stub: KrakenRuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun toString(): String = "KrakenRuleDecl"

    override fun getNameIdentifier(): PsiElement? = nameLeaf()?.psi

    override fun getName(): String? {
        val stub = this.stub
        if (stub != null) return stub.name
        return nameLeaf()?.text?.let(KrakenPsiUtil::unquote)
    }

    override fun setName(name: String): PsiElement {
        KrakenPsiUtil.replaceQuoted(nameLeaf(), name)
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    /**
     * The name alone is ambiguous when an imported rule exists in several namespaces;
     * the location resolves it.
     */
    override fun getPresentation(): ItemPresentation = KrakenPresentations.of(
        this,
        KrakenPresentations.declarationText(this, name, "Rule"),
        KrakenPresentations.RULE_ICON,
    )

    fun hasTarget(): Boolean = node.findChildByType(KrakenTypes.RULE_TARGET) != null

    /** `Policy` in `On Policy.state`. */
    fun targetContextLeaf(): PsiElement? = node.findChildByType(KrakenTypes.RULE_TARGET)
        ?.let { KrakenPsiUtil.firstIdAfter(it, KrakenTypes.ON_KW) }
        ?.psi

    fun ruleKeyword(): PsiElement? = node.findChildByType(KrakenTypes.RULE_KW)?.psi

    private fun nameLeaf(): ASTNode? = node.findChildByType(KrakenTypes.RULE_NAME)?.findChildByType(KrakenTypes.STRING)
}
