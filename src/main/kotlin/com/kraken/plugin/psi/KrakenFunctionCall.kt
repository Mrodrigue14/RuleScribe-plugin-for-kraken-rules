package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafElement
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.parser.KrakenTypes

/**
 * Function call in a KEL expression: `Round(x, 2)`, `Limits(coverages)`.
 *
 * As in the engine (`kraken.model.project.scope.ScopeBuilder`), the target is a native
 * Java function, a `Function` with a KEL body, or a bodiless `Function` signature. Only
 * the last two exist as PSI; natives live in the bundled catalogue.
 */
class KrakenFunctionCall(node: ASTNode) : ASTWrapperPsiElement(node) {

    /** Everything before the argument list. */
    val functionName: String
        get() = headRange()?.substring(text)?.trim().orEmpty()

    /** The engine identifies a function by (name, arity), so this, not the types, selects the overload. */
    val argumentCount: Int
        get() {
            val args = node.findChildByType(KrakenTypes.CALL_ARGS) ?: return 0
            return args.getChildren(null)
                .count { it.elementType == KrakenTypes.EXPRESSION }
        }

    override fun getReference(): PsiReference? {
        val range = headRange()?.takeIf { !it.isEmpty } ?: return null
        return KrakenFunctionReference(this, range)
    }

    /** True if a native function or a visible declared function matches this call. */
    fun isResolvable(): Boolean = KrakenFunctionCatalog.find(functionName, argumentCount) != null ||
        KrakenPsiUtil.findFunctionVisible(this, functionName, argumentCount) != null

    /** Range of the called name, relative to this element. */
    private fun headRange(): TextRange? {
        val args = node.findChildByType(KrakenTypes.CALL_ARGS) ?: return null
        return TextRange(0, args.startOffset - node.startOffset)
    }
}

/**
 * Soft reference: a native function has no declaration to open, so resolving to null
 * is normal. [KrakenFunctionCall.isResolvable] applies the same logic.
 */
class KrakenFunctionReference(element: KrakenFunctionCall, range: TextRange) : PsiReferenceBase<KrakenFunctionCall>(element, range, true) {

    override fun resolve(): PsiElement? = KrakenPsiUtil.findFunctionVisible(element, element.functionName, element.argumentCount)

    override fun getVariants(): Array<Any> = KrakenPsiUtil.findFunctionsVisible(element)
        .mapNotNull { it.name }
        .distinct()
        .toTypedArray()
}

/**
 * Renaming a `Function` rewrites its calls. The called name is not its own BNF node
 * (`call_head` is private), so the head token is replaced, not the whole element.
 */
class KrakenFunctionCallManipulator : AbstractElementManipulator<KrakenFunctionCall>() {

    override fun handleContentChange(
        element: KrakenFunctionCall,
        range: TextRange,
        newContent: String,
    ): KrakenFunctionCall {
        val head = element.node.firstChildNode
        if (head is LeafElement) head.replaceWithText(newContent)
        return element
    }

    override fun getRangeInElement(element: KrakenFunctionCall): TextRange = element.reference?.rangeInElement ?: TextRange(0, element.textLength)
}
