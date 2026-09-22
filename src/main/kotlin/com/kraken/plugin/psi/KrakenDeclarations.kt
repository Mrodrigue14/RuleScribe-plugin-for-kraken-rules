package com.kraken.plugin.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.declarations
import com.kraken.plugin.psi.stubs.KrakenRuleNameIndex

/** Finds rules, entry points, functions and dimensions, and the references to them, as namespaces allow. */
object KrakenDeclarations {

    fun findRulesVisible(from: PsiElement): List<KrakenRuleDecl> {
        val direct = KrakenNamespaces.visibleFiles(from.containingFile)
            .flatMap { it.declarations<KrakenRuleDecl>() }
        val imported = KrakenNamespaces.ruleImportsForNamespaceOf(from.containingFile)
            .mapNotNull { findRuleInNamespace(from.project, it.sourceNamespace, it.ruleName) }
        return if (imported.isEmpty()) direct else (direct + imported).distinct()
    }

    /**
     * Every visible declaration with this name. Several targets are legitimate:
     * `@Dimension` variants share the rule name (`KrakenDuplicateRuleInspection` only
     * reports duplicates without a distinguishing annotation), so navigation must offer
     * all of them.
     */
    fun findRulesVisible(from: PsiElement, name: String): List<KrakenRuleDecl> {
        val indexed = indexedRules(from.project, KrakenNamespaces.visibleFiles(from.containingFile), name)
        // Fallback for unindexed files (light editor, fragments, tests).
        val declared = indexed.ifEmpty { findRulesVisible(from).filter { it.name == name } }
        // Explicitly imported rule, independent of Include.
        return (declared + listOfNotNull(findImportedRule(from, name))).distinct()
    }

    fun findRuleVisible(from: PsiElement, name: String): KrakenRuleDecl? = findRulesVisible(from, name).firstOrNull()

    /** Resolves a rule declared in namespace [ns], bypassing Include visibility as `Import Rule` does. */
    fun findRuleInNamespace(project: Project, ns: String?, name: String): KrakenRuleDecl? {
        val files = KrakenNamespaces.filesOfNamespace(project, ns)
        indexedRules(project, files, name).firstOrNull()?.let { return it }
        return files
            .flatMap { it.declarations<KrakenRuleDecl>() }
            .firstOrNull { it.name == name }
    }

    /** Declaration targeted by an `Import Rule` in [from]'s namespace, if any. */
    private fun findImportedRule(from: PsiElement, name: String): KrakenRuleDecl? {
        val import = KrakenNamespaces.ruleImportsForNamespaceOf(from.containingFile)
            .firstOrNull { it.ruleName == name } ?: return null
        return findRuleInNamespace(from.project, import.sourceNamespace, name)
    }

    /** Rules named [name] in [files], read from the stub index without loading ASTs. */
    private fun indexedRules(project: Project, files: List<KrakenFile>, name: String): List<KrakenRuleDecl> {
        val virtualFiles = files.mapNotNull { it.virtualFile }
        if (virtualFiles.isEmpty()) return emptyList()
        val scope = GlobalSearchScope.filesScope(project, virtualFiles)
        return StubIndex.getElements(KrakenRuleNameIndex.KEY, name, project, scope, KrakenRuleDecl::class.java).toList()
    }

    fun findEntryPointsVisible(from: PsiElement): List<KrakenEntryPointDecl> = KrakenNamespaces.visibleFiles(from.containingFile)
        .flatMap { it.declarations<KrakenEntryPointDecl>() }

    /** Same as for rules: an EntryPoint can have `@Dimension` variants too. */
    fun findEntryPointsVisible(from: PsiElement, name: String): List<KrakenEntryPointDecl> = findEntryPointsVisible(from).filter { it.name == name }

    fun findEntryPointVisible(from: PsiElement, name: String): KrakenEntryPointDecl? = findEntryPointsVisible(from, name).firstOrNull()

    fun findFunctionsVisible(from: PsiElement): List<KrakenFunctionDecl> = KrakenNamespaces.visibleFiles(from.containingFile)
        .flatMap { it.declarations<KrakenFunctionDecl>() }

    /**
     * The engine indexes a function by `(name, parameter count)`, not by types
     * (`FunctionHeader`). A bodiless signature overrides a `Function` with a body
     * (`ScopeBuilder.resolveFunctionSymbols`), so it wins here too.
     */
    fun findFunctionVisible(from: PsiElement, name: String, arity: Int): KrakenFunctionDecl? = findFunctionsVisible(from)
        .filter { it.name == name && it.arity == arity }
        .minByOrNull { it.hasBody() }

    fun findDimensionNamesVisible(from: PsiFile?): List<String> = KrakenNamespaces.visibleFiles(from)
        .flatMap { it.declarations<KrakenDimensionDecl>() }
        .mapNotNull { it.name }
        .distinct()

    /** Visible calls with this name and arity, for Find Usages. */
    fun findFunctionCallsVisibleTo(declaration: KrakenFunctionDecl): List<KrakenFunctionCall> {
        val name = declaration.name ?: return emptyList()
        val declarationFile = declaration.containingFile
        return KrakenNamespaces.krakenFiles(declaration.project)
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenFunctionCall::class.java) }
            .filter {
                it.functionName == name &&
                    it.argumentCount == declaration.arity &&
                    KrakenNamespaces.sees(it.containingFile, declarationFile)
            }
    }

    /**
     * Rule references that can actually see [declaration]. As in the engine, a reference
     * in a namespace that does not include the declaration's does not count, unless its
     * namespace imports the rule explicitly.
     */
    fun findRuleRefsVisibleTo(declaration: KrakenRuleDecl): List<KrakenRuleRef> {
        val name = declaration.name ?: return emptyList()
        val declarationFile = declaration.containingFile
        val declNs = (declarationFile as? KrakenFile)?.let { KrakenNamespaces.namespaceOf(it) }
        return findRuleRefs(declaration.project, name).filter {
            KrakenNamespaces.seesRule(it.containingFile, name, declarationFile, declNs)
        }
    }

    fun findEpRefsVisibleTo(declaration: KrakenEntryPointDecl): List<KrakenEpRef> {
        val name = declaration.name ?: return emptyList()
        val declarationFile = declaration.containingFile
        return findEpRefs(declaration.project, name).filter { KrakenNamespaces.sees(it.containingFile, declarationFile) }
    }

    /** Nested `EntryPoint "name"` references with this name, across the project. */
    private fun findEpRefs(project: Project, name: String): List<KrakenEpRef> = KrakenNamespaces.krakenFiles(project)
        .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenEpRef::class.java) }
        .filter { it.entryPointName == name }

    /** EntryPoint rule items with this name, across the project. */
    private fun findRuleRefs(project: Project, name: String): List<KrakenRuleRef> = KrakenNamespaces.krakenFiles(project)
        .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenRuleRef::class.java) }
        .filter { it.ruleName == name }
}
