package com.kraken.plugin.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenIcons
import com.kraken.plugin.psi.KrakenContextDecl
import com.kraken.plugin.psi.KrakenDimensionDecl
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenPresentations
import com.kraken.plugin.psi.KrakenRuleDecl
import javax.swing.Icon

class KrakenStructureViewFactory : PsiStructureViewFactory {

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is KrakenFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel = KrakenStructureViewModel(psiFile, editor)
        }
    }
}

class KrakenStructureViewModel(file: KrakenFile, editor: Editor?) :
    StructureViewModelBase(file, editor, KrakenStructureViewElement(file)),
    StructureViewModel.ElementInfoProvider {

    override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = element.value !is KrakenFile
}

class KrakenStructureViewElement(private val element: PsiElement) :
    StructureViewTreeElement,
    SortableTreeElement {

    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) {
        (element as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (element as? Navigatable)?.canNavigate() ?: false

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun getAlphaSortKey(): String = presentableText()

    override fun getPresentation(): ItemPresentation = PresentationData(presentableText(), kind?.typeText, kind?.icon ?: KrakenIcons.FILE, null)

    private val kind: Kind? get() = kindOf(element)

    override fun getChildren(): Array<TreeElement> {
        if (element !is KrakenFile) return TreeElement.EMPTY_ARRAY
        return PsiTreeUtil.collectElements(element) { it !== element && kindOf(it) != null }
            .sortedBy { it.textOffset }
            .map { KrakenStructureViewElement(it) }
            .toTypedArray()
    }

    private fun presentableText(): String = when (element) {
        is KrakenFile -> element.name
        else -> (element as? NavigationItem)?.name ?: kind?.label ?: element.text.take(30)
    }

    /** The declarations the structure view lists. */
    private enum class Kind(val label: String, val typeText: String, val icon: Icon) {
        RULE("Rule", "rule", KrakenPresentations.RULE_ICON),
        ENTRY_POINT("EntryPoint", "entry point", KrakenPresentations.ENTRY_POINT_ICON),
        DIMENSION("Dimension", "dimension", AllIcons.Nodes.Variable),
        CONTEXT("Context", "context", AllIcons.Nodes.Class),
        FUNCTION("Function", "function", KrakenPresentations.FUNCTION_ICON),
    }

    private companion object {
        fun kindOf(element: PsiElement): Kind? = when {
            element is KrakenRuleDecl -> Kind.RULE
            element is KrakenEntryPointDecl -> Kind.ENTRY_POINT
            element is KrakenDimensionDecl -> Kind.DIMENSION
            element is KrakenFunctionDecl -> Kind.FUNCTION
            element is KrakenContextDecl -> Kind.CONTEXT
            else -> null
        }
    }
}
