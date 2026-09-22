package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.kraken.plugin.parser.KrakenTypes

/**
 * A reference whose target is cached until the next PSI change: the annotators, the
 * inspections and type inference all resolve the same elements in one highlighting pass.
 */
abstract class KrakenCachedReference<T : PsiElement>(element: T, range: TextRange, soft: Boolean) : PsiReferenceBase<T>(element, range, soft) {

    internal abstract fun resolveTarget(): PsiElement?

    final override fun resolve(): PsiElement? = ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, false)

    private companion object {
        val RESOLVER = ResolveCache.AbstractResolver<KrakenCachedReference<*>, PsiElement> { reference, _ -> reference.resolveTarget() }
    }
}

/** An element naming a rule or an entry point in quotes: `"My rule"`, `EntryPoint "Nested"`. */
abstract class KrakenQuotedNameElement(node: ASTNode) : ASTWrapperPsiElement(node) {

    val referencedName: String?
        get() = nameLeaf()?.text?.let(StringUtil::unquoteString)

    /** The name without its quotes, relative to this element. */
    val nameRange: TextRange?
        get() = nameLeaf()?.let { KrakenPsiUtil.insideQuotes(it.startOffset - node.startOffset, it.textLength) }

    fun rename(newName: String) = KrakenPsiUtil.replaceQuoted(nameLeaf(), newName)

    private fun nameLeaf(): ASTNode? = node.findChildByType(KrakenTypes.STRING)
}

/**
 * References every visible declaration with the element's name. `@Dimension` variants
 * share a name, so navigation offers all of them, and any one makes the reference valid.
 */
abstract class KrakenNameReference<T : KrakenQuotedNameElement>(element: T, range: TextRange) : PsiPolyVariantReferenceBase<T>(element, range) {

    internal abstract fun declarationsNamed(name: String): List<PsiElement>

    internal abstract fun visibleDeclarations(): List<PsiNamedElement>

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> = ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, incompleteCode)

    override fun resolve(): PsiElement? = multiResolve(false).firstOrNull()?.element

    override fun getVariants(): Array<Any> = visibleDeclarations().mapNotNull { it.name }.distinct().toTypedArray()

    private companion object {
        val RESOLVER = ResolveCache.PolyVariantResolver<KrakenNameReference<*>> { reference, _ ->
            val name = reference.element.referencedName
            if (name == null) ResolveResult.EMPTY_ARRAY else PsiElementResolveResult.createResults(reference.declarationsNamed(name))
        }
    }
}

class KrakenQuotedNameManipulator : AbstractElementManipulator<KrakenQuotedNameElement>() {

    override fun handleContentChange(element: KrakenQuotedNameElement, range: TextRange, newContent: String): KrakenQuotedNameElement {
        element.rename(newContent)
        return element
    }

    override fun getRangeInElement(element: KrakenQuotedNameElement): TextRange = element.nameRange ?: TextRange(0, element.textLength)
}
