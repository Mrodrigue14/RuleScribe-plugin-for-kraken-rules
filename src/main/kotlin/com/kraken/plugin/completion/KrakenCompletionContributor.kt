package com.kraken.plugin.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenContexts
import com.kraken.plugin.psi.KrakenDeclaration
import com.kraken.plugin.psi.KrakenDeclarations
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenPresentations
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
        result: CompletionResultSet,
    ) {
        // Position in the original file, without the dummy identifier, where the tree is intact.
        val position = parameters.originalPosition ?: parameters.position
        val file = position.containingFile as? KrakenFile ?: return
        val prev = PsiTreeUtil.prevCodeLeaf(position)
        val prevType = prev?.node?.elementType
        val dot = prev?.takeIf { prevType == KrakenTypes.DOT || prevType == KrakenTypes.QDOT }
        val inRuleTarget = isInside(position, KrakenTypes.RULE_TARGET) ||
            prevType == KrakenTypes.ON_KW ||
            (prev != null && isInside(prev, KrakenTypes.RULE_TARGET))

        when {
            isInside(position, KrakenTypes.DIMENSION_ANNOTATION) -> {
                for (name in KrakenDeclarations.findDimensionNamesVisible(file)) {
                    result.addElement(
                        LookupElementBuilder.create("\"$name\"")
                            .withPresentableText(name)
                            .withTypeText("dimension", true),
                    )
                }
            }

            isInside(position, KrakenTypes.ANNOTATION) -> {
                addKeywords(result, ANNOTATION_KEYWORDS)
            }

            // "On Context.<caret>" and "Context.<caret>" in an expression (When, Assert, Default To…).
            dot != null && (!inRuleTarget || prevType == KrakenTypes.DOT) -> addFieldCompletions(file, dot, result)

            inRuleTarget -> {
                for (name in KrakenContexts.findContextNamesVisible(file)) {
                    result.addElement(
                        LookupElementBuilder.create(name).withTypeText("context", true),
                    )
                }
            }

            isInside(position, KrakenTypes.ENTRY_POINT_DECL) -> {
                addEntryPointItemCompletions(position, file, result)
            }

            isInside(position, KrakenTypes.RULE_BODY) -> {
                addKeywords(result, RULE_BODY_KEYWORDS)
                addFunctionCompletions(position, result)
            }

            isInside(position, KrakenTypes.FUNCTION_BODY) -> {
                addFunctionCompletions(position, result)
            }

            else -> {
                addKeywords(result, TOP_LEVEL_KEYWORDS)
            }
        }
    }

    /** Fields and children of the context named just before [dot]. */
    private fun addFieldCompletions(file: KrakenFile, dot: PsiElement, result: CompletionResultSet) {
        val contextName = PsiTreeUtil.prevCodeLeaf(dot)?.text ?: return
        for (field in KrakenContexts.contextFieldNames(file, contextName)) {
            result.addElement(LookupElementBuilder.create(field).withTypeText("field", true))
        }
    }

    /**
     * Completion inside `EntryPoint { ... }`: visible rules (with their file) and other
     * entry points as `EntryPoint "name"`, excluding items already listed and the current
     * entry point (a direct cycle).
     */
    private fun addEntryPointItemCompletions(
        position: PsiElement,
        file: KrakenFile,
        result: CompletionResultSet,
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

        for (rule in KrakenDeclarations.findRulesVisible(file)) {
            val name = rule.name ?: continue
            if (name in alreadyListed) continue
            result.addElement(
                LookupElementBuilder.create("\"$name\"")
                    .withPresentableText(name)
                    .withIcon(KrakenDeclaration.Kind.RULE.icon)
                    .withTypeText(rule.containingFile.name, true),
            )
        }
        for (entryPoint in KrakenDeclarations.findEntryPointsVisible(file)) {
            val name = entryPoint.name ?: continue
            if (name == currentName || name in alreadyListed) continue
            result.addElement(
                LookupElementBuilder.create("EntryPoint \"$name\"")
                    .withPresentableText("EntryPoint $name")
                    .withIcon(KrakenDeclaration.Kind.ENTRY_POINT.icon)
                    .withTypeText(entryPoint.containingFile.name, true),
            )
        }
    }

    /**
     * Functions callable from an expression: the natives of the bundled catalogue, then the
     * `Function`s declared and visible from this file. A name can exist with several
     * arities, which the engine tells apart, so each arity is its own entry.
     */
    private fun addFunctionCompletions(position: PsiElement, result: CompletionResultSet) {
        for (function in KrakenFunctionCatalog.functions) {
            result.addElement(
                LookupElementBuilder.create(function.name)
                    .withIcon(KrakenDeclaration.Kind.FUNCTION.icon)
                    .withTailText("(${function.parameters.joinToString(", ") { it.presentation() }})", true)
                    .withTypeText(function.returnType, true)
                    .withInsertHandler(ParenthesesInsertHandler.getInstance(function.parameters.isNotEmpty())),
            )
        }
        for (declaration in KrakenDeclarations.findFunctionsVisible(position)) {
            val name = declaration.name ?: continue
            result.addElement(
                LookupElementBuilder.create(name)
                    .withIcon(KrakenDeclaration.Kind.FUNCTION.icon)
                    .withTailText("(${declaration.parameterText()})", true)
                    .withTypeText(declaration.returnType ?: declaration.containingFile.name, true)
                    .withInsertHandler(ParenthesesInsertHandler.getInstance(declaration.arity > 0)),
            )
        }
    }

    private fun addKeywords(result: CompletionResultSet, keywords: List<String>) {
        for (keyword in keywords) {
            result.addElement(LookupElementBuilder.create(keyword).bold())
        }
    }

    private fun isInside(leaf: PsiElement, elementType: IElementType): Boolean = TreeUtil.findParent(leaf.node, elementType) != null

    companion object {
        private val TOP_LEVEL_KEYWORDS = listOf(
            "Rule", "Rules", "EntryPoint", "EntryPoints",
            "Context", "Contexts", "Root Context", "System Context",
            "ExternalContext", "ExternalEntity",
            "Namespace", "Include", "Import Rule",
            "Dimension", "Function",
        )

        private val RULE_BODY_KEYWORDS = listOf(
            "Description", "Priority", "When",
            "Assert", "Assert Empty", "Assert Matches", "Assert Length",
            "Assert Size", "Assert Size Min", "Assert Number Min", "Assert In",
            "Set Mandatory", "Set Hidden", "Set Disabled",
            "Default To", "Reset To",
            "Error", "Warn", "Info", "Overridable",
        )

        private val ANNOTATION_KEYWORDS = listOf(
            "Dimension",
            "ServerSideOnly",
            "NotStrict",
            "ForbidTarget",
            "ForbidReference",
        )
    }
}
