package com.kraken.plugin.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenFileType
import com.kraken.plugin.parser.KrakenTypes

/**
 * Which files see which: namespaces, `Include`, and `Import Rule`.
 *
 * Mirrors the engine's namespace semantics, including the directional rule: a reference
 * in a namespace that cannot see a declaration does not count, even if the reverse is true.
 */
object KrakenNamespaces {

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
        return withUnindexed(result, fromKraken)
    }

    /** [file] may not be indexed yet (light editor, tests), so it is added when missing. */
    private fun withUnindexed(files: List<KrakenFile>, file: KrakenFile): List<KrakenFile> = if (files.any { it.isEquivalentTo(file) }) files else files + file

    fun namespaceExists(project: Project, ns: String): Boolean = ns in namespaceModel(project).filesByNamespace

    /** Files of namespace [ns]; null means files without a namespace. */
    fun filesOfNamespace(project: Project, ns: String?): List<KrakenFile> = namespaceModel(project).filesByNamespace[ns].orEmpty()

    /** True if [from] sees [declarationFile] through its namespace and its includes. */
    fun sees(from: PsiFile?, declarationFile: PsiFile?): Boolean = declarationFile != null && visibleFiles(from).any { it.isEquivalentTo(declarationFile) }

    /**
     * True if [from] sees rule [name] declared in [declarationFile]: through namespaces, or
     * because its namespace imports the rule from [declarationNamespace].
     */
    fun seesRule(from: PsiFile?, name: String, declarationFile: PsiFile?, declarationNamespace: String?): Boolean = sees(from, declarationFile) ||
        ruleImportsForNamespaceOf(from).any { it.ruleName == name && it.sourceNamespace == declarationNamespace }

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
                result.add(RuleImport(StringUtil.unquoteString(child.text), ns, child.psi, nsNode.psi))
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
        val files = filesOfNamespace(fromKraken.project, namespaceOf(fromKraken))
        return withUnindexed(files, fromKraken).flatMap { ruleImportsOf(it) }
    }
}
