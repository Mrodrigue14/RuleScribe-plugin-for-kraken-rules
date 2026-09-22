package com.kraken.plugin.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.tree.TokenSet
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenRuleImportDecl
import com.kraken.plugin.psi.RuleImport

class KrakenFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, KrakenLanguage) {
    override fun getFileType(): FileType = KrakenFileType
    override fun toString(): String = "Kraken rules file"

    /** `Namespace Base`; null for a file without one, which sees the whole project. */
    val namespace: String?
        get() = node.findChildByType(KrakenTypes.NAMESPACE_DECL)?.let { qualifiedNameIn(it) }

    /** Namespaces named by `Include`. */
    val includes: List<String>
        get() = node.getChildren(INCLUDES).mapNotNull { qualifiedNameIn(it) }

    val ruleImports: List<RuleImport>
        get() = declarations<KrakenRuleImportDecl>().flatMap { it.imports }

    private fun qualifiedNameIn(declaration: ASTNode): String? = declaration.findChildByType(KrakenTypes.QUALIFIED_NAME)?.text?.trim()

    /**
     * Declarations of type [T] at top level or in `Rules`, `EntryPoints` and `Contexts`
     * blocks, the only places the grammar puts them. Rule bodies are not walked.
     */
    fun <T : PsiElement> declarations(type: Class<T>): List<T> = SyntaxTraverser.psiTraverser(this as PsiElement)
        .expand { it === this || it.node?.elementType in DECLARATION_BLOCKS }
        .traverse()
        .filter(type)
        .toList()

    private companion object {
        val INCLUDES = TokenSet.create(KrakenTypes.INCLUDE_DECL)

        val DECLARATION_BLOCKS = TokenSet.create(
            KrakenTypes.RULES_BLOCK,
            KrakenTypes.ENTRY_POINTS_BLOCK,
            KrakenTypes.CONTEXTS_BLOCK,
        )
    }
}

inline fun <reified T : PsiElement> KrakenFile.declarations(): List<T> = declarations(T::class.java)
