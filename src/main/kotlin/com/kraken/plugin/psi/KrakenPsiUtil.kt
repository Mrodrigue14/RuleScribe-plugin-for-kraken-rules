package com.kraken.plugin.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenFileType
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.stubs.KrakenRuleNameIndex

object KrakenPsiUtil {

    /** Identifiers of a declaration, stopping before the `: …` navigation. */
    fun identifiersOf(node: ASTNode): List<String> {
        val names = mutableListOf<String>()
        var child = node.firstChildNode
        while (child != null) {
            when {
                child.elementType == KrakenTypes.COLON -> return names
                child.elementType == KrakenTypes.ANNOTATION -> Unit
                child.psi is com.intellij.psi.PsiWhiteSpace -> Unit
                child.elementType == KrakenTypes.STAR -> Unit
                child.elementType == KrakenTypes.LBRACKET -> Unit
                child.elementType == KrakenTypes.RBRACKET -> Unit
                child.elementType == KrakenTypes.CHILD_KW -> Unit
                child.elementType == KrakenTypes.EXTERNAL_KW -> Unit
                else -> child.text.trim().takeIf { it.isNotEmpty() }?.let { names += it }
            }
            child = child.treeNext
        }
        return names
    }

    /** Replaces a string's content, keeping its original quote character. */
    fun replaceQuoted(leaf: ASTNode?, content: String) {
        if (leaf !is LeafElement) return
        val quote = leaf.text.firstOrNull() ?: '"'
        leaf.replaceWithText("$quote$content$quote")
    }

    fun insideQuotes(start: Int, length: Int): TextRange = if (length >= 2) TextRange(start + 1, start + length - 1) else TextRange(start, start + length)

    /** Tokens accepted as identifiers (mirrors the BNF `id` rule). */
    @JvmField
    val ID_TOKENS: TokenSet = TokenSet.create(
        KrakenTypes.IDENTIFIER,
        KrakenTypes.ON_KW, KrakenTypes.FROM_KW, KrakenTypes.TO_KW,
        KrakenTypes.MIN_KW, KrakenTypes.MAX_KW, KrakenTypes.STEP_KW,
        KrakenTypes.SIZE_KW, KrakenTypes.LENGTH_KW, KrakenTypes.NUMBER_KW,
        KrakenTypes.EMPTY_KW, KrakenTypes.MANDATORY_KW, KrakenTypes.DISABLED_KW,
        KrakenTypes.HIDDEN_KW, KrakenTypes.DESCRIPTION_KW, KrakenTypes.PRIORITY_KW,
        KrakenTypes.EXTERNAL_KW, KrakenTypes.CHILD_KW, KrakenTypes.ROOT_KW,
        KrakenTypes.SYSTEM_KW, KrakenTypes.CONTEXT_KW, KrakenTypes.DIMENSION_KW,
        KrakenTypes.FUNCTION_KW, KrakenTypes.MATCHES_KW, KrakenTypes.INCLUDE_KW,
        KrakenTypes.NAMESPACE_KW, KrakenTypes.ERROR_KW, KrakenTypes.WARN_KW,
        KrakenTypes.INFO_KW,
    )

    fun unquote(text: String): String {
        if (text.length >= 2) {
            val first = text.first()
            if ((first == '"' || first == '\'') && text.last() == first) {
                return text.substring(1, text.length - 1)
            }
        }
        return text
    }

    fun krakenFiles(project: Project): List<KrakenFile> = FileTypeIndex.getFiles(KrakenFileType, GlobalSearchScope.projectScope(project))
        .mapNotNull { PsiManager.getInstance(project).findFile(it) as? KrakenFile }

    fun namespaceOf(file: KrakenFile): String? = file.node.findChildByType(KrakenTypes.NAMESPACE_DECL)
        ?.findChildByType(KrakenTypes.QUALIFIED_NAME)?.text?.trim()

    fun includesOf(file: KrakenFile): List<String> = file.node.getChildren(TokenSet.create(KrakenTypes.INCLUDE_DECL))
        .mapNotNull { it.findChildByType(KrakenTypes.QUALIFIED_NAME)?.text?.trim() }

    /**
     * Project namespace model: files per namespace plus the include graph. Cached and
     * invalidated on any PSI change, because [visibleFiles] is called by nearly every
     * resolution, completion and inspection.
     */
    private class NamespaceModel(
        val filesByNamespace: Map<String?, List<KrakenFile>>,
        val nsIncludes: Map<String, Set<String>>,
        val allFiles: List<KrakenFile>,
    )

    private val NS_MODEL_KEY: Key<CachedValue<NamespaceModel>> =
        Key.create("kraken.namespaceModel")

    private fun namespaceModel(project: Project): NamespaceModel = CachedValuesManager.getManager(project).getCachedValue(project, NS_MODEL_KEY, {
        val all = krakenFiles(project)
        val filesByNs = HashMap<String?, MutableList<KrakenFile>>()
        val nsIncludes = HashMap<String, MutableSet<String>>()
        for (f in all) {
            val n = namespaceOf(f)
            filesByNs.getOrPut(n) { mutableListOf() }.add(f)
            if (n != null) nsIncludes.getOrPut(n) { mutableSetOf() }.addAll(includesOf(f))
        }
        CachedValueProvider.Result.create(
            NamespaceModel(filesByNs, nsIncludes, all),
            PsiModificationTracker.MODIFICATION_COUNT,
        )
    }, false)

    /**
     * Files visible from [from]: same namespace, transitively included namespaces, and
     * files without a namespace. A file without a namespace sees the whole project.
     */
    fun visibleFiles(from: PsiFile?): List<KrakenFile> {
        val fromKraken = from as? KrakenFile ?: return emptyList()
        val model = namespaceModel(fromKraken.project)
        val ns = namespaceOf(fromKraken)
        val result: List<KrakenFile>
        if (ns == null) {
            result = model.allFiles
        } else {
            // Includes of the current namespace, merged with those of the file being edited,
            // which may not be indexed yet.
            val ownIncludes = includesOf(fromKraken)
            val visited = linkedSetOf(ns)
            val queue = ArrayDeque(listOf(ns))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val includes = if (current == ns) {
                    model.nsIncludes[current].orEmpty() + ownIncludes
                } else {
                    model.nsIncludes[current].orEmpty()
                }
                for (inc in includes) {
                    if (visited.add(inc)) queue.add(inc)
                }
            }
            val collected = mutableListOf<KrakenFile>()
            for (n in visited) collected.addAll(model.filesByNamespace[n].orEmpty())
            collected.addAll(model.filesByNamespace[null].orEmpty())
            result = collected
        }
        // The current file may not be indexed yet (light editor, tests).
        return if (result.any { it.isEquivalentTo(fromKraken) }) result else result + fromKraken
    }

    /**
     * A declared `Import Rule "X" From Ns`: rule name, source namespace, and the PSI
     * elements to highlight.
     *
     * Engine semantics (`ResourceKrakenProjectBuilder.importRules`): the imported rule is
     * copied into the importing namespace as if declared there, regardless of any Include,
     * and imports declared by any file of a namespace apply to the whole namespace.
     */
    data class RuleImport(
        val ruleName: String,
        val sourceNamespace: String,
        val nameElement: PsiElement,
        val namespaceElement: PsiElement,
    )

    /** Imports declared by one `Import Rule … From …` declaration. */
    fun ruleImportsIn(decl: PsiElement): List<RuleImport> {
        val node = decl.node ?: return emptyList()
        if (node.elementType != KrakenTypes.RULE_IMPORT_DECL) return emptyList()
        val nsNode = node.findChildByType(KrakenTypes.QUALIFIED_NAME) ?: return emptyList()
        val ns = nsNode.text.trim()
        val namesNode = node.findChildByType(KrakenTypes.IMPORT_RULE_NAMES) ?: return emptyList()
        val result = mutableListOf<RuleImport>()
        var child = namesNode.firstChildNode
        while (child != null) {
            if (child.elementType == KrakenTypes.STRING) {
                result.add(RuleImport(unquote(child.text), ns, child.psi, nsNode.psi))
            }
            child = child.treeNext
        }
        return result
    }

    fun ruleImportsOf(file: KrakenFile): List<RuleImport> = file.node.getChildren(TokenSet.create(KrakenTypes.RULE_IMPORT_DECL))
        .flatMap { ruleImportsIn(it.psi) }

    /** Imports that apply to [from]'s namespace: those of all its files, as the engine merges them. */
    fun ruleImportsForNamespaceOf(from: PsiFile?): List<RuleImport> {
        val fromKraken = from as? KrakenFile ?: return emptyList()
        val ns = namespaceOf(fromKraken)
        val files = krakenFiles(fromKraken.project).filter { namespaceOf(it) == ns }
        val importing =
            if (files.any { it.isEquivalentTo(fromKraken) }) files else files + fromKraken
        return importing.flatMap { ruleImportsOf(it) }
    }

    fun namespaceExists(project: Project, ns: String): Boolean = krakenFiles(project).any { namespaceOf(it) == ns }

    /** Files of namespace [ns]; null means files without a namespace. */
    fun filesOfNamespace(project: Project, ns: String?): List<KrakenFile> = krakenFiles(project).filter { namespaceOf(it) == ns }

    /** Resolves a rule declared in namespace [ns], bypassing Include visibility as `Import Rule` does. */
    fun findRuleInNamespace(project: Project, ns: String?, name: String): KrakenRuleDecl? {
        val files = filesOfNamespace(project, ns)
        val virtualFiles = files.mapNotNull { it.virtualFile }
        if (virtualFiles.isNotEmpty()) {
            val scope = GlobalSearchScope.filesScope(project, virtualFiles)
            StubIndex.getElements(
                KrakenRuleNameIndex.KEY,
                name,
                project,
                scope,
                KrakenRuleDecl::class.java,
            ).firstOrNull()?.let { return it }
        }
        return files
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenRuleDecl::class.java) }
            .firstOrNull { it.name == name }
    }

    /** Declaration targeted by an `Import Rule` in [from]'s namespace, if any. */
    fun findImportedRule(from: PsiElement, name: String): KrakenRuleDecl? {
        val import = ruleImportsForNamespaceOf(from.containingFile)
            .firstOrNull { it.ruleName == name } ?: return null
        return findRuleInNamespace(from.project, import.sourceNamespace, name)
    }

    private fun refImportsRule(ref: PsiElement, name: String, declNs: String?): Boolean = declNs != null &&
        ruleImportsForNamespaceOf(ref.containingFile)
            .any { it.ruleName == name && it.sourceNamespace == declNs }

    fun findRulesVisible(from: PsiElement): List<KrakenRuleDecl> {
        val direct = visibleFiles(from.containingFile)
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenRuleDecl::class.java) }
        val imported = ruleImportsForNamespaceOf(from.containingFile)
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
        // Fast path: the stub index, without loading ASTs.
        val project = from.project
        val virtualFiles = visibleFiles(from.containingFile).mapNotNull { it.virtualFile }
        val indexed = if (virtualFiles.isEmpty()) {
            emptyList()
        } else {
            StubIndex.getElements(
                KrakenRuleNameIndex.KEY,
                name,
                project,
                GlobalSearchScope.filesScope(project, virtualFiles),
                KrakenRuleDecl::class.java,
            ).toList()
        }
        // Fallback for unindexed files (light editor, fragments, tests).
        val declared = indexed.ifEmpty { findRulesVisible(from).filter { it.name == name } }
        // Explicitly imported rule, independent of Include.
        return (declared + listOfNotNull(findImportedRule(from, name))).distinct()
    }

    fun findRuleVisible(from: PsiElement, name: String): KrakenRuleDecl? = findRulesVisible(from, name).firstOrNull()

    fun findEntryPointsVisible(from: PsiElement): List<KrakenEntryPointDecl> = visibleFiles(from.containingFile)
        .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenEntryPointDecl::class.java) }

    /** Same as for rules: an EntryPoint can have `@Dimension` variants too. */
    fun findEntryPointsVisible(from: PsiElement, name: String): List<KrakenEntryPointDecl> = findEntryPointsVisible(from).filter { it.name == name }

    fun findEntryPointVisible(from: PsiElement, name: String): KrakenEntryPointDecl? = findEntryPointsVisible(from, name).firstOrNull()

    fun findFunctionsVisible(from: PsiElement): List<KrakenFunctionDecl> = visibleFiles(from.containingFile)
        .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenFunctionDecl::class.java) }

    /**
     * The engine indexes a function by `(name, parameter count)`, not by types
     * (`FunctionHeader`): two `Function`s with the same name and arity conflict.
     */
    fun findFunctionVisible(from: PsiElement, name: String, arity: Int): KrakenFunctionDecl? = findFunctionsVisible(from).firstOrNull { it.name == name && it.arity == arity }

    /** Visible calls with this name and arity, for Find Usages. */
    fun findFunctionCallsVisibleTo(declaration: KrakenFunctionDecl): List<KrakenFunctionCall> {
        val name = declaration.name ?: return emptyList()
        val declarationFile = declaration.containingFile
        return krakenFiles(declaration.project)
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenFunctionCall::class.java) }
            .filter {
                it.functionName == name &&
                    it.argumentCount == declaration.arity &&
                    refSees(it, declarationFile)
            }
    }

    private fun refSees(refElement: PsiElement, declarationFile: PsiFile?): Boolean = declarationFile != null &&
        visibleFiles(refElement.containingFile).any { it.isEquivalentTo(declarationFile) }

    /**
     * Rule references that can actually see [declaration]. As in the engine, a reference
     * in a namespace that does not include the declaration's does not count, unless its
     * namespace imports the rule explicitly.
     */
    fun findRuleRefsVisibleTo(declaration: KrakenRuleDecl): List<KrakenRuleRef> {
        val name = declaration.name ?: return emptyList()
        val declarationFile = declaration.containingFile
        val declNs = (declarationFile as? KrakenFile)?.let { namespaceOf(it) }
        return findRuleRefs(declaration.project, name).filter {
            refSees(it, declarationFile) || refImportsRule(it, name, declNs)
        }
    }

    fun findEpRefsVisibleTo(declaration: KrakenEntryPointDecl): List<KrakenEpRef> {
        val name = declaration.name ?: return emptyList()
        val declarationFile = declaration.containingFile
        return findEpRefs(declaration.project, name).filter { refSees(it, declarationFile) }
    }

    /** Nested `EntryPoint "name"` references with this name, across the project. */
    fun findEpRefs(project: Project, name: String): List<KrakenEpRef> = krakenFiles(project)
        .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenEpRef::class.java) }
        .filter { it.entryPointName == name }

    /** EntryPoint rule items with this name, across the project. */
    fun findRuleRefs(project: Project, name: String): List<KrakenRuleRef> = krakenFiles(project)
        .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenRuleRef::class.java) }
        .filter { it.ruleName == name }

    fun findDimensionNamesVisible(from: PsiFile?): List<String> = visibleFiles(from)
        .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenDimensionDecl::class.java) }
        .mapNotNull { it.dimensionName }
        .distinct()

    fun findContextNamesVisible(from: PsiFile?): List<String> = visibleFiles(from).flatMap { contextDecls(it).mapNotNull { decl -> contextName(decl) } }.distinct()

    /**
     * Every visible declaration of this context name.
     *
     * Several files in one namespace can declare the same context (a repository hosting
     * several products, or test fixtures next to the code). Keeping only one, by file
     * order, breaks resolution of fields that exist only in the others.
     */
    fun findContextDecls(from: PsiFile?, name: String): List<PsiElement> = visibleFiles(from)
        .flatMap { contextDecls(it) }
        .filter { contextName(it) == name }

    fun findContextDecl(from: PsiFile?, name: String): PsiElement? = findContextDecls(from, name).firstOrNull()

    /** Field and child names of a context, including those inherited through `Is Parent`. */
    fun contextFieldNames(from: PsiFile?, contextName: String, depth: Int = 0): List<String> {
        if (depth > 4) return emptyList()
        val names = LinkedHashSet<String>()
        // Union over all same-named declarations, so completion does not depend on file order.
        for (decl in findContextDecls(from, contextName)) {
            var child = decl.node.firstChildNode
            while (child != null) {
                when (child.elementType) {
                    KrakenTypes.FIELD_DECL -> fieldName(child)?.let { names.add(it) }
                    KrakenTypes.CHILD_DECL -> childContextName(child)?.let { names.add(it) }
                }
                child = child.treeNext
            }
            val inherited = decl.node.findChildByType(KrakenTypes.INHERITED_CONTEXTS)
            if (inherited != null) {
                for (parent in idLeafTexts(inherited)) {
                    names.addAll(contextFieldNames(from, parent, depth + 1))
                }
            }
        }
        return names.toList()
    }

    fun contextDecls(file: KrakenFile): List<PsiElement> = PsiTreeUtil.collectElements(file) { it.node?.elementType == KrakenTypes.CONTEXT_DECL }.toList()

    fun contextName(contextDecl: PsiElement): String? {
        val keyword = contextDecl.node.findChildByType(KrakenTypes.CONTEXT_KW) ?: return null
        var node: ASTNode? = keyword.treeNext
        while (node != null && node.psi is PsiWhiteSpace) {
            node = node.treeNext
        }
        return if (node != null && node.elementType in ID_TOKENS) node.text else null
    }

    /** Field name: the last identifier before `:` (`[External] Type [*] name`). */
    private fun fieldName(fieldDecl: ASTNode): String? {
        var last: String? = null
        var child = fieldDecl.firstChildNode
        while (child != null) {
            if (child.elementType == KrakenTypes.COLON) break
            if (child.elementType in ID_TOKENS) last = child.text
            child = child.treeNext
        }
        return last
    }

    /** Child context name: the first identifier after `Child [*]`. */
    private fun childContextName(childDecl: ASTNode): String? {
        var seenChildKw = false
        var child = childDecl.firstChildNode
        while (child != null) {
            if (child.elementType == KrakenTypes.CHILD_KW) {
                seenChildKw = true
            } else if (seenChildKw && child.elementType in ID_TOKENS) {
                return child.text
            }
            child = child.treeNext
        }
        return null
    }

    private fun idLeafTexts(node: ASTNode): List<String> {
        val texts = mutableListOf<String>()
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType in ID_TOKENS) texts.add(child.text)
            child = child.treeNext
        }
        return texts
    }
}
