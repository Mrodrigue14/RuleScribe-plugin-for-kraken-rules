package com.kraken.plugin.psi

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.tree.TokenSet
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
    PsiNameIdentifierOwner,
    KrakenReferencedDeclaration {

    constructor(node: ASTNode) : super(node)

    constructor(stub: KrakenRuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun toString(): String = "KrakenRuleDecl"

    override val kind: KrakenDeclaration.Kind get() = KrakenDeclaration.Kind.RULE

    override fun visibleUsages(): List<KrakenRuleRef> = KrakenDeclarations.findRuleRefsVisibleTo(this)

    override fun getNameIdentifier(): PsiElement? = nameLeaf()?.psi

    override fun getName(): String? {
        val stub = this.stub
        if (stub != null) return stub.name
        return nameLeaf()?.text?.let(StringUtil::unquoteString)
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
        kind.icon,
    )

    fun hasTarget(): Boolean = node.findChildByType(KrakenTypes.RULE_TARGET) != null

    /** `Policy` in `On Policy.state`. */
    fun targetContextLeaf(): PsiElement? = node.findChildByType(KrakenTypes.RULE_TARGET)
        ?.let { KrakenPsiUtil.firstIdAfter(it, KrakenTypes.ON_KW) }
        ?.psi

    fun ruleKeyword(): PsiElement? = node.findChildByType(KrakenTypes.RULE_KW)?.psi

    /**
     * `@Dimension` values of this rule merged over those of its enclosing `Rules` blocks,
     * the innermost winning, as the engine merges rule metadata
     * (`KrakenDSLModelMetadataConverter.merge`). Keys and values are compared as written.
     */
    fun dimensions(): Map<String, String> = generateSequence(node) { it.treeParent }
        .takeWhile { it.elementType == KrakenTypes.RULE_DECL || it.elementType == KrakenTypes.RULES_BLOCK }
        .toList()
        .asReversed()
        .flatMap { dimensionsDeclaredOn(it) }
        .toMap()

    private fun dimensionsDeclaredOn(declaration: ASTNode): List<Pair<String, String>> = declaration.getChildren(ANNOTATIONS)
        .mapNotNull { it.findChildByType(KrakenTypes.DIMENSION_ANNOTATION) }
        .mapNotNull { dimension ->
            val (key, value) = dimension.getChildren(ANNOTATION_ARGS).map { it.text.trim() }.takeIf { it.size == 2 } ?: return@mapNotNull null
            key to value
        }

    private fun nameLeaf(): ASTNode? = node.findChildByType(KrakenTypes.RULE_NAME)?.findChildByType(KrakenTypes.STRING)

    private companion object {
        val ANNOTATIONS = TokenSet.create(KrakenTypes.ANNOTATION)
        val ANNOTATION_ARGS = TokenSet.create(KrakenTypes.ANNOTATION_ARG)
    }
}
