package com.kraken.plugin.structure

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
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenIcons
import com.kraken.plugin.lang.declarations
import com.kraken.plugin.psi.KrakenDeclaration

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

    override fun getPresentation(): ItemPresentation {
        val kind = (element as? KrakenDeclaration)?.kind
        return PresentationData(presentableText(), kind?.typeText, kind?.icon ?: KrakenIcons.FILE, null)
    }

    override fun getChildren(): Array<TreeElement> {
        if (element !is KrakenFile) return TreeElement.EMPTY_ARRAY
        return element.declarations<KrakenDeclaration>()
            .map { KrakenStructureViewElement(it) }
            .toTypedArray()
    }

    private fun presentableText(): String = when (element) {
        is KrakenFile -> element.name
        is KrakenDeclaration -> element.name ?: element.kind.label
        else -> element.text
    }
}
