package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.text.StringUtil
import com.kraken.plugin.parser.KrakenTypes

/** `Dimension "name" : Type` declaration. */
class KrakenDimensionDecl(node: ASTNode) :
    ASTWrapperPsiElement(node),
    KrakenDeclaration {

    override val kind: KrakenDeclaration.Kind get() = KrakenDeclaration.Kind.DIMENSION

    override fun getName(): String? = node.findChildByType(KrakenTypes.STRING)?.text?.let(StringUtil::unquoteString)
}
