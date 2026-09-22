package com.kraken.plugin.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.tree.TokenSet
import com.kraken.plugin.parser.KrakenTypes

class KrakenFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, KrakenLanguage) {
    override fun getFileType(): FileType = KrakenFileType
    override fun toString(): String = "Kraken rules file"

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
        val DECLARATION_BLOCKS = TokenSet.create(
            KrakenTypes.RULES_BLOCK,
            KrakenTypes.ENTRY_POINTS_BLOCK,
            KrakenTypes.CONTEXTS_BLOCK,
        )
    }
}

inline fun <reified T : PsiElement> KrakenFile.declarations(): List<T> = declarations(T::class.java)
