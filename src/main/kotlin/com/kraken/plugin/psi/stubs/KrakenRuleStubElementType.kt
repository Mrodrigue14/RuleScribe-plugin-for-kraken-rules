package com.kraken.plugin.psi.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.kraken.plugin.lang.KrakenLanguage
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Stub element type for RULE_DECL, instantiated by the generated parser through the
 * `elementTypeClass` attribute of the BNF `rule_decl` rule.
 */
class KrakenRuleStubElementType(debugName: String) : IStubElementType<KrakenRuleStub, KrakenRuleDecl>(debugName, KrakenLanguage) {

    override fun createPsi(stub: KrakenRuleStub): KrakenRuleDecl = KrakenRuleDecl(stub, this)

    override fun createStub(psi: KrakenRuleDecl, parentStub: StubElement<out PsiElement>?): KrakenRuleStub = KrakenRuleStubImpl(parentStub, this, psi.name)

    override fun getExternalId(): String = "kraken.RULE_DECL"

    override fun serialize(stub: KrakenRuleStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): KrakenRuleStub = KrakenRuleStubImpl(parentStub, this, dataStream.readNameString())

    override fun indexStub(stub: KrakenRuleStub, sink: IndexSink) {
        val name = stub.name
        if (name != null) {
            sink.occurrence(KrakenRuleNameIndex.KEY, name)
        }
    }
}
