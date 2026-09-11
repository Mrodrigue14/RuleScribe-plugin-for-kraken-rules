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
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenIcons
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenDimensionDecl
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenPsiUtil
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

    override fun getPresentation(): ItemPresentation = PresentationData(presentableText(), typeText(), icon(), null)

    override fun getChildren(): Array<TreeElement> {
        if (element !is KrakenFile) return TreeElement.EMPTY_ARRAY
        val items = mutableListOf<PsiElement>()
        items.addAll(PsiTreeUtil.findChildrenOfType(element, KrakenRuleDecl::class.java))
        items.addAll(PsiTreeUtil.findChildrenOfType(element, KrakenEntryPointDecl::class.java))
        items.addAll(PsiTreeUtil.findChildrenOfType(element, KrakenDimensionDecl::class.java))
        items.addAll(KrakenPsiUtil.contextDecls(element))
        items.addAll(
            PsiTreeUtil.collectElements(element) { it.node?.elementType == KrakenTypes.FUNCTION_DECL },
        )
        return items
            .sortedBy { it.textOffset }
            .map { KrakenStructureViewElement(it) }
            .toTypedArray()
    }

    private fun presentableText(): String = when {
        element is KrakenFile -> element.name

        element is KrakenRuleDecl -> element.name ?: "Rule"

        element is KrakenEntryPointDecl -> element.name ?: "EntryPoint"

        element is KrakenDimensionDecl -> element.dimensionName ?: "Dimension"

        element.node?.elementType == KrakenTypes.CONTEXT_DECL ->
            KrakenPsiUtil.contextName(element) ?: "Context"

        element.node?.elementType == KrakenTypes.FUNCTION_DECL -> functionName()

        else -> element.text.take(30)
    }

    private fun typeText(): String? = when {
        element is KrakenRuleDecl -> "rule"
        element is KrakenEntryPointDecl -> "entry point"
        element is KrakenDimensionDecl -> "dimension"
        element.node?.elementType == KrakenTypes.CONTEXT_DECL -> "context"
        element.node?.elementType == KrakenTypes.FUNCTION_DECL -> "function"
        else -> null
    }

    private fun icon(): Icon = when {
        element is KrakenFile -> KrakenIcons.FILE
        element is KrakenRuleDecl -> AllIcons.Nodes.Method
        element is KrakenEntryPointDecl -> AllIcons.Nodes.Plugin
        element is KrakenDimensionDecl -> AllIcons.Nodes.Variable
        element.node?.elementType == KrakenTypes.CONTEXT_DECL -> AllIcons.Nodes.Class
        element.node?.elementType == KrakenTypes.FUNCTION_DECL -> AllIcons.Nodes.Function
        else -> KrakenIcons.FILE
    }

    private fun functionName(): String {
        var node = element.node.firstChildNode
        var seenKw = false
        while (node != null) {
            if (node.elementType == KrakenTypes.FUNCTION_KW) {
                seenKw = true
            } else if (seenKw && node.elementType in KrakenPsiUtil.ID_TOKENS) {
                return node.text
            }
            node = node.treeNext
        }
        return "Function"
    }
}
