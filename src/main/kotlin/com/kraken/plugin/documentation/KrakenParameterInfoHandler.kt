package com.kraken.plugin.documentation

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.psi.KrakenDeclarations
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl

/**
 * Parameter info (Ctrl+P) for a KEL function call.
 *
 * Every overload of the name is shown, not only the one matching the current arity:
 * while typing, the call is incomplete by definition.
 */
class KrakenParameterInfoHandler : ParameterInfoHandler<KrakenFunctionCall, String> {

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): KrakenFunctionCall? {
        val call = callAt(context.file, context.offset) ?: return null
        val signatures = signaturesFor(call)
        if (signatures.isEmpty()) return null
        context.itemsToShow = signatures.toTypedArray()
        return call
    }

    override fun showParameterInfo(element: KrakenFunctionCall, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(
        context: UpdateParameterInfoContext,
    ): KrakenFunctionCall? = callAt(context.file, context.offset)

    override fun updateParameterInfo(
        parameterOwner: KrakenFunctionCall,
        context: UpdateParameterInfoContext,
    ) {
        context.setCurrentParameter(0)
    }

    override fun updateUI(p: String?, context: ParameterInfoUIContext) {
        if (p == null) {
            context.isUIComponentEnabled = false
            return
        }
        context.setupUIComponentPresentation(p, 0, 0, false, false, false, context.defaultParameterColor)
    }

    private fun callAt(file: com.intellij.psi.PsiFile, offset: Int): KrakenFunctionCall? = PsiTreeUtil.getParentOfType(file.findElementAt(offset), KrakenFunctionCall::class.java, false)

    /** Known signatures for this name: natives first, then project functions. */
    private fun signaturesFor(call: KrakenFunctionCall): List<String> {
        val name = call.functionName
        if (name.isEmpty()) return emptyList()
        val native = KrakenFunctionCatalog.byName(name)
            .sortedBy { it.parameters.size }
            .map { it.signature() }
        val declared = KrakenDeclarations.findFunctionsVisible(call)
            .filter { it.name == name }
            .sortedBy(KrakenFunctionDecl::arity)
            .map { it.signature() }
        return (native + declared).distinct()
    }
}
