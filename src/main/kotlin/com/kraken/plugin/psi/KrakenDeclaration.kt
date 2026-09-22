package com.kraken.plugin.psi

import com.intellij.icons.AllIcons
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import javax.swing.Icon

/** A named declaration of a `.rules` file: what the structure view lists. */
sealed interface KrakenDeclaration : NavigatablePsiElement {

    val kind: Kind

    enum class Kind(val label: String, val typeText: String, val icon: Icon) {
        RULE("Rule", "rule", AllIcons.Nodes.Method),
        ENTRY_POINT("EntryPoint", "entry point", AllIcons.Nodes.Plugin),
        FUNCTION("Function", "function", AllIcons.Nodes.Function),
        CONTEXT("Context", "context", AllIcons.Nodes.Class),
        DIMENSION("Dimension", "dimension", AllIcons.Nodes.Variable),
    }
}

/** A declaration that references point to. */
sealed interface KrakenReferencedDeclaration : KrakenDeclaration {

    /**
     * References that can see this declaration. As in the engine, a reference in a
     * namespace that cannot see it is not a usage.
     */
    fun visibleUsages(): List<PsiElement>
}
