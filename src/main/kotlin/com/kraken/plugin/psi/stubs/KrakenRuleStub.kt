package com.kraken.plugin.psi.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Rule declaration stub: only the name is stored, so references resolve without
 * loading file ASTs.
 */
interface KrakenRuleStub : StubElement<KrakenRuleDecl> {
    val name: String?
}

class KrakenRuleStubImpl(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
) : StubBase<KrakenRuleDecl>(parent, elementType),
    KrakenRuleStub
