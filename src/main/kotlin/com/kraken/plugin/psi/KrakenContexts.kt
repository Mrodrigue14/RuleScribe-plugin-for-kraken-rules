package com.kraken.plugin.psi

import com.intellij.psi.PsiFile
import com.kraken.plugin.lang.declarations

/** Context declarations visible from a file, their members, and `Is` inheritance. */
object KrakenContexts {

    fun findContextNamesVisible(from: PsiFile?): List<String> = visibleContexts(from).mapNotNull { it.name }.distinct()

    /**
     * Every visible declaration of this context name.
     *
     * Several files in one namespace can declare the same context (a repository hosting
     * several products, or test fixtures next to the code). Keeping only one, by file
     * order, breaks resolution of fields that exist only in the others.
     */
    fun findContextDecls(from: PsiFile?, name: String): List<KrakenContextDecl> = visibleContexts(from).filter { it.name == name }

    fun findContextDecl(from: PsiFile?, name: String): KrakenContextDecl? = findContextDecls(from, name).firstOrNull()

    fun contextExists(from: PsiFile?, name: String): Boolean = findContextDecl(from, name) != null

    /** Field and child names of a context, including those inherited through `Is Parent`. */
    fun contextFieldNames(from: PsiFile?, contextName: String): List<String> = contextMembers(from, contextName).mapNotNull { it.name }.distinct().toList()

    /**
     * Members of every visible [context] declaration, each followed by those it inherits
     * through `Is`. Same-named declarations are all walked, so a member declared in only
     * one of them is still found. A context already walked is skipped, which stops an `Is`
     * cycle.
     */
    fun contextMembers(from: PsiFile?, context: String): Sequence<KrakenContextMember> = membersOf(from, context, mutableSetOf())

    private fun membersOf(from: PsiFile?, context: String, walked: MutableSet<String>): Sequence<KrakenContextMember> = sequence {
        if (!walked.add(context)) return@sequence
        for (decl in findContextDecls(from, context)) {
            yieldAll(decl.members)
            for (parent in decl.parentNames) yieldAll(membersOf(from, parent, walked))
        }
    }

    private fun visibleContexts(from: PsiFile?): List<KrakenContextDecl> = KrakenNamespaces.visibleFiles(from).flatMap { it.declarations<KrakenContextDecl>() }
}
