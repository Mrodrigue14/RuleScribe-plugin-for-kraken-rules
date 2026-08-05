package com.kraken.plugin.documentation

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenPsiUtil

/**
 * Info paramètres (Ctrl+P) sur un appel de fonction KEL.
 *
 * Toutes les surcharges du nom sont proposées, pas seulement celle qui
 * correspond à l'arité courante : pendant la frappe, l'appel est justement
 * incomplet, et n'afficher que la signature déjà satisfaite serait inutile.
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
        context: UpdateParameterInfoContext
    ): KrakenFunctionCall? = callAt(context.file, context.offset)

    override fun updateParameterInfo(
        parameterOwner: KrakenFunctionCall,
        context: UpdateParameterInfoContext
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

    private fun callAt(file: com.intellij.psi.PsiFile, offset: Int): KrakenFunctionCall? =
        PsiTreeUtil.getParentOfType(file.findElementAt(offset), KrakenFunctionCall::class.java, false)

    /** Signatures connues pour ce nom : natives d'abord, puis celles du projet. */
    private fun signaturesFor(call: KrakenFunctionCall): List<String> {
        val name = call.functionName
        if (name.isEmpty()) return emptyList()
        val native = KrakenFunctionCatalog.byName(name)
            .sortedBy { it.parameters.size }
            .map { it.signature() }
        val declared = KrakenPsiUtil.findFunctionsVisible(call)
            .filter { it.name == name }
            .sortedBy(KrakenFunctionDecl::arity)
            .map { it.signature() }
        return (native + declared).distinct()
    }
}
