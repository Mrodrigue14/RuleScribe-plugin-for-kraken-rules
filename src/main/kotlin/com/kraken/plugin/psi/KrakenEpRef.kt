package com.kraken.plugin.psi

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference

/** Nested `EntryPoint "name"` item, referencing that entry point's declaration. */
class KrakenEpRef(node: ASTNode) : KrakenQuotedNameElement(node) {

    val entryPointName: String?
        get() = referencedName

    override fun getReference(): PsiReference? = nameRange?.let { KrakenEntryPointReference(this, it) }

    /** Tells identical references apart in navigation popups. */
    override fun getPresentation(): ItemPresentation = KrakenPresentations.of(
        this,
        KrakenPresentations.containerText(this, entryPointName?.let { "\"$it\"" }),
        KrakenDeclaration.Kind.ENTRY_POINT.icon,
    )
}

class KrakenEntryPointReference(element: KrakenEpRef, range: TextRange) : KrakenNameReference<KrakenEpRef>(element, range) {

    override fun declarationsNamed(name: String): List<PsiElement> = KrakenDeclarations.findEntryPointsVisible(element, name)

    override fun visibleDeclarations(): List<PsiNamedElement> = KrakenDeclarations.findEntryPointsVisible(element)
}
