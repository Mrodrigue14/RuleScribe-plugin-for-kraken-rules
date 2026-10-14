package com.kraken.plugin.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorStringDescriptor
import com.kraken.plugin.lang.KrakenFileType

/**
 * The names each file references: rule and entry point items, and called functions.
 *
 * Usage searches run for every declaration of a file on each highlighting pass (usages
 * inlay, gutter icon, unused rule inspection). Reading this index, they load only the
 * files that mention a name instead of every file of the project.
 */
class KrakenReferenceIndex : ScalarIndexExtension<String>() {

    enum class Kind { RULE, ENTRY_POINT, FUNCTION }

    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void?, FileContent> = DataIndexer { content ->
        SyntaxTraverser.psiTraverser(content.psiFile).mapNotNull { keyOf(it) }.toSet().associateWith { null }
    }

    override fun getKeyDescriptor(): EnumeratorStringDescriptor = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 1

    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(KrakenFileType)

    override fun dependsOnFileContent(): Boolean = true

    companion object {
        private val NAME: ID<String, Void> = ID.create("kraken.references")

        /** Project files referencing [name] as a [kind]. */
        fun filesReferencing(project: Project, kind: Kind, name: String): List<PsiFile> = FileBasedIndex.getInstance()
            .getContainingFiles(NAME, key(kind, name), GlobalSearchScope.projectScope(project))
            .mapNotNull { PsiManager.getInstance(project).findFile(it) }

        private fun keyOf(element: PsiElement): String? = when (element) {
            is KrakenRuleRef -> key(Kind.RULE, element.ruleName)
            is KrakenEpRef -> element.entryPointName?.let { key(Kind.ENTRY_POINT, it) }
            is KrakenFunctionCall -> element.functionName.takeIf { it.isNotEmpty() }?.let { key(Kind.FUNCTION, it) }
            else -> null
        }

        private fun key(kind: Kind, name: String): String = "${kind.name}:$name"
    }
}
