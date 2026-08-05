package com.kraken.plugin.psi

import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.parser.KrakenTypes
import javax.swing.Icon

/**
 * Libellés des éléments Kraken dans les popups de navigation (Ctrl+clic,
 * find usages, gutter).
 *
 * Sans [ItemPresentation], la plateforme retombe sur le texte brut de
 * l'élément : quand une déclaration est référencée à plusieurs endroits, le
 * popup affiche N entrées identiques et il devient impossible de savoir
 * laquelle mène où. On fournit donc deux informations distinctes :
 *
 * - le **texte principal** : le conteneur qui référence, p. ex. l'EntryPoint ;
 * - la **localisation** (grisée à droite) : le fichier, et le namespace quand
 *   le fichier en déclare un — c'est ce qui distingue deux références portant
 *   le même nom dans des fichiers différents.
 */
internal object KrakenPresentations {

    /** `police.rules · Base` — le namespace n'apparaît que s'il est déclaré. */
    fun location(element: PsiElement): String? {
        val file = element.containingFile as? KrakenFile ?: return null
        val namespace = KrakenPsiUtil.namespaceOf(file)?.takeIf { it.isNotBlank() }
        return if (namespace == null) file.name else "${file.name} · $namespace"
    }

    /**
     * Texte principal d'une *référence* : l'EntryPoint qui la contient, seul
     * élément qui distingue deux références au sein d'un même fichier. À
     * défaut, le texte de la référence elle-même.
     */
    fun containerText(reference: PsiElement, fallback: String?): String {
        val entryPoint = PsiTreeUtil.getParentOfType(reference, KrakenEntryPointDecl::class.java)
        val name = entryPoint?.name
        if (!name.isNullOrBlank()) return "EntryPoint \"$name\""
        return fallback ?: reference.text.trim()
    }

    /**
     * Texte principal d'une *déclaration* : son nom, suivi de ses annotations
     * quand elle en porte. Deux variantes `@Dimension` d'une même règle
     * partagent le nom *et* le fichier — sans l'annotation, le popup de
     * navigation afficherait deux lignes rigoureusement identiques.
     */
    fun declarationText(declaration: PsiElement, name: String?, fallback: String): String {
        val base = name?.let { "\"$it\"" } ?: fallback
        val annotations = declaration.node.getChildren(null)
            .filter { it.elementType == KrakenTypes.ANNOTATION }
            .joinToString(" ") { it.text.replace(WHITESPACE, " ") }
        return if (annotations.isEmpty()) base else "$base $annotations"
    }

    fun of(element: PsiElement, text: String, icon: Icon?): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String = text
            override fun getLocationString(): String? = location(element)
            override fun getIcon(unused: Boolean): Icon? = icon
        }

    private val WHITESPACE = Regex("""\s+""")

    val RULE_ICON: Icon = AllIcons.Nodes.Method
    val ENTRY_POINT_ICON: Icon = AllIcons.Nodes.Plugin
}
