package com.kraken.plugin.navigation

import com.intellij.codeInsight.hints.VcsCodeVisionCurlyBracketLanguageContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenRuleDecl
import java.awt.event.MouseEvent

/**
 * Inlay « auteur, date » au-dessus des déclarations, à côté de « N usages ».
 *
 * Tout le travail VCS est déjà fait par `VcsCodeVisionProvider` : il lit les
 * annotations, en déduit le dernier auteur d'un bloc et gère le clic. Ce qui
 * lui manque, pour un langage qu'il ne connaît pas, c'est *quels* éléments
 * méritent un inlay et *jusqu'où* va leur bloc — c'est précisément ce que ce
 * contexte fournit.
 *
 * [VcsCodeVisionCurlyBracketLanguageContext] calcule déjà l'étendue d'un bloc
 * délimité par des accolades ; les déclarations Kraken en sont, il suffit donc
 * de lui apprendre à reconnaître l'accolade fermante.
 *
 * **API expérimentale, en connaissance de cause.** Cette classe de base et les
 * deux méthodes redéfinies sont marquées instables : JetBrains peut les changer
 * d'une version à l'autre. C'est le prix de l'inlay, qu'aucune API stable
 * n'expose, et le risque est surveillé plutôt que subi — le Plugin Verifier les
 * signale à chaque exécution hebdomadaire, contre toutes les majeures depuis
 * 2024.1, et aucune n'a bougé jusqu'ici. Le `@Suppress` évite que Qodana
 * répète chaque semaine ce que le Verifier dit déjà.
 */
@Suppress("UnstableApiUsage")
class KrakenVcsCodeVisionContext : VcsCodeVisionCurlyBracketLanguageContext() {

    /**
     * Les déclarations de premier plan d'un fichier `.rules`. Même ensemble que
     * [KrakenReferencesCodeVisionProvider], plus les contextes : ils n'ont pas
     * d'usages à compter, mais savoir qui a modifié un modèle de données en
     * dernier a autant de valeur que pour une règle.
     */
    override fun isAccepted(element: PsiElement): Boolean =
        element is KrakenRuleDecl ||
            element is KrakenEntryPointDecl ||
            element is KrakenFunctionDecl ||
            element.node?.elementType == KrakenTypes.CONTEXT_DECL

    override fun isRBrace(element: PsiElement): Boolean =
        element.node?.elementType == KrakenTypes.RBRACE

    /**
     * Rien à faire : le provider ouvre lui-même l'annotation. Côté Java, cette
     * méthode ne sert qu'à remonter des statistiques d'usage, que ce plugin ne
     * collecte pas.
     */
    override fun handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement) = Unit
}
