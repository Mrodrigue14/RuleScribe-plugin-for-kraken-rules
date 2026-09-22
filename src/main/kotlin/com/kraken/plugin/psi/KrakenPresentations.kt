package com.kraken.plugin.psi

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.parser.KrakenTypes
import javax.swing.Icon

/**
 * Labels for Kraken elements in navigation popups (Ctrl+click, Find Usages, gutter).
 *
 * Without [ItemPresentation] the platform shows the raw element text, so a declaration
 * referenced in several places produces identical entries. Each entry gets:
 *
 * - main text: the referencing container, such as the EntryPoint;
 * - location (greyed, on the right): the file, plus the namespace when the file
 *   declares one, which separates same-named references in different files.
 */
internal object KrakenPresentations {

    /** `policy.rules · Base`; the namespace only appears when declared. */
    private fun location(element: PsiElement): String? {
        val file = element.containingFile as? KrakenFile ?: return null
        val namespace = file.namespace?.takeIf { it.isNotBlank() }
        return if (namespace == null) file.name else "${file.name} · $namespace"
    }

    /**
     * Main text of a reference: its enclosing EntryPoint, the only thing that tells two
     * references in one file apart. Falls back to the reference text.
     */
    fun containerText(reference: PsiElement, fallback: String?): String {
        val entryPoint = PsiTreeUtil.getParentOfType(reference, KrakenEntryPointDecl::class.java)
        val name = entryPoint?.name
        if (!name.isNullOrBlank()) return "EntryPoint \"$name\""
        return fallback ?: reference.text.trim()
    }

    /**
     * Main text of a declaration: its name, followed by its annotations. Two `@Dimension`
     * variants of a rule share name and file, so without the annotation the popup would
     * show two identical lines.
     */
    fun declarationText(declaration: PsiElement, name: String?, fallback: String): String {
        val base = name?.let { "\"$it\"" } ?: fallback
        val annotations = annotationsOf(declaration)
        return if (annotations.isEmpty()) base else "$base ${annotations.joinToString(" ")}"
    }

    /** `@Dimension("state", "CA")`, each on one line. */
    fun annotationsOf(declaration: PsiElement): List<String> = declaration.node.getChildren(ANNOTATIONS).map { compact(it.text) }

    fun of(element: PsiElement, text: String, icon: Icon?): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = text
        override fun getLocationString(): String? = location(element)
        override fun getIcon(unused: Boolean): Icon? = icon
    }

    /** [text] on one line, with each run of whitespace collapsed to a single space. */
    fun compact(text: String): String = text.replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("""\s+""")

    private val ANNOTATIONS = TokenSet.create(KrakenTypes.ANNOTATION)
}
