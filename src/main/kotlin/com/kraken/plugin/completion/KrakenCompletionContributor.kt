package com.kraken.plugin.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleRef

class KrakenCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), KrakenCompletionProvider())
    }
}

private class KrakenCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        // Position dans le fichier original (sans identifiant fictif) : arbre intact
        val position = parameters.originalPosition ?: parameters.position
        val file = position.containingFile as? KrakenFile ?: return
        val prev = prevVisibleLeaf(position)

        val inRuleTarget = isInside(position, KrakenTypes.RULE_TARGET) ||
                (prev != null && (prev.node?.elementType == KrakenTypes.ON_KW ||
                        isInside(prev, KrakenTypes.RULE_TARGET)))

        when {
            isInside(position, KrakenTypes.DIMENSION_ANNOTATION) -> {
                for (name in KrakenPsiUtil.findDimensionNamesVisible(file)) {
                    result.addElement(
                        LookupElementBuilder.create("\"$name\"")
                            .withPresentableText(name)
                            .withTypeText("dimension", true)
                    )
                }
            }
            isInside(position, KrakenTypes.ANNOTATION) -> {
                addKeywords(result, ANNOTATION_KEYWORDS)
            }
            inRuleTarget -> {
                if (prev != null && prev.node?.elementType == KrakenTypes.DOT) {
                    // "On Contexte.<caret>" : champs et enfants du contexte
                    val contextLeaf = prevVisibleLeaf(prev)
                    val contextName = contextLeaf?.text
                    if (contextName != null) {
                        for (field in KrakenPsiUtil.contextFieldNames(file, contextName)) {
                            result.addElement(
                                LookupElementBuilder.create(field).withTypeText("field", true)
                            )
                        }
                    }
                } else {
                    for (name in KrakenPsiUtil.findContextNamesVisible(file)) {
                        result.addElement(
                            LookupElementBuilder.create(name).withTypeText("context", true)
                        )
                    }
                }
            }
            prev != null && (prev.node?.elementType == KrakenTypes.DOT ||
                    prev.node?.elementType == KrakenTypes.QDOT) -> {
                // "Contexte.<caret>" dans une expression (When, Assert, Default To…)
                val headName = prevVisibleLeaf(prev)?.text
                if (headName != null) {
                    for (field in KrakenPsiUtil.contextFieldNames(file, headName)) {
                        result.addElement(
                            LookupElementBuilder.create(field).withTypeText("field", true)
                        )
                    }
                }
            }
            isInside(position, KrakenTypes.ENTRY_POINT_DECL) -> {
                addEntryPointItemCompletions(position, file, result)
            }
            isInside(position, KrakenTypes.RULE_BODY) -> {
                addKeywords(result, RULE_BODY_KEYWORDS)
            }
            else -> {
                addKeywords(result, TOP_LEVEL_KEYWORDS)
            }
        }
    }

    /**
     * Complétion dans un bloc `EntryPoint { ... }` :
     * - règles visibles (icône méthode, fichier d'origine en légende) ;
     * - autres entry points sous la forme `EntryPoint "nom"` (icône plugin) ;
     * - sans les items déjà listés ni l'entry point courant (cycle direct).
     */
    private fun addEntryPointItemCompletions(
        position: PsiElement,
        file: KrakenFile,
        result: CompletionResultSet
    ) {
        val currentDecl = PsiTreeUtil.getParentOfType(position, KrakenEntryPointDecl::class.java, false)
        val currentName = currentDecl?.name
        val alreadyListed: Set<String> = if (currentDecl != null) {
            val rules = PsiTreeUtil.findChildrenOfType(currentDecl, KrakenRuleRef::class.java)
                .map { it.ruleName }
            val entryPoints = PsiTreeUtil.findChildrenOfType(currentDecl, KrakenEpRef::class.java)
                .mapNotNull { it.entryPointName }
            (rules + entryPoints).toSet()
        } else {
            emptySet()
        }

        for (rule in KrakenPsiUtil.findRulesVisible(file)) {
            val name = rule.name ?: continue
            if (name in alreadyListed) continue
            result.addElement(
                LookupElementBuilder.create("\"$name\"")
                    .withPresentableText(name)
                    .withIcon(AllIcons.Nodes.Method)
                    .withTypeText(rule.containingFile.name, true)
            )
        }
        for (entryPoint in KrakenPsiUtil.findEntryPointsVisible(file)) {
            val name = entryPoint.name ?: continue
            if (name == currentName || name in alreadyListed) continue
            result.addElement(
                LookupElementBuilder.create("EntryPoint \"$name\"")
                    .withPresentableText("EntryPoint $name")
                    .withIcon(AllIcons.Nodes.Plugin)
                    .withTypeText(entryPoint.containingFile.name, true)
            )
        }
    }

    private fun addKeywords(result: CompletionResultSet, keywords: List<String>) {
        for (keyword in keywords) {
            result.addElement(LookupElementBuilder.create(keyword).bold())
        }
    }

    private fun isInside(position: PsiElement, elementType: IElementType): Boolean {
        var current: PsiElement? = position
        while (current != null && current !is KrakenFile) {
            if (current.node?.elementType == elementType) return true
            current = current.parent
        }
        return false
    }

    private fun prevVisibleLeaf(element: PsiElement): PsiElement? {
        var current = PsiTreeUtil.prevLeaf(element)
        while (current != null &&
            (current is PsiWhiteSpace || current is PsiComment || current.textLength == 0)
        ) {
            current = PsiTreeUtil.prevLeaf(current)
        }
        return current
    }

    companion object {
        private val TOP_LEVEL_KEYWORDS = listOf(
            "Rule", "Rules", "EntryPoint", "EntryPoints",
            "Context", "Contexts", "Root Context", "System Context",
            "ExternalContext", "ExternalEntity",
            "Namespace", "Include", "Import Rule",
            "Dimension", "Function"
        )

        private val RULE_BODY_KEYWORDS = listOf(
            "Description", "Priority", "When",
            "Assert", "Assert Empty", "Assert Matches", "Assert Length",
            "Assert Size", "Assert Size Min", "Assert Number Min", "Assert In",
            "Set Mandatory", "Set Hidden", "Set Disabled",
            "Default To", "Reset To",
            "Error", "Warn", "Info", "Overridable"
        )

        private val ANNOTATION_KEYWORDS = listOf(
            "Dimension", "ServerSideOnly", "NotStrict", "ForbidTarget", "ForbidReference"
   
        )
    }
}
