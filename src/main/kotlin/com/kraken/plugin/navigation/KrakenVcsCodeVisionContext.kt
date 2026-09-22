package com.kraken.plugin.navigation

import com.intellij.codeInsight.hints.VcsCodeVisionCurlyBracketLanguageContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenDeclaration
import com.kraken.plugin.psi.KrakenDimensionDecl
import java.awt.event.MouseEvent

/**
 * "Author, date" inlay above declarations, next to "N usages".
 *
 * `VcsCodeVisionProvider` does the VCS work: annotations, last author of a block, the
 * click. For a language it does not know, it only needs to be told which elements get
 * an inlay and where their block ends. [VcsCodeVisionCurlyBracketLanguageContext]
 * already computes the extent of a brace-delimited block, so this context only has to
 * recognise the closing brace.
 *
 * This base class and both overridden methods are experimental API that JetBrains may
 * change. No stable API exposes the inlay; the weekly Plugin Verifier run checks them
 * against every major version since 2024.1. `@Suppress` keeps Qodana from repeating
 * that warning.
 */
@Suppress("UnstableApiUsage")
class KrakenVcsCodeVisionContext : VcsCodeVisionCurlyBracketLanguageContext() {

    /**
     * Declarations with a block: the same set as [KrakenReferencesCodeVisionProvider] plus
     * contexts, which have no usages to count but whose last author matters as much as a
     * rule's. A one-line `Dimension` has no block.
     */
    override fun isAccepted(element: PsiElement): Boolean = element is KrakenDeclaration && element !is KrakenDimensionDecl

    override fun isRBrace(element: PsiElement): Boolean = element.node?.elementType == KrakenTypes.RBRACE

    /**
     * Nothing to do: the provider opens the annotation itself. On the Java side this method
     * only reports usage statistics, which this plugin does not collect.
     */
    override fun handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement) = Unit
}
