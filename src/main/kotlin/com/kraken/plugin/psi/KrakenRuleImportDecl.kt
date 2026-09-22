package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.kraken.plugin.lang.KrakenParserDefinition
import com.kraken.plugin.parser.KrakenTypes

/**
 * `Import Rule "A", "B" From Ns` declaration.
 *
 * Engine semantics (`ResourceKrakenProjectBuilder.importRules`): the imported rule is
 * copied into the importing namespace as if declared there, regardless of any Include,
 * and imports declared by any file of a namespace apply to the whole namespace.
 */
class KrakenRuleImportDecl(node: ASTNode) : ASTWrapperPsiElement(node) {

    val imports: List<RuleImport>
        get() {
            val namespace = node.findChildByType(KrakenTypes.QUALIFIED_NAME) ?: return emptyList()
            val names = node.findChildByType(KrakenTypes.IMPORT_RULE_NAMES) ?: return emptyList()
            return names.getChildren(KrakenParserDefinition.STRINGS).map {
                RuleImport(StringUtil.unquoteString(it.text), namespace.text.trim(), it.psi, namespace.psi)
            }
        }
}

/** One imported rule name, with the elements to highlight. */
data class RuleImport(
    val ruleName: String,
    val sourceNamespace: String,
    val nameElement: PsiElement,
    val namespaceElement: PsiElement,
)
