package com.kraken.plugin.psi.stubs

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Index nom de règle → déclarations. Résolution en O(1) sans scan de fichiers.
 */
class KrakenRuleNameIndex : StringStubIndexExtension<KrakenRuleDecl>() {

    override fun getKey(): StubIndexKey<String, KrakenRuleDecl> = KEY

    companion object {
        @JvmField
        val KEY: StubIndexKey<String, KrakenRuleDecl> =
            StubIndexKey.createIndexKey("kraken.rule.name")
    }
}
