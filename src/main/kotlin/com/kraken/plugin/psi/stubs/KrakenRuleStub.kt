package com.kraken.plugin.psi.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Stub d'une déclaration de règle : seul le nom est persisté dans l'index,
 * ce qui permet de résoudre les références sans charger l'AST des fichiers.
 */
interface KrakenRuleStub : StubElement<KrakenRuleDecl> {
    val name: String?
}

class KrakenRuleStubImpl(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?
) : StubBase<KrakenRuleDecl>(parent, elementType), KrakenRuleStub
