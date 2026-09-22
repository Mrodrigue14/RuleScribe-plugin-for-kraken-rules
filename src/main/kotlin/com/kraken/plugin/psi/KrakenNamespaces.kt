package com.kraken.plugin.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenFileType
import com.kraken.plugin.lang.KrakenLanguage
import java.util.concurrent.ConcurrentHashMap

/**
 * Which files see which: namespaces, `Include`, and `Import Rule`.
 *
 * Mirrors the engine's namespace semantics, including the directional rule: a reference
 * in a namespace that cannot see a declaration does not count, even if the reverse is true.
 */
object KrakenNamespaces {

    fun krakenFiles(project: Project): List<KrakenFile> = namespaceModel(project).allFiles

    fun namespaceOf(file: PsiFile?): String? = (file as? KrakenFile)?.namespace

    /**
     * Files visible from [from]: same namespace, transitively included namespaces, and
     * files without a namespace. A file without a namespace sees the whole project.
     */
    fun visibleFiles(from: PsiFile?): List<KrakenFile> {
        val file = from as? KrakenFile ?: return emptyList()
        val model = namespaceModel(file.project)
        val visible = model.visibleNamespaces(file)
            ?.let { namespaces -> namespaces.flatMap { model.filesOf(it) } + model.filesOf(null) }
            ?: model.allFiles
        return if (model.isIndexed(file)) visible else visible + file
    }

    fun namespaceExists(project: Project, ns: String): Boolean = namespaceModel(project).filesByNamespace.containsKey(ns)

    /** Files of namespace [ns]; null means files without a namespace. */
    fun filesOfNamespace(project: Project, ns: String?): List<KrakenFile> = namespaceModel(project).filesOf(ns)

    /** True if [from] sees the declarations of [declarationFile] through its namespace and its includes. */
    fun sees(from: PsiFile?, declarationFile: PsiFile?): Boolean {
        val file = from as? KrakenFile ?: return false
        val declarations = declarationFile as? KrakenFile ?: return false
        val model = namespaceModel(file.project)
        if (!model.isIndexed(declarations) && !declarations.isEquivalentTo(file)) return false
        val namespace = declarations.namespace ?: return true
        return model.visibleNamespaces(file)?.contains(namespace) ?: true
    }

    /**
     * True if [from] sees rule [name] declared in [declarationFile]: through namespaces, or
     * because its namespace imports the rule from the declaration's namespace.
     */
    fun seesRule(from: PsiFile?, name: String, declarationFile: PsiFile?): Boolean = sees(from, declarationFile) ||
        ruleImportsForNamespaceOf(from).any { it.ruleName == name && it.sourceNamespace == namespaceOf(declarationFile) }

    /** Imports that apply to [from]'s namespace: those of all its files, as the engine merges them. */
    fun ruleImportsForNamespaceOf(from: PsiFile?): List<RuleImport> {
        val file = from as? KrakenFile ?: return emptyList()
        val model = namespaceModel(file.project)
        val imports = model.importsOf(file.namespace)
        return if (model.isIndexed(file)) imports else imports + file.ruleImports
    }

    /**
     * Project namespace model: files, includes and imports per namespace. Cached until a
     * `.rules` file changes, because [visibleFiles] is called by nearly every resolution,
     * completion and inspection.
     */
    private class NamespaceModel(val allFiles: List<KrakenFile>) {
        val filesByNamespace: Map<String?, List<KrakenFile>> = allFiles.groupBy { it.namespace }

        private val includesByNamespace: Map<String?, Set<String>> = filesByNamespace.mapValues { (_, files) -> files.flatMapTo(HashSet()) { it.includes } }

        private val importsByNamespace: Map<String?, List<RuleImport>> = filesByNamespace.mapValues { (_, files) -> files.flatMap { it.ruleImports } }

        private val indexedFiles: Set<VirtualFile> = allFiles.mapNotNullTo(HashSet()) { it.virtualFile }

        private val closures = ConcurrentHashMap<String, Set<String>>()

        fun filesOf(namespace: String?): List<KrakenFile> = filesByNamespace[namespace].orEmpty()

        fun importsOf(namespace: String?): List<RuleImport> = importsByNamespace[namespace].orEmpty()

        /** A copy made for completion stands for its original file. */
        fun isIndexed(file: KrakenFile): Boolean = file.originalFile.virtualFile in indexedFiles

        /**
         * [file]'s namespace and those it includes transitively; null when it has no
         * namespace and so sees everything. The includes of a file missing from the model are
         * merged in, since it may not be indexed yet.
         */
        fun visibleNamespaces(file: KrakenFile): Set<String>? {
            val namespace = file.namespace ?: return null
            if (isIndexed(file)) return closures.getOrPut(namespace) { reachableFrom(namespace, emptyList()) }
            return reachableFrom(namespace, file.includes)
        }

        private fun reachableFrom(namespace: String, extraIncludes: List<String>): Set<String> {
            val reached = linkedSetOf(namespace)
            val queue = ArrayDeque(includesByNamespace[namespace].orEmpty() + extraIncludes)
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (reached.add(next)) queue.addAll(includesByNamespace[next].orEmpty())
            }
            return reached
        }
    }

    private val NS_MODEL_KEY: Key<CachedValue<NamespaceModel>> = Key.create("kraken.namespaceModel")

    private fun namespaceModel(project: Project): NamespaceModel = CachedValuesManager.getManager(project).getCachedValue(project, NS_MODEL_KEY, {
        val files = FileTypeIndex.getFiles(KrakenFileType, GlobalSearchScope.projectScope(project))
            .mapNotNull { PsiManager.getInstance(project).findFile(it) as? KrakenFile }
        CachedValueProvider.Result.create(
            NamespaceModel(files),
            PsiModificationTracker.getInstance(project).forLanguage(KrakenLanguage),
            VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
            ProjectRootModificationTracker.getInstance(project),
        )
    }, false)
}
